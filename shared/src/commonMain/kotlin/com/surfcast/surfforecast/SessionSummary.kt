package com.surfcast.surfforecast

import kotlin.math.roundToInt

/** Les heures prévues qui recouvrent le créneau [startMin, endMin) (minutes depuis minuit). */
fun sessionWindowHours(allHours: List<HourlyUiModel>, startMin: Int, endMin: Int): List<HourlyUiModel> =
    allHours.filter { it.rawTime.hour * 60 < endMin && (it.rawTime.hour + 1) * 60 > startMin }

/** La note prévue moyenne (0-100) du créneau, -1 si toutes les heures sont « trop gros » ; null sans heure. */
fun windowForecastScore(hours: List<HourlyUiModel>, ratingOf: (HourlyUiModel) -> SlotRating): Int? {
    if (hours.isEmpty()) return null
    val ratings = hours.map(ratingOf)
    val normal = ratings.filter { !it.tooBig }
    return if (normal.isEmpty()) -1 else normal.map { it.score }.average().roundToInt()
}

private fun strengthOf(kmh: Int): String = when {
    kmh < 10 -> "léger"
    kmh < 18 -> "modéré"
    kmh < 26 -> "soutenu"
    else -> "fort"
}

/** Le mot du vent par rapport à la plage : Offshore, Onshore, Side-off, Side-on, Sideshore. */
private fun categoryWord(cat: String): String = when (cat) {
    "side" -> "Sideshore"
    else -> cat.replaceFirstChar { it.uppercase() }
}

/**
 * La phrase des conditions prévues sur le créneau : « 1,3 m à 10 s · Offshore léger · Montant » (moyennes sur les heures du
 * créneau). Null sans heure.
 */
fun sessionSentence(hours: List<HourlyUiModel>, beachFacing: Int?, tidePhase: String?): String? {
    if (hours.isEmpty()) return null
    val height = hours.map { it.waveHeight }.average()
    val period = hours.map { it.wavePeriod }.average().roundToInt()
    val windKmh = hours.map { windFeelKmh(it) }.average().roundToInt()
    val cat = hours.groupingBy { windCategoryFor(it.windDirectionStr, beachFacing) }.eachCount().maxByOrNull { it.value }?.key
    val wind = if (cat != null && windKmh >= 4) tr(categoryWord(cat)) + " " + tr(strengthOf(windKmh)) else tr("Pas de vent")
    val tide = when (tidePhase) {
        "rising" -> tr("Montant")
        "falling" -> tr("Descendant")
        "high" -> tr("Marée haute")
        "low" -> tr("Marée basse")
        else -> null
    }
    val swell = formatDecimal(height, 1).replace('.', ',') + " m " + tr("à") + " $period s"
    return listOfNotNull(swell, wind, tide).joinToString(" · ")
}
