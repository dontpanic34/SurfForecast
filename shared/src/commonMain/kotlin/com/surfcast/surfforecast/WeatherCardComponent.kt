package com.surfcast.surfforecast

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import kotlin.math.roundToInt
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun WeatherCardComponent(
    selectedHour: HourlyUiModel,
    allHoursOfDay: List<HourlyUiModel>,
    onHourSelected: (HourlyUiModel) -> Unit,
    isCollapsed: Boolean = false,
    onToggleCollapse: () -> Unit = {},
    dragHandleModifier: Modifier = Modifier,
    modifier: Modifier = Modifier,
    sun: DailySunInfo? = null,
    onOpenDetail: (() -> Unit)? = null
) {
    if (allHoursOfDay.isEmpty()) return

    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = surfaceColor),
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            CardControlsRow(
                title = "Météo", guide = "weather",
                isCollapsed = isCollapsed,
                onToggleCollapse = onToggleCollapse,
                dragHandleModifier = dragHandleModifier,
                summary = "${selectedHour.feelsLike}° ressentis · nuages ${selectedHour.cloudCover} %" +
                    (if (selectedHour.precipitationMm >= 0.05) " · pluie ${formatDecimal(selectedHour.precipitationMm, 1).replace('.', ',')} mm" else ""),
                trailing = if (onOpenDetail != null) ({
                    Text(
                        "Semaine ›", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onOpenDetail).padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }) else null
            )
            if (!isCollapsed) {
            Spacer(modifier = Modifier.height(2.dp))
            WeatherHeader(selectedHour, sun, onSurfaceColor)
            Spacer(modifier = Modifier.height(8.dp))
            WeatherBands(selectedHour, allHoursOfDay, sun, onHourSelected, onSurfaceColor, isDarkSurfaceTheme())
            }
        }
    }
}

private const val BAND_LABEL_W = 62
private const val BAND_VALUE_W = 52
private const val CIEL_H = 14
private const val SOLEIL_H = 22
private const val BAND_GAP = 7
private const val HOURS_H = 18

/** « 8 h 17 » : un lever ou un coucher écrit sur la ligne d'heures. */
private fun sunHm(t: kotlinx.datetime.LocalTime): String = "${t.hour} h ${t.minute.toString().padStart(2, '0')}"

/**
 * Les bandes de l'encart : une cellule par heure, comme la barre d'ensoleillement du déroulé de la journée.
 * « Ciel » : bleue quand il pleut cette heure-là (plus foncée si c'est plus fort), sinon la couverture nuageuse en gris.
 * « Soleil » : dorée entre le lever et le coucher (plus vive vers midi), sombre la nuit, avec l'indice UV à droite.
 * Un point sur chaque bande marque l'heure choisie, écrite sous les bandes avec les heures rondes, le lever (jaune) et le coucher (orange).
 * Toucher ou glisser sur une bande change l'heure choisie (comme dans la houle et le vent).
 */
