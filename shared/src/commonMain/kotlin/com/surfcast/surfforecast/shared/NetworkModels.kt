package com.surfcast.surfforecast.shared

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MarineResponse(val hourly: MarineHourly)

@Serializable
data class MarineHourly(
    val time: List<String>,
    @SerialName("wave_height") val waveHeight: List<Double?> = emptyList(),
    @SerialName("wave_period") val wavePeriod: List<Double?> = emptyList(),
    @SerialName("wave_direction") val waveDirection: List<Double?> = emptyList()
)

@Serializable
data class WeatherResponse(val hourly: WeatherHourly)

@Serializable
data class WeatherHourly(
    val time: List<String>,
    @SerialName("wind_speed_10m") val windSpeed: List<Double?> = emptyList(),
    @SerialName("wind_direction_10m") val windDirection: List<Double?> = emptyList(),
    @SerialName("weather_code") val weatherCode: List<Int?> = emptyList(),
    @SerialName("temperature_2m") val temperature: List<Double?> = emptyList()
)
