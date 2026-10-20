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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
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

/** Les trois moments du jour : l'heure de prévision qu'ils montrent sur la carte. */
private val MOMENTS = listOf("Matin" to 9, "Midi" to 13, "Après-midi" to 17)

private fun mercY(lat: Double) = ln(tan(PI / 4 + lat * PI / 360)) * 180 / PI

/** Un spot de la carte : ses heures, et s'il s'agit du spot de départ. */
private class MapSpot(val spot: ScanSpot, val hours: List<HourlyUiModel>, val isHome: Boolean)

/** Un spot à un moment donné : sa note (0 à 100) et ses conditions. */
private class Pt(val ms: MapSpot, val hour: HourlyUiModel?, val score: Int)

/** Une pastille de la carte : des spots voisins de la côte, résumés par leur note moyenne. */
private class Grp(val members: List<Pt>, val pos: Offset, val stars: Float, val score: Float)

private fun hourOf(hours: List<HourlyUiModel>, date: LocalDate, hour: Int) = hours.firstOrNull { it.rawTime.date == date && it.rawTime.hour == hour }

private val COMPASS = arrayOf("nord", "nord-est", "est", "sud-est", "sud", "sud-ouest", "ouest", "nord-ouest")
private fun compassWord(deg: Double) = COMPASS[(((deg % 360 + 360) % 360 + 22.5) / 45).toInt() % 8]

private fun windWord(category: String) = when (category) {
    "offshore" -> "offshore"
    "side-off" -> "side-off"
    "side-on" -> "side-on"
    "onshore" -> "onshore"
    else -> "side"
}

private fun swellWord(swellDir: Double, facing: Int?): String {
    if (facing == null) return "houle ?"
    val d = angularDifference(swellDir, facing.toDouble())
    return when {
        d < 30 -> "houle de face"
        d < 60 -> "houle de côté"
        d < 90 -> "houle rasante"
        else -> "abrité de la houle"
    }
}

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

/** Les spots dans l'ordre de la côte : chaque spot est suivi du plus proche pas encore pris, en partant du plus au nord. */
private fun coastOrder(spots: List<MapSpot>): List<MapSpot> {
    if (spots.isEmpty()) return emptyList()
    val rest = spots.toMutableList()
    val first = rest.maxByOrNull { it.spot.lat }!!
    rest.remove(first)
    val out = arrayListOf(first)
    while (rest.isNotEmpty()) {
        val last = out.last().spot
        val next = rest.minByOrNull { distanceKm(last.lat, last.lon, it.spot.lat, it.spot.lon) }!!
        rest.remove(next)
        out.add(next)
    }
    return out
}

/**
 * La carte « Où surfer ? » façon carte météo : une note à la place de la température. La côte est colorée par la note, avec des
 * pastilles rondes dessus (la moyenne d'une portion de côte ; en zoomant, un chiffre par spot). On choisit le jour et le moment
 * (matin, midi, après-midi), on touche une pastille pour voir les spots qu'elle résume. En dessous, « Le conseil du coin ».
 */
