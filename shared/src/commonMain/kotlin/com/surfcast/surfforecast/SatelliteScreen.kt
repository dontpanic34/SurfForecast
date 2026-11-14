package com.surfcast.surfforecast

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.decodeToImageBitmap
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Canvas as GfxCanvas

/** Réponse d'une aide à la saisie : la position trouvée, ou le message qui explique pourquoi non. */
typealias SatelliteLocationResult = (position: Pair<Double, Double>?, message: String?) -> Unit

/**
 * Ce que l'appareil sait faire en plus : « Ma position », la position enregistrée dans une photo, la lecture d'un lien court
 * Google Maps, le bouton retour. Rien de tout cela sur le site (le bouton correspondant n'apparaît pas) ; l'appli Android les fournit.
 */
class SatellitePlatform(
    val locate: ((SatelliteLocationResult) -> Unit)? = null,
    val photoPosition: ((SatelliteLocationResult) -> Unit)? = null,
    val resolveLink: (suspend (String) -> Pair<Double, Double>?)? = null,
    val backHandler: @Composable (enabled: Boolean, onBack: () -> Unit) -> Unit = { _, _ -> }
)

private val MONTHS = listOf("janvier", "février", "mars", "avril", "mai", "juin", "juillet", "août", "septembre", "octobre", "novembre", "décembre")
private val PinRed = Color(0xFFE5392F)

private fun dateLabel(image: SatImage): String =
    "${frenchShortDayName(LocalDate(image.year, image.month, image.day))} ${image.day} ${MONTHS[image.month - 1]}"

private fun shortDate(image: SatImage): String = "${image.day} ${MONTHS[image.month - 1].take(4)}."

