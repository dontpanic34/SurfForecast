package com.surfcast.surfforecast

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class SkyCurveTest {
    @Test
    fun aJumpBecomesASmoothRamp() {
        // La couverture saute de 0 à 1 entre deux heures : le lissage doit l'étaler, sans marche brusque.
        val curve = SkyCurve(floatArrayOf(0f, 100f, 200f, 300f), floatArrayOf(0f, 0f, 1f, 1f), 40f)
        assertTrue(abs(curve.at(150f) - 0.5f) < 0.02f, "au milieu de la rampe : ${curve.at(150f)}")
        assertTrue(curve.at(60f) < curve.at(150f) && curve.at(150f) < curve.at(240f))
        var x = 0f
        while (x < 300f) {
            assertTrue(abs(curve.at(x + 4f) - curve.at(x)) < 0.05f, "marche brusque en $x")
            x += 4f
        }
    }

    @Test
    fun aConstantValueStaysConstant() {
        val curve = SkyCurve(floatArrayOf(0f, 100f, 200f), floatArrayOf(0.7f, 0.7f, 0.7f), 30f)
        assertTrue(abs(curve.at(100f) - 0.7f) < 1e-4f)
    }
}
