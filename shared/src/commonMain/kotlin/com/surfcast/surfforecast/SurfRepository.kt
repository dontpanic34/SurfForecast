package com.surfcast.surfforecast

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.roundToInt

@Serializable
data class MareeSite(
    @SerialName("site_id") val siteId: String,
    @SerialName("site_name") val siteName: String = "",
    val latitude: Double,
    val longitude: Double
)

@Serializable
data class MareeSitesResponse(val sites: List<MareeSite>? = null)

@Serializable
data class MareeExtremum(
    val type: String,
    val time: String,
    val height: Double = 0.0,
    val coef: Int? = null
)

@Serializable
data class MareeDayData(val date: String, val extrema: List<MareeExtremum>? = null)

@Serializable
data class MareeExtremaResponse(
    @SerialName("site_id") val siteId: String? = null,
    val data: List<MareeDayData>? = null
)

/**
 * Portage multiplateforme de SurfRepository (app/) : même fusion court terme / long terme,
 * mêmes replis, mêmes calculs. HttpURLConnection + org.json (JVM) remplacés par Ktor +
 * kotlinx.serialization ; le JSON reste lu "à la main" pour garder à l'identique les
 * replis sur clés suffixées par modèle et valeurs nulles.
 */
class SurfRepository(private val httpClient: HttpClient) {

    constructor() : this(defaultHttpClient())

    private val apiMareeToken = "0093ca14ffeffadcf739be7cc77f4738"

    private suspend fun httpGet(url: String): JsonObject {
        val response = httpClient.get(url)
        val text = response.bodyAsText()
        val code = response.status.value
        if (code !in 200..299) {
            val reason = runCatching {
                Json.parseToJsonElement(text).jsonObject["reason"]?.jsonPrimitive?.contentOrNull
            }.getOrNull() ?: text
            throw IllegalStateException("API Open-Meteo ($code): $reason")
        }
        return Json.parseToJsonElement(text).jsonObject
    }

    private class RawMarineHourly(
        val times: List<LocalDateTime>,
        val waveHeights: List<Double>,
        val wavePeriods: List<Double>,
        val waveDirections: List<Float>,
        // Mer de vent : 0 est une valeur légitime (pas de clapot), pas de repli sur le total.
        val windWaveHeights: List<Double>,
        val windWavePeriods: List<Double>,
        val windWaveDirections: List<Float>
    )

    private class RawWeatherHourly(
        val times: List<LocalDateTime>,
        val temperatures: List<Double>,
        val weatherCodes: List<Int>,
        val windSpeeds: List<Double>,
        val windDirections: List<Double>,
        val cloudCovers: List<Int>,
        val apparentTemperatures: List<Double>,
        val sunriseByDate: Map<LocalDate, LocalTime>,
        val sunsetByDate: Map<LocalDate, LocalTime>
    )