private suspend fun HttpClient.fetchText(url: String): String? = try {
    val r = get(url)
    if (r.status.isSuccess()) r.bodyAsText() else null
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {   // sur le web, un échec réseau est une kotlin.Error
    null
}

private suspend fun HttpClient.fetchImage(url: String): ImageBitmap? = try {
    val r = get(url)
    if (r.status.isSuccess()) r.body<ByteArray>().decodeToImageBitmap() else null
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    null
}

/**
 * Onglet « Vue satellite » (maquette `docs/maquettes/vue-satellite.html`) : les points enregistrés en haut (le spot affiché d'abord),
 * la photo Sentinel-2 du point choisi, le film de son évolution (lecture, flèches, curseur), la période, la vitesse, la marée,
 * et « Poser un point ». Les photos viennent de la branche `sat` : une tuile de 8 km par zone de spots (côte atlantique française
 * pour l'instant). Un point hors de ces tuiles n'a pas de photos.
 */
@Composable
fun SatelliteScreen(spotName: String, spotLat: Double, spotLon: Double, prefs: KeyValueStore, platform: SatellitePlatform, onClose: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val client = remember { defaultHttpClient() }
    DisposableEffect(client) { onDispose { client.close() } }

    var zones by remember { mutableStateOf<SatZones?>(null) }
    var failed by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(reload) {
        failed = false
        zones = client.fetchText(SatelliteSource.ZONES_URL)?.let { SatelliteModel.parseZones(it) }
        if (zones == null) failed = true
    }

    var userPoints by remember { mutableStateOf(SatelliteModel.loadPoints(prefs)) }
    var selected by remember { mutableIntStateOf(prefs.getInt("sat_selected", 0)) }
    var months by remember { mutableIntStateOf(prefs.getInt("sat_months", 3)) }
    var speed by remember { mutableIntStateOf(prefs.getInt("sat_speed", 1)) }
    var lowTideOnly by remember { mutableStateOf(false) }
    var currentDate by remember { mutableStateOf<String?>(null) }
    var playing by remember { mutableStateOf(false) }
    var placing by remember { mutableStateOf(false) }

    platform.backHandler(true) { if (placing) placing = false else onClose() }

    Surface(color = colors.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Vue satellite", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
                    Text("Photos Sentinel-2 de la côte, tous les 2 à 5 jours", fontSize = 12.sp, color = colors.onSurfaceVariant)
                }
                IconButton(onClick = onClose) { Icon(SurfIcons.Close, contentDescription = "Fermer", tint = colors.onBackground) }
            }

            val allZones = zones
            if (allZones == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (failed) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Les photos ne se chargent pas.", fontSize = 14.sp, color = colors.onBackground)
                        Text("Vérifie ta connexion.", fontSize = 12.sp, color = colors.onSurfaceVariant)
                        Chip("Réessayer", selected = true) { reload++ }
                    } else CircularProgressIndicator()
                }
                return@Column
            }

            val points = remember(userPoints, spotName, spotLat, spotLon) { listOf(SatPoint(spotName, spotLat, spotLon)) + userPoints }
            val point = points.getOrElse(selected) { points.first() }
            val zone = allZones.zoneFor(point.lat, point.lon)

            if (placing) {
                PlacePointSheet(
                    client = client, zones = allZones, reference = points.first(), existing = points, platform = platform,
                    onSave = { p ->
                        userPoints = userPoints + p
                        SatelliteModel.savePoints(prefs, userPoints)
                        selected = userPoints.size
                        prefs.putInt("sat_selected", selected)
                        currentDate = null
                        placing = false
                    },
                    onCancel = { placing = false }
                )
                return@Column
            }

            // Le cadre de la zone du point.
            var index by remember(zone?.id) { mutableStateOf<SatIndex?>(null) }
            var indexFailed by remember(zone?.id) { mutableStateOf(false) }
            LaunchedEffect(zone?.id, reload) {
                indexFailed = false
                if (zone != null) {
                    index = client.fetchText(SatelliteSource.indexUrl(zone.id))?.let { SatelliteModel.parseIndex(it) }
                    if (index == null) indexFailed = true
                }
            }
            val idx = index

            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                // Les points enregistrés.
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    points.forEachIndexed { i, p ->
                        Chip(p.name, selected = i == selected) {
                            playing = false; selected = i; currentDate = null; prefs.putInt("sat_selected", i)
                        }
                    }
                    Chip("+ Poser un point", selected = false, outlined = true) { playing = false; placing = true }
                }

                if (zone == null) {
                    NoticeBox("Pas encore de photos ici", "La vue satellite couvre pour l'instant la côte atlantique française, autour des spots du catalogue. Pose un point sur cette côte, ou choisis un autre spot.")
                } else if (idx == null) {
                    if (indexFailed) NoticeBox("Les photos de ${zone.name} ne se chargent pas", "Vérifie ta connexion, puis réessaie.", "Réessayer") { reload++ }
                    else Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else if (idx.images.isEmpty()) {
                    NoticeBox("Les photos de ${zone.name} arrivent", "La collecte de cette zone est en cours : reviens dans quelques heures.")
                } else {
                    SatelliteBody(
                        client = client, zone = zone, idx = idx, point = point, points = points, selected = selected,
                        months = months, onMonths = { playing = false; months = it; prefs.putInt("sat_months", it) },
                        speed = speed, onSpeed = { speed = it; prefs.putInt("sat_speed", it) },
                        lowTideOnly = lowTideOnly, onLowTide = { playing = false; lowTideOnly = it },
                        currentDate = currentDate, onDate = { currentDate = it },
                        playing = playing, onPlaying = { playing = it },
                        referenceName = spotName,
                        onDelete = {
                            userPoints = userPoints.filterIndexed { i, _ -> i != selected - 1 }
                            SatelliteModel.savePoints(prefs, userPoints)
                            selected = 0; prefs.putInt("sat_selected", 0); currentDate = null
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun NoticeBox(title: String, text: String, action: String? = null, onAction: () -> Unit = {}) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(14.dp), color = colors.surface) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = colors.onSurface)
            Text(text, fontSize = 13.sp, color = colors.onSurfaceVariant)
            if (action != null) Chip(action, selected = true, onClick = onAction)
        }
    }
}

