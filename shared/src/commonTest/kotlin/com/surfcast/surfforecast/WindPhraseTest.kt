package com.surfcast.surfforecast

import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** La phrase du vent dit sa force (léger, gêne, fort, très fort) avec les mêmes mots pour l'onshore et le sideshore. */
class WindPhraseTest {
    private fun hour(h: Int, windKmh: Int, windDir: String, gustKmh: Int = windKmh) = HourlyUiModel(
        timeFormatted = "$h h", rawTime = LocalDateTime(2026, 10, 17, h, 0), waveHeight = 1.0, wavePeriod = 12.0, waveDirection = 270f,
        energyKj = 300, windSpeedKmh = windKmh, windDirectionStr = windDir, weatherCode = 0, temperature = 18,
        windWaveHeight = 0.0, windGustKmh = gustKmh
    )

    @Test
    fun onshoreWordsFollowTheStrength() {
        assertEquals("Un peu d'onshore", windPhrase("onshore", 0.8))
        assertEquals("Onshore dégrade la vague", windPhrase("onshore", 0.65))
        assertEquals("Onshore fort, ça sectionne", windPhrase("onshore", 0.45))
        assertEquals("Onshore très fort, c'est démonté", windPhrase("onshore", 0.2))
    }

    @Test
    fun sideshoreUsesTheSameWords() {
        assertEquals("Un peu de side", windPhrase("side", 0.8))
        assertEquals("Side dégrade la vague", windPhrase("side", 0.65))
        assertEquals("Side fort, ça sectionne", windPhrase("side", 0.45))
        assertEquals("Side très fort, c'est démonté", windPhrase("side", 0.2))
    }

    @Test
    fun offshoreIsNamedByItsStrengthToo() {
        assertEquals("Offshore soutenu", windPhrase("offshore", 0.8))
        assertEquals("Offshore très fort", windPhrase("offshore", 0.1))
    }

    @Test
    fun thirtyOneKmhNorthEastIsNotJustAnnoying() {
        // Le cas vu sur le site : NE 31 km/h à Montalivet (plage vers l'ouest) disait seulement « abîme la vague ».
        val why = calculateSlotRating(hour(16, 31, "NE"), 275, "intermediate").why
        assertTrue(why.startsWith("Side-off fort") || why.startsWith("Side-off très fort"), "phrase : $why")
    }

    @Test
    fun noOldWordingRemains() {
        val phrases = listOf("onshore", "cross", "offshore").flatMap { c -> listOf(0.8, 0.65, 0.45, 0.2).map { windPhrase(c, it) } }
        assertTrue(phrases.none { it.contains("gâche") || it.contains("abîme") || it.contains("Vent de côté") })
    }
}
