package com.surfcast.surfforecast

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** La phrase du jour choisi (titre, houle, vent) et l'état de la marée dit en un mot. */
class DayPhraseTest {
    private fun hour(h: Int, waveM: Double, windKmh: Int, windDir: String) = HourlyUiModel(
        timeFormatted = "$h h", rawTime = LocalDateTime(2026, 10, 12, h, 0), waveHeight = waveM, wavePeriod = 10.0, waveDirection = 270f,
        energyKj = 196, windSpeedKmh = windKmh, windDirectionStr = windDir, weatherCode = 0, temperature = 18, windWaveHeight = 0.0
    )

    private val beachFacing = 275

    @Test
    fun noHoursGiveNoPhrase() {
        assertNull(dayPhraseFor(emptyList(), null, beachFacing))
    }

    @Test
    fun swellLineGivesTheRangeOfTheDay() {
        val p = dayPhraseFor(listOf(hour(9, 1.0, 5, "E"), hour(15, 1.4, 5, "E")), null, beachFacing)!!
        assertEquals("Houle de 1,0 m à 1,4 m, 10 s.", p.swell)
    }

    @Test
    fun periodIsARangeOnlyWhenItReallyChanges() {
        fun h(hh: Int, period: Double) = hour(hh, 1.0, 5, "E").copy(wavePeriod = period)
        assertEquals("Houle de 1,0 m, période de 8 à 14 s.", dayPhraseFor(listOf(h(9, 8.0), h(15, 14.0)), null, beachFacing)!!.swell)
        assertEquals("Houle de 1,0 m, 8 s.", dayPhraseFor(listOf(h(9, 8.0), h(15, 9.0)), null, beachFacing)!!.swell)
    }

    @Test
    fun offshoreAllDayGettingStrongerSaysSo() {
        val hours = listOf(hour(9, 1.0, 6, "E"), hour(11, 1.0, 8, "E"), hour(14, 1.0, 16, "E"), hour(17, 1.0, 18, "E"))
        assertEquals("Vent offshore soutenu toute la journée, plus fort l'après-midi.", dayPhraseFor(hours, null, beachFacing)!!.wind)
    }

    @Test
    fun windThatTurnsIsNamedMorningThenAfternoon() {
        val hours = listOf(hour(9, 1.0, 10, "E"), hour(11, 1.0, 10, "E"), hour(14, 1.0, 14, "O"), hour(17, 1.0, 14, "O"))
        assertEquals("Vent offshore modéré le matin, onshore modéré l'après-midi.", dayPhraseFor(hours, null, beachFacing)!!.wind)
    }

    @Test
    fun titleIsTheBestHourPhrase() {
        val p = dayPhraseFor(listOf(hour(12, 1.0, 5, "E")), SlotRating(score = 80, tooBig = false, why = "Bonne taille, vent propre"), beachFacing)!!
        assertEquals("Bonne taille, vent propre", p.title)
    }

    @Test
    fun tideIsOneWord() {
        val tide = DailyTideInfo(highTideTime = "12:00", lowTideTime = "06:00")
        assertEquals("pleine haute", tideStateWord(LocalTime(12, 20), tide))
        assertEquals("pleine basse", tideStateWord(LocalTime(6, 10), tide))
        assertEquals("montante", tideStateWord(LocalTime(9, 0), tide))
        assertEquals("descendante", tideStateWord(LocalTime(15, 0), tide))
        assertNull(tideStateWord(LocalTime(9, 0), null))
    }
}