/** La photo, le film et les réglages d'un point dont la zone et le cadre sont connus. */
@Composable
private fun SatelliteBody(
    client: HttpClient, zone: SatZone, idx: SatIndex, point: SatPoint, points: List<SatPoint>, selected: Int,
    months: Int, onMonths: (Int) -> Unit, speed: Int, onSpeed: (Int) -> Unit, lowTideOnly: Boolean, onLowTide: (Boolean) -> Unit,
    currentDate: String?, onDate: (String?) -> Unit, playing: Boolean, onPlaying: (Boolean) -> Unit, referenceName: String, onDelete: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val frames = remember(idx, point, months, lowTideOnly) { SatelliteModel.frames(idx, point.lat, months, clearOnly = true, lowTideOnly = lowTideOnly) }
    val current = frames.firstOrNull { it.date == currentDate } ?: frames.lastOrNull()
    val bitmaps = rememberTileCrops(client, zone, frames, current, idx, point)

    // Le film : une photo toutes les 2,2 s (lent), 1,4 s (normal) ou 0,8 s (rapide). On n'avance que quand la photo suivante est
    // chargée : la photo en cours reste affichée, jamais d'écran noir entre deux dates.
    LaunchedEffect(playing, frames, speed) {
        if (!playing) return@LaunchedEffect
        if (frames.size < 2) { onPlaying(false); return@LaunchedEffect }
        if (current == frames.last()) onDate(frames.first().date)
        var date = if (current == frames.last()) frames.first().date else current?.date
        while (true) {
            delay(longArrayOf(2200L, 1400L, 800L)[speed.coerceIn(0, 2)])
            val k = frames.indexOfFirst { it.date == date }
            if (k < 0 || k >= frames.size - 1) { onPlaying(false); break }
            var waited = 0
            while (bitmaps.get(frames[k + 1]) == null && waited < 100) { delay(100); waited++ }
            date = frames[k + 1].date
            onDate(date)
        }
    }

    // La photo du point.
    Box(Modifier.fillMaxWidth().aspectRatio(SatelliteSource.VIEW_COLS.toFloat() / SatelliteSource.VIEW_ROWS).clip(RoundedCornerShape(14.dp)).background(Color.Black)) {
        val crop = current?.let { bitmaps.get(it) }
        var lastCrop by remember(point) { mutableStateOf<Crop?>(null) }
        if (crop != null) lastCrop = crop
        val shown = crop ?: lastCrop
        if (shown != null) PointView(shown, idx, point) else if (current != null) CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
        if (current != null) {
            Column(
                Modifier.align(Alignment.TopStart).padding(8.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xC7061018)).padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(dateLabel(current), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("vers midi" + (current.tide?.let { " · " + it.label } ?: ""), fontSize = 12.sp, color = Color.White)
            }
        } else {
            Text(
                "Aucune photo avec ces choix.\nEssaie une période plus longue.",
                fontSize = 13.sp, color = Color.White, modifier = Modifier.align(Alignment.Center).padding(16.dp)
            )
        }
        Text("Copernicus Sentinel-2", fontSize = 9.sp, color = Color(0xCCFFFFFF), modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp))
    }

    // Lecture, flèches, curseur.
    Surface(shape = RoundedCornerShape(14.dp), color = colors.surface) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(colors.primary).clickable { onPlaying(!playing) },
                contentAlignment = Alignment.Center
            ) { PlayGlyph(playing, colors.onPrimary) }
            StepArrow(left = true, color = colors.onSurface) {
                onPlaying(false)
                val k = frames.indexOf(current) - 1
                if (k >= 0) onDate(frames[k].date)
            }
            Column(Modifier.weight(1f)) {
                val k = frames.indexOf(current).coerceAtLeast(0)
                if (frames.size > 1) Slider(
                    value = k.toFloat(), valueRange = 0f..(frames.size - 1).toFloat(), steps = (frames.size - 2).coerceAtLeast(0),
                    onValueChange = { onPlaying(false); onDate(frames[it.roundToInt().coerceIn(0, frames.size - 1)].date) }
                )
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(frames.firstOrNull()?.let { shortDate(it) } ?: "", fontSize = 10.5.sp, color = colors.onSurfaceVariant)
                    val loaded = bitmaps.loaded(frames)
                    Text(if (loaded < frames.size) "chargement $loaded/${frames.size}" else "${k + 1} / ${frames.size}", fontSize = 10.5.sp, color = colors.onSurfaceVariant)
                    Text(frames.lastOrNull()?.let { shortDate(it) } ?: "", fontSize = 10.5.sp, color = colors.onSurfaceVariant)
                }
            }
            StepArrow(left = false, color = colors.onSurface) {
                onPlaying(false)
                val k = frames.indexOf(current) + 1
                if (k in 1 until frames.size) onDate(frames[k].date)
            }
        }
    }

    OptionRow("Période") {
        listOf(1 to "1 mois", 3 to "3 mois", 6 to "6 mois", 12 to "1 an").forEach { (m, label) -> Chip(label, selected = months == m) { onMonths(m) } }
    }
    OptionRow("Vitesse") {
        listOf("Lent", "Normal", "Rapide").forEachIndexed { k, label -> Chip(label, selected = speed == k) { onSpeed(k) } }
    }
    OptionRow("Marée") {
        Chip("Toutes", selected = !lowTideOnly) { onLowTide(false) }
        Chip("Basse seulement", selected = lowTideOnly) { onLowTide(true) }
    }
    Text(
        "${zone.name} · " + (if (lowTideOnly) "marée basse seulement : les bancs se comparent mieux. " else "") + "Pince pour zoomer, glisse pour te déplacer.",
        fontSize = 11.5.sp, color = colors.onSurfaceVariant
    )
    if (selected > 0 && selected < points.size) {
        Text(
            "${point.name} : ${satDistanceWord(point.lat, point.lon, points.first().lat, points.first().lon)} de $referenceName · Supprimer ce point",
            fontSize = 12.sp, color = colors.primary,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onDelete).padding(vertical = 4.dp)
        )
    }
}

