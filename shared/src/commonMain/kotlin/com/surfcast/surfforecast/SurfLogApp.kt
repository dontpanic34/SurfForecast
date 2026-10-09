package com.surfcast.surfforecast

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
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
    appVersion: String? = null,
    backup: SessionLogBackup? = null,
    install: AppInstall? = null
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
        // Taille de l'affichage : on agrandit dp et sp ensemble (textes, étoiles, icônes), comme un vrai zoom.
        val base = LocalDensity.current
        val scale = displayScaleFor(controller.displaySize)
        CompositionLocalProvider(LocalDensity provides Density(base.density * scale, base.fontScale)) {
            PinchZoomBox {
                MainScreen(viewModel = controller, onPinWidget = onPinWidget, appVersion = appVersion, backup = backup, install = install)
            }
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

/**
 * Zoom « loupe » à deux doigts, où l'on veut : on pince pour agrandir l'écran, on le fait glisser à deux doigts pour
 * se déplacer, on repince pour revenir. Un seul doigt garde son rôle habituel (défiler, toucher), donc rien ne change
 * tant qu'on ne pince pas.
 */
@Composable
private fun PinchZoomBox(content: @Composable () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.count { it.pressed } >= 2) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            val centroid = event.calculateCentroid(useCurrent = false)
                            val newScale = (scale * zoom).coerceIn(1f, 4f)
                            val ratio = newScale / scale
                            val moved = centroid - (centroid - offset) * ratio + pan
                            offset = Offset(
                                moved.x.coerceIn(size.width * (1f - newScale), 0f),
                                moved.y.coerceIn(size.height * (1f - newScale), 0f)
                            )
                            scale = newScale
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                    if (scale < 1.05f) {
                        scale = 1f
                        offset = Offset.Zero
                    }
                }
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                    transformOrigin = TransformOrigin(0f, 0f)
                }
        ) { content() }
    }
}
