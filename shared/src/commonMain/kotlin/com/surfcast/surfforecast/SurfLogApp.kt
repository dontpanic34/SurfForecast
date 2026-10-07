package com.surfcast.surfforecast

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.surfcast.surfforecast.ui.theme.SurfForecastTheme

/**
 * Racine de l'app partagée : équivalent de MainActivity.setContent côté Android
 * (même choix de thème clair/sombre/système, même MainScreen).
 */
@Composable
fun SurfLogApp(
    prefs: KeyValueStore,
    // Stockage du journal de bord, fourni par la plateforme.
    sessionLogStore: SessionLogStore? = null,
    onPinWidget: (() -> Unit)? = null,
    appVersion: String? = null
) {
    val scope = rememberCoroutineScope()
    val controller = remember(prefs) { SurfController(scope = scope, prefs = prefs, sessionLogStore = sessionLogStore) }
    LaunchedEffect(controller) {
        AppForeground.events.collect { controller.onAppResumed() }
    }

    val useDarkTheme = when (controller.themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }

    SurfForecastTheme(useDarkTheme = useDarkTheme) {
        MainScreen(viewModel = controller, onPinWidget = onPinWidget, appVersion = appVersion)
    }
}
