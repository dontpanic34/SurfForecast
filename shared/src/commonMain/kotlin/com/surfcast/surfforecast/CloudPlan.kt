package com.surfcast.surfforecast

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

// Plan de placement des nuages (sprites détourés posés un à un selon la couverture). Fonction pure, sans dessin : on la teste en JVM.
// Algorithme repris du prototype Python (sky3.py) ; le générateur aléatoire est le nôtre (graine fixe par vue), donc les positions
// exactes diffèrent du prototype, mais le principe et les statistiques (recouvrement attendu == couverture) sont les mêmes.

/** Taille native d'un sprite de l'atlas. */
class CloudSprite(val id: Int, val w: Int, val h: Int, val area: Float)

/** Un nuage (ou une brume) à dessiner : centre, taille d'affichage, retournement, opacité, couleurs ombre / lumière. */
class CloudPlacement(
    val spriteId: Int, val cx: Float, val cy: Float, val w: Float, val h: Float, val flip: Boolean, val alpha: Float,
    val depth: Float, val haze: Boolean, val lit: FloatArray, val shad: FloatArray, val litScale: Float
)

internal class CloudTheme(
    val lit: FloatArray, val shad: FloatArray, val glit: FloatArray, val gshad: FloatArray, val rlit: FloatArray, val rshad: FloatArray
)

internal val CLOUD_THEME_DARK = CloudTheme(
    floatArrayOf(.88f, .92f, .98f), floatArrayOf(.40f, .47f, .60f), floatArrayOf(.50f, .56f, .66f),
    floatArrayOf(.29f, .34f, .43f), floatArrayOf(.34f, .38f, .46f), floatArrayOf(.13f, .16f, .22f)
)
internal val CLOUD_THEME_LIGHT = CloudTheme(
    floatArrayOf(1f, 1f, 1f), floatArrayOf(.62f, .69f, .80f), floatArrayOf(.84f, .87f, .92f),
    floatArrayOf(.58f, .63f, .72f), floatArrayOf(.62f, .66f, .72f), floatArrayOf(.34f, .38f, .46f)
)

object CloudPlan {
    private val ANCH_C = floatArrayOf(0f, 10f, 30f, 55f, 80f, 100f)
    private val ANCH_E = floatArrayOf(0f, 0.04f, 0.24f, 0.52f, 0.93f, 1f)

    /** Couverture ressentie (0 à 1) pour une couverture de 0 à 100 % : quatre états nets (dégagé, quelques, partiel, couvert). */
    fun effective(coverPct: Float): Float {
        val c = coverPct.coerceIn(0f, 100f)
        for (i in 1 until ANCH_C.size) {
            if (c <= ANCH_C[i]) {
                val t = (c - ANCH_C[i - 1]) / (ANCH_C[i] - ANCH_C[i - 1])
                return ANCH_E[i - 1] + (ANCH_E[i] - ANCH_E[i - 1]) * t
            }
        }
        return 1f
    }

