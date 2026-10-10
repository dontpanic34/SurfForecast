package com.surfcast.surfforecast

import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// Valeurs attendues calculées avec un portage des formules de SurfScoring.kt (hauteur / période / vent / profil).
class SurfScoringTest {

    private fun hour(
        h: Int,
        height: Double = 1.0,
        period: Double = 10.0,
        waveDir: Float = 270f,
        windKmh: Int = 5,
        windDir: String = "E",
        gustKmh: Int = windKmh,
        chop: Double = 0.0
    ) = HourlyUiModel(
        timeFormatted = formatHour(LocalDateTime(2026, 1, 1, h, 0)),
        rawTime = LocalDateTime(2026, 1, 1, h, 0),
        waveHeight = height,
        wavePeriod = period,
        waveDirection = waveDir,
        energyKj = calculateWaveEnergyReal(height, period),
        windSpeedKmh = windKmh,
        windDirectionStr = windDir,
        weatherCode = 0,
        temperature = 18,
        windWaveHeight = chop,
        windGustKmh = gustKmh
    )

    @Test
    fun crossWindFromTwelveKmhIsClearlyPenalized() {
        val calm = calculateSlotScore(hour(10, windKmh = 4, windDir = "N"), 270, "confirmed", false)
        val cross12 = calculateSlotScore(hour(10, windKmh = 12, windDir = "N"), 270, "confirmed", false)
        assertTrue(cross12 < calm * 0.88, "side-shore 12 km/h : $cross12 contre $calm sans vent")
        assertEquals(86, calm)
        assertEquals(69, cross12)
    }

    @Test
    fun windMemoryKeepsTheSeaRoughAfterTheWindDrops() {
        val hours = listOf(
            hour(8, windKmh = 30, windDir = "O"),
            hour(9, windKmh = 5, windDir = "E"),
            hour(10, windKmh = 5, windDir = "E"),
            hour(11, windKmh = 5, windDir = "E")
        )
        val adjusted = windMemoryAdjusted(hours)
        assertEquals(30, adjusted[0].windSpeedKmh)
        assertEquals(23, adjusted[1].windSpeedKmh)   // 30 x 0,75 = 22,5 -> 23, avec la direction du vent de mer
        assertEquals("O", adjusted[1].windDirectionStr)
        assertEquals(17, adjusted[2].windSpeedKmh)
        assertEquals(13, adjusted[3].windSpeedKmh)
        // Une heure à 5 km/h juste après 30 km/h de vent de mer ne peut donc plus être « excellente ».
        val after = calculateSlotScore(adjusted[1], 270, "confirmed", false)
        val calm = calculateSlotScore(hour(9, windKmh = 5, windDir = "E"), 270, "confirmed", false)
        assertTrue(after < calm - 40, "juste après 30 km/h : $after contre $calm")
        assertEquals(29, after)
    }

    @Test
    fun windMemoryDoesNotChangeAStrongerNewWind() {
        val hours = listOf(hour(8, windKmh = 10, windDir = "O"), hour(9, windKmh = 25, windDir = "N"))
        val adjusted = windMemoryAdjusted(hours)
        assertEquals(25, adjusted[1].windSpeedKmh)
        assertEquals("N", adjusted[1].windDirectionStr)
    }

    @Test
    fun dayScoreIsHalfMorningHalfAfternoon() {
        val good = { it: Int -> hour(it, windKmh = 5, windDir = "E") }
        val bad = { it: Int -> hour(it, windKmh = 26, windDir = "O") }
        val whole = dayQualityScore((8..18).map(good), 270, "confirmed", null)!!
        val morningOnly = dayQualityScore((8..12).map(good) + (13..18).map(bad), 270, "confirmed", null)!!
        val afternoonOnly = dayQualityScore((8..12).map(bad) + (13..18).map(good), 270, "confirmed", null)!!
        assertTrue(whole >= 80, "journée propre : $whole")
        assertTrue(morningOnly in 35..60, "bon le matin seulement : $morningOnly")
        assertTrue(afternoonOnly in 35..60, "bon l'après-midi seulement : $afternoonOnly")
    }

