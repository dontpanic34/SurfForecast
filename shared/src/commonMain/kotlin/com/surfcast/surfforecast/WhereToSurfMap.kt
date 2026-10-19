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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.decodeToImageBitmap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

private const val TILE_URL = "https://gibs.earthdata.nasa.gov/wmts/epsg3857/best/BlueMarble_ShadedRelief_Bathymetry/default/2004-08/GoogleMapsCompatible_Level8"
private const val MAX_TILE_ZOOM = 8

private fun mercY(lat: Double) = ln(tan(PI / 4 + lat * PI / 360)) * 180 / PI

private fun windColor(kmh: Int) = when {
    kmh < 12 -> Color(0xFF3FD0B0)
    kmh < 25 -> Color(0xFFFF9A1F)
    else -> Color(0xFFFF5A55)
}

private fun windWord(kmh: Int) = when { kmh < 12 -> "faible"; kmh < 25 -> "modéré"; else -> "fort" }

/** Un spot de la carte : ses heures, et s'il s'agit du spot de départ. */
private class MapSpot(val spot: ScanSpot, val hours: List<HourlyUiModel>, val isHome: Boolean)

private fun hourOf(hours: List<HourlyUiModel>, date: LocalDate, hour: Int) = hours.firstOrNull { it.rawTime.date == date && it.rawTime.hour == hour }

/** Le point (latitude, longitude) à [km] km de ([lat], [lon]) dans la direction [bearing]° (pour tracer le cercle du rayon). */
private fun destination(lat: Double, lon: Double, bearing: Double, km: Double): Pair<Double, Double> {
    val d = km / 6371.0
    val la = lat * PI / 180
    val lo = lon * PI / 180
    val b = bearing * PI / 180
    val la2 = asin(sin(la) * cos(d) + cos(la) * sin(d) * cos(b))
    val lo2 = lo + atan2(sin(b) * sin(d) * cos(la), cos(d) - sin(la) * sin(la2))
    return la2 * 180 / PI to lo2 * 180 / PI
}

/**
 * La carte « Où surfer ? » : fond NASA (Blue Marble, relief et fonds marins, libre de droit), les spots du rayon colorés par leur note à
 * l'heure choisie, avec la taille de la houle et sa direction (flèche blanche), la force et la direction du vent (flèche de couleur). On
 * glisse et on pince pour zoomer ; un spot touché donne le détail.
 */
