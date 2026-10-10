package com.surfcast.surfforecast

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.surfcast.surfforecast.resources.Res
import com.surfcast.surfforecast.resources.sky_clouds_1
import com.surfcast.surfforecast.resources.sky_clouds_2
import com.surfcast.surfforecast.resources.sky_clouds_3
import com.surfcast.surfforecast.resources.sky_clouds_4
import com.surfcast.surfforecast.resources.sky_rain
import com.surfcast.surfforecast.resources.sky_snow
import com.surfcast.surfforecast.resources.sky_sun
import com.surfcast.surfforecast.resources.sky_water
import com.surfcast.surfforecast.resources.sky_water_rain
import org.jetbrains.compose.resources.imageResource
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Fond météo vivant de la vue semaine et du déroulé du jour : de vraies images de nuages (quatre niveaux, du blanc au gris
 * foncé, fondus entre eux selon la couverture de chaque heure), un soleil qui s'efface sous les nuages, de la pluie et de la
 * neige qui tombent. Il remplace les icônes météo. Pas de ciel bleu : le fond de la carte reste le nôtre.
 */

/** Pluie estimée (de 0 à environ 2,5 mm/h) d'après le code météo de la prévision, qui ne donne pas de quantité. */
fun rainAmountForCode(code: Int): Float = when (code) {
    51 -> 0.25f
    53 -> 0.6f
    55 -> 1.0f
    56, 57 -> 0.5f
    61 -> 0.7f
    63 -> 1.5f
    65 -> 2.5f
    66, 67 -> 1.2f
    80 -> 0.7f
    81 -> 1.5f
    82 -> 2.5f
    95, 96, 99 -> 2.0f
    else -> 0f
}

/** Neige estimée (de 0 à environ 1,8) d'après le code météo. */
fun snowAmountForCode(code: Int): Float = when (code) {
    71 -> 0.5f
    73 -> 1.0f
    75 -> 1.8f
    77 -> 0.7f
    85 -> 1.0f
    86 -> 1.8f
    else -> 0f
}

/** Une valeur qui varie le long de l'axe horizontal : interpolée entre des points, puis lissée (pas de marche brusque). */
class SkyCurve(private val xs: FloatArray, private val vs: FloatArray, private val smooth: Float) {
    private fun raw(x: Float): Float {
        if (xs.isEmpty()) return 0f
        if (x <= xs.first()) return vs.first()
        for (i in 1 until xs.size) {
            if (x <= xs[i]) {
                val t = (x - xs[i - 1]) / (xs[i] - xs[i - 1]).coerceAtLeast(1e-3f)
                return vs[i - 1] + (vs[i] - vs[i - 1]) * t
            }
        }
        return vs.last()
    }

    fun at(x: Float): Float {
        var a = 0f
        for (k in -3..3) a += raw(x + k * smooth)
        return a / 7f
    }
}

class SunSpot(val x: Float, val sizePx: Float, val topPx: Float)

/**
 * Ce qu'il faut dessiner : la zone du ciel (en pixels du canevas) et les trois courbes météo le long de l'axe.
 * [skyHeight] : hauteur sur laquelle les nuages sont étalés, depuis le haut, c'est la zone au-dessus de l'eau (les nuages
 * restent ainsi visibles au lieu de tomber derrière l'océan). 0 = toute la zone.
 */
class SkyScene(
    val left: Float,
    val right: Float,
    val top: Float,
    val bottom: Float,
    val cover: SkyCurve,
    val rain: SkyCurve,
    val snow: SkyCurve,
    val suns: List<SunSpot>,
    val animate: Boolean,
    val skyHeight: Float = 0f
)

internal class SkyImages(
    val clouds: List<ImageBitmap>, val sun: ImageBitmap, val rain: ImageBitmap, val snow: ImageBitmap,
    val water: ImageBitmap, val waterRain: ImageBitmap
)

