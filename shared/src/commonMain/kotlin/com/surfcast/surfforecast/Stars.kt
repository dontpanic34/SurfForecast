package com.surfcast.surfforecast

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Score 0-100 → étoiles par demi-étoile, de 0,5 à 5. */
fun starsForScore(score: Int): Float = ((score.coerceIn(0, 100) / 10.0).roundToInt() / 2f).coerceIn(0.5f, 5f)

private fun starPath(cx: Float, cy: Float, r: Float): Path {
    val path = Path()
    for (i in 0 until 10) {
        val radius = if (i % 2 == 0) r else r * 0.42f
        val angle = -PI / 2 + i * PI / 5
        val x = cx + (radius * cos(angle)).toFloat()
        val y = cy + (radius * sin(angle)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

private fun DrawScope.drawStarsRow(value: Float, filled: Color, empty: Color) {
    val cell = size.width / 5f
    val r = minOf(cell, size.height) / 2f * 0.95f
    for (i in 0 until 5) {
        val cx = cell * (i + 0.5f)
        val cy = size.height / 2f
        val path = starPath(cx, cy, r)
        drawPath(path, empty)
        val fill = (value - i).coerceIn(0f, 1f)
        if (fill > 0f) {
            clipRect(left = cx - cell / 2f, top = 0f, right = cx - cell / 2f + cell * fill, bottom = size.height) {
                drawPath(path, filled)
            }
        }
    }
}

/** Étoiles dessinées (identiques sur Android et iOS), avec demi-étoiles. */
@Composable
fun StarsRow(
    value: Float,
    starSize: Dp = 11.dp,
    filled: Color = Color(0xFFFFC107),
    empty: Color = Color(0x33888888),
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.width(starSize * 5).height(starSize)) {
        drawStarsRow(value, filled, empty)
    }
}
