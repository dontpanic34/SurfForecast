package com.surfcast.surfforecast

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WhereToSurfTest {
    private val day = LocalDate(2026, 10, 21)

    /** Une journée de 7 h à 19 h : houle [h] m de [period] s venant de [dir], vent [wind] km/h de [windDir]. */
    private fun hours(h: Double, period: Double, dir: Float, wind: Int, windDir: String) = (0..23).map { hour ->
        HourlyUiModel(
            timeFormatted = "$hour:00", rawTime = LocalDateTime(day.year, day.monthNumber, day.dayOfMonth, hour, 0),
            waveHeight = h, wavePeriod = period, waveDirection = dir, energyKj = calculateWaveEnergyReal(h, period),
            windSpeedKmh = wind, windDirectionStr = windDir, weatherCode = 0, temperature = 18
        )
    }

    private fun spot(name: String, facing: Int?) = ScanSpot(name, "zone", "France", 43.0, -1.5, facing, 100)

    @Test
    fun entreeSeulementSiTroisJoursSousDeuxEtoiles() {
        assertFalse(WhereToSurf.needsWhereToSurf(listOf(1f, 1.5f, 3f, 4f, 3f, 2f, 5f)))
        assertTrue(WhereToSurf.needsWhereToSurf(listOf(1f, 1.5f, 0.5f, 4f, 3f, 2f, null)))
        // Deux étoiles pile ne comptent pas comme « moins de 2 ».
        assertFalse(WhereToSurf.needsWhereToSurf(listOf(2f, 2f, 2f, 2f)))
    }

    @Test
    fun spotsAutourExclutLeSpotDeDepartEtRespecteLeRayon() {
        val home = SurfDatabase.findSpotByName("Montalivet")
        assertNotNull(home)
        val near = WhereToSurf.spotsAround(home.latitude, home.longitude, 50, "Montalivet")
        assertTrue(near.none { it.name == "Montalivet" })
        assertTrue(near.all { it.distanceKm <= 50 })
        val wide = WhereToSurf.spotsAround(home.latitude, home.longitude, 500, "Montalivet")
        assertTrue(wide.size > near.size)
        assertTrue(wide.size <= WhereToSurf.MAX_POINTS)
        assertTrue(wide.all { it.distanceKm <= 500 })
    }

    @Test
    fun ailleursVautLeDeplacementSeulementSiMieuxEtAssezBon() {
        // Plage plein ouest : houle d'ouest de 1,8 m et vent d'est offshore = très bon ; vent d'ouest fort = mauvais.
        val good = spot("Propre", 270) to hours(1.8, 12.0, 270f, 8, "E")
        val bad = spot("Venté", 270) to hours(1.8, 12.0, 270f, 35, "O")
        val result = WhereToSurf.analyse(300, mapOf(day to 1.0f), listOf(good, bad))
        val d = result.days.single()
        assertEquals("Propre", d.best?.spot?.name)
        assertTrue(d.worthMoving)
        assertEquals("Propre", d.top.first().spot.name)
        assertTrue(d.hours.isNotEmpty())
        // Si ici est déjà aussi bon, on ne bouge pas.
        val same = WhereToSurf.analyse(300, mapOf(day to 5.0f), listOf(good))
        assertFalse(same.days.single().worthMoving)
        // Rien de bon ailleurs : on ne propose pas de bouger.
        val none = WhereToSurf.analyse(300, mapOf(day to 1.0f), listOf(bad))
        assertFalse(none.days.single().worthMoving)
    }

    @Test
    fun sensDuDeplacement() {
        assertEquals("au sud", WhereToSurf.directionWord(45.0, -1.0, 43.5, -1.0))
        assertEquals("au nord", WhereToSurf.directionWord(43.0, -1.0, 46.0, -1.0))
        assertEquals("à l'ouest", WhereToSurf.directionWord(43.0, -1.0, 43.0, -3.0))
    }
}