/** Une photo découpée autour du point (480 sur 480 pixels, 4,8 km) : le bitmap, son coin haut-gauche et sa taille en pixels du cadre. */
private class Crop(val bitmap: ImageBitmap, val topRow: Float, val leftCol: Float, val size: Float)

private const val CROP_SIZE = 480

/** Les fenêtres déjà téléchargées. Chacune pèse environ 0,9 Mo au lieu de 2,5 Mo pour la tuile entière. */
private class TileCrops(private val map: Map<String, Crop>) {
    fun get(image: SatImage): Crop? = map[image.file]
    fun loaded(frames: List<SatImage>): Int = frames.count { it.file in map }
}

private fun cropOf(src: ImageBitmap, index: SatIndex, point: SatPoint): Crop {
    val k = src.height.toFloat() / index.height
    val size = CROP_SIZE.toFloat().coerceAtMost(index.height.toFloat())
    val top = (index.rowOf(point.lat).toFloat() - size / 2).coerceIn(0f, index.height - size)
    val left = (index.colOf(point.lon).toFloat() - size / 2).coerceIn(0f, index.width - size)
    val px = (size * k).roundToInt().coerceAtLeast(1)
    val out = ImageBitmap(px, px)
    GfxCanvas(out).drawImageRect(
        src, srcOffset = IntOffset((left * k).roundToInt(), (top * k).roundToInt()), srcSize = IntSize(px, px),
        dstOffset = IntOffset.Zero, dstSize = IntSize(px, px), paint = Paint()
    )
    return Crop(out, top, left, size)
}

@Composable
private fun rememberTileCrops(client: HttpClient, zone: SatZone, frames: List<SatImage>, current: SatImage?, index: SatIndex, point: SatPoint): TileCrops {
    val map = remember(point) { mutableStateMapOf<String, Crop>() }
    // La photo affichée d'abord, puis les autres de la période (pour que le film soit fluide).
    LaunchedEffect(frames, current?.file, point) {
        val order = listOfNotNull(current) + frames.reversed()
        for (image in order) {
            if (image.file in map) continue
            val bitmap = client.fetchImage(SatelliteSource.imageUrl(zone.id, image.file)) ?: continue
            map[image.file] = cropOf(bitmap, index, point)
        }
    }
    return TileCrops(map)
}

/**
 * Rendu des photos : un peu plus de saturation et de contraste, pour séparer les bancs (sable, jaune) des baïnes (eau plus sombre).
 * Saturation 1,35, contraste 1,5 autour de 70, luminosité +12 (essayé sur la photo du 7 mai 2026). Sentinel-2 ne donne que 10 m
 * par pixel : cela améliore la lecture, pas la finesse.
 */
