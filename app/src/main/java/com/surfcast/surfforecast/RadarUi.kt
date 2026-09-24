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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Les calques radar disponibles, tous fournis par Xweather (ex-AerisWeather, cf.
 * XWEATHER_CLIENT_ID/XWEATHER_CLIENT_SECRET dans local.properties) : precipitation
 * previsionnelle, nuages (satellite visible), temperature. Meme source pour tous les jours
 * (J0 a J+3), y compris "aujourd'hui" -- contrairement a une precedente version qui melangeait
 * RainViewer (aujourd'hui uniquement) et OpenWeatherMap (temperature uniquement).
 */
enum class RadarLayer { PRECIPITATION, CLOUDS, TEMPERATURE }

/** Code de calque Xweather (segment d'URL apres les identifiants). */
private fun RadarLayer.xweatherLayerCode(): String = when (this) {
    RadarLayer.PRECIPITATION -> "precip-forecast"
    RadarLayer.CLOUDS -> "sat-vis"
    RadarLayer.TEMPERATURE -> "temperatures"
}

/**
 * Gabarit de tuile Xweather Raster Maps : tuiles PNG standard 256px, pas de decodage a faire
 * (contrairement a d'autres fournisseurs dont les tuiles encodent des valeurs dans les canaux
 * RGB). Le decalage temporel se passe en minutes depuis maintenant (negatif = passe, positif =
 * futur), ce qui permet d'utiliser le meme calque pour "aujourd'hui" et les jours suivants.
 */
private fun xweatherTileUrlTemplate(layerCode: String, minutesOffset: Long, clientId: String, clientSecret: String): String {
    return "https://maps1.aerisapi.com/${clientId}_${clientSecret}/$layerCode/{z}/{x}/{y}/${minutesOffset}min.png"
}

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

private fun radarDayLabel(date: LocalDate, today: LocalDate): String {
    val days = ChronoUnit.DAYS.between(today, date)
    return when {
        days == 0L -> "Aujourd'hui"
        days == 1L -> "Demain"
        days > 1L -> "J+$days"
        else -> date.format(DateTimeFormatter.ofPattern("dd/MM"))
    }
}

@Composable
fun RadarScreen(
    centerLat: Double,
    centerLon: Double,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current

    val today = remember { LocalDate.now() }
    // Xweather (offre gratuite) couvre environ 4 jours de prevision : J0 a J+3.
    val availableDates = remember { (0..3).map { today.plusDays(it.toLong()) } }
    var selectedDate by remember { mutableStateOf(today) }
    var selectedHour by remember { mutableStateOf(LocalTime.now().hour) }
    var selectedLayer by remember { mutableStateOf(RadarLayer.PRECIPITATION) }
    var isPlaying by remember { mutableStateOf(false) }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var locationError by remember { mutableStateOf(false) }

    val minutesOffset = remember(selectedDate, selectedHour) {
        ChronoUnit.MINUTES.between(LocalDateTime.now(), LocalDateTime.of(selectedDate, LocalTime.of(selectedHour, 0)))
    }
    // Le satellite est une observation, pas une prevision : demander une image satellite dans
    // le futur (Demain/J+2/J+3) n'a pas de sens et ne renverrait rien. On reste sur "maintenant"
    // pour ce calque tant que le jour selectionne est dans le futur.
    val effectiveMinutesOffset = if (selectedLayer == RadarLayer.CLOUDS) minutesOffset.coerceAtMost(0L) else minutesOffset

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            delay(700)
            selectedHour = (selectedHour + 1) % 24
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

    val clientId = BuildConfig.XWEATHER_CLIENT_ID
    val clientSecret = BuildConfig.XWEATHER_CLIENT_SECRET
    val credentialsMissing = clientId.isBlank() || clientSecret.isBlank()

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (credentialsMissing) {
                Surface(modifier = Modifier.fillMaxSize(), color = colors.background) {
                    Text(
                        text = "Radar : ajoute tes identifiants Xweather gratuits dans local.properties " +
                            "(XWEATHER_CLIENT_ID=... et XWEATHER_CLIENT_SECRET=...) pour l'activer.",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        color = colors.onBackground
                    )
                }
            } else {
                RadarMapView(
                    layerCode = selectedLayer.xweatherLayerCode(),
                    minutesOffset = effectiveMinutesOffset,
                    clientId = clientId,
                    clientSecret = clientSecret,
                    centerLat = centerLat,
                    centerLon = centerLon,
                    onMapReady = { mapViewRef = it },
                    modifier = Modifier.fillMaxSize()
                )
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

                RadarDayTabs(
                    availableDates = availableDates,
                    today = today,
                    selectedDate = selectedDate,
                    onSelectDate = { selectedDate = it }
                )
            }

            if (!credentialsMissing) {
                RadarLayerSelector(
                    selectedLayer = selectedLayer,
                    onLayerSelected = { selectedLayer = it },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 16.dp)
                )

                RadarBottomPanel(
                    selectedDate = selectedDate,
                    today = today,
                    selectedHour = selectedHour,
                    onHourChange = {
                        isPlaying = false
                        selectedHour = it
                    },
                    isPlaying = isPlaying,
                    onTogglePlay = { isPlaying = !isPlaying },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        // La Dialog est en plein ecran (usePlatformDefaultWidth = false), donc sans
                        // ceci le slider/bouton Lecture se retrouvent sous la barre de navigation
                        // systeme (geste/boutons) et deviennent inutilisables.
                        .navigationBarsPadding()
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

/** Onglets de jour (Aujourd'hui/Demain/J+2/J+3) : meme calque Xweather pour tous, seul le
 * decalage temporel (minutesOffset) change. */
@Composable
private fun RadarDayTabs(
    availableDates: List<LocalDate>,
    today: LocalDate,
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit
) {
    Row(
        modifier = Modifier
            .widthIn(max = 280.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.45f))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        availableDates.forEach { date ->
            val isSelected = date == selectedDate
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (isSelected) AppColors.TideHigh.copy(alpha = 0.9f) else Color.Transparent)
                    .clickable { onSelectDate(date) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = radarDayLabel(date, today),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun RadarMapView(
    layerCode: String,
    minutesOffset: Long,
    clientId: String,
    clientSecret: String,
    centerLat: Double,
    centerLon: Double,
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
    val tileProvider = remember { MapTileProviderBasic(context) }
    val overlay = remember {
        TilesOverlay(tileProvider, context).apply { loadingBackgroundColor = android.graphics.Color.TRANSPARENT }
    }

    DisposableEffect(mapView) {
        mapView.overlayManager.add(overlay)
        onMapReady(mapView)
        onDispose { mapView.onDetach() }
    }

    LaunchedEffect(layerCode, minutesOffset, clientId, clientSecret) {
        val urlTemplate = xweatherTileUrlTemplate(layerCode, minutesOffset, clientId, clientSecret)
        // XYTileSource construit chaque URL de tuile comme baseUrl + z + "/" + x + "/" + y +
        // imageFilenameEnding, sans rien pouvoir inserer apres {y} : on doit donc decouper le
        // gabarit "{z}/{x}/{y}" autour de ce point plutot que de coller ".png" en dur, sinon le
        // decalage temporel (.../${minutesOffset}min.png) est perdu et les tuiles ne chargent
        // jamais (meme bug deja rencontre avec RainViewer).
        val baseUrl = urlTemplate.substringBefore("{z}")
        val imageFilenameEnding = urlTemplate.substringAfter("{y}")
        // Xweather ne documente pas de plafond de zoom accessible depuis cet environnement (doc
        // bloquee) : 12 est une valeur prudente issue des standards du secteur pour des calques
        // meteo (au-dela, la donnee n'apporte de toute facon plus de detail reel). A ajuster si
        // des tuiles d'erreur apparaissent en zoomant, comme observe avec RainViewer.
        val tileSource = XYTileSource(
            "Xweather-$layerCode-$minutesOffset", 0, 12, 256, imageFilenameEnding,
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
    selectedDate: LocalDate,
    today: LocalDate,
    selectedHour: Int,
    onHourChange: (Int) -> Unit,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val label = "${radarDayLabel(selectedDate, today)} - ${String.format(Locale.FRANCE, "%02dh00", selectedHour)}"

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
                value = selectedHour.toFloat(),
                onValueChange = { onHourChange(it.toInt()) },
                valueRange = 0f..23f,
                steps = 22,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = AppColors.WindHigh,
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                )
            )
        }
    }
}