    @Test
    fun dayScoreRewardsAWholeGoodDayOverASingleGoodHour() {
        val goodAllDay = (8..18).map { hour(it, windKmh = 5, windDir = "E") }
        val oneGoodHour = (8..18).map { if (it == 11) hour(it, windKmh = 5, windDir = "E") else hour(it, windKmh = 24, windDir = "O") }
        val whole = dayQualityScore(goodAllDay, 270, "confirmed", null)!!
        val single = dayQualityScore(oneGoodHour, 270, "confirmed", null)!!
        assertTrue(whole - single >= 40, "journée propre $whole contre une seule bonne heure $single")
    }

    @Test
    fun offshoreNightGivesAFewPointsAndNeedsEnoughHours() {
        val night = (0..5).map { hour(it, windKmh = 8, windDir = "E") }
        assertTrue(hasOffshoreNight(night, 270))
        assertFalse(hasOffshoreNight(night.take(3), 270))
        assertFalse(hasOffshoreNight((0..5).map { hour(it, windKmh = 8, windDir = "O") }, 270))
        val day = (8..18).map { hour(it, windKmh = 12, windDir = "N") }
        val base = dayQualityScore(day, 270, "confirmed", null, offshoreNight = false)!!
        assertEquals(base + 5, dayQualityScore(day, 270, "confirmed", null, offshoreNight = true))
    }

    @Test
    fun cleanModerateDayIsGoodForIntermediate() {
        // 1.962 x 1² x 10² = 196.2 kJ : au-dessus du minimum (100), pas encore à 2,5 fois (250) : la note monte
        // de 60 à 100 entre les deux, ici 0,6 + 0,4 x 96,2 / 150 = 0,857. Vent offshore faible -> x1.
        assertEquals(86, calculateSlotScore(hour(10), null, "intermediate", false))
        // Dès 250 kJ (1,13 m à 10 s), la note est maximale.
        assertEquals(100, calculateSlotScore(hour(10, height = 1.15), null, "intermediate", false))
    }

    @Test
    fun strongOnshoreWindOrSmallWavesScoreLow() {
        // 30 km/h de vent de mer : très mauvais (et « trop de vent »), mais plus à zéro.
        val storm = calculateSlotRating(hour(10, windKmh = 30, windDir = "O"), null, "intermediate", false)
        assertEquals(19, storm.score)
        assertEquals(ConditionKind.TOO_WINDY, storm.kind)
        // Une vague de 0,3 m ne vaut rien.
        val tiny = calculateSlotRating(hour(10, height = 0.3), null, "intermediate", false)
        assertEquals(0, tiny.score)
        assertEquals(ConditionKind.TOO_SMALL, tiny.kind)
    }

    @Test
    fun beginnerGetsChallengingAndThenTooBigEarly() {
        // 1,2 m à 10 s (283 kJ) : au-dessus du confort du débutant (200 kJ) mais sous son maximum (330) : challengeant.
        val medium = calculateSlotRating(hour(10, height = 1.2, period = 10.0), null, "beginner", false)
        assertEquals(ConditionKind.CHALLENGING, medium.kind)
        assertFalse(medium.tooBig)
        // Un peu plus gros : la note baisse progressivement avant « trop gros » (1,3 m à 10 s : 65).
        assertEquals(65, calculateSlotScore(hour(10, height = 1.3, period = 10.0), null, "beginner", false))
    }

    @Test
    fun swellDirectionAndCrossWind() {
        // Écart 60° -> cos = 0.5 -> 98,1 kJ (juste sous le minimum de 100 : note 59) ; vent de travers N 10 km/h ;
        // houle de travers : facteur 1 - 0.3 x 60/90 = 0.8 -> 42.
        assertEquals(42, calculateSlotScore(hour(10, waveDir = 330f, windKmh = 10, windDir = "N"), 270, "intermediate", false))
    }

