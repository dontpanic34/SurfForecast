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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.withTransform
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
import com.surfcast.surfforecast.resources.sky_atlas_flou
import com.surfcast.surfforecast.resources.sky_atlas_nuages
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

internal class SkyImages(
    val atlas: ImageBitmap, val haze: ImageBitmap, val sun: ImageBitmap, val rain: ImageBitmap, val snow: ImageBitmap,
    val water: ImageBitmap, val waterRain: ImageBitmap
)

/** Les images du ciel, chargées dès le lancement de l'appli (voir SurfLogApp) pour être prêtes quand la semaine s'affiche. */
internal val LocalSkyImages = androidx.compose.runtime.compositionLocalOf<SkyImages?> { null }

@Composable
internal fun rememberSkyImages(): SkyImages = SkyImages(
    atlas = imageResource(Res.drawable.sky_atlas_nuages),
    haze = imageResource(Res.drawable.sky_atlas_flou),
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

/** Part de ciel gris du fond selon la couverture (0 à 1) : suit la couverture ressentie, donc les éclaircies restent bleues et seul un ciel presque couvert vire au gris. */
private fun overcastShare(cover: Float): Float = smoothstep(0.45f, 0.95f, CloudPlan.effective(cover * 100f))


// Fond du ciel, haut puis bas (rouge, vert, bleu) : du bleu net au gris selon la couverture.
private val SKY_LIGHT_CLEAR = arrayOf(floatArrayOf(58f, 126f, 203f), floatArrayOf(150f, 200f, 242f))
private val SKY_LIGHT_OVERCAST = arrayOf(floatArrayOf(112f, 124f, 138f), floatArrayOf(158f, 170f, 182f))
private val SKY_DARK_CLEAR = arrayOf(floatArrayOf(9f, 34f, 68f), floatArrayOf(22f, 74f, 118f))
private val SKY_DARK_OVERCAST = arrayOf(floatArrayOf(14f, 20f, 29f), floatArrayOf(30f, 40f, 52f))

private fun skyColor(cover: Float, isDark: Boolean, bottom: Boolean): Color {
    val k = smoothstep(0.2f, 0.95f, cover)  // (un seul calque de fond : voir [overcastShare] pour le fondu horizontal)
    val i = if (bottom) 1 else 0
    val a = if (isDark) SKY_DARK_CLEAR[i] else SKY_LIGHT_CLEAR[i]
    val b = if (isDark) SKY_DARK_OVERCAST[i] else SKY_LIGHT_OVERCAST[i]
    return Color(mix(a[0], b[0], k) / 255f, mix(a[1], b[1], k) / 255f, mix(a[2], b[2], k) / 255f)
}

/** Le fond du ciel gardé en image : [key] résume ce qui l'a produit. */
internal class SkyBackdropCache {
    var bitmap: ImageBitmap? = null   // fond du ciel (derrière le soleil)
    var front: ImageBitmap? = null    // voile, brume et nuages (devant le soleil)
    var key: Long = 0L
}

/** Résumé de ce qui change le fond du ciel : taille, thème, images, et la couverture et la pluie le long de l'axe (pas le soleil, qui suit l'heure choisie). */
private fun backdropKey(scene: SkyScene, isDark: Boolean, size: Size, skyH: Float, im: SkyImages): Long {
    var k = 17L
    fun add(v: Int) { k = k * 31L + v }
    add(size.width.roundToInt()); add(size.height.roundToInt()); add(skyH.roundToInt()); add(if (isDark) 1 else 0)
    add(im.atlas.width); add(im.atlas.height); add(im.haze.width); add(im.sun.width)   // images pas encore chargées : on refait le ciel quand elles arrivent
    add(scene.left.roundToInt()); add(scene.top.roundToInt()); add(scene.right.roundToInt()); add(scene.bottom.roundToInt())
    val w = scene.right - scene.left
    for (i in 0..63) {
        val x = scene.left + w * i / 63f
        add((scene.cover.at(x).coerceIn(0f, 1f) * 64f).roundToInt())
        add((min(1f, scene.rain.at(x) / 1.8f) * 16f).roundToInt())
    }
    return k
}

/**
 * Fond, soleil, voile de couvert, brume puis nuages. Les nuages sont de vrais nuages détourés (atlas de sprites), posés un à un par
 * [CloudPlan] selon la couverture et la pluie le long de l'axe, à leur taille d'affichage, en composition normale. Tout est dessiné
 * une seule fois dans une image en cache ; seule la pluie est animée.
 */
private fun DrawScope.drawBackdrop(scene: SkyScene, im: SkyImages, isDark: Boolean, w: Float, h: Float, n: Int, front: Boolean) {
    val bounds = Rect(scene.left, scene.top, scene.right, scene.bottom)
    fun coverAt(x: Float) = scene.cover.at(x).coerceIn(0f, 1f)
    fun rainShare(x: Float) = min(1f, scene.rain.at(x) / 1.8f)

    if (!front) clipRect(scene.left, scene.top, scene.right, scene.bottom) {
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
            val stops = fadeStops(scene, n) { x -> overcastShare(coverAt(x)) }
            drawRect(
                brush = Brush.horizontalGradient(*stops, startX = scene.left, endX = scene.right),
                topLeft = Offset(scene.left, scene.top), size = Size(w, h), blendMode = BlendMode.DstIn
            )
            canvas.restore()
        }
    }

    if (!front) return

    // Voile de couvert : grisaille à plat derrière les nuages quand la couverture approche 100 %.
    val th = if (isDark) CLOUD_THEME_DARK else CLOUD_THEME_LIGHT
    clipRect(scene.left, scene.top, scene.right, scene.bottom) {
        drawIntoCanvas { canvas ->
            canvas.saveLayer(bounds, Paint())
            val vc = FloatArray(3) { th.glit[it] * 0.55f + th.gshad[it] * 0.45f }
            fun veilColor(m: Float) = Color((vc[0] * m).coerceIn(0f, 1f), (vc[1] * m).coerceIn(0f, 1f), (vc[2] * m).coerceIn(0f, 1f))
            drawRect(
                brush = Brush.verticalGradient(listOf(veilColor(1.05f), veilColor(0.80f)), startY = scene.top, endY = scene.bottom),
                topLeft = Offset(scene.left, scene.top), size = Size(w, h)
            )
            val stops = fadeStops(scene, n) { x -> CloudPlan.veilAlpha(CloudPlan.effective(coverAt(x) * 100f)) }
            drawRect(
                brush = Brush.horizontalGradient(*stops, startX = scene.left, endX = scene.right),
                topLeft = Offset(scene.left, scene.top), size = Size(w, h), blendMode = BlendMode.DstIn
            )
            canvas.restore()
        }

        // Brume puis nuages : de vrais nuages détourés posés un à un, à leur taille d'affichage (jamais agrandis), composés normalement.
        val plan = CloudPlan.compute(
            CloudAtlas.sprites, w.roundToInt(), h.roundToInt(),
            coverAt = { u -> coverAt(scene.left + u * w) * 100f }, rainAt = { u -> rainShare(scene.left + u * w) }, dark = isDark
        )
        for (p in plan) {
            val r = CloudAtlas.rects[p.spriteId]
            val src = if (p.haze) IntOffset(r[4], r[5]) to IntSize(r[6], r[7]) else IntOffset(r[0], r[1]) to IntSize(r[2], r[3])
            val img = if (p.haze) im.haze else im.atlas
            val k = if (p.haze) floatArrayOf(0f, 0f, 0f) else floatArrayOf((p.lit[0] - p.shad[0]) * p.litScale, (p.lit[1] - p.shad[1]) * p.litScale, (p.lit[2] - p.shad[2]) * p.litScale)
            val filter = ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
                k[0], 0f, 0f, 0f, p.shad[0] * 255f,
                k[1], 0f, 0f, 0f, p.shad[1] * 255f,
                k[2], 0f, 0f, 0f, p.shad[2] * 255f,
                0f, 0f, 0f, 1f, 0f
            )))
            val x0 = scene.left + p.cx - p.w / 2f
            val y0 = scene.top + p.cy - p.h / 2f
            withTransform({ if (p.flip) scale(-1f, 1f, Offset(scene.left + p.cx, scene.top + p.cy)) }) {
                drawImage(
                    img, srcOffset = src.first, srcSize = src.second,
                    dstOffset = IntOffset(x0.roundToInt(), y0.roundToInt()), dstSize = IntSize(p.w.roundToInt().coerceAtLeast(1), p.h.roundToInt().coerceAtLeast(1)),
                    alpha = p.alpha, colorFilter = filter, filterQuality = FilterQuality.Medium
                )
            }
        }
    }
}

