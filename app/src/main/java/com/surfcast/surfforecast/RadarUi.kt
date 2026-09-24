@file:Suppress("SpellCheckingInspection")
package com.surfcast.surfforecast

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.surfcast.surfforecast.ui.theme.AppColors
import kotlinx.coroutines.delay
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.TilesOverlay
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun RadarIcon(
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val center = Offset(w / 2f, h / 2f)
        val radius = minOf(w, h) / 2f * 0.9f
        drawCircle(color = color, radius = radius, center = center, style = Stroke(width = w * 0.07f))
        drawCircle(color = color, radius = radius * 0.6f, center = center, style = Stroke(width = w * 0.06f))
        drawCircle(color = color, radius = w * 0.06f, center = center)
        drawLine(color = color, start = center, end = Offset(center.x, center.y - radius), strokeWidth = w * 0.07f)
    }
}

private fun initOsmdroidConfig(context: Context) {
    val config = Configuration.getInstance()
    config.load(context, context.getSharedPreferences("osmdroid_prefs", Context.MODE_PRIVATE))
    config.userAgentValue = context.packageName
    config.osmdroidTileCache = File(context.cacheDir, "osmdroid_tiles")
}

@Composable
fun RadarScreen(
    centerLat: Double,
    centerLon: Double,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme

    var frames by remember { mutableStateOf<RainviewerFrames?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var frameIndex by remember { mutableStateOf(0) }
    var isPlaying by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            val result = RainviewerRepository().fetchFrames()
            frames = result
            frameIndex = result.nowIndex
        } catch (e: Exception) {
            loadError = e.message ?: "Chargement du radar impossible"
        }
    }

    LaunchedEffect(isPlaying, frames) {
        val f = frames ?: return@LaunchedEffect
        if (f.all.isEmpty()) return@LaunchedEffect
        while (isPlaying) {
            delay(600)
            frameIndex = (frameIndex + 1) % f.all.size
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize()) {
            val currentFrames = frames
            when {
                currentFrames != null && currentFrames.all.isNotEmpty() -> {
                    RadarMapView(
                        frames = currentFrames,
                        frameIndex = frameIndex,
                        centerLat = centerLat,
                        centerLon = centerLon,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                loadError != null -> {
                    Surface(modifier = Modifier.fillMaxSize(), color = colors.background) {
                        Text(
                            text = "Radar indisponible : $loadError",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            color = colors.onBackground
                        )
                    }
                }
                else -> {
                    Surface(modifier = Modifier.fillMaxSize(), color = colors.background) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = colors.primary)
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable { onDismiss() }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(text = "Fermer", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }

            currentFrames?.let { f ->
                RadarBottomPanel(
                    frames = f,
                    frameIndex = frameIndex,
                    onFrameIndexChange = {
                        isPlaying = false
                        frameIndex = it
                    },
                    isPlaying = isPlaying,
                    onTogglePlay = { isPlaying = !isPlaying },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun RadarMapView(
    frames: RainviewerFrames,
    frameIndex: Int,
    centerLat: Double,
    centerLon: Double,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val mapView = remember {
        initOsmdroidConfig(context)
        MapView(context).apply {
            setMultiTouchControls(true)
            controller.setZoom(7.0)
            controller.setCenter(GeoPoint(centerLat, centerLon))
        }
    }

    // NB : cette version d'osmdroid n'expose pas de canal alpha sur TilesOverlay/Overlay,
    // un vrai crossfade entre deux calques n'est donc pas possible avec cette lib.
    // Le cache disque (clearTileCache cible uniquement la frame courante) limite le
    // clignotement une fois les tuiles d'une frame deja vues.
    val tileProvider = remember { MapTileProviderBasic(context) }
    val overlay = remember {
        TilesOverlay(tileProvider, context).apply { loadingBackgroundColor = android.graphics.Color.TRANSPARENT }
    }

    DisposableEffect(mapView) {
        mapView.overlayManager.add(overlay)
        onDispose { mapView.onDetach() }
    }

    LaunchedEffect(frameIndex, frames) {
        val frame = frames.all.getOrNull(frameIndex) ?: return@LaunchedEffect
        val tileSource = XYTileSource(
            "RainViewer-$frameIndex", 0, 19, 256, ".png",
            arrayOf(radarTileUrlTemplate(frames.host, frame).substringBefore("{z}"))
        )
        tileProvider.setTileSource(tileSource)
        mapView.invalidate()
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier
    )
}

@Composable
private fun RadarBottomPanel(
    frames: RainviewerFrames,
    frameIndex: Int,
    onFrameIndexChange: (Int) -> Unit,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val zone = ZoneId.systemDefault()
    val currentFrame = frames.all.getOrNull(frameIndex)
    val timeFormatted = currentFrame?.let {
        Instant.ofEpochSecond(it.time).atZone(zone).format(DateTimeFormatter.ofPattern("EEE d MMM - HH:mm", Locale.FRANCE))
    } ?: ""
    val isForecast = frameIndex > frames.nowIndex

    Surface(
        modifier = modifier,
        color = Color.Black.copy(alpha = 0.55f)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isForecast) "Prevision - $timeFormatted" else "Radar - $timeFormatted",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (isPlaying) AppColors.WindHigh.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.2f))
                        .clickable { onTogglePlay() }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(text = if (isPlaying) "Pause" else "Lecture", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Slider(
                value = frameIndex.toFloat(),
                onValueChange = { onFrameIndexChange(it.toInt()) },
                valueRange = 0f..(frames.all.size - 1).coerceAtLeast(1).toFloat(),
                steps = (frames.all.size - 2).coerceAtLeast(0),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = AppColors.WindHigh,
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                )
            )
        }
    }
}