    @Test
    fun bestSlotPicksThreeHourWindowWithFrenchRecap() {
        val hours = listOf(hour(8, height = 0.3), hour(9), hour(10), hour(11))
        val best = findBestSlot(hours, null, "intermediate", null)
        assertNotNull(best)
        assertEquals(9, best.startHour)
        assertEquals(11, best.endHour)
        assertEquals(86, best.averageScore)
        assertEquals("1,0m / 10s · vent léger offshore", best.recap)
    }

    @Test
    fun nearHighTideWithinOneHourIncludingMidnightWrap() {
        assertTrue(isNearHighTide(hour(11), DailyTideInfo(highTideTime = "10:30")))
        assertFalse(isNearHighTide(hour(12), DailyTideInfo(highTideTime = "10:30")))
        assertTrue(isNearHighTide(hour(0), DailyTideInfo(highTideTime = "23:50")))
        assertFalse(isNearHighTide(hour(0), null))
    }

    @Test
    fun scoreBandsHaveSixShadesPlusTooBig() {
        assertEquals(ScoreBand.AVOID, scoreBand(19))
        assertEquals(ScoreBand.POOR, scoreBand(20))
        assertEquals(ScoreBand.FAIR, scoreBand(40))
        assertEquals(ScoreBand.GOOD, scoreBand(55))
        assertEquals(ScoreBand.VERY_GOOD, scoreBand(70))
        assertEquals(ScoreBand.EXCELLENT, scoreBand(85))
        assertEquals(ScoreBand.TOO_BIG, scoreBand(-1))
        assertEquals(ScoreBand.TOO_BIG, scoreBand(50, tooBig = true))
        assertEquals("Parfait", ScoreBand.EXCELLENT.label)
        assertEquals("Mauvais", ScoreBand.AVOID.label)
    }

    @Test
    fun conditionBandFollowsTheReasonThenTheQuality() {
        assertEquals(ScoreBand.TOO_SMALL, conditionBand(SlotRating(10, false, ConditionKind.TOO_SMALL)))
        assertEquals(ScoreBand.TOO_WINDY, conditionBand(SlotRating(10, false, ConditionKind.TOO_WINDY)))
        assertEquals(ScoreBand.CHALLENGING, conditionBand(SlotRating(90, false, ConditionKind.CHALLENGING)))
        assertEquals(ScoreBand.TOO_BIG, conditionBand(SlotRating(0, true)))
        assertEquals(ScoreBand.GOOD, conditionBand(SlotRating(60, false)))
    }

    @Test
    fun labelsSayWhereTheScoreIsHeadedNearALimit() {
        assertEquals("Correct à Bon", bandLabelWithTrend(SlotRating(53, false)))
        assertEquals("Bon à Très bon", bandLabelWithTrend(SlotRating(71, false)))
        assertEquals("Très bon à Parfait", bandLabelWithTrend(SlotRating(83, false)))
        assertEquals("Médiocre à Correct", bandLabelWithTrend(SlotRating(38, false)))
        // Loin d'une limite : le niveau seul. Les cas à part n'ont pas de tendance.
        assertEquals("Bon", bandLabelWithTrend(SlotRating(62, false)))
        assertEquals("Challengeant", bandLabelWithTrend(SlotRating(54, false, ConditionKind.CHALLENGING)))
    }

    @Test
    fun bigDayIsTooBigForConfirmedButGoodForExpert() {
        val big = hour(10, height = 2.5, period = 14.0, windKmh = 10, windDir = "E")
        assertTrue(calculateSlotRating(big, 275, "confirmed", false).tooBig)
        val expert = calculateSlotRating(big, 275, "expert", false)
        assertFalse(expert.tooBig)
        assertTrue(expert.score >= 70, "2,5 m 14 s offshore pour un expert : ${expert.score}")
    }

