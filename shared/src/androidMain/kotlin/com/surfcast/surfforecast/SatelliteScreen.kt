package com.surfcast.surfforecast

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

private val MONTHS = listOf("janvier", "février", "mars", "avril", "mai", "juin", "juillet", "août", "septembre", "octobre", "novembre", "décembre")
private val PinRed = Color(0xFFE5392F)

private fun dateLabel(image: SatImage): String =
    "${frenchShortDayName(LocalDate(image.year, image.month, image.day))} ${image.day} ${MONTHS[image.month - 1]}"

private fun shortDate(image: SatImage): String = "${image.day} ${MONTHS[image.month - 1].take(4)}."

/**
 * Onglet « Vue satellite » (maquette `docs/maquettes/vue-satellite.html`, appli Android seulement pour l'instant) :
 * les points enregistrés en haut, la photo Sentinel-2 du point choisi, le film de son évolution (lecture, flèches, ligne du temps),
 * la période, « Photos : Sans nuages / Toutes », « Marée : Toutes / Basse seulement », et « Poser un point ».
 */
@Composable
fun SatelliteScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val prefs = remember(context) { SharedPreferencesStore(context) }

    var index by remember { mutableStateOf<SatIndex?>(null) }
    var failed by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(reload) {
        failed = false
        val loaded = withContext(Dispatchers.IO) {
            runCatching {
                val conn = URL(SatelliteSource.INDEX_URL + "?t=" + System.currentTimeMillis() / 3_600_000).openConnection() as HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                SatelliteModel.parseIndex(conn.inputStream.bufferedReader().use { it.readText() })
            }.getOrNull()
        }
        if (loaded == null) failed = true else index = loaded
    }

    var userPoints by remember { mutableStateOf(SatelliteModel.loadPoints(prefs)) }
    var selected by remember { mutableIntStateOf(prefs.getInt("sat_selected", 0)) }
    var months by remember { mutableIntStateOf(prefs.getInt("sat_months", 3)) }
    var clearOnly by remember { mutableStateOf(prefs.getBoolean("sat_clear_only", true)) }
    var lowTideOnly by remember { mutableStateOf(false) }
    var currentDate by remember { mutableStateOf<String?>(null) }
    var playing by remember { mutableStateOf(false) }
    var placing by remember { mutableStateOf(false) }

    BackHandler { if (placing) placing = false else onClose() }

    Column(Modifier.fillMaxSize().background(colors.background).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Vue satellite", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
                Text("Photos Sentinel-2 de la côte, tous les 2 à 5 jours", fontSize = 12.sp, color = colors.onSurfaceVariant)
            }
            IconButton(onClick = onClose) { Icon(SurfIcons.Close, contentDescription = "Fermer", tint = colors.onBackground) }
        }

        val idx = index
        if (idx == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (failed) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Les photos ne se chargent pas.", fontSize = 14.sp, color = colors.onBackground)
                    Text("Vérifie ta connexion.", fontSize = 12.sp, color = colors.onSurfaceVariant)
                    Chip("Réessayer", selected = true) { reload++ }
                } else CircularProgressIndicator()
            }
            return@Column
        }

        val points = remember(userPoints, idx) { listOf(SatPoint("Montalivet", idx.homeLat, idx.homeLon)) + userPoints }
        val point = points.getOrElse(selected) { points.first() }
        val frames = remember(idx, point, months, clearOnly, lowTideOnly) { SatelliteModel.frames(idx, point.lat, months, clearOnly, lowTideOnly) }
        val current = frames.firstOrNull { it.date == currentDate } ?: frames.lastOrNull()
        val bitmaps = rememberStripBitmaps(frames, current)

        // Le film : une photo toutes les 0,9 s, de la plus ancienne à la plus récente.
        LaunchedEffect(playing, frames) {
            if (!playing) return@LaunchedEffect
            if (frames.size < 2) { playing = false; return@LaunchedEffect }
            if (current == frames.last()) currentDate = frames.first().date
            while (true) {
                delay(900)
                val k = frames.indexOfFirst { it.date == currentDate }
                if (k < 0 || k >= frames.size - 1) { playing = false; break }
                currentDate = frames[k + 1].date
            }
        }

        if (placing) {
            PlacePointSheet(
                index = idx,
                strip = (idx.images.lastOrNull { !idx.isCloudy(it, idx.homeLat) } ?: idx.images.lastOrNull())?.let { bitmaps.get(it) },
                existing = points,
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

            // La photo du point.
            Box(Modifier.fillMaxWidth().aspectRatio(SatelliteSource.VIEW_COLS.toFloat() / SatelliteSource.VIEW_ROWS).clip(RoundedCornerShape(14.dp)).background(Color.Black)) {
                val bmp = current?.let { bitmaps.get(it) }
                if (bmp != null) PointView(bmp, idx, point) else if (current != null) CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
                if (current != null) {
                    Column(
                        Modifier.align(Alignment.TopStart).padding(8.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xC7061018)).padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(dateLabel(current) + if (idx.isCloudy(current, point.lat)) " · nuages" else "", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("vers midi" + (current.tide?.let { " · " + it.label } ?: ""), fontSize = 12.sp, color = Color.White)
                    }
                } else {
                    Text(
                        "Aucune photo avec ces choix.\nEssaie « Toutes » ou une période plus longue.",
                        fontSize = 13.sp, color = Color.White, modifier = Modifier.align(Alignment.Center).padding(16.dp)
                    )
                }
                Text("Copernicus Sentinel-2", fontSize = 9.sp, color = Color(0xCCFFFFFF), modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp))
            }

            // Lecture, flèches, ligne du temps.
            Surface(shape = RoundedCornerShape(14.dp), color = colors.surface) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(colors.primary).clickable { playing = !playing },
                        contentAlignment = Alignment.Center
                    ) { PlayGlyph(playing, colors.onPrimary) }
                    StepArrow(left = true, color = colors.onSurface) {
                        playing = false
                        val k = frames.indexOf(current) - 1
                        if (k >= 0) currentDate = frames[k].date
                    }
                    Column(Modifier.weight(1f)) {
                        val all = remember(idx, months) { SatelliteModel.frames(idx, point.lat, months, clearOnly = false, lowTideOnly = false) }
                        Timeline(all = all, shown = frames, current = current, months = months, last = idx.images.lastOrNull()) { playing = false; currentDate = it.date }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(frames.firstOrNull()?.let { shortDate(it) } ?: "", fontSize = 10.5.sp, color = colors.onSurfaceVariant)
                            Text("${frames.size} photo${if (frames.size > 1) "s" else ""}", fontSize = 10.5.sp, color = colors.onSurfaceVariant)
                            Text(frames.lastOrNull()?.let { shortDate(it) } ?: "", fontSize = 10.5.sp, color = colors.onSurfaceVariant)
                        }
                    }
                    StepArrow(left = false, color = colors.onSurface) {
                        playing = false
                        val k = frames.indexOf(current) + 1
                        if (k in 1 until frames.size) currentDate = frames[k].date
                    }
                }
            }

            OptionRow("Période") {
                listOf(1 to "1 mois", 3 to "3 mois", 6 to "6 mois", 12 to "1 an").forEach { (m, label) ->
                    Chip(label, selected = months == m) { playing = false; months = m; prefs.putInt("sat_months", m) }
                }
            }
            OptionRow("Photos") {
                Chip("Sans nuages", selected = clearOnly) { playing = false; clearOnly = true; prefs.putBoolean("sat_clear_only", true) }
                Chip("Toutes", selected = !clearOnly) { playing = false; clearOnly = false; prefs.putBoolean("sat_clear_only", false) }
            }
            OptionRow("Marée") {
                Chip("Toutes", selected = !lowTideOnly) { playing = false; lowTideOnly = false }
                Chip("Basse seulement", selected = lowTideOnly) { playing = false; lowTideOnly = true }
            }

            val cloudy = remember(idx, point, months) { SatelliteModel.frames(idx, point.lat, months, false, false).count { idx.isCloudy(it, point.lat) } }
            Text(
                (if (clearOnly) "$cloudy photo(s) nuageuse(s) sautée(s) sur ce point. " else "Toutes les photos : $cloudy nuageuse(s) sur ce point. ") +
                    (if (lowTideOnly) "Marée basse seulement : les bancs se comparent mieux. " else "") +
                    "Pince pour zoomer, glisse pour te déplacer.",
                fontSize = 11.5.sp, color = colors.onSurfaceVariant
            )
            if (selected > 0 && selected < points.size) {
                Text(
                    "${point.name} : ${idx.distanceWord(point.lat, point.lon)} de Montalivet · Supprimer ce point",
                    fontSize = 12.sp, color = colors.primary,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                        userPoints = userPoints.filterIndexed { i, _ -> i != selected - 1 }
                        SatelliteModel.savePoints(prefs, userPoints)
                        selected = 0; prefs.putInt("sat_selected", 0); currentDate = null
                    }.padding(vertical = 4.dp)
                )
            }
        }
    }
}

