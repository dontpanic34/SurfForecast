package com.surfcast.surfforecast

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Trois jours simulés (houle, vent, ciel) pour les aperçus des réglages : les vraies cartes de l'appli sont dessinées avec,
 * donc ce qu'on règle se voit tel quel.
 */
private class PreviewData(val dates: List<LocalDate>, val grouped: Map<LocalDate, List<HourlyUiModel>>, val sun: Map<LocalDate, DailySunInfo>, val tides: Map<LocalDate, DailyTideInfo>)

private fun buildPreviewData(): PreviewData {
    val today = nowLocalDateTime().date
    val dates = (0 until 5).map { today.plus(it, DateTimeUnit.DAY) }
    val codes = listOf(2, 3, 61, 1, 0)
    val clouds = listOf(35, 70, 90, 20, 5)
    val grouped = dates.mapIndexed { d, date ->
        date to (0 until 24).map { h ->
            val i = d * 24 + h
            val height = 1.25 + 0.45 * sin(i / 9.0) + 0.12 * d
            val period = 10.0 + 1.5 * sin(i / 20.0)
            val wind = (6 + 8 * maxOf(0.0, sin((h - 6) / 24.0 * 2 * PI))).roundToInt()
            HourlyUiModel(
                timeFormatted = "${h}h", rawTime = LocalDateTime(date, LocalTime(h, 0)), waveHeight = height, wavePeriod = period,
                waveDirection = 285f, energyKj = calculateWaveEnergyReal(height, period), windSpeedKmh = wind,
                windDirectionStr = if (h < 12) "E" else "WSW", weatherCode = codes[d], temperature = 14 + h / 6, cloudCover = clouds[d],
                windGustKmh = wind + 6, seaTemperature = 17.0
            )
        }
    }.toMap()
    val sun = dates.associateWith { DailySunInfo(LocalTime(7, 55), LocalTime(19, 0)) }
    val tides = dates.associateWith {
        DailyTideInfo("13:16", "07:05", 78, listOf(MareeExtremum("BM", "07:05"), MareeExtremum("PM", "13:16"), MareeExtremum("BM", "19:30")))
    }
    return PreviewData(dates, grouped, sun, tides)
}

/** Affiche [content] à sa vraie taille puis la réduit à [scale], dans une hauteur qui suit le contenu réduit. */
@Composable
private fun Shrunk(scale: Float, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).layout { measurable, constraints ->
            val inner = measurable.measure(Constraints(minWidth = 0, maxWidth = (constraints.maxWidth / scale).roundToInt(), minHeight = 0, maxHeight = Constraints.Infinity))
            layout(constraints.maxWidth, (inner.height * scale).roundToInt()) {
                inner.placeWithLayer(0, 0) { scaleX = scale; scaleY = scale; transformOrigin = TransformOrigin(0f, 0f) }
            }
        }
    ) { content() }
}

/** Carte de la semaine, vraie, sur trois jours simulés : suit le niveau, l'unité du vent, les indicateurs et les icônes réglés. */
@Composable
fun WeekCardPreview(surferLevel: String, windUnit: String, windMode: String, density: Int, scale: Float = 0.6f) {
    val data = remember { buildPreviewData() }
    var selected by remember { mutableStateOf(data.dates.first()) }
    val colors = MaterialTheme.colorScheme
    val facing = 275
    val dates = data.dates
    val daylight = dates.map { daylightHoursFor(it, data.grouped, data.sun) }
    val stars = dates.mapIndexed { i, d ->
        dayQualityScore(daylight[i], facing, NEUTRAL_LEVEL, data.tides[d])?.let { starsForScore(it, 0) }
    }
    val bands = dates.mapIndexed { i, _ -> dayBandsInTimeOrder(daylight[i], facing, surferLevel, density) }
    Shrunk(scale) {
        WeeklyForecastCard(
            availableDates = dates, groupedByDate = data.grouped, selectedDate = selected, onSelectDate = { selected = it },
            dailyTides = data.tides,
            dailyPeriods = daylight.map { h -> h.maxByOrNull { it.waveHeight }?.wavePeriod?.roundToInt() ?: 10 },
            dailyHeights = daylight.map { h -> h.maxByOrNull { it.waveHeight }?.waveHeight ?: 0.0 },
            dailyFeelsLike = dates.map { 13 }, dailyWaterTemps = dates.map { 17 },
            dailyStars = stars, dailyBands = bands, dailySunInfo = data.sun, fixedMaxScale = 4f,
            selectedIndex = dates.indexOf(selected).coerceAtLeast(0), surferLevel = surferLevel, windUnit = windUnit,
            idealSwellDirection = facing, weeklyDensity = density, weeklyWindMode = windMode,
            primaryColor = colors.primary, surfaceColor = colors.surface, onSurfaceColor = colors.onSurface,
            isCollapsed = false, onToggleCollapse = {}
        )
    }
}

/** Déroulé du jour, vrai, sur un jour simulé : pour régler la place du graphique et l'échelle de houle. */
@Composable
fun DayCardPreview(surferLevel: String, windUnit: String, scale: Float = 0.6f) {
    val data = remember { buildPreviewData() }
    val date = data.dates.first()
    var hour by remember { mutableStateOf(data.grouped.getValue(date).first { it.rawTime.hour == 13 }) }
    Shrunk(scale) {
        DailyTimelineCard(
            selectedDate = date, groupedByDate = data.grouped, dailySunInfo = data.sun, dailyTides = data.tides,
            windUnit = windUnit, idealSwellDirection = 275, surferLevel = surferLevel,
            selectedHour = hour, onHourSelected = { hour = it }, isCollapsed = false, onToggleCollapse = {}
        )
    }
}