@Composable
internal fun rememberSkyImages(): SkyImages = SkyImages(
    clouds = listOf(
        imageResource(Res.drawable.sky_clouds_1), imageResource(Res.drawable.sky_clouds_2),
        imageResource(Res.drawable.sky_clouds_3), imageResource(Res.drawable.sky_clouds_4)
    ),
    sun = imageResource(Res.drawable.sky_sun),
    rain = imageResource(Res.drawable.sky_rain),
    snow = imageResource(Res.drawable.sky_snow),
    water = imageResource(Res.drawable.sky_water),
    waterRain = imageResource(Res.drawable.sky_water_rain)
)

/** Les images du ciel, chargées dès le lancement de l'appli (voir SurfLogApp) pour être prêtes quand la semaine s'affiche. */
internal val LocalSkyImages = androidx.compose.runtime.compositionLocalOf<SkyImages?> { null }

// Trois ambiances reliées en continu selon la couverture : blanc et éclaircies (30 %), gris (70 %), couvert sombre et bas (100 %).
private val KF_COVER = floatArrayOf(0f, 0.3f, 0.7f, 1f)
private val KF_LAYERS = arrayOf(
    floatArrayOf(0f, 0f, 0f, 0f), floatArrayOf(1f, 0f, 0f, 0f), floatArrayOf(0.3f, 0.3f, 1f, 0f), floatArrayOf(0f, 0f, 0.3f, 1f)
)
private val KF_WASH = floatArrayOf(0f, 0f, 0.12f, 0.5f)
private val KF_SUN = floatArrayOf(1f, 0.9f, 0.18f, 0f)
// Fond du ciel en thème clair, du bleu clair au gris ardoise (rouge, vert, bleu).
private val KF_SKY_LIGHT = arrayOf(
    floatArrayOf(206f, 224f, 244f), floatArrayOf(170f, 194f, 224f), floatArrayOf(134f, 150f, 170f), floatArrayOf(90f, 102f, 118f)
)
// Recadrage vertical de chaque niveau de nuages sur la zone où se trouvent vraiment les nuages de l'image.
private val CLOUD_CROP = arrayOf(0.28f to 0.62f, 0.12f to 0.82f, 0.06f to 0.90f, 0.05f to 0.95f)
private val CLOUD_TINT_LIGHT = listOf(
    Color(0xFFC8D2E0).copy(alpha = 0.10f), Color(0xFF8FA0B3).copy(alpha = 0.45f),
    Color(0xFFA5B0BE).copy(alpha = 0.28f), Color(0xFF4A586B).copy(alpha = 0.70f)
)

private fun kfSegment(c: Float): Pair<Int, Float> {
    val cc = c.coerceIn(0f, 1f)
    for (i in 1 until KF_COVER.size) if (cc <= KF_COVER[i]) return (i - 1) to ((cc - KF_COVER[i - 1]) / (KF_COVER[i] - KF_COVER[i - 1]))
    return (KF_COVER.size - 2) to 1f
}

private fun kfLerp(c: Float, v: (Int) -> Float): Float {
    val (i, t) = kfSegment(c)
    return v(i) + (v(i + 1) - v(i)) * t
}

private fun cloudWeight(c: Float, layer: Int): Float = kfLerp(c) { KF_LAYERS[it][layer] }
private fun washAmount(c: Float): Float = kfLerp(c) { KF_WASH[it] }
private fun sunAmount(c: Float): Float = kfLerp(c) { KF_SUN[it] }
private fun skyColorLight(c: Float): Color {
    val (i, t) = kfSegment(c)
    fun ch(k: Int) = (KF_SKY_LIGHT[i][k] + (KF_SKY_LIGHT[i + 1][k] - KF_SKY_LIGHT[i][k]) * t) / 255f
    return Color(ch(0), ch(1), ch(2))
}

/** Horloge pour les anneaux de pluie sur l'eau : tourne seulement quand il pleut. */
@Composable
internal fun rememberRainClock(active: Boolean): State<Float> {
    val t = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(active) {
        if (active) {
            var start = 0L
            while (true) withFrameNanos { ns ->
                if (start == 0L) start = ns
                t.floatValue = (ns - start) / 1_000_000_000f
            }
        }
    }
    return t
}