@Composable
fun WhereToSurfMap(result: WhereResult, homeLat: Double, homeLon: Double, onPickSpot: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val dates = result.days.map { it.date }
    val spots = remember(result) { result.scans.map { MapSpot(it.first, it.second, false) } + listOfNotNull(result.home?.let { MapSpot(it.first, it.second, true) }) }
    var dateIndex by remember(result) { mutableFloatStateOf(result.days.indexOfFirst { it.worthMoving }.coerceAtLeast(0).toFloat()) }
    var hour by remember(result) { mutableFloatStateOf(13f) }
    var showSwell by remember { mutableStateOf(true) }
    var showWind by remember { mutableStateOf(true) }
    var selected by remember(result) { mutableStateOf<String?>(null) }
    val date = dates.getOrNull(dateIndex.roundToInt())
    val h = hour.roundToInt()

    var size by remember { mutableStateOf(IntSize.Zero) }
    var cLon by remember(result) { mutableFloatStateOf(homeLon.toFloat()) }
    var cMy by remember(result) { mutableFloatStateOf(mercY(homeLat).toFloat()) }
    var scale by remember(result) { mutableFloatStateOf(0f) }
    var fitScale by remember(result) { mutableFloatStateOf(1f) }
    LaunchedEffect(size, result) {
        if (size.width > 0) {
            val radiusDeg = result.radiusKm / (111.32 * cos(homeLat * PI / 180))
            fitScale = (size.width / (2 * radiusDeg * 1.15)).toFloat()
            scale = fitScale
        }
    }

    // Les tuiles du fond : chargées au besoin, gardées en mémoire.
    val client = remember { defaultHttpClient() }
    DisposableEffect(client) { onDispose { client.close() } }
    val tiles = remember { mutableStateMapOf<String, ImageBitmap>() }
    val loading = remember { HashSet<String>() }
    val scope = rememberCoroutineScope()
    val z = if (scale > 0f) (ln(360.0 * scale / 256.0) / ln(2.0)).roundToInt().coerceIn(3, MAX_TILE_ZOOM) else 3
    val n = 2.0.pow(z)
    fun tileX(lon: Double) = (lon + 180) / 360 * n
    fun tileY(my: Double) = (1 - my * PI / 180 / PI) / 2 * n
    val neededKeys: List<String> = if (scale <= 0f || size.width == 0) emptyList() else {
        val leftLon = cLon - (size.width / 2f) / scale
        val rightLon = cLon + (size.width / 2f) / scale
        val topMy = cMy + (size.height / 2f) / scale
        val bottomMy = cMy - (size.height / 2f) / scale
        val x0 = tileX(leftLon.toDouble()).toInt().coerceAtLeast(0)
        val x1 = tileX(rightLon.toDouble()).toInt().coerceAtMost(n.toInt() - 1)
        val y0 = tileY(topMy.toDouble()).toInt().coerceAtLeast(0)
        val y1 = tileY(bottomMy.toDouble()).toInt().coerceAtMost(n.toInt() - 1)
        buildList { for (x in x0..x1) for (y in y0..y1) add("$z/$x/$y") }.take(40)
    }
    LaunchedEffect(neededKeys) {
        neededKeys.filter { it !in tiles && loading.add(it) }.forEach { key ->
            scope.launch {
                runCatching {
                    val parts = key.split('/')
                    val bytes: ByteArray = client.get("$TILE_URL/${parts[0]}/${parts[2]}/${parts[1]}.jpeg").body()
                    tiles[key] = bytes.decodeToImageBitmap()
                }
                loading.remove(key)
            }
        }
    }

    val measurer = rememberTextMeasurer()
    val seaColor = Color(0xFF0E2C44)

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().height(400.dp).clip(RoundedCornerShape(12.dp)).onSizeChanged { size = it }) {
            Canvas(
                modifier = Modifier.fillMaxWidth().height(400.dp)
                    .pointerInput(result) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            if (scale <= 0f) return@detectTransformGestures
                            val w = size.width
                            val hh = size.height
                            val lonAtC = cLon + (centroid.x - w / 2f) / scale
                            val myAtC = cMy - (centroid.y - hh / 2f) / scale
                            scale = (scale * zoom).coerceIn(fitScale * 0.5f, fitScale * 40f)
                            cLon = lonAtC - (centroid.x + pan.x - w / 2f) / scale
                            cMy = myAtC + (centroid.y + pan.y - hh / 2f) / scale
                        }
                    }
                    .pointerInput(result) {
                        detectTapGestures { tap ->
                            if (scale <= 0f) return@detectTapGestures
                            val limit = with(density) { 42.dp.toPx() }
                            fun gap(ms: MapSpot): Float {
                                val px = size.width / 2f + (ms.spot.lon - cLon) * scale
                                val py = size.height / 2f - (mercY(ms.spot.lat) - cMy) * scale
                                return (abs(px - tap.x) + abs(py - tap.y)).toFloat()
                            }
                            selected = spots.minByOrNull { gap(it) }?.takeIf { gap(it) < limit }?.spot?.name
                        }
                    }
            ) {
                drawRect(seaColor)
                if (scale <= 0f) return@Canvas
                fun sx(lon: Double) = (size.width / 2f + (lon - cLon) * scale).toFloat()
                fun sy(lat: Double) = (size.height / 2f - (mercY(lat) - cMy) * scale).toFloat()
                // Fond : les tuiles déjà chargées.
                val tilePx = 360.0 * scale / n
                neededKeys.forEach { key ->
                    val img = tiles[key] ?: return@forEach
                    val parts = key.split('/')
                    val tx = parts[1].toInt()
                    val ty = parts[2].toInt()
                    val left = size.width / 2f + ((tx / n * 360 - 180) - cLon) * scale
                    val tileTopMy = (1 - 2.0 * ty / n) * 180
                    val top = size.height / 2f - (tileTopMy - cMy) * scale
                    drawImage(img, dstOffset = IntOffset(left.roundToInt(), top.toFloat().roundToInt()), dstSize = IntSize(ceil(tilePx).toInt() + 1, ceil(tilePx).toInt() + 1))
                }
                // Cercle du rayon.
                val ring = Path()
                for (b in 0..360 step 6) {
                    val (la, lo) = destination(homeLat, homeLon, b.toDouble(), result.radiusKm.toDouble())
                    if (b == 0) ring.moveTo(sx(lo), sy(la)) else ring.lineTo(sx(lo), sy(la))
                }
                ring.close()
                drawPath(ring, Color(0x143FD0B0))
                drawPath(ring, Color(0xE63FD0B0), style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))))
                val detail = scale >= fitScale * 1.3f || result.radiusKm <= 100
                if (date != null) spots.forEach { ms ->
                    val hr = hourOf(ms.hours, date, h) ?: return@forEach
                    val px = sx(ms.spot.lon)
                    val py = sy(ms.spot.lat)
                    if (px < -40 || py < -40 || px > size.width + 40 || py > size.height + 40) return@forEach
                    val rating = calculateSlotRating(hr, ms.spot.facing, NEUTRAL_LEVEL, bandShift = 0)
                    val fill = if (rating.tooBig) ScoreBand.TOO_BIG.color() else scoreGradientColor(rating.score)
                    val center = Offset(px, py)
                    val r = (if (ms.isHome) 10.dp else 8.dp).toPx()
                    if (showSwell) arrowOn(center, (hr.waveDirection + 180f) % 360f, 15.dp.toPx(), Color.White, 2.dp.toPx())
                    drawCircle(fill, radius = r, center = center)
                    drawCircle(Color.White, radius = r, center = center, style = Stroke(2.dp.toPx()))
                    if (ms.isHome) drawCircle(Color.White, radius = 14.dp.toPx(), center = center, style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))))
                    if (showWind) {
                        val wdeg = (SurfUnitsHelper.cardinalToDegrees(SurfUnitsHelper.formatCardinalFr(hr.windDirectionStr)).toFloat() + 180f) % 360f
                        arrowOn(Offset(px, py - 24.dp.toPx()), wdeg, 12.dp.toPx(), windColor(hr.windSpeedKmh), 2.5.dp.toPx())
                    }
                    if (detail || ms.spot.name == selected) {
                        val txt = (if (showSwell) formatDecimal(hr.waveHeight, 1).replace('.', ',') + " m" else "") +
                            (if (showSwell && showWind) " · " else "") + (if (showWind) "${hr.windSpeedKmh}" else "")
                        if (txt.isNotEmpty()) {
                            val m = measurer.measure(txt, TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White))
                            val tl = Offset(px - m.size.width / 2f, py + 11.dp.toPx())
                            drawRoundRect(
                                Color(0xCC08121C), topLeft = Offset(tl.x - 4.dp.toPx(), tl.y - 1.dp.toPx()),
                                size = Size(m.size.width + 8.dp.toPx(), m.size.height + 2.dp.toPx()), cornerRadius = CornerRadius(8.dp.toPx())
                            )
                            drawText(m, topLeft = tl)
                        }
                    }
                    if (ms.spot.name == selected) drawCircle(Color.White, radius = 15.dp.toPx(), center = center, style = Stroke(2.5.dp.toPx()))
                }
            }
            Text("NASA Blue Marble", fontSize = 9.sp, color = Color(0xCCFFFFFF), modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp))
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            LayerChip(tr("Houle"), showSwell) { showSwell = !showSwell }
            LayerChip(tr("Vent"), showWind) { showWind = !showWind }
            Text(
                tr("flèche blanche : d'où va la houle · flèche de couleur : le vent (km/h)"),
                fontSize = 10.sp, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f).padding(start = 4.dp)
            )
        }
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            dates.forEachIndexed { i, d ->
                val on = i == dateIndex.roundToInt()
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) colors.primary else colors.surfaceVariant)
                        .clickable { dateIndex = i.toFloat() }.padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text("${frenchShortDayName(d)} ${d.day}", fontSize = 12.5.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, color = if (on) colors.onPrimary else colors.onSurface)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("$h h", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.onSurface, modifier = Modifier.padding(end = 10.dp))
            Slider(value = hour, onValueChange = { hour = it }, valueRange = 6f..21f, steps = 14, modifier = Modifier.weight(1f))
        }
        val sel = spots.firstOrNull { it.spot.name == selected }
        val selHour = if (sel != null && date != null) hourOf(sel.hours, date, h) else null
        if (sel != null && selHour != null && date != null) {
            val stars = WhereToSurf.spotDay(sel.spot, sel.hours, date)?.stars
            Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.surfaceVariant).padding(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(sel.spot.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                    if (stars != null) StarsRow(stars)
                }
                Text(
                    if (sel.isHome) tr("ici") else "${sel.spot.zone} · ${sel.spot.distanceKm} km",
                    fontSize = 11.5.sp, color = colors.onSurfaceVariant
                )
                Text(
                    "${formatDecimal(selHour.waveHeight, 1).replace('.', ',')} m · ${selHour.wavePeriod.roundToInt()} s · ${tr("vent")} ${selHour.windSpeedKmh} km/h ${SurfUnitsHelper.formatCardinalFr(selHour.windDirectionStr)} (${tr(windWord(selHour.windSpeedKmh))})",
                    fontSize = 12.5.sp, color = colors.onSurface, modifier = Modifier.padding(top = 3.dp)
                )
                if (!sel.isHome) Text(
                    tr("Aller sur ce spot") + " ›", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.primary,
                    modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(50)).clickable { onPickSpot(sel.spot.name) }.padding(vertical = 4.dp, horizontal = 2.dp)
                )
            }
        } else {
            Text(tr("Touche un spot pour voir ses conditions."), fontSize = 11.5.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun LayerChip(label: String, on: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) colors.primary else colors.surfaceVariant).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(label, fontSize = 12.5.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, color = if (on) colors.onPrimary else colors.onSurface)
    }
}

/** Une flèche centrée sur [c], de longueur [len], qui pointe vers [deg]° (0 = nord, vers le haut). */
private fun DrawScope.arrowOn(c: Offset, deg: Float, len: Float, color: Color, width: Float) {
    val r = deg * PI.toFloat() / 180f
    val dx = sin(r)
    val dy = -cos(r)
    val tip = Offset(c.x + dx * len, c.y + dy * len)
    val tail = Offset(c.x - dx * len, c.y - dy * len)
    drawLine(color, tail, tip, strokeWidth = width)
    val head = len * 0.6f
    val p = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(tip.x - dx * head - dy * head * 0.55f, tip.y - dy * head + dx * head * 0.55f)
        lineTo(tip.x - dx * head + dy * head * 0.55f, tip.y - dy * head - dx * head * 0.55f)
        close()
    }
    drawPath(p, color)
}
