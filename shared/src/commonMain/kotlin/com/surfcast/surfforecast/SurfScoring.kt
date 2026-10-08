@file:Suppress("SpellCheckingInspection")
package com.surfcast.surfforecast

import kotlinx.datetime.LocalTime
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt

// Portage 1:1 de app/.../SurfScoring.kt (mêmes seuils, mêmes coefficients).

data class BestSlotResult(
    val startHour: Int,
    val endHour: Int,
    val averageScore: Int,
    val recap: String
)

/** Note d'une heure : [score] 0-100, [tooBig] = trop gros pour le niveau (ce n'est pas « mauvais », c'est trop). */
data class SlotRating(val score: Int, val tooBig: Boolean)

/** Courbes du facteur vent (de 0 à 1) selon la vitesse effective (km/h) : offshore, travers, onshore. */
private val windCurves: Map<String, List<Pair<Double, Double>>> = mapOf(
    "offshore" to listOf(0.0 to 1.0, 15.0 to 1.0, 25.0 to 0.8, 40.0 to 0.5, 55.0 to 0.3),
    "cross" to listOf(0.0 to 1.0, 8.0 to 1.0, 15.0 to 0.8, 25.0 to 0.45, 40.0 to 0.2, 55.0 to 0.1),
    // Onshore très faible (quasi pas de vent) : presque aussi bon que l'offshore.
    "onshore" to listOf(0.0 to 0.95, 8.0 to 0.95, 12.0 to 0.75, 18.0 to 0.4, 25.0 to 0.15, 30.0 to 0.0)
)

internal fun interpolate(x: Double, points: List<Pair<Double, Double>>): Double {
    if (x <= points.first().first) return points.first().second
    for (i in 1 until points.size) {
        val (x0, y0) = points[i - 1]
        val (x1, y1) = points[i]
        if (x <= x1) return y0 + (y1 - y0) * (x - x0) / (x1 - x0)
    }
    return points.last().second
}

/** Énergie (kJ) : zone idéale [min, max] et plafond au-delà duquel c'est « trop gros » pour ce niveau. */
private class LevelProfile(val idealMin: Double, val idealMax: Double, val cap: Double)

private fun profileFor(level: String) = when (level) {
    "beginner" -> LevelProfile(50.0, 200.0, 450.0)
    "confirmed" -> LevelProfile(250.0, 2000.0, 3500.0)
    else -> LevelProfile(150.0, 450.0, 1100.0)
}

fun calculateSlotScore(
    hourlyModel: HourlyUiModel,
    idealSwellDirection: Int?,
    surferLevel: String,
    isHighTide: Boolean
): Int = calculateSlotRating(hourlyModel, idealSwellDirection, surferLevel, isHighTide).score

/**
 * Note d'une heure de prévision (sans connaître le spot réel : bancs de sable, courants...).
 * Énergie de la houle selon le niveau, direction par rapport à la plage, vent (rafales comprises,
 * offshore / onshore selon l'orientation) et clapot.
 */
fun calculateSlotRating(
    hourlyModel: HourlyUiModel,
    idealSwellDirection: Int?,
    surferLevel: String,
    isHighTide: Boolean
): SlotRating {
    val h = hourlyModel.waveHeight
    val t = hourlyModel.wavePeriod
    if (h < 0.4) return SlotRating(0, false)

    // Vent « ressenti » sur l'eau : les rafales comptent aux deux tiers de leur écart avec le vent moyen.
    val windKmh = effectiveWindKmh(hourlyModel)
    // idealSwellDirection = orientation de la plage : elle définit aussi offshore / onshore.
    val windCategory = windCategoryFor(hourlyModel.windDirectionStr, idealSwellDirection)

    val directionDiff = idealSwellDirection?.let {
        angularDifference(hourlyModel.waveDirection.toDouble(), it.toDouble())
    } ?: 0.0
    val coeffDirection = if (directionDiff >= 90.0) 0.0 else cos(directionDiff * PI / 180.0)

    val energyKj = 1.962 * h * h * t * t * coeffDirection
    val profile = profileFor(surferLevel)
    if (energyKj > profile.cap) return SlotRating(0, true)

    val fit = when {
        energyKj in profile.idealMin..profile.idealMax -> 1.0
        energyKj < profile.idealMin -> (energyKj / profile.idealMin).coerceIn(0.0, 1.0)
        else -> (profile.idealMax / energyKj).coerceIn(0.0, 1.0)
    }

    var score = fit * 100.0

    // Clapot (mer de vent) : une houle propre coiffée de clapot est moins bonne qu'une houle seule.
    score *= chopFactor(h, hourlyModel.windWaveHeight)
    score *= interpolate(windKmh, windCurves.getValue(windCategory))
    // Rafales fortes : mauvaises pour tout le monde, quelle que soit la direction.
    val gust = hourlyModel.windGustKmh
    if (gust > 25) score *= (1.0 - (gust - 25) / 30.0).coerceAtLeast(0.3)
    // Houle de travers : en plus de l'énergie réduite, la vague est moins bien formée.
    score *= 1.0 - 0.3 * minOf(directionDiff, 90.0) / 90.0

    if (surferLevel == "beginner") {
        if (h > 1.5) score *= 0.7
        if (t > 13.0) score *= 0.8
        if (isHighTide) score *= 0.85
    }

    return SlotRating(score.roundToInt().coerceIn(0, 100), false)
}

