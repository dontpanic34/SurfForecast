package com.surfcast.surfforecast

import android.graphics.Paint
import android.graphics.Typeface
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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun WeatherCardComponent(
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
    val maxTemp = (allHoursOfDay.maxOfOrNull { it.temperature.toDouble() } ?: 25.0).coerceAtLeast(30.0)

    val density = LocalDensity.current
    val timeTextPaint = remember(density, onSurfaceColor) {
        Paint().apply {
            color = onSurfaceColor.toArgb()
            textSize = with(density) { 9.sp.toPx() }
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
    }

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
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = SurfUnitsHelper.resolveRealWeatherEmoji(selectedHour),
                        fontSize = 18.sp
                    )
                    Text(
                        text = "${selectedHour.temperature}°C",
                        fontSize = 18.sp,
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
                val currentHourNow = LocalTime.now().hour
                val isToday = allHoursOfDay.firstOrNull()?.rawTime?.toLocalDate() == LocalDate.now()

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

                        val timeStr = selectedHour.rawTime.format(DateTimeFormatter.ofPattern("HH:mm"))
                        val textWidth = timeTextPaint.measureText(timeStr)
                        val adjustedX = indicatorX.coerceIn(textWidth / 2f, size.width - textWidth / 2f)

                        drawContext.canvas.nativeCanvas.drawText(
                            timeStr,
                            adjustedX - textWidth / 2f,
                            4.dp.toPx(),
                            timeTextPaint
                        )
                    }
                }
            }
            }
        }
    }
}
