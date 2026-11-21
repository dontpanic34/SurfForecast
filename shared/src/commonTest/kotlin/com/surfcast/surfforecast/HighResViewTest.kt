package com.surfcast.surfforecast

import kotlin.math.abs
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HighResViewTest {
    @Test
    fun montaliveBeachIsInTheTileFoundOnTheIgnServer() {
        // Tuile de la plage de Montalivet à 17 : colonne 65112, ligne 46954 (vérifiée sur data.geopf.fr).
        val x = floor(IgnTiles.worldX(-1.1630, 17.0) / 256).toInt()
        val y = floor(IgnTiles.worldY(45.3787, 17.0) / 256).toInt()
        assertEquals(65112, x)
        assertEquals(46954, y)
    }

    @Test
    fun projectionRoundTrips() {
        val z = 16.4
        val lon = IgnTiles.lonOf(IgnTiles.worldX(-1.2, z), z)
        val lat = IgnTiles.latOf(IgnTiles.worldY(45.3, z), z)
        assertTrue(abs(lon + 1.2) < 1e-9 && abs(lat - 45.3) < 1e-9)
    }

    @Test
    fun flightDateAndTileChecks() {
        assertEquals("2024-05-10", IgnTiles.parseFlightDate("""{"features":[{"properties":{"pva":2024,"date_vol":"2024-05-10Z"}}]}"""))
        assertNull(IgnTiles.parseFlightDate("{}"))
        assertEquals("10 mai 2024", IgnTiles.dateLabel("2024-05-10"))
        assertTrue(!IgnTiles.isUsable(IgnTiles.pleiades.first(), ByteArray(852)))
        assertTrue(IgnTiles.isUsable(IgnTiles.pleiades.first(), ByteArray(40000)))
        assertTrue(IgnTiles.isUsable(IgnTiles.aerial, ByteArray(2868)))
        assertTrue(!IgnTiles.isUsable(IgnTiles.aerial, null))
        assertNotNull(IgnTiles.tileUrl(IgnTiles.aerial, 17, 65112, 46954).takeIf { "TILEROW=46954" in it && "TILECOL=65112" in it })
    }
}
