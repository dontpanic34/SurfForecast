package com.surfcast.surfforecast

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.surfcast.surfforecast.ui.theme.SurfForecastTheme

/** Ce que reçoit le lecteur webcam de l'hôte (Android) : spot, fermeture, changement de spot, réglages. */
class LiveCamContext(
    val spotName: String,
    val onClose: () -> Unit,
    val onSwitchSpot: (String) -> Unit,
    val showLiveOverlay: Boolean,
    val windUnit: String,
    val loadLiveConditions: suspend (String) -> LiveConditions?
)

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
    appVersion: String? = null,
    backup: SessionLogBackup? = null,
    install: AppInstall? = null,
    // Hôte (Android) : branchement du widget d'écran d'accueil sur chaque prévision chargée.
    onForecastLoaded: (SurfUiState.Success, SurfRepository.TidesBundle) -> Unit = { _, _ -> },
    // Hôte (Android) : lecteur webcam intégré (spot, fermeture, changement de spot) ; null = page web.
    liveCamOverlay: (@Composable (LiveCamContext) -> Unit)? = null,
    // Hôte (Android) : bandeau « nouvelle version disponible ».
    updateBanner: (@Composable () -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    val controller = remember(prefs) {
        SurfController(scope = scope, prefs = prefs, onForecastLoaded = onForecastLoaded, sessionLogStore = sessionLogStore)
    }
    LaunchedEffect(controller) {
        AppForeground.events.collect { controller.onAppResumed() }
    }

    val useDarkTheme = when (controller.themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }

    SurfForecastTheme(useDarkTheme = useDarkTheme) {
        // Taille de l'affichage : on agrandit dp et sp ensemble (textes, étoiles, icônes), comme un vrai zoom.
        val base = LocalDensity.current
        val scale = displayScaleFor(controller.displaySize)
        CompositionLocalProvider(LocalDensity provides Density(base.density * scale, base.fontScale)) {
            MainScreen(
                viewModel = controller,
                onPinWidget = onPinWidget,
                appVersion = appVersion,
                backup = backup,
                install = install,
                liveCamOverlay = liveCamOverlay,
                updateBanner = updateBanner
            )
        }
    }
}

/** Facteur de zoom de l'affichage : Normal, Grand, Très grand. */
fun displayScaleFor(size: String): Float = when (size) {
    "large" -> 1.18f
    "xlarge" -> 1.38f
    else -> 1f
}

val DISPLAY_SIZE_CHOICES = listOf("normal" to "Normal", "large" to "Grand", "xlarge" to "Très grand")