    fun smooth(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /** Opacité (0 à 1) du voile de couvert à plat, pour une couverture ressentie [e]. */
    fun veilAlpha(e: Float): Float = smooth(0.80f, 1f, e) * 0.92f

    private class Rng(seed: Int) {
        private var s = (seed.toLong() * 6364136223846793005L + 1442695040888963407L)
        fun next(): Double {
            s = s * 6364136223846793005L + 1442695040888963407L
            return ((s ushr 11).toDouble()) / (1L shl 53).toDouble()
        }
        fun uniform(a: Double, b: Double) = a + (b - a) * next()
        fun beta(a: Double, b: Double): Double {
            while (true) {
                val x = next().pow(1.0 / a)
                val y = next().pow(1.0 / b)
                if (x + y <= 1.0 && x + y > 0.0) return x / (x + y)
            }
        }
    }

    internal class Candidate(
        val sprite: CloudSprite, val u: Float, val cy: Float, val wp: Float, val hp: Float, val flip: Boolean,
        val depth: Float, var p: Float = 0f
    )

    /** Candidats déterministes : leur nombre dépend de l'aire de la vue, leur seuil p fait que le recouvrement attendu égale la couverture. */
    internal fun candidates(sprites: List<CloudSprite>, w: Int, h: Int, seed: Int, canvasMult: Float): List<Candidate> {
        val big = sprites.filter { it.area > 60000f }
        val med = sprites.filter { it.area > 12000f && it.area <= 60000f }
        val small = sprites.filter { it.area <= 12000f }
        // Classe vide : on se rabat sur toutes les autres (jeu de sprites réduit).
        val classes = listOf(
            Triple(big.ifEmpty { sprites }, 0.60 to 1.05, 0.10),
            Triple(med.ifEmpty { sprites }, 0.32 to 0.55, 0.30),
            Triple(small.ifEmpty { sprites }, 0.13 to 0.28, 0.60)
        )
        if (sprites.isEmpty()) return emptyList()
        val rng = Rng(seed)
        val canvas = w.toDouble() * h
        val out = ArrayList<Candidate>()
        var total = 0.0
        while (total < canvas * canvasMult) {
            val r = rng.next()
            var acc = 0.0
            var pick = classes[0]
            for (c in classes) { acc += c.third; pick = c; if (r <= acc) break }
            val s = pick.first[(rng.next() * pick.first.size).toInt().coerceAtMost(pick.first.size - 1)]
            val hp = h * rng.uniform(pick.second.first, pick.second.second)
            val wp = hp * s.w / s.h
            val u = rng.uniform(-0.04, 1.04)
            val v = rng.beta(1.25, 1.6)
            out.add(Candidate(s, u.toFloat(), (v * h * 0.95).toFloat(), wp.toFloat(), hp.toFloat(), rng.next() < 0.5, rng.next().toFloat()))
            total += wp * hp * 0.45
        }
        // Mélange déterministe (Fisher-Yates), puis seuil p.
        for (i in out.size - 1 downTo 1) {
            val j = (rng.next() * (i + 1)).toInt().coerceAtMost(i)
            val t = out[i]; out[i] = out[j]; out[j] = t
        }
        var cum = 0.0
        for (c in out) {
            cum += c.wp.toDouble() * c.hp * 0.45
            c.p = (1.0 - exp(-cum / canvas)).toFloat()
        }
        return out.filter { it.p < 0.999f }
    }

    /**
     * Les nuages à dessiner. [coverAt] et [rainAt] : couverture (0 à 100) et pluie (0 à 1) en fonction de la position u de 0 à 1 le
     * long de l'axe. Rend d'abord la brume (derrière), puis les nuages triés du plus loin au plus proche.
     */
    fun compute(
        sprites: List<CloudSprite>, wPx: Int, hPx: Int, coverAt: (Float) -> Float, rainAt: (Float) -> Float, dark: Boolean, seed: Int = 11
    ): List<CloudPlacement> {
        if (wPx < 2 || hPx < 2 || sprites.isEmpty()) return emptyList()
        val th = if (dark) CLOUD_THEME_DARK else CLOUD_THEME_LIGHT
        fun e(u: Float) = effective(coverAt(u.coerceIn(0f, 1f)))
        fun r(u: Float) = rainAt(u.coerceIn(0f, 1f)).coerceIn(0f, 1f)
        val out = ArrayList<CloudPlacement>()

        // Brume : sprites flous agrandis, gris, qui relient les nuages dès 55 % environ.
        for (c in candidates(sprites, wPx, hPx, seed + 5, 2.2f)) {
            val ec = e(c.u)
            val vis = smooth(c.p * 0.55f - 0.05f, c.p * 0.55f + 0.10f, ec) * smooth(0.35f, 0.8f, ec)
            if (vis < 0.01f) continue
            val ru = r(c.u)
            val col = FloatArray(3) { i -> (th.glit[i] * 0.5f + th.gshad[i] * 0.5f) * (1f - 0.5f * ru) + th.rshad[i] * 0.5f * ru }
            out.add(CloudPlacement(c.sprite.id, c.u * wPx, c.cy, c.wp * 1.8f, c.hp * 1.5f, c.flip, vis * 0.60f, c.depth, true, col, col, 0f))
        }

        // Nuages : visibles quand la couverture locale dépasse leur seuil, du plus loin au plus proche.
        val clouds = ArrayList<CloudPlacement>()
        for (c in candidates(sprites, wPx, hPx, seed, 3.4f)) {
            val ec = e(c.u)
            val ru = r(c.u)
            val vis = smooth(c.p - 0.05f, c.p + 0.05f, ec)
            if (vis < 0.01f) continue
            val k = 0.82f + 0.18f * vis
            val g = (0.8f * ec.pow(1.4f) + 0.75f * ru).coerceIn(0f, 1f)
            val m = if (ru > 0.25f) (ru / 0.9f).coerceIn(0f, 1f) else 0f
            val lit = FloatArray(3) { i -> th.lit[i] * (1f - g) + (th.glit[i] * (1f - m) + th.rlit[i] * m) * g }
            val shad = FloatArray(3) { i -> th.shad[i] * (1f - g) + (th.gshad[i] * (1f - m) + th.rshad[i] * m) * g }
            val litScale = 1f - 0.35f * smooth(0.7f, 1f, ec)
            clouds.add(CloudPlacement(c.sprite.id, c.u * wPx, c.cy, c.wp * k, c.hp * k, c.flip, vis, c.depth, false, lit, shad, litScale))
        }
        clouds.sortBy { it.depth }
        out.addAll(clouds)
        return out
    }
}
