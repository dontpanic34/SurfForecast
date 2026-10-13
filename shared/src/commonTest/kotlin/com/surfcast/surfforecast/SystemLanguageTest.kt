package com.surfcast.surfforecast

import kotlin.test.Test
import kotlin.test.assertEquals

class SystemLanguageTest {
    @Test
    fun systemLanguageIsUsedWhenTheAppOffersIt() {
        assertEquals("en", defaultAppLanguage("en"))
        assertEquals("de", defaultAppLanguage("de-DE"))
        assertEquals("es", defaultAppLanguage("ES"))
    }

    @Test
    fun unknownOrMissingSystemLanguageFallsBackToFrench() {
        assertEquals("fr", defaultAppLanguage("ja"))
        assertEquals("fr", defaultAppLanguage(""))
    }
}
