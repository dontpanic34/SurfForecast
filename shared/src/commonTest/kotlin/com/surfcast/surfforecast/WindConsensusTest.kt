package com.surfcast.surfforecast

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WindConsensusTest {
    @Test
    fun sansModeleRienNEstInvente() {
        assertNull(windConsensus(emptyList()))
    }

    @Test
    fun deuxModelesDonnentLeurMoyenne() {
        val c = windConsensus(listOf(WindVoiceIn("ECMWF", 20.0, 290.0, 40.0), WindVoiceIn("GFS", 30.0, 290.0, 50.0)))
        assertNotNull(c)
        assertEquals(25.0, c.speed, 0.01)
        assertEquals(45.0, c.gust, 0.01)
    }

    @Test
    fun medianeEtRafalesFortesLe20Octobre() {
        // 14 h, Montalivet : ECMWF 23/50, GFS 34/50, AROME HD 27/71, AROME 34/70, ARPEGE 30/56.
        val c = windConsensus(
            listOf(
                WindVoiceIn("ECMWF", 23.0, 295.0, 50.0), WindVoiceIn("GFS", 34.0, 291.0, 50.0), WindVoiceIn("AROME HD", 27.0, 295.0, 71.0),
                WindVoiceIn("AROME", 34.0, 296.0, 70.0), WindVoiceIn("ARPEGE", 30.0, 288.0, 56.0)
            )
        )
        assertNotNull(c)
        assertEquals(30.0, c.speed, 0.01)
        // Les deux plus fortes rafales : 71 et 70.
        assertEquals(70.5, c.gust, 0.01)
        assertTrue(c.dir in 288.0..296.0)
        assertEquals("ECMWF+GFS+AROME HD+AROME+ARPEGE", c.source)
    }

    @Test
    fun unModeleAberrantNeTirePasLaMoyenne() {
        val c = windConsensus(listOf(WindVoiceIn("A", 20.0, 270.0, 30.0), WindVoiceIn("B", 22.0, 270.0, 32.0), WindVoiceIn("C", 60.0, 270.0, 90.0)))
        assertNotNull(c)
        assertEquals(22.0, c.speed, 0.01)
    }

    @Test
    fun directionAutourDuNord() {
        val c = windConsensus(listOf(WindVoiceIn("A", 20.0, 350.0, 25.0), WindVoiceIn("B", 20.0, 10.0, 25.0)))
        assertNotNull(c)
        assertTrue(c.dir < 1.0 || c.dir > 359.0)
    }

    @Test
    fun lesRafalesNeSontJamaisSousLeVentMoyen() {
        val c = windConsensus(listOf(WindVoiceIn("A", 30.0, 270.0, 20.0)))
        assertNotNull(c)
        assertEquals(30.0, c.gust, 0.01)
    }
}
