package com.surfcast.surfforecast.shared

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SharedTideServiceTest {

    private val sitesJson = """
        {"sites":[
          {"site_id":"brest","site_name":"Brest","latitude":48.38,"longitude":-4.49},
          {"site_id":"cordouan","site_name":"Cordouan","latitude":45.58,"longitude":-1.17}
        ]}
    """.trimIndent()

    private val extremaJson = """
        {"site_id":"cordouan","data":[{"date":"2026-01-01","extrema":[
          {"type":"PM","time":"04:10","height":5.1,"coef":95},
          {"type":"BM","time":"10:15","height":1.2},
          {"type":"PM","time":"16:30","height":5.0},
          {"type":"BM","time":"22:40","height":1.3}
        ]}]}
    """.trimIndent()

    private fun clientFor(engine: MockEngine) = HttpClient(engine) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    @Test
    fun picksClosestSiteAndDaytimeTidesWithCoefFallback() = runTest {
        var requestedSite: String? = null
        val engine = MockEngine { request ->
            val body = if (request.url.encodedPath.endsWith("/sites")) {
                sitesJson
            } else {
                requestedSite = request.url.parameters["site"]
                extremaJson
            }
            respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val service = SharedTideService(clientFor(engine), "test-key")

        val tides = service.getTides(SpotCoordinates(45.38, -1.16), "2026-01-01", "2026-01-01")

        assertEquals("cordouan", requestedSite)
        assertEquals(1, tides.size)
        val day = tides.single()
        assertEquals("2026-01-01", day.dateIso)
        assertEquals("16:30", day.highTideTime)
        assertEquals("10:15", day.lowTideTime)
        assertEquals(95, day.coefficient)
        assertEquals(4, day.allExtrema.size)
    }

    @Test
    fun networkErrorGivesEmptyList() = runTest {
        val engine = MockEngine { respondError(HttpStatusCode.ServiceUnavailable) }
        val service = SharedTideService(clientFor(engine), "test-key")

        val tides = service.getTides(SpotCoordinates(45.38, -1.16), "2026-01-01", "2026-01-01")

        assertTrue(tides.isEmpty())
    }
}
