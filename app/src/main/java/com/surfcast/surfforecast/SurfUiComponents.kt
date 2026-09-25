package com.surfcast.surfforecast

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDateTime
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

data class HourlyUiModel(
    val timeFormatted: String,
    val rawTime: LocalDateTime,
    val waveHeight: Double,
    val wavePeriod: Double,
    val waveDirection: Float,
    val energyKj: Int,
    val windSpeedKmh: Int,
    val windDirectionStr: String,
    val weatherCode: Int,
    val temperature: Int,
    val cloudCover: Int = 0,
    val feelsLike: Int = temperature
)

@Composable
fun WeatherCanvas(
    dayData: List<HourlyUiModel>,
    modifier: Modifier = Modifier
) {
    val morning = dayData.minByOrNull { abs(it.rawTime.hour - 9) }
    val midday = dayData.minByOrNull { abs(it.rawTime.hour - 13) }
    val evening = dayData.minByOrNull { abs(it.rawTime.hour - 18) }

    val slots = listOfNotNull(morning, midday, evening)

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (slots.isNotEmpty()) {
                slots.forEach { slot ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = getWeatherEmoji(slot.weatherCode),
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                Text(
                    text = "☀️",
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun HourlyForecastRow(hourlyData: HourlyUiModel) {
    val formattedHeight = String.format(Locale.US, "%.1f", hourlyData.waveHeight)
    val roundedPeriod = hourlyData.wavePeriod.roundToInt()
    val windColor = getSurfWindColor(hourlyData.windDirectionStr, hourlyData.windSpeedKmh)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = hourlyData.timeFormatted,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            modifier = Modifier.weight(1f)
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1.2f)
        ) {
            Text(
                text = "${formattedHeight}m",
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0277BD)
            )
            Text(
                text = "${hourlyData.energyKj}kJ",
                fontSize = 12.sp,
                color = Color.Gray
            )
        }

        Text(
            text = "${roundedPeriod}s",
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = getWeatherEmoji(hourlyData.weatherCode),
                fontSize = 20.sp
            )
            Text(
                text = "${hourlyData.temperature}°C",
                fontSize = 12.sp,
                color = Color.DarkGray
            )
        }

        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.weight(1.2f)
        ) {
            Text(
                text = "${hourlyData.windSpeedKmh} km/h",
                fontWeight = FontWeight.Bold,
                color = windColor
            )
            Text(
                text = hourlyData.windDirectionStr,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = windColor
            )
        }
    }
}

fun getSurfWindColor(direction: String, speedKmh: Int): Color {
    val isOffshore = direction in listOf("E", "ENE", "ESE", "SE", "NE")
    return when {
        isOffshore -> Color(0xFFFFD600)
        speedKmh <= 19 -> Color(0xFFFF9800)
        else -> Color(0xFFE53935)
    }
}

fun getWeatherEmoji(code: Int): String {
    return when (code) {
        0 -> "☀️"
        1, 2, 3 -> "⛅"
        45, 48 -> "🌫️"
        51, 53, 55, 56, 57, 61, 63, 65, 66, 67 -> "🌧️"
        71, 73, 75, 77, 85, 86 -> "❄️"
        80, 81, 82 -> "🌦️"
        95, 96, 99 -> "⛈️"
        else -> "❓"
    }
}