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
    val maxTemp = (allHoursOfDay.maxOfOrNull { it.temperature.toDouble() } ?: 25.0).coerceAtLeast(30.0)

    // Remplace le Paint Android (non multiplateforme) : même taille, même graisse.
    val textMeasurer = rememberTextMeasurer()
    val timeTextPaint = TextStyle(color = onSurfaceColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(allHoursOfDay) {
                detectHorizontalDragGestures { change, _ ->
                    val x = change.position.x.coerceIn(0f, size.width.toFloat())
                    val widthPerItem = size.width / allHoursOfDay.size.toFloat()
                    val index = (x / widthPerItem).toInt().coerceIn(0, allHoursOfDay.size - 1)
                    onHourSelected(allHoursOfDay[index])
                }
            }
            .pointerInput(allHoursOfDay) {
                detectTapGestures { offset ->
                    val widthPerItem = size.width / allHoursOfDay.size.toFloat()
                    val index = (offset.x / widthPerItem).toInt().coerceIn(0, allHoursOfDay.size - 1)
                    onHourSelected(allHoursOfDay[index])
                }
            },
        colors = CardDefaults.cardColors(containerColor = surfaceColor),
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            CardControlsRow(
                title = "Météo",
                isCollapsed = isCollapsed,
                onToggleCollapse = onToggleCollapse,
                dragHandleModifier = dragHandleModifier,
                summary = "${selectedHour.feelsLike}° ressentis · nuages ${selectedHour.cloudCover} %" +
                    (if (selectedHour.precipitationMm >= 0.05) " · pluie ${formatDecimal(selectedHour.precipitationMm, 1).replace('.', ',')} mm" else "")
            )
            if (!isCollapsed) {
            Spacer(modifier = Modifier.height(2.dp))
            WeatherFacts(selectedHour, sun, onSurfaceColor, onOpenDetail)
            Spacer(modifier = Modifier.height(6.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                val currentHourNow = nowLocalDateTime().hour
                val isToday = allHoursOfDay.firstOrNull()?.rawTime?.date == nowLocalDateTime().date

                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.fillMaxSize()
                ) {
                    allHoursOfDay.forEach { hourly ->
                        val isSelected = hourly.rawTime == selectedHour.rawTime
                        val isCurrentHour = isToday && hourly.rawTime.hour == currentHourNow
                        val heightRatio = (hourly.temperature.toFloat() / maxTemp.toFloat()).coerceIn(0.15f, 1f)

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Canvas(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(heightRatio)
                            ) {
                                val brush = Brush.verticalGradient(
                                    colors = if (isSelected) {
                                        listOf(Color(0xFF26A69A), Color(0xFF00897B))
                                    } else {
                                        listOf(Color(0xFF26A69A).copy(alpha = 0.5f), Color(0xFF00897B).copy(alpha = 0.2f))
                                    }
                                )
                                drawRoundRect(
                                    brush = brush,
                                    topLeft = Offset.Zero,
                                    size = Size(size.width, size.height),
                                    cornerRadius = CornerRadius(3f, 3f)
                                )

                                if (isCurrentHour) {
                                    drawRoundRect(
                                        color = onSurfaceColor.copy(alpha = 0.85f),
                                        topLeft = Offset.Zero,
                                        size = Size(size.width, size.height),
                                        cornerRadius = CornerRadius(3f, 3f),
                                        style = Stroke(width = 1.2.dp.toPx())
                                    )
                                }
                            }
                        }
                    }
                }

                val selectedIndex = allHoursOfDay.indexOfFirst { it.rawTime == selectedHour.rawTime }
                if (selectedIndex != -1) {
                    val fraction = selectedIndex.toFloat() / (allHoursOfDay.size - 1).coerceAtLeast(1)
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val indicatorX = size.width * fraction

                        drawLine(
                            color = onSurfaceColor.copy(alpha = 0.75f),
                            start = Offset(indicatorX, 0f),
                            end = Offset(indicatorX, size.height),
                            strokeWidth = 1.8f
                        )

                        val currentBarHeight = size.height * (1f - (selectedHour.temperature.toFloat() / maxTemp.toFloat()).coerceIn(0.15f, 1f))
                        drawCircle(
                            color = onSurfaceColor,
                            radius = 3.2f,
                            center = Offset(indicatorX, currentBarHeight)
                        )

                        val timeStr = selectedHour.rawTime.formatHHmm()
                        val timeStrLayout = textMeasurer.measure(timeStr, timeTextPaint)
                        val textWidth = timeStrLayout.size.width.toFloat()
                        val adjustedX = indicatorX.coerceIn(textWidth / 2f, size.width - textWidth / 2f)

                        drawText(timeStrLayout, topLeft = Offset(adjustedX - textWidth / 2f, 4.dp.toPx() - timeStrLayout.firstBaseline))
                    }
                }
            }
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

private fun hm(t: kotlinx.datetime.LocalTime): String = "${t.hour} h ${t.minute.toString().padStart(2, '0')}"

/**
 * L'en-tête de la météo de l'heure. À gauche : l'icône du ciel et le ressenti en gros, la température de l'eau à côté, la pluie,
 * puis lever et coucher du soleil sur une même ligne. À droite, l'un au-dessus de l'autre : l'indice UV et les nuages.
 */
@Composable
private fun WeatherFacts(h: HourlyUiModel, sun: DailySunInfo?, onSurface: Color, onOpenDetail: (() -> Unit)?) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                WeatherIcon(SurfUnitsHelper.resolveRealWeatherEmoji(h), 22.dp)
                Text("${h.feelsLike}°C", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = onSurface)
                // Quand il pleut : l'icône de pluie et les millimètres de l'heure, juste à côté de la température.
                if (h.precipitationMm >= 0.05) {
                    Text("🌧️ ${formatDecimal(h.precipitationMm, 1).replace('.', ',')} mm", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = onSurface)
                }
                h.seaTemperature?.let {
                    Spacer(modifier = Modifier.width(4.dp))
                    WaterTempIcon(18.dp, onSurface)
                    Text("${it.roundToInt()}°", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = onSurface)
                }
            }
            if (sun != null) Text("🌅 ${hm(sun.sunrise)}   🌇 ${hm(sun.sunset)}", fontSize = 12.5.sp, color = onSurface, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("☀️ UV ${h.uvIndex.roundToInt()}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = onSurface)
            Text("☁️ ${h.cloudCover} %", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = onSurface)
        }
    }
    if (onOpenDetail != null) {
        Text(
            "Détail sur la semaine ›", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 4.dp).clickable(onClick = onOpenDetail)
        )
    }
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
