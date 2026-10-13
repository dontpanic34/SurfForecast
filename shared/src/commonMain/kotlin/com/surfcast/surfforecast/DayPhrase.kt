package com.surfcast.surfforecast

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.math.roundToInt

/** Le résumé du jour choisi : un verdict (titre) et une ligne « houle matin → après-midi · vent matin → après-midi ». */
data class DayPhrase(val title: String, val line: String = "")

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
 * Le verdict du jour, en parlé : ce que fait la houle (ça grossit, ça tombe, ça tient) et ce que fait le vent (ça tourne
 * onshore, offshore toute la journée, glassy, venté). Le titre suffit : la taille est sur le graphique.
 */
fun dayPhraseFor(hours: List<HourlyUiModel>, best: SlotRating?, beachFacing: Int?, surferLevel: String = "intermediate"): DayPhrase? {
    if (hours.isEmpty()) return null
    val sorted = hours.sortedBy { it.rawTime }
    val half = maxOf(sorted.size / 2, 1)
    val am = sorted.take(half)
    val pm = sorted.drop(half).ifEmpty { am }
    val amH = am.map { it.waveHeight }.average()
    val pmH = pm.map { it.waveHeight }.average()
    val maxH = sorted.maxOf { it.waveHeight }
    if (maxH < FLAT_BELOW) return DayPhrase("Flat, va pêcher !")
    if (maxH >= MASSIVE_FROM) return DayPhrase("Massif, sors le jet !")

    // La houle est toujours dite (grossit, tombe, constante) : on peut ouvrir l'appli sans avoir vu les prévisions la veille.
    val swell = when {
        pmH - amH >= 0.3 -> "ça grossit"
        amH - pmH >= 0.3 -> "ça tombe"
        else -> "houle constante"
    }
    fun colorOf(hs: List<HourlyUiModel>): Int {
        val dir = hs.filter { windFeelKmh(it) >= 4 }.groupingBy { windDirectionClass(it.windDirectionStr, beachFacing) }
            .eachCount().maxByOrNull { it.value }?.key
        return dir ?: -1
    }
    val ca = colorOf(am)
    val cp = colorOf(pm)
    val strongest = sorted.maxOf { windFeelKmh(it) }
    val onshoreAm = ca == 2
    val onshorePm = cp == 2
    val calm = strongest < 4
    val wind = when {
        calm -> "glassy"
        !onshoreAm && onshorePm -> "ça tourne onshore l'après-midi"
        onshoreAm && !onshorePm -> "ça tourne offshore l'après-midi"
        onshoreAm && onshorePm -> when {
            strongest >= 18 -> "c'est venté"
            strongest >= 10 -> "onshore modéré"
            else -> "léger onshore"
        }
        strongest < 10 -> "glassy, offshore léger"
        ca == 1 && cp == 1 -> "sideshore toute la journée" + if (strongest >= 18) " soutenu" else ""
        else -> "offshore" + (if (strongest >= 18) " soutenu" else "") + " toute la journée"
    }
    val bad = wind == "c'est venté" || wind == "onshore modéré" || wind == "léger onshore" || wind == "ça tourne onshore l'après-midi"
    val title = if (maxH < SMALL_BELOW) {
        if (bad) "Petit et venté" else "Petit mais propre, " + wind.removeSuffix(" toute la journée")
    } else {
        swell + (if (bad && swell != "ça tombe") " mais " else if (bad) " et " else ", ") + wind
    }
    return DayPhrase(title.replaceFirstChar { it.uppercase() })
}

private const val FLAT_BELOW = 0.4
private const val SMALL_BELOW = 0.8
private const val MASSIVE_FROM = 3.0

/** La classe d'une direction de vent : 0 = offshore (jaune), 1 = de travers (orange), 2 = onshore (rouge). */
internal fun windDirectionClass(directionStr: String, beachFacing: Int?): Int {
    val dirFr = SurfUnitsHelper.formatCardinalFr(directionStr)
    val degrees = SurfUnitsHelper.cardinalToDegrees(dirFr).toDouble()
    val offshoreAxis = (((beachFacing ?: 270) + 180) % 360).toDouble()
    val d = angularDifference(degrees, offshoreAxis)
    return when {
        d <= 45.0 -> 0
        d <= 100.0 -> 1
        else -> 2
    }
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
        // Les horaires de la veille manquent après minuit (les prévisions commencent aujourd'hui) : le coucher d'hier vaut celui d'aujourd'hui, à une minute près.
        val ySun = sun[yesterday] ?: todaySun
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

/** Une heure avant le lever, l'encart « Actuellement » passe déjà sur la première heure de surf. */
private const val DAWN_LEAD_MINUTES = 60

/** Vrai quand l'encart « Actuellement » est encore dans la nuit : avant l'heure qui précède le lever, ou après le coucher. */
fun isNowNight(now: LocalDateTime, sun: DailySunInfo?): Boolean {
    sun ?: return false
    val minutes = now.time.hour * 60 + now.time.minute
    val dawn = sun.sunrise.hour * 60 + sun.sunrise.minute - DAWN_LEAD_MINUTES
    return minutes < dawn || now.time > sun.sunset
}

/** L'heure que l'encart « Actuellement » montre : la plus proche, mais l'heure du lever dès qu'on est dans l'heure qui le précède. */
fun nowCardHourOf(now: LocalDateTime, sun: DailySunInfo?): Int {
    sun ?: return now.time.hour
    val minutes = now.time.hour * 60 + now.time.minute
    val sunriseMin = sun.sunrise.hour * 60 + sun.sunrise.minute
    return if (minutes in (sunriseMin - DAWN_LEAD_MINUTES) until sunriseMin) sun.sunrise.hour else now.time.hour
}