@Composable
private fun WeatherBands(
    selected: HourlyUiModel, hours: List<HourlyUiModel>, sun: DailySunInfo?, onHourSelected: (HourlyUiModel) -> Unit,
    onSurface: Color, isDark: Boolean
) {
    val textMeasurer = rememberTextMeasurer()
    val n = hours.size
    val selIndex = hours.indexOfFirst { it.rawTime == selected.rawTime }
    val firstHour = hours.first().rawTime.hour
    val sunrise = sun?.let { it.sunrise.hour + it.sunrise.minute / 60f }
    val sunset = sun?.let { it.sunset.hour + it.sunset.minute / 60f }
    val cellDark = if (isDark) Color(0xFF0F1822) else Color(0xFFD3DBE4)
    val goldLo = if (isDark) Color(0xFF0B121A) else Color(0xFFC9D2DC)
    val gold = if (isDark) Color(0xFFF6C453) else Color(0xFFE2A025)
    val rainBlue = Color(0xFF5AA9E6)
    val cloudGrey = if (isDark) Color(0xFF9FB2BD) else Color(0xFF6F8794)
    val nacre = if (isDark) Color(0xFFF1F4F7) else Color(0xFF2A3A4A)
    val sunYellow = if (isDark) Color(0xFFF6C453) else Color(0xFFB57A00)
    val duskOrange = if (isDark) Color(0xFFE8844A) else Color(0xFFC4561F)
    val muted = onSurface.copy(alpha = 0.6f)
    val raining = selected.precipitationMm >= 0.05
    val labelStyle = TextStyle(color = onSurface.copy(alpha = 0.6f), fontSize = 12.sp)

    fun indexAt(x: Float, width: Float): Int = (x / width * n).toInt().coerceIn(0, n - 1)

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        // Colonne des noms.
        Column(modifier = Modifier.width(BAND_LABEL_W.dp), verticalArrangement = Arrangement.spacedBy(BAND_GAP.dp)) {
            Box(Modifier.height(CIEL_H.dp), contentAlignment = Alignment.CenterStart) { Text("Ciel", style = labelStyle) }
            Box(Modifier.height(SOLEIL_H.dp), contentAlignment = Alignment.CenterStart) { Text("Soleil", style = labelStyle) }
        }
        // Les bandes et la ligne d'heures.
        Box(
            modifier = Modifier.weight(1f)
                .pointerInput(hours) {
                    detectHorizontalDragGestures { change, _ -> onHourSelected(hours[indexAt(change.position.x, size.width.toFloat())]) }
                }
                .pointerInput(hours) {
                    detectTapGestures { o -> onHourSelected(hours[indexAt(o.x, size.width.toFloat())]) }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height((CIEL_H + BAND_GAP + SOLEIL_H + BAND_GAP + HOURS_H).dp)) {
                val gap = 2.dp.toPx()
                val cellW = (size.width - gap * (n - 1)) / n
                val r = CornerRadius(3.dp.toPx())
                fun x0(i: Int) = i * (cellW + gap)
                val cielTop = 0f
                val cielH = CIEL_H.dp.toPx()
                val solTop = cielH + BAND_GAP.dp.toPx()
                val solH = SOLEIL_H.dp.toPx()
                for (i in 0 until n) {
                    val h = hours[i]
                    // Ciel
                    drawRoundRect(cellDark, Offset(x0(i), cielTop), Size(cellW, cielH), r)
                    if (h.precipitationMm >= 0.05) {
                        val a = (0.45f + (h.precipitationMm.toFloat() / 1f).coerceIn(0f, 1f) * 0.55f)
                        drawRoundRect(rainBlue.copy(alpha = a), Offset(x0(i), cielTop), Size(cellW, cielH), r)
                    } else {
                        drawRoundRect(cloudGrey.copy(alpha = (h.cloudCover / 100f * 0.85f).coerceIn(0f, 0.85f)), Offset(x0(i), cielTop), Size(cellW, cielH), r)
                    }
                    // Soleil
                    drawRoundRect(cellDark, Offset(x0(i), solTop), Size(cellW, solH), r)
                    if (sunrise != null && sunset != null) {
                        val hr = h.rawTime.hour.toFloat()
                        if (hr + 0.5f > sunrise && hr - 0.5f < sunset) {
                            val mid = kotlin.math.sin(kotlin.math.PI * ((hr - sunrise) / (sunset - sunrise)).coerceIn(0f, 1f).toDouble()).toFloat()
                            drawRoundRect(androidx.compose.ui.graphics.lerp(goldLo, gold, 0.4f + 0.6f * mid), Offset(x0(i), solTop), Size(cellW, solH), r)
                        }
                    }
                    if (i == selIndex) {
                        drawRoundRect(nacre, Offset(x0(i), cielTop), Size(cellW, cielH), r, style = Stroke(1.4.dp.toPx()))
                        drawRoundRect(nacre, Offset(x0(i), solTop), Size(cellW, solH), r, style = Stroke(1.4.dp.toPx()))
                    }
                }
                // Un point sur chaque bande, à l'heure choisie.
                if (selIndex >= 0) {
                    val cx = x0(selIndex) + cellW / 2f
                    for (cy in listOf(cielTop + cielH / 2f, solTop + solH / 2f)) {
                        drawCircle(Color.White, 5.5.dp.toPx(), Offset(cx, cy))
                        drawCircle(Color(0xFF0B1519), 5.5.dp.toPx(), Offset(cx, cy), style = Stroke(2.dp.toPx()))
                    }
                }
                // La ligne d'heures : l'heure choisie d'abord, puis le lever et le coucher, puis les heures rondes ; une étiquette qui en toucherait une autre disparaît.
                class Lbl(val cx: Float, val text: String, val color: Color, val size: Float, val bold: Boolean, val prio: Int)
                fun xOfTime(t: Float) = (t - firstHour + 0.5f) / n * size.width
                val items = ArrayList<Lbl>()
                if (selIndex >= 0) items.add(Lbl(x0(selIndex) + cellW / 2f, selected.rawTime.formatHHmm(), nacre, 12.5f, true, 0))
                if (sunrise != null) items.add(Lbl(xOfTime(sunrise), sunHm(sun!!.sunrise), sunYellow, 10.5f, true, 1))
                if (sunset != null) items.add(Lbl(xOfTime(sunset), sunHm(sun!!.sunset), duskOrange, 10.5f, true, 1))
                for (i in 0 until n) { val hr = hours[i].rawTime.hour; if (hr % 3 == 0) items.add(Lbl(x0(i) + cellW / 2f, "$hr h", muted, 10.5f, false, 2)) }
                val laid = ArrayList<Pair<Lbl, androidx.compose.ui.text.TextLayoutResult>>()
                val top = solTop + solH + BAND_GAP.dp.toPx() * 0.6f
                for (lb in items.sortedBy { it.prio }) {
                    val layout = textMeasurer.measure(lb.text, TextStyle(color = lb.color, fontSize = lb.size.sp, fontWeight = if (lb.bold) FontWeight.ExtraBold else FontWeight.SemiBold))
                    val w = layout.size.width.toFloat()
                    val left = (lb.cx - w / 2f).coerceIn(0f, size.width - w)
                    val ok = laid.all { (k, kl) ->
                        val kLeft = (k.cx - kl.size.width / 2f).coerceIn(0f, size.width - kl.size.width)
                        left + w + 3.dp.toPx() <= kLeft || kLeft + kl.size.width + 3.dp.toPx() <= left
                    }
                    if (ok) { laid.add(lb to layout); drawText(layout, topLeft = Offset(left, top)) }
                }
            }
        }
        // Colonne des valeurs de l'heure choisie.
        Column(modifier = Modifier.width(BAND_VALUE_W.dp), verticalArrangement = Arrangement.spacedBy(BAND_GAP.dp), horizontalAlignment = Alignment.End) {
            Box(Modifier.height(CIEL_H.dp), contentAlignment = Alignment.CenterEnd) {
                Text(
                    if (raining) "${formatDecimal(selected.precipitationMm, 1).replace('.', ',')} mm" else "${selected.cloudCover} %",
                    fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (raining) rainBlue else onSurface
                )
            }
            Box(Modifier.height(SOLEIL_H.dp), contentAlignment = Alignment.CenterEnd) {
                Text("UV ${selected.uvIndex.roundToInt()}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = onSurface)
            }
        }
    }
}

