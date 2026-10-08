package com.surfcast.surfforecast

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BoardVolumeTest {

    @Test
    fun parsesDecimalsAndFractions() {
        assertEquals(19.0, parseInches("19"))
        assertEquals(2.15, parseInches("2,15"))
        assertEquals(2.38, parseInches("2.38"))
        assertEquals(2.3125, parseInches("2 5/16"))
        assertEquals(19.25, parseInches("19 1/4"))
        assertEquals(0.625, parseInches("5/8"))
        assertEquals(0.0, parseInches("  "))
        assertNull(parseInches("abc"))
        assertNull(parseInches("1/0"))
        assertNull(parseInches("1 2 3"))
    }

    @Test
    fun estimatesMatchAManufacturerVolumeChart() {
        // Grille d'un modèle réel (longueur, largeur, épaisseur -> volume annoncé) : estimation à ±1 L.
        val chart = listOf(
            Triple(Triple(5.0, 4.0, 19.875), 2.3125, 27.6),
            Triple(Triple(5.0, 8.0, 20.375), 2.5, 32.5),
            Triple(Triple(6.0, 0.0, 21.25), 2.75, 39.4),
            Triple(Triple(6.0, 4.0, 21.75), 2.875, 44.4)
        )
        chart.forEach { (dims, thickness, expected) ->
            val volume = estimateVolumeL(dims.first, dims.second, dims.third, thickness)
            assertNotNull(volume)
            assertEquals(expected, volume, 1.0, "${dims.first}'${dims.second}")
        }
        assertNull(estimateVolumeL(0.0, 0.0, 19.0, 2.3))
    }

    @Test
    fun ratioAndLevelFollowTheReferenceTable() {
        assertEquals(0.4, volumeRatio(30.0, 75))
        assertNull(volumeRatio(30.0, 0))
        assertNull(volumeRatio(null, 75))
        assertEquals("beginner", levelForRatio(0.75))
        assertEquals("intermediate", levelForRatio(0.5))
        assertEquals("confirmed", levelForRatio(0.4))
        assertEquals("expert", levelForRatio(0.33))
    }

    @Test
    fun summaryShowsApproximationOnlyForEstimatedVolumes() {
        val estimated = QuiverBoard(model = "A", family = "shortboard", lengthLitrage = "", finSetup = "", volumeL = 31.1, volumeEstimated = true)
        val written = estimated.copy(volumeEstimated = false)
        assertEquals("≈ 31,1 L · 0,41 L/kg", boardVolumeSummary(estimated, 75))
        assertEquals("31,1 L", boardVolumeSummary(written, 0))
        assertNull(boardVolumeSummary(estimated.copy(volumeL = null), 75))
    }

    @Test
    fun dimensionsAreKeptAsTyped() {
        assertEquals("6'0 x 19 x 2 5/16", formatDimensions("6", "0", "19", "2 5/16"))
        assertEquals("5'8 x 19 1/4 x 2,38", formatDimensions(" 5 ", "8", "19 1/4", "2,38"))
    }

    @Test
    fun energyCalculatorMatchesTheScoringFormula() {
        // 1,5 m à 10 s ≈ 441 kJ ; 0,8 m à 9 s ≈ 102.
        assertEquals(441.45, waveEnergyKj(1.5, 10.0), 0.01)
        assertEquals(101.7, waveEnergyKj(0.8, 9.0), 0.1)
        // L'inverse retrouve la hauteur.
        assertEquals(1.5, heightForEnergy(waveEnergyKj(1.5, 10.0), 10.0), 1e-9)
    }
}
