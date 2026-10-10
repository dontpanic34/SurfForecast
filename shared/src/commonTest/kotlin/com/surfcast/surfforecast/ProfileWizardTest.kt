package com.surfcast.surfforecast

import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Assistant de profil : réponses aux exemples, profil sérialisé, libellés de plage de vagues.
class ProfileWizardTest {

    private fun hour(h: Int, height: Double = 1.0, period: Double = 10.0, windKmh: Int = 5, windDir: String = "E") = HourlyUiModel(
        timeFormatted = "${h}h",
        rawTime = LocalDateTime(2026, 1, 1, h, 0),
        waveHeight = height,
        wavePeriod = period,
        waveDirection = 270f,
        energyKj = calculateWaveEnergyReal(height, period),
        windSpeedKmh = windKmh,
        windDirectionStr = windDir,
        weatherCode = 0,
        temperature = 18,
        windWaveHeight = 0.0,
        windGustKmh = windKmh
    )

    private fun autoAnswers(level: String): List<Int> =
        PROFILE_EXAMPLES.map { autoExampleAnswer(calculateSlotRating(it.hour, 270, level)) }

    @Test
    fun answersRoundTripAndIgnoreGarbage() {
        assertEquals(mapOf(2 to 0, 5 to 2), parseExampleAnswers("2:0,5:2"))
        assertEquals("2:0,5:2", serializeExampleAnswers(mapOf(5 to 2, 2 to 0)))
        // Indice ou réponse hors limites, texte invalide : ignorés.
        assertEquals(mapOf(3 to 1), parseExampleAnswers("9:9,abc,3:1,10:1,4:7"))
        assertEquals(emptyMap(), parseExampleAnswers(""))
    }

    @Test
    fun thereAreTenExamplesAndEightRealDays() {
        assertEquals(10, PROFILE_EXAMPLES.size)
        assertEquals(8, PROFILE_DAYS.size)
        assertEquals(5, INTRO_DAYS.size)
        // Les journées vont de la plus petite à la plus grosse.
        assertEquals("Très petit jour", PROFILE_DAYS.first().title)
        assertEquals("Gros jour propre", PROFILE_DAYS.last().title)
    }

    @Test
    fun theAppAnswersDependOnTheLevel() {
        // 0 = J'y vais pas, 1 = Pas mal, 2 = Parfait, 3 = Challengeant, 4 = Trop gros.
        assertEquals(listOf(2, 1, 3, 1, 3, 3, 4, 4, 4, 4), autoAnswers("beginner"))
        assertEquals(listOf(0, 0, 2, 0, 3, 2, 3, 3, 4, 4), autoAnswers("intermediate"))
        assertEquals(listOf(0, 0, 2, 0, 2, 1, 3, 3, 3, 3), autoAnswers("confirmed"))
        assertEquals(listOf(0, 0, 2, 0, 2, 1, 2, 2, 3, 2), autoAnswers("expert"))
    }

    @Test
    fun everyLevelSeesAChallengingWave() {
        listOf("beginner", "intermediate", "confirmed", "expert").forEach { level ->
            assertTrue(autoAnswers(level).any { it == 3 }, "aucune vague challengeante pour $level")
        }
    }

    @Test
    fun noAnswerChangedKeepsTheProfileAsIs() {
        val base = SurfProfile.preset("intermediate")
        assertEquals(base, applyExampleAnswers(base, emptyMap()))
        assertEquals(base, applyExampleAnswers(base.copy(scoreOffset = 30, comfortScale = 0.7), emptyMap()))
    }

    @Test
    fun beingStricterThanTheAppLowersEveryScore() {
        val base = SurfProfile.preset("intermediate")
        // Exemple 2 : l'appli dit « Parfait » (2) ; l'utilisateur dit « Pas mal » (1) : un niveau plus exigeant.
        val stricter = applyExampleAnswers(base, mapOf(2 to 1))
        assertEquals(-15, stricter.scoreOffset)
        assertEquals(1.0, stricter.comfortScale)
        val day = hour(10, height = 1.0, period = 10.0)
        val before = calculateSlotScore(day, 270, base.serialize(), false)
        val after = calculateSlotScore(day, 270, stricter.serialize(), false)
        assertEquals(before - 15, after)
        // Plus généreux : les notes montent, mais jamais au-delà de 100.
        val generous = applyExampleAnswers(base, mapOf(3 to 1))
        assertTrue(generous.scoreOffset > 0, "offset ${generous.scoreOffset}")
    }

    @Test
    fun sayingChallengingLessOftenRaisesTheComfortThreshold() {
        val base = SurfProfile.preset("intermediate")
        // Exemple 4 : l'appli dit « Challengeant » (3) ; l'utilisateur dit « Parfait » (2) : sa zone de confort est plus grande.
        val roomier = applyExampleAnswers(base, mapOf(4 to 2))
        assertEquals(0, roomier.scoreOffset)
        assertTrue(roomier.comfortScale > 1.0, "confort x${roomier.comfortScale}")
        // L'inverse : plus de vagues challengeantes.
        val tighter = applyExampleAnswers(base, mapOf(2 to 3))
        assertTrue(tighter.comfortScale < 1.0, "confort x${tighter.comfortScale}")
    }

