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
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

@Composable
fun SurfCardComponent(
    selectedHour: HourlyUiModel,
    allHoursOfDay: List<HourlyUiModel>,
    onHourSelected: (HourlyUiModel) -> Unit,
    isCollapsed: Boolean = false,
    onToggleCollapse: () -> Unit = {},
    dragHandleModifier: Modifier = Modifier,
    modifier: Modifier = Modifier,
    // Orientation de la plage : la taille de la flèche de houle dit si la houle est bien orientée.
    beachFacing: Int? = null
) {
    if (allHoursOfDay.isEmpty()) return

    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val primaryColor = MaterialTheme.colorScheme.primary
    val maxWave = (allHoursOfDay.maxOfOrNull { it.waveHeight } ?: 1.5).coerceAtLeast(2.0)

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
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
            CardControlsRow(
                title = "Houle", guide = "swell",
                isCollapsed = isCollapsed,
                onToggleCollapse = onToggleCollapse,
                dragHandleModifier = dragHandleModifier,
                summary = "${formatDecimal(selectedHour.waveHeight, 1).replace('.', ',')} m · ${selectedHour.wavePeriod.roundToInt()} s · " +
                    "${SurfUnitsHelper.formatCardinalFr(getCardinalDirection(selectedHour.waveDirection.toDouble()))} ${selectedHour.waveDirection.roundToInt()}° · ${selectedHour.energyKj} kJ"
            )
            if (!isCollapsed) {
            Spacer(modifier = Modifier.height(2.dp))
            SwellLine(
                height = selectedHour.waveHeight, period = selectedHour.wavePeriod, energyKj = selectedHour.energyKj,
                direction = selectedHour.waveDirection, onSurfaceColor = onSurfaceColor, primaryColor = primaryColor, secondary = false, beachFacing = beachFacing
            )
            if (selectedHour.secondaryHeight >= 0.2 && selectedHour.secondaryPeriod >= 7.0) {
                Spacer(modifier = Modifier.height(2.dp))
                SwellLine(
                    height = selectedHour.secondaryHeight, period = selectedHour.secondaryPeriod,
                    energyKj = calculateWaveEnergyReal(selectedHour.secondaryHeight, selectedHour.secondaryPeriod),
                    direction = selectedHour.secondaryDirection, onSurfaceColor = onSurfaceColor, primaryColor = primaryColor, secondary = true, beachFacing = beachFacing
                )
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
                        val heightRatio = (hourly.waveHeight / maxWave).toFloat().coerceIn(0.15f, 1f)

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
                            center = Offset(indicatorX, size.height * (1f - (selectedHour.waveHeight / maxWave).toFloat().coerceIn(0.15f, 1f)))
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

/** Une houle : à gauche « 1,4m 10s », à droite « OSO 250° », la flèche puis l'énergie en kJ ; la houle secondaire est plus petite et grisée. */
@Composable
private fun SwellLine(
    height: Double, period: Double, energyKj: Int, direction: Float,
    onSurfaceColor: Color, primaryColor: Color, secondary: Boolean, beachFacing: Int?
) {
    val fade = if (secondary) 0.6f else 1f
    val big = if (secondary) 14.sp else 18.sp
    val mid = if (secondary) 12.sp else 14.sp
    val small = if (secondary) 11.sp else 13.sp
    val deg = direction.roundToInt()
    val rotation = (direction + 180f) % 360f
    val cardinal = SurfUnitsHelper.formatCardinalFr(getCardinalDirection(deg.toDouble()))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${formatDecimal(height, 1)}m", fontSize = big, fontWeight = FontWeight.Bold, color = onSurfaceColor.copy(alpha = fade))
            Text("${period.roundToInt()}s", fontSize = mid, fontWeight = FontWeight.SemiBold, color = primaryColor.copy(alpha = fade))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("$cardinal $deg°", fontSize = small, fontWeight = FontWeight.Bold, color = onSurfaceColor.copy(alpha = fade))
            NavArrow(Color(0xFF29B6F6).copy(alpha = fade), rotation, ((if (secondary) 11f else 13f) * swellArrowScale(direction, beachFacing)).dp)
            Text("$energyKj kJ", fontSize = small, fontWeight = FontWeight.Bold, color = Color(0xFF8F6300).copy(alpha = fade))
        }
    }
}


/** « Bouée Cap Ferret, à 43 km : 1,7 m · 9,8 s · mesuré à 09 h 30 » (heure locale). */
internal fun buoyLine(b: BuoyReading): String {
    val local = b.timeUtc.toInstant(TimeZone.UTC).toLocalDateTime(TimeZone.currentSystemDefault())
    val h = formatDecimal(b.significantHeight, 1).replace('.', ',')
    val period = b.period?.let { " · ${it.roundToInt()} s" }.orEmpty()
    val max = b.maxHeight?.let { " (max ${formatDecimal(it, 1).replace('.', ',')} m)" }.orEmpty()
    return "Bouée ${b.buoy.name}, à ${b.distanceKm} km : $h m$period$max · mesuré à ${local.hour} h ${local.minute.toString().padStart(2, '0')}"
}
