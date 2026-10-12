package com.surfcast.surfforecast

import kotlin.math.abs

/** Une marée placée sur la ligne des heures : [hour] décimale, [high] vrai pour une pleine mer. */
internal class TideMark(val hour: Float, val high: Boolean)

private fun tideHour(e: MareeExtremum): Float? {
    val parts = e.time.split(":")
    val h = parts.getOrNull(0)?.toIntOrNull() ?: return null
    val m = parts.getOrNull(1)?.toIntOrNull() ?: return null
    return h + m / 60f
}

/**
 * Toutes les marées qui tombent dans la fenêtre du déroulé (une demi-heure avant le lever à une demi-heure après le
 * coucher), rangées par heure. S'il y en a moins de deux dedans, on complète avec les plus proches de la fenêtre.
 */
internal fun daylightTides(extrema: List<MareeExtremum>, sunrise: Float, sunset: Float): List<MareeExtremum> {
    val lo = sunrise - 0.5f
    val hi = sunset + 0.5f
    val mid = (sunrise + sunset) / 2f
    val ranked = extrema.mapNotNull { e -> tideHour(e)?.let { e to it } }
        .sortedWith(
            compareBy<Pair<MareeExtremum, Float>>(
                { (_, t) -> if (t < lo) lo - t else if (t > hi) t - hi else 0f },
                { (_, t) -> abs(t - mid) }
            )
        )
    val inside = ranked.filter { (_, t) -> t in lo..hi }
    return (if (inside.size >= 2) inside else ranked.take(2)).sortedBy { it.second }.map { it.first }
}

/** Les marées à afficher pour un jour : les deux de la journée, ou à défaut la haute et la basse déjà connues. */
internal fun tidesForDay(info: DailyTideInfo, sunrise: Float, sunset: Float): List<MareeExtremum> =
    if (info.extrema.isNotEmpty()) daylightTides(info.extrema, sunrise, sunset)
    else listOfNotNull(
        info.highTideTime?.let { MareeExtremum("PM", it) },
        info.lowTideTime?.let { MareeExtremum("BM", it) }
    ).sortedBy { it.time }

/** Repères de la ligne des heures : seulement les marées qui tombent dans la fenêtre du déroulé. */
internal fun tideMarks(tides: List<MareeExtremum>, ax0: Float, ax1: Float): List<TideMark> =
    tides.mapNotNull { e -> tideHour(e)?.takeIf { it in ax0..ax1 }?.let { TideMark(it, e.type == "PM") } }
