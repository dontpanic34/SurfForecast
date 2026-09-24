@file:Suppress("SpellCheckingInspection")
package com.surfcast.surfforecast

import java.time.Duration
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt

data class BestSlotResult(
    val startHour: Int,
    val endHour: Int,
    val averageScore: Int,
    val recap: String
)

private const val ENERGY_SATURATION_THRESHOLD_KJ = 1000.0

fun calculateSlotScore(
    hourlyModel: HourlyUiModel,
    idealSwellDirection: Int?,
    surferLevel: String,
    isHighTide: Boolean
): Int {
    val h = hourlyModel.waveHeight
    val t = hourlyModel.wavePeriod
    val windKmh = hourlyModel.windSpeedKmh.toDouble()
    val windCategory = windCategoryFor(hourlyModel.windDirectionStr)

    if (windCategory == "onshore" && windKmh > 25.0) return 0
    if (h < 0.4) return 0

    val coeffDirection = if (idealSwellDirection == null) {
        1.0
    } else {
        val diff = angularDifference(hourlyModel.waveDirection.toDouble(), idealSwellDirection.toDouble())
        if (diff >= 90.0) 0.0 else cos(Math.toRadians(diff))
    }

    val hD = h.toDouble()
    val tD = t.toDouble()
    val energyKj = 1.962 * hD * hD * tD * tD * coeffDirection

    if (energyKj > ENERGY_SATURATION_THRESHOLD_KJ) return 0

    val (targetMin, targetMax) = when (surferLevel) {
        "beginner" -> 50.0 to 150.0
        "confirmed" -> 300.0 to 800.0
        else -> 150.0 to 300.0
    }

    val fit = when {
        energyKj in targetMin..targetMax -> 1.0
        energyKj < targetMin -> (energyKj / targetMin).coerceIn(0.0, 1.0)
        else -> (targetMax / energyKj).coerceIn(0.0, 1.0)
    }

    var score = fit * 100.0

    val windPercent = windMatrixPercent(windKmh, windCategory)
    score *= windPercent

    when (surferLevel) {
        "beginner" -> {
            if (h > 1.0) score *= 0.3
            if (t > 11.0) score *= 0.5
            if (isHighTide) score *= 0.6
            if (windCategory == "onshore" && windKmh <= 15.0 && windPercent > 0.0) {
                score = (score / windPercent) * windPercent.coerceAtLeast(0.6)
            }
        }
        "confirmed" -> {
            if (windCategory != "offshore" && windKmh > 3.0) score *= 0.4
            if (h < 0.8) score *= 0.5
        }
        else -> {
            if (windCategory == "onshore" && windKmh > 15.0) score *= 0.5
        }
    }

    return score.roundToInt().coerceIn(0, 100)
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

    for (windowSize in listOf(3, 2)) {
        if (sorted.size < windowSize) continue
        for (start in 0..(sorted.size - windowSize)) {
            val window = scores.subList(start, start + windowSize)
            val avg = window.average()
            if (avg <= 0.0) continue
            if (best == null || avg > best.averageScore) {
                val windowHours = sorted.subList(start, start + windowSize)
                best = BestSlotResult(
                    startHour = sorted[start].rawTime.hour,
                    endHour = sorted[start + windowSize - 1].rawTime.hour,
                    averageScore = avg.roundToInt(),
                    recap = buildSlotRecap(windowHours)
                )
            }
        }
    }

    return best
}

fun scoreToColorCategory(score: Int): String {
    return when {
        score < 30 -> "red"
        score <= 60 -> "orange"
        else -> "green"
    }
}

fun angularDifference(a: Double, b: Double): Double {
    var diff = abs(a - b) % 360.0
    if (diff > 180.0) diff = 360.0 - diff
    return diff
}

fun windCategoryFor(directionStr: String): String {
    val dirFr = SurfUnitsHelper.formatCardinalFr(directionStr)
    val degrees = SurfUnitsHelper.cardinalToDegrees(dirFr)
    return windCategoryFromDegrees(degrees)
}

fun windCategoryFromDegrees(degrees: Float): String {
    return when (degrees) {
        in 45f..135f -> "offshore"
        in 225f..315f -> "onshore"
        else -> "cross"
    }
}

private fun windMatrixPercent(windKmh: Double, category: String): Double {
    return when {
        windKmh < 8.0 -> when (category) {
            "offshore" -> 1.00
            "cross" -> 0.90
            else -> 0.80
        }
        windKmh <= 18.0 -> when (category) {
            "offshore" -> 1.00
            "cross" -> 0.60
            else -> 0.30
        }
        else -> when (category) {
            "offshore" -> 0.70
            "cross" -> 0.20
            else -> 0.00
        }
    }
}

// Recap court des conditions attendues sur un creneau, ex: "1.0m / 10s - vent leger offshore".
private fun buildSlotRecap(windowHours: List<HourlyUiModel>): String {
    val avgHeight = windowHours.map { it.waveHeight }.average()
    val avgPeriod = windowHours.map { it.wavePeriod }.average()
    val avgWindKmh = windowHours.map { it.windSpeedKmh }.average().roundToInt()
    val midHour = windowHours[windowHours.size / 2]
    val windCategory = windCategoryFor(midHour.windDirectionStr)

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

    val heightStr = String.format(Locale.FRANCE, "%.1f", avgHeight)
    val periodStr = avgPeriod.roundToInt()

    return "${heightStr}m / ${periodStr}s · vent $windIntensity $windCategoryLabel"
}

fun isNearHighTide(hourly: HourlyUiModel, dailyTide: DailyTideInfo?): Boolean {
    val highTimeStr = dailyTide?.highTideTime ?: return false
    return try {
        val highTime = LocalTime.parse(highTimeStr, DateTimeFormatter.ofPattern("HH:mm"))
        val diffMinutes = abs(Duration.between(highTime, hourly.rawTime.toLocalTime()).toMinutes())
        diffMinutes <= 60
    } catch (e: Exception) {
        false
    }
}