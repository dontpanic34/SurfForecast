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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.sin

/**
 * La ligne de temps de l'encart Actuellement : une case par heure le jour (dorée entre le lever et le coucher, plus vive vers midi),
 * des blocs de 3 heures hachurés la nuit. Trois repères seulement : l'heure choisie (blanc), le lever (jaune) et le coucher (orange).
 * Toucher ou glisser change l'heure choisie des trois cartes Houle, Vent et Météo.
 */
@Composable
fun TimeRuler(
    hours: List<HourlyUiModel>,
    sun: DailySunInfo?,
    selected: HourlyUiModel,
    onHourSelected: (HourlyUiModel) -> Unit,
    modifier: Modifier = Modifier
) {
    if (hours.isEmpty()) return
    val isDark = isDarkSurfaceTheme()
    val cells = remember(hours, sun) { buildTimeCells(hours, sun) }
    val selectedCell = cellIndexOf(cells, hours.indexOfFirst { it.rawTime == selected.rawTime }.coerceAtLeast(0))
    val measurer = rememberTextMeasurer()
    val cellDark = if (isDark) Color(0xFF0F1822) else Color(0xFFD3DBE4)
    val goldLo = if (isDark) Color(0xFF0B121A) else Color(0xFFC9D2DC)
    val gold = if (isDark) Color(0xFFF6C453) else Color(0xFFE2A025)
    val night = nightBlockColor(isDark)
    val nacre = if (isDark) Color(0xFFF1F4F7) else Color(0xFF2A3A4A)
    val sunYellow = if (isDark) Color(0xFFF6C453) else Color(0xFFB57A00)
    val duskOrange = if (isDark) Color(0xFFE8844A) else Color(0xFFC4561F)
    val sunrise = sun?.let { it.sunrise.hour + it.sunrise.minute / 60.0 }
    val sunset = sun?.let { it.sunset.hour + it.sunset.minute / 60.0 }

    fun pick(x: Float, width: Float) {
        if (width <= 0f) return
        val k = (x / width * cells.size).toInt().coerceIn(0, cells.size - 1)
        onHourSelected(hours[cells[k].representativeIndex()])
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp)
            .pointerInput(hours, sun) { detectHorizontalDragGestures { change, _ -> pick(change.position.x, size.width.toFloat()) } }
            .pointerInput(hours, sun) { detectTapGestures { pick(it.x, size.width.toFloat()) } }
    ) {
        val gap = 2.dp.toPx()
        val cellW = (size.width - gap * (cells.size - 1)) / cells.size
        val barH = 20.dp.toPx()
        val round = CornerRadius(3.dp.toPx())
        cells.forEachIndexed { k, cell ->
            val x0 = k * (cellW + gap)
            if (cell.isBlock) {
                drawRoundRect(night, Offset(x0, 0f), Size(cellW, barH), round)
                clipRect(x0, 0f, x0 + cellW, barH) {
                    var d = -barH
                    while (d < cellW) {
                        drawLine(Color.White.copy(alpha = 0.16f), Offset(x0 + d, barH), Offset(x0 + d + barH, 0f), 1.dp.toPx())
                        d += 4.dp.toPx()
                    }
                }
            } else {
                drawRoundRect(cellDark, Offset(x0, 0f), Size(cellW, barH), round)
                val hr = hours[cell.first].rawTime.hour.toDouble()
                if (sunrise != null && sunset != null && hr + 0.5 > sunrise && hr - 0.5 < sunset) {
                    val mid = sin(PI * ((hr - sunrise) / (sunset - sunrise)).coerceIn(0.0, 1.0)).toFloat()
                    drawRoundRect(androidx.compose.ui.graphics.lerp(goldLo, gold, 0.4f + 0.6f * mid), Offset(x0, 0f), Size(cellW, barH), round)
                }
            }
            if (k == selectedCell) drawRoundRect(nacre, Offset(x0, 0f), Size(cellW, barH), round, style = Stroke(1.5.dp.toPx()))
        }
        val cx = selectedCell * (cellW + gap) + cellW / 2f
        val nowCell = nowCellIndex(cells, hours)
        if (nowCell != null && nowCell != selectedCell) {
            val nx = nowCell * (cellW + gap) + cellW / 2f
            drawCircle(Color.White, 2.5.dp.toPx(), Offset(nx, barH / 2f))
            drawCircle(Color(0xFF0B1519), 2.5.dp.toPx(), Offset(nx, barH / 2f), style = Stroke(1.dp.toPx()))
        }
        drawCircle(Color.White, 4.5.dp.toPx(), Offset(cx, barH / 2f))
        drawCircle(Color(0xFF0B1519), 4.5.dp.toPx(), Offset(cx, barH / 2f), style = Stroke(1.6.dp.toPx()))

        // Les trois repères : l'heure choisie d'abord, puis le lever et le coucher ; un repère qui en toucherait un autre disparaît.
        class Lbl(val x: Float, val text: String, val color: Color, val fontSize: Float, val prio: Int)
        val items = ArrayList<Lbl>()
        items += Lbl(cx, selected.rawTime.formatHHmm(), nacre, 13f, 0)
        if (sunrise != null) items += Lbl(timeFraction(cells, hours, sunrise) * size.width, "${sun.sunrise.hour} h ${sun.sunrise.minute.toString().padStart(2, '0')}", sunYellow, 10.5f, 1)
        if (sunset != null) items += Lbl(timeFraction(cells, hours, sunset) * size.width, "${sun.sunset.hour} h ${sun.sunset.minute.toString().padStart(2, '0')}", duskOrange, 10.5f, 1)
        val placed = ArrayList<Pair<Float, Float>>()   // (gauche, droite)
        for (lb in items.sortedBy { it.prio }) {
            val layout = measurer.measure(lb.text, TextStyle(color = lb.color, fontSize = lb.fontSize.sp, fontWeight = FontWeight.ExtraBold))
            val w = layout.size.width.toFloat()
            val left = (lb.x - w / 2f).coerceIn(0f, size.width - w)
            if (placed.all { (l, r) -> left + w + 4.dp.toPx() <= l || r + 4.dp.toPx() <= left }) {
                placed += left to left + w
                drawText(layout, topLeft = Offset(left, barH + 3.dp.toPx()))
            }
        }
    }
}
