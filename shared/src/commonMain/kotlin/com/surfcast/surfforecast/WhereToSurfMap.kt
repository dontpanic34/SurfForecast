package com.surfcast.surfforecast

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.AnnotatedString
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
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

private const val TILE_URL = "https://gibs.earthdata.nasa.gov/wmts/epsg3857/best/BlueMarble_ShadedRelief_Bathymetry/default/2004-08/GoogleMapsCompatible_Level8"
private const val MAX_TILE_ZOOM = 8

/** Vert du n° 1 et des jours qui valent le déplacement. */
internal val WhereGood = Color(0xFF2BD47A)

private fun mercY(lat: Double) = ln(tan(PI / 4 + lat * PI / 360)) * 180 / PI

private fun hourOf(hours: List<HourlyUiModel>, date: LocalDate, hour: Int) = hours.firstOrNull { it.rawTime.date == date && it.rawTime.hour == hour }

private val COMPASS = arrayOf("nord", "nord-est", "est", "sud-est", "sud", "sud-ouest", "ouest", "nord-ouest")
private fun compassWord(deg: Double) = COMPASS[(((deg % 360 + 360) % 360 + 22.5) / 45).toInt() % 8]

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
 * La carte « Où surfer ? » (maquette `ou-surfer-lisible.html`, carte en premier) : fond NASA, cercle du rayon, ta maison et les spots
 * du classement du jour, numérotés (le n° 1 en vert). Rien d'autre dessus. Elle se cadre sur ta maison et ces spots ; on peut zoomer.
 * [banner] : la réponse du jour, écrite en haut de la carte.
 */
@Composable
fun WhereToSurfMap(result: WhereResult, homeLat: Double, homeLon: Double, day: DayPick?, banner: AnnotatedString) {
    val ranked = day?.top.orEmpty()
    var size by remember { mutableStateOf(IntSize.Zero) }
    var cLon by remember { mutableFloatStateOf(homeLon.toFloat()) }
    var cMy by remember { mutableFloatStateOf(mercY(homeLat).toFloat()) }
    var scale by remember { mutableFloatStateOf(0f) }
    var fitScale by remember { mutableFloatStateOf(1f) }

    // Cadre ajusté à la maison et aux spots classés, avec de la place en haut pour la réponse.
    LaunchedEffect(size, day, result.radiusKm) {
        if (size.width == 0) return@LaunchedEffect
        val lats = listOf(homeLat) + ranked.map { it.spot.lat }
        val lons = listOf(homeLon) + ranked.map { it.spot.lon }
        val minLon = lons.min() - 0.35
        val maxLon = lons.max() + 0.35
        val minMy = mercY(lats.min()) - 0.35
        val maxMy = mercY(lats.max()) + 0.35
        val topPad = size.height * 0.17f
        val sx = size.width / (maxLon - minLon)
        val sy = (size.height - topPad) / (maxMy - minMy)
        val s = min(sx, sy).toFloat().coerceAtMost(size.width / 0.6f)
        fitScale = s
        scale = s
        cLon = ((minLon + maxLon) / 2).toFloat()
        cMy = ((minMy + maxMy) / 2 + (topPad / 2) / s).toFloat()
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

    val measurer = rememberTextMeasurer()
    val seaColor = Color(0xFF0E2C44)
    val accent = MaterialTheme.colorScheme.primary

    Box(modifier = Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(12.dp)).onSizeChanged { size = it }) {
        Canvas(
            modifier = Modifier.fillMaxWidth().height(300.dp)
                .pointerInput(result, day) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        if (scale <= 0f) return@detectTransformGestures
                        val w = size.width
                        val hh = size.height
                        val lonAtC = cLon + (centroid.x - w / 2f) / scale
                        val myAtC = cMy - (centroid.y - hh / 2f) / scale
                        scale = (scale * zoom).coerceIn(fitScale * 0.3f, fitScale * 40f)
                        cLon = lonAtC - (centroid.x + pan.x - w / 2f) / scale
                        cMy = myAtC + (centroid.y + pan.y - hh / 2f) / scale
                    }
                }
        ) {
            drawRect(seaColor)
            if (scale <= 0f) return@Canvas
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
            drawPath(ring, Color(0x99FFFFFF), style = Stroke(width = 1.2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))))
            // Les spots classés, du dernier au premier (le n° 1 par-dessus).
            ranked.indices.reversed().forEach { i ->
                val sp = ranked[i].spot
                val p = screenPos(sp.lat, sp.lon)
                val r = 11.dp.toPx()
                drawCircle(if (i == 0) WhereGood else accent, radius = r, center = p)
                drawCircle(Color.White, radius = r, center = p, style = Stroke(2.dp.toPx()))
                val txt = measurer.measure("${i + 1}", TextStyle(fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF061018)))
                drawText(txt, topLeft = Offset(p.x - txt.size.width / 2f, p.y - txt.size.height / 2f))
            }
            // Ta maison.
            val hp = screenPos(homeLat, homeLon)
            drawCircle(Color.White, radius = 8.dp.toPx(), center = hp)
            drawCircle(Color(0xFF0B1620), radius = 8.dp.toPx(), center = hp, style = Stroke(2.dp.toPx()))
            val house = measurer.measure("⌂", TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0B1620)))
            drawText(house, topLeft = Offset(hp.x - house.size.width / 2f, hp.y - house.size.height / 2f))
        }
        // La réponse du jour, écrite sur la carte.
        Text(
            banner, fontSize = 12.5.sp, color = Color.White, lineHeight = 16.sp,
            modifier = Modifier.align(Alignment.TopCenter).padding(8.dp).fillMaxWidth()
                .clip(RoundedCornerShape(10.dp)).background(Color(0xC7061018)).padding(horizontal = 10.dp, vertical = 7.dp)
        )
        Text("NASA Blue Marble", fontSize = 9.sp, color = Color(0xCCFFFFFF), modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp))
    }
}

