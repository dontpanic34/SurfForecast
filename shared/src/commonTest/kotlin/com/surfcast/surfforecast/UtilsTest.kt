package com.surfcast.surfforecast

import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class UtilsTest {

    @Test
    fun cardinalDirections() {
        assertEquals("N", getCardinalDirection(0.0))
        assertEquals("SSO", getCardinalDirection(202.5))
        assertEquals("E", getCardinalDirection(90.0))
        assertEquals("N", getCardinalDirection(359.0))
    }

    @Test
    fun hourFormatting() {
        assertEquals("07:00", formatHour(LocalDateTime(2026, 1, 1, 7, 30)))
        assertEquals("15:00", formatHour(LocalDateTime(2026, 1, 1, 15, 0)))
    }

    @Test
    fun waveEnergyMatchesSurfForecastCalibration() {
        // 1.962 x 1.44 x 196 = 553.76
        assertEquals(554, calculateWaveEnergyReal(1.2, 14.0))
        assertEquals(0, calculateWaveEnergyReal(0.0, 10.0))
    }

    @Test
    fun decimalFormattingRoundsHalfUpLikeStringFormat() {
        assertEquals("1,0", formatDecimal(1.0, 1, ','))
        assertEquals("0.3", formatDecimal(0.25, 1))
        assertEquals("1.5", formatDecimal(1.46, 1))
        assertEquals("12", formatDecimal(11.5, 0))
        assertEquals("-0.5", formatDecimal(-0.5, 1))
    }

    @Test
    fun beaufortAndWindUnits() {
        assertEquals(3, SurfUnitsHelper.kmhToBeaufort(15))
        assertEquals("8", SurfUnitsHelper.formatWindValue(15, "kts"))
        assertEquals("kts", SurfUnitsHelper.getWindUnitSymbol("noeuds"))
    }
}