    @Test
    fun veryGustyOffshoreIsBadForEveryone() {
        val gusty = hour(10, height = 1.2, period = 11.0, windKmh = 12, windDir = "E", gustKmh = 40)
        listOf("beginner", "intermediate", "confirmed").forEach { level ->
            assertTrue(calculateSlotScore(gusty, 275, level, false) <= 40, "rafales 40 ($level)")
        }
    }

    @Test
    fun lightOnshoreIsNearlyAsGoodAsOffshore() {
        val calmOnshore = calculateSlotScore(hour(10, windKmh = 6, windDir = "O"), 275, "intermediate", false)
        assertTrue(calmOnshore >= 70, "onshore faible : $calmOnshore")
    }

    // --- Orientation de la plage, rafales, clapot ---

    @Test
    fun windCategoryFollowsTheBeachOrientation() {
        // Plage plein ouest (275) : vent d'est = offshore, vent d'ouest = onshore, nord = travers.
        assertEquals("offshore", windCategoryFromDegrees(90f, 275))
        assertEquals("onshore", windCategoryFromDegrees(270f, 275))
        assertEquals("cross", windCategoryFromDegrees(0f, 275))
        // Côte nord de l'Espagne (350) : le vent de sud est offshore, celui du nord onshore.
        assertEquals("offshore", windCategoryFromDegrees(170f, 350))
        assertEquals("onshore", windCategoryFromDegrees(350f, 350))
        // Sans orientation : ancienne règle (plein ouest).
        assertEquals("offshore", windCategoryFromDegrees(90f))
        assertEquals("onshore", windCategoryFromDegrees(270f))
    }

    @Test
    fun sameSouthWindIsOffshoreOnANorthFacingBeachAndOnshoreOnASouthFacingOne() {
        // Houle de face dans les deux cas : seule la lecture du vent (S 12 km/h) change.
        val north = calculateSlotScore(hour(10, waveDir = 350f, windKmh = 12, windDir = "S"), 350, "intermediate", false)
        val south = calculateSlotScore(hour(10, waveDir = 195f, windKmh = 12, windDir = "S"), 195, "intermediate", false)
        assertTrue(north > south, "offshore ($north) doit battre onshore ($south)")
    }

    @Test
    fun swellComingFromTheSideScoresLowerThanHeadOn() {
        val headOn = calculateSlotScore(hour(10, waveDir = 275f), 275, "intermediate", false)
        val oblique = calculateSlotScore(hour(10, waveDir = 330f), 275, "intermediate", false)
        val blocked = calculateSlotScore(hour(10, waveDir = 5f), 275, "intermediate", false)
        assertTrue(headOn > oblique, "de face ($headOn) > de travers ($oblique)")
        assertTrue(oblique > blocked, "de travers ($oblique) > bloquée ($blocked)")
        assertEquals(0, blocked)
    }

    @Test
    fun gustsLowerTheScoreOfOtherwiseIdenticalHours() {
        val calm = calculateSlotScore(hour(10, windKmh = 12, windDir = "E"), 275, "intermediate", false)
        val gusty = calculateSlotScore(hour(10, windKmh = 12, windDir = "E", gustKmh = 40), 275, "intermediate", false)
        assertTrue(gusty < calm, "rafales ($gusty) < calme ($calm)")
        assertEquals(12.0 + 0.5 * (40 - 12), effectiveWindKmh(hour(10, windKmh = 12, gustKmh = 40)))
    }

    @Test
    fun chopLowersTheScoreProgressively() {
        assertEquals(1.0, chopFactor(1.0, 0.2))
        val some = chopFactor(1.2, 0.6)
        val lots = chopFactor(1.2, 1.2)
        assertTrue(some in 0.7..0.9, "clapot modéré : $some")
        assertTrue(lots < some && lots >= 0.4, "gros clapot : $lots")
        val clean = calculateSlotScore(hour(10), 275, "intermediate", false)
        val choppy = calculateSlotScore(hour(10, chop = 0.8), 275, "intermediate", false)
        assertTrue(choppy < clean)
    }

