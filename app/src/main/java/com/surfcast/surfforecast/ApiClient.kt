package com.surfcast.surfforecast

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url

interface SurfApiService {
    @GET
    suspend fun searchSpot(
        @Url url: String = "https://geocoding-api.open-meteo.com/v1/search",
        @Query("name") name: String,
        @Query("language") language: String = "fr",
        @Query("count") count: Int = 10
    ): GeocodingResponse

    @GET
    suspend fun getMarineData(
        @Url url: String = "https://marine-api.open-meteo.com/v1/marine",
        @Query("latitude") lat: Double,
        @Query("longitude") lon: Double,
        @Query("hourly") hourly: String = "wave_height,wave_period,wave_direction,sea_surface_temperature",
        @Query("timezone") timezone: String = "Europe/Paris"
    ): MarineResponse

    @GET
    suspend fun getWeatherData(
        @Url url: String = "https://api.open-meteo.com/v1/forecast",
        @Query("latitude") lat: Double,
        @Query("longitude") lon: Double,
        @Query("hourly") hourly: String = "wind_speed_10m,wind_direction_10m,weather_code,temperature_2m,apparent_temperature,cloudcover",
        @Query("daily") daily: String = "sunrise,sunset",
        @Query("wind_speed_unit") windUnit: String = "kmh",
        @Query("timezone") timezone: String = "Europe/Paris"
    ): WeatherResponse

    @GET
    suspend fun getMareeSites(
        @Url url: String = "https://api-maree.fr/sites"
    ): MareeSitesResponse

    @GET
    suspend fun getTideExtrema(
        @Url url: String = "https://api-maree.fr/tide-extrema",
        @Query("site") site: String,
        @Query("from") from: String,
        @Query("to") to: String,
        @Query("tz") tz: String = "Europe/Paris",
        @Query("key") key: String
    ): MareeExtremaResponse
}

object RetrofitClient {
    private const val BASE_URL = "https://api.open-meteo.com/"

    val apiService: SurfApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SurfApiService::class.java)
    }
}