@Composable
fun WhereToSurfMap(result: WhereResult, homeLat: Double, homeLon: Double, onPickSpot: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val dates = result.days.map { it.date }
    val spots = remember(result) { result.scans.map { MapSpot(it.first, it.second, false) } + listOfNotNull(result.home?.let { MapSpot(it.first, it.second, true) }) }
    val chain = remember(spots) { coastOrder(spots) }
    var dateIndex by remember(result) { mutableIntStateOf(result.days.indexOfFirst { it.worthMoving }.coerceAtLeast(0)) }
    var hour by remember(result) { mutableIntStateOf(13) }
    var selected by remember(result) { mutableStateOf<String?>(null) }
    // La pastille touchée : les noms des spots qu'elle résume.
    var zoneNames by remember(result) { mutableStateOf<List<String>?>(null) }
    val date = dates.getOrNull(dateIndex)

    // Les spots à ce moment : leur note et leurs conditions.
    val points = remember(chain, date, hour) {
        chain.map { ms ->
            val hr = date?.let { hourOf(ms.hours, it, hour) }
            Pt(ms, hr, if (hr == null) 0 else calculateSlotRating(hr, ms.spot.facing, NEUTRAL_LEVEL, bandShift = 0).score)
        }
    }

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

    fun screenPos(lat: Double, lon: Double) = Offset(
        size.width / 2f + ((lon - cLon) * scale).toFloat(),
        size.height / 2f - ((mercY(lat) - cMy) * scale).toFloat()
    )

    /** Regroupe la côte en portions d'environ 76 dp d'écran : chacune porte la note moyenne de ses spots. */
    fun computeGroups(): List<Grp> {
        if (scale <= 0f || size.width == 0 || points.isEmpty()) return emptyList()
        val minPx = with(density) { 76.dp.toPx() }
        val out = ArrayList<Grp>()
        var cur = ArrayList<Pair<Pt, Offset>>()
        var len = 0f
        var prev: Pair<Pt, Offset>? = null
        fun finish(list: List<Pair<Pt, Offset>>) {
            val mean = list.map { it.first.score }.average().toFloat()
            out.add(Grp(list.map { it.first }, list[list.size / 2].second, starsForScore(mean.roundToInt(), 0), mean))
        }
        for (p in points) {
            val pos = screenPos(p.ms.spot.lat, p.ms.spot.lon)
            val pr = prev
            if (pr != null) {
                val d = distanceKm(pr.first.ms.spot.lat, pr.first.ms.spot.lon, p.ms.spot.lat, p.ms.spot.lon)
                if (d < 150) len += (pos - pr.second).getDistance() else { if (cur.isNotEmpty()) finish(cur); cur = ArrayList(); len = 0f }
            }
            val here = p to pos
            cur.add(here)
            prev = here
            if (len >= minPx) { finish(cur); cur = ArrayList(); len = 0f }
        }
        if (cur.isNotEmpty()) finish(cur)
        return out.filter { it.pos.x > -40f && it.pos.x < size.width + 40f && it.pos.y > -40f && it.pos.y < size.height + 40f }
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
                            scale = (scale * zoom).coerceIn(fitScale * 0.5f, fitScale * 60f)
                            cLon = lonAtC - (centroid.x + pan.x - w / 2f) / scale
                            cMy = myAtC + (centroid.y + pan.y - hh / 2f) / scale
                        }
                    }
                    .pointerInput(result, points) {
                        detectTapGestures { tap ->
                            if (scale <= 0f) return@detectTapGestures
                            val limit = with(density) { 30.dp.toPx() }
                            val hit = computeGroups().minByOrNull { (it.pos - tap).getDistance() }?.takeIf { (it.pos - tap).getDistance() < limit }
                            // Une pastille de plusieurs spots montre leur sélection ; un spot seul, sa fiche.
                            if (hit != null && hit.members.size > 1) { zoneNames = hit.members.map { it.ms.spot.name }; selected = null }
                            else { selected = hit?.members?.firstOrNull()?.ms?.spot?.name; zoneNames = null }
                        }
                    }
            ) {
                drawRect(seaColor)
                if (scale <= 0f) return@Canvas
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
                    val p = screenPos(la, lo)
                    if (b == 0) ring.moveTo(p.x, p.y) else ring.lineTo(p.x, p.y)
                }
                ring.close()
                drawPath(ring, Color(0x143FD0B0))
                drawPath(ring, Color(0xE6FFFFFF), style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))))
                // Le ruban : la côte colorée par la note de ses spots.
                for (i in 1 until points.size) {
                    val a = points[i - 1]
                    val b = points[i]
                    if (distanceKm(a.ms.spot.lat, a.ms.spot.lon, b.ms.spot.lat, b.ms.spot.lon) >= 150) continue
                    val pa = screenPos(a.ms.spot.lat, a.ms.spot.lon)
                    val pb = screenPos(b.ms.spot.lat, b.ms.spot.lon)
                    drawLine(Color(0xBF061018), pa, pb, strokeWidth = 13.dp.toPx(), cap = StrokeCap.Round)
                    drawLine(Brush.linearGradient(listOf(scoreGradientColor(a.score), scoreGradientColor(b.score)), start = pa, end = pb), pa, pb, strokeWidth = 8.dp.toPx(), cap = StrokeCap.Round)
                }
                // Les pastilles.
                val showNames = scale > size.width * 0.29f
                computeGroups().forEach { g ->
                    val inZone = zoneNames?.let { names -> g.members.any { it.ms.spot.name in names } } == true
                    val isSel = inZone || (g.members.size == 1 && g.members[0].ms.spot.name == selected)
                    val r = 17.dp.toPx()
                    drawCircle(scoreGradientColor(g.score.roundToInt()).copy(alpha = 0.96f), radius = r, center = g.pos)
                    drawCircle(Color.White, radius = r, center = g.pos, style = Stroke(if (isSel) 4.dp.toPx() else 2.dp.toPx()))
                    val txt = measurer.measure(starsText(g.stars), TextStyle(fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF061018)))
                    drawText(txt, topLeft = Offset(g.pos.x - txt.size.width / 2f, g.pos.y - txt.size.height / 2f))
                    if (g.members.size > 1) {
                        val c = Offset(g.pos.x + 15.dp.toPx(), g.pos.y - 15.dp.toPx())
                        drawCircle(Color(0xFF0B1620), radius = 9.dp.toPx(), center = c)
                        val badge = measurer.measure("+${g.members.size - 1}", TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White))
                        drawText(badge, topLeft = Offset(c.x - badge.size.width / 2f, c.y - badge.size.height / 2f))
                    } else if (showNames) {
                        val name = measurer.measure(g.members[0].ms.spot.name, TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White))
                        val top = Offset(g.pos.x - name.size.width / 2f, g.pos.y + r + 2.dp.toPx())
                        drawRoundRect(Color(0xB308121C), topLeft = Offset(top.x - 3.dp.toPx(), top.y - 1.dp.toPx()), size = androidx.compose.ui.geometry.Size(name.size.width + 6.dp.toPx(), name.size.height + 2.dp.toPx()), cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()))
                        drawText(name, topLeft = top)
                    }
                }
                // Le spot de départ.
                result.home?.first?.let { home ->
                    val p = screenPos(home.lat, home.lon)
                    drawCircle(Color.White, radius = 4.dp.toPx(), center = p)
                    drawCircle(Color(0xFF0B1620), radius = 4.dp.toPx(), center = p, style = Stroke(1.5.dp.toPx()))
                }
            }
            Text("NASA Blue Marble", fontSize = 9.sp, color = Color(0xCCFFFFFF), modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp))
        }
        // La légende des notes.
        Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr("Note"), fontSize = 10.5.sp, color = colors.onSurfaceVariant)
            Box(
                modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp))
                    .background(Brush.horizontalGradient(listOf(scoreGradientColor(12), scoreGradientColor(35), scoreGradientColor(53), scoreGradientColor(71), scoreGradientColor(85), scoreGradientColor(97))))
            )
            Text("1 ★ → 5 ★", fontSize = 10.5.sp, color = colors.onSurfaceVariant)
        }
        // Jour : une pastille par jour avec les étoiles du meilleur spot du rayon.
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            result.days.forEachIndexed { i, day ->
                val on = i == dateIndex
                Column(
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (on) colors.primary else colors.surfaceVariant)
                        .clickable { dateIndex = i; selected = null; zoneNames = null }.padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("${frenchShortDayName(day.date)} ${day.date.day}", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = if (on) colors.onPrimary else colors.onSurface)
                    Text(day.best?.let { "★ " + starsText(it.stars) } ?: "–", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (on) colors.onPrimary else Color(0xFFFFC107))
                }
            }
        }
        // Moment : matin, midi, après-midi.
        Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MOMENTS.forEach { (label, hh) ->
                val on = hour == hh
                Box(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (on) colors.primary else colors.surfaceVariant)
                        .clickable { hour = hh; selected = null; zoneNames = null }.padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) { Text(tr(label), fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, color = if (on) colors.onPrimary else colors.onSurface) }
            }
        }
        Spacer(Modifier.height(8.dp))

        // La zone touchée : ses spots, du meilleur au moins bon.
        val zoneSpots = zoneNames?.let { names -> names.mapNotNull { nm -> points.firstOrNull { it.ms.spot.name == nm } } }.orEmpty().sortedByDescending { it.score }
        if (zoneSpots.isNotEmpty()) {
            Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.surfaceVariant).padding(12.dp)) {
                Text(zoneSpots.first().ms.spot.zone.let { if (it == "ici") tr("Autour de toi") else it } + " · " + zoneSpots.size + " " + tr("spots"), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                Text(tr("Du meilleur au moins bon, au moment choisi"), fontSize = 11.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
                zoneSpots.forEach { pt -> SpotLine(pt) { selected = pt.ms.spot.name; zoneNames = null } }
            }
            Spacer(Modifier.height(8.dp))
        }
        // La fiche d'un spot.
        val sel = points.firstOrNull { it.ms.spot.name == selected }
        if (sel != null && sel.hour != null) {
            Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.surfaceVariant).padding(12.dp)) {
                SpotLine(sel, null)
                if (!sel.ms.isHome) Text(
                    tr("Aller sur ce spot") + " ›", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.primary,
                    modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(50)).clickable { onPickSpot(sel.ms.spot.name) }.padding(vertical = 4.dp, horizontal = 2.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
        } else if (zoneSpots.isEmpty()) {
            Text(tr("Touche une pastille pour voir les spots qu'elle résume. Zoome pour les détailler."), fontSize = 11.5.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
        }
        if (date != null) LocalAdvice(result, date, hour)
    }
}