    @Test
    fun everySpotHasAnOrientation() {
        val missing = SurfDatabase.getAllSpots().filter { it.idealSwellDirection == null }.map { it.name }
        assertTrue(missing.isEmpty(), "spots sans orientation : $missing")
        assertTrue(SurfDatabase.getAllSpots().all { it.idealSwellDirection in 0..359 })
    }

    @Test
    fun smallCleanDayOpensForEveryLevel() {
        // 0,8 m à 9 s, vent léger offshore : ça ouvre, du débutant à l'expert (note autour de 60 : « Bon », pas « Parfait »).
        val small = hour(10, height = 0.8, period = 9.0, windKmh = 6, windDir = "E")
        listOf("beginner", "intermediate", "confirmed", "expert").forEach { level ->
            val rating = calculateSlotRating(small, 275, level, false)
            assertFalse(rating.tooBig)
            assertTrue(rating.kind != ConditionKind.TOO_SMALL, "0,8 m 9 s pour $level : ${rating.kind}")
            assertTrue(rating.score >= 55, "0,8 m 9 s pour $level : ${rating.score}")
        }
    }

    @Test
    fun expertPrefersPowerButBigDaysAreNotTooBigForThem() {
        val big = hour(10, height = 3.2, period = 14.0, windKmh = 8, windDir = "E")
        assertTrue(calculateSlotRating(big, 275, "confirmed", false).tooBig)
        val expert = calculateSlotRating(big, 275, "expert", false)
        assertFalse(expert.tooBig)
        assertTrue(expert.score >= 60, "3,2 m 14 s pour un expert : ${expert.score}")
    }

    // --- Profils : plafonds par niveau, profil personnalisé ---

    @Test
    fun capsPerLevelMatchTheDefinitions() {
        // Le « trop gros » commence 30 % au-dessus du maximum du profil (débutant 330 kJ, intermédiaire 570, confirmé 950).
        // 1,2 m à 10 s ≈ 283 kJ : pas trop gros pour un débutant ; 1,5 m à 10 s ≈ 441 kJ : trop gros pour lui.
        val day = hour(10, height = 1.2, period = 10.0, windKmh = 6, windDir = "E")
        assertFalse(calculateSlotRating(day, 275, "beginner", false).tooBig)
        val beginnerBig = hour(10, height = 1.5, period = 10.0, windKmh = 6, windDir = "E")
        assertTrue(calculateSlotRating(beginnerBig, 275, "beginner", false).tooBig)
        // 1,9 m à 10 s : encore pas trop gros pour un intermédiaire ; 2,0 m à 10 s : trop gros.
        assertFalse(calculateSlotRating(hour(10, height = 1.9, period = 10.0, windKmh = 6, windDir = "E"), 275, "intermediate", false).tooBig)
        assertTrue(calculateSlotRating(hour(10, height = 2.0, period = 10.0, windKmh = 6, windDir = "E"), 275, "intermediate", false).tooBig)
        // Confirmé : 2,5 m à 10 s encore pas trop gros, 2,6 m à 10 s trop gros.
        assertFalse(calculateSlotRating(hour(10, height = 2.5, period = 10.0, windKmh = 6, windDir = "E"), 275, "confirmed", false).tooBig)
        assertTrue(calculateSlotRating(hour(10, height = 2.6, period = 10.0, windKmh = 6, windDir = "E"), 275, "confirmed", false).tooBig)
        // L'expert surfe tout : jamais « trop gros ».
        val huge = hour(10, height = 6.0, period = 18.0, windKmh = 6, windDir = "E")
        assertFalse(calculateSlotRating(huge, 275, "expert", false).tooBig)
    }