/** Soleil : derrière les nuages, jamais coupé ; il apparaît entre les îlots et s'efface sous la couverture. Dessiné à chaque image (il suit l'heure choisie). */
private fun DrawScope.drawSun(scene: SkyScene, im: SkyImages, isDark: Boolean) {
    for (sun in scene.suns) {
        val a = ((1f - smoothstep(0.4f, 0.97f, scene.cover.at(sun.x)) * 0.92f) * (if (isDark) 0.85f else 0.95f)).coerceIn(0f, 1f)
        if (a <= 0.03f) continue
        drawImage(
            im.sun, srcOffset = IntOffset.Zero, srcSize = IntSize(im.sun.width, im.sun.height),
            dstOffset = IntOffset((sun.x - sun.sizePx / 2f).roundToInt(), sun.topPx.roundToInt()),
            dstSize = IntSize(sun.sizePx.roundToInt(), sun.sizePx.roundToInt()), alpha = a
        )
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
    val key = backdropKey(scene, isDark, size, skyH, im)
    var back = cache.bitmap
    var front = cache.front
    if (back == null || front == null || cache.key != key) {
        val pw = size.width.roundToInt().coerceAtLeast(1)
        val ph = size.height.roundToInt().coerceAtLeast(1)
        val bmpBack = ImageBitmap(pw, ph)
        CanvasDrawScope().draw(this, layoutDirection, androidx.compose.ui.graphics.Canvas(bmpBack), size) { drawBackdrop(scene, im, isDark, w, h, n, front = false) }
        val bmpFront = ImageBitmap(pw, ph)
        CanvasDrawScope().draw(this, layoutDirection, androidx.compose.ui.graphics.Canvas(bmpFront), size) { drawBackdrop(scene, im, isDark, w, h, n, front = true) }
        cache.bitmap = bmpBack
        cache.front = bmpFront
        cache.key = key
        back = bmpBack
        front = bmpFront
    }
    drawImage(back)
    drawSun(scene, im, isDark)
    drawImage(front)

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
