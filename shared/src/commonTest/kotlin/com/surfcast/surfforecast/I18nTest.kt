package com.surfcast.surfforecast

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class I18nTest {
    private fun lang(code: String, block: () -> Unit) { I18n.language = code; try { block() } finally { I18n.language = "fr" } }

    @Test fun frenchStaysFrench() { assertEquals("Vent", tr("Vent")) }

    @Test fun translatesEachLanguage() {
        lang("en") { assertEquals("Wind", tr("Vent")) }
        lang("de") { assertEquals("Einstellungen", tr("Réglages")) }
        lang("nl") { assertEquals("Instellingen", tr("Réglages")) }
        lang("es") { assertEquals("Ajustes", tr("Réglages")) }
    }

    @Test fun unknownTextStaysFrench() { lang("de") { assertEquals("Texte inconnu", tr("Texte inconnu")) } }

    @Test fun placeholders() { lang("en") { assertEquals("a-b", tr("{0}-{1}", "a", "b")) } }

    @Test fun everyEntryHasFourTranslations() { assertTrue(missingTranslations().isEmpty(), missingTranslations().toString()) }

    @Test fun fragmentsAreTranslatedInsideComposedTexts() {
        lang("en") { assertEquals("swell rising from 1.2 m to 1.8 m", tr("houle qui monte de 1,2 m à 1,8 m")) }
    }

    @Test fun calendarNames() {
        lang("nl") { assertEquals("maandag", tr("lundi")) }
    }

    @Test fun dutchIsOffered() { assertEquals("nl", defaultAppLanguage("nl-NL")) }
}
