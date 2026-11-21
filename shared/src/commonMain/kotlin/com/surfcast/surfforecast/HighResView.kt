package com.surfcast.surfforecast

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.jetbrains.compose.resources.decodeToImageBitmap
import kotlin.math.PI
import kotlin.math.asinh
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sinh

/**
 * Option « Haute qualité » de la vue satellite : les photos de l'IGN (Géoplateforme, Licence Ouverte, sans clé), France seulement.
 *  - la photo aérienne (BD ORTHO, 20 cm par pixel), prise en avion : une campagne tous les 2 à 4 ans par département ;
 *  - les mosaïques annuelles du satellite Pléiades (50 cm par pixel), une par an (2012 à 2026 selon l'endroit).
 * Pas de film : quelques dates. La marée de chaque photo n'est pas connue.
 */
class HighResLayer(val id: String, val label: String, val format: String, val maxZoom: Int, val isAerial: Boolean = false)

object IgnTiles {
    private const val BASE = "https://data.geopf.fr/wmts?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0&STYLE=normal&TILEMATRIXSET=PM"

    const val AERIAL_ID = "ORTHOIMAGERY.ORTHOPHOTOS"

    val aerial = HighResLayer(AERIAL_ID, "Photo aérienne 20 cm", "image/jpeg", 19, isAerial = true)

    /** Mosaïques annuelles Pléiades, de la plus récente à la plus ancienne. */
    val pleiades: List<HighResLayer> = (2026 downTo 2012).map {
        HighResLayer("ORTHOIMAGERY.ORTHO-SAT.PLEIADES.$it", "Satellite $it", "image/png", 18)
    }

    fun tileUrl(layer: HighResLayer, z: Int, x: Int, y: Int) = "$BASE&LAYER=${layer.id}&FORMAT=${layer.format}&TILEMATRIX=$z&TILEROW=$y&TILECOL=$x"

    /** Date de prise de vue de la photo aérienne à cet endroit (AAAA-MM-JJ), lue dans les informations de la tuile. */
    fun aerialInfoUrl(lat: Double, lon: Double): String {
        val z = 17
        val wx = worldX(lon, z.toDouble()); val wy = worldY(lat, z.toDouble())
        val tx = floor(wx / 256).toInt(); val ty = floor(wy / 256).toInt()
        val i = (wx - tx * 256).toInt().coerceIn(0, 255); val j = (wy - ty * 256).toInt().coerceIn(0, 255)
        return "https://data.geopf.fr/wmts?SERVICE=WMTS&REQUEST=GetFeatureInfo&VERSION=1.0.0&LAYER=$AERIAL_ID&STYLE=normal&FORMAT=image/jpeg" +
            "&TILEMATRIXSET=PM&TILEMATRIX=$z&TILEROW=$ty&TILECOL=$tx&I=$i&J=$j&INFOFORMAT=application/json"
    }

    fun parseFlightDate(text: String): String? = Regex("\"date_vol\"\\s*:\\s*\"(\\d{4}-\\d{2}-\\d{2})").find(text)?.groupValues?.get(1)

    /** Une mosaïque PNG vide (hors couverture) pèse moins de 1,5 Ko ; une tuile absente répond par une erreur. */
    fun isUsable(layer: HighResLayer, bytes: ByteArray?): Boolean = bytes != null && (layer.isAerial || bytes.size > 1500)

    // Projection Web Mercator : pixels du monde à un niveau de zoom (peut être fractionnaire), tuiles de 256 pixels.
    fun worldX(lon: Double, zoom: Double): Double = (lon + 180.0) / 360.0 * 256.0 * 2.0.pow(zoom)
    fun worldY(lat: Double, zoom: Double): Double {
        val t = kotlin.math.tan(lat * PI / 180.0)
        return (1.0 - asinh(t) / PI) / 2.0 * 256.0 * 2.0.pow(zoom)
    }
    fun lonOf(x: Double, zoom: Double): Double = x / (256.0 * 2.0.pow(zoom)) * 360.0 - 180.0
    fun latOf(y: Double, zoom: Double): Double {
        val n = PI * (1.0 - 2.0 * y / (256.0 * 2.0.pow(zoom)))
        return atan(sinh(n)) * 180.0 / PI
    }