/** Une ligne de spot : son nom, ses conditions au moment choisi, son vent (offshore…) et sa houle (de face…), ses étoiles. */
@Composable
private fun SpotLine(pt: Pt, onClick: (() -> Unit)?) {
    val colors = MaterialTheme.colorScheme
    val hr = pt.hour
    Row(
        modifier = Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick) else Modifier).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(pt.ms.spot.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
            Text(if (pt.ms.isHome) tr("ici") else "${pt.ms.spot.distanceKm} km", fontSize = 11.sp, color = colors.onSurfaceVariant)
            if (hr != null) {
                Text(
                    "${formatDecimal(hr.waveHeight, 1).replace('.', ',')} m · ${hr.wavePeriod.roundToInt()} s · ${tr("vent")} ${hr.windSpeedKmh} km/h ${SurfUnitsHelper.formatCardinalFr(hr.windDirectionStr)}",
                    fontSize = 11.5.sp, color = colors.onSurfaceVariant
                )
                Text(
                    windWord(windCategoryFor(hr.windDirectionStr, pt.ms.spot.facing)) + " · " + swellWord(hr.waveDirection.toDouble(), pt.ms.spot.facing) +
                        (pt.ms.spot.facing?.let { " ($it°)" } ?: ""),
                    fontSize = 11.5.sp, color = Color(0xFF9FD6E8)
                )
            }
        }
        StarsRow(starsForScore(pt.score, 0))
    }
}

