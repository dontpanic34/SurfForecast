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
    fun ratioIsLitresPerKilo() {
        assertEquals(0.4, volumeRatio(30.0, 75))
        assertNull(volumeRatio(30.0, 0))
        assertNull(volumeRatio(null, 75))
    }

    @Test
    fun shortboardVolumeMatchesProPilotsOfJsIndustries() {
        // Fiches « team rider reference » (expert, 25 ans, excellente forme) : tolérance de 8 %.
        val pros = listOf(59 to 24.5, 64 to 24.6, 70 to 25.6, 75 to 27.0, 80 to 28.6, 83 to 28.3)
        for ((kg, ref) in pros) {
            val r = recommendedVolumeRange(BodyState(ageYears = 25, weightKg = kg, fitness = 0), 4)!!
            val mid = (r.start + r.endInclusive) / 2
            assertTrue(mid in ref * 0.92..ref * 1.08, "$kg kg : $mid L pour $ref L")
        }
    }

    @Test
    fun ownerCaseFallsInsideTheRange() {
        // Avancé +, 34 ans, 81 kg, forme bonne : sa Xero Gravity de 31,5 L est dans la fourchette.
        val r = recommendedVolumeRange(BodyState(ageYears = 34, weightKg = 81, fitness = 1), volumeLevelIndex("advanced+"))!!
        assertTrue(31.5 in r, "fourchette $r")
        // Sa planche groveler à 32,5 L est dans la fourchette fish, twin, groveler.
        val fish = recommendedVolumeRange(BodyState(ageYears = 34, weightKg = 81, fitness = 1), 3, "groveler")!!
        assertTrue(32.5 in fish, "fourchette $fish")
    }

    @Test
    fun fishAndTwinAreSlightlyBiggerThanShortboards() {
        val body = BodyState(ageYears = 30, weightKg = 80, fitness = 1)
        val short = recommendedVolumeL(body, 3)!!
        val fish = recommendedVolumeL(body, 3, "fish")!!
        assertTrue(fish - short in 1.0..2.0, "écart ${fish - short}")
    }

    @Test
    fun longBoardsDoNotDependOnTheLevel() {
        val body = BodyState(ageYears = 30, weightKg = 78, fitness = 1)
        assertEquals(recommendedVolumeRange(body, 0, "mid-length"), recommendedVolumeRange(body, 4, "mid-length"))
        assertEquals(55.0..80.0, recommendedVolumeRange(body, 2, "longboard"))
        assertNull(recommendedVolumeRange(body, 2, "mousse"))
        assertEquals("55 à 80 L et plus", volumeRangeLabel(55.0..80.0, "longboard"))
        val mid = recommendedVolumeRange(body, 3, "mid-length")!!
        assertEquals(43.0, mid.start, 0.1)
        assertEquals(62.4, mid.endInclusive, 0.1)
    }

    @Test
    fun noWeightMeansNoRecommendation() {
        assertNull(recommendedVolumeRange(BodyState(ageYears = 40), 3))
        assertNull(recommendedVolumeL(BodyState(), 3))
    }

    @Test
    fun ageIsOptionalAndGentle() {
        val noAge = recommendedVolumeL(BodyState(weightKg = 80, fitness = 1), 3)!!
        val young = recommendedVolumeL(BodyState(ageYears = 30, weightKg = 80, fitness = 1), 3)!!
        val old = recommendedVolumeL(BodyState(ageYears = 55, weightKg = 80, fitness = 1), 3)!!
        assertEquals(noAge, young, 1e-9)
        assertTrue(old > young && old < young * 1.1)
    }

    @Test
    fun lightSurfersGetMoreLitresPerKilo() {
        assertTrue(weightFactor(59.0) > weightFactor(70.0))
        assertEquals(1.0, weightFactor(85.0), 1e-9)
        assertEquals(1.0, weightFactor(95.0), 1e-9)
        assertEquals(1.2, weightFactor(55.0), 1e-9)
    }

    @Test
    fun levelAndFitnessMapToTheTables() {
        assertEquals(3, volumeLevelIndex("advanced"))
        assertEquals(3, volumeLevelIndex("advanced+"))
        assertEquals(1, volumeLevelIndex("custom:1;2;3"))
        assertEquals(2, fitnessFromTraining(0))
        assertEquals(1, fitnessFromTraining(null))
        assertEquals(0, fitnessFromTraining(2))
    }
}
