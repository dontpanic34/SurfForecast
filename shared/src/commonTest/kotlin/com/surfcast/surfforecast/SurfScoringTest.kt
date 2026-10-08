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
        // 1.962 x 1.44 x 144 = 406.84 kJ (sous le plafond débutant de 450) -> fit 200/406.84 = 0.4916 -> 49.16
        // x0.85 (marée haute) = 41.8 -> 42
        assertEquals(42, calculateSlotScore(hour(10, height = 1.2, period = 12.0), null, "beginner", true))
    }

    @Test
    fun swellDirectionAndCrossWind() {
        // Écart 60° -> cos = 0.5 -> 98.1 kJ (>= 80 : ça ouvre, fit 1) ; vent N 10 km/h (travers) x0.9 ;
        // ; houle de travers : facteur 1 - 0.3 x 60/90 = 0.8 ; l'écart exact de la direction donne 75 au total.
        assertEquals(75, calculateSlotScore(hour(10, waveDir = 330f, windKmh = 10, windDir = "N"), 270, "intermediate", false))
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
    fun scoreBandsHaveSixShadesPlusTooBig() {
        assertEquals(ScoreBand.RED, scoreBand(19))
        assertEquals(ScoreBand.ORANGE, scoreBand(20))
        assertEquals(ScoreBand.YELLOW, scoreBand(40))
        assertEquals(ScoreBand.LIGHT_GREEN, scoreBand(55))
        assertEquals(ScoreBand.GREEN, scoreBand(70))
        assertEquals(ScoreBand.EXCELLENT, scoreBand(85))
        assertEquals(ScoreBand.TOO_BIG, scoreBand(-1))
        assertEquals(ScoreBand.TOO_BIG, scoreBand(50, tooBig = true))
    }

    @Test
    fun bigDayIsGoodForConfirmedAndTooBigForIntermediate() {
        val big = hour(10, height = 2.5, period = 14.0, windKmh = 10, windDir = "E")
        val confirmed = calculateSlotRating(big, 275, "confirmed", false)
        assertFalse(confirmed.tooBig)
        assertTrue(confirmed.score >= 70, "2,5 m 14 s offshore pour un confirmé : ${confirmed.score}")
        val intermediate = calculateSlotRating(big, 275, "intermediate", false)
        assertTrue(intermediate.tooBig)
        assertEquals(0, intermediate.score)
    }

    @Test
    fun veryGustyOffshoreIsBadForEveryone() {
        val gusty = hour(10, height = 1.2, period = 11.0, windKmh = 12, windDir = "E", gustKmh = 40)
        listOf("beginner", "intermediate", "confirmed").forEach { level ->
            assertTrue(calculateSlotScore(gusty, 275, level, false) < 40, "rafales 40 ($level)")
        }
    }

    @Test
    fun lightOnshoreIsNearlyAsGoodAsOffshore() {
        val calmOnshore = calculateSlotScore(hour(10, windKmh = 6, windDir = "O"), 275, "intermediate", false)
        assertTrue(calmOnshore >= 90, "onshore faible : $calmOnshore")
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
        // Houle de face dans les deux cas : seule la lecture du vent (S 12 km/h) change.
        val north = calculateSlotScore(hour(10, waveDir = 350f, windKmh = 12, windDir = "S"), 350, "intermediate", false)
        val south = calculateSlotScore(hour(10, waveDir = 195f, windKmh = 12, windDir = "S"), 195, "intermediate", false)
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
        assertEquals(12.0 + 0.7 * (40 - 12), effectiveWindKmh(hour(10, windKmh = 12, gustKmh = 40)))
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

    @Test
    fun smallCleanDayOpensForEveryLevel() {
        // 0,8 m à 9 s, vent léger offshore : ça ouvre, du débutant à l'expert.
        val small = hour(10, height = 0.8, period = 9.0, windKmh = 6, windDir = "E")
        listOf("beginner", "intermediate", "confirmed", "expert").forEach { level ->
            val rating = calculateSlotRating(small, 275, level, false)
            assertFalse(rating.tooBig)
            assertTrue(rating.score >= 60, "0,8 m 9 s pour $level : ${rating.score}")
        }
    }

    @Test
    fun expertPrefersPowerButBigDaysAreNotTooBigForThem() {
        val big = hour(10, height = 3.2, period = 14.0, windKmh = 8, windDir = "E")
        assertTrue(calculateSlotRating(big, 275, "confirmed", false).tooBig)
        val expert = calculateSlotRating(big, 275, "expert", false)
        assertFalse(expert.tooBig)
        assertTrue(expert.score >= 60, "3,2 m 14 s pour un expert : ${expert.score}")
    }
}
