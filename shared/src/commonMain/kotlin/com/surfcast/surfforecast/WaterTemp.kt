package com.surfcast.surfforecast

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Bleu de la température de l'eau (clair en thème sombre, plus profond en thème clair). */
@Composable
fun waterTempColor(): Color = if (isDarkSurfaceTheme()) Color(0xFF4FC3F7) else Color(0xFF0277BD)

/** Trois lignes ondulées : l'icône de la mer / de l'eau, dessinée dans un carré de côté [sizePx] à partir de [topLeft]. */
fun DrawScope.drawWaveIcon(topLeft: Offset, sizePx: Float, color: Color) {
    val stroke = Stroke(width = sizePx * 0.11f, cap = StrokeCap.Round)
    for (row in 0..2) {
        val y = topLeft.y + sizePx * (0.2f + 0.3f * row)
        val amp = sizePx * 0.14f
        val path = Path().apply {
            moveTo(topLeft.x, y)
            val seg = sizePx / 4f
            for (k in 0 until 4) {
                val x0 = topLeft.x + seg * k
                quadraticBezierTo(x0 + seg / 2f, y + if (k % 2 == 0) -amp else amp, x0 + seg, y)
            }
        }
        drawPath(path = path, color = color, style = stroke)
    }
}

@Composable
fun WaveIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) { drawWaveIcon(Offset.Zero, size.minDimension, color) }
}

/** Équipement courant pour cette température de l'eau (repères du Stormrider, indicatifs). */
fun wetsuitLabel(celsius: Int): String = when {
    celsius >= 25 -> "Short"
    celsius >= 20 -> "Shorty"
    celsius >= 15 -> "3/2"
    celsius >= 10 -> "4/3"
    else -> "5/4 + cagoule"
}

/** Température de l'eau : l'icône vague puis le chiffre, en bleu ; [showSuit] ajoute l'équipement (« 17° · 3/2 »). */
@Composable
fun WaterTempChip(celsius: Int, modifier: Modifier = Modifier, showSuit: Boolean = false) {
    val color = waterTempColor()
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        WaveIcon(color = color, modifier = Modifier.size(13.dp))
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = if (showSuit) "$celsius° · ${wetsuitLabel(celsius)}" else "$celsius°",
            fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1
        )
    }
}
