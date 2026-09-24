@file:Suppress("SpellCheckingInspection")
package com.surfcast.surfforecast

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.content.ContextCompat
import com.surfcast.surfforecast.ui.theme.AppColors
import kotlinx.coroutines.delay
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.TilesOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Les calques radar disponibles. Temperature utilise OpenWeatherMap (cle API gratuite,
 * cf. OPENWEATHERMAP_API_KEY dans local.properties). Foudre et Vent ne sont pas retenus. */
enum class RadarLayer { PRECIPITATION, CLOUDS, TEMPERATURE }

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

/** Une trame factice pour les calques sans historique (Temperature) : juste "maintenant". */
private val staticNowFrame = RadarFrame(time = System.currentTimeMillis() / 1000, path = "")

/** Trames du calque actif : precipitation = past+nowcast (radar), nuages = satellite (past
 * uniquement), temperature = une seule trame "actuelle" (OpenWeatherMap gratuit n'a pas
 * d'historique/prevision). */
private fun RainviewerFrames.framesFor(layer: RadarLayer): List<RadarFrame> = when (layer) {
    RadarLayer.PRECIPITATION -> all
    RadarLayer.CLOUDS -> satellite
    RadarLayer.TEMPERATURE -> listOf(staticNowFrame)
}

/** Index "maintenant" dans les trames du calque actif (derniere trame passee = la plus recente). */
private fun RainviewerFrames.nowIndexFor(layer: RadarLayer): Int = when (layer) {
    RadarLayer.PRECIPITATION -> nowIndex
    RadarLayer.CLOUDS -> (satellite.size - 1).coerceAtLeast(0)
    RadarLayer.TEMPERATURE -> 0
}

/** Les calques avec defilement temporel (slider + lecture) : Temperature est une image fixe. */
private fun RadarLayer.hasTimeControls(): Boolean = this != RadarLayer.TEMPERATURE

