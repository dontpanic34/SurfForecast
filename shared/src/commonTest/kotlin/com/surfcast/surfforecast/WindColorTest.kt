package com.surfcast.surfforecast

import kotlin.test.Test
import kotlin.test.assertEquals

class WindColorTest {
    private val yellow = SurfUnitsHelper.WIND_YELLOW
    private val orange = SurfUnitsHelper.WIND_ORANGE
    private val red = SurfUnitsHelper.WIND_RED

    @Test
    fun calmIsYellowWhateverTheDirection() {
        for (dir in listOf("E", "N", "O", "NNO", "SO")) {
            assertEquals(yellow, SurfUnitsHelper.getSurfWindColor(dir, 0), "$dir 0 km/h")
            assertEquals(yellow, SurfUnitsHelper.getSurfWindColor(dir, 6), "$dir 6 km/h")
        }
    }

    @Test
    fun offshoreGetsWorseWithStrength() {
        assertEquals(yellow, SurfUnitsHelper.getSurfWindColor("E", 7))
        assertEquals(yellow, SurfUnitsHelper.getSurfWindColor("ESE", 20))
        assertEquals(orange, SurfUnitsHelper.getSurfWindColor("E", 21))
        assertEquals(orange, SurfUnitsHelper.getSurfWindColor("SE", 30))
        assertEquals(red, SurfUnitsHelper.getSurfWindColor("E", 31))
    }

    @Test
    fun sideShoreThresholds() {
        assertEquals(yellow, SurfUnitsHelper.getSurfWindColor("N", 10))
        assertEquals(orange, SurfUnitsHelper.getSurfWindColor("S", 11))
        assertEquals(orange, SurfUnitsHelper.getSurfWindColor("NNE", 20))
        assertEquals(red, SurfUnitsHelper.getSurfWindColor("SSE", 21))
    }

    @Test
    fun onshoreThresholds() {
        assertEquals(orange, SurfUnitsHelper.getSurfWindColor("O", 7))
        assertEquals(orange, SurfUnitsHelper.getSurfWindColor("NNO", 15))
        assertEquals(red, SurfUnitsHelper.getSurfWindColor("OSO", 16))
        assertEquals(red, SurfUnitsHelper.getSurfWindColor("O", 40))
    }

    @Test
    fun lightThemeDarkensTheTextButKeepsTheSameThreeSteps() {
        assertEquals(yellow, SurfUnitsHelper.windTextColor(yellow, true))
        val l = listOf(yellow, orange, red).map { SurfUnitsHelper.windTextColor(it, false) }
        assertEquals(3, l.toSet().size)
    }
}