private val SatEnhance: ColorFilter = run {
    val sat = 1.35f
    val c = 1.5f
    val t = 70f * (1f - c) + 12f
    ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                c * (0.213f + 0.787f * sat), c * (0.715f - 0.715f * sat), c * (0.072f - 0.072f * sat), 0f, t,
                c * (0.213f - 0.213f * sat), c * (0.715f + 0.285f * sat), c * (0.072f - 0.072f * sat), 0f, t,
                c * (0.213f - 0.213f * sat), c * (0.715f - 0.715f * sat), c * (0.072f + 0.928f * sat), 0f, t,
                0f, 0f, 0f, 1f, 0f
            )
        )
    )
}

/**
 * La fenêtre du point : 1,8 km sur 2,2 km au départ, centrée sur lui. On peut zoomer et dézoomer (pincer ou boutons − et +, jusqu'à
 * 4,4 km de haut) et se déplacer en glissant, dans les 4,8 km découpés autour du point.
 */
@Composable
private fun PointView(crop: Crop, index: SatIndex, point: SatPoint) {
    var zoom by remember(point) { mutableFloatStateOf(1f) }
    var panCols by remember(point) { mutableFloatStateOf(0f) }
    var panRows by remember(point) { mutableFloatStateOf(0f) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val minZoom = 0.5f
    val strip = crop.bitmap
    val k = strip.height.toFloat() / crop.size
    Box(Modifier.fillMaxSize()) {
        Canvas(
            Modifier.fillMaxSize().onSizeChanged { size = it }.pointerInput(point) {
                detectTransformGestures { _, pan, gestureZoom, _ ->
                    if (size.width == 0) return@detectTransformGestures
                    zoom = (zoom * gestureZoom).coerceIn(minZoom, 4f)
                    val rowsShown = SatelliteSource.VIEW_ROWS / zoom
                    panCols -= pan.x / size.height * rowsShown
                    panRows -= pan.y / size.height * rowsShown
                }
            }
        ) {
            val rows = (SatelliteSource.VIEW_ROWS / zoom).coerceAtMost(crop.size)
            val cols = (rows * this.size.width / this.size.height).coerceAtMost(crop.size)
            val left = (index.colOf(point.lon).toFloat() + panCols - cols / 2).coerceIn(crop.leftCol, crop.leftCol + crop.size - cols)
            val top = (index.rowOf(point.lat).toFloat() + panRows - rows / 2).coerceIn(crop.topRow, crop.topRow + crop.size - rows)
            drawImage(
                strip, srcOffset = IntOffset(((left - crop.leftCol) * k).roundToInt(), ((top - crop.topRow) * k).roundToInt()),
                srcSize = IntSize((cols * k).roundToInt().coerceAtLeast(1), (rows * k).roundToInt().coerceAtLeast(1)),
                dstSize = IntSize(this.size.width.roundToInt(), this.size.height.roundToInt()),
                filterQuality = FilterQuality.High, colorFilter = SatEnhance
            )
            // Le point lui-même.
            val px = (index.colOf(point.lon).toFloat() - left) / cols * this.size.width
            val py = (index.rowOf(point.lat).toFloat() - top) / rows * this.size.height
            drawPin(Offset(px, py), 9.dp.toPx())
            // Échelle : 500 m.
            val bar = 50f / cols * this.size.width
            val y = this.size.height - 12.dp.toPx()
            drawLine(Color.White, Offset(10.dp.toPx(), y), Offset(10.dp.toPx() + bar, y), strokeWidth = 3.dp.toPx())
        }
        Text("500 m", fontSize = 10.sp, color = Color.White, modifier = Modifier.align(Alignment.BottomStart).padding(start = 10.dp, bottom = 16.dp))
        Column(Modifier.align(Alignment.CenterEnd).padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ZoomButton("+") { zoom = (zoom * 1.5f).coerceAtMost(4f) }
            ZoomButton("−") { zoom = (zoom / 1.5f).coerceAtLeast(minZoom) }
        }
    }
}

@Composable
private fun ZoomButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(38.dp).clip(CircleShape).background(Color(0xC7061018)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(label, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White) }
}

