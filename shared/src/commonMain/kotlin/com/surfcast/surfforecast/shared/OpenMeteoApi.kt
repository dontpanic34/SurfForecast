package com.surfcast.surfforecast.shared

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Client Open-Meteo minimal, partagé Android/iOS via Ktor (moteur résolu
 * automatiquement par plateforme : OkHttp sur Android, Darwin sur iOS).
 * Ne couvre pour l'instant que ce qu'il faut pour un premier écran (houle + météo,
 * modèle par défaut Open-Meteo) — pas encore le choix de modèle AROME/ECMWF/MFWAM
 * ni les marées (api-maree.fr), qui restent à porter depuis SurfRepository.kt.
 */
class OpenMeteoApi(private val httpClient: HttpClient) {
    // Swift ne voit pas les valeurs par défaut Kotlin : constructeur explicite sans argument.
    constructor() : this(
        HttpClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    )

    suspend fun getMarine(lat: Double, lon: Double): MarineResponse =
        httpClient.get("https://marine-api.open-meteo.com/v1/marine") {
            parameter("latitude", lat)
            parameter("longitude", lon)
            parameter("hourly", "wave_height,wave_period,wave_direction")
            parameter("timezone", "Europe/Paris")
        }.body()

    suspend fun getWeather(lat: Double, lon: Double): WeatherResponse =
        httpClient.get("https://api.open-meteo.com/v1/forecast") {
            parameter("latitude", lat)
            parameter("longitude", lon)
            parameter("hourly", "wind_speed_10m,wind_direction_10m,weather_code,temperature_2m")
            parameter("wind_speed_unit", "kmh")
            parameter("timezone", "Europe/Paris")
        }.body()
}
