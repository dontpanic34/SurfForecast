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
 * La flèche unique de l'appli (modèle « pointe de navigation ») : vent, houle, clapot. Elle pointe là où ça va ([rotation] en
 * degrés, 0 = vers le haut) ; sa taille [arrowSize] dit la force du vent ou l'orientation de la houle (voir [windArrowScale] et
 * [swellArrowScale]).
 */
@Composable
internal fun NavArrow(color: Color, rotation: Float, arrowSize: Dp) {
    Canvas(modifier = Modifier.size(arrowSize)) {
        val w = size.width
        val h = size.height
        rotate(rotation, pivot = Offset(w / 2f, h / 2f)) {
            val p = Path().apply {
                moveTo(w * 0.5f, h * 0.05f)
                lineTo(w * 0.85f, h * 0.95f)
                lineTo(w * 0.5f, h * 0.72f)
                lineTo(w * 0.15f, h * 0.95f)
                close()
            }
            drawPath(p, color = color)
        }
    }
}

/** Compatibilité : la flèche du vent est la flèche unique. */
@Composable
internal fun WindArrow(color: Color, rotation: Float, arrowSize: Dp) = NavArrow(color, rotation, arrowSize)

/** Taille de la flèche du vent selon sa force (km/h ressentis) : petite sous 10, moyenne de 10 à 20, grande au-dessus. */
internal fun windArrowScale(kmh: Int): Float = when {
    kmh < 10 -> 0.8f
    kmh <= 20 -> 1f
    else -> 1.25f
}

/**
 * Taille de la flèche d'une houle selon son orientation : écart avec l'orientation de la plage de 25° au plus = grande,
 * jusqu'à 50° = moyenne, au-delà = petite. Sans orientation connue, moyenne.
 */
internal fun swellArrowScale(swellDeg: Float, beachFacing: Int?): Float {
    if (beachFacing == null) return 1f
    val gap = angularDifference(swellDeg.toDouble(), beachFacing.toDouble())
    return when {
        gap <= 25.0 -> 1.25f
        gap <= 50.0 -> 1f
        else -> 0.8f
    }
}