/**
 * « Le conseil du coin », parlé comme quelqu'un du coin : le vent et la houle du moment, ce que ça donne chez toi, l'orientation de
 * plage qu'il faudrait pour que le vent soit offshore, le meilleur compromis entre vent et houle (toutes les orientations essayées)
 * et les spots du rayon qui y répondent.
 */
@Composable
private fun LocalAdvice(result: WhereResult, date: LocalDate, hour: Int) {
    val colors = MaterialTheme.colorScheme
    val home = result.home ?: return
    val hr = hourOf(home.second, date, hour) ?: return
    val windDeg = SurfUnitsHelper.cardinalToDegrees(SurfUnitsHelper.formatCardinalFr(hr.windDirectionStr)).toDouble()
    val bestFacing = (0 until 360 step 10).maxByOrNull { calculateSlotRating(hr, it, NEUTRAL_LEVEL, bandShift = 0).score } ?: return
    val bestScore = calculateSlotRating(hr, bestFacing, NEUTRAL_LEVEL, bandShift = 0).score
    val here = windCategoryFor(hr.windDirectionStr, home.first.facing)
    val offshoreFacing = ((windDeg + 180) % 360).roundToInt()
    val matches = result.scans.mapNotNull { (spot, hours) ->
        val f = spot.facing ?: return@mapNotNull null
        if (angularDifference(f.toDouble(), bestFacing.toDouble()) > 30.0) return@mapNotNull null
        val h = hourOf(hours, date, hour) ?: return@mapNotNull null
        Triple(spot, f, calculateSlotRating(h, f, NEUTRAL_LEVEL, bandShift = 0).score)
    }.sortedByDescending { it.third }.take(4)
    val text = buildString {
        append(tr("Vent de") + " " + compassWord(windDeg) + " ${hr.windSpeedKmh} km/h, " + tr("houle de") + " " + compassWord(hr.waveDirection.toDouble()) + " " + formatDecimal(hr.waveHeight, 1).replace('.', ',') + " m. ")
        home.first.facing?.let { append(tr("Chez toi") + " ($it°) " + tr("le vent est") + " " + windWord(here) + ". ") }
        if (hr.windSpeedKmh >= 12 && here == "onshore") append(tr("Pour qu'il soit offshore, il faudrait une plage tournée vers") + " $offshoreFacing° (${compassWord(offshoreFacing.toDouble())}), " + tr("mais elle ne reçoit pas la houle.") + " ")
        append("\n" + tr("Le meilleur compromis") + " : " + tr("une plage tournée vers") + " $bestFacing° (${compassWord(bestFacing.toDouble())}), " + starsText(starsForScore(bestScore, 0)) + " ★.")
        if (matches.isNotEmpty()) append("\n" + tr("Dans ton rayon, les spots qui répondent à ça") + " : " + matches.joinToString(", ") { "${it.first.name} (${it.second}°, ${starsText(starsForScore(it.third, 0))} ★)" } + ".")
        else append("\n" + tr("Aucun spot du rayon n'est orienté comme ça : élargis le rayon."))
    }
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.surfaceVariant).padding(12.dp)) {
        Text(tr("Le conseil du coin"), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
        Text(text, fontSize = 12.5.sp, color = colors.onSurface, modifier = Modifier.padding(top = 4.dp), textAlign = TextAlign.Start)
        Text(tr("Orientations du catalogue : tu peux corriger celle d'un spot dans les réglages."), fontSize = 10.5.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
    }
}
