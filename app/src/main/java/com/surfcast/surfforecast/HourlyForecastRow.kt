package com.surfcast.surfforecast

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun HourlyForecastRow(
    hourlyData: HourlyUiModel,
    windUnit: String,
    modifier: Modifier = Modifier,
    dailyTideInfo: DailyTideInfo? = null,
    isSelected: Boolean = false
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    // Détection de l'heure actuelle en temps réel
    val now = LocalTime.now()
    val isToday = hourlyData.rawTime.toLocalDate() == LocalDate.now()
    val isCurrentHour = isToday && hourlyData.rawTime.hour == now.hour

    val dirFr = SurfUnitsHelper.formatCardinalFr(hourlyData.windDirectionStr)
    val degrees = SurfUnitsHelper.cardinalToDegrees(dirFr)
    val rotationAngle = (degrees + 180f) % 360f

    // Palette foncee (vs. getSurfWindColor) : meilleur contraste en petite taille sur
    // fond clair pour les 3 paliers (jaune/orange/rouge), pas seulement le jaune.
    val windColor = SurfUnitsHelper.getSurfWindTextColor(dirFr, hourlyData.windSpeedKmh)
    val speedFormatted = SurfUnitsHelper.formatWindValue(hourlyData.windSpeedKmh, windUnit)
    val unitSymbol = SurfUnitsHelper.getWindUnitSymbol(windUnit)

    val rowShape = RoundedCornerShape(8.dp)

    // Calcul de l'état : Montant (Bleu) ou Descendant (Vert)
    val isRising = isTideRising(hourlyData.rawTime.toLocalTime(), dailyTideInfo)

    val tideBackgroundColor = when (isRising) {
        true -> Color(0xFF1E88E5).copy(alpha = 0.16f)   // Bleu marée montante
        false -> Color(0xFF00897B).copy(alpha = 0.16f)  // Vert turquoise marée descendante
        null -> Color.Transparent
    }

    val finalBackground = when {
        isSelected -> primaryColor.copy(alpha = 0.32f)
        isCurrentHour -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
        else -> tideBackgroundColor
    }

    val finalBorderColor = when {
        isSelected -> primaryColor
        isCurrentHour -> primaryColor.copy(alpha = 0.8f)
        else -> Color.Transparent
    }

    val textShadow = Shadow(
        color = Color.Black.copy(alpha = 0.35f),
        offset = Offset(0.5f, 1f),
        blurRadius = 2f
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 1.5.dp)
            .clip(rowShape)
            .background(finalBackground)
            .border(if (isSelected || isCurrentHour) 1.5.dp else 0.dp, finalBorderColor, rowShape)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Col 1 : Icône Marée (Weekly) + Pastille Heure Actuelle + Heure
        Row(
            modifier = Modifier.weight(1.35f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icône de marée identique au Weekly
            if (isRising != null) {
                Canvas(modifier = Modifier.size(width = 9.dp, height = 11.dp)) {
                    val w = size.width
                    val h = size.height
                    val iconColor = if (isRising) Color(0xFF1E88E5) else Color(0xFF00897B)
                    val waveY = if (isRising) h * 0.75f else h * 0.25f

                    // Petite onde
                    val wavePath = Path().apply {
                        moveTo(0f, waveY)
                        quadraticBezierTo(w * 0.5f, if (isRising) h * 0.55f else h * 0.45f, w, waveY)
                    }
                    drawPath(wavePath, color = iconColor.copy(alpha = 0.8f), style = Stroke(width = 1.dp.toPx()))

                    // Flèche montante ou descendante
                    val arrowX = w * 0.5f
                    val arrowBottom = if (isRising) waveY else h * 0.75f
                    val arrowTop = if (isRising) h * 0.15f else waveY
                    drawLine(color = iconColor, start = Offset(arrowX, arrowBottom), end = Offset(arrowX, arrowTop), strokeWidth = 1.1.dp.toPx())

                    val headPath = Path().apply {
                        if (isRising) {
                            moveTo(arrowX - 2.dp.toPx(), arrowTop + 2.dp.toPx())
                            lineTo(arrowX, arrowTop)
                            lineTo(arrowX + 2.dp.toPx(), arrowTop + 2.dp.toPx())
                        } else {
                            moveTo(arrowX - 2.dp.toPx(), arrowBottom - 2.dp.toPx())
                            lineTo(arrowX, arrowBottom)
                            lineTo(arrowX + 2.dp.toPx(), arrowBottom - 2.dp.toPx())
                        }
                    }
                    drawPath(headPath, color = iconColor, style = Stroke(width = 1.1.dp.toPx()))
                }
                Spacer(modifier = Modifier.width(3.dp))
            }

            // Pastille lumineuse pour l'heure actuelle
            if (isCurrentHour) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(RoundedCornerShape(2.5.dp))
                        .background(primaryColor)
                )
                Spacer(modifier = Modifier.width(3.dp))
            }

            Text(
                text = hourlyData.timeFormatted,
                fontSize = 12.sp,
                fontWeight = if (isSelected || isCurrentHour) FontWeight.ExtraBold else FontWeight.Bold,
                color = if (isSelected || isCurrentHour) primaryColor else onSurfaceColor,
                maxLines = 1,
                textAlign = TextAlign.Start
            )
        }

        // Col 2 : Météo (Émoji + Température)
        Row(
            modifier = Modifier.weight(1.05f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Text(
                text = SurfUnitsHelper.resolveRealWeatherEmoji(hourlyData),
                fontSize = 12.sp,
                maxLines = 1
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = "${hourlyData.temperature}°",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = onSurfaceColor,
                maxLines = 1
            )
        }

        // Col 3 : Hauteur de vague
        Text(
            text = String.format(Locale.US, "%.1fm", hourlyData.waveHeight),
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            color = primaryColor,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1.1f)
        )

        // Col 4 : Période
        Text(
            text = "${hourlyData.wavePeriod.roundToInt()}s",
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            color = if (isSelected || isCurrentHour) primaryColor else Color.Gray,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(0.9f)
        )

        // Col 5 : Énergie (couleur foncée + ombre pour lisibilité en mode clair)
        Text(
            text = "${hourlyData.energyKj}kJ",
            style = TextStyle(
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF8F6300),
                shadow = textShadow,
                textAlign = TextAlign.Center
            ),
            maxLines = 1,
            modifier = Modifier.weight(1.2f)
        )

        // Col 6 : Direction cardinale (avec ombre contrastée)
        Text(
            text = dirFr,
            style = TextStyle(
                fontSize = if (dirFr.length >= 3) 10.sp else 11.sp,
                fontWeight = FontWeight.Bold,
                color = windColor,
                shadow = textShadow,
                textAlign = TextAlign.End
            ),
            maxLines = 1,
            modifier = Modifier.weight(1.0f)
        )

        // Col 7 : Flèche de vent
        Box(
            modifier = Modifier.weight(0.7f),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(12.dp)) {
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
                    drawPath(path = path, color = windColor)
                    drawPath(path = path, color = Color.Gray, style = Stroke(width = 0.8.dp.toPx()))
                }
            }
        }

        // Col 8 : Vitesse du vent (avec ombre contrastée)
        Text(
            text = "$speedFormatted $unitSymbol",
            style = TextStyle(
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = windColor,
                shadow = textShadow,
                textAlign = TextAlign.End
            ),
            maxLines = 1,
            modifier = Modifier.weight(1.6f)
        )
    }
}

