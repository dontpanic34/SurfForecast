package com.surfcast.surfforecast

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SatelliteModelTest {

    private val text = """{"images":[
        {"date":"20261001","time":"11:08","file":"20261001.jpg","cloud":[0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,4,7,16,12,9,19,0,0,0,0,0,0,0,0,0,9,25,54,60,63,70,70,70,70,70,70,70,70,70,70,70,70],"tide":{"level":-2.2,"state":"basse","range":3.4}},
        {"date":"20261021","time":"11:08","file":"20261021.jpg","cloud":[],"tide":{"level":0.55,"state":"haute","range":2.1}},
        {"date":"20260601","time":"11:08","file":"20260601.jpg","cloud":[],"tide":null}],
        "width":520,"height":3000,"lat_top":45.514746676248656,"lat_bottom":45.24525332375135,"lon_left":-1.1871361078031775,"lon_right":-1.1206325802683943,
        "zone":"montalivet","lat":45.38,"lon":-1.159,"pixel_m":10.0,"block_rows":50}"""

    @Test
    fun indexIsReadAndSortedByDate() {
        val index = assertNotNull(SatelliteModel.parseIndex(text))
        assertEquals(listOf("20260601", "20261001", "20261021"), index.images.map { it.date })
        assertEquals("marée basse · marnage 3,4 m", index.images[1].tide?.label)
        assertNull(SatelliteModel.parseIndex("pas du json"))
    }

    @Test
    fun frameMapsLatitudeAndLongitudeToPixels() {
        val index = SatelliteModel.parseIndex(text)!!
        assertTrue(abs(index.rowOf(45.38) - 1500) < 1.0)
        assertTrue(abs(index.colOf(-1.159) - 220) < 1.0)
        assertTrue(abs(index.latOf(index.rowOf(45.31)) - 45.31) < 1e-9)
        assertTrue(index.contains(45.31, -1.165))
        assertTrue(!index.contains(45.60, -1.165))
    }

    @Test
    fun cloudsAreJudgedPointByPoint() {
        // 1er octobre 2026 : dégagé à Montalivet, bouché 8 km au sud.
        val index = SatelliteModel.parseIndex(text)!!
        val oct1 = index.images[1]
        assertTrue(!index.isCloudy(oct1, 45.38))
        assertTrue(index.isCloudy(oct1, 45.308))
        assertEquals(listOf("20261001", "20261021"), SatelliteModel.frames(index, 45.38, 3, clearOnly = true, lowTideOnly = false).map { it.date })
        assertEquals(listOf("20261021"), SatelliteModel.frames(index, 45.308, 3, clearOnly = true, lowTideOnly = false).map { it.date })
        assertEquals(listOf("20261001"), SatelliteModel.frames(index, 45.38, 3, clearOnly = false, lowTideOnly = true).map { it.date })
        assertEquals(3, SatelliteModel.frames(index, 45.38, 6, clearOnly = false, lowTideOnly = false).size)
    }

    @Test
    fun coordinatesAreReadFromTextAndMapsLinks() {
        assertEquals(45.31 to -1.16, SatelliteModel.parseCoordinates("45.31, -1.16"))
        assertEquals(45.31 to -1.16, SatelliteModel.parseCoordinates("45,31 -1,16"))
        assertEquals(45.3101 to -1.1602, SatelliteModel.parseCoordinates("https://www.google.com/maps/@45.3101,-1.1602,15z"))
        assertEquals(45.3101 to -1.1602, SatelliteModel.parseCoordinates("https://maps.google.com/?q=45.3101,-1.1602"))
        assertEquals(45.3101 to -1.1602, SatelliteModel.parseCoordinates("https://www.google.com/maps/place/x/@45.2,-1.1,12z/data=!3d45.3101!4d-1.1602"))
        assertNull(SatelliteModel.parseCoordinates("https://maps.app.goo.gl/AbCdEf"))
        assertNull(SatelliteModel.parseCoordinates("120.5, 3.2"))
    }

    @Test
    fun pointsAreKeptOnTheDevice() {
        val prefs = InMemoryKeyValueStore()
        assertTrue(SatelliteModel.loadPoints(prefs).isEmpty())
        SatelliteModel.savePoints(prefs, listOf(SatPoint("Plage du vélo", 45.31, -1.165), SatPoint("Nord | essai", 45.45, -1.15)))
        val back = SatelliteModel.loadPoints(prefs)
        assertEquals(2, back.size)
        assertEquals(SatPoint("Plage du vélo", 45.31, -1.165), back[0])
        assertEquals("Nord   essai", back[1].name)
    }
}
