package com.surfcast.surfforecast

import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModelAgreementTest {
    private fun hour(height: Double = 1.2, pMin: Double = 0.0, pMax: Double = 0.0, wMin: Int = 0, wMax: Int = 0) = HourlyUiModel(
        timeFormatted = "14:00", rawTime = LocalDateTime(2026, 10, 13, 14, 0), waveHeight = height, wavePeriod = 9.0, waveDirection = 280f,
        energyKj = 100, windSpeedKmh = 10, windDirectionStr = "O", weatherCode = 3, temperature = 20,
        periodMin = pMin, periodMax = pMax, windMinKmh = wMin, windMaxKmh = wMax
    )

    @Test
    fun seaIsMixedWhenModelsDisagreeOnThePeriodByThreeSecondsOrMore() {
        assertTrue(seaIsMixed(hour(pMin = 7.7, pMax = 11.4)))
        assertFalse(seaIsMixed(hour(pMin = 9.0, pMax = 10.5)))
        assertFalse(seaIsMixed(hour()))                          // un seul modèle : rien à dire
        assertFalse(seaIsMixed(hour(height = 0.2, pMin = 5.0, pMax = 12.0)))  // trop petit pour que ça compte
    }

    @Test
    fun windModelsDisagreeFromSevenKmhOfSpread() {
        assertTrue(windModelsDisagree(hour(wMin = 3, wMax = 12)))
        assertFalse(windModelsDisagree(hour(wMin = 9, wMax = 12)))
        assertFalse(windModelsDisagree(hour()))
    }
}
