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

/** Ce qu'il faut dessiner : la zone du ciel (en pixels du canevas) et les trois courbes météo le long de l'axe. */
class SkyScene(
    val left: Float,
    val right: Float,
    val top: Float,
    val bottom: Float,
    val cover: SkyCurve,
    val rain: SkyCurve,
    val snow: SkyCurve,
    val suns: List<SunSpot>,
    val animate: Boolean
)

/** Les nuages restent des suggestions : assez transparents pour laisser lire la courbe et les chiffres. */
private val CLOUD_LAYER_OPACITY = floatArrayOf(0.5f, 0.65f, 0.85f, 1f)

internal class SkyImages(
    val clouds: List<ImageBitmap>, val sun: ImageBitmap, val rain: ImageBitmap, val snow: ImageBitmap, val water: ImageBitmap
)

/** Les images du ciel, chargées dès le lancement de l'appli (voir SurfLogApp) pour être prêtes quand la semaine s'affiche. */
internal val LocalSkyImages = androidx.compose.runtime.compositionLocalOf<SkyImages?> { null }

@Composable
internal fun rememberSkyImages(): SkyImages = SkyImages(
    clouds = listOf(
        imageResource(Res.drawable.sky_clouds_1), imageResource(Res.drawable.sky_clouds_2),
        imageResource(Res.drawable.sky_clouds_3), imageResource(Res.drawable.sky_clouds_4)
    ),
    sun = imageResource(Res.drawable.sky_sun),
    rain = imageResource(Res.drawable.sky_rain),
    snow = imageResource(Res.drawable.sky_snow),
    water = imageResource(Res.drawable.sky_water)
)

/** Poids des quatre niveaux de nuages pour une couverture de 0 à 1 : deux niveaux voisins se fondent l'un dans l'autre. */
private fun cloudWeights(c: Float): FloatArray {
    val w = FloatArray(4)
    val p = floatArrayOf(0.25f, 0.5f, 0.75f, 1f)
    if (c <= p[0]) { w[0] = c / p[0]; return w }
    for (i in 1 until 4) {
        if (c <= p[i]) {
            val t = (c - p[i - 1]) / (p[i] - p[i - 1])
            w[i - 1] = 1f - t
            w[i] = t
            return w
        }
    }
    w[3] = 1f
    return w
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

    clipRect(scene.left, scene.top, scene.right, scene.bottom) {
        // Nuages : quatre calques, chacun plus ou moins opaque le long de l'axe selon la couverture.
        // Plus le ciel se couvre, plus les nuages sont sombres (en thème clair, on grise les images) et présents.
        val cloudTint = listOf(
            Color.Transparent, Color(0xFF8FA0B3).copy(alpha = 0.45f),
            Color(0xFF66778C).copy(alpha = 0.60f), Color(0xFF4A586B).copy(alpha = 0.70f)
        )
        for (l in 0 until 4) {
            val stops = fadeStops(scene, n) { x -> cloudWeights(scene.cover.at(x))[l] * CLOUD_LAYER_OPACITY[l] }
            if (stops.all { it.second.alpha < 0.02f }) continue
            drawIntoCanvas { canvas ->
                canvas.saveLayer(bounds, Paint())
                val img = im.clouds[l]
                drawImage(
                    img, srcOffset = IntOffset.Zero, srcSize = IntSize(img.width, img.height),
                    dstOffset = IntOffset(scene.left.roundToInt(), (scene.top - h * 0.05f).roundToInt()),
                    dstSize = IntSize(w.roundToInt(), (h * 1.25f).roundToInt()),
                    colorFilter = if (cloudTint[l] != Color.Transparent && !isDark) ColorFilter.tint(cloudTint[l], BlendMode.SrcAtop) else null
                )
                drawRect(
                    brush = Brush.horizontalGradient(*stops, startX = scene.left, endX = scene.right),
                    topLeft = Offset(scene.left, scene.top), size = androidx.compose.ui.geometry.Size(w, h), blendMode = BlendMode.DstIn
                )
                canvas.restore()
            }
        }
        // Pluie et neige : deux couches chacune, qui tombent à des vitesses différentes.
        val d = density
        class Layer(val img: ImageBitmap, val scale: Float, val ax: Float, val speed: Float, val xoff: Float, val mask: (Float) -> Float, val alpha: Float)
        val layers = listOf(
            Layer(im.rain, 0.36f, 2.1f, 170f, 0f, { x -> min(1f, scene.rain.at(x) / 2.2f * 0.85f) }, 0.95f),
            Layer(im.rain, 0.28f, 2.4f, 120f, 57f, { x -> min(1f, scene.rain.at(x) / 2.2f * 0.85f) }, 0.95f),
            Layer(im.snow, 0.50f, 1.0f, 45f, 0f, { x -> min(1f, scene.snow.at(x) / 1.5f * 0.6f) }, 0.95f),
            Layer(im.snow, 0.80f, 1.0f, 28f, 91f, { x -> min(1f, scene.snow.at(x) / 1.5f * 0.6f) }, 0.95f)
        )
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
                            dstSize = IntSize(iw.roundToInt() + 1, ih.roundToInt() + 1), alpha = ly.alpha
                        )
                        yy += ih
                    }
                    xx += iw
                }
                drawRect(
                    brush = Brush.horizontalGradient(*stops, startX = scene.left, endX = scene.right),
                    topLeft = Offset(scene.left, scene.top), size = androidx.compose.ui.geometry.Size(w, h), blendMode = BlendMode.DstIn
                )
                canvas.restore()
            }
        }
    }

    // Soleil : au premier plan, devant les nuages, jamais coupé (donc hors du rognage du ciel) ; il s'efface sous la couverture.
    for (s in scene.suns) {
        val a = (max(0f, 1f - scene.cover.at(s.x) * 1.1f) * (if (isDark) 0.8f else 0.9f)).coerceIn(0f, 1f)
        if (a <= 0.03f) continue
        drawImage(
            im.sun, srcOffset = IntOffset.Zero, srcSize = IntSize(im.sun.width, im.sun.height),
            dstOffset = IntOffset((s.x - s.sizePx / 2f).roundToInt(), s.topPx.roundToInt()),
            dstSize = IntSize(s.sizePx.roundToInt(), s.sizePx.roundToInt()), alpha = a
        )
    }
}

/**
 * L'océan : l'image d'eau remplit, opaque, tout ce qui est sous la courbe de houle (rien ne transparaît derrière). Le haut de
 * l'image se cale sur le point le plus haut de la courbe, le bas sur [bottom].
 */
internal fun DrawScope.drawWater(water: ImageBitmap, under: androidx.compose.ui.graphics.Path, left: Float, right: Float, topY: Float, bottom: Float) {
    if (right - left < 2f || bottom - topY < 2f) return
    clipPath(under) {
        drawImage(
            water, srcOffset = IntOffset.Zero, srcSize = IntSize(water.width, water.height),
            dstOffset = IntOffset(left.roundToInt(), topY.roundToInt()),
            dstSize = IntSize((right - left).roundToInt(), (bottom - topY).roundToInt())
        )
    }
}
