package com.surfcast.surfforecast

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document

// Fuseaux horaires (kotlinx-datetime) : chargés depuis le paquet npm @js-joda/timezone.
@JsModule("@js-joda/timezone")
external object JsJodaTimeZoneModule : JsAny

private val jsJodaTz = JsJodaTimeZoneModule

/** Point d'entrée de la version web (surflog.js), même app que sur Android. */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    jsJodaTz
    val prefs = LocalStorageStore()
    val sessionLogStore = JsonSessionLogStore(prefs)
    // Numéro de version injecté dans index.html par la CI (1.0.<n° de build>).
    val version = document.querySelector("meta[name=app-version]")?.getAttribute("content")

    // Retour sur l'onglet / l'app installée -> recharge des prévisions trop anciennes.
    document.addEventListener("visibilitychange") {
        if (!document.hidden) AppForeground.notifyResumed()
    }

    document.getElementById("loading")?.remove()
    ComposeViewport(document.body!!) {
        SurfLogApp(prefs = prefs, sessionLogStore = sessionLogStore, appVersion = version)
    }
}
