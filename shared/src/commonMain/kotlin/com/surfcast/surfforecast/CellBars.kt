package com.surfcast.surfforecast

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Hauteur commune des cheminées de la houle, du vent et de la météo (« basses »). */
val CHIMNEY_HEIGHT: Dp = 32.dp

/** Le bleu nuit des blocs de nuit, plus clair en thème clair. */
internal fun nightBlockColor(isDark: Boolean): Color = if (isDark) Color(0xFF1B2540) else Color(0xFFB9C2DC)

/**
 * Les cheminées : une barre par case de la ligne de temps (une heure le jour, un bloc de 3 heures la nuit, hachuré). Toucher ou glisser
 * choisit l'heure (l'heure du milieu pour un bloc). Un point blanc marque l'heure choisie. [valueOf] donne la valeur d'une case à partir
 * des heures qu'elle couvre (moyenne ou maximum, au choix de la carte) ; [backValueOf] ajoute une barre grise derrière (les rafales).
 */
@Composable
fun CellBars(
    hours: List<HourlyUiModel>,
    sun: DailySunInfo?,
    selected: HourlyUiModel,
    onHourSelected: (HourlyUiModel) -> Unit,
    maxValue: Double,
    valueOf: (List<HourlyUiModel>) -> Double,
    colorOf: (List<HourlyUiModel>, Boolean) -> Color,
    modifier: Modifier = Modifier,
    height: Dp = CHIMNEY_HEIGHT,
    backValueOf: ((List<HourlyUiModel>) -> Double)? = null,
    nightColor: Color = nightBlockColor(isDarkSurfaceTheme()),
    outline: Color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
) {
    if (hours.isEmpty()) return
    val cells = remember(hours, sun) { buildTimeCells(hours, sun) }
    val selectedHourIndex = hours.indexOfFirst { it.rawTime == selected.rawTime }.coerceAtLeast(0)
    val selectedCell = cellIndexOf(cells, selectedHourIndex)

    fun pick(x: Float, width: Float) {
        if (width <= 0f) return
        val k = (x / width * cells.size).toInt().coerceIn(0, cells.size - 1)
        onHourSelected(hours[cells[k].representativeIndex()])
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(hours, sun) { detectHorizontalDragGestures { change, _ -> pick(change.position.x, size.width.toFloat()) } }
            .pointerInput(hours, sun) { detectTapGestures { pick(it.x, size.width.toFloat()) } }
    ) {
        val gap = 2.dp.toPx()
        val cellW = (size.width - gap * (cells.size - 1)) / cells.size
        val round = CornerRadius(2.dp.toPx())
        var selectedTop = size.height
        cells.forEachIndexed { k, cell ->
            val group = hours.subList(cell.first, cell.last + 1)
            val x0 = k * (cellW + gap)
            val isSel = k == selectedCell
            val h = (valueOf(group) / maxValue).toFloat().coerceIn(0.1f, 1f) * size.height
            val top = size.height - h
            if (backValueOf != null && !cell.isBlock) {
                val back = (backValueOf(group) / maxValue).toFloat().coerceIn(0f, 1f) * size.height
                if (back > h) drawRoundRect(outline.copy(alpha = if (isSel) 0.32f else 0.2f), Offset(x0, size.height - back), Size(cellW, back), round)
            }
            if (cell.isBlock) {
                drawRoundRect(nightColor, Offset(x0, top), Size(cellW, h), round)
                // Hachures : un bloc de nuit regroupe plusieurs heures.
                clipRect(x0, top, x0 + cellW, size.height) {
                    var d = -h
                    while (d < cellW) {
                        drawLine(Color.White.copy(alpha = 0.16f), Offset(x0 + d, size.height), Offset(x0 + d + h, top), 1.dp.toPx())
                        d += 4.dp.toPx()
                    }
                }
            } else {
                drawRoundRect(colorOf(group, isSel), Offset(x0, top), Size(cellW, h), round)
            }
            if (isSel) {
                drawRoundRect(outline.copy(alpha = 0.9f), Offset(x0, top), Size(cellW, h), round, style = Stroke(1.3.dp.toPx()))
                selectedTop = top
            }
        }
        // Le point blanc, au sommet de la barre choisie.
        val cx = selectedCell * (cellW + gap) + cellW / 2f
        drawCircle(Color.White, 4.5.dp.toPx(), Offset(cx, selectedTop.coerceAtLeast(4.5.dp.toPx())))
        drawCircle(Color(0xFF0B1519), 4.5.dp.toPx(), Offset(cx, selectedTop.coerceAtLeast(4.5.dp.toPx())), style = Stroke(1.6.dp.toPx()))
    }
}
