package com.surfcast.surfforecast

import kotlin.math.roundToInt

/** La phrase du jour choisi, en trois lignes : un titre, la houle, le vent. */
data class DayPhrase(val title: String, val swell: String, val wind: String)

/**
 * Écrit les trois lignes du jour choisi à partir des heures de jour [hours] et de la meilleure heure [best] (son libellé
 * sert de titre : les phrases de l'appli, jamais décourageantes). Le vent est dit en offshore / sideshore / onshore.
 */
fun dayPhraseFor(hours: List<HourlyUiModel>, best: SlotRating?, beachFacing: Int?): DayPhrase? {
    if (hours.isEmpty()) return null
    val title = best?.why?.takeIf { it.isNotBlank() } ?: "Prévisions du jour"

    val low = hours.minOf { it.waveHeight }
    val high = hours.maxOf { it.waveHeight }
    val lowText = formatDecimal(low, 1).replace('.', ',')
    val highText = formatDecimal(high, 1).replace('.', ',')
    val sizeText = if (lowText == highText) "$highText m" else "$lowText m à $highText m"
    // La période n'est dite en fourchette que si elle change vraiment dans la journée (2 s ou plus) ; sinon une seule valeur.
    val pLow = hours.minOf { it.wavePeriod }.roundToInt()
    val pHigh = hours.maxOf { it.wavePeriod }.roundToInt()
    val periodText = if (pHigh - pLow >= 2) ", période de $pLow à $pHigh s" else ", $pLow s"
    val swell = "$sizeText$periodText."

    return DayPhrase(title, swell, windLine(hours, beachFacing))
}

private fun categoryWord(category: String): String = when (category) {
    "offshore" -> "offshore"
    "onshore" -> "onshore"
    else -> category
}

/** Catégorie dominante du vent sur une plage d'heures (la plus fréquente, les vents calmes comptent pour rien). */
private fun dominantCategory(hours: List<HourlyUiModel>, beachFacing: Int?): String? =
    hours.filter { windFeelKmh(it) >= 4 }
        .groupingBy { windCategoryFor(it.windDirectionStr, beachFacing) }
        .eachCount().maxByOrNull { it.value }?.key

private fun windLine(hours: List<HourlyUiModel>, beachFacing: Int?): String {
    val half = hours.size / 2
    val morning = hours.take(maxOf(half, 1))
    val afternoon = hours.drop(maxOf(half, 1)).ifEmpty { morning }
    val strongest = hours.maxOf { windFeelKmh(it) }
    if (strongest < 4) return "Quasiment pas de vent."

    val catMorning = dominantCategory(morning, beachFacing)
    val catAfternoon = dominantCategory(afternoon, beachFacing)
    val morningMax = morning.maxOf { windFeelKmh(it) }
    val afternoonMax = afternoon.maxOf { windFeelKmh(it) }

    if (catMorning != null && catAfternoon != null && catMorning != catAfternoon) {
        return "${categoryWord(catMorning).replaceFirstChar { it.uppercase() }} ${strengthWord(morningMax)} le matin, ${categoryWord(catAfternoon)} ${strengthWord(afternoonMax)} l'après-midi."
    }
    val cat = categoryWord(catAfternoon ?: catMorning ?: "side")
    val light = " " + strengthWord(strongest)
    val trend = when {
        afternoonMax >= morningMax + 6 -> ", plus fort l'après-midi"
        morningMax >= afternoonMax + 6 -> ", qui faiblit l'après-midi"
        else -> ""
    }
    return "${cat.replaceFirstChar { it.uppercase() }}$light toute la journée$trend."
}

/** La force du vent ressenti en un mot : léger, modéré, soutenu, fort. */
private fun strengthWord(kmh: Int): String = when {
    kmh < 10 -> "léger"
    kmh < 18 -> "modéré"
    kmh < 26 -> "soutenu"
    else -> "fort"
}
