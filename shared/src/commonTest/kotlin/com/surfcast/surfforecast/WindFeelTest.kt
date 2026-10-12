package com.surfcast.surfforecast

import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** Le vent ressenti : un seul chiffre (moyen + 35 % de l'écart avec les rafales), la même règle pour la couleur, la phrase et la note. */
class WindFeelTest {
    private fun hour(h: Int, windKmh: Int, windDir: String, gustKmh: Int = windKmh) = HourlyUiModel(
        timeFormatted = "$h h", rawTime = LocalDateTime(2026, 10, 11, h, 0), waveHeight = 1.0, wavePeriod = 10.0, waveDirection = 270f,
        energyKj = 196, windSpeedKmh = windKmh, windDirectionStr = windDir, weatherCode = 0, temperature = 18,
        windWaveHeight = 0.0, windGustKmh = gustKmh
    )

    @Test
    fun feelWindIsMeanPlus35PercentOfTheGustGap() {
        assertEquals(13, windFeelKmh(hour(14, 8, "SSE", 22)))
        assertEquals(8, windFeelKmh(hour(14, 8, "SSE", 8)))
    }

    @Test
    fun colorFollowsTheSameRuleAsThePhrase() {
        // Vent de côté léger : propre. Vent de mer 10 km/h : gêne. Vent de mer 20 km/h : fort.
        assertEquals(SurfUnitsHelper.WIND_YELLOW, windFeelColor(hour(14, 8, "SO"), 275, "intermediate"))
        assertEquals(SurfUnitsHelper.WIND_ORANGE, windFeelColor(hour(14, 10, "O"), 275, "intermediate"))
        assertEquals(SurfUnitsHelper.WIND_RED, windFeelColor(hour(14, 20, "O"), 275, "intermediate"))
    }

    @Test
    fun lightSideWindIsCleanNotBothering() {
        // SSE 8 km/h (cas de la capture) : plus de « vent de côté qui gêne ».
        assertEquals("Bonne taille, vent propre", calculateSlotRating(hour(13, 8, "SSE", 14), 275, "intermediate").why)
    }

    @Test
    fun memoryOfThePreviousWindIsSaidInThePhrase() {
        // 15 h : vent de mer 14 km/h (rafales 20) ; 16 h : vent de terre 3 km/h. La note garde le vent d'avant, l'écran affiche le réel.
        val h15 = hour(15, 14, "O", 20)
        val h16 = hour(16, 3, "E", 6)
        val kept = windMemoryAdjusted(listOf(h15, h16))[1]
        assertNotEquals(h16.windSpeedKmh, kept.windSpeedKmh)
        assertEquals(WHY_WIND_MEMORY, calculateSlotRating(kept, 275, "intermediate", displayedWind = h16).why)
        // Sans l'heure affichée, la phrase décrit le vent retenu.
        assertNotEquals(WHY_WIND_MEMORY, calculateSlotRating(kept, 275, "intermediate").why)
    }

    @Test
    fun perfectNeedsThreeCleanHoursBefore() {
        // Vent de mer 18 km/h à 17 h, 18 h et 19 h, puis 10 km/h de terre à 20 h : la note de 20 h ne peut pas être « Parfait ».
        val hours = listOf(hour(16, 4, "E"), hour(17, 18, "O", 24), hour(18, 18, "O", 24), hour(19, 18, "O", 24), hour(20, 4, "E"))
        val kept = windMemoryAdjusted(hours)[4]
        assertEquals(3, kept.pastWind.size)
        assertEquals(true, calculateSlotRating(kept, 275, "intermediate").score < PERFECT_MIN)
        // Sans vent avant, la même heure peut monter plus haut.
        val clean = windMemoryAdjusted(listOf(hour(17, 4, "E"), hour(18, 4, "E"), hour(19, 4, "E"), hour(20, 4, "E")))[3]
        assertEquals(true, calculateSlotRating(clean, 275, "intermediate").score >= calculateSlotRating(kept, 275, "intermediate").score)
    }
}
