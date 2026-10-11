package com.surfcast.surfforecast

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CloudPlanTest {
    private val sprites = listOf(
        CloudSprite(0, 1157, 463, 315249f), CloudSprite(1, 640, 421, 119907f), CloudSprite(2, 306, 136, 24870f),
        CloudSprite(3, 275, 122, 17116f), CloudSprite(4, 141, 103, 6338f), CloudSprite(5, 121, 87, 4793f)
    )

    private fun plan(cover: Float, rain: Float = 0f, w: Int = 800, h: Int = 300) =
        CloudPlan.compute(sprites, w, h, { cover }, { rain }, dark = true)

    @Test
    fun effectiveCoverFollowsAnchors() {
        assertEquals(0f, CloudPlan.effective(0f), 1e-6f)
        assertEquals(0.24f, CloudPlan.effective(30f), 1e-6f)
        assertEquals(0.52f, CloudPlan.effective(55f), 1e-6f)
        assertEquals(1f, CloudPlan.effective(100f), 1e-6f)
        assertEquals(0.04f + (0.24f - 0.04f) * 0.5f, CloudPlan.effective(20f), 1e-6f)
    }

    @Test
    fun clearSkyHasNoCloud() {
        assertTrue(plan(0f).isEmpty())
    }

    @Test
    fun moreCoverMeansMoreClouds() {
        val n30 = plan(30f).count { !it.haze }
        val n60 = plan(60f).count { !it.haze }
        val n100 = plan(100f).count { !it.haze }
        assertTrue(n30 in 1 until n60, "30 % : $n30, 60 % : $n60")
        assertTrue(n60 < n100, "60 % : $n60, 100 % : $n100")
    }

    @Test
    fun planIsDeterministic() {
        val a = plan(60f, 0.3f)
        val b = plan(60f, 0.3f)
        assertEquals(a.size, b.size)
        a.forEachIndexed { i, p -> assertEquals(p.cx, b[i].cx); assertEquals(p.cy, b[i].cy); assertEquals(p.spriteId, b[i].spriteId) }
    }

    @Test
    fun expectedCoverageMatchesCoverInArea() {
        // Aire cumulée des nuages visibles (0,45 par nuage) rapportée à la vue : proche de 1 - exp(-aire / vue) ~ couverture ressentie.
        val w = 800; val h = 300
        val visibleArea = plan(60f, 0f, w, h).filter { !it.haze && it.alpha > 0.95f }.sumOf { (it.w * it.h * 0.45f).toDouble() }
        val ratio = visibleArea / (w * h)
        val expected = -kotlin.math.ln(1.0 - CloudPlan.effective(60f))
        assertTrue(abs(ratio - expected) < 0.45, "aire visible $ratio, attendue $expected")
    }

    @Test
    fun cloudsStayInDepthOrderAndSpritesKeepRatio() {
        val p = plan(100f).filter { !it.haze }
        for (i in 1 until p.size) assertTrue(p[i - 1].depth <= p[i].depth)
        for (c in p) {
            val s = sprites[c.spriteId]
            assertEquals(s.w.toFloat() / s.h, c.w / c.h, 0.01f)
        }
    }

    @Test
    fun hazeAppearsOnlyBeforeFullCoverWithHigherCover() {
        assertTrue(plan(30f).none { it.haze })
        assertTrue(plan(80f).any { it.haze })
    }

    @Test
    fun veilStartsAtHighCover() {
        assertEquals(0f, CloudPlan.veilAlpha(0.5f), 1e-6f)
        assertTrue(CloudPlan.veilAlpha(1f) > 0.9f)
    }

    @Test
    fun rainDarkensColors() {
        val dry = plan(100f, 0f).first { !it.haze }
        val wet = plan(100f, 1f).first { !it.haze }
        assertTrue(wet.lit[0] < dry.lit[0])
    }
}
