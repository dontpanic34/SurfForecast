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

    private fun day(vararg hs: HourlyUiModel) = dayPhraseFor(hs.toList(), null, beachFacing)!!

    @Test
    fun lineSaysSwellThenWindMorningThenAfternoon() {
        val p = day(hour(9, 0.7, 10, "E"), hour(11, 0.7, 10, "E"), hour(14, 1.3, 20, "O"), hour(17, 1.3, 20, "O"))
        assertEquals("Ça grossit mais ça tourne onshore l'après-midi", p.title)
    }

    @Test
    fun sameHalvesAreSaidOnce() {
        val p = day(hour(9, 1.0, 10, "E"), hour(14, 1.0, 10, "E"))
        assertEquals("Offshore toute la journée", p.title)
    }

    @Test
    fun windThatTurnsOnshoreSpoilsTheAfternoon() {
        val p = day(hour(9, 1.3, 8, "E"), hour(11, 1.3, 8, "E"), hour(14, 1.3, 20, "O"), hour(17, 1.3, 20, "O"))
        assertEquals("Ça tourne onshore l'après-midi", p.title)
    }

    @Test
    fun windThatFallsMakesTheAfternoonBetter() {
        val p = day(hour(9, 1.3, 20, "O"), hour(11, 1.3, 20, "O"), hour(14, 1.3, 8, "E"), hour(17, 1.3, 8, "E"))
        assertEquals("Ça tourne offshore l'après-midi", p.title)
    }

    @Test
    fun cleanDayIsSaidClean() {
        val p = day(hour(9, 1.0, 6, "E"), hour(14, 1.0, 6, "E")).copy(title = day(hour(9, 1.2, 6, "E"), hour(14, 1.2, 6, "E")).title)
        assertEquals("Glassy, offshore léger", p.title)
    }

    @Test
    fun noWindIsSaidSo() {
        assertEquals("Glassy", day(hour(9, 1.3, 1, "E"), hour(14, 1.3, 1, "E")).title)
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

/** Les étoiles (note « neutre ») ne dépendent ni du niveau, ni de la motivation, et un offshore vaut plus qu'un onshore. */
class NeutralStarsTest {
    private fun hour(h: Int, waveM: Double, windKmh: Int, windDir: String) = HourlyUiModel(
        timeFormatted = "$h h", rawTime = LocalDateTime(2026, 10, 12, h, 0), waveHeight = waveM, wavePeriod = 12.0, waveDirection = 280f,
        energyKj = calculateWaveEnergyReal(waveM, 12.0), windSpeedKmh = windKmh, windDirectionStr = windDir, weatherCode = 0,
        temperature = 18, windWaveHeight = 0.0
    )

    private fun day(waveM: Double, wind: Int, dir: String) = (8..19).map { hour(it, waveM, wind, dir) }

    @Test
    fun starsDoNotMoveWithMotivation() {
        val d = day(1.2, 15, "NO")
        ToleranceSettings.mode = 0
        val normal = dayQualityScore(d, 275, NEUTRAL_LEVEL, null)
        ToleranceSettings.mode = 1
        val motivated = dayQualityScore(d, 275, NEUTRAL_LEVEL, null)
        ToleranceSettings.mode = -1
        val lazy = dayQualityScore(d, 275, NEUTRAL_LEVEL, null)
        ToleranceSettings.mode = 0
        assertEquals(normal, motivated)
        assertEquals(normal, lazy)
    }

    @Test
    fun offshoreBeatsOnshoreAtTheSameSize() {
        val offshore = dayQualityScore(day(1.2, 12, "E"), 275, NEUTRAL_LEVEL, null)!!
        val onshore = dayQualityScore(day(1.2, 15, "NO"), 275, NEUTRAL_LEVEL, null)!!
        kotlin.test.assertTrue(offshore > onshore, "offshore $offshore, onshore $onshore")
    }

    private fun night(vararg specs: Triple<Double, Int, String>) = specs.mapIndexed { i, (h, w, d) ->
        hour(18 + i, h, w, d)
    }

    @Test
    fun nightPhraseTurnsTowardTheMorningWithPrudence() {
        val hours = night(Triple(1.4, 4, "E"), Triple(1.4, 4, "E"), Triple(1.4, 4, "E"), Triple(1.4, 14, "E"), Triple(1.4, 14, "E"), Triple(1.4, 14, "E"))
        assertEquals("Cette nuit, houle stable autour de 1,4 m, offshore qui monte ; au lever, offshore modéré : plan d'eau plutôt lisse.", nightPhrase(hours, 275))
    }

    @Test
    fun nightPhraseSaysTheWindFell() {
        val hours = night(Triple(1.5, 22, "O"), Triple(1.5, 22, "O"), Triple(1.5, 12, "O"), Triple(1.5, 3, "E"), Triple(1.5, 3, "E"), Triple(1.5, 3, "E"))
        val p = nightPhrase(hours, 275)!!
        assertEquals(true, p.contains("au lever, pas de vent") || p.contains("le vent est tombé"))
    }

    @Test
    fun nightPhraseNeedsEnoughHours() {
        assertNull(nightPhrase(night(Triple(1.0, 5, "E")), 275))
    }

    @Test
    fun nightAfterMidnightWorksWithoutYesterdaysSunTimes() {
        val sunToday = DailySunInfo(sunrise = LocalTime(8, 15), sunset = LocalTime(19, 24))
        val date = kotlinx.datetime.LocalDate(2026, 10, 13)
        val hours = (0..6).map { h ->
            HourlyUiModel(
                timeFormatted = "$h h", rawTime = LocalDateTime(2026, 10, 13, h, 0), waveHeight = 1.4, wavePeriod = 11.0, waveDirection = 280f,
                energyKj = 300, windSpeedKmh = 8, windDirectionStr = "E", weatherCode = 0, temperature = 12, windWaveHeight = 0.0
            )
        }
        val night = nightHours(hours, mapOf(date to sunToday), LocalDateTime(2026, 10, 13, 0, 17))
        assertEquals(7, night.size)
    }
}
