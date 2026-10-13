package com.surfcast.surfforecast

import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class BoardOfTheDayTest {
    private fun hour(height: Double, period: Double, kj: Int, wind: Int, dir: String) = HourlyUiModel(
        timeFormatted = "14:00", rawTime = LocalDateTime(2026, 10, 13, 14, 0), waveHeight = height, wavePeriod = period, waveDirection = 280f,
        energyKj = kj, windSpeedKmh = wind, windDirectionStr = dir, weatherCode = 3, temperature = 20
    )

    private val shortboard = QuiverBoard(id = 1, model = "Short", family = "shortboard", lengthLitrage = "45 L", finSetup = "thruster")
    private val grace = QuiverBoard(id = 2, model = "grace", family = "mid-length", lengthLitrage = "7'0", finSetup = "2+1")

    @Test
    fun aSolidDayWithOnshoreWindStillGetsTheShortboardEvenWhenTheMidLengthIsTickedMou() {
        // 1,8 m, 10 s, 590 kJ, vent de mer 17 km/h : solide, pas mou
        val h = hour(1.8, 10.0, 590, 17, "O")
        val picked = boardOfTheDay(h, listOf(shortboard, grace), 270, conditionsOf = { id -> if (id == 2L) setOf("mou", "longues") else setOf("gros", "creux") })
        assertEquals("Short", picked?.model)
    }

    @Test
    fun aBoardTickedPetitIsNotChosenOnABigDay() {
        val h = hour(2.0, 11.0, 800, 8, "E")
        val picked = boardOfTheDay(h, listOf(grace, shortboard), 270, conditionsOf = { id -> if (id == 2L) setOf("petit", "gros") else emptySet() })
        assertEquals("Short", picked?.model)
    }

    @Test
    fun aSmallMushyDayGoesToTheMidLengthWithAnEmptyGrid() {
        val h = hour(0.6, 7.0, 60, 12, "O")
        assertEquals("grace", boardOfTheDay(h, listOf(shortboard, grace), 270, conditionsOf = { emptySet() })?.model)
    }

    @Test
    fun windChopOnAShoulderHighWaveIsMouButNotOnASolidOne() {
        assertEquals(true, "mou" in gearConditionsOf(hour(1.0, 9.0, 150, 15, "O"), 270))
        assertEquals(false, "mou" in gearConditionsOf(hour(1.8, 10.0, 590, 17, "O"), 270))
    }

    @Test
    fun anAdvancedSurferDoesNotGetABigVolumeBoardOnADemandingDay() {
        val big = QuiverBoard(id = 3, model = "grace", family = "mid-length", lengthLitrage = "7'0", finSetup = "2+1", volumeL = 55.0)
        val short = QuiverBoard(id = 4, model = "Short", family = "shortboard", lengthLitrage = "5'11", finSetup = "thruster", volumeL = 29.0)
        val h = hour(1.8, 10.0, 590, 17, "O")
        // la grille donne la même note aux deux planches : seuls le volume et le niveau les départagent
        val picked = boardOfTheDay(h, listOf(big, short), 270, { setOf("gros") }, levelIndex = 3, body = BodyState(weightKg = 75))
        assertEquals("Short", picked?.model)
    }
}
