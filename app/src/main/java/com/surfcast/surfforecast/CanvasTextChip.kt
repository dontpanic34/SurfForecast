package com.surfcast.surfforecast

import android.graphics.Paint
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp

/**
 * Dessine un texte (via un android.graphics.Paint deja configure : couleur, taille,
 * police) avec un petit fond sombre arrondi derriere lui, pour qu'il reste lisible sur
 * n'importe quel fond -- theme clair ET sombre -- sans avoir a assombrir sa propre
 * couleur. A utiliser pour tout texte dont la teinte fait partie d'un code couleur
 * impose (vent jaune/orange/rouge, houle/eau bleu...) qu'on ne veut jamais alterer :
 * le Paint doit donc garder sa teinte vive d'origine, jamais une variante foncee pensee
 * seulement pour fond clair (elle se fondrait dans le fond sombre du chip lui-meme).
 */
fun DrawScope.drawTextWithChip(
    text: String,
    x: Float,
    baselineY: Float,
    paint: Paint,
    chipAlpha: Float = 0.34f
) {
    if (text.isEmpty()) return
    val textWidth = paint.measureText(text)
    val padH = 2.5.dp.toPx()
    val cornerPx = 2.dp.toPx()
    val above = paint.textSize * 0.78f
    val below = paint.textSize * 0.24f
    drawRoundRect(
        color = Color.Black.copy(alpha = chipAlpha),
        topLeft = Offset(x - padH, baselineY - above),
        size = Size(textWidth + padH * 2f, above + below),
        cornerRadius = CornerRadius(cornerPx, cornerPx)
    )
    drawContext.canvas.nativeCanvas.drawText(text, x, baselineY, paint)
}