/** L'en-tête : le ressenti avec l'icône du ciel et la température de l'eau, la durée du jour à droite. */
@Composable
private fun WeatherHeader(h: HourlyUiModel, sun: DailySunInfo?, onSurface: Color) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            WeatherIcon(SurfUnitsHelper.resolveRealWeatherEmoji(h), 22.dp)
            Text("${h.feelsLike}°C", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = onSurface)
            h.seaTemperature?.let {
                Spacer(modifier = Modifier.width(4.dp))
                WaterTempIcon(18.dp, onSurface)
                Text("${it.roundToInt()}°", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = onSurface)
            }
        }
        if (sun != null) {
            val minutes = (sun.sunset.hour * 60 + sun.sunset.minute) - (sun.sunrise.hour * 60 + sun.sunrise.minute)
            if (minutes > 0) {
                Text(
                    "☀ ${minutes / 60} h ${(minutes % 60).toString().padStart(2, '0')}\nde jour", fontSize = 11.sp, lineHeight = 13.sp,
                    color = onSurface.copy(alpha = 0.6f), textAlign = androidx.compose.ui.text.style.TextAlign.End
                )
            }
        }
    }
}

private fun uvWord(uv: Double): String = when {
    uv < 3 -> "faible"
    uv < 6 -> "modéré"
    uv < 8 -> "fort"
    uv < 11 -> "très fort"
    else -> "extrême"
}

/** Température de l'eau : un thermomètre plein, rempli de bleu, posé sur deux petites vagues (même style que la flèche). */
@Composable
private fun WaterTempIcon(size: androidx.compose.ui.unit.Dp, tint: Color) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val blue = Color(0xFF29B6F6)
        // Tige et bulbe
        drawRoundRect(tint.copy(alpha = 0.85f), Offset(w * 0.36f, h * 0.02f), Size(w * 0.2f, h * 0.55f), CornerRadius(w * 0.1f, w * 0.1f))
        drawCircle(tint.copy(alpha = 0.85f), radius = w * 0.2f, center = Offset(w * 0.46f, h * 0.6f))
        drawRoundRect(blue, Offset(w * 0.41f, h * 0.22f), Size(w * 0.1f, h * 0.4f), CornerRadius(w * 0.05f, w * 0.05f))
        drawCircle(blue, radius = w * 0.13f, center = Offset(w * 0.46f, h * 0.6f))
        // Deux vagues
        for (i in 0..1) {
            val y = h * (0.84f + 0.13f * i)
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(w * 0.05f, y)
                quadraticTo(w * 0.2f, y - h * 0.07f, w * 0.35f, y)
                quadraticTo(w * 0.5f, y + h * 0.07f, w * 0.65f, y)
                quadraticTo(w * 0.8f, y - h * 0.07f, w * 0.95f, y)
            }
            drawPath(path, blue, style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.07f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        }
    }
}
