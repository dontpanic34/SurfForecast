package com.surfcast.surfforecast.shared

import kotlinx.datetime.LocalDateTime

/**
 * Service de prevision minimal, appelable depuis Android (via le module :shared) et
 * depuis Swift (via le framework SharedCore). Version MVP : un seul modele Open-Meteo,
 * pas encore la fusion court-terme/long-terme d'AROME/ECMWF/MFWAM que fait
 * SurfRepository.getHybridForecast() cote Android — a porter dans une etape suivante.
 */
class SharedForecastService(
    private val api: OpenMeteoApi = OpenMeteoApi()
) {
    suspend fun getForecast(spot: SpotCoordinates): List<HourlyForecastPoint> {
        val marine = api.getMarine(spot.latitude, spot.longitude)
        val weather = api.getWeather(spot.latitude, spot.longitude)

        val marineByTime = marine.hourly.time.indices.associate { i ->
            marine.hourly.time[i] to Triple(
                marine.hourly.waveHeight.getOrNull(i) ?: 0.0,
                marine.hourly.wavePeriod.getOrNull(i) ?: 0.0,
                marine.hourly.waveDirection.getOrNull(i) ?: 0.0
            )
        }

        return weather.hourly.time.indices.mapNotNull { i ->
            val timeStr = weather.hourly.time[i]
            val marinePoint = marineByTime[timeStr] ?: return@mapNotNull null
            val (waveHeight, wavePeriod, waveDirection) = marinePoint

            HourlyForecastPoint(
                time = LocalDateTime.parse(timeStr),
                waveHeight = waveHeight,
                wavePeriod = wavePeriod,
                waveDirection = waveDirection,
                windSpeedKmh = weather.hourly.windSpeed.getOrNull(i) ?: 0.0,
                windDirectionDeg = weather.hourly.windDirection.getOrNull(i) ?: 0.0,
                temperature = weather.hourly.temperature.getOrNull(i) ?: 0.0,
                weatherCode = weather.hourly.weatherCode.getOrNull(i) ?: 0
            )
        }
    }
}
