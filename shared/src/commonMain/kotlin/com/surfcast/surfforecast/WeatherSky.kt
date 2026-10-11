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
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.surfcast.surfforecast.resources.Res
import com.surfcast.surfforecast.resources.sky_cl_a100
import com.surfcast.surfforecast.resources.sky_cl_a100r
import com.surfcast.surfforecast.resources.sky_cl_a20
import com.surfcast.surfforecast.resources.sky_cl_a40
import com.surfcast.surfforecast.resources.sky_cl_a60
import com.surfcast.surfforecast.resources.sky_cl_a80
import com.surfcast.surfforecast.resources.sky_cl_a80r
import com.surfcast.surfforecast.resources.sky_cl_d100
import com.surfcast.surfforecast.resources.sky_cl_d100r
import com.surfcast.surfforecast.resources.sky_cl_d20
import com.surfcast.surfforecast.resources.sky_cl_d40
import com.surfcast.surfforecast.resources.sky_cl_d60
import com.surfcast.surfforecast.resources.sky_cl_d80
import com.surfcast.surfforecast.resources.sky_cl_d80r
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
 * Fond météo vivant de la vue semaine et du déroulé du jour : un ciel stable (bleu net, gris quand c'est couvert), un seul
 * champ de nuages dont la quantité suit la couverture de chaque heure (îlots blancs, puis gris, puis couvert), un soleil
 * derrière les nuages, de la pluie et de la neige qui tombent. Il remplace les icônes météo.
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

/** Une valeur qui varie le long de l'axe horizontal : interpolée entre des points, puis lissée (pas de marche brusque). [smooth] : écart-type du lissage, en pixels. */
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

    /** Moyenne gaussienne d'écart-type [smooth] (pixels) : un changement brusque s'étale en rampe douce, sans colonne. */
    fun at(x: Float): Float {
        if (smooth < 0.5f) return raw(x)
        var a = 0f
        var ws = 0f
        var k = -3f
        while (k <= 3.001f) {
            val wgt = exp(-0.5f * k * k)
            a += wgt * raw(x + k * smooth)
            ws += wgt
            k += 0.5f
        }
        return a / ws
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

/** Nuages déjà colorés pour un thème : un par palier de couverture, et pour les deux plus couverts une version sous la pluie. */
internal class CloudSet(val levels: List<ImageBitmap>, val rainy: List<ImageBitmap?>)

internal class SkyImages(
    val light: CloudSet, val dark: CloudSet, val sun: ImageBitmap, val rain: ImageBitmap, val snow: ImageBitmap,
    val water: ImageBitmap, val waterRain: ImageBitmap
)

/** Les images du ciel, chargées dès le lancement de l'appli (voir SurfLogApp) pour être prêtes quand la semaine s'affiche. */
internal val LocalSkyImages = androidx.compose.runtime.compositionLocalOf<SkyImages?> { null }

@Composable
internal fun rememberSkyImages(): SkyImages = SkyImages(
    light = CloudSet(
        listOf(imageResource(Res.drawable.sky_cl_a20), imageResource(Res.drawable.sky_cl_a40), imageResource(Res.drawable.sky_cl_a60),
            imageResource(Res.drawable.sky_cl_a80), imageResource(Res.drawable.sky_cl_a100)),
        listOf(null, null, null, imageResource(Res.drawable.sky_cl_a80r), imageResource(Res.drawable.sky_cl_a100r))
    ),
    dark = CloudSet(
        listOf(imageResource(Res.drawable.sky_cl_d20), imageResource(Res.drawable.sky_cl_d40), imageResource(Res.drawable.sky_cl_d60),
            imageResource(Res.drawable.sky_cl_d80), imageResource(Res.drawable.sky_cl_d100)),
        listOf(null, null, null, imageResource(Res.drawable.sky_cl_d80r), imageResource(Res.drawable.sky_cl_d100r))
    ),
    sun = imageResource(Res.drawable.sky_sun),
    rain = imageResource(Res.drawable.sky_rain),
    snow = imageResource(Res.drawable.sky_snow),
    water = imageResource(Res.drawable.sky_water),
    waterRain = imageResource(Res.drawable.sky_water_rain)
)

private fun smoothstep(a: Float, b: Float, x: Float): Float {
    val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
private fun mix(a: Float, b: Float, t: Float): Float = a + (b - a) * t

private const val CLOUD_SPAN = 0.62f       // part de la largeur de l'image visible à l'écran
private const val CLOUD_OFFSET = 0.078f    // départ dans l'image, en part de sa largeur

// Fond du ciel, haut puis bas (rouge, vert, bleu) : du bleu net au gris selon la couverture.
private val SKY_LIGHT_CLEAR = arrayOf(floatArrayOf(58f, 126f, 203f), floatArrayOf(150f, 200f, 242f))
private val SKY_LIGHT_OVERCAST = arrayOf(floatArrayOf(112f, 124f, 138f), floatArrayOf(158f, 170f, 182f))
private val SKY_DARK_CLEAR = arrayOf(floatArrayOf(9f, 34f, 68f), floatArrayOf(22f, 74f, 118f))
private val SKY_DARK_OVERCAST = arrayOf(floatArrayOf(14f, 20f, 29f), floatArrayOf(30f, 40f, 52f))

private fun skyColor(cover: Float, isDark: Boolean, bottom: Boolean): Color {
    val k = smoothstep(0.2f, 0.95f, cover)
    val i = if (bottom) 1 else 0
    val a = if (isDark) SKY_DARK_CLEAR[i] else SKY_LIGHT_CLEAR[i]
    val b = if (isDark) SKY_DARK_OVERCAST[i] else SKY_LIGHT_OVERCAST[i]
    return Color(mix(a[0], b[0], k) / 255f, mix(a[1], b[1], k) / 255f, mix(a[2], b[2], k) / 255f)
}

/** Le fond du ciel gardé en image : [key] résume ce qui l'a produit. */
internal class SkyBackdropCache {
    var bitmap: ImageBitmap? = null
    var key: Long = 0L
}

/** Résumé de ce qui change le fond du ciel : taille, thème, soleil, et la couverture et la pluie le long de l'axe. */
private fun backdropKey(scene: SkyScene, isDark: Boolean, size: Size, skyH: Float): Long {
    var k = 17L
    fun add(v: Int) { k = k * 31L + v }
    add(size.width.roundToInt()); add(size.height.roundToInt()); add(skyH.roundToInt()); add(if (isDark) 1 else 0)
    add(scene.left.roundToInt()); add(scene.top.roundToInt()); add(scene.right.roundToInt()); add(scene.bottom.roundToInt())
    for (s in scene.suns) { add(s.x.roundToInt()); add(s.sizePx.roundToInt()); add(s.topPx.roundToInt()) }
    val w = scene.right - scene.left
    for (i in 0..63) {
        val x = scene.left + w * i / 63f
        add((scene.cover.at(x).coerceIn(0f, 1f) * 64f).roundToInt())
        add((min(1f, scene.rain.at(x) / 1.8f) * 16f).roundToInt())
    }
    return k
}

// Paliers de couverture dessinés : entre deux paliers voisins, deux calques de nuages se fondent horizontalement (somme des poids = 1).
private val CLOUD_LEVELS = floatArrayOf(0.2f, 0.4f, 0.6f, 0.8f, 1f)

/** Poids du palier [i] pour la couverture [c] : 1 sur le palier, 0 sur les paliers voisins, linéaire entre les deux. */
private fun levelWeight(i: Int, c: Float): Float {
    val l = CLOUD_LEVELS[i]
    return when {
        c < l -> if (i == 0) (c / l).coerceIn(0f, 1f) else ((c - CLOUD_LEVELS[i - 1]) / (l - CLOUD_LEVELS[i - 1])).coerceIn(0f, 1f)
        c > l -> if (i == CLOUD_LEVELS.lastIndex) 0f else ((CLOUD_LEVELS[i + 1] - c) / (CLOUD_LEVELS[i + 1] - l)).coerceIn(0f, 1f)
        else -> 1f
    }
}

/**
 * Fond, soleil puis nuages. Les nuages sont dessinés en images entières (jamais en bandes) : une image par palier de couverture
 * et de pluie, chacune avec son filtre, fondue dans la suivante par un dégradé horizontal. L'échelle est la même en largeur et
 * en hauteur ; la texture se répète vers le bas si le ciel est plus haut qu'elle.
 */
private fun DrawScope.drawBackdrop(scene: SkyScene, im: SkyImages, isDark: Boolean, w: Float, h: Float, n: Int) {
    val bounds = Rect(scene.left, scene.top, scene.right, scene.bottom)
    fun coverAt(x: Float) = scene.cover.at(x).coerceIn(0f, 1f)
    fun rainShare(x: Float) = min(1f, scene.rain.at(x) / 1.8f)

    clipRect(scene.left, scene.top, scene.right, scene.bottom) {
        // Fond du ciel : bleu net quand c'est dégagé, gris quand c'est couvert, fondu horizontalement.
        drawRect(
            brush = Brush.verticalGradient(listOf(skyColor(0f, isDark, false), skyColor(0f, isDark, true)), startY = scene.top, endY = scene.bottom),
            topLeft = Offset(scene.left, scene.top), size = Size(w, h)
        )
        drawIntoCanvas { canvas ->
            canvas.saveLayer(bounds, Paint())
            drawRect(
                brush = Brush.verticalGradient(listOf(skyColor(1f, isDark, false), skyColor(1f, isDark, true)), startY = scene.top, endY = scene.bottom),
                topLeft = Offset(scene.left, scene.top), size = Size(w, h)
            )
            val stops = fadeStops(scene, n) { x -> smoothstep(0.2f, 0.95f, coverAt(x)) }
            drawRect(
                brush = Brush.horizontalGradient(*stops, startX = scene.left, endX = scene.right),
                topLeft = Offset(scene.left, scene.top), size = Size(w, h), blendMode = BlendMode.DstIn
            )
            canvas.restore()
        }
    }

    // Soleil : derrière les nuages, jamais coupé ; il apparaît entre les îlots et s'efface sous la couverture.
    for (sun in scene.suns) {
        val a = ((1f - smoothstep(0.4f, 0.97f, scene.cover.at(sun.x)) * 0.92f) * (if (isDark) 0.85f else 0.95f)).coerceIn(0f, 1f)
        if (a <= 0.03f) continue
        drawImage(
            im.sun, srcOffset = IntOffset.Zero, srcSize = IntSize(im.sun.width, im.sun.height),
            dstOffset = IntOffset((sun.x - sun.sizePx / 2f).roundToInt(), sun.topPx.roundToInt()),
            dstSize = IntSize(sun.sizePx.roundToInt(), sun.sizePx.roundToInt()), alpha = a
        )
    }

    val set = if (isDark) im.dark else im.light
    clipRect(scene.left, scene.top, scene.right, scene.bottom) {
        // Les calques sont additionnés (poids de somme 1) : superposés, leur opacité cumulée ferait des bandes plus denses.
        drawIntoCanvas { canvas ->
            canvas.saveLayer(bounds, Paint())
            for (li in CLOUD_LEVELS.indices) for (rainy in 0..1) {
                val img = (if (rainy == 1) set.rainy[li] else set.levels[li]) ?: continue
                val hasRainy = set.rainy[li] != null
                val stops = fadeStops(scene, n) { x ->
                    val share = if (!hasRainy) (if (rainy == 1) 0f else 1f) else if (rainy == 1) rainShare(x) else 1f - rainShare(x)
                    levelWeight(li, coverAt(x)) * share
                }
                if (stops.all { it.second.alpha < 0.01f }) continue
                val scale = max(w / (img.width * CLOUD_SPAN), h / img.height)   // pixels d'écran par pixel d'image, sur les deux axes
                val srcW = (w / scale).roundToInt().coerceIn(1, img.width)
                canvas.saveLayer(bounds, Paint().apply { blendMode = BlendMode.Plus })
                drawImage(
                    img, srcOffset = IntOffset((img.width * CLOUD_OFFSET).roundToInt().coerceAtMost(img.width - srcW), 0),
                    srcSize = IntSize(srcW, img.height),
                    dstOffset = IntOffset(scene.left.roundToInt(), scene.top.roundToInt()),
                    dstSize = IntSize(w.roundToInt(), (img.height * scale).roundToInt())
                )
                drawRect(
                    brush = Brush.horizontalGradient(*stops, startX = scene.left, endX = scene.right),
                    topLeft = Offset(scene.left, scene.top), size = Size(w, h), blendMode = BlendMode.DstIn
                )
                canvas.restore()
            }
            canvas.restore()
        }
    }
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
    val cache = remember { SkyBackdropCache() }
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
        if (scene != null) drawSky(scene, images, isDark, frameNs / 1_000_000_000f, cache)
    }
}

private fun DrawScope.fadeStops(scene: SkyScene, n: Int, level: (Float) -> Float): Array<Pair<Float, Color>> =
    Array(n + 1) { i ->
        val f = i.toFloat() / n
        val x = scene.left + (scene.right - scene.left) * f
        f to Color.Black.copy(alpha = level(x).coerceIn(0f, 1f))
    }

private fun DrawScope.drawSky(scene: SkyScene, im: SkyImages, isDark: Boolean, t: Float, cache: SkyBackdropCache) {
    val w = scene.right - scene.left
    val h = scene.bottom - scene.top
    if (w <= 1f || h <= 1f) return
    val n = max(8, (w / (6f * density)).toInt())
    val bounds = Rect(scene.left, scene.top, scene.right, scene.bottom)
    val skyH = h

    // Fond du ciel, soleil et nuages : dessinés une fois dans une image, refaite seulement quand la couverture, la taille, le
    // thème ou l'heure du soleil changent. La pluie, qui anime l'écran, ne les recalcule donc pas à chaque image.
    val key = backdropKey(scene, isDark, size, skyH)
    var backdrop = cache.bitmap
    if (backdrop == null || cache.key != key) {
        val bmp = ImageBitmap(size.width.roundToInt().coerceAtLeast(1), size.height.roundToInt().coerceAtLeast(1))
        CanvasDrawScope().draw(this, layoutDirection, androidx.compose.ui.graphics.Canvas(bmp), size) { drawBackdrop(scene, im, isDark, w, h, n) }
        cache.bitmap = bmp
        cache.key = key
        backdrop = bmp
    }
    drawImage(backdrop)

    clipRect(scene.left, scene.top, scene.right, scene.bottom) {
        // Pluie et neige : deux couches chacune, qui tombent à des vitesses différentes. En thème clair, la pluie est
        // sombre sur un ciel clair et redevient claire sur un ciel très sombre.
        val d = density
        class Layer(val img: ImageBitmap, val scale: Float, val ax: Float, val speed: Float, val xoff: Float, val mask: (Float) -> Float, val alpha: Float, val tint: Color?)
        fun rainMask(x: Float) = min(1f, (scene.rain.at(x) / 1.5f).coerceAtLeast(0f).pow(0.6f))
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
