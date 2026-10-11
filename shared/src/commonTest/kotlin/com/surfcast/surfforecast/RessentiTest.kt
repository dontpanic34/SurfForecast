package com.surfcast.surfforecast

import kotlin.test.Test
import kotlin.test.assertEquals

class RessentiTest {
    @Test
    fun skyWordFollowsTheSeenCover() {
        assertEquals("Grand soleil", skyWord(0))
        assertEquals("Ciel voilé", skyWord(20))
        assertEquals("Éclaircies", skyWord(45))
        assertEquals("Nuageux", skyWord(65))
        assertEquals("Très couvert", skyWord(95))
    }
}