    @Test
    fun customProfileRoundTripsThroughTheLevelString() {
        val confirmed = SurfProfile.preset("confirmed")
        val level = confirmed.serialize()
        assertTrue(isCustomLevel(level))
        assertEquals(confirmed, SurfProfile.fromLevel(level))
        val expert = SurfProfile.preset("expert")
        assertFalse(SurfProfile.fromLevel(expert.serialize()).hasCap)
        // Un profil personnalisé identique à un préréglage donne exactement les mêmes notes.
        val hours = listOf(hour(10), hour(11, height = 2.0, period = 12.0, windKmh = 12, windDir = "O", gustKmh = 25))
        hours.forEach { h ->
            assertEquals(calculateSlotRating(h, 275, "confirmed", false), calculateSlotRating(h, 275, level, false))
        }
        // Texte invalide : retombe sur Intermédiaire plutôt que de planter.
        assertEquals(SurfProfile.preset("intermediate"), SurfProfile.fromLevel("custom:n'importe quoi"))
    }

    @Test
    fun customToleranceChangesTheScore() {
        val windy = hour(10, windKmh = 18, windDir = "O", gustKmh = 18)
        val strict = SurfProfile.preset("confirmed").copy(onshoreMax = 5.0).serialize()
        val tolerant = SurfProfile.preset("confirmed").copy(onshoreMax = 20.0).serialize()
        assertTrue(
            calculateSlotScore(windy, 275, tolerant, false) > calculateSlotScore(windy, 275, strict, false),
            "un surfeur qui tolère l'onshore note mieux ce jour-là"
        )
        val shortPeriod = hour(10, height = 1.5, period = 7.0)
        val noMinimum = SurfProfile.preset("confirmed").copy(minPeriod = 6.0).serialize()
        val longOnly = SurfProfile.preset("confirmed").copy(minPeriod = 12.0).serialize()
        assertTrue(calculateSlotScore(shortPeriod, 275, noMinimum, false) > calculateSlotScore(shortPeriod, 275, longOnly, false))
    }

    @Test
    fun theHigherTheLevelTheMoreChopAndOnshoreWeigh() {
        // Un expert cherche le plein potentiel de la vague : le clapot et l'onshore le pénalisent plus qu'un débutant.
        val choppy = hour(10, height = 0.9, period = 9.0, windKmh = 10, windDir = "E", chop = 0.5)
        val beginner = calculateSlotScore(choppy, 275, "beginner", false)
        val expert = calculateSlotScore(choppy, 275, "expert", false)
        assertTrue(beginner > expert, "clapot 0,5 m : débutant ($beginner) > expert ($expert)")
        val onshore = hour(10, height = 0.9, period = 9.0, windKmh = 15, windDir = "O")
        assertTrue(
            calculateSlotScore(onshore, 275, "beginner", false) > calculateSlotScore(onshore, 275, "expert", false),
            "onshore 15 km/h : le débutant s'en accommode mieux"
        )
        // Période courte : le débutant (mousse) s'en moque plus que l'expert.
        val shortPeriod = hour(10, height = 0.9, period = 5.0, windKmh = 5, windDir = "E")
        assertTrue(calculateSlotScore(shortPeriod, 275, "beginner", false) > calculateSlotScore(shortPeriod, 275, "expert", false))
    }

    @Test
    fun offshoreIsForConfirmedAndExpertNotForBeginners() {
        val offshore = hour(10, height = 0.9, period = 9.0, windKmh = 15, windDir = "E")
        val cross = hour(10, height = 0.9, period = 9.0, windKmh = 15, windDir = "N")
        // Débutant : la direction ne change presque rien.
        val begDiff = calculateSlotScore(offshore, 275, "beginner", false) - calculateSlotScore(cross, 275, "beginner", false)
        assertTrue(begDiff in 0..6, "débutant offshore - travers : $begDiff")
        // Confirmé : l'offshore est nettement mieux que le travers.
        val confDiff = calculateSlotScore(offshore, 275, "confirmed", false) - calculateSlotScore(cross, 275, "confirmed", false)
        assertTrue(confDiff >= 12, "confirmé offshore - travers : $confDiff")
    }

