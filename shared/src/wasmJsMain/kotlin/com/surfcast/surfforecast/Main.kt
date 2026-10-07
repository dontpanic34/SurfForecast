package com.surfcast.surfforecast

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document

// Fuseaux horaires (kotlinx-datetime) : chargés depuis le paquet npm @js-joda/timezone.
@JsModule("@js-joda/timezone")
external object JsJodaTimeZoneModule : JsAny

private val jsJodaTz = JsJodaTimeZoneModule

@JsFun("() => document.visibilityState === 'visible'")
private external fun isPageVisible(): Boolean

@JsFun(
    """(name, text) => {
        const a = document.createElement('a');
        a.href = URL.createObjectURL(new Blob([text], { type: 'application/json' }));
        a.download = name;
        document.body.appendChild(a);
        a.click();
        a.remove();
    }"""
)
private external fun downloadTextFile(name: String, text: String)

@JsFun(
    """(onText) => {
        const input = document.createElement('input');
        input.type = 'file';
        input.accept = '.json,application/json';
        input.onchange = () => {
            const f = input.files[0];
            if (!f) return;
            f.text().then(t => onText(t), () => onText(''));
        };
        input.click();
    }"""
)
private external fun pickTextFile(onText: (JsString) -> Unit)

/** Point d'entrée de la version web (surflog.js), même app que sur Android. */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    jsJodaTz
    val prefs = LocalStorageStore()
    val sessionLogStore = JsonSessionLogStore(prefs)
    val backup = SessionLogBackup(
        export = { downloadTextFile("surflog-journal.json", sessionLogStore.exportJson()) },
        import = { onDone -> pickTextFile { text -> onDone(sessionLogStore.importJson(text.toString())) } }
    )
    // Numéro de version injecté dans index.html par la CI (1.0.<n° de build>).
    val version = document.querySelector("meta[name=app-version]")?.getAttribute("content")

    // Retour sur l'onglet / l'app installée -> recharge des prévisions trop anciennes.
    document.addEventListener("visibilitychange") {
        if (isPageVisible()) AppForeground.notifyResumed()
    }

    document.getElementById("loading")?.remove()
    ComposeViewport(document.body!!) {
        SurfLogApp(prefs = prefs, sessionLogStore = sessionLogStore, appVersion = version, backup = backup)
    }
}
