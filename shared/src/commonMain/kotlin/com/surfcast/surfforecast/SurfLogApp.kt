package com.surfcast.surfforecast

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.surfcast.surfforecast.ui.theme.SurfForecastTheme
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * Écran temporaire de la migration Compose Multiplatform : affiche sur iOS les écrans déjà
 * portés depuis Android (déroulé de la journée, encarts, liste horaire), avec les vraies
 * données. Sera remplacé par MainScreen une fois celui-ci déplacé dans le module partagé.
 */
@Composable
fun SurfLogApp() {
    val spot = remember { SurfDatabase.findSpotByName("Montalivet") ?: SurfDatabase.getAllSpots().first() }
    var forecast by remember { mutableStateOf<HybridForecastResult?>(null) }
    var tides by remember { mutableStateOf<Map<LocalDate, DailyTideInfo>>(emptyMap()) }
    var selected by remember { mutableStateOf<HourlyUiModel?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var timelineCollapsed by remember { mutableStateOf(false) }

    LaunchedEffect(spot) {
        try {
            val repository = SurfRepository()
            val result = repository.getHybridForecast(spot.latitude, spot.longitude, ForecastEngineConfig())
            val today = nowLocalDateTime().date
            tides = repository.getTides(
                spot.latitude,
                spot.longitude,
                today.toString(),
                today.plus(7, DateTimeUnit.DAY).toString()
            ).dailyByDate
            val now = nowLocalDateTime()
            selected = result.hourly.firstOrNull { it.rawTime.date == now.date && it.rawTime.hour == now.hour }
                ?: result.hourly.firstOrNull()
            forecast = result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Erreur de connexion."
        }
    }

    SurfForecastTheme(useDarkTheme = true) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .safeDrawingPadding()
        ) {
            val currentForecast = forecast
            val currentSelected = selected
            when {
                error != null -> Text(error ?: "", color = MaterialTheme.colorScheme.error, modifier = Modifier.align(Alignment.Center))
                currentForecast == null || currentSelected == null -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                else -> {
                    val selectedDate = currentSelected.rawTime.date
                    val groupedByDate = currentForecast.hourly.groupBy { it.rawTime.date }
                    val hoursOfDay = groupedByDate[selectedDate].orEmpty()
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(spot.name, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
                        DailyTimelineCard(
                            selectedDate = selectedDate,
                            groupedByDate = groupedByDate,
                            dailySunInfo = currentForecast.dailySun,
                            dailyTides = tides,
                            windUnit = "kmh",
                            idealSwellDirection = spot.idealSwellDirection,
                            surferLevel = "intermediate",
                            selectedHour = currentSelected,
                            onHourSelected = { selected = it },
                            isCollapsed = timelineCollapsed,
                            onToggleCollapse = { timelineCollapsed = !timelineCollapsed }
                        )
                        SurfCardComponent(
                            selectedHour = currentSelected,
                            allHoursOfDay = hoursOfDay,
                            onHourSelected = { selected = it }
                        )
                        WindCardComponent(
                            selectedHour = currentSelected,
                            allHoursOfDay = hoursOfDay,
                            windUnit = "kmh",
                            onHourSelected = { selected = it }
                        )
                        WindSeaCardComponent(
                            selectedHour = currentSelected,
                            allHoursOfDay = hoursOfDay,
                            onHourSelected = { selected = it }
                        )
                        WeatherCardComponent(
                            selectedHour = currentSelected,
                            allHoursOfDay = hoursOfDay,
                            onHourSelected = { selected = it }
                        )
                        hoursOfDay.forEach { hourly ->
                            HourlyForecastRow(
                                hourlyData = hourly,
                                windUnit = "kmh",
                                dailyTideInfo = tides[selectedDate],
                                isSelected = hourly.rawTime == currentSelected.rawTime
                            )
                        }
                    }
                }
            }
        }
    }
}
