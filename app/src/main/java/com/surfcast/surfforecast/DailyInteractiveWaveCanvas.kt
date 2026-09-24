package com.surfcast.surfforecast

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun DailyInteractiveWaveCanvas(
    hourlyData: List<HourlyUiModel>,
    selectedHour: HourlyUiModel?,
    onHourSelected: (HourlyUiModel) -> Unit,
    modifier: Modifier = Modifier
) {
    if (hourlyData.isEmpty()) return

    val primaryColor = MaterialTheme.colorScheme.primary
    val primaryColorInt = primaryColor.hashCode()
    val density = LocalDensity.current

    var activeIndex by remember(hourlyData, selectedHour) {
        val initialIndex = if (selectedHour != null) {
            hourlyData.indexOfFirst { it.rawTime == selectedHour.rawTime }.coerceAtLeast(0)
        } else 0
        mutableIntStateOf(initialIndex)
    }

    val maxWaveHeight = (hourlyData.maxOfOrNull { it.waveHeight } ?: 1.5).coerceAtLeast(2.0).toFloat()

    val labelPaint = remember(density) {
        Paint().apply {
            color = Color.White.hashCode()
            textSize = with(density) { 9.sp.toPx() }
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
    }

    val axisTextPaint = remember(density) {
        Paint().apply {
            color = 0xFF90A4AE.toInt()
            textSize = with(density) { 8.sp.toPx() }
            isAntiAlias = true
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(hourlyData) {
                detectHorizontalDragGestures { change, _ ->
                    val x = change.position.x.coerceIn(0f, size.width.toFloat())
                    val stepWidth = size.width / (hourlyData.size - 1).coerceAtLeast(1).toFloat()
                    val newIndex = (x / stepWidth).roundToInt().coerceIn(0, hourlyData.size - 1)
                    if (newIndex != activeIndex) {
                        activeIndex = newIndex
                        onHourSelected(hourlyData[newIndex])
                    }
                }
            }
            .pointerInput(hourlyData) {
                detectTapGestures { offset ->
                    val stepWidth = size.width / (hourlyData.size - 1).coerceAtLeast(1).toFloat()
                    val newIndex = (offset.x / stepWidth).roundToInt().coerceIn(0, hourlyData.size - 1)
                    activeIndex = newIndex
                    onHourSelected(hourlyData[newIndex])
                }
            }
    ) {
        val w = size.width
        val h = size.height

        val paddingTop = 18.dp.toPx()
        val paddingBottom = 16.dp.toPx()
        val usableHeight = h - paddingTop - paddingBottom
        val baseY = h - paddingBottom

        val stepX = w / (hourlyData.size - 1).coerceAtLeast(1).toFloat()

        val points = hourlyData.mapIndexed { index, item ->
            val ratio = (item.waveHeight.toFloat() / maxWaveHeight).coerceIn(0f, 1f)
            val y = paddingTop + usableHeight * (1f - ratio)
            Offset(index * stepX, y)
        }

        val fillPath = Path().apply {
            moveTo(0f, baseY)
            lineTo(points.first().x, points.first().y)
            for (i in 1 until points.size) {
                val prev = points[i - 1]
                val curr = points[i]
                val midX = (prev.x + curr.x) / 2f
                cubicTo(midX, prev.y, midX, curr.y, curr.x, curr.y)
            }
            lineTo(w, baseY)
            close()
        }

        drawPath(
            path = fillPath,
            color = primaryColor.copy(alpha = 0.25f)
        )

        val strokePath = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (i in 1 until points.size) {
                val prev = points[i - 1]
                val curr = points[i]
                val midX = (prev.x + curr.x) / 2f
                cubicTo(midX, prev.y, midX, curr.y, curr.x, curr.y)
            }
        }

        drawPath(
            path = strokePath,
            color = primaryColor,
            style = Stroke(width = 2.dp.toPx())
        )

        if (activeIndex in points.indices) {
            val selectedPoint = points[activeIndex]
            val currentItem = hourlyData[activeIndex]

            drawLine(
                color = Color.White.copy(alpha = 0.8f),
                start = Offset(selectedPoint.x, 0f),
                end = Offset(selectedPoint.x, h),
                strokeWidth = 1.5.dp.toPx()
            )

            drawCircle(
                color = Color.White,
                radius = 4.dp.toPx(),
                center = selectedPoint
            )
            drawCircle(
                color = primaryColor,
                radius = 2.5.dp.toPx(),
                center = selectedPoint
            )

            val timeStr = currentItem.rawTime.format(DateTimeFormatter.ofPattern("HH:mm"))
            val textWidth = labelPaint.measureText(timeStr)
            val targetTextX = (selectedPoint.x - textWidth / 2f).coerceIn(4.dp.toPx(), w - textWidth - 4.dp.toPx())

            drawContext.canvas.nativeCanvas.drawText(
                timeStr,
                targetTextX,
                12.dp.toPx(),
                labelPaint
            )
        }

        val stepHours = if (hourlyData.size > 12) 3 else 2
        hourlyData.forEachIndexed { index, item ->
            if (item.rawTime.hour % stepHours == 0) {
                val x = index * stepX
                val hourLabel = "${item.rawTime.hour}h"
                val textW = axisTextPaint.measureText(hourLabel)
                drawContext.canvas.nativeCanvas.drawText(
                    hourLabel,
                    x - textW / 2f,
                    h - 2.dp.toPx(),
                    axisTextPaint
                )
            }
        }
    }
}