    @Test
    fun oldCustomProfilesWithoutDirectionSettingsStillLoad() {
        val legacy = "custom:80.0;500.0;700.0;0.85;250.0;7.0;25.0;8.0;25.0;0.2;0.0"
        val profile = SurfProfile.fromLevel(legacy)
        assertEquals(700.0, profile.cap)
        assertEquals(1.0, profile.directionMatters)
        assertEquals(1.0, profile.offshoreMinFactor)
    }

    @Test
    fun bestSlotsAreSplitBetweenMorningAndAfternoon() {
        val hours = (7..18).map { h ->
            // Matin : houle propre ; après-midi : onshore costaud qui dégrade tout.
            if (h < 13) hour(h) else hour(h, windKmh = 28, windDir = "O")
        }
        val slots = findBestSlotsOfDay(hours, null, "intermediate", null)
        val morning = slots.morning
        assertNotNull(morning)
        assertTrue(morning.endHour < 13)
        assertEquals(86, morning.averageScore)
        // L'après-midi n'a aucun bon créneau : il est soit absent, soit nettement moins bon.
        val afternoon = slots.afternoon
        assertTrue(afternoon == null || afternoon.averageScore < 35)
        // Un jour qui n'a que des heures de l'après-midi : pas de créneau du matin.
        val onlyAfternoon = findBestSlotsOfDay(listOf(hour(14), hour(15), hour(16)), null, "intermediate", null)
        assertTrue(onlyAfternoon.morning == null && onlyAfternoon.afternoon != null)
    }

    @Test
    fun tideNoLongerChangesTheScore() {
        val tide = DailyTideInfo(highTideTime = "12:00", lowTideTime = "06:00")
        val nine = hour(9)
        val base = calculateSlotScore(nine, null, "intermediate", false)
        listOf("rising", "high", "falling", "low").forEach { pref ->
            assertEquals(base, calculateSlotScore(nine, null, "intermediate", false, pref, tide))
        }
        assertEquals(base, calculateSlotScore(nine, null, "intermediate", true))
    }

    @Test
    fun energyZonesFollowTheProfile() {
        val confirmed = SurfProfile.preset("confirmed")
        assertEquals(0, energyZone(40.0, confirmed))
        assertEquals(1, energyZone(283.0, confirmed))
        assertEquals(2, energyZone(700.0, confirmed))
        assertEquals(3, energyZone(1300.0, confirmed))
        // Le même jour n'a pas la même couleur pour tout le monde ; l'expert n'a jamais de « trop gros ».
        assertEquals(2, energyZone(723.0, SurfProfile.preset("intermediate")))
        assertEquals(3, energyZone(800.0, SurfProfile.preset("intermediate")))
        assertEquals(1, energyZone(723.0, SurfProfile.preset("expert")))
        assertEquals(2, energyZone(5000.0, SurfProfile.preset("expert")))
    }

    @Test
    fun swellAxisAdaptsToTheForecast() {
        val (smallMax, smallGrid) = swellAxisScale(0.7)
        val (bigMax, bigGrid) = swellAxisScale(3.2)
        assertTrue(smallMax < bigMax)
        assertTrue(smallMax >= 0.7 && bigMax >= 3.2)
        assertTrue(smallGrid.size in 2..5 && bigGrid.size in 2..5)
    }
}

class StarsTest {
    @kotlin.test.Test
    fun scoreMapsToHalfStars() {
        kotlin.test.assertEquals(0.5f, starsForScore(0, 0))
        kotlin.test.assertEquals(2f, starsForScore(50, 0))
        kotlin.test.assertEquals(4f, starsForScore(85, 0))
        kotlin.test.assertEquals(5f, starsForScore(100, 0))
    }
}

class StarsOffsetTest {
    @kotlin.test.Test
    fun offsetShiftsTheScale() {
        kotlin.test.assertEquals(3f, starsForScore(70, 0))
        kotlin.test.assertEquals(2.5f, starsForScore(70, -10))
        kotlin.test.assertEquals(3.5f, starsForScore(70, 10))
        kotlin.test.assertEquals("3,5", starsText(3.5f))
        kotlin.test.assertEquals("4", starsText(4f))
    }
}
