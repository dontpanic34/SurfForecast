package com.surfcast.surfforecast

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MicroSpotFitTest {

    // Basse mer 06:00, pleine mer 12:20.
    private val tide = DailyTideInfo(highTideTime = "12:20", lowTideTime = "06:00", coefficient = 80)

    private fun hour(h: Int, height: Double) = HourlyUiModel(
        timeFormatted = "$h:00",
        rawTime = LocalDateTime(LocalDate(2026, 10, 8), LocalTime(h, 0)),
        waveHeight = height, wavePeriod = 9.0, waveDirection = 280f,
        energyKj = 100, windSpeedKmh = 5, windDirectionStr = "E", weatherCode = 0, temperature = 18
    )

    @Test
    fun tidePhaseFollowsTheTide() {
        assertEquals("low", tidePhaseAt(LocalTime(6, 20), tide))
        assertEquals("rising", tidePhaseAt(LocalTime(9, 0), tide))
        assertEquals("high", tidePhaseAt(LocalTime(12, 0), tide))
        assertEquals("falling", tidePhaseAt(LocalTime(16, 0), tide))
        assertEquals("falling", tidePhaseAt(LocalTime(23, 0), tide))
        assertEquals("falling", tidePhaseAt(LocalTime(2, 0), tide))
    }

    @Test
    fun tidePhaseIsUnknownWithoutBothExtremes() {
        assertNull(tidePhaseAt(LocalTime(9, 0), DailyTideInfo(highTideTime = "12:20")))
        assertNull(tidePhaseAt(LocalTime(9, 0), null))
    }

    @Test
    fun spotMatchesOnTideAndHeight() {
        val spot = MicroSpot(parentSpotName = "Montalivet", name = "Banc magique", tidePhase = "falling", minHeight = 1.0, maxHeight = 1.6)
        assertTrue(spot.matches(hour(16, 1.2), tide))
        assertFalse(spot.matches(hour(9, 1.2), tide))   // montant
        assertFalse(spot.matches(hour(16, 0.7), tide))  // trop petit
        assertFalse(spot.matches(hour(16, 2.0), tide))  // trop gros
    }

    @Test
    fun spotWithoutProfileNeverMatches() {
        assertFalse(MicroSpot(parentSpotName = "Montalivet", name = "Nord").matches(hour(16, 1.2), tide))
    }

    @Test
    fun matchingHoursAreGroupedIntoWindows() {
        val spot = MicroSpot(parentSpotName = "Montalivet", name = "Banc", tidePhase = "falling")
        val hours = listOf(13, 14, 15, 18, 19).map { hour(it, 1.2) }
        // 13h est à moins de 45 min de la pleine mer (12h20) : étale, donc pas « descendant ».
        assertEquals(listOf(14..15, 18..19), spot.matchingWindows(hours, tide))
    }

    @Test
    fun profileSummaryIsReadable() {
        val spot = MicroSpot(parentSpotName = "M", name = "B", tidePhase = "rising", minHeight = 1.0, maxHeight = 1.5)
        assertEquals("montant · 1,0–1,5 m", spot.profileSummary())
    }
}
