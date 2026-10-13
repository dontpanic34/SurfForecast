package com.surfcast.surfforecast

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TimeCellsTest {
    private val sun = DailySunInfo(LocalTime(8, 17), LocalTime(19, 22))

    private fun day(count: Int = 24) = (0 until count).map {
        HourlyUiModel(
            timeFormatted = "$it:00", rawTime = LocalDateTime(2026, 10, 13, it, 0), waveHeight = 1.0, wavePeriod = 9.0, waveDirection = 280f,
            energyKj = 100, windSpeedKmh = 10, windDirectionStr = "O", weatherCode = 3, temperature = 18
        )
    }

    @Test
    fun dayStaysHourlyTwoHoursAroundSunAndNightIsGroupedByThree() {
        val hours = day()
        val cells = buildTimeCells(hours, sun)
        // Avant 6 h : deux blocs (0-2, 3-5) ; de 6 h à 22 h : heure par heure (17 cases) ; après : un bloc (23).
        assertEquals(listOf(TimeCell(0, 2, true), TimeCell(3, 5, true)), cells.take(2))
        assertEquals((6..22).toList(), cells.filter { !it.isBlock }.map { it.first })
        assertEquals(TimeCell(23, 23, true), cells.last())
        assertEquals(24, cells.sumOf { it.size })          // aucune heure perdue ni comptée deux fois
    }

    @Test
    fun withoutSunTimesEveryHourKeepsItsOwnCell() {
        assertEquals(24, buildTimeCells(day(), null).size)
        assertTrue(buildTimeCells(emptyList(), sun).isEmpty())
    }

    @Test
    fun touchingABlockPicksItsMiddleHourAndCellLookupFindsTheBlock() {
        val cells = buildTimeCells(day(), sun)
        assertEquals(1, cells.first().representativeIndex())
        assertEquals(0, cellIndexOf(cells, 2))
        assertEquals(1, cellIndexOf(cells, 4))
        assertFalse(cells[cellIndexOf(cells, 14)].isBlock)
    }

    @Test
    fun sunriseSitsAtTheRightPlaceOnTheLine() {
        val hours = day()
        val cells = buildTimeCells(hours, sun)
        val f = timeFraction(cells, hours, 8 + 17 / 60.0)
        // 8 h 17 est dans la case de 8 h : 2 blocs + 2 cases (6 h, 7 h) + 0,28 de case 8 h.
        assertEquals((2 + 2 + 0.283) / cells.size, f.toDouble(), 0.01)
    }
}
