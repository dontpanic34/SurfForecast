package com.surfcast.surfforecast

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DaylightTidesTest {
    private fun e(type: String, time: String) = MareeExtremum(type, time)

    @Test
    fun keepsTheTwoTidesInsideTheDaylightWindow() {
        // Lever 08:14, coucher 19:26 : la basse mer de 00:14 et la pleine mer de 06:31 sont de nuit.
        val tides = listOf(e("BM", "00:14"), e("PM", "06:31"), e("BM", "12:39"), e("PM", "18:56"))
        assertEquals(listOf("12:39", "18:56"), daylightTides(tides, 8f + 14f / 60f, 19f + 26f / 60f).map { it.time })
    }

    @Test
    fun fallsBackToTheClosestWhenFewerThanTwoAreInside() {
        // Fenêtre de 07:30 à 19:30 : une seule marée dedans, la deuxième retenue est la plus proche (07:20).
        val tides = listOf(e("BM", "02:00"), e("PM", "07:20"), e("BM", "13:40"), e("PM", "20:05"))
        assertEquals(listOf("07:20", "13:40"), daylightTides(tides, 8f, 19f).map { it.time })
    }

    @Test
    fun keepsTheTwoClosestToMiddayWhenThreeAreInside() {
        val tides = listOf(e("PM", "07:50"), e("BM", "14:05"), e("PM", "20:20"))
        assertEquals(listOf("07:50", "14:05"), daylightTides(tides, 7f, 20f).map { it.time })
    }

    @Test
    fun marksOnlyTheTidesInsideTheWindow() {
        val marks = tideMarks(listOf(e("BM", "00:14"), e("PM", "12:39")), 7.74f, 19.93f)
        assertEquals(1, marks.size)
        assertTrue(marks[0].high)
        assertTrue(abs(marks[0].hour - (12f + 39f / 60f)) < 1e-4f)
    }

    @Test
    fun usesTheKnownHighAndLowWhenThereIsNoFullList() {
        val tides = tidesForDay(DailyTideInfo(highTideTime = "10:30", lowTideTime = "04:10", coefficient = 80), 8f, 19f)
        assertEquals(listOf("04:10", "10:30"), tides.map { it.time })
    }
}