/** Vent pris en compte : vent moyen + les deux tiers de l'écart avec les rafales (0,7 arrondi). */
fun effectiveWindKmh(hourly: HourlyUiModel): Double {
    val wind = hourly.windSpeedKmh.toDouble()
    val gust = hourly.windGustKmh.toDouble()
    return if (gust > wind) wind + 0.7 * (gust - wind) else wind
}

/**
 * Facteur (0.4 à 1) lié au clapot : 1 sous 0,3 m de mer de vent, puis baisse avec le rapport
 * clapot / houle (clapot de 0,6 m sur 1,2 m de houle : environ 0,8 ; égal à la houle : 0,4).
 */
fun chopFactor(swellHeight: Double, windWaveHeight: Double): Double {
    if (windWaveHeight <= 0.3) return 1.0
    val ratio = (windWaveHeight - 0.3) / maxOf(swellHeight, 0.5)
    return (1.0 - ratio * 0.8).coerceIn(0.4, 1.0)
}

fun findBestSlot(
    dailyHours: List<HourlyUiModel>,
    idealSwellDirection: Int?,
    surferLevel: String,
    dailyTide: DailyTideInfo?
): BestSlotResult? {
    if (dailyHours.size < 2) return null

    val sorted = dailyHours.sortedBy { it.rawTime }
    val scores = sorted.map { hourly ->
        calculateSlotScore(hourly, idealSwellDirection, surferLevel, isNearHighTide(hourly, dailyTide))
    }

    var best: BestSlotResult? = null
    var bestAvg = Double.NEGATIVE_INFINITY

    for (windowSize in listOf(3, 2)) {
        if (sorted.size < windowSize) continue
        for (start in 0..(sorted.size - windowSize)) {
            val window = scores.subList(start, start + windowSize)
            val avg = window.average()
            if (avg <= 0.0) continue
            if (best == null || avg > bestAvg) {
                val windowHours = sorted.subList(start, start + windowSize)
                bestAvg = avg
                best = BestSlotResult(
                    startHour = sorted[start].rawTime.hour,
                    endHour = sorted[start + windowSize - 1].rawTime.hour,
                    averageScore = avg.roundToInt(),
                    recap = buildSlotRecap(windowHours, idealSwellDirection)
                )
            }
        }
    }

    return best
}

fun angularDifference(a: Double, b: Double): Double {
    var diff = abs(a - b) % 360.0
    if (diff > 180.0) diff = 360.0 - diff
    return diff
}

fun windCategoryFor(directionStr: String, beachFacing: Int? = null): String {
    val dirFr = SurfUnitsHelper.formatCardinalFr(directionStr)
    val degrees = SurfUnitsHelper.cardinalToDegrees(dirFr)
    return windCategoryFromDegrees(degrees, beachFacing)
}

/**
 * [degrees] : direction d'où vient le vent. [beachFacing] : direction vers laquelle regarde la
 * plage. Vent venant de la mer (écart <= 45 degrés avec l'orientation) = onshore, venant de
 * derrière la plage (>= 135 degrés) = offshore, sinon travers. Sans orientation connue, ancienne
 * règle : plage orientée plein ouest (270).
 */
fun windCategoryFromDegrees(degrees: Float, beachFacing: Int? = null): String {
    val diff = angularDifference(degrees.toDouble(), (beachFacing ?: 270).toDouble())
    return when {
        diff >= 135.0 -> "offshore"
        diff <= 45.0 -> "onshore"
        else -> "cross"
    }
}

// Récap court des conditions attendues sur un créneau, ex : "1,0m / 10s · vent léger offshore".
private fun buildSlotRecap(windowHours: List<HourlyUiModel>, beachFacing: Int?): String {
    val avgHeight = windowHours.map { it.waveHeight }.average()
    val avgPeriod = windowHours.map { it.wavePeriod }.average()
    val avgWindKmh = windowHours.map { it.windSpeedKmh }.average().roundToInt()
    val midHour = windowHours[windowHours.size / 2]
    val windCategory = windCategoryFor(midHour.windDirectionStr, beachFacing)

    val windIntensity = when {
        avgWindKmh < 8 -> "léger"
        avgWindKmh <= 18 -> "modéré"
        else -> "fort"
    }
    val windCategoryLabel = when (windCategory) {
        "offshore" -> "offshore"
        "onshore" -> "onshore"
        else -> "travers"
    }

    // Virgule décimale : l'original utilisait Locale.FRANCE.
    val heightStr = formatDecimal(avgHeight, 1, decimalSeparator = ',')
    val periodStr = avgPeriod.roundToInt()

    return "${heightStr}m / ${periodStr}s · vent $windIntensity $windCategoryLabel"
}

fun isNearHighTide(hourly: HourlyUiModel, dailyTide: DailyTideInfo?): Boolean {
    val highTimeStr = dailyTide?.highTideTime ?: return false
    return try {
        val highTime = LocalTime.parse(highTimeStr)
        val hourlyTime = hourly.rawTime.time
        val highMinutes = highTime.hour * 60 + highTime.minute
        val hourlyMinutes = hourlyTime.hour * 60 + hourlyTime.minute
        val rawDiff = abs(highMinutes - hourlyMinutes)
        val diffMinutes = minOf(rawDiff, 1440 - rawDiff)
        diffMinutes <= 60
    } catch (e: IllegalArgumentException) {
        false
    }
}
