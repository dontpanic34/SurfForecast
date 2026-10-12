package com.surfcast.surfforecast

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.math.roundToInt

/** Le résumé du jour choisi : un verdict (titre) et une ligne « houle matin → après-midi · vent matin → après-midi ». */
data class DayPhrase(val title: String, val line: String)

private fun categoryWord(category: String): String = when (category) {
    "side" -> "sideshore"
    else -> category
}

/** Catégorie dominante du vent sur une plage d'heures (la plus fréquente, les vents calmes comptent pour rien). */
private fun dominantCategory(hours: List<HourlyUiModel>, beachFacing: Int?): String? =
    hours.filter { windFeelKmh(it) >= 4 }
        .groupingBy { windCategoryFor(it.windDirectionStr, beachFacing) }
        .eachCount().maxByOrNull { it.value }?.key

/** Le vent d'une moitié de journée gêne-t-il ? Onshore et side-on dès 12 km/h ressentis, les autres plus tard. */
private fun windBad(hours: List<HourlyUiModel>, beachFacing: Int?): Boolean {
    val cat = dominantCategory(hours, beachFacing) ?: return false
    val strongest = hours.maxOf { windFeelKmh(it) }
    return when (cat) {
        "onshore", "side-on" -> strongest >= 12
        "side", "side-off" -> strongest >= 22
        else -> strongest >= 30
    }
}

private fun windHalfText(hours: List<HourlyUiModel>, beachFacing: Int?): String {
    val strongest = hours.maxOf { windFeelKmh(it) }
    val cat = dominantCategory(hours, beachFacing) ?: return "pas de vent"
    return "${categoryWord(cat)} ${strengthWord(strongest)}"
}

private fun meters(h: Double) = formatDecimal(h, 1).replace('.', ',') + " m"

/**
 * Le résumé du jour à partir des heures de jour [hours] : matin et après-midi, houle et vent. Le verdict suit
 * une priorité : trop gros, la taille, le vent qui change de camp, une limite (rafales, clapot, période, travers), puis propre.
 */
fun dayPhraseFor(hours: List<HourlyUiModel>, best: SlotRating?, beachFacing: Int?, surferLevel: String = "intermediate"): DayPhrase? {
    if (hours.isEmpty()) return null
    val sorted = hours.sortedBy { it.rawTime }
    val half = maxOf(sorted.size / 2, 1)
    val am = sorted.take(half)
    val pm = sorted.drop(half).ifEmpty { am }
    fun bestOf(hs: List<HourlyUiModel>) = hs.map { calculateSlotRating(it, beachFacing, surferLevel) }.maxByOrNull { it.score }!!
    val amBest = bestOf(am)
    val pmBest = bestOf(pm)
    val amH = am.map { it.waveHeight }.average()
    val pmH = pm.map { it.waveHeight }.average()
    val avgH = (amH + pmH) / 2
    val amBad = windBad(am, beachFacing)
    val pmBad = windBad(pm, beachFacing)
    val amSmall = amBest.kind == ConditionKind.TOO_SMALL
    val pmSmall = pmBest.kind == ConditionKind.TOO_SMALL
    val limitWhy = (best ?: maxOf(amBest, pmBest, compareBy { it.score })).why
    val limit = when {
        limitWhy.startsWith("Rafales") || limitWhy.startsWith("Des rafales") -> "Rafales gênantes"
        limitWhy.contains("clapot") -> "Clapot"
        limitWhy.startsWith("Houle trop courte") || limitWhy.startsWith("Houle un peu courte") -> "Houle trop courte"
        limitWhy == "Houle de travers" -> "Houle de travers"
        limitWhy == "Creux et puissant" -> "Creux et puissant"
        else -> null
    }
    val title = when {
        amBest.kind == ConditionKind.TOO_BIG && pmBest.kind == ConditionKind.TOO_BIG -> "Massif, sors le jet !"
        amSmall && pmSmall -> when {
            maxOf(amH, pmH) < 0.5 -> "Tout petit, mer lisse"
            amBad || pmBad -> "Petit et venté"
            else -> "Petit mais propre"
        }
        amSmall -> "Ça se lève l'après-midi"
        pmSmall -> "Ça s'éteint l'après-midi"
        !amBad && pmBad -> "Le vent gâte l'après-midi"
        amBad && !pmBad -> "Mieux l'après-midi"
        amBad && pmBad -> if (avgH >= 2.0) "Gros et venté" else "Venté toute la journée"
        limit != null -> limit
        amBest.score >= pmBest.score + 10 -> "Mieux le matin"
        pmBest.score >= amBest.score + 10 -> "Mieux l'après-midi"
        avgH >= 2.0 -> "Gros et propre"
        avgH < 1.1 -> "Petit mais propre"
        pmH - amH >= 0.4 -> "La houle monte"
        amH - pmH >= 0.4 -> "La houle baisse"
        dominantCategory(am, beachFacing) == "side" && dominantCategory(pm, beachFacing) == "side" -> "Vent de travers"
        else -> "Journée propre"
    }

    val swell = if (kotlin.math.abs(amH - pmH) >= 0.2) "${meters(amH)} → ${meters(pmH)}" else meters(avgH)
    val amWind = windHalfText(am, beachFacing)
    val pmWind = windHalfText(pm, beachFacing)
    val wind = if (amWind == pmWind) amWind else "$amWind → $pmWind"
    return DayPhrase(title, "$swell · $wind")
}

