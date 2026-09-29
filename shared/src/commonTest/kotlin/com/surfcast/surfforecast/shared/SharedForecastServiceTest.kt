package com.surfcast.surfforecast.shared

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class SharedForecastServiceTest {

    private val marineJson = """
        {"hourly":{"time":["2026-01-01T10:00","2026-01-01T11:00"],
        "wave_height":[1.2,1.3],"wave_period":[9.0,9.5],"wave_direction":[270.0,272.0]}}
    """.trimIndent()

    private val weatherJson = """
        {"hourly":{"time":["2026-01-01T10:00","2026-01-01T11:00"],
        "wind_speed_10m":[12.0,14.0],"wind_direction_10m":[90.0,95.0],
        "weather_code":[1,2],"temperature_2m":[18.0,18.5]}}
    """.trimIndent()

    @Test
    fun mergesMarineAndWeatherByTimestamp() = runTest {
        val mockEngine = MockEngine { request ->
            val body = if (request.url.host == "marine-api.open-meteo.com") marineJson else weatherJson
            respond(
                content = body,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        val service = SharedForecastService(OpenMeteoApi(client))

        val result = service.getForecast(SpotCoordinates(45.0, -1.2))

        assertEquals(2, result.size)
        assertEquals(LocalDateTime.parse("2026-01-01T10:00"), result[0].time)
        assertEquals(1.2, result[0].waveHeight)
        assertEquals(12.0, result[0].windSpeedKmh)
        assertEquals(2, result[1].weatherCode)
        assertEquals("01/01 11h", result[1].timeLabel)
    }
}
