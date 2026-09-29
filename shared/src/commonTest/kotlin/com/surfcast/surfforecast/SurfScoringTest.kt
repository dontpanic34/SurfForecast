package com.surfcast.surfforecast

import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// Valeurs attendues calculées à la main depuis les formules de app/.../SurfScoring.kt.
class SurfScoringTest {

    private fun hour(
        h: Int,
        height: Double = 1.0,
        period: Double = 10.0,
        waveDir: Float = 270f,
        windKmh: Int = 5,
        windDir: String = "E"
    ) = HourlyUiModel(
        timeFormatted = formatHour(LocalDateTime(2026, 1, 1, h, 0)),
        rawTime = LocalDateTime(2026, 1, 1, h, 0),
        waveHeight = height,
        wavePeriod = period,
        waveDirection = waveDir,
        energyKj = calculateWaveEnergyReal(height, period),
        windSpeedKmh = windKmh,
        windDirectionStr = windDir,
        weatherCode = 0,
        temperature = 18
    )

    @Test
    fun perfectIntermediateConditionsScore100() {
        // 1.962 x 1² x 10² = 196.2 kJ, dans la cible 150-300 ; vent offshore faible -> x1.
        assertEquals(100, calculateSlotScore(hour(10), null, "intermediate", false))
    }

    @Test
    fun strongOnshoreWindOrSmallWavesScoreZero() {
        assertEquals(0, calculateSlotScore(hour(10, windKmh = 30, windDir = "O"), null, "intermediate", false))
        assertEquals(0, calculateSlotScore(hour(10, height = 0.3), null, "intermediate", false))
    }

    @Test
    fun beginnerPenalties() {
        // 1.962 x 1.44 x 144 = 406.84 -> fit 150/406.84 = 0.3687 -> 36.87
        // x0.3 (h > 1) x0.5 (t > 11) x0.6 (marée haute) = 3.318 -> 3
        assertEquals(3, calculateSlotScore(hour(10, height = 1.2, period = 12.0), null, "beginner", true))
    }

    @Test
    fun swellDirectionAndCrossWind() {
        // Écart 60° -> cos = 0.5 -> 98.1 kJ -> fit 0.654 -> 65.4 ; vent N 10 km/h (travers) x0.6 -> 39.24
        assertEquals(39, calculateSlotScore(hour(10, waveDir = 330f, windKmh = 10, windDir = "N"), 270, "intermediate", false))
    }

    @Test
    fun bestSlotPicksThreeHourWindowWithFrenchRecap() {
        val hours = listOf(hour(8, height = 0.3), hour(9), hour(10), hour(11))
        val best = findBestSlot(hours, null, "intermediate", null)
        assertNotNull(best)
        assertEquals(9, best.startHour)
        assertEquals(11, best.endHour)
        assertEquals(100, best.averageScore)
        assertEquals("1,0m / 10s · vent léger offshore", best.recap)
    }

    @Test
    fun nearHighTideWithinOneHourIncludingMidnightWrap() {
        assertTrue(isNearHighTide(hour(11), DailyTideInfo(highTideTime = "10:30")))
        assertFalse(isNearHighTide(hour(12), DailyTideInfo(highTideTime = "10:30")))
        assertTrue(isNearHighTide(hour(0), DailyTideInfo(highTideTime = "23:50")))
        assertFalse(isNearHighTide(hour(0), null))
    }

    @Test
    fun scoreColorCategories() {
        assertEquals("red", scoreToColorCategory(29))
        assertEquals("orange", scoreToColorCategory(60))
        assertEquals("green", scoreToColorCategory(61))
    }
}
