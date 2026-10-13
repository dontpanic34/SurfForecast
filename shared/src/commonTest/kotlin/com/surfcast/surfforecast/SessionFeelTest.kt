package com.surfcast.surfforecast

import kotlinx.datetime.LocalDateTime
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionFeelTest {
    @BeforeTest fun french() { I18n.language = "fr" }

    private fun hour(h: Int, height: Double = 1.2, period: Double = 10.0, wind: Int = 8, dir: String = "E") = HourlyUiModel(
        timeFormatted = "$h:00", rawTime = LocalDateTime(2026, 10, 14, h, 0), waveHeight = height, wavePeriod = period, waveDirection = 280f,
        energyKj = 250, windSpeedKmh = wind, windDirectionStr = dir, weatherCode = 3, temperature = 18
    )

    @Test
    fun oldStarsAreReadAsWords() {
        assertEquals("Médiocre", feelLabel(1))
        assertEquals("Parfait", feelLabel(5))
        assertNull(feelLabel(0))
    }

    @Test
    fun newFeelsRoundTrip() {
        SESSION_FEELS.indices.forEach { i -> assertEquals(SESSION_FEELS[i].first, feelLabel(encodeFeel(i))) }
        assertEquals("Challengeant", feelLabel(encodeFeel(6)))
    }

    @Test
    fun referenceSessionsAreVeryGoodOrPerfect() {
        assertTrue(isReferenceFeel(4)) // 4 étoiles = Très bon
        assertTrue(isReferenceFeel(encodeFeel(5)))
        assertFalse(isReferenceFeel(encodeFeel(0)))
        assertFalse(isReferenceFeel(3))
    }

    @Test
    fun windowKeepsTheHoursOfTheSession() {
        val hours = (6..12).map { hour(it) }
        // 8 h 00 → 9 h 40 : les heures 8 et 9
        assertEquals(listOf(8, 9), sessionWindowHours(hours, 8 * 60, 8 * 60 + 100).map { it.rawTime.hour })
    }

    @Test
    fun sentenceTellsSwellWindAndTide() {
        val s = sessionSentence(listOf(hour(8, 1.2, 10.0), hour(9, 1.4, 10.0)), 270, "rising")
        assertEquals("1,3 m à 10 s · Offshore léger · Montant", s)
    }
}
