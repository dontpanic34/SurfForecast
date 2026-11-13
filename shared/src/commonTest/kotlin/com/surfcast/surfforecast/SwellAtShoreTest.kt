package com.surfcast.surfforecast

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SwellAtShoreTest {

    private fun near(expected: Double, actual: Double, tol: Double = 0.05) =
        assertTrue(abs(expected - actual) <= tol, "attendu $expected, obtenu $actual")

    @Test
    fun montalivetCalibrationPoint() {
        // Repère du propriétaire : 1,3 m à 14 s au large = 1,6 m au bord à Montalivet (plage douce), soit taille d'homme.
        val r = SwellAtShoreCalc.compute(1.3, 14.0, ShoreType.GENTLE)
        near(1.6, r.seriesHeight, 0.001)
        assertEquals("Séries taille d'homme", r.headline)
    }

    @Test
    fun surferScaleFollowsTheOwnerRule() {
        // On surfe fléchi : 1,5 m = taille d'homme, 2 m = overhead.
        assertEquals("taille d'homme", SwellAtShoreCalc.bodyWord(1.5))
        assertEquals("overhead", SwellAtShoreCalc.bodyWord(2.0))
        assertEquals("à la poitrine", SwellAtShoreCalc.bodyWord(1.0))
        assertEquals("aux genoux", SwellAtShoreCalc.bodyWord(0.5))
        assertEquals("double overhead", SwellAtShoreCalc.bodyWord(3.0))
    }

    @Test
    fun longPeriodSmallSwellGivesRareSets() {
        // Petite houle très longue (0,3 m à 18 s) : séries très espacées, 6 à 10 vagues, 108 à 180 s, 29 kJ.
        val r = SwellAtShoreCalc.compute(0.3, 18.0, ShoreType.GENTLE)
        assertEquals(6..10, r.wavesPerSet)
        assertEquals(15..25, r.minutesBetweenSets)
        assertEquals(108..180, r.setDurationSeconds)
        assertEquals(29, r.energyKj)
        assertEquals("groundswell très long", r.swellName)
        assertTrue(r.feel.startsWith("Séries rares"))
        assertEquals("Séries aux genoux", r.headline)
    }

    @Test
    fun steeperSpotsGiveBiggerAndHollowerWaves() {
        val gentle = SwellAtShoreCalc.compute(1.0, 12.0, ShoreType.GENTLE)
        val reef = SwellAtShoreCalc.compute(1.0, 12.0, ShoreType.REEF)
        assertTrue(reef.seriesHeight > gentle.seriesHeight)
        assertEquals(BreakerShape.SPILLING, gentle.shape)
        assertEquals(BreakerShape.PLUNGING, reef.shape)
    }

    @Test
    fun spotTypeDefaultsAndSavedChoice() {
        assertEquals(ShoreType.GENTLE, SwellAtShoreCalc.defaultType("Montalivet"))
        assertEquals(ShoreType.BARS, SwellAtShoreCalc.defaultType("Hossegor"))
        val prefs = InMemoryKeyValueStore()
        ShoreSettings.store = prefs
        try {
            assertEquals(ShoreType.GENTLE, ShoreSettings.typeFor("Montalivet"))
            ShoreSettings.setType("Montalivet", ShoreType.BARS)
            assertEquals(ShoreType.BARS, ShoreSettings.typeFor("Montalivet"))
        } finally {
            ShoreSettings.store = null
        }
    }
}
