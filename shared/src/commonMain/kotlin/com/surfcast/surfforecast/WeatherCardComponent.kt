package com.surfcast.surfforecast

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.foundation.Canvas
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
                title = "Météo",
                isCollapsed = isCollapsed,
                onToggleCollapse = onToggleCollapse,
                dragHandleModifier = dragHandleModifier,
                summary = "${selectedHour.feelsLike}° ressentis · nuages ${selectedHour.cloudCover} %" +
                    (if (selectedHour.precipitationMm >= 0.05) " · pluie ${formatDecimal(selectedHour.precipitationMm, 1).replace('.', ',')} mm" else "")
            )
            if (!isCollapsed) {
            Spacer(modifier = Modifier.height(2.dp))
            WeatherInfoLine(selectedHour, onSurfaceColor)
            Spacer(modifier = Modifier.height(6.dp))
            val isDark = isDarkSurfaceTheme()
            CellBars(
                hours = allHoursOfDay, sun = sun, selected = selectedHour, onHourSelected = onHourSelected,
                maxValue = 100.0,
                // Le ciel : bleu et plus haut quand il pleut, sinon la couverture nuageuse en gris (la couverture ne compte pas sous la pluie).
                valueOf = { g ->
                    // La hauteur dit la température (de 5 à 35 °C) : plus il fait chaud, plus la cheminée est haute ; la couleur dit le ciel.
                    val temp = g.map { it.temperature }.average()
                    14.0 + 86.0 * ((temp - 5.0) / 30.0).coerceIn(0.0, 1.0)
                },
                colorOf = { g, sel ->
                    val raining = g.any { it.precipitationMm >= 0.05 }
                    val cover = g.map { it.cloudCover }.average()
                    val temp = g.map { it.temperature }.average()
                    // La couleur dit le ciel : de la pluie en bleu ; des nuages en gris, plus sombre quand le ciel se couvre ; un ciel
                    // dégagé en jaune, orange puis rouge selon la température.
                    val base = if (raining) Color(0xFF5AA9E6) else skyColor(cover, temp, isDark)
                    if (sel) base else base.copy(alpha = 0.85f)
                }
            )
            }
        }
    }
}

/** La couleur d'une barre du ciel : gris selon la couverture nuageuse ; sans nuages, jaune (frais) à rouge (très chaud) selon la température. */
internal fun skyColor(cover: Double, tempC: Double, dark: Boolean): Color {
    if (cover >= 15.0) {
        val light = if (dark) Color(0xFFD3DCE2) else Color(0xFFB4C1CA)
        val heavy = if (dark) Color(0xFF55697A) else Color(0xFF4C6070)
        return androidx.compose.ui.graphics.lerp(light, heavy, ((cover - 15.0) / 85.0).coerceIn(0.0, 1.0).toFloat())
    }
    val yellow = Color(0xFFF7D23E); val orange = Color(0xFFF59A23); val red = Color(0xFFE5483B)
    return if (tempC <= 26.0) androidx.compose.ui.graphics.lerp(yellow, orange, ((tempC - 14.0) / 12.0).coerceIn(0.0, 1.0).toFloat())
    else androidx.compose.ui.graphics.lerp(orange, red, ((tempC - 26.0) / 10.0).coerceIn(0.0, 1.0).toFloat())
}

/** Une couleur pour l'indice UV : vert (faible), jaune (modéré), orange (fort), rouge (très fort et plus). */
internal fun uvColor(uv: Double): Color = when {
    uv < 3 -> Color(0xFF34C98A)
    uv < 6 -> Color(0xFFF2C94C)
    uv < 8 -> Color(0xFFF2994A)
    else -> Color(0xFFEB5757)
}

/** Un petit nuage : la couverture nuageuse. */
@Composable
private fun CloudCoverIcon(size: androidx.compose.ui.unit.Dp, tint: Color) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        drawCircle(tint, w * 0.20f, Offset(w * 0.32f, h * 0.62f))
        drawCircle(tint, w * 0.26f, Offset(w * 0.52f, h * 0.48f))
        drawCircle(tint, w * 0.20f, Offset(w * 0.74f, h * 0.62f))
        drawRoundRect(tint, Offset(w * 0.18f, h * 0.58f), Size(w * 0.68f, h * 0.24f), CornerRadius(h * 0.12f))
    }
}

/** Un petit soleil à rayons : l'indice UV. */
@Composable
private fun UvIcon(size: androidx.compose.ui.unit.Dp, tint: Color) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width; val c = Offset(w / 2f, this.size.height / 2f)
        drawCircle(tint, w * 0.2f, c)
        for (i in 0 until 8) {
            val a = i * kotlin.math.PI / 4
            val dx = kotlin.math.cos(a).toFloat(); val dy = kotlin.math.sin(a).toFloat()
            drawLine(tint, Offset(c.x + dx * w * 0.32f, c.y + dy * w * 0.32f), Offset(c.x + dx * w * 0.46f, c.y + dy * w * 0.46f), w * 0.09f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        }
    }
}

/** La ligne d'infos : l'état du ciel et le ressenti en gros, puis l'eau, les nuages (ou la pluie en mm) et l'UV, chacun avec son icône. */
@Composable
private fun WeatherInfoLine(h: HourlyUiModel, onSurface: Color) {
    val raining = h.precipitationMm >= 0.05
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            WeatherIcon(SurfUnitsHelper.resolveRealWeatherEmoji(h), 20.dp)
            Text("${h.feelsLike}°", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = onSurface)
        }
        h.seaTemperature?.let {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                WaterTempIcon(17.dp, onSurface)
                Text("${it.roundToInt()}°", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = onSurface)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            CloudCoverIcon(17.dp, if (raining) Color(0xFF5AA9E6) else onSurface.copy(alpha = 0.6f))
            Text(
                if (raining) "${formatDecimal(h.precipitationMm, 1).replace('.', ',')} mm" else "${h.cloudCover} %",
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = onSurface
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            UvIcon(17.dp, uvColor(h.uvIndex))
            Text("UV ${h.uvIndex.roundToInt()}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = uvColor(h.uvIndex))
        }
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
