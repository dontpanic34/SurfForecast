package com.surfcast.surfforecast

import android.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp

/**
 * Dessine un texte (via un android.graphics.Paint deja configure : couleur, taille,
 * police) avec une ombre portee, pour qu'il reste lisible sur n'importe quel fond --
 * theme clair ET sombre -- sans avoir a assombrir sa propre couleur ni a poser un pave
 * de fond derriere (rendu trop lourd visuellement, "sale"). A utiliser pour tout texte
 * dont la teinte fait partie d'un code couleur impose (vent jaune/orange/rouge,
 * houle/eau bleu...) qu'on ne veut jamais alterer : le Paint doit donc garder sa teinte
 * vive d'origine, jamais une variante foncee pensee seulement pour fond clair.
 */
fun DrawScope.drawTextWithShadow(
    text: String,
    x: Float,
    baselineY: Float,
    paint: Paint
) {
    if (text.isEmpty()) return
    val radiusPx = 2.2.dp.toPx()
    val dxPx = 0f
    val dyPx = 0.6.dp.toPx()
    paint.setShadowLayer(radiusPx, dxPx, dyPx, 0xDD000000.toInt())
    drawContext.canvas.nativeCanvas.drawText(text, x, baselineY, paint)
    paint.clearShadowLayer()
}