/** Les bandes déjà téléchargées (gardées en mémoire le temps de l'écran ; le disque est géré par Coil). */
private class StripBitmaps(private val map: Map<String, ImageBitmap>) {
    fun get(image: SatImage): ImageBitmap? = map[image.file]
}

@Composable
private fun rememberStripBitmaps(frames: List<SatImage>, current: SatImage?): StripBitmaps {
    val context = LocalContext.current
    val map = remember { mutableStateMapOf<String, ImageBitmap>() }
    // La photo affichée d'abord, puis les autres de la période (pour que le film soit fluide).
    LaunchedEffect(frames, current?.file) {
        val order = listOfNotNull(current) + frames.reversed()
        for (image in order) {
            if (image.file in map) continue
            val request = ImageRequest.Builder(context).data(SatelliteSource.BASE_URL + image.file).allowHardware(false).build()
            val drawable = context.imageLoader.execute(request).drawable as? BitmapDrawable ?: continue
            map[image.file] = drawable.bitmap.asImageBitmap()
            if (map.size > 40) map.keys.firstOrNull { k -> k != current?.file && frames.none { it.file == k } }?.let { map.remove(it) }
        }
    }
    return StripBitmaps(map)
}

/** La fenêtre du point dans la bande : 1,8 km sur 2,2 km, un peu décalée vers le large. On peut zoomer et se déplacer. */
@Composable
private fun PointView(strip: ImageBitmap, index: SatIndex, point: SatPoint) {
    var zoom by remember(point) { mutableFloatStateOf(1f) }
    var panCols by remember(point) { mutableFloatStateOf(0f) }
    var panRows by remember(point) { mutableFloatStateOf(0f) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    Canvas(
        Modifier.fillMaxSize().onSizeChanged { size = it }.pointerInput(point) {
            detectTransformGestures { _, pan, gestureZoom, _ ->
                if (size.width == 0) return@detectTransformGestures
                zoom = (zoom * gestureZoom).coerceIn(0.35f, 4f)
                val rowsShown = SatelliteSource.VIEW_ROWS / zoom
                panCols -= pan.x / size.height * rowsShown
                panRows -= pan.y / size.height * rowsShown
            }
        }
    ) {
        val rows = (SatelliteSource.VIEW_ROWS / zoom).coerceAtMost(strip.height.toFloat())
        val cols = (rows * this.size.width / this.size.height).coerceAtMost(strip.width.toFloat())
        val left = (index.colOf(point.lon).toFloat() - 25f + panCols - cols / 2).coerceIn(0f, strip.width - cols)
        val top = (index.rowOf(point.lat).toFloat() + panRows - rows / 2).coerceIn(0f, strip.height - rows)
        drawImage(
            strip, srcOffset = IntOffset(left.roundToInt(), top.roundToInt()), srcSize = IntSize(cols.roundToInt(), rows.roundToInt()),
            dstSize = IntSize(this.size.width.roundToInt(), this.size.height.roundToInt())
        )
        // Le point lui-même.
        val px = (index.colOf(point.lon).toFloat() - left) / cols * this.size.width
        val py = (index.rowOf(point.lat).toFloat() - top) / rows * this.size.height
        drawPin(Offset(px, py), 9.dp.toPx())
        // Échelle : 500 m.
        val bar = 50f / cols * this.size.width
        val y = this.size.height - 14.dp.toPx()
        drawLine(Color.White, Offset(10.dp.toPx(), y), Offset(10.dp.toPx() + bar, y), strokeWidth = 3.dp.toPx())
    }
    Text("500 m", fontSize = 10.sp, color = Color.White, modifier = Modifier.padding(start = 10.dp, top = 4.dp))
}

private fun DrawScope.drawPin(at: Offset, r: Float) {
    drawCircle(Color.White, radius = r, center = at, style = Stroke(r * 0.35f))
    drawCircle(PinRed, radius = r * 0.45f, center = at)
}

/** La ligne du temps : un rond par passage du satellite ; les photos écartées par les choix sont estompées. */
@Composable
private fun Timeline(all: List<SatImage>, shown: List<SatImage>, current: SatImage?, months: Int, last: SatImage?, onPick: (SatImage) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val end = last?.dayNumber ?: return
    val start = end - months * 31
    Canvas(
        Modifier.fillMaxWidth().height(22.dp).pointerInput(shown, months) {
            detectTapGestures { tap ->
                val target = start + tap.x / size.width * (end - start)
                shown.minByOrNull { kotlin.math.abs(it.dayNumber - target) }?.let(onPick)
            }
        }
    ) {
        val y = size.height / 2
        drawLine(colors.onSurface.copy(alpha = 0.2f), Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx())
        all.forEach { image ->
            val x = (image.dayNumber - start).toFloat() / (end - start) * size.width
            val on = image in shown
            if (image == current) drawCircle(colors.primary, radius = 7.dp.toPx(), center = Offset(x, y))
            else drawCircle(colors.onSurface.copy(alpha = if (on) 0.75f else 0.2f), radius = 3.dp.toPx(), center = Offset(x, y))
        }
    }
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
 * Poser un point : la bande de côte entière, qu'on fait glisser et qu'on zoome ; on touche l'endroit où l'on surfe.
 * Trois aides pour se repérer : « Ma position » (GPS du téléphone), des coordonnées ou un lien Google Maps, la position d'une photo.
 * Les plages connues du catalogue sont écrites sur la bande.
 */
@Composable
private fun PlacePointSheet(index: SatIndex, strip: ImageBitmap?, existing: List<SatPoint>, onSave: (SatPoint) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    var pin by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var message by remember { mutableStateOf("Fais glisser la côte, zoome, puis touche l'endroit où tu surfes.") }
    var name by remember { mutableStateOf("") }
    var coordsText by remember { mutableStateOf("") }
    var showCoords by remember { mutableStateOf(false) }
    // Centre de la vue (en pixels de la bande) et nombre de colonnes visibles.
    var centerRow by remember { mutableFloatStateOf(index.rowOf(index.homeLat).toFloat()) }
    var centerCol by remember { mutableFloatStateOf(index.width / 2f) }
    var colsShown by remember { mutableFloatStateOf(index.width.toFloat()) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val measurer = rememberTextMeasurer()
    val landmarks = remember(index) { SurfDatabase.getAllSpots().filter { index.contains(it.latitude, it.longitude) } }

    fun place(lat: Double, lon: Double, source: String) {
        if (!index.contains(lat, lon)) {
            message = "$source : hors de la bande (15 km au nord et au sud de Montalivet)."
            return
        }
        pin = lat to lon
        centerRow = index.rowOf(lat).toFloat()
        centerCol = index.colOf(lon).toFloat()
        colsShown = colsShown.coerceAtMost(260f)
        message = "$source : point posé à ${index.distanceWord(lat, lon)} de Montalivet."
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) {
            message = "Recherche de ta position…"
            currentLocation(context) { loc -> if (loc == null) message = "Position introuvable : active la localisation du téléphone." else place(loc.latitude, loc.longitude, "Ma position") }
        } else message = "Sans l'autorisation de localisation, pose le point à la main ou colle des coordonnées."
    }
    val photo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val latLon = photoLatLon(context, uri)
            if (latLon == null) message = "Cette photo n'a pas de position enregistrée (Android la retire parfois). Essaie « Ma position » ou des coordonnées."
            else place(latLon.first, latLon.second, "Photo")
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Poser un point", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.onBackground, modifier = Modifier.weight(1f))
            Chip("Annuler", selected = false, onClick = onCancel)
        }
        Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(12.dp)).background(Color.Black).onSizeChanged { size = it }) {
            if (strip == null) CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
            else Canvas(
                Modifier.fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            if (size.width == 0) return@detectTransformGestures
                            colsShown = (colsShown / zoom).coerceIn(60f, index.width.toFloat())
                            val perPx = colsShown / size.width
                            centerCol -= pan.x * perPx
                            centerRow -= pan.y * perPx
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures { tap ->
                            if (size.width == 0) return@detectTapGestures
                            val perPx = colsShown / size.width
                            val rowsShown = size.height * perPx
                            val left = (centerCol - colsShown / 2).coerceIn(0f, index.width - colsShown)
                            val top = (centerRow - rowsShown / 2).coerceIn(0f, (index.height - rowsShown).coerceAtLeast(0f))
                            val lat = index.latOf((top + tap.y * perPx).toDouble())
                            val lon = index.lonOf((left + tap.x * perPx).toDouble())
                            pin = lat to lon
                            message = "Point posé à ${index.distanceWord(lat, lon)} de Montalivet."
                        }
                    }
            ) {
                val perPx = colsShown / this.size.width
                val rowsShown = (this.size.height * perPx).coerceAtMost(index.height.toFloat())
                val left = (centerCol - colsShown / 2).coerceIn(0f, index.width - colsShown)
                val top = (centerRow - rowsShown / 2).coerceIn(0f, (index.height - rowsShown).coerceAtLeast(0f))
                drawImage(
                    strip, srcOffset = IntOffset(left.roundToInt(), top.roundToInt()), srcSize = IntSize(colsShown.roundToInt(), rowsShown.roundToInt()),
                    dstSize = IntSize(this.size.width.roundToInt(), this.size.height.roundToInt())
                )
                fun pos(lat: Double, lon: Double) = Offset((index.colOf(lon).toFloat() - left) / perPx, (index.rowOf(lat).toFloat() - top) / perPx)
                // Les plages connues, pour se repérer.
                landmarks.forEach { spot ->
                    val p = pos(spot.latitude, spot.longitude)
                    if (p.y < 0 || p.y > this.size.height) return@forEach
                    drawCircle(Color.White, radius = 3.dp.toPx(), center = p)
                    val label = measurer.measure(spot.name, TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White))
                    val at = Offset((p.x + 8.dp.toPx()).coerceAtMost(this.size.width - label.size.width - 4.dp.toPx()), p.y - label.size.height / 2f)
                    drawRoundRect(
                        Color(0xB308121C), topLeft = Offset(at.x - 3.dp.toPx(), at.y - 1.dp.toPx()),
                        size = androidx.compose.ui.geometry.Size(label.size.width + 6.dp.toPx(), label.size.height + 2.dp.toPx()),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx())
                    )
                    drawText(label, topLeft = at)
                }
                existing.drop(1).forEach { p -> drawCircle(Color(0xFF7FC3E6), radius = 5.dp.toPx(), center = pos(p.lat, p.lon)) }
                pin?.let { (lat, lon) -> drawPin(pos(lat, lon), 11.dp.toPx()) }
            }
        }
        Text(message, fontSize = 12.sp, color = colors.onSurfaceVariant)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip("Ma position", selected = false) {
                permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }
            Chip("Coordonnées", selected = showCoords) { showCoords = !showCoords }
            Chip("Depuis une photo", selected = false) { photo.launch("image/*") }
        }
        if (showCoords) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = coordsText, onValueChange = { coordsText = it }, singleLine = true, modifier = Modifier.weight(1f),
                    placeholder = { Text("45.31, -1.16 ou lien Google Maps", fontSize = 13.sp) }
                )
                Chip("Placer", selected = true) {
                    val c = SatelliteModel.parseCoordinates(coordsText)
                    if (c == null) message = "Coordonnées illisibles. Dans Google Maps : appui long sur la carte, puis copie les deux nombres."
                    else place(c.first, c.second, "Coordonnées")
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

/** Position du téléphone, une seule fois (GPS, sinon réseau). */
@SuppressLint("MissingPermission")
private fun currentLocation(context: Context, onResult: (Location?) -> Unit) {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return onResult(null)
    val provider = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).firstOrNull { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        ?: return onResult(null)
    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            manager.getCurrentLocation(provider, null, context.mainExecutor) { loc -> onResult(loc ?: manager.getLastKnownLocation(provider)) }
        } else {
            val last = manager.getLastKnownLocation(provider)
            if (last != null) onResult(last)
            else {
                @Suppress("DEPRECATION")
                manager.requestSingleUpdate(provider, object : LocationListener {
                    override fun onLocationChanged(location: Location) = onResult(location)
                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                }, Looper.getMainLooper())
            }
        }
    }.onFailure { onResult(null) }
}

/** Latitude et longitude enregistrées dans une photo, si Android les laisse lire. */
private fun photoLatLon(context: Context, uri: Uri): Pair<Double, Double>? = runCatching {
    val original = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) runCatching { MediaStore.setRequireOriginal(uri) }.getOrDefault(uri) else uri
    val stream = runCatching { context.contentResolver.openInputStream(original) }.getOrNull() ?: context.contentResolver.openInputStream(uri)
    stream?.use { input ->
        val out = FloatArray(2)
        if (ExifInterface(input).getLatLong(out) && (out[0] != 0f || out[1] != 0f)) out[0].toDouble() to out[1].toDouble() else null
    }
}.getOrNull()
