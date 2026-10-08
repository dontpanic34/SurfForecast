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
        windDir: String = "E",
        gustKmh: Int = windKmh,
        chop: Double = 0.0
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
        temperature = 18,
        windWaveHeight = chop,
        windGustKmh = gustKmh
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

    // --- Orientation de la plage, rafales, clapot ---

    @Test
    fun windCategoryFollowsTheBeachOrientation() {
        // Plage plein ouest (275) : vent d'est = offshore, vent d'ouest = onshore, nord = travers.
        assertEquals("offshore", windCategoryFromDegrees(90f, 275))
        assertEquals("onshore", windCategoryFromDegrees(270f, 275))
        assertEquals("cross", windCategoryFromDegrees(0f, 275))
        // Côte nord de l'Espagne (350) : le vent de sud est offshore, celui du nord onshore.
        assertEquals("offshore", windCategoryFromDegrees(170f, 350))
        assertEquals("onshore", windCategoryFromDegrees(350f, 350))
        // Sans orientation : ancienne règle (plein ouest).
        assertEquals("offshore", windCategoryFromDegrees(90f))
        assertEquals("onshore", windCategoryFromDegrees(270f))
    }

    @Test
    fun sameSouthWindIsOffshoreOnANorthFacingBeachAndOnshoreOnASouthFacingOne() {
        val southWind = hour(10, windKmh = 12, windDir = "S")
        val north = calculateSlotScore(southWind, 350, "intermediate", false)
        val south = calculateSlotScore(southWind, 195, "intermediate", false)
        assertTrue(north > south, "offshore ($north) doit battre onshore ($south)")
    }

    @Test
    fun swellComingFromTheSideScoresLowerThanHeadOn() {
        val headOn = calculateSlotScore(hour(10, waveDir = 275f), 275, "intermediate", false)
        val oblique = calculateSlotScore(hour(10, waveDir = 330f), 275, "intermediate", false)
        val blocked = calculateSlotScore(hour(10, waveDir = 5f), 275, "intermediate", false)
        assertTrue(headOn > oblique, "de face ($headOn) > de travers ($oblique)")
        assertTrue(oblique > blocked, "de travers ($oblique) > bloquée ($blocked)")
        assertEquals(0, blocked)
    }

    @Test
    fun gustsLowerTheScoreOfOtherwiseIdenticalHours() {
        val calm = calculateSlotScore(hour(10, windKmh = 12, windDir = "E"), 275, "intermediate", false)
        val gusty = calculateSlotScore(hour(10, windKmh = 12, windDir = "E", gustKmh = 40), 275, "intermediate", false)
        assertTrue(gusty < calm, "rafales ($gusty) < calme ($calm)")
        assertEquals(12.0 + 0.5 * (40 - 12), effectiveWindKmh(hour(10, windKmh = 12, gustKmh = 40)))
    }

    @Test
    fun chopLowersTheScoreProgressively() {
        assertEquals(1.0, chopFactor(1.0, 0.2))
        val some = chopFactor(1.2, 0.6)
        val lots = chopFactor(1.2, 1.2)
        assertTrue(some in 0.7..0.9, "clapot modéré : $some")
        assertTrue(lots < some && lots >= 0.4, "gros clapot : $lots")
        val clean = calculateSlotScore(hour(10), 275, "intermediate", false)
        val choppy = calculateSlotScore(hour(10, chop = 0.8), 275, "intermediate", false)
        assertTrue(choppy < clean)
    }

    @Test
    fun everySpotHasAnOrientation() {
        val missing = SurfDatabase.getAllSpots().filter { it.idealSwellDirection == null }.map { it.name }
        assertTrue(missing.isEmpty(), "spots sans orientation : $missing")
        assertTrue(SurfDatabase.getAllSpots().all { it.idealSwellDirection in 0..359 })
    }
}