    @Test
    fun personalNotesMoveTheChallengeThreshold() {
        // 1,35 m à 10 s offshore : challengeant pour un intermédiaire (seuil de confort 340 kJ).
        val wave = PROFILE_EXAMPLES[4].hour
        assertEquals(ConditionKind.CHALLENGING, calculateSlotRating(wave, 270, "intermediate").kind)
        val roomier = SurfProfile.preset("intermediate").copy(comfortScale = 1.5).serialize()
        assertEquals(ConditionKind.NORMAL, calculateSlotRating(wave, 270, roomier).kind)
    }

    @Test
    fun customProfileKeepsTheNewSettingsThroughSerialization() {
        val profile = SurfProfile.preset("confirmed").copy(countGusts = false, scoreOffset = -15, comfortScale = 1.3)
        assertEquals(profile, SurfProfile.fromLevel(profile.serialize()))
        // Ancien format à 13 nombres (avant les notes perso) : valeurs par défaut pour les nouveaux réglages.
        val legacy = SurfProfile.fromLevel("custom:80.0;500.0;700.0;0.85;250.0;7.0;25.0;8.0;25.0;0.2;0.0;1.0;1.0")
        assertEquals(true, legacy.countGusts)
        assertEquals(0, legacy.scoreOffset)
        assertEquals(1.0, legacy.comfortScale)
    }

    @Test
    fun gustsCanBeLeftOutOfTheNote() {
        val gusty = hour(10, height = 1.2, period = 11.0, windKmh = 12).copy(windGustKmh = 40)
        val counted = SurfProfile.preset("confirmed").serialize()
        val ignored = SurfProfile.preset("confirmed").copy(countGusts = false).serialize()
        assertTrue(calculateSlotScore(gusty, 275, ignored, false) > calculateSlotScore(gusty, 275, counted, false))
    }

    @Test
    fun waveRangeIsReadInMetersAndSeconds() {
        assertEquals("0,5 m · 8 s  →  1,3 m · 10 s", waveRangeText(SurfProfile.preset("beginner")))
        assertEquals("0,8 m · 9 s  →  1,7 m · 10 s", waveRangeText(SurfProfile.preset("intermediate")))
        assertEquals("0,8 m · 9 s  →  2,0 m · 11 s", waveRangeText(SurfProfile.preset("confirmed")))
        assertEquals("0,8 m · 9 s  →  aucune limite", waveRangeText(SurfProfile.preset("expert")))
    }

    @Test
    fun aSavedCustomProfileIsReadBackInMetersAndSeconds() {
        val custom = SurfProfile.preset("intermediate").copy(idealMin = waveEnergyKj(0.9, 9.0), cap = waveEnergyKj(1.7, 10.0))
        assertEquals("0,9 m · 9 s  →  1,7 m · 10 s", waveRangeText(custom))
        val big = SurfProfile.preset("confirmed").copy(idealMin = waveEnergyKj(1.2, 10.0), cap = waveEnergyKj(2.5, 12.0))
        assertEquals("1,2 m · 10 s  →  2,5 m · 12 s", waveRangeText(big))
    }

    @Test
    fun bestRatingOfTheDayIsTheBestHour() {
        val hours = listOf(
            hour(8, windKmh = 30, windDir = "O"),
            hour(9, height = 1.15),
            hour(10)
        )
        val best = bestRatingOfDay(hours, 270, "intermediate")
        assertNotNull(best)
        assertEquals(100, best.score)
        assertNull(bestRatingOfDay(emptyList(), 270, "intermediate"))
        // Tout est trop gros : on renvoie « trop gros ».
        val huge = bestRatingOfDay(listOf(hour(9, height = 4.0, period = 14.0)), 270, "beginner")
        assertNotNull(huge)
        assertTrue(huge.tooBig)
    }

    @Test
    fun hollowOffshoreIsMediocreAndChallengingNotTooBig() {
        // 2 m à 12 s offshore : pas plus gros que le maximum d'un confirmé, mais trop creux pour lui.
        val wave = PROFILE_DAYS.last().hour
        val rating = calculateSlotRating(wave, 270, "confirmed")
        assertTrue(rating.hollow)
        assertEquals(ConditionKind.CHALLENGING, rating.kind)
        assertEquals(30, rating.score)
        assertEquals(ScoreBand.CHALLENGING, conditionBand(rating))
        // Un expert surfe la même vague sans que ce soit « trop creux ».
        val expert = calculateSlotRating(wave, 270, "expert")
        assertEquals(false, expert.hollow)
        assertEquals(ConditionKind.CHALLENGING, expert.kind)
        assertTrue(expert.score >= 70)
    }

    @Test
    fun biggerWavesTolerateMoreOnshore() {
        // 16 km/h de vent de mer : gênant à 1,2 m, presque sans effet à 1,9 m (les faces sont hautes).
        val small = hour(10, height = 1.2, period = 10.0, windKmh = 16, windDir = "O")
        val big = hour(10, height = 1.9, period = 10.0, windKmh = 16, windDir = "O")
        val smallRating = calculateSlotRating(small, 270, "confirmed")
        val bigRating = calculateSlotRating(big, 270, "confirmed")
        assertTrue(bigRating.score > smallRating.score, "grosse ${bigRating.score} contre petite ${smallRating.score}")
    }

    @Test
    fun everyConditionHasASentence() {
        PROFILE_DAYS.forEach { day ->
            listOf("beginner", "intermediate", "confirmed", "expert").forEach { level ->
                val rating = calculateSlotRating(day.hour, 270, level)
                assertTrue(rating.why.isNotBlank(), "phrase vide pour ${day.title} ($level)")
            }
        }
    }
}
