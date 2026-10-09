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
        assertEquals("confirmed", levelForRatio(0.39))
        assertEquals("expert", levelForRatio(0.33))
        // Xero Gravity 31,5 L pour 81 kg (≈ 0,39 L/kg) : un ratio de confirmé.
        assertEquals("confirmed", levelForRatio(31.5 / 81))
        val range = volumeRangeFor("confirmed", 80)
        assertEquals(28.0, range.start, 0.01)
        assertEquals(34.4, range.endInclusive, 0.01)
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

    @Test
    fun recommendedVolumeMatchesTheShopReferenceExample() {
        // 81 kg, 40 ans, forme excellente, Confirmé (index 4), planche courte : 30,62 L, plage 29,87 à 31,39.
        val body = BodyState(ageYears = 40, weightKg = 81, fitness = 0)
        val rec = recommendedVolumeL(body, 4)
        assertNotNull(rec)
        assertEquals(30.62, rec, 0.01)
        val range = recommendedRange(rec)
        assertEquals(29.85, range.start, 0.05)
        assertEquals(31.38, range.endInclusive, 0.05)
        assertNull(recommendedVolumeL(BodyState(ageYears = 40), 4))
        // Plus âgé, moins en forme, planche longue : plus de volume.
        val older = recommendedVolumeL(BodyState(ageYears = 55, weightKg = 81, fitness = 2), 4, family = "longboard")
        assertNotNull(older)
        assertTrue(older > rec * 1.5)
    }

    @Test
    fun tableAndDefaultsFollowTheLevels() {
        assertEquals(54.4, volumeTableValue(0, 80), 0.1)
        assertEquals(43.2, volumeTableValue(1, 80), 0.1)
        assertEquals(33.6, volumeTableValue(2, 80), 0.1)
        assertEquals(28.0, volumeTableValue(4, 80), 0.1)
        assertEquals(2, BodyState().levelIndex("intermediate"))
        assertEquals(4, BodyState().levelIndex("confirmed"))
        assertEquals(1, BodyState(volumeLevel = 1).levelIndex("expert"))
        // Surfeur léger : plus de litres par kilo.
        assertTrue(volumeTableValue(4, 40) / 40 > volumeTableValue(4, 80) / 80)
    }

    @Test
    fun sliderDimensionsAreWrittenLikeOnABoard() {
        assertEquals("20 3/8", formatInchesFraction(163, 8))
        assertEquals("2 1/2", formatInchesFraction(40, 16))
        assertEquals("2 5/16", formatInchesFraction(37, 16))
        assertEquals("20", formatInchesFraction(160, 8))
        assertEquals("5'8", formatLengthFeet(68))
        assertEquals("5'8 x 20 3/8 x 2 1/2", formatSliderDimensions(68, 163, 40))
        // Les cotes de ma planche écrites comme sur l'étiquette se relisent à l'identique.
        assertEquals(2.3125, parseInches(formatInchesFraction(37, 16)))
    }

    @Test
    fun fuller_boards_have_a_higher_fill_factor() {
        assertTrue(fillFactorFor("longboard") > fillFactorFor("shortboard"))
        assertTrue(fillFactorFor("fish") > fillFactorFor("shortboard"))
        assertTrue(isLongFamily("mid-length") && !isLongFamily("shortboard"))
    }

    @Test
    fun volumeRangeIsWholeLitresPlusOrMinusFivePercent() {
        assertEquals("27–29", volumeRangeText(28.0))
        assertEquals("31–34", volumeRangeText(32.15))
        assertEquals("52–57", volumeRangeText(54.4))
    }

    @Test
    fun recommendedVolumeDependsOnTheBoardType() {
        // 61 kg, 34 ans, forme moyenne, intermédiaire : environ 30 L en shortboard, bien plus en mid-length.
        val body = BodyState(ageYears = 34, weightKg = 61, fitness = 2)
        val short = recommendedVolumeL(body, 2, "shortboard")!!
        val mid = recommendedVolumeL(body, 2, "mid-length")!!
        val long = recommendedVolumeL(body, 2, "longboard")!!
        assertTrue(short in 28.0..33.0, "shortboard : $short")
        assertTrue(mid in 44.0..54.0, "mid-length : $mid")
        assertTrue(long > mid)
    }
}