/**
 * Parsing tolérant ("07h30", "7h30", "07:30", "7:30")
 */
private fun parseTimeStringSafe(timeStr: String): LocalTime? {
    val clean = timeStr.trim().lowercase().replace("h", ":")
    val parts = clean.split(":")
    if (parts.size < 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    return try {
        LocalTime.of(h.coerceIn(0, 23), m.coerceIn(0, 59))
    } catch (_: Exception) {
        null
    }
}

/**
 * Calcule si la marée monte ou descend à une heure cible.
 */
private fun isTideRising(targetTime: LocalTime, tideInfo: DailyTideInfo?): Boolean? {
    val pm = tideInfo?.highTideTime?.let { parseTimeStringSafe(it) }
    val bm = tideInfo?.lowTideTime?.let { parseTimeStringSafe(it) }

    if (pm == null && bm == null) return null

    val targetSec = targetTime.toSecondOfDay()
    val halfCycle = (6.21 * 3600).toInt() // ~6h12.5min

    val bmSecList = mutableListOf<Int>()
    val pmSecList = mutableListOf<Int>()

    bm?.let {
        val s = it.toSecondOfDay()
        for (k in -3..3) {
            bmSecList.add(s + k * (2 * halfCycle))
        }
    }

    pm?.let {
        val s = it.toSecondOfDay()
        for (k in -3..3) {
            pmSecList.add(s + k * (2 * halfCycle))
        }
    }

    val lastBm = bmSecList.filter { it <= targetSec }.maxOrNull()
    val lastPm = pmSecList.filter { it <= targetSec }.maxOrNull()

    return when {
        lastBm != null && lastPm != null -> lastBm > lastPm
        lastBm != null -> true
        lastPm != null -> false
        else -> null
    }
}