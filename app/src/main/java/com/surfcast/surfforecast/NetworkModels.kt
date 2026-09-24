package com.surfcast.surfforecast

import com.google.gson.annotations.SerializedName
import java.time.LocalDate
import java.time.LocalTime

data class DailyTideInfo(
    val highTideTime: String? = null,
    val lowTideTime: String? = null,
    val coefficient: Int? = null
)

// Tâche 3 : lever/coucher du soleil du jour, utilisé pour filtrer l'encart
// "Déroulé de la journée" aux seules heures d'ensoleillement.
data class DailySunInfo(
    val sunrise: LocalTime,
    val sunset: LocalTime
)

data class HybridForecastResult(
    val hourly: List<HourlyUiModel>,
    val dailySun: Map<LocalDate, DailySunInfo>
)

data class DailyWeatherSummary(
    val avgAirTemp: Int = 0,
    val avgFeelsLike: Int = 0,
    val avgWaterTemp: Int = 0
)

// --- OPEN-METEO GEOCODING ---
data class GeocodingResponse(
    val results: List<GeocodingResult>?
)

data class GeocodingResult(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val admin1: String?
)

// --- OPEN-METEO MARINE ---
data class MarineResponse(
    val hourly: MarineHourly
)

data class MarineHourly(
    val time: List<String>,
    @SerializedName("wave_height") val waveHeight: List<Double>,
    @SerializedName("wave_period") val wavePeriod: List<Double>,
    @SerializedName("wave_direction") val waveDirection: List<Double>? = null,
    @SerializedName("sea_surface_temperature") val seaSurfaceTemperature: List<Double>? = null
)

// --- OPEN-METEO WEATHER ---
data class WeatherResponse(
    val hourly: WeatherHourly,
    val daily: WeatherDaily
)

data class WeatherHourly(
    val time: List<String>,
    @SerializedName("wind_speed_10m") val windSpeed: List<Double>,
    @SerializedName("wind_direction_10m") val windDirection: List<Double>,
    @SerializedName("weather_code") val weatherCode: List<Int>,
    @SerializedName("temperature_2m") val temperature: List<Double>,
    @SerializedName("apparent_temperature") val apparentTemperature: List<Double>? = null,
    @SerializedName("cloudcover") val cloudCover: List<Int>? = null
)

data class WeatherDaily(
    val time: List<String>,
    val sunrise: List<String>,
    val sunset: List<String>
)

// --- API-MAREE.FR ---
data class MareeSite(
    @SerializedName("site_id") val siteId: String,
    @SerializedName("site_name") val siteName: String,
    val latitude: Double,
    val longitude: Double
)

data class MareeSitesResponse(
    val sites: List<MareeSite>? = null
)

data class MareeExtremum(
    val type: String,
    val time: String,
    val height: Double,
    val coef: Int? = null
)

data class MareeDayData(
    val date: String,
    val extrema: List<MareeExtremum>? = null
)

data class MareeExtremaResponse(
    @SerializedName("site_id") val siteId: String? = null,
    @SerializedName("site_name") val siteName: String? = null,
    val data: List<MareeDayData>? = null
)
