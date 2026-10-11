package com.surfcast.surfforecast

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp

/**
 * La flèche du vent : une pointe fine, de la couleur du vent. Elle pointe là où va le vent ([rotation] en degrés, 0 = vers le
 * haut). C'est la même dans le déroulé de la journée et dans la vue semaine.
 */
@Composable
internal fun WindArrow(color: Color, rotation: Float, arrowSize: Dp) {
    Canvas(modifier = Modifier.size(arrowSize)) {
        val w = size.width
        val h = size.height
        rotate(rotation, pivot = Offset(w / 2f, h / 2f)) {
            val p = Path().apply {
                moveTo(w * 0.5f, 0f)
                lineTo(w * 0.9f, h * 0.55f)
                lineTo(w * 0.5f, h * 0.38f)
                lineTo(w * 0.1f, h * 0.55f)
                close()
            }
            drawPath(p, color = color)
        }
    }
}
