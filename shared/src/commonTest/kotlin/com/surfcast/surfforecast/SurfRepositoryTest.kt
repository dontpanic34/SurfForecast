package com.surfcast.surfforecast

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SurfRepositoryTest {

    private val today = LocalDate(2026, 1, 1)

    // Court terme MFWAM : houle séparée (swell) prioritaire sur le total, période de pic.
    private val shortMarine = """
        {"hourly":{"time":["2026-01-01T10:00"],
        "wave_height":[2.0],"wave_period":[8.0],"wave_direction":[250.0],
        "swell_wave_height":[1.5],"swell_wave_period":[10.0],"swell_wave_direction":[280.0],
        "swell_wave_peak_period":[12.0],"wind_wave_height":[0.4],"wind_wave_direction":[200.0],
        "wind_wave_peak_period":[4.0]}}
    """.trimIndent()

    // Long terme ECMWF : pas de champs swell -> repli sur le total ; J+3 hors court terme.
    private val longMarine = """
        {"hourly":{"time":["2026-01-01T10:00","2026-01-04T10:00"],
        "wave_height":[9.9,1.1],"wave_period":[9.9,11.0],"wave_direction":[9.9,300.0]}}
    """.trimIndent()

    // Court terme AROME : clés suffixées par le modèle, comme le renvoie parfois l'API.
    private val shortWeather = """
        {"hourly":{"time":["2026-01-01T10:00"],
        "temperature_2m_meteofrance_arome_france":[14.6],
        "weather_code_meteofrance_arome_france":[3],
        "wind_speed_10m_meteofrance_arome_france":[12.4],
        "wind_direction_10m_meteofrance_arome_france":[90.0],
        "cloudcover_meteofrance_arome_france":[80],
        "apparent_temperature_meteofrance_arome_france":[12.2]}}
    """.trimIndent()

    private val longWeather = """
        {"hourly":{"time":["2026-01-01T10:00","2026-01-04T10:00"],
        "temperature_2m":[99.0,17.4],"weather_code":[99,1],"wind_speed_10m":[99.0,20.6],
        "wind_direction_10m":[0.0,270.0],"cloudcover":[0,10],"apparent_temperature":[99.0,null]},
        "daily":{"time":["2026-01-01"],"sunrise":["2026-01-01T08:40"],"sunset":["2026-01-01T17:25"]}}
    """.trimIndent()

    private fun MockRequestHandleScope.respondJson(body: String) =
        respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))

    private fun repository(engine: MockEngine) = SurfRepository(
        HttpClient(engine) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
    )

    // AROME HD indisponible par défaut : la prévision doit retomber sur AROME standard.
    private val openMeteoEngine = MockEngine { request ->
        val model = request.url.parameters["models"]
        if (model == "ncep_gfswave016") return@MockEngine respondError(HttpStatusCode.InternalServerError)
        if (model == "meteofrance_arome_france_hd") return@MockEngine respondError(HttpStatusCode.InternalServerError)
        val body = when (request.url.host) {
            "marine-api.open-meteo.com" -> if (model == "meteofrance_wave") shortMarine else longMarine
            else -> if (model == "meteofrance_arome_france") shortWeather else longWeather
        }
        respondJson(body)
    }

    @Test
    fun aromeHdWindWinsInShortTermAndMissingHoursFallBack() = runTest {
        // HD : vent seulement, valeur nulle pour l'unique heure -> repli sur AROME standard
        // pour cette heure ; puis un second cas où HD a une valeur et passe devant.
        fun engine(hdSpeed: String) = MockEngine { request ->
            val model = request.url.parameters["models"]
            if (model == "ncep_gfswave016") return@MockEngine respondError(HttpStatusCode.InternalServerError)
            val body = when {
                request.url.host == "marine-api.open-meteo.com" -> if (model == "meteofrance_wave") shortMarine else longMarine
                model == "meteofrance_arome_france_hd" ->
                    """{"hourly":{"time":["2026-01-01T10:00"],"wind_speed_10m":[$hdSpeed],"wind_direction_10m":[180.0]}}"""
                model == "meteofrance_arome_france" -> shortWeather
                else -> longWeather
            }
            respondJson(body)
        }
        val withHd = repository(engine("3.2")).getHybridForecast(45.38, -1.16, ForecastEngineConfig(), today).hourly[0]
        assertEquals(3, withHd.windSpeedKmh)
        assertEquals("S", withHd.windDirectionStr)
        assertEquals("AROME HD", withHd.windSource)

        val hdNull = repository(engine("null")).getHybridForecast(45.38, -1.16, ForecastEngineConfig(), today).hourly[0]
        assertEquals(12, hdNull.windSpeedKmh)
        assertEquals("AROME", hdNull.windSource)
    }

    @Test
    fun shortTermUsesAromeMfwamAndLongTermUsesEcmwf() = runTest {
        val result = repository(openMeteoEngine).getHybridForecast(45.38, -1.16, ForecastEngineConfig(), today)

        assertEquals(2, result.hourly.size)

        val shortTerm = result.hourly[0]
        assertEquals(LocalDateTime(2026, 1, 1, 10, 0), shortTerm.rawTime)
        assertEquals("10:00", shortTerm.timeFormatted)
        assertEquals(1.5, shortTerm.waveHeight)
        assertEquals(12.0, shortTerm.wavePeriod)
        assertEquals(280f, shortTerm.waveDirection)
        assertEquals(0.4, shortTerm.windWaveHeight)
        assertEquals(4.0, shortTerm.windWavePeriod)
        assertEquals(calculateWaveEnergyReal(1.5, 12.0), shortTerm.energyKj)
        assertEquals(12, shortTerm.windSpeedKmh)
        assertEquals("E", shortTerm.windDirectionStr)
        assertEquals("AROME", shortTerm.windSource)
        assertEquals(15, shortTerm.temperature)
        assertEquals(12, shortTerm.feelsLike)
        assertEquals(3, shortTerm.weatherCode)
        assertEquals(80, shortTerm.cloudCover)

        val longTerm = result.hourly[1]
        assertEquals(1.1, longTerm.waveHeight)
        // Seule la période MOYENNE (11 s) est donnée : convertie en période de pic (x 1,2).
        assertEquals(11.0 * 1.2, longTerm.wavePeriod, 1e-9)
        assertEquals(0.0, longTerm.windWaveHeight)
        assertEquals(21, longTerm.windSpeedKmh)
        assertEquals("O", longTerm.windDirectionStr)
        // Ressenti manquant -> température de l'air.
        assertEquals(17, longTerm.feelsLike)

        assertEquals(LocalTime(8, 40), result.dailySun.getValue(today).sunrise)
        assertEquals(LocalTime(17, 25), result.dailySun.getValue(today).sunset)
    }

    @Test
    fun totalPeakPeriodBeatsShortMeanSwellPeriod() = runTest {
        // ECMWF : houle à 6 s en moyenne, mais pic du total des vagues à 11 s -> on affiche 11 s.
        val ecmwf = """
            {"hourly":{"time":["2026-01-04T10:00"],"wave_height":[1.6],"wave_period":[6.0],"wave_peak_period":[11.0],
            "swell_wave_height":[1.4],"swell_wave_period":[6.2],"wave_direction":[300.0]}}
        """.trimIndent()
        val engine = MockEngine { request ->
            val model = request.url.parameters["models"]
            if (model == "ncep_gfswave016") return@MockEngine respondError(HttpStatusCode.InternalServerError)
            val body = when {
                request.url.host == "marine-api.open-meteo.com" -> if (model == "meteofrance_wave") shortMarine else ecmwf
                model == "meteofrance_arome_france" -> shortWeather
                else -> longWeather
            }
            respondJson(body)
        }
        val hourly = repository(engine).getHybridForecast(45.38, -1.16, ForecastEngineConfig(), today).hourly
        assertEquals(11.0, hourly.last().wavePeriod)
    }

    @Test
    fun referenceGfsWavePeakPeriodWinsWhenAvailable() = runTest {
        val gfs = """{"hourly":{"time":["2026-01-01T10:00","2026-01-04T10:00"],"wave_period":[9.0,null],"wave_peak_period":[11.0,13.0]}}"""
        val engine = MockEngine { request ->
            val model = request.url.parameters["models"]
            val body = when {
                request.url.host == "marine-api.open-meteo.com" -> when (model) {
                    "ncep_gfswave016" -> gfs
                    "meteofrance_wave" -> shortMarine
                    else -> longMarine
                }
                model == "meteofrance_arome_france" -> shortWeather
                else -> longWeather
            }
            respondJson(body)
        }
        val hourly = repository(engine).getHybridForecast(45.38, -1.16, ForecastEngineConfig(), today).hourly
        // Aujourd'hui : MFWAM (12 s) ; les jours suivants : GFS Wave.
        assertEquals(12.0, hourly[0].wavePeriod)
        assertEquals(13.0, hourly[1].wavePeriod)
        assertEquals(calculateWaveEnergyReal(1.5, 12.0), hourly[0].energyKj)
    }

    @Test
    fun openMeteoErrorKeepsAndroidMessage() = runTest {
        val engine = MockEngine {
            respond(
                """{"error":true,"reason":"The service is overloaded"}""",
                status = HttpStatusCode.ServiceUnavailable,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val error = assertFailsWith<IllegalStateException> {
            repository(engine).getHybridForecast(45.38, -1.16, ForecastEngineConfig(), today)
        }
        assertEquals("API Open-Meteo (503): The service is overloaded", error.message)
    }

    @Test
    fun tidesPickClosestSiteDaytimeTidesAndCoefFallback() = runTest {
        var requestedSite: String? = null
        val engine = MockEngine { request ->
            if (request.url.encodedPath.endsWith("/sites")) {
                respondJson(
                    """{"sites":[
                      {"site_id":"brest","latitude":48.38,"longitude":-4.49},
                      {"site_id":"cordouan","latitude":45.58,"longitude":-1.17}]}"""
                )
            } else {
                requestedSite = request.url.parameters["site"]
                respondJson(
                    """{"data":[{"date":"2026-01-01","extrema":[
                      {"type":"PM","time":"04:10","height":5.1,"coef":95},
                      {"type":"BM","time":"10:15","height":1.2},
                      {"type":"PM","time":"16:30","height":5.0},
                      {"type":"BM","time":"22:40","height":1.3}]}]}"""
                )
            }
        }

        val tides = repository(engine).getTides(45.38, -1.16, "2026-01-01", "2026-01-01")

        assertEquals("cordouan", requestedSite)
        val day = tides.dailyByDate.getValue(today)
        assertEquals("16:30", day.highTideTime)
        assertEquals("10:15", day.lowTideTime)
        assertEquals(95, day.coefficient)
        assertEquals(4, tides.rawByDate.getValue(today).size)
    }

    @Test
    fun tidesNetworkErrorGivesEmptyBundle() = runTest {
        val engine = MockEngine { respondError(HttpStatusCode.ServiceUnavailable) }
        val tides = repository(engine).getTides(45.38, -1.16, "2026-01-01", "2026-01-01")
        assertTrue(tides.dailyByDate.isEmpty())
        assertTrue(tides.rawByDate.isEmpty())
    }

    // --- Marées estimées hors de France ---

    @Test
    fun estimatedTidesFindHighAndLowWaterFromHourlySeaLevel() {
        // Marée semi-diurne simulée : période 12,42 h, pleine mer à 03:00 puis ~15:25, amplitude 1,5 m.
        val times = (0 until 48).map { LocalDateTime(2026, 1, 1 + it / 24, it % 24, 0) }
        val heights = times.indices.map { i ->
            1.5 * kotlin.math.cos(2 * kotlin.math.PI * (i - 3.0) / 12.42)
        }
        val days = tideDaysFromSeaLevel(times, heights, listOf(Triple("2026-01-01", "03:00", 95), Triple("2026-01-01", "16:00", 93)))
        val first = days.first { it.date == "2026-01-01" }.extrema!!
        val highs = first.filter { it.type == "PM" }
        val lows = first.filter { it.type == "BM" }
        assertEquals(2, highs.size)
        assertEquals("03:00", highs[0].time)
        assertTrue(highs[1].time in listOf("15:25", "15:24", "15:26"), "2e PM : ${highs[1].time}")
        assertEquals(2, lows.size)
        // Coefficient de la même date, PM le plus proche en heure.
        assertEquals(95, highs[0].coef)
        assertEquals(93, highs[1].coef)
        assertTrue(lows.all { it.coef == null })
        // Hauteurs relatives : la plus basse mer vaut 0, l'amplitude vaut ~3 m.
        assertTrue(lows.minOf { it.height } < 0.05)
        assertTrue(highs.maxOf { it.height } in 2.9..3.1)
    }

    @Test
    fun spotFarFromAnyFrenchSiteUsesEstimatedTidesWithFrenchCoefficient() = runTest {
        val engine = MockEngine { request ->
            when {
                request.url.encodedPath.endsWith("/sites") -> respondJson(
                    """{"sites":[{"site_id":"brest","site_name":"Brest","latitude":48.38,"longitude":-4.49},
                      {"site_id":"cordouan","latitude":45.58,"longitude":-1.17}]}"""
                )
                request.url.host == "marine-api.open-meteo.com" -> {
                    val times = (0 until 24).joinToString(",") { "\"2026-01-01T${it.toString().padStart(2, '0')}:00\"" }
                    val levels = (0 until 24).joinToString(",") { i ->
                        (1.2 * kotlin.math.cos(2 * kotlin.math.PI * (i - 5.0) / 12.42)).toString()
                    }
                    respondJson("""{"hourly":{"time":[$times],"sea_level_height_msl":[$levels]}}""")
                }
                else -> respondJson(
                    """{"data":[{"date":"2026-01-01","extrema":[
                      {"type":"PM","time":"05:00","height":6.0,"coef":88},
                      {"type":"BM","time":"11:10","height":1.0}]}]}"""
                )
            }
        }
        // Peniche (Portugal) : très loin de tout site français.
        val tides = repository(engine).getTides(39.355, -9.378, "2026-01-01", "2026-01-01")
        val day = tides.dailyByDate.getValue(today)
        // PM de jour (06:00-21:30) le plus tôt : la 2e de la journée simulée (~17h25).
        assertTrue(day.highTideTime!!.startsWith("17"), "PM : ${day.highTideTime}")
        assertEquals(88, day.coefficient)
    }

    @Test
    fun seaTemperatureComesFromItsOwnMarineRequestAndMayBeMissing() = runTest {
        val engine = MockEngine { request ->
            val model = request.url.parameters["models"]
            if (model == "ncep_gfswave016") return@MockEngine respondError(HttpStatusCode.InternalServerError)
            val body = when {
                request.url.host == "marine-api.open-meteo.com" && request.url.parameters["hourly"] == "sea_surface_temperature" ->
                    """{"hourly":{"time":["2026-01-01T10:00","2026-01-04T10:00"],"sea_surface_temperature":[14.4,null]}}"""
                request.url.host == "marine-api.open-meteo.com" -> if (model == "meteofrance_wave") shortMarine else longMarine
                model == "meteofrance_arome_france" -> shortWeather
                else -> longWeather
            }
            respondJson(body)
        }
        val hourly = repository(engine).getHybridForecast(45.38, -1.16, ForecastEngineConfig(), today).hourly
        assertEquals(14.4, hourly[0].seaTemperature)
        assertEquals(null, hourly[1].seaTemperature)
        // Sans réponse exploitable, la température reste inconnue (jamais inventée).
        val plain = repository(openMeteoEngine).getHybridForecast(45.38, -1.16, ForecastEngineConfig(), today).hourly
        assertTrue(plain.all { it.seaTemperature == null })
    }
}