@Composable
fun RadarScreen(
    centerLat: Double,
    centerLon: Double,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current

    var frames by remember { mutableStateOf<RainviewerFrames?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var frameIndex by remember { mutableStateOf(0) }
    var isPlaying by remember { mutableStateOf(false) }
    var selectedLayer by remember { mutableStateOf(RadarLayer.PRECIPITATION) }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var locationError by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            val result = RainviewerRepository().fetchFrames()
            frames = result
            frameIndex = result.nowIndex
        } catch (e: Exception) {
            loadError = e.message ?: "Chargement du radar impossible"
        }
    }

    // Changer de calque repart sur la trame "maintenant" de ce calque (les deux calques
    // n'ont pas forcement le meme nombre de trames ni le meme pas de temps).
    LaunchedEffect(selectedLayer, frames) {
        val f = frames ?: return@LaunchedEffect
        isPlaying = false
        frameIndex = f.nowIndexFor(selectedLayer)
    }

    LaunchedEffect(isPlaying, frames, selectedLayer) {
        val f = frames ?: return@LaunchedEffect
        if (!selectedLayer.hasTimeControls()) return@LaunchedEffect
        val activeFrames = f.framesFor(selectedLayer)
        if (activeFrames.isEmpty()) return@LaunchedEffect
        while (isPlaying) {
            delay(600)
            frameIndex = (frameIndex + 1) % activeFrames.size
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            locationError = false
            locateUserOnMap(context, mapViewRef) { locationError = true }
        } else {
            locationError = true
        }
    }

    fun onLocateClick() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            locationError = false
            locateUserOnMap(context, mapViewRef) { locationError = true }
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    val temperatureApiKey = BuildConfig.OPENWEATHERMAP_API_KEY
    val temperatureKeyMissing = selectedLayer == RadarLayer.TEMPERATURE && temperatureApiKey.isBlank()

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize()) {
            val currentFrames = frames
            val activeFrames = currentFrames?.framesFor(selectedLayer) ?: emptyList()
            when {
                temperatureKeyMissing -> {
                    Surface(modifier = Modifier.fillMaxSize(), color = colors.background) {
                        Text(
                            text = "Calque Temperature : ajoute ta cle API OpenWeatherMap gratuite " +
                                "dans local.properties (OPENWEATHERMAP_API_KEY=...) pour l'activer.",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            color = colors.onBackground
                        )
                    }
                }
                // Le calque Temperature (OpenWeatherMap) ne depend pas des trames RainViewer :
                // on l'affiche des que la cle API est presente, sans attendre/exiger le succes
                // du fetch RainViewer (host inutilise pour ce calque).
                selectedLayer == RadarLayer.TEMPERATURE -> {
                    RadarMapView(
                        host = currentFrames?.host ?: "",
                        layer = selectedLayer,
                        layerFrames = activeFrames,
                        frameIndex = frameIndex,
                        centerLat = centerLat,
                        centerLon = centerLon,
                        temperatureApiKey = temperatureApiKey,
                        onMapReady = { mapViewRef = it },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                currentFrames != null && activeFrames.isNotEmpty() -> {
                    RadarMapView(
                        host = currentFrames.host,
                        layer = selectedLayer,
                        layerFrames = activeFrames,
                        frameIndex = frameIndex,
                        centerLat = centerLat,
                        centerLon = centerLon,
                        temperatureApiKey = temperatureApiKey,
                        onMapReady = { mapViewRef = it },
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
                currentFrames != null -> {
                    // Trames chargees mais vides pour ce calque (ex : pas de satellite disponible).
                    Surface(modifier = Modifier.fillMaxSize(), color = colors.background) {
                        Text(
                            text = "Calque indisponible pour le moment.",
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

            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable { onDismiss() }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(text = "Fermer", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable { onLocateClick() },
                    contentAlignment = Alignment.Center
                ) {
                    LocateMeIcon(color = Color.White, modifier = Modifier.size(18.dp))
                }

                if (locationError) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Position indisponible",
                            color = Color.White,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            RadarLayerSelector(
                selectedLayer = selectedLayer,
                onLayerSelected = { selectedLayer = it },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp)
            )

            if (currentFrames != null && activeFrames.isNotEmpty() && selectedLayer.hasTimeControls()) {
                RadarBottomPanel(
                    layer = selectedLayer,
                    layerFrames = activeFrames,
                    nowIndex = currentFrames.nowIndexFor(selectedLayer),
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

/** Centre + zoome la carte sur la derniere position connue de l'utilisateur, via l'overlay
 * de localisation d'osmdroid (pas de dependance Play Services necessaire). */
@SuppressLint("MissingPermission")
private fun locateUserOnMap(context: Context, mapView: MapView?, onError: () -> Unit) {
    val map = mapView ?: return
    val provider = GpsMyLocationProvider(context)
    val overlay = MyLocationNewOverlay(provider, map)
    overlay.runOnFirstFix {
        val location = overlay.myLocation
        map.post {
            if (location != null) {
                map.controller.animateTo(location)
                map.controller.setZoom(11.0)
            } else {
                onError()
            }
            overlay.disableMyLocation()
        }
    }
    overlay.enableMyLocation()
}

@Composable
private fun LocateMeIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val center = Offset(w / 2f, h / 2f)
        val radius = minOf(w, h) / 2f * 0.55f
        drawCircle(color = color, radius = radius, center = center, style = Stroke(width = w * 0.09f))
        drawCircle(color = color, radius = w * 0.07f, center = center)
        drawLine(color = color, start = Offset(center.x, 0f), end = Offset(center.x, h * 0.18f), strokeWidth = w * 0.08f)
        drawLine(color = color, start = Offset(center.x, h), end = Offset(center.x, h * 0.82f), strokeWidth = w * 0.08f)
        drawLine(color = color, start = Offset(0f, center.y), end = Offset(w * 0.18f, center.y), strokeWidth = w * 0.08f)
        drawLine(color = color, start = Offset(w, center.y), end = Offset(w * 0.82f, center.y), strokeWidth = w * 0.08f)
    }
}

@Composable
private fun CloudsIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        drawCircle(color = color, radius = w * 0.22f, center = Offset(w * 0.35f, h * 0.55f))
        drawCircle(color = color, radius = w * 0.28f, center = Offset(w * 0.58f, h * 0.45f))
        drawCircle(color = color, radius = w * 0.18f, center = Offset(w * 0.78f, h * 0.58f))
        drawRect(
            color = color,
            topLeft = Offset(w * 0.22f, h * 0.5f),
            size = androidx.compose.ui.geometry.Size(w * 0.6f, h * 0.28f)
        )
    }
}

@Composable
private fun PrecipitationIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.5f, h * 0.05f)
            cubicTo(w * 0.95f, h * 0.55f, w * 0.8f, h * 0.95f, w * 0.5f, h * 0.95f)
            cubicTo(w * 0.2f, h * 0.95f, w * 0.05f, h * 0.55f, w * 0.5f, h * 0.05f)
            close()
        }
        drawPath(path = path, color = color)
    }
}

@Composable
private fun TemperatureIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stemLeft = w * 0.42f
        val stemWidth = w * 0.16f
        val bulbCenter = Offset(w * 0.5f, h * 0.78f)
        val bulbRadius = w * 0.22f

        drawRect(
            color = color,
            topLeft = Offset(stemLeft, h * 0.06f),
            size = androidx.compose.ui.geometry.Size(stemWidth, h * 0.62f)
        )
        drawCircle(color = color, radius = bulbRadius, center = bulbCenter)
    }
}

