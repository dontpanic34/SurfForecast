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
private const val AROME_HD_MODEL = "meteofrance_arome_france_hd"

/** Source du vent quand aucun modèle n'a de valeur pour cette heure (vent affiché : 0). */
const val WIND_SOURCE_MISSING = "manquant"

class SurfRepository(
    private val httpClient: HttpClient,
    // Cache hors réseau (navigateur) : la dernière réponse de chaque appel sert de repli quand le réseau
    // ou l'API tombe. null = pas de cache (tests, Android qui a le sien).
    private val cache: KeyValueStore? = null
) {

    constructor() : this(defaultHttpClient())

    /** Date (ms) de la plus ancienne réponse servie depuis le cache depuis le dernier [resetStale] ; null = tout est frais. */
    var staleSinceMillis: Long? = null
        private set

    fun resetStale() {
        staleSinceMillis = null
    }

    private fun cacheKey(url: String) = "hc_" + url.hashCode().toUInt().toString(16)

    private fun markStale(savedAt: Long) {
        val current = staleSinceMillis
        if (current == null || savedAt < current) staleSinceMillis = savedAt
    }

    private fun readCached(url: String): Pair<Long, String>? {
        val raw = cache?.getString(cacheKey(url), null) ?: return null
        val sep = raw.indexOf('\n')
        if (sep <= 0) return null
        val savedAt = raw.substring(0, sep).toLongOrNull() ?: return null
        return savedAt to raw.substring(sep + 1)
    }

    @OptIn(ExperimentalTime::class)
    private fun writeCached(url: String, text: String) {
        val store = cache ?: return
        if (text.length > 400_000) return
        try {
            val key = cacheKey(url)
            val index = (store.getString("hc_index", "") ?: "").split(',').filter { it.isNotEmpty() && it != key }.toMutableList()
            index.add(key)
            // 20 réponses au plus (quelques spots) : le stockage du navigateur est limité (~5 Mo).
            while (index.size > 20) store.remove(index.removeAt(0))
            store.putString(key, "${Clock.System.now().toEpochMilliseconds()}\n$text")
            store.putString("hc_index", index.joinToString(","))
        } catch (e: Exception) {
            // Stockage plein ou refusé : le cache est un confort, pas une obligation.
        }
    }

    /** Texte d'une réponse : réseau d'abord, sinon dernière réponse en cache (marquée périmée). */
    private suspend fun getTextWithFallback(url: String, fetch: suspend () -> String): String {
        return try {
            val text = fetch()
            writeCached(url, text)
            text
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val cached = readCached(url) ?: throw e
            markStale(cached.first)
            cached.second
        }
    }

    private val apiMareeToken = "0093ca14ffeffadcf739be7cc77f4738"

    private suspend fun httpGet(url: String): JsonObject {
        val text = getTextWithFallback(url) {
            val response = httpClient.get(url)
            val body = response.bodyAsText()
            val code = response.status.value
            if (code !in 200..299) {
                val reason = runCatching {
                    Json.parseToJsonElement(body).jsonObject["reason"]?.jsonPrimitive?.contentOrNull
                }.getOrNull() ?: body
                throw IllegalStateException("API Open-Meteo ($code): $reason")
            }
            body
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
        val windGusts: List<Double>,
        val cloudCovers: List<Int>,
        val apparentTemperatures: List<Double>,
        val sunriseByDate: Map<LocalDate, LocalTime>,
        val sunsetByDate: Map<LocalDate, LocalTime>
    )

    private suspend fun fetchMarineBlock(lat: Double, lon: Double, waveModel: WaveModel, days: Int): RawMarineHourly {
        val url = "https://marine-api.open-meteo.com/v1/marine?" +
            "latitude=$lat&longitude=$lon" +
            "&hourly=wave_height,wave_period,wave_direction,swell_wave_height,swell_wave_period,swell_wave_direction,swell_wave_peak_period,wind_wave_height,wind_wave_direction,wind_wave_peak_period,wind_wave_period" +
            "&models=${waveModel.apiParam}" +
            "&forecast_days=$days" +
            "&timezone=auto"

        val hourly = httpGet(url).getValue("hourly").jsonObject
        val timeArr = hourly.getValue("time").jsonArray

        val swellHArr = hourly.optArray("swell_wave_height")
        val swellPArr = hourly.optArray("swell_wave_period")
        val swellPeakArr = hourly.optArray("swell_wave_peak_period")
        val windPeakArr = hourly.optArray("wind_wave_peak_period")
        val windMeanPArr = hourly.optArray("wind_wave_period")
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
            // Jamais la période de la mer de vent (clapot, 4-8 s) : associée à la hauteur
            // de houle, elle affichait des "0.9m - 8s" qui ne correspondaient à rien.
            val finalP = when {
                !swellPeak.isNaN() && swellPeak > 0.0 -> swellPeak
                !swellP.isNaN() && swellP > 0.0 -> swellP
                else -> totalP
            }
            val finalD = if (!swellD.isNaN()) swellD else totalD

            waveHeights.add(finalH)
            wavePeriods.add(finalP)
            waveDirs.add(finalD)

            windWaveHeights.add(windWaveHArr.doubleAt(i) ?: 0.0)
            // Période du clapot : pic, sinon moyenne, sinon 0 = inconnue (affichée "—").
            val windMeanP = windMeanPArr.doubleAt(i) ?: Double.NaN
            windWavePeriods.add(
                when {
                    !windPeak.isNaN() && windPeak > 0.0 -> windPeak
                    !windMeanP.isNaN() && windMeanP > 0.0 -> windMeanP
                    else -> 0.0
                }
            )
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
            "&hourly=temperature_2m,weather_code,wind_speed_10m,wind_direction_10m,wind_gusts_10m,cloudcover,apparent_temperature" +
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
        val windGustArr = getArray("wind_gusts_10m")
        val cloudArr = getArray("cloudcover")
        val apparentArr = getArray("apparent_temperature")

        val times = mutableListOf<LocalDateTime>()
        val temps = mutableListOf<Double>()
        val codes = mutableListOf<Int>()
        val windSpeeds = mutableListOf<Double>()
        val windDirs = mutableListOf<Double>()
        val windGusts = mutableListOf<Double>()
        val clouds = mutableListOf<Int>()
        val apparentTemps = mutableListOf<Double>()

        for (i in timeArr.indices) {
            times.add(LocalDateTime.parse(timeArr[i].jsonPrimitive.content))
            val temp = tempArr.doubleAt(i) ?: 20.0
            temps.add(temp)
            codes.add(codeArr.doubleAt(i)?.toInt() ?: 0)
            // NaN = pas de valeur : la fusion choisira un autre modèle au lieu d'inventer
            // un vent (ancien défaut : 10 km/h de Nord).
            windSpeeds.add(windSpeedArr.doubleAt(i) ?: Double.NaN)
            windDirs.add(windDirArr.doubleAt(i) ?: Double.NaN)
            windGusts.add(windGustArr.doubleAt(i) ?: Double.NaN)
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

        return RawWeatherHourly(times, temps, codes, windSpeeds, windDirs, windGusts, clouds, apparentTemps, sunriseByDate, sunsetByDate)
    }

    /**
     * Vent AROME HD (maille ~1.3 km au lieu de 2.5 km), plus fin près des côtes. Ne demande
     * que le vent (ce modèle ne fournit pas toutes les autres variables). Un échec ne doit
     * jamais faire échouer la prévision : on retombe simplement sur AROME standard.
     */
    private suspend fun fetchAromeHdWind(lat: Double, lon: Double): Map<LocalDateTime, Pair<Double, Double>> {
        return try {
            val url = "https://api.open-meteo.com/v1/forecast?" +
                "latitude=$lat&longitude=$lon" +
                "&hourly=wind_speed_10m,wind_direction_10m" +
                "&models=$AROME_HD_MODEL" +
                "&forecast_days=2" +
                "&timezone=auto"
            val hourly = httpGet(url).getValue("hourly").jsonObject
            val timeArr = hourly.getValue("time").jsonArray
            val speedArr = hourly.optArray("wind_speed_10m") ?: hourly.optArray("wind_speed_10m_$AROME_HD_MODEL")
            val dirArr = hourly.optArray("wind_direction_10m") ?: hourly.optArray("wind_direction_10m_$AROME_HD_MODEL")
            if (speedArr == null || dirArr == null) return emptyMap()
            buildMap {
                for (i in timeArr.indices) {
                    val speed = speedArr.doubleAt(i) ?: continue
                    val dir = dirArr.doubleAt(i) ?: continue
                    put(LocalDateTime.parse(timeArr[i].jsonPrimitive.content), speed to dir)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyMap()
        }
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
            val aromeHdWindDeferred = async {
                if (config.shortTermWeather == WeatherModel.AROME) fetchAromeHdWind(lat, lon) else emptyMap()
            }
            // Lever/coucher du soleil : donnée astronomique, demandée une seule fois (appel 7 jours).
            val longWeatherDeferred = async {
                fetchWeatherBlock(lat, lon, config.longTermWeather, 7, includeDailySun = true)
            }

            val shortMarine = shortMarineDeferred.await()
            val longMarine = longMarineDeferred.await()
            val shortWeather = shortWeatherDeferred.await()
            val longWeather = longWeatherDeferred.await()
            val aromeHdWind = aromeHdWindDeferred.await()

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
                // Vent : AROME HD en court terme si dispo, sinon le modèle météo de cette
                // heure, sinon le long terme. Jamais de valeur inventée.
                val longIndex = weatherMapLong[t]
                val hd = if (isShortTerm) aromeHdWind[t] else null
                val primarySpeed = wData.windSpeeds.getOrElse(wIndex) { Double.NaN }
                val primaryDir = wData.windDirections.getOrElse(wIndex) { Double.NaN }
                val longSpeed = longIndex?.let { longWeather.windSpeeds.getOrElse(it) { Double.NaN } } ?: Double.NaN
                val longDir = longIndex?.let { longWeather.windDirections.getOrElse(it) { Double.NaN } } ?: Double.NaN
                // Rafales : celles du modèle météo de l'heure, sinon du long terme ; jamais
                // inférieures au vent moyen affiché.
                val primaryGust = wData.windGusts.getOrElse(wIndex) { Double.NaN }
                val longGust = longIndex?.let { longWeather.windGusts.getOrElse(it) { Double.NaN } } ?: Double.NaN
                val gustRaw = when {
                    !primaryGust.isNaN() -> primaryGust
                    !longGust.isNaN() -> longGust
                    else -> Double.NaN
                }
                val (windSpeedRaw, windDirDeg, windSource) = when {
                    hd != null -> Triple(hd.first, hd.second, "AROME HD")
                    !primarySpeed.isNaN() && !primaryDir.isNaN() -> Triple(
                        primarySpeed,
                        primaryDir,
                        if (wData === shortWeather) config.shortTermWeather.name else config.longTermWeather.name
                    )
                    !longSpeed.isNaN() && !longDir.isNaN() -> Triple(longSpeed, longDir, config.longTermWeather.name)
                    else -> Triple(0.0, 0.0, WIND_SOURCE_MISSING)
                }

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
                        windSpeedKmh = windSpeedRaw.roundToInt(),
                        windGustKmh = (if (gustRaw.isNaN()) windSpeedRaw else maxOf(gustRaw, windSpeedRaw)).roundToInt(),
                        windDirectionStr = getCardinalDirection(windDirDeg),
                        windSource = windSource,
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
    // Délai max par requête (pas de socketTimeout : non géré par le moteur du navigateur).
    install(HttpTimeout) {
        requestTimeoutMillis = 20_000
    }
}

private fun JsonObject.optArray(key: String): JsonArray? = this[key] as? JsonArray

// Valeur numérique à l'index i, null si absente, JSON null ou hors limites (≈ org.json isNull).
private fun JsonArray?.doubleAt(i: Int): Double? =
    (this?.getOrNull(i) as? kotlinx.serialization.json.JsonPrimitive)?.doubleOrNull
