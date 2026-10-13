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

@Composable
fun WindCardComponent(
    selectedHour: HourlyUiModel,
    allHoursOfDay: List<HourlyUiModel>,
    windUnit: String,
    onHourSelected: (HourlyUiModel) -> Unit,
    isCollapsed: Boolean = false,
    onToggleCollapse: () -> Unit = {},
    dragHandleModifier: Modifier = Modifier,
    modifier: Modifier = Modifier,
    sun: DailySunInfo? = null
) {
    if (allHoursOfDay.isEmpty()) return

    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    // Échelle commune au vent et aux rafales : la barre de rafale (derrière) est toujours au moins aussi haute que celle du vent.
    val maxWind = (allHoursOfDay.maxOfOrNull { maxOf(it.windSpeedKmh, it.windGustKmh).toDouble() } ?: 15.0).coerceAtLeast(20.0)

    // Remplace le Paint Android (non multiplateforme) : même taille, même graisse.
    val textMeasurer = rememberTextMeasurer()
    val timeTextPaint = TextStyle(color = onSurfaceColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)

    val dirFr = SurfUnitsHelper.formatCardinalFr(selectedHour.windDirectionStr)
    val degrees = SurfUnitsHelper.cardinalToDegrees(dirFr)
    val rotationAngle = (degrees + 180f) % 360f
    val currentArrowColor = SurfUnitsHelper.getSurfWindColor(dirFr, windFeelKmh(selectedHour))

    val formattedSpeed = SurfUnitsHelper.formatWindValue(effectiveWindKmh(selectedHour), windUnit)
    val unitSymbol = SurfUnitsHelper.getWindUnitSymbol(windUnit)
    val formattedGust = SurfUnitsHelper.formatWindValue(selectedHour.windGustKmh, windUnit)

    Card(
        modifier = modifier
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = surfaceColor),
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            CardControlsRow(
                title = "Vent", guide = "wind",
                isCollapsed = isCollapsed,
                onToggleCollapse = onToggleCollapse,
                dragHandleModifier = dragHandleModifier,
                summary = "$dirFr $formattedSpeed $unitSymbol" + (if (selectedHour.windGustKmh > selectedHour.windSpeedKmh) " · rafales $formattedGust" else "")
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
                        text = "$formattedSpeed $unitSymbol",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = onSurfaceColor
                    )
                    if (selectedHour.windGustKmh > selectedHour.windSpeedKmh) {
                        Text(
                            text = "Rafales $formattedGust $unitSymbol",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = onSurfaceColor.copy(alpha = 0.7f)
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        NavArrow(currentArrowColor, rotationAngle, (13 * windArrowScale(windFeelKmh(selectedHour))).dp)
                        Text(
                            text = dirFr,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = onSurfaceColor
                        )
                    }
                }
            }

            if (windModelsDisagree(selectedHour)) {
                Text(
                    "Modèles partagés : ${SurfUnitsHelper.formatWindValue(selectedHour.windMinKmh, windUnit)} à ${SurfUnitsHelper.formatWindValue(selectedHour.windMaxKmh, windUnit)} $unitSymbol",
                    fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFF2B84B),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))

            CellBars(
                hours = allHoursOfDay, sun = sun, selected = selectedHour, onHourSelected = onHourSelected,
                maxValue = maxWind,
                valueOf = { g -> g.maxOf { effectiveWindKmh(it).toDouble() } },
                colorOf = { g, sel ->
                    // Le vent du moment le plus fort de la case, dans la couleur de sa direction et de sa force.
                    val h = g.maxByOrNull { effectiveWindKmh(it) } ?: g.first()
                    val c = SurfUnitsHelper.getSurfWindColor(SurfUnitsHelper.formatCardinalFr(h.windDirectionStr), windFeelKmh(h))
                    if (sel) c else c.copy(alpha = 0.85f)
                },
                // Les rafales : une barre grise derrière celle du vent.
                backValueOf = { g -> g.maxOf { it.windGustKmh.toDouble() } }
            )
            }
        }
    }
}