/**
 * Le ciel : à poser derrière une courbe. [sceneOf] reçoit la taille du canevas et rend la scène (ou null s'il n'y a rien à
 * dessiner). Les images tournent en continu seulement s'il pleut ou neige.
 */
@Composable
fun SkyCanvas(modifier: Modifier, isDark: Boolean, sceneOf: (androidx.compose.ui.geometry.Size) -> SkyScene?) {
    val images = LocalSkyImages.current ?: rememberSkyImages()
    var frameNs by remember { mutableLongStateOf(0L) }
    var animating by remember { mutableLongStateOf(0L) }
    LaunchedEffect(animating) {
        if (animating == 1L) {
            while (true) withFrameNanos { frameNs = it }
        }
    }
    Canvas(modifier.graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
        val scene = sceneOf(size)
        val want = if (scene != null && scene.animate) 1L else 0L
        if (want != animating) animating = want
        if (scene != null) drawSky(scene, images, isDark, frameNs / 1_000_000_000f)
    }
}

private fun DrawScope.fadeStops(scene: SkyScene, n: Int, level: (Float) -> Float): Array<Pair<Float, Color>> =
    Array(n + 1) { i ->
        val f = i.toFloat() / n
        val x = scene.left + (scene.right - scene.left) * f
        f to Color.Black.copy(alpha = level(x).coerceIn(0f, 1f))
    }

