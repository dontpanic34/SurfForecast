package com.surfcast.surfforecast

import kotlin.math.roundToInt

/** Le résumé du jour choisi : un verdict (titre) et une ligne « houle matin → après-midi · vent matin → après-midi ». */
data class DayPhrase(val title: String, val line: String)

private fun categoryWord(category: String): String = when (category) {
    "side" -> "de travers"
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
        amBest.kind == ConditionKind.TOO_BIG && pmBest.kind == ConditionKind.TOO_BIG -> "Gros jour"
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