/**
 * « Le conseil du coin », en plages plutôt qu'en degrés : le vent du jour, ce qu'il fait chez toi, le côté vers lequel une plage
 * devrait regarder pour s'en sortir, et les spots du rayon qui regardent par là.
 */
@Composable
fun LocalAdvice(result: WhereResult, date: LocalDate, hour: Int) {
    val colors = MaterialTheme.colorScheme
    val home = result.home ?: return
    val hr = hourOf(home.second, date, hour) ?: return
    val windDeg = SurfUnitsHelper.cardinalToDegrees(SurfUnitsHelper.formatCardinalFr(hr.windDirectionStr)).toDouble()
    val bestFacing = (0 until 360 step 10).maxByOrNull { calculateSlotRating(hr, it, NEUTRAL_LEVEL, bandShift = 0).score } ?: return
    val here = windCategoryFor(hr.windDirectionStr, home.first.facing)
    val matches = result.scans.mapNotNull { (spot, hours) ->
        val f = spot.facing ?: return@mapNotNull null
        if (angularDifference(f.toDouble(), bestFacing.toDouble()) > 30.0) return@mapNotNull null
        val h = hourOf(hours, date, hour) ?: return@mapNotNull null
        spot to calculateSlotRating(h, f, NEUTRAL_LEVEL, bandShift = 0).score
    }.sortedByDescending { it.second }.take(3).map { it.first.name }
    val text = buildString {
        append(tr("Vent de") + " " + compassWord(windDeg) + " ${hr.windSpeedKmh} km/h : ")
        append(
            when (here) {
                "onshore" -> tr("de face chez toi")
                "side-on" -> tr("de côté, venant de la mer, chez toi")
                "side" -> tr("de côté chez toi")
                "side-off" -> tr("de côté, venant de la terre, chez toi")
                else -> tr("de terre chez toi")
            }
        )
        append(". ")
        if (here == "offshore" || here == "side-off" || hr.windSpeedKmh < 10) {
            append(tr("Pas de piège de vent ce jour-là : la note suit surtout la taille de la houle."))
        } else {
            append(tr("Les plages tournées vers le") + " " + compassWord(bestFacing.toDouble()) + " " + tr("s'en sortent mieux"))
            if (matches.isNotEmpty()) append(", " + tr("comme") + " " + matches.joinToString(", ")) else append(" ; " + tr("aucune dans ton rayon, élargis-le"))
            append(".")
        }
    }
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.surfaceVariant).padding(12.dp)) {
        Text(tr("Le conseil du coin"), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
        Text(text, fontSize = 12.5.sp, color = colors.onSurface, modifier = Modifier.padding(top = 4.dp), lineHeight = 17.sp)
    }
}