    private suspend fun fetchMarineBlock(lat: Double, lon: Double, waveModel: WaveModel, days: Int): RawMarineHourly {
        val url = "https://marine-api.open-meteo.com/v1/marine?" +
            "latitude=$lat&longitude=$lon" +
            "&hourly=wave_height,wave_period,wave_direction,swell_wave_height,swell_wave_period,swell_wave_direction,swell_wave_peak_period,wind_wave_height,wind_wave_direction,wind_wave_peak_period" +
            "&models=${waveModel.apiParam}" +
            "&forecast_days=$days" +
            "&timezone=auto"

        val hourly = httpGet(url).getValue("hourly").jsonObject
        val timeArr = hourly.getValue("time").jsonArray

        val swellHArr = hourly.optArray("swell_wave_height")
        val swellPArr = hourly.optArray("swell_wave_period")
        val swellPeakArr = hourly.optArray("swell_wave_peak_period")
        val windPeakArr = hourly.optArray("wind_wave_peak_period")
        val swellDArr = hourly.optArray("swell_wave_direction")
        val windWaveHArr = hourly.optArray("wind_wave_height")
        val windWaveDArr = hourly.optArray("wind_wave_direction")
        val waveHeightArr = hourly.getValue("wave_height").jsonArray
        val wavePeriodArr = hourly.getValue("wave_period").jsonArray
        val waveDirArr = hourly.getValue("wave_direction").jsonArray

        val times = mutableListOf<LocalDateTime>()
        val waveHeights = mutableListOf<Double>()
        val wavePeriods = mutableListOf<Double>()
        val waveDirs = mutableListOf<Float>()
        val windWaveHeights = mutableListOf<Double>()
        val windWavePeriods = mutableListOf<Double>()
        val windWaveDirs = mutableListOf<Float>()

        for (i in timeArr.indices) {
            times.add(LocalDateTime.parse(timeArr[i].jsonPrimitive.content))

            val swellH = swellHArr.doubleAt(i) ?: Double.NaN
            val swellP = swellPArr.doubleAt(i) ?: Double.NaN
            val swellPeak = swellPeakArr.doubleAt(i) ?: Double.NaN
            val windPeak = windPeakArr.doubleAt(i) ?: Double.NaN
            val swellD = swellDArr.doubleAt(i)?.toFloat() ?: Float.NaN

            val totalH = waveHeightArr.doubleAt(i) ?: 0.0
            val totalP = wavePeriodArr.doubleAt(i) ?: 0.0
            val totalD = waveDirArr.doubleAt(i)?.toFloat() ?: 0f

            val finalH = if (!swellH.isNaN() && swellH > 0.0) swellH else totalH
            // Période de PIC (train de houle le plus énergétique) plutôt que la moyenne.
            val finalP = when {
                !swellPeak.isNaN() && swellPeak > 0.0 -> swellPeak
                !windPeak.isNaN() && windPeak > 0.0 -> windPeak
                !swellP.isNaN() && swellP > 0.0 -> swellP
                else -> totalP
            }
            val finalD = if (!swellD.isNaN()) swellD else totalD

            waveHeights.add(finalH)
            wavePeriods.add(finalP)
            waveDirs.add(finalD)

            windWaveHeights.add(windWaveHArr.doubleAt(i) ?: 0.0)
            windWavePeriods.add(if (!windPeak.isNaN()) windPeak else 0.0)
            windWaveDirs.add(windWaveDArr.doubleAt(i)?.toFloat() ?: 0f)
        }

        return RawMarineHourly(times, waveHeights, wavePeriods, waveDirs, windWaveHeights, windWavePeriods, windWaveDirs)
    }

    private suspend fun fetchWeatherBlock(
        lat: Double,
        lon: Double,
        weatherModel: WeatherModel,
        days: Int,
        includeDailySun: Boolean = false
    ): RawWeatherHourly {
        val safeDays = if (weatherModel == WeatherModel.AROME) days.coerceAtMost(2) else days
        val modelParam = if (weatherModel.apiParam.isNotEmpty()) "&models=${weatherModel.apiParam}" else ""
        val dailyParam = if (includeDailySun) "&daily=sunrise,sunset" else ""

        val url = "https://api.open-meteo.com/v1/forecast?" +
            "latitude=$lat&longitude=$lon" +
            "&hourly=temperature_2m,weather_code,wind_speed_10m,wind_direction_10m,cloudcover,apparent_temperature" +
            modelParam +
            dailyParam +
            "&forecast_days=$safeDays" +
            "&timezone=auto"

        val root = httpGet(url)
        val hourly = root.getValue("hourly").jsonObject
        val timeArr = hourly.getValue("time").jsonArray

        // Certains modèles suffixent les clés (ex : temperature_2m_meteofrance_arome_france).
        fun getArray(baseKey: String): JsonArray {
            hourly[baseKey]?.let { return it.jsonArray }
            hourly["${baseKey}_${weatherModel.apiParam}"]?.let { return it.jsonArray }
            hourly.entries.firstOrNull { it.key.startsWith(baseKey) }?.let { return it.value.jsonArray }
            return JsonArray(emptyList())
        }

        val tempArr = getArray("temperature_2m")
        val codeArr = getArray("weather_code")
        val windSpeedArr = getArray("wind_speed_10m")
        val windDirArr = getArray("wind_direction_10m")
        val cloudArr = getArray("cloudcover")
        val apparentArr = getArray("apparent_temperature")

        val times = mutableListOf<LocalDateTime>()
        val temps = mutableListOf<Double>()
        val codes = mutableListOf<Int>()
        val windSpeeds = mutableListOf<Double>()
        val windDirs = mutableListOf<Double>()
        val clouds = mutableListOf<Int>()
        val apparentTemps = mutableListOf<Double>()

        for (i in timeArr.indices) {
            times.add(LocalDateTime.parse(timeArr[i].jsonPrimitive.content))
            val temp = tempArr.doubleAt(i) ?: 20.0
            temps.add(temp)
            codes.add(codeArr.doubleAt(i)?.toInt() ?: 0)
            windSpeeds.add(windSpeedArr.doubleAt(i) ?: 10.0)
            windDirs.add(windDirArr.doubleAt(i) ?: 0.0)
            clouds.add(cloudArr.doubleAt(i)?.toInt() ?: 0)
            // Repli sur la température de l'air si le ressenti manque pour cette heure.
            apparentTemps.add(apparentArr.doubleAt(i) ?: temp)
        }

        val sunriseByDate = mutableMapOf<LocalDate, LocalTime>()
        val sunsetByDate = mutableMapOf<LocalDate, LocalTime>()
        if (includeDailySun) {
            val daily = root["daily"] as? JsonObject
            val dTimeArr = daily?.optArray("time")
            val sunriseArr = daily?.optArray("sunrise")
            val sunsetArr = daily?.optArray("sunset")
            if (dTimeArr != null && sunriseArr != null && sunsetArr != null) {
                for (i in dTimeArr.indices) {
                    try {
                        val date = LocalDate.parse(dTimeArr[i].jsonPrimitive.content)
                        sunriseByDate[date] = LocalDateTime.parse(sunriseArr[i].jsonPrimitive.content).time
                        sunsetByDate[date] = LocalDateTime.parse(sunsetArr[i].jsonPrimitive.content).time
                    } catch (e: IllegalArgumentException) {
                        // Jour ignoré, le repli 7h-21h prend le relais en amont.
                    } catch (e: IndexOutOfBoundsException) {
                        // Idem si les tableaux daily n'ont pas la même longueur.
                    }
                }
            }
        }

        return RawWeatherHourly(times, temps, codes, windSpeeds, windDirs, clouds, apparentTemps, sunriseByDate, sunsetByDate)
    }

