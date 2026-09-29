package com.surfcast.surfforecast

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
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

/**
 * Écran temporaire de l'étape 1 de la migration Compose Multiplatform : valide la chaîne
 * complète (données partagées + encart Houle identique à Android) sur iOS. Sera remplacé
 * par MainScreen une fois celui-ci déplacé dans le module partagé.
 */
@Composable
fun SurfLogApp() {
    val spot = remember { SurfDatabase.findSpotByName("Montalivet") ?: SurfDatabase.getAllSpots().first() }
    var hours by remember { mutableStateOf<List<HourlyUiModel>?>(null) }
    var selected by remember { mutableStateOf<HourlyUiModel?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(spot) {
        try {
            val today = nowLocalDateTime().date
            val forecast = SurfRepository().getHybridForecast(spot.latitude, spot.longitude, ForecastEngineConfig())
            val todayHours = forecast.hourly.filter { it.rawTime.date == today && it.rawTime.hour in 7..21 }
            hours = todayHours
            val nowHour = nowLocalDateTime().hour
            selected = todayHours.minByOrNull { kotlin.math.abs(it.rawTime.hour - nowHour) }
        } catch (e: kotlinx.coroutines.CancellationException) {
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
            val currentHours = hours
            val currentSelected = selected
            when {
                error != null -> Text(error ?: "", color = MaterialTheme.colorScheme.error, modifier = Modifier.align(Alignment.Center))
                currentHours == null || currentSelected == null -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                else -> Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                    Text(spot.name, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
                    SurfCardComponent(
                        selectedHour = currentSelected,
                        allHoursOfDay = currentHours,
                        onHourSelected = { selected = it },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                }
            }
        }
    }
}
