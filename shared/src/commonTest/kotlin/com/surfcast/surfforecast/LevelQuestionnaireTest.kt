package com.surfcast.surfforecast

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Questionnaire de niveau : mêmes parcours que la maquette docs/maquettes/profil-questions.html.
class LevelQuestionnaireTest {

    private val form = LevelAnswers(feel = 0, training = 1, where = listOf(1, 3))

    private fun large(barre: Int, takeOff: Int, peak: Int, goals: List<Int>, man: Int? = null) =
        form.copy(what = 2, barre = barre, takeOff = takeOff, peak = peak, goals = goals, maneuvers = man)

    @Test
    fun foamOnlyIsBeginner() {
        val r = computeLevel(LevelAnswers(what = 0, takeOff = 2, wantLarge = 1))
        assertEquals("Débutant", r.name)
        assertEquals("beginner", r.levelKey)
        assertEquals(16, r.score)
    }

    @Test
    fun foamWithSureTakeOffAndWishForLargeIsBeginnerPlus() {
        val r = computeLevel(LevelAnswers(what = 0, takeOff = 0, wantLarge = 0))
        assertEquals("Débutant +", r.name)
        assertEquals("beginner+", r.levelKey)
        assertEquals(19, r.score)
    }

    @Test
    fun foamNeverGoesAboveBeginnerPlus() {
        assertEquals("Débutant", computeLevel(LevelAnswers(what = 1, takeOff = 2, wantLarge = 1)).name)
        assertEquals("Débutant", computeLevel(LevelAnswers(what = 0, takeOff = 1, wantLarge = 0)).name)
    }

    @Test
    fun levelsFollowTheLowestStep() {
        assertEquals("Intermédiaire", computeLevel(large(2, 1, 0, listOf(0))).name)
        assertEquals("Confirmé", computeLevel(large(4, 0, 1, listOf(1))).name)
        assertEquals("Avancé", computeLevel(large(4, 0, 2, listOf(2), man = 2)).name)
        assertEquals("Expert", computeLevel(large(5, 0, 2, listOf(3), man = 3)).name)
    }

    @Test
    fun oneHoldingBackPointGivesAPlus() {
        // Seul le take-off retient : Débutant +.
        assertEquals("Débutant +", computeLevel(large(4, 2, 2, listOf(2), man = 3)).name)
        // Veut être radical mais les manœuvres ne sont pas replaquées : Confirmé +.
        assertEquals("Confirmé +", computeLevel(large(5, 0, 2, listOf(2), man = 0)).name)
        // Vise l'expert avec des manœuvres presque replaquées : Avancé +.
        assertEquals("Avancé +", computeLevel(large(5, 0, 2, listOf(3), man = 2)).name)
    }

    @Test
    fun maneuversQuestionShowsOnlyForRadicalGoals() {
        assertFalse(showManeuvers(LevelAnswers(what = 2, goals = listOf(0, 1))))
        assertTrue(showManeuvers(LevelAnswers(what = 2, goals = listOf(1, 2))))
        assertFalse(showManeuvers(LevelAnswers(what = 0, goals = listOf(3))))
    }

    @Test
    fun expertScoreFillsItsBandWithTheFormAnswers() {
        val low = computeLevel(large(5, 0, 2, listOf(3), man = 3).copy(feel = 2, training = 0, where = listOf(0)))
        val high = computeLevel(large(5, 0, 2, listOf(3), man = 3).copy(feel = 0, training = 2, where = listOf(3)))
        assertTrue(low.score in 84..100 && high.score in 84..100)
        assertTrue(high.score > low.score)
        assertEquals(100, high.score)
    }

    @Test
    fun extraGoalsNeverLowerTheScore() {
        val one = computeLevel(large(4, 0, 2, listOf(2), man = 2))
        val two = computeLevel(large(4, 0, 2, listOf(1, 2), man = 2))
        assertTrue(two.score >= one.score)
    }

    @Test
    fun scoresStayInsideTheirBand() {
        val r = computeLevel(large(4, 0, 2, listOf(2), man = 2))
        val b = LEVEL_BANDS[r.bandIndex]
        assertTrue(r.score in b.from..b.to)
    }

    @Test
    fun shiftMovesOneBandAndIsClamped() {
        val base = computeLevel(large(4, 0, 2, listOf(2), man = 2))
        assertEquals("Avancé +", computeLevel(large(4, 0, 2, listOf(2), man = 2), shift = 1).name)
        assertEquals("Confirmé +", computeLevel(large(4, 0, 2, listOf(2), man = 2), shift = -1).name)
        assertEquals("Expert", computeLevel(large(5, 0, 2, listOf(3), man = 3), shift = 1).name)
        assertEquals("Débutant", computeLevel(LevelAnswers(what = 0, takeOff = 2), shift = -3).name)
        assertEquals(base.level, 3)
    }

    @Test
    fun partialAnswersDoNotCrash() {
        assertEquals("Expert", computeLevel(LevelAnswers()).name)
        // Une seule réponse : elle seule retient, d'où le « + ».
        val r = computeLevel(LevelAnswers(what = 2, barre = 3))
        assertEquals("Confirmé +", r.name)
    }

    @Test
    fun holdBackNamesTheBindingAnswers() {
        val r = computeLevel(large(4, 2, 2, listOf(2), man = 3))
        assertEquals(listOf("ton take-off"), r.holdBack)
        assertEquals("Intermédiaire", r.nextName)
    }

    @Test
    fun advancedProfileSitsBetweenConfirmedAndExpert() {
        val c = SurfProfile.fromLevel("confirmed")
        val a = SurfProfile.fromLevel("advanced")
        val e = SurfProfile.fromLevel("expert")
        assertTrue(a.idealMax > c.idealMax && a.idealMax < e.idealMax)
        assertTrue(a.cap > c.cap)
        assertTrue(a.windTolerance < c.windTolerance && a.windTolerance > e.windTolerance)
    }

    @Test
    fun plusLevelsBlendTowardsTheNextStep() {
        val c = SurfProfile.fromLevel("confirmed")
        val cPlus = SurfProfile.fromLevel("confirmed+")
        val a = SurfProfile.fromLevel("advanced")
        assertTrue(cPlus.idealMax > c.idealMax && cPlus.idealMax < a.idealMax)
        val aPlus = SurfProfile.fromLevel("advanced+")
        assertTrue(aPlus.hasCap && aPlus.cap > a.cap)
        assertEquals(SurfProfile.preset("beginner").beginnerExtras, SurfProfile.fromLevel("beginner+").beginnerExtras)
    }

    @Test
    fun everyResultKeyIsAKnownProfile() {
        for (shift in -8..8) {
            val key = computeLevel(large(4, 0, 2, listOf(2), man = 2), shift).levelKey
            assertEquals(SurfProfile.fromLevel(key), SurfProfile.fromLevel(key))
            assertTrue(key.removeSuffix("+") in LEVEL_KEYS)
        }
    }

    @Test
    fun everyBandHasItsOwnAdviceAndTheTopHasNone() {
        val advices = (0..8).map { shift -> computeLevel(large(4, 0, 2, listOf(2), man = 2), shift - 6).advice }
        // Les neuf tranches, de Débutant à Expert : huit conseils différents, aucun au sommet.
        val first8 = advices.take(8)
        assertEquals(8, first8.filterNotNull().toSet().size)
        assertEquals(null, computeLevel(large(5, 0, 2, listOf(3), man = 3)).advice)
    }
}
