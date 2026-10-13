package com.surfcast.surfforecast

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
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
    beachFacing: Int? = null,
    sun: DailySunInfo? = null
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
            .fillMaxWidth(),
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
                direction = selectedHour.waveDirection, onSurfaceColor = onSurfaceColor, primaryColor = primaryColor, secondary = false, beachFacing = beachFacing,
                chopHeight = if (SkyGraphSettings.showChop) selectedHour.windWaveHeight else 0.0,
                mixedPeriods = if (seaIsMixed(selectedHour)) selectedHour.periodMin to selectedHour.periodMax else null
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

            CellBars(
                hours = allHoursOfDay, sun = sun, selected = selectedHour, onHourSelected = onHourSelected,
                maxValue = maxWave,
                valueOf = { g -> g.map { it.waveHeight }.average() },
                colorOf = { _, sel -> if (sel) primaryColor else primaryColor.copy(alpha = 0.78f) }
            )
            }
        }
    }
}

/** Une houle : à gauche « 1,4m 10s », à droite « OSO 250° », la flèche puis l'énergie en kJ ; la houle secondaire est plus petite et grisée. */
@Composable
private fun SwellLine(
    height: Double, period: Double, energyKj: Int, direction: Float,
    onSurfaceColor: Color, primaryColor: Color, secondary: Boolean, beachFacing: Int?, chopHeight: Double = 0.0, mixedPeriods: Pair<Double, Double>? = null
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
        Column(horizontalAlignment = Alignment.End) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("$cardinal $deg°", fontSize = small, fontWeight = FontWeight.Bold, color = onSurfaceColor.copy(alpha = fade))
            NavArrow(Color(0xFF29B6F6).copy(alpha = fade), rotation, ((if (secondary) 11f else 13f) * swellArrowScale(direction, beachFacing)).dp)
            Text("$energyKj kJ", fontSize = small, fontWeight = FontWeight.Bold, color = Color(0xFF8F6300).copy(alpha = fade))
        }
        if (mixedPeriods != null) {
            Text(
                "Mer mélangée · ${mixedPeriods.first.roundToInt()} à ${mixedPeriods.second.roundToInt()} s", fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                color = Color(0xFFF2B84B),
                modifier = Modifier.padding(top = 3.dp)
                    .border(1.dp, onSurfaceColor.copy(alpha = 0.25f), RoundedCornerShape(50))
                    .padding(horizontal = 9.dp, vertical = 2.dp)
            )
        }
        if (chopHeight >= CHOP_PILL_MIN) {
            Text(
                "Clapot ${formatDecimal(chopHeight, 1).replace('.', ',')} m", fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                color = chopColor(chopHeight),
                modifier = Modifier.padding(top = 3.dp)
                    .border(1.dp, onSurfaceColor.copy(alpha = 0.25f), RoundedCornerShape(50))
                    .padding(horizontal = 9.dp, vertical = 2.dp)
            )
        }
        }
    }
}

/** Sous ce seuil (m), la mer est lisse : pas de pastille. */
internal const val CHOP_PILL_MIN = 0.1

/** Vert quand le clapot est faible, jaune puis rouge quand il gêne (mêmes repères que la note : 0,3 m). */
internal fun chopColor(h: Double): Color = when {
    h < 0.3 -> Color(0xFF34C98A)
    h < 0.6 -> Color(0xFFF2B84B)
    else -> Color(0xFFEF6A62)
}


/** « Bouée Cap Ferret, à 43 km : 1,7 m · 9,8 s · mesuré à 09 h 30 » (heure locale). */
internal fun buoyLine(b: BuoyReading): String {
    val local = b.timeUtc.toInstant(TimeZone.UTC).toLocalDateTime(TimeZone.currentSystemDefault())
    val h = formatDecimal(b.significantHeight, 1).replace('.', ',')
    val period = b.period?.let { " · ${it.roundToInt()} s" }.orEmpty()
    val max = b.maxHeight?.let { " (max ${formatDecimal(it, 1).replace('.', ',')} m)" }.orEmpty()
    return "Bouée ${b.buoy.name}, à ${b.distanceKm} km : $h m$period$max · mesuré à ${local.hour} h ${local.minute.toString().padStart(2, '0')}"
}
