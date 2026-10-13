package com.surfcast.surfforecast

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BuoyCatalogTest {
    @Test
    fun gironde_and_landes_spots_use_cap_ferret_not_royan() {
        assertEquals("03302", BuoyCatalog.referenceFor(45.00, -1.20)!!.first.code) // Lacanau
        assertEquals("03302", BuoyCatalog.referenceFor(45.38, -1.17)!!.first.code) // Montalivet
        assertEquals("03302", BuoyCatalog.referenceFor(44.45, -1.25)!!.first.code) // Biscarrosse
    }

    @Test
    fun basque_coast_uses_saint_jean_de_luz() {
        val (b, km) = BuoyCatalog.referenceFor(43.67, -1.44)!! // Hossegor
        assertEquals("06403", b.code)
        assertTrue(km in 25..50, "distance $km km")
    }

    @Test
    fun brittany_uses_pierres_noires() {
        assertEquals("02911", BuoyCatalog.referenceFor(48.22, -4.62)!!.first.code) // Crozon
    }

    @Test
    fun far_from_any_buoy_gives_none() {
        assertNull(BuoyCatalog.referenceFor(48.85, 2.35)) // Paris
        assertNull(BuoyCatalog.referenceFor(37.0, -8.9)) // Algarve
    }
}