private fun DrawScope.drawSky(scene: SkyScene, im: SkyImages, isDark: Boolean, t: Float) {
    val w = scene.right - scene.left
    val h = scene.bottom - scene.top
    if (w <= 1f || h <= 1f) return
    val n = max(8, (w / (6f * density)).toInt())
    val bounds = Rect(scene.left, scene.top, scene.right, scene.bottom)
    val skyH = if (scene.skyHeight > 1f) scene.skyHeight.coerceIn(h * 0.2f, h) else h

    clipRect(scene.left, scene.top, scene.right, scene.bottom) {
        // Fond du ciel (thème clair seulement) : bleu clair quand c'est dégagé, gris ardoise quand c'est couvert.
        if (!isDark) {
            val stops = Array(n + 1) { i ->
                val f = i.toFloat() / n
                f to skyColorLight(scene.cover.at(scene.left + w * f))
            }
            drawRect(
                brush = Brush.horizontalGradient(*stops, startX = scene.left, endX = scene.right),
                topLeft = Offset(scene.left, scene.top), size = Size(w, h)
            )
        }
        // Nuages : quatre calques recadrés et étalés sur le ciel, chacun plus ou moins opaque le long de l'axe.
        for (l in 0 until 4) {
            val stops = fadeStops(scene, n) { x -> cloudWeight(scene.cover.at(x), l) }
            if (stops.all { it.second.alpha < 0.02f }) continue
            drawIntoCanvas { canvas ->
                canvas.saveLayer(bounds, Paint())
                val img = im.clouds[l]
                val (c0, c1) = CLOUD_CROP[l]
                drawImage(
                    img, srcOffset = IntOffset(0, (img.height * c0).roundToInt()), srcSize = IntSize(img.width, (img.height * (c1 - c0)).roundToInt()),
                    dstOffset = IntOffset(scene.left.roundToInt(), scene.top.roundToInt()),
                    dstSize = IntSize(w.roundToInt(), skyH.roundToInt()),
                    colorFilter = if (!isDark) ColorFilter.tint(CLOUD_TINT_LIGHT[l], BlendMode.SrcAtop) else null
                )
                drawRect(
                    brush = Brush.horizontalGradient(*stops, startX = scene.left, endX = scene.right),
                    topLeft = Offset(scene.left, scene.top), size = Size(w, h), blendMode = BlendMode.DstIn
                )
                canvas.restore()
            }
        }
        // Voile sombre quand le ciel est couvert : ciel bas, menaçant.
        run {
            val wash = Color(if (isDark) 0xFF080D14 else 0xFF3A4656)
            val stops = Array(n + 1) { i ->
                val f = i.toFloat() / n
                f to wash.copy(alpha = washAmount(scene.cover.at(scene.left + w * f)).coerceIn(0f, 1f))
            }
            if (stops.any { it.second.alpha > 0.01f }) {
                drawRect(
                    brush = Brush.horizontalGradient(*stops, startX = scene.left, endX = scene.right),
                    topLeft = Offset(scene.left, scene.top), size = Size(w, h)
                )
            }
        }
        // Pluie et neige : deux couches chacune, qui tombent à des vitesses différentes. En thème clair, la pluie est
        // sombre sur un ciel clair et redevient claire sur un ciel très sombre.
        val d = density
        class Layer(val img: ImageBitmap, val scale: Float, val ax: Float, val speed: Float, val xoff: Float, val mask: (Float) -> Float, val alpha: Float, val tint: Color?)
        fun rainMask(x: Float) = min(1f, scene.rain.at(x) / 2.2f * 0.85f)
        fun lightShare(x: Float) = ((scene.cover.at(x) - 0.7f) / 0.2f).coerceIn(0f, 1f)
        val rainDark = Color(0xFF3A4C66).copy(alpha = 0.97f)
        val snowTint = Color(0xFF70849E).copy(alpha = 0.95f)
        val layers = ArrayList<Layer>()
        for ((scale, ax, speed, xoff) in listOf(listOf(0.36f, 2.1f, 170f, 0f), listOf(0.28f, 2.4f, 120f, 57f))) {
            if (isDark) layers.add(Layer(im.rain, scale, ax, speed, xoff, ::rainMask, 0.95f, null))
            else {
                layers.add(Layer(im.rain, scale, ax, speed, xoff, { x -> rainMask(x) * (1f - lightShare(x)) }, 0.95f, rainDark))
                layers.add(Layer(im.rain, scale, ax, speed, xoff, { x -> rainMask(x) * lightShare(x) }, 0.95f, null))
            }
        }
        for ((scale, speed, xoff) in listOf(listOf(0.50f, 45f, 0f), listOf(0.80f, 28f, 91f))) {
            layers.add(Layer(im.snow, scale, 1f, speed, xoff, { x -> min(1f, scene.snow.at(x) / 1.5f * 0.6f) }, 0.95f, if (isDark) null else snowTint))
        }
        for (ly in layers) {
            val stops = fadeStops(scene, n) { x -> ly.mask(x) }
            if (stops.all { it.second.alpha < 0.02f }) continue
            drawIntoCanvas { canvas ->
                canvas.saveLayer(bounds, Paint())
                val iw = (ly.img.width * ly.scale * ly.ax * d).coerceAtLeast(8f)
                val ih = (ly.img.height * ly.scale * d).coerceAtLeast(8f)
                val yo = ((t * ly.speed * d) % ih)
                var xx = scene.left - ((ly.xoff * d) % iw)
                while (xx < scene.right) {
                    var yy = scene.top - ih + yo
                    while (yy < scene.bottom) {
                        drawImage(
                            ly.img, srcOffset = IntOffset.Zero, srcSize = IntSize(ly.img.width, ly.img.height),
                            dstOffset = IntOffset(xx.roundToInt(), yy.roundToInt()),
                            dstSize = IntSize(iw.roundToInt() + 1, ih.roundToInt() + 1), alpha = ly.alpha,
                            colorFilter = ly.tint?.let { ColorFilter.tint(it, BlendMode.SrcAtop) }
                        )
                        yy += ih
                    }
                    xx += iw
                }
                drawRect(
                    brush = Brush.horizontalGradient(*stops, startX = scene.left, endX = scene.right),
                    topLeft = Offset(scene.left, scene.top), size = Size(w, h), blendMode = BlendMode.DstIn
                )
                canvas.restore()
            }
        }
    }

    // Soleil : au premier plan, devant les nuages, jamais coupé (donc hors du rognage du ciel) ; il s'efface sous la couverture.
    for (s in scene.suns) {
        val a = (sunAmount(scene.cover.at(s.x)) * (if (isDark) 0.85f else 0.95f)).coerceIn(0f, 1f)
        if (a <= 0.03f) continue
        drawImage(
            im.sun, srcOffset = IntOffset.Zero, srcSize = IntSize(im.sun.width, im.sun.height),
            dstOffset = IntOffset((s.x - s.sizePx / 2f).roundToInt(), s.topPx.roundToInt()),
            dstSize = IntSize(s.sizePx.roundToInt(), s.sizePx.roundToInt()), alpha = a
        )
    }
}

