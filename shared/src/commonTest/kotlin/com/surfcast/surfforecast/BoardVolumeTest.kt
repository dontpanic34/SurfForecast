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
    fun estimatesTheVolumeOfATypicalShortboard() {
        // 6'0 x 19 x 2 5/16 : environ 31 L.
        val volume = estimateVolumeL(6.0, 0.0, 19.0, 2.3125)
        assertNotNull(volume)
        assertTrue(volume in 30.5..31.7, "volume : $volume")
        assertNull(estimateVolumeL(0.0, 0.0, 19.0, 2.3))
    }

    @Test
    fun ratioAndLevelFollowTheReferenceTable() {
        assertEquals(0.4, volumeRatio(30.0, 75))
        assertNull(volumeRatio(30.0, 0))
        assertNull(volumeRatio(null, 75))
        assertEquals("beginner", levelForRatio(0.75))
        assertEquals("intermediate", levelForRatio(0.5))
        assertEquals("confirmed", levelForRatio(0.4))
        assertEquals("expert", levelForRatio(0.33))
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
}
