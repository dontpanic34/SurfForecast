package com.surfcast.surfforecast

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class I18nTest {
    @AfterTest fun back() { I18n.language = "fr" }

    @Test fun frenchStaysFrench() { assertEquals("Vent", tr("Vent")) }

    @Test fun translatesEachLanguage() {
        I18n.language = "en"; assertEquals("Wind", tr("Vent"))
        I18n.language = "de"; assertEquals("Einstellungen", tr("Réglages"))
        I18n.language = "nl"; assertEquals("Instellingen", tr("Réglages"))
        I18n.language = "es"; assertEquals("Ajustes", tr("Réglages"))
    }

    @Test fun unknownTextStaysFrench() { I18n.language = "de"; assertEquals("Texte inconnu", tr("Texte inconnu")) }

    @Test fun placeholders() { I18n.language = "en"; assertEquals("a-b", tr("{0}-{1}", "a", "b")) }

    @Test fun everyEntryHasFourTranslations() { assertTrue(missingTranslations().isEmpty(), missingTranslations().toString()) }

    @Test fun dutchIsOffered() { assertEquals("nl", defaultAppLanguage("nl-NL")) }
}
