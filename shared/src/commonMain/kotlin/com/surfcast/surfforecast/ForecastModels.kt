package com.surfcast.surfforecast

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

// Portage multiplateforme des modèles de app/ (mêmes noms, mêmes champs) :
// java.time remplacé par kotlinx-datetime pour compiler aussi sur iOS.

data class HourlyUiModel(
    val timeFormatted: String,
    val rawTime: LocalDateTime,
    val waveHeight: Double,
    val wavePeriod: Double,
    val waveDirection: Float,
    // Mer de vent (clapot) : 0 = pas de clapot (valeur légitime, pas une donnée manquante).
    val windWaveHeight: Double = 0.0,
    val windWavePeriod: Double = 0.0,
    val windWaveDirection: Float = 0f,
    val energyKj: Int,
    val windSpeedKmh: Int,
    val windDirectionStr: String,
    val weatherCode: Int,
    val temperature: Int,
    val cloudCover: Int = 0,
    val feelsLike: Int = temperature,
    // Modèle d'où vient le vent de cette heure ("AROME HD", "AROME", "ECMWF_IFS"...,
    // ou WIND_SOURCE_MISSING) : sert au journal des prévisions. Vide = inconnu.
    val windSource: String = "",
    // Rafales (km/h) : par défaut égales au vent moyen quand la source n'en donne pas.
    val windGustKmh: Int = windSpeedKmh
)

data class DailyTideInfo(
    val highTideTime: String? = null,
    val lowTideTime: String? = null,
    val coefficient: Int? = null
)

data class DailySunInfo(
    val sunrise: LocalTime,
    val sunset: LocalTime
)

data class HybridForecastResult(
    val hourly: List<HourlyUiModel>,
    val dailySun: Map<LocalDate, DailySunInfo>
)

enum class WeatherModel(
    val apiParam: String,
    val displayName: String,
    val resolution: String,
    val description: String
) {
    AROME(
        apiParam = "meteofrance_arome_france",
        displayName = "Météo-France AROME",
        resolution = "1.3 km",
        description = "Ultra-précis sur les thermiques et brises côtières (J0-J1)."
    ),
    ECMWF_IFS(
        apiParam = "ecmwf_ifs025",
        displayName = "ECMWF IFS Europe",
        resolution = "9 km",
        description = "Vue d'ensemble globale européenne, très stable à moyen terme (J2-J6)."
    ),
    ARPEGE(
        apiParam = "meteofrance_arpege_europe",
        displayName = "Météo-France ARPEGE",
        resolution = "11 km",
        description = "Alternative régionale Météo-France."
    )
}

enum class WaveModel(
    val apiParam: String,
    val displayName: String,
    val resolution: String,
    val description: String
) {
    MFWAM(
        apiParam = "meteofrance_wave",
        displayName = "Météo-France MFWAM",
        resolution = "8 km",
        description = "Spécialiste de la bathymétrie côtière et des bancs de sable français."
    ),
    ECMWF_WAM(
        apiParam = "ecmwf_wave",
        displayName = "ECMWF WAM Europe",
        resolution = "14 km",
        description = "Traqueur de grand large, référence sur la houle longue et l'énergie océanique."
    )
}

data class ForecastEngineConfig(
    val shortTermWeather: WeatherModel = WeatherModel.AROME,
    val shortTermWave: WaveModel = WaveModel.MFWAM,
    val longTermWeather: WeatherModel = WeatherModel.ECMWF_IFS,
    val longTermWave: WaveModel = WaveModel.ECMWF_WAM
)