private fun DrawScope.drawPin(at: Offset, r: Float) {
    drawCircle(Color.White, radius = r, center = at, style = Stroke(r * 0.35f))
    drawCircle(PinRed, radius = r * 0.45f, center = at)
}

@Composable
private fun OptionRow(label: String, content: @Composable () -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(62.dp))
        content()
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, outlined: Boolean = false, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClick),
        color = if (selected) colors.primaryContainer else if (outlined) Color.Transparent else colors.surfaceVariant,
        border = if (outlined) androidx.compose.foundation.BorderStroke(1.5.dp, colors.onSurfaceVariant.copy(alpha = 0.5f)) else null
    ) {
        Text(
            text, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1,
            color = if (selected) colors.onPrimaryContainer else if (outlined) colors.onSurfaceVariant else colors.onSurface,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        )
    }
}

@Composable
private fun PlayGlyph(playing: Boolean, color: Color) {
    Canvas(Modifier.size(16.dp)) {
        if (playing) {
            drawRect(color, topLeft = Offset(size.width * 0.15f, 0f), size = androidx.compose.ui.geometry.Size(size.width * 0.25f, size.height))
            drawRect(color, topLeft = Offset(size.width * 0.6f, 0f), size = androidx.compose.ui.geometry.Size(size.width * 0.25f, size.height))
        } else {
            drawPath(Path().apply { moveTo(size.width * 0.2f, 0f); lineTo(size.width, size.height / 2); lineTo(size.width * 0.2f, size.height); close() }, color)
        }
    }
}