@Composable
private fun RadarLayerSelector(
    selectedLayer: RadarLayer,
    onLayerSelected: (RadarLayer) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val entries = listOf(RadarLayer.PRECIPITATION, RadarLayer.TEMPERATURE, RadarLayer.CLOUDS)
        entries.forEach { layer ->
            val isSelected = layer == selectedLayer
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) AppColors.TideHigh.copy(alpha = 0.9f) else Color.Transparent)
                    .clickable { onLayerSelected(layer) },
                contentAlignment = Alignment.Center
            ) {
                when (layer) {
                    RadarLayer.PRECIPITATION -> PrecipitationIcon(color = Color.White, modifier = Modifier.size(16.dp))
                    RadarLayer.CLOUDS -> CloudsIcon(color = Color.White, modifier = Modifier.size(18.dp))
                    RadarLayer.TEMPERATURE -> TemperatureIcon(color = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun RadarMapView(
    host: String,
    layer: RadarLayer,
    layerFrames: List<RadarFrame>,
    frameIndex: Int,
    centerLat: Double,
    centerLon: Double,
    temperatureApiKey: String,
    onMapReady: (MapView) -> Unit,
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
        onMapReady(mapView)
        onDispose { mapView.onDetach() }
    }

    LaunchedEffect(frameIndex, layer, layerFrames, temperatureApiKey) {
        val urlTemplate = when (layer) {
            RadarLayer.PRECIPITATION -> {
                val frame = layerFrames.getOrNull(frameIndex) ?: return@LaunchedEffect
                radarTileUrlTemplate(host, frame)
            }
            RadarLayer.CLOUDS -> {
                val frame = layerFrames.getOrNull(frameIndex) ?: return@LaunchedEffect
                satelliteTileUrlTemplate(host, frame)
            }
            RadarLayer.TEMPERATURE -> temperatureTileUrlTemplate(temperatureApiKey)
        }
        // XYTileSource construit chaque URL de tuile comme baseUrl + z + "/" + x + "/" + y +
        // imageFilenameEnding, sans rien pouvoir inserer apres {y} : on doit donc decouper le
        // gabarit "{z}/{x}/{y}" autour de ce point plutot que de coller ".png" en dur, sinon
        // le suffixe (palette RainViewer, cle API OpenWeatherMap...) est perdu et les tuiles
        // ne chargent jamais.
        val baseUrl = urlTemplate.substringBefore("{z}")
        val imageFilenameEnding = urlTemplate.substringAfter("{y}")
        // Le niveau de zoom max de RainViewer (gratuit) est 7 : au-dela, leur serveur renvoie
        // une tuile "Zoom Level Not Supported" au lieu de la precipitation/des nuages. En le
        // declarant ici, osmdroid arrete de demander des tuiles au-dela et se contente
        // d'agrandir la derniere tuile z=7 valide (la carte de fond, elle, continue de zoomer
        // normalement). OpenWeatherMap tolere un zoom bien plus eleve.
        val maxZoom = if (layer == RadarLayer.TEMPERATURE) 18 else 7
        val tileSource = XYTileSource(
            "RainViewer-${layer.name}-$frameIndex", 0, maxZoom, 256, imageFilenameEnding,
            arrayOf(baseUrl)
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
    layer: RadarLayer,
    layerFrames: List<RadarFrame>,
    nowIndex: Int,
    frameIndex: Int,
    onFrameIndexChange: (Int) -> Unit,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val zone = ZoneId.systemDefault()
    val currentFrame = layerFrames.getOrNull(frameIndex)
    val timeFormatted = currentFrame?.let {
        Instant.ofEpochSecond(it.time).atZone(zone).format(DateTimeFormatter.ofPattern("EEE d MMM - HH:mm", Locale.FRANCE))
    } ?: ""
    val isForecast = frameIndex > nowIndex
    val label = when {
        layer == RadarLayer.CLOUDS -> "Nuages - $timeFormatted"
        isForecast -> "Prevision - $timeFormatted"
        else -> "Radar - $timeFormatted"
    }

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
                    text = label,
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
                valueRange = 0f..(layerFrames.size - 1).coerceAtLeast(1).toFloat(),
                steps = (layerFrames.size - 2).coerceAtLeast(0),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = AppColors.WindHigh,
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                )
            )
        }
    }
}