    /**
     * `today` est injectable pour les tests ; par défaut, la date du jour du téléphone
     * (comme LocalDate.now() côté Android).
     */
    @OptIn(ExperimentalTime::class)
    suspend fun getHybridForecast(
        lat: Double,
        lon: Double,
        config: ForecastEngineConfig,
        today: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())
    ): HybridForecastResult = withContext(Dispatchers.Default) {
            val cutoffDate = today.plus(2, DateTimeUnit.DAY)

            // Court terme (J0-J1) : AROME + MFWAM, plus précis sur la côte française.
            // Long terme : ECMWF prend le relais. Les 4 appels partent en parallèle.
            val shortMarineDeferred = async { fetchMarineBlock(lat, lon, config.shortTermWave, 2) }
            val longMarineDeferred = async { fetchMarineBlock(lat, lon, config.longTermWave, 7) }
            val shortWeatherDeferred = async { fetchWeatherBlock(lat, lon, config.shortTermWeather, 2) }
            // Lever/coucher du soleil : donnée astronomique, demandée une seule fois (appel 7 jours).
            val longWeatherDeferred = async {
                fetchWeatherBlock(lat, lon, config.longTermWeather, 7, includeDailySun = true)
            }

            val shortMarine = shortMarineDeferred.await()
            val longMarine = longMarineDeferred.await()
            val shortWeather = shortWeatherDeferred.await()
            val longWeather = longWeatherDeferred.await()

            val marineMapShort = shortMarine.times.indices.associateBy { shortMarine.times[it] }
            val weatherMapShort = shortWeather.times.indices.associateBy { shortWeather.times[it] }
            val weatherMapLong = longWeather.times.indices.associateBy { longWeather.times[it] }

            val resultList = mutableListOf<HourlyUiModel>()

            for (i in longMarine.times.indices) {
                val t = longMarine.times[i]
                val isShortTerm = t.date < cutoffDate

                val shortMarineIndex = marineMapShort[t]
                val (mIndex, mData) = if (isShortTerm && shortMarineIndex != null) {
                    shortMarineIndex to shortMarine
                } else {
                    i to longMarine
                }

                val shortWeatherIndex = weatherMapShort[t]
                val (wIndex, wData) = if (isShortTerm && shortWeatherIndex != null) {
                    shortWeatherIndex to shortWeather
                } else {
                    (weatherMapLong[t] ?: 0) to longWeather
                }

                val h = mData.waveHeights[mIndex]
                val p = mData.wavePeriods[mIndex]
                val windDirDeg = wData.windDirections.getOrElse(wIndex) { 0.0 }

                resultList.add(
                    HourlyUiModel(
                        timeFormatted = formatHour(t),
                        rawTime = t,
                        waveHeight = h,
                        wavePeriod = p,
                        waveDirection = mData.waveDirections[mIndex],
                        windWaveHeight = mData.windWaveHeights.getOrElse(mIndex) { 0.0 },
                        windWavePeriod = mData.windWavePeriods.getOrElse(mIndex) { 0.0 },
                        windWaveDirection = mData.windWaveDirections.getOrElse(mIndex) { 0f },
                        energyKj = calculateWaveEnergyReal(h, p),
                        windSpeedKmh = wData.windSpeeds.getOrElse(wIndex) { 10.0 }.roundToInt(),
                        windDirectionStr = getCardinalDirection(windDirDeg),
                        weatherCode = wData.weatherCodes.getOrElse(wIndex) { 0 },
                        temperature = wData.temperatures.getOrElse(wIndex) { 20.0 }.roundToInt(),
                        cloudCover = wData.cloudCovers.getOrElse(wIndex) { 0 },
                        feelsLike = wData.apparentTemperatures.getOrElse(wIndex) {
                            wData.temperatures.getOrElse(wIndex) { 20.0 }
                        }.roundToInt()
                    )
                )
            }

            val dailySun = longWeather.sunriseByDate.keys.associateWith { date ->
                DailySunInfo(
                    sunrise = longWeather.sunriseByDate[date] ?: LocalTime(7, 0),
                    sunset = longWeather.sunsetByDate[date] ?: LocalTime(21, 0)
                )
            }

            HybridForecastResult(hourly = resultList.sortedBy { it.rawTime }, dailySun = dailySun)
    }

    /**
     * dailyByDate : une PM + une BM "de jour" par date (ce qu'affiche l'appli).
     * rawByDate : toutes les marées du jour, nuit comprise (pour la vraie prochaine marée du widget).
     */
    data class TidesBundle(
        val dailyByDate: Map<LocalDate, DailyTideInfo>,
        val rawByDate: Map<LocalDate, List<MareeExtremum>>
    )

    /** Dates au format ISO (yyyy-MM-dd). Erreur réseau -> bundle vide, comme côté Android. */
    suspend fun getTides(lat: Double, lon: Double, fromDate: String, toDate: String): TidesBundle =
        withContext(Dispatchers.Default) {
            try {
                val sitesRes: MareeSitesResponse = httpClient.get("https://api-maree.fr/sites").body()
                val targetSiteId = sitesRes.sites?.minByOrNull { site ->
                    val dLat = site.latitude - lat
                    val dLon = site.longitude - lon
                    dLat * dLat + dLon * dLon
                }?.siteId ?: "cordouan"

                val extremaRes: MareeExtremaResponse = httpClient.get("https://api-maree.fr/tide-extrema") {
                    parameter("site", targetSiteId)
                    parameter("from", fromDate)
                    parameter("to", toDate)
                    parameter("tz", "Europe/Paris")
                    parameter("key", apiMareeToken)
                }.body()

                val dailyMap = mutableMapOf<LocalDate, DailyTideInfo>()
                val rawMap = mutableMapOf<LocalDate, List<MareeExtremum>>()

                extremaRes.data?.forEach { dayData ->
                    val parsedDate = LocalDate.parse(dayData.date)
                    val extrema = dayData.extrema ?: emptyList()
                    rawMap[parsedDate] = extrema

                    val dayPm = extrema.firstOrNull { it.type == "PM" && it.time >= "06:00" && it.time <= "21:30" }
                        ?: extrema.firstOrNull { it.type == "PM" }
                    val dayBm = extrema.firstOrNull { it.type == "BM" && it.time >= "06:00" && it.time <= "21:30" }
                        ?: extrema.firstOrNull { it.type == "BM" }
                    // L'API ne met le coefficient que sur les PM.
                    val coef = dayPm?.coef ?: extrema.firstNotNullOfOrNull { it.coef }

                    dailyMap[parsedDate] = DailyTideInfo(
                        highTideTime = dayPm?.time,
                        lowTideTime = dayBm?.time,
                        coefficient = coef
                    )
                }
                TidesBundle(dailyMap, rawMap)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                TidesBundle(emptyMap(), emptyMap())
            }
        }
}

internal fun defaultHttpClient(): HttpClient = HttpClient {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
    // Équivalent des timeouts de 10 s de HttpURLConnection côté Android.
    install(HttpTimeout) {
        requestTimeoutMillis = 20_000
        socketTimeoutMillis = 10_000
    }
}

private fun JsonObject.optArray(key: String): JsonArray? = this[key] as? JsonArray

// Valeur numérique à l'index i, null si absente, JSON null ou hors limites (≈ org.json isNull).
private fun JsonArray?.doubleAt(i: Int): Double? =
    (this?.getOrNull(i) as? kotlinx.serialization.json.JsonPrimitive)?.doubleOrNull
