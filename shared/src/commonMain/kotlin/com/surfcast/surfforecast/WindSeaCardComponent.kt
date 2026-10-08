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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun WindSeaCardComponent(
    selectedHour: HourlyUiModel,
    allHoursOfDay: List<HourlyUiModel>,
    onHourSelected: (HourlyUiModel) -> Unit,
    isCollapsed: Boolean = false,
    onToggleCollapse: () -> Unit = {},
    dragHandleModifier: Modifier = Modifier,
    modifier: Modifier = Modifier
) {
    if (allHoursOfDay.isEmpty()) return

    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val primaryColor = Color(0xFF66BB6A)
    val maxWindWave = (allHoursOfDay.maxOfOrNull { it.windWaveHeight } ?: 1.0).coerceAtLeast(1.0)

    // Remplace le Paint Android (non multiplateforme) : même taille, même graisse.
    val textMeasurer = rememberTextMeasurer()
    val timeTextPaint = TextStyle(color = onSurfaceColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)

    val windWaveDeg = selectedHour.windWaveDirection.roundToInt()
    val windWaveRotationAngle = (selectedHour.windWaveDirection + 180f) % 360f

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
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
            CardControlsRow(
                title = "Mer de vent",
                isCollapsed = isCollapsed,
                onToggleCollapse = onToggleCollapse,
                dragHandleModifier = dragHandleModifier
            )
            if (!isCollapsed) {
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${formatDecimal(selectedHour.windWaveHeight, 1)}m",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = onSurfaceColor
                    )
                    Text(
                        // 0 = période inconnue (le modèle ne la donne pas à cette heure), pas "0s".
                        text = if (selectedHour.windWavePeriod > 0.0) "${selectedHour.windWavePeriod.roundToInt()}s" else "—",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = primaryColor
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Canvas(modifier = Modifier.size(13.dp)) {
                        val w = size.width
                        val h = size.height
                        rotate(windWaveRotationAngle, pivot = Offset(w / 2f, h / 2f)) {
                            val path = Path().apply {
                                moveTo(w * 0.5f, 0.5.dp.toPx())
                                lineTo(w * 0.95f, h * 0.48f)
                                lineTo(w * 0.68f, h * 0.48f)
                                lineTo(w * 0.68f, h * 0.98f)
                                lineTo(w * 0.32f, h * 0.98f)
                                lineTo(w * 0.32f, h * 0.48f)
                                lineTo(w * 0.05f, h * 0.48f)
                                close()
                            }
                            drawPath(path = path, color = primaryColor)
                            drawPath(
                                path = path,
                                color = onSurfaceColor.copy(alpha = 0.5f),
                                style = Stroke(width = 0.8.dp.toPx())
                            )
                        }
                    }

                    Text(
                        text = "$windWaveDeg°",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = onSurfaceColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

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
                        val heightRatio = (hourly.windWaveHeight / maxWindWave).toFloat().coerceIn(0.15f, 1f)

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
                                        listOf(primaryColor, primaryColor.copy(alpha = 0.85f))
                                    } else {
                                        listOf(primaryColor.copy(alpha = 0.5f), primaryColor.copy(alpha = 0.2f))
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

                        drawCircle(
                            color = onSurfaceColor,
                            radius = 3.2f,
                            center = Offset(indicatorX, size.height * (1f - (selectedHour.windWaveHeight / maxWindWave).toFloat().coerceIn(0.15f, 1f)))
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