@Composable
private fun StepArrow(left: Boolean, color: Color, onClick: () -> Unit) {
    Box(Modifier.size(width = 32.dp, height = 40.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(14.dp)) {
            val p = Path().apply {
                if (left) { moveTo(size.width * 0.7f, size.height * 0.1f); lineTo(size.width * 0.3f, size.height / 2); lineTo(size.width * 0.7f, size.height * 0.9f) }
                else { moveTo(size.width * 0.3f, size.height * 0.1f); lineTo(size.width * 0.7f, size.height / 2); lineTo(size.width * 0.3f, size.height * 0.9f) }
            }
            drawPath(p, color, style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Poser un point
// ---------------------------------------------------------------------------------------------

/**
 * Poser un point : on choisit une zone (les plus proches du spot affiché d'abord), la tuile de 8 km s'affiche, on la fait glisser et on la
 * zoome, puis on touche l'endroit où l'on surfe. Aides : « Ma position » (GPS), des coordonnées ou un lien Google Maps, la position
 * d'une photo (appli Android seulement pour la position, la photo et les liens courts). Les plages du catalogue sont écrites sur la tuile.
 */
@Composable
private fun PlacePointSheet(
    client: HttpClient, zones: SatZones, reference: SatPoint, existing: List<SatPoint>, platform: SatellitePlatform,
    onSave: (SatPoint) -> Unit, onCancel: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val ordered = remember(zones, reference) { zones.nearest(reference.lat, reference.lon) }
    var zone by remember { mutableStateOf(zones.zoneFor(reference.lat, reference.lon) ?: ordered.first()) }
    var index by remember(zone.id) { mutableStateOf<SatIndex?>(null) }
    var strip by remember(zone.id) { mutableStateOf<ImageBitmap?>(null) }
    var pin by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var message by remember { mutableStateOf("Choisis la zone, fais glisser la tuile, zoome, puis touche l'endroit où tu surfes.") }
    var name by remember { mutableStateOf("") }
    var coordsText by remember { mutableStateOf("") }
    var showCoords by remember { mutableStateOf(false) }
    var centerRow by remember { mutableFloatStateOf(0f) }
    var centerCol by remember { mutableFloatStateOf(0f) }
    var colsShown by remember { mutableFloatStateOf(1f) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val measurer = rememberTextMeasurer()

    // La tuile de la zone choisie : son cadre, puis sa dernière photo dégagée.
    LaunchedEffect(zone.id) {
        val idx = client.fetchText(SatelliteSource.indexUrl(zone.id))?.let { SatelliteModel.parseIndex(it) }
        index = idx
        if (idx == null || idx.images.isEmpty()) {
            message = "Les photos de ${zone.name} ne sont pas encore là."
            return@LaunchedEffect
        }
        colsShown = idx.width.toFloat()
        val target = pin?.takeIf { idx.contains(it.first, it.second) }
        centerRow = idx.rowOf(target?.first ?: zone.lat).toFloat()
        centerCol = idx.colOf(target?.second ?: zone.lon).toFloat()
        if (target != null) colsShown = colsShown.coerceAtMost(260f)
        val image = idx.images.lastOrNull { !idx.isCloudy(it, zone.lat) } ?: idx.images.last()
        strip = client.fetchImage(SatelliteSource.imageUrl(zone.id, image.file))
    }

    fun place(lat: Double, lon: Double, source: String) {
        val target = zones.zoneFor(lat, lon)
        if (target == null) {
            message = "$source : hors des zones photographiées (côte atlantique française, autour des spots)."
            return
        }
        pin = lat to lon
        message = "$source : point posé à ${satDistanceWord(lat, lon, reference.lat, reference.lon)} de ${reference.name}."
        if (target.id != zone.id) zone = target
        else {
            index?.let { idx ->
                centerRow = idx.rowOf(lat).toFloat(); centerCol = idx.colOf(lon).toFloat()
                colsShown = colsShown.coerceAtMost(260f)
            }
        }
    }

    val landmarks = remember(index) { index?.let { idx -> SurfDatabase.getAllSpots().filter { idx.contains(it.latitude, it.longitude) } } ?: emptyList() }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Poser un point", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.onBackground, modifier = Modifier.weight(1f))
            Chip("Annuler", selected = false, onClick = onCancel)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ordered.forEach { z -> Chip(z.name, selected = z.id == zone.id) { zone = z } }
        }
        Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(12.dp)).background(Color.Black).onSizeChanged { size = it }) {
            val idx = index
            val bmp = strip
            if (idx == null || bmp == null) CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
            else Canvas(
                Modifier.fillMaxSize()
                    .pointerInput(idx) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            if (size.width == 0) return@detectTransformGestures
                            colsShown = (colsShown / zoom).coerceIn(60f, idx.width.toFloat())
                            val perPx = colsShown / size.width
                            centerCol -= pan.x * perPx
                            centerRow -= pan.y * perPx
                        }
                    }
                    .pointerInput(idx) {
                        detectTapGestures { tap ->
                            if (size.width == 0) return@detectTapGestures
                            val perPx = colsShown / size.width
                            val rowsShown = size.height * perPx
                            val left = (centerCol - colsShown / 2).coerceIn(0f, (idx.width - colsShown).coerceAtLeast(0f))
                            val top = (centerRow - rowsShown / 2).coerceIn(0f, (idx.height - rowsShown).coerceAtLeast(0f))
                            val lat = idx.latOf((top + tap.y * perPx).toDouble())
                            val lon = idx.lonOf((left + tap.x * perPx).toDouble())
                            pin = lat to lon
                            message = "Point posé à ${satDistanceWord(lat, lon, reference.lat, reference.lon)} de ${reference.name}."
                        }
                    }
            ) {
                val perPx = colsShown / this.size.width
                val rowsShown = (this.size.height * perPx).coerceAtMost(idx.height.toFloat())
                val left = (centerCol - colsShown / 2).coerceIn(0f, (idx.width - colsShown).coerceAtLeast(0f))
                val top = (centerRow - rowsShown / 2).coerceIn(0f, (idx.height - rowsShown).coerceAtLeast(0f))
                val k = bmp.height.toFloat() / idx.height
                drawImage(
                    bmp, srcOffset = IntOffset((left * k).roundToInt(), (top * k).roundToInt()),
                    srcSize = IntSize((colsShown * k).roundToInt().coerceAtLeast(1), (rowsShown * k).roundToInt().coerceAtLeast(1)),
                    dstSize = IntSize(this.size.width.roundToInt(), this.size.height.roundToInt()),
                    filterQuality = FilterQuality.High, colorFilter = SatEnhance
                )
                fun pos(lat: Double, lon: Double) = Offset((idx.colOf(lon).toFloat() - left) / perPx, (idx.rowOf(lat).toFloat() - top) / perPx)
                // Les plages connues, pour se repérer.
                landmarks.forEach { spot ->
                    val p = pos(spot.latitude, spot.longitude)
                    if (p.y < 0 || p.y > this.size.height || p.x < 0 || p.x > this.size.width) return@forEach
                    drawCircle(Color.White, radius = 3.dp.toPx(), center = p)
                    val label = measurer.measure(spot.name, TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White))
                    val at = Offset((p.x + 8.dp.toPx()).coerceAtMost(this.size.width - label.size.width - 4.dp.toPx()), p.y - label.size.height / 2f)
                    drawRoundRect(
                        Color(0xB308121C), topLeft = Offset(at.x - 3.dp.toPx(), at.y - 1.dp.toPx()),
                        size = androidx.compose.ui.geometry.Size(label.size.width + 6.dp.toPx(), label.size.height + 2.dp.toPx()),
                        cornerRadius = CornerRadius(6.dp.toPx())
                    )
                    drawText(label, topLeft = at)
                }
                existing.drop(1).forEach { p -> if (idx.contains(p.lat, p.lon)) drawCircle(Color(0xFF7FC3E6), radius = 5.dp.toPx(), center = pos(p.lat, p.lon)) }
                pin?.let { (lat, lon) -> if (idx.contains(lat, lon)) drawPin(pos(lat, lon), 11.dp.toPx()) }
            }
            if (idx != null && bmp != null) Column(Modifier.align(Alignment.CenterEnd).padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ZoomButton("+") { colsShown = (colsShown / 1.5f).coerceAtLeast(60f) }
                ZoomButton("−") { colsShown = (colsShown * 1.5f).coerceAtMost(idx.width.toFloat()) }
            }
        }
        Text(message, fontSize = 12.sp, color = colors.onSurfaceVariant)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            platform.locate?.let { locate ->
                Chip("Ma position", selected = false) {
                    message = "Recherche de ta position…"
                    locate { position, why -> if (position == null) message = why ?: "Position introuvable." else place(position.first, position.second, "Ma position") }
                }
            }
            Chip("Coordonnées", selected = showCoords) { showCoords = !showCoords }
            platform.photoPosition?.let { photo ->
                Chip("Depuis une photo", selected = false) {
                    photo { position, why -> if (position == null) message = why ?: "Cette photo n'a pas de position enregistrée." else place(position.first, position.second, "Photo") }
                }
            }
        }
        if (showCoords) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = coordsText, onValueChange = { coordsText = it }, singleLine = true, modifier = Modifier.weight(1f),
                    placeholder = { Text("45.31, -1.16 ou lien Google Maps", fontSize = 13.sp) }
                )
                Chip("Placer", selected = true) {
                    val direct = SatelliteModel.parseCoordinates(coordsText)
                    val resolve = platform.resolveLink
                    if (direct != null) place(direct.first, direct.second, "Coordonnées")
                    else if (coordsText.contains("http") && resolve != null) {
                        // Lien court de partage (maps.app.goo.gl) : on le suit pour lire les coordonnées de l'adresse finale.
                        message = "Lecture du lien…"
                        scope.launch {
                            val c = resolve(coordsText)
                            if (c == null) message = "Ce lien ne donne pas de coordonnées. Dans Google Maps : appui long sur la carte, puis copie les deux nombres."
                            else place(c.first, c.second, "Lien")
                        }
                    } else if (coordsText.contains("http")) message = "Ce lien court ne se lit que dans l'appli Android. Colle plutôt les deux nombres (appui long sur la carte dans Google Maps)."
                    else message = "Coordonnées illisibles. Exemple : 45.31, -1.16, ou un lien Google Maps."
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                value = name, onValueChange = { name = it.take(24) }, singleLine = true, modifier = Modifier.weight(1f),
                placeholder = { Text("Nom du point (ex. Plage du vélo)", fontSize = 13.sp) }
            )
            val p = pin
            Chip("Enregistrer", selected = p != null) {
                if (p != null) onSave(SatPoint(name.trim().ifBlank { "Mon point" }, p.first, p.second))
                else message = "Pose d'abord un point sur la côte."
            }
        }
        Spacer(Modifier.height(2.dp))
    }
}