/** La force du vent ressenti en un mot : léger, modéré, soutenu, fort. */
private fun strengthWord(kmh: Int): String = when {
    kmh < 10 -> "léger"
    kmh < 18 -> "modéré"
    kmh < 26 -> "soutenu"
    else -> "fort"
}

/**
 * Les heures de la nuit autour de [now] : de la tombée de la nuit jusqu'à 1 h avant le lever du soleil.
 * Vide quand les horaires du soleil manquent.
 */
fun nightHours(all: List<HourlyUiModel>, sun: Map<LocalDate, DailySunInfo>, now: LocalDateTime): List<HourlyUiModel> {
    val today = now.date
    val todaySun = sun[today] ?: return emptyList()
    val fromDate: LocalDate
    val fromHour: Int
    val toDate: LocalDate
    val toHour: Int
    if (now.time < todaySun.sunrise) {
        val yesterday = today.minus(1, DateTimeUnit.DAY)
        val ySun = sun[yesterday] ?: return emptyList()
        fromDate = yesterday; fromHour = ySun.sunset.hour; toDate = today; toHour = todaySun.sunrise.hour
    } else {
        val tomorrow = today.plus(1, DateTimeUnit.DAY)
        val tSun = sun[tomorrow] ?: return emptyList()
        fromDate = today; fromHour = todaySun.sunset.hour; toDate = tomorrow; toHour = tSun.sunrise.hour
    }
    return all.filter { h ->
        val afterStart = h.rawTime.date > fromDate || (h.rawTime.date == fromDate && h.rawTime.hour > fromHour)
        val beforeEnd = h.rawTime.date < toDate || (h.rawTime.date == toDate && h.rawTime.hour < toHour)
        afterStart && beforeEnd
    }.sortedBy { it.rawTime }
}

/** Le plan d'eau attendu au lever, dit avec prudence, selon la catégorie et la force du vent. */
private fun waterWord(category: String, strength: String): String {
    val hard = strength == "soutenu" || strength == "fort"
    return when (category) {
        "offshore" -> when (strength) {
            "léger" -> "plutôt propre"
            "modéré" -> "plutôt lisse"
            else -> "à surveiller"
        }
        "side-off" -> if (hard) "plutôt marqué" else if (strength == "léger") "plutôt propre" else "plutôt correct"
        else -> when {
            hard -> "probablement dégradé"
            strength == "léger" -> "plutôt correct"
            else -> "plutôt marqué"
        }
    }
}

/**
 * La nuit en une phrase : houle et vent de la nuit, puis ce que le vent laisse au lever. Null s'il y a trop peu d'heures.
 */
fun nightPhrase(night: List<HourlyUiModel>, beachFacing: Int?): String? {
    if (night.size < 3) return null
    val third = maxOf(night.size / 3, 1)
    val early = night.take(third)
    val late = night.takeLast(third)
    val avgH = night.map { it.waveHeight }.average()
    val earlyH = early.map { it.waveHeight }.average()
    val lateH = late.map { it.waveHeight }.average()
    val swell = when {
        lateH - earlyH >= 0.3 -> "houle qui monte de ${meters(earlyH).removeSuffix(" m")} à ${meters(lateH)}"
        earlyH - lateH >= 0.3 -> "houle qui baisse de ${meters(earlyH).removeSuffix(" m")} à ${meters(lateH)}"
        else -> "houle stable autour de ${meters(avgH)}"
    }
    val nightMax = night.maxOf { windFeelKmh(it) }
    val earlyMax = early.maxOf { windFeelKmh(it) }
    val lateMax = late.maxOf { windFeelKmh(it) }
    val nightWind = when {
        nightMax < 4 -> "pas de vent"
        nightMax < 10 -> "vent faible"
        else -> {
            val cat = categoryWord(dominantCategory(night, beachFacing) ?: "side")
            val trend = when {
                lateMax >= earlyMax + 6 -> " qui monte"
                earlyMax >= lateMax + 6 -> " qui tombe"
                else -> ""
            }
            "$cat$trend"
        }
    }
    val lateCat = dominantCategory(late, beachFacing)
    val morning = when {
        lateMax < 4 || lateCat == null -> "au lever, pas de vent : plan d'eau plutôt lisse"
        earlyMax >= lateMax + 8 && lateMax < 10 ->
            "au lever, le vent est tombé (${categoryWord(lateCat)} ${strengthWord(lateMax)})"
        else -> "au lever, ${categoryWord(lateCat)} ${strengthWord(lateMax)} : plan d'eau ${waterWord(lateCat, strengthWord(lateMax))}"
    }
    return "Cette nuit, $swell, $nightWind ; $morning."
}