    /** Mètres par pixel d'écran à cette latitude et ce zoom. */
    fun metersPerPixel(lat: Double, zoom: Double): Double = 156543.03392 * cos(lat * PI / 180.0) / 2.0.pow(zoom)

    fun dateLabel(iso: String): String {
        val months = listOf("janvier", "février", "mars", "avril", "mai", "juin", "juillet", "août", "septembre", "octobre", "novembre", "décembre")
        val p = iso.split("-")
        val m = p.getOrNull(1)?.toIntOrNull()?.let { months.getOrNull(it - 1) }
        val d = p.getOrNull(2)?.toIntOrNull()
        return if (m != null && d != null) "$d $m ${p[0]}" else iso
    }
}

@Composable
fun HighResBody(client: HttpClient, point: SatPoint, userPoints: List<SatPoint>, onPinHere: (SatPoint) -> Unit) {
    val colors = MaterialTheme.colorScheme
    var centerLat by remember(point) { mutableStateOf(point.lat) }
    var centerLon by remember(point) { mutableStateOf(point.lon) }
    var zoom by remember(point) { mutableFloatStateOf(17f) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val measurer = rememberTextMeasurer()

    // Les couches qui ont des photos à cet endroit, et la date du vol de la photo aérienne.
    var available by remember(point) { mutableStateOf<List<HighResLayer>?>(null) }
    var flightDate by remember(point) { mutableStateOf<String?>(null) }
    var layerId by remember(point) { mutableStateOf(IgnTiles.AERIAL_ID) }
    LaunchedEffect(point) {
        val z = 17
        val tx = floor(IgnTiles.worldX(point.lon, z.toDouble()) / 256).toInt()
        val ty = floor(IgnTiles.worldY(point.lat, z.toDouble()) / 256).toInt()
        flightDate = client.fetchText(IgnTiles.aerialInfoUrl(point.lat, point.lon))?.let { IgnTiles.parseFlightDate(it) }
        val found = ArrayList<HighResLayer>()
        coroutineScope {
            val gate = Semaphore(5)
            val results = (listOf(IgnTiles.aerial) + IgnTiles.pleiades).map { layer ->
                layer to async { gate.withPermit { IgnTiles.isUsable(layer, client.fetchBytes(IgnTiles.tileUrl(layer, z, tx, ty))) } }
            }
            results.forEach { (layer, ok) -> if (ok.await()) found += layer }
        }
        available = found
        if (found.none { it.id == layerId }) found.firstOrNull()?.let { layerId = it.id }
    }
    val layers = available
    val layer = layers?.firstOrNull { it.id == layerId } ?: IgnTiles.aerial

    val cache = remember { mutableStateMapOf<String, ImageBitmap>() }
    val missing = remember { HashSet<String>() }

    // Les tuiles à l'écran pour la couche et le zoom courants.
    val tz = min(floor(zoom).toInt(), layer.maxZoom)
    val scale = 2.0.pow((zoom - tz).toDouble())
    val cxT = IgnTiles.worldX(centerLon, tz.toDouble())
    val cyT = IgnTiles.worldY(centerLat, tz.toDouble())
    val halfW = size.width / (2.0 * scale)
    val halfH = size.height / (2.0 * scale)
    val x0 = floor((cxT - halfW) / 256).toInt(); val x1 = floor((cxT + halfW) / 256).toInt()
    val y0 = floor((cyT - halfH) / 256).toInt(); val y1 = floor((cyT + halfH) / 256).toInt()
    LaunchedEffect(layer.id, tz, x0, x1, y0, y1, layers != null) {
        if (size.width == 0 || layers == null) return@LaunchedEffect
        val limit = (1 shl tz) - 1
        val needed = ArrayList<Triple<Int, Int, String>>()
        for (x in x0..x1) for (y in y0..y1) {
            if (x !in 0..limit || y !in 0..limit) continue
            needed += Triple(x, y, "${layer.id}/$tz/$x/$y")
        }
        val keep = needed.map { it.third }.toSet()
        if (cache.size > 220) cache.keys.filter { it !in keep }.take(120).forEach { cache.remove(it) }
        val gate = Semaphore(6)
        coroutineScope {
            needed.sortedBy { (x, y, _) -> kotlin.math.abs(x + 0.5 - cxT / 256) + kotlin.math.abs(y + 0.5 - cyT / 256) }.forEach { (x, y, key) ->
                if (key in cache || key in missing) return@forEach
                launch {
                    gate.withPermit {
                        val data = client.fetchBytes(IgnTiles.tileUrl(layer, tz, x, y))
                        val bmp = if (data != null) try { data.decodeToImageBitmap() } catch (e: CancellationException) { throw e } catch (e: Throwable) { null } else null
                        if (bmp != null) cache[key] = bmp else missing += key
                    }
                }
            }
        }
    }

    val nearest = remember(centerLat, centerLon) { SurfDatabase.getAllSpots().minByOrNull { distanceKm(centerLat, centerLon, it.latitude, it.longitude) } }

    Box(Modifier.fillMaxWidth().height(430.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF0B1A22)).onSizeChanged { size = it }) {
        Canvas(
            Modifier.fillMaxSize().pointerInput(point, layer.id) {
                detectTransformGestures { _, pan, gestureZoom, _ ->
                    val z = zoom.toDouble()
                    val newZoom = (z + ln(gestureZoom.toDouble()) / ln(2.0)).coerceIn(12.0, layer.maxZoom + 1.5)
                    // On déplace le centre de la quantité glissée (en pixels du monde au zoom courant).
                    val wx = IgnTiles.worldX(centerLon, z) - pan.x
                    val wy = IgnTiles.worldY(centerLat, z) - pan.y
                    centerLon = IgnTiles.lonOf(wx, z)
                    centerLat = IgnTiles.latOf(wy, z).coerceIn(-85.0, 85.0)
                    zoom = newZoom.toFloat()
                }
            }
        ) {
            val w = this.size.width; val h = this.size.height
            for (x in x0..x1) for (y in y0..y1) {
                val bmp = cache["${layer.id}/$tz/$x/$y"] ?: continue
                val dx = ((x * 256 - cxT) * scale + w / 2).toFloat()
                val dy = ((y * 256 - cyT) * scale + h / 2).toFloat()
                val side = (256 * scale).roundToInt() + 1
                drawImage(bmp, dstOffset = IntOffset(dx.roundToInt(), dy.roundToInt()), dstSize = IntSize(side, side), filterQuality = FilterQuality.High)
            }
            val z = zoom.toDouble()
            val cx = IgnTiles.worldX(centerLon, z); val cy = IgnTiles.worldY(centerLat, z)
            fun pos(lat: Double, lon: Double) = Offset((IgnTiles.worldX(lon, z) - cx + w / 2).toFloat(), (IgnTiles.worldY(lat, z) - cy + h / 2).toFloat())
            // Repères : spots du catalogue (blanc) et points de l'utilisateur (bleu), sans que les noms se recouvrent.
            val taken = mutableListOf<Rect>()
            fun landmark(name: String, lat: Double, lon: Double, dot: Color) {
                val at0 = pos(lat, lon)
                if (at0.x < 0 || at0.y < 0 || at0.x > w || at0.y > h) return
                drawCircle(Color.Black.copy(alpha = 0.5f), radius = 4.5.dp.toPx(), center = at0)
                drawCircle(dot, radius = 3.dp.toPx(), center = at0)
                val label = measurer.measure(name, TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White))
                val at = Offset((at0.x + 8.dp.toPx()).coerceAtMost(w - label.size.width - 4.dp.toPx()), at0.y - label.size.height / 2f)
                val box = Rect(at.x - 3.dp.toPx(), at.y - 1.dp.toPx(), at.x + label.size.width + 3.dp.toPx(), at.y + label.size.height + 1.dp.toPx())
                if (taken.any { it.overlaps(box) }) return
                taken += box
                drawRoundRect(Color(0xB308121C), topLeft = box.topLeft, size = box.size, cornerRadius = CornerRadius(6.dp.toPx()))
                drawText(label, topLeft = at)
            }
            userPoints.forEach { landmark(it.name, it.lat, it.lon, Color(0xFF7FC3E6)) }
            SurfDatabase.getAllSpots().forEach { landmark(it.name, it.latitude, it.longitude, Color.White) }
            val pin = pos(point.lat, point.lon)
            if (pin.x in 0f..w.toFloat() && pin.y in 0f..h.toFloat()) drawPin(pin, 9.dp.toPx())
            val c = Offset(w / 2f, h / 2f); val arm = 7.dp.toPx()
            drawLine(Color.White.copy(alpha = 0.8f), Offset(c.x - arm, c.y), Offset(c.x + arm, c.y), strokeWidth = 1.5.dp.toPx())
            drawLine(Color.White.copy(alpha = 0.8f), Offset(c.x, c.y - arm), Offset(c.x, c.y + arm), strokeWidth = 1.5.dp.toPx())
            // Échelle : la plus ronde entre 50 et 150 pixels.
            val mpp = IgnTiles.metersPerPixel(centerLat, z)
            val meters = listOf(10, 20, 50, 100, 200, 500, 1000, 2000).firstOrNull { it / mpp in 50.0..150.0 } ?: 100
            val bar = (meters / mpp).toFloat(); val y = h - 12.dp.toPx()
            drawLine(Color.White, Offset(10.dp.toPx(), y), Offset(10.dp.toPx() + bar, y), strokeWidth = 3.dp.toPx())
        }
        if (layers == null || (layers.isNotEmpty() && cache.keys.none { it.startsWith(layer.id) })) {
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
        }
        if (layers != null && layers.isEmpty()) {
            Text(
                "Pas de photo haute qualité ici.\nL'IGN ne couvre que la France.",
                fontSize = 13.sp, color = Color.White, modifier = Modifier.align(Alignment.Center).padding(16.dp)
            )
        }
        Column(
            Modifier.align(Alignment.TopStart).padding(8.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xC7061018)).padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                if (layer.isAerial) (flightDate?.let { "Photo aérienne du " + IgnTiles.dateLabel(it) } ?: "Photo aérienne") else layer.label.replace("Satellite", "Satellite Pléiades"),
                fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White
            )
            Text(if (layer.isAerial) "20 cm par pixel" else "50 cm par pixel · mosaïque de l'année", fontSize = 12.sp, color = Color.White)
        }
        Column(Modifier.align(Alignment.CenterEnd).padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ZoomButton("+") { zoom = (zoom + 0.7f).coerceAtMost(layer.maxZoom + 1.5f) }
            ZoomButton("−") { zoom = (zoom - 0.7f).coerceAtLeast(12f) }
        }
        Text("© IGN", fontSize = 9.sp, color = Color(0xCCFFFFFF), modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp))
    }

    // Les dates disponibles.
    if (layers != null && layers.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        layers.forEach { l ->
            val label = if (l.isAerial) "Aérienne" + (flightDate?.let { " " + it.take(4) } ?: "") else l.label.removePrefix("Satellite ")
            Chip(if (l.isAerial) "✈ $label" else "◉ $label", selected = l.id == layer.id) { layerId = l.id }
        }
    }
    // Se déplacer.
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (distanceKm(centerLat, centerLon, point.lat, point.lon) > 0.05) Chip("◎ ${point.name}", selected = true) { centerLat = point.lat; centerLon = point.lon }
        Chip("↑ Nord", selected = false) { centerLat += 0.5 / 111.32 }
        Chip("↓ Sud", selected = false) { centerLat -= 0.5 / 111.32 }
        val near = remember((centerLat * 40).roundToInt(), (centerLon * 40).roundToInt()) {
            SurfDatabase.getAllSpots().sortedBy { distanceKm(centerLat, centerLon, it.latitude, it.longitude) }.take(8)
        }
        near.forEach { spot -> Chip(spot.name, selected = false, outlined = true) { centerLat = spot.latitude; centerLon = spot.longitude } }
        Chip("+ Poser ici", selected = false, outlined = true) { onPinHere(SatPoint("Banc près de " + (nearest?.name ?: point.name), centerLat, centerLon)) }
    }
    Text(
        (nearest?.let { "Près de ${it.name}, ${satDistanceWord(centerLat, centerLon, it.latitude, it.longitude)}" } ?: "") +
            " · Pince pour zoomer, glisse pour te déplacer. Photo aérienne : une campagne tous les 2 à 4 ans ; satellite : une mosaïque par an (plusieurs jours de prise de vue, marée inconnue). © IGN, Licence Ouverte ; Pléiades © CNES, Airbus DS.",
        fontSize = 11.5.sp, color = colors.onSurfaceVariant
    )
}
