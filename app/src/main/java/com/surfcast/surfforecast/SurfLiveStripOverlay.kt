@file:Suppress("SpellCheckingInspection")
package com.surfcast.surfforecast

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@Composable
fun SurfLiveStripOverlay(
    hourlyModel: HourlyUiModel,
    tideInfo: DailyTideInfo?,
    windUnit: String,
    onOpenCam: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dirFr = SurfUnitsHelper.formatCardinalFr(hourlyModel.windDirectionStr)
    val degrees = SurfUnitsHelper.cardinalToDegrees(dirFr)
    val rotationAngle = (degrees + 180f) % 360f
    val arrowColor = SurfUnitsHelper.getSurfWindColor(dirFr, hourlyModel.windSpeedKmh)
    val speedValue = SurfUnitsHelper.formatWindValue(hourlyModel.windSpeedKmh, windUnit)
    val unitSymbol = SurfUnitsHelper.getWindUnitSymbol(windUnit)

    val formattedH = String.format(Locale.US, "%.1fm", hourlyModel.waveHeight)
    val periodSec = hourlyModel.wavePeriod.toInt()
    val tempVal = hourlyModel.temperature

    val highTime = tideInfo?.highTideTime ?: "--h--"
    val lowTime = tideInfo?.lowTideTime ?: "--h--"
    val coef = tideInfo?.coefficient

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(8.dp)),
        color = Color(0xEB1A1E24),
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Houle & Eau
            Row(
                modifier = Modifier.weight(1.05f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "🌊", fontSize = 11.sp)
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = "$formattedH - ${periodSec}s - ${tempVal}°C",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1
                )
            }

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(14.dp)
                    .background(Color(0x33FFFFFF))
            )

            // 2. Marées (Icônes vectorielles originales de la vue hebdo)
            Row(
                modifier = Modifier
                    .weight(1.25f)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // Pleine mer
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(modifier = Modifier.size(11.dp)) {
                        val w = size.width
                        val h = size.height

                        val wavePath = Path().apply {
                            moveTo(0f, h * 0.70f)
                            quadraticBezierTo(w * 0.5f, h * 0.25f, w, h * 0.70f)
                        }
                        drawPath(wavePath, color = Color(0xFF64B5F6), style = Stroke(width = 1.1.dp.toPx(), cap = StrokeCap.Round))

                        val arrowX = w * 0.5f
                        drawLine(
                            color = Color(0xFF64B5F6),
                            start = Offset(arrowX, h * 0.70f),
                            end = Offset(arrowX, h * 0.10f),
                            strokeWidth = 1.1.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                        val headSize = 1.6.dp.toPx()
                        val headPath = Path().apply {
                            moveTo(arrowX - headSize, h * 0.10f + headSize)
                            lineTo(arrowX, h * 0.10f)
                            lineTo(arrowX + headSize, h * 0.10f + headSize)
                        }
                        drawPath(headPath, color = Color(0xFF64B5F6), style = Stroke(width = 1.1.dp.toPx(), cap = StrokeCap.Round))
                    }
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = if (coef != null) "$highTime ($coef)" else highTime,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.width(5.dp))

                // Basse mer
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(modifier = Modifier.size(11.dp)) {
                        val w = size.width
                        val h = size.height

                        val wavePath = Path().apply {
                            moveTo(0f, h * 0.30f)
                            quadraticBezierTo(w * 0.5f, h * 0.75f, w, h * 0.30f)
                        }
                        drawPath(wavePath, color = Color(0xFF26A69A), style = Stroke(width = 1.1.dp.toPx(), cap = StrokeCap.Round))

                        val arrowX = w * 0.5f
                        drawLine(
                            color = Color(0xFF26A69A),
                            start = Offset(arrowX, h * 0.30f),
                            end = Offset(arrowX, h * 0.90f),
                            strokeWidth = 1.1.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                        val headSize = 1.6.dp.toPx()
                        val headPath = Path().apply {
                            moveTo(arrowX - headSize, h * 0.90f - headSize)
                            lineTo(arrowX, h * 0.90f)
                            lineTo(arrowX + headSize, h * 0.90f - headSize)
                        }
                        drawPath(headPath, color = Color(0xFF26A69A), style = Stroke(width = 1.1.dp.toPx(), cap = StrokeCap.Round))
                    }
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = lowTime,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1
                    )
                }
            }

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(14.dp)
                    .background(Color(0x33FFFFFF))
            )

            // 3. Vent (Direction + Flèche originale orientée + Vitesse avec unité dynamique)
            Row(
                modifier = Modifier
                    .weight(1.05f)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                Text(
                    text = dirFr,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = arrowColor,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.width(2.dp))

                Canvas(modifier = Modifier.size(11.dp)) {
                    val w = size.width
                    val h = size.height

                    rotate(rotationAngle, pivot = Offset(w / 2f, h / 2f)) {
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
                        drawPath(path = path, color = arrowColor)
                        drawPath(path = path, color = Color.White.copy(alpha = 0.5f), style = Stroke(width = 0.8.dp.toPx()))
                    }
                }

                Spacer(modifier = Modifier.width(2.dp))

                Text(
                    text = "$speedValue $unitSymbol",
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = arrowColor,
                    maxLines = 1
                )
            }
        }
    }
}