/**
 * L'océan : l'image d'eau remplit, opaque, tout ce qui est sous la courbe de houle (rien ne transparaît derrière). Quand il
 * pleut, l'image d'eau de pluie (avec ses anneaux) se fond dessus selon la puissance de la pluie, tronçon par tronçon.
 */
internal fun DrawScope.drawWater(
    im: SkyImages, rain: SkyCurve?, under: Path, left: Float, right: Float, topY: Float, bottom: Float
) {
    if (right - left < 2f || bottom - topY < 2f) return
    val dst = IntOffset(left.roundToInt(), topY.roundToInt())
    val dstSize = IntSize((right - left).roundToInt(), (bottom - topY).roundToInt())
    clipPath(under) {
        drawImage(im.water, srcOffset = IntOffset.Zero, srcSize = IntSize(im.water.width, im.water.height), dstOffset = dst, dstSize = dstSize)
        if (rain != null) {
            val seg = 6
            for (i in 0 until seg) {
                val a = left + (right - left) * i / seg
                val b = left + (right - left) * (i + 1) / seg
                val alpha = min(1f, rain.at((a + b) / 2f) / 1.4f)
                if (alpha < 0.02f) continue
                clipRect(a - 0.5f, topY, b + 0.5f, bottom) {
                    drawImage(
                        im.waterRain, srcOffset = IntOffset.Zero, srcSize = IntSize(im.waterRain.width, im.waterRain.height),
                        dstOffset = dst, dstSize = dstSize, alpha = alpha
                    )
                }
            }
        }
    }
}

private fun ringHash(a: Int, b: Float): Float {
    val v = sin(a * 127.1f + b * 311.7f) * 43758.547f
    return v - floor(v)
}

/** Fins anneaux ovales qui naissent, s'élargissent et s'effacent dans l'eau, plus nombreux et plus larges quand la pluie est forte. */
internal fun DrawScope.drawRainRipples(
    under: Path, rain: SkyCurve, surfaceY: (Float) -> Float, left: Float, right: Float, bottom: Float, t: Float
) {
    val dp = density
    clipPath(under) {
        for (i in 0 until 16) {
            val period = 1.5f + (i % 5) * 0.3f
            val u = t / period + i * 0.41f
            val k = floor(u)
            val phase = u - k
            val px = left + 10f * dp + ringHash(i, k) * (right - left - 20f * dp)
            val amount = rain.at(px)
            if (amount <= 0.02f || ringHash(i + 70, k) > min(1f, amount / 2.2f * 1.2f)) continue
            val surface = surfaceY(px)
            val depth = bottom - surface
            val y = surface + 10f * dp + ringHash(i + 9, k) * max(8f * dp, depth * 0.5f)
            val r = (4f + phase * (10f + amount * 6f)) * dp
            val alpha = 0.42f * (1f - phase).pow(1.5f)
            val c = Color(0xFFEBF8FF).copy(alpha = alpha)
            drawOval(c, Offset(px - r, y - r * 0.26f), Size(2f * r, 2f * r * 0.26f), style = Stroke(0.8f * dp))
            drawOval(c, Offset(px - r * 0.6f, y - r * 0.16f), Size(2f * r * 0.6f, 2f * r * 0.16f), style = Stroke(0.8f * dp))
        }
    }
}
