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

/** Relais Cloudflare des marées (functions/api/maree) : jeton côté serveur + cache. Repli : appel direct. */
const val MAREE_RELAY_BASE = "https://surflog.fr/api/maree"

/** Source du vent quand aucun modèle n'a de valeur pour cette heure (vent affiché : 0). */
const val WIND_SOURCE_MISSING = "manquant"

/** Rapport approximatif période de pic / période moyenne d'une houle (spectre type). */
private const val MEAN_TO_PEAK_PERIOD = 1.2

/** Modèle de vagues dont on affiche la période (même source que Windguru, Windy, Surf-Forecast). */
private const val REFERENCE_WAVE_MODEL = "ncep_gfswave016"

class SurfRepository(
    private val httpClient: HttpClient,
    // Cache hors réseau (navigateur) : la dernière réponse de chaque appel sert de repli quand le réseau
    // ou l'API tombe. null = pas de cache (tests, Android qui a le sien).
    private val cache: KeyValueStore? = null,
    // Relais Cloudflare des marées (ex. https://surflog.fr/api/maree) : garde le jeton côté serveur et met
    // les réponses en cache. null = appel direct à api-maree.fr.
    private val mareeRelayBase: String? = MAREE_RELAY_BASE
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
            "&hourly=wave_height,wave_period,wave_direction,swell_wave_height,swell_wave_period,swell_wave_direction,swell_wave_peak_period,wave_peak_period,wind_wave_height,wind_wave_direction,wind_wave_peak_period,wind_wave_period" +
            "&models=${waveModel.apiParam}" +
            "&forecast_days=$days" +
            "&timezone=auto"

        val hourly = httpGet(url).getValue("hourly").jsonObject
        val timeArr = hourly.getValue("time").jsonArray

        val swellHArr = hourly.optArray("swell_wave_height")
        val swellPArr = hourly.optArray("swell_wave_period")
        val swellPeakArr = hourly.optArray("swell_wave_peak_period")
        val totalPeakArr = hourly.optArray("wave_peak_period")
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
            // Période de PIC, comme l'affichent Windy, Windguru, Surfline et Surf-Forecast : la plus longue des
            // périodes de pic disponibles (houle, ou total des vagues). Jamais la période de la mer de vent
            // (clapot, 4-8 s) : associée à la hauteur de houle, elle ne correspondrait à rien.
            // Si le modèle (ECMWF) ne donne que des périodes MOYENNES, plus courtes d'environ 20 %,
            // on les convertit en période de pic : sinon l'appli afficherait 6 s là où tout le monde lit 10.
            val totalPeak = totalPeakArr.doubleAt(i) ?: Double.NaN
            val finalP = when {
                // Cas habituel (MFWAM) : la période de pic de la houle, comme avant.
                !swellPeak.isNaN() && swellPeak > 0.0 -> swellPeak
                !totalPeak.isNaN() && totalPeak > 0.0 -> maxOf(totalPeak, if (!swellP.isNaN()) swellP else 0.0)
                !swellP.isNaN() && swellP > 0.0 -> swellP * MEAN_TO_PEAK_PERIOD
                else -> totalP * MEAN_TO_PEAK_PERIOD
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

    /**
     * Température de la mer par heure (°C), sans modèle de vagues imposé : appel isolé, un échec ou une
     * donnée absente (point trop côtier) laisse simplement la température inconnue.
     */
    private suspend fun fetchSeaTemperature(lat: Double, lon: Double, days: Int): Map<LocalDateTime, Double> = runCatching {
        val url = "https://marine-api.open-meteo.com/v1/marine?" +
            "latitude=$lat&longitude=$lon&hourly=sea_surface_temperature&forecast_days=$days&timezone=auto"
        val hourly = httpGet(url).getValue("hourly").jsonObject
        val timeArr = hourly.getValue("time").jsonArray
        val tempArr = hourly.optArray("sea_surface_temperature")
        buildMap {
            for (i in timeArr.indices) {
                val t = tempArr.doubleAt(i) ?: continue
                put(LocalDateTime.parse(timeArr[i].jsonPrimitive.content), t)
            }
        }
    }.getOrDefault(emptyMap())

    /**
     * Période « de référence » par heure : la période de GFS Wave 0,16° (NOAA WW3), exactement celle que lisent les
     * surfeurs sur Windguru, Windy, Surf-Forecast et sur la partie NOAA de Yadusurf. On la préfère à celle de
     * MFWAM / ECMWF, plus courte de 1 à 3 s. Échec ou donnée absente : on garde le modèle de base (map vide).
     * (Open-Meteo ne fournit pas de période de pic pour ce modèle : `wave_period` EST la valeur de Windguru.)
     */
    private suspend fun fetchReferencePeriods(lat: Double, lon: Double, days: Int): Map<LocalDateTime, Double> = runCatching {
        val url = "https://marine-api.open-meteo.com/v1/marine?" +
            "latitude=$lat&longitude=$lon&hourly=wave_period&models=$REFERENCE_WAVE_MODEL" +
            "&forecast_days=$days&timezone=auto"
        val hourly = httpGet(url).getValue("hourly").jsonObject
        val timeArr = hourly.getValue("time").jsonArray
        val periodArr = hourly.optArray("wave_period")
        buildMap {
            for (i in timeArr.indices) {
                val period = periodArr.doubleAt(i)?.takeIf { it > 0.0 } ?: continue
                put(LocalDateTime.parse(timeArr[i].jsonPrimitive.content), period)
            }
        }
    }.getOrDefault(emptyMap())

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

            val seaTempDeferred = async { fetchSeaTemperature(lat, lon, 7) }
            val referencePeriodsDeferred = async { fetchReferencePeriods(lat, lon, 7) }

            val shortMarine = shortMarineDeferred.await()
            val longMarine = longMarineDeferred.await()
            val shortWeather = shortWeatherDeferred.await()
            val longWeather = longWeatherDeferred.await()
            val aromeHdWind = aromeHdWindDeferred.await()
            val seaTemps = seaTempDeferred.await()
            val referencePeriods = referencePeriodsDeferred.await()

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
                // Période : celle de GFS Wave (comme Windguru / Yadusurf / Surf-Forecast) ; à défaut, le modèle de la façade.
                val basePeriod = mData.wavePeriods[mIndex]
                val refPeriod = referencePeriods[t]
                val p = refPeriod ?: basePeriod
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
                        }.roundToInt(),
                        seaTemperature = seaTemps[t]
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

    /**
     * Dates au format ISO (yyyy-MM-dd). Erreur réseau -> bundle vide, comme côté Android.
     *
     * France (site de marée à moins de [MAX_FRENCH_TIDE_KM]) : horaires, hauteurs et coefficients de
     * api-maree.fr (SHOM). Plus loin (Espagne, Portugal) : horaires et hauteurs ESTIMÉS à partir de la
     * hauteur d'eau Open-Meteo, et coefficient repris de la France (voir [tideExtremaFromSeaLevel]).
     */
    suspend fun getTides(lat: Double, lon: Double, fromDate: String, toDate: String): TidesBundle =
        withContext(Dispatchers.Default) {
            try {
                val sites = fetchMareeSites()
                val nearest = sites.minByOrNull { distanceKm(lat, lon, it.latitude, it.longitude) }
                val distance = nearest?.let { distanceKm(lat, lon, it.latitude, it.longitude) }
                val days: List<MareeDayData> = if (nearest == null || (distance ?: 0.0) <= MAX_FRENCH_TIDE_KM) {
                    fetchMareeExtrema(nearest?.siteId ?: "cordouan", fromDate, toDate)
                } else {
                    // Hors France : on lit le coefficient chez le port français le plus proche (même date,
                    // PM la plus proche en heure). Le SHOM le définit à partir de Brest, mais chaque port
                    // le publie rattaché à sa propre marée : le port voisin est le plus fidèle.
                    val referenceSite = nearest
                    val french = runCatching { fetchMareeExtrema(referenceSite.siteId, fromDate, toDate) }.getOrDefault(emptyList())
                    estimatedTideDays(lat, lon, french)
                }

                val dailyMap = mutableMapOf<LocalDate, DailyTideInfo>()
                val rawMap = mutableMapOf<LocalDate, List<MareeExtremum>>()

                days.forEach { dayData ->
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

    private val mareeJson = Json { ignoreUnknownKeys = true }

    /**
     * Réponse du service de marées : par le relais Cloudflare (le jeton api-maree.fr reste côté serveur, variable
     * MAREE_TOKEN du projet Pages). Seule la liste des sites, publique, a un appel direct de secours.
     */
    private suspend fun mareeText(path: String, params: List<Pair<String, String>>, ttlMillis: Long): String {
        val query = params.joinToString("&") { (k, v) -> "$k=$v" }
        val suffix = if (query.isEmpty()) "" else "?$query"
        val relayUrl = mareeRelayBase?.let { "$it/$path$suffix" }
        // /sites est public : appel direct possible. Les marées demandent le jeton, qui n'est plus dans l'appli.
        val directUrl = if (path == "sites") "https://api-maree.fr/$path$suffix" else null
        val cacheId = "maree:$path?$query"
        return cachedText(cacheId, ttlMillis) {
            val relayResult = relayUrl?.let { url ->
                runCatching {
                    val response = httpClient.get(url)
                    if (response.status.value !in 200..299) error("relais ${response.status.value}")
                    response.bodyAsText()
                }.getOrNull()
            }
            relayResult ?: run {
                if (directUrl == null) error("relais des marées indisponible")
                val response = httpClient.get(directUrl)
                if (response.status.value !in 200..299) error("api-maree ${response.status.value}")
                response.bodyAsText()
            }
        }
    }

    private suspend fun fetchMareeSites(): List<MareeSite> =
        mareeJson.decodeFromString<MareeSitesResponse>(mareeText("sites", emptyList(), 7 * 24 * 3600_000L)).sites ?: emptyList()

    private suspend fun fetchMareeExtrema(siteId: String, from: String, to: String): List<MareeDayData> =
        mareeJson.decodeFromString<MareeExtremaResponse>(
            mareeText("tide-extrema", listOf("site" to siteId, "from" to from, "to" to to, "tz" to "Europe/Paris"), 12 * 3600_000L)
        ).data ?: emptyList()

    /** Marées estimées pour un spot hors France, avec le coefficient français de même date. */
    private suspend fun estimatedTideDays(lat: Double, lon: Double, french: List<MareeDayData>): List<MareeDayData> {
        val url = "https://marine-api.open-meteo.com/v1/marine?latitude=$lat&longitude=$lon" +
            "&hourly=sea_level_height_msl&past_days=1&forecast_days=8&timezone=auto"
        val root = Json.parseToJsonElement(cachedText(url, 6 * 3600_000L) { httpGetText(url) }).jsonObject
        val hourly = root["hourly"]?.jsonObject ?: return emptyList()
        val times = hourly.getValue("time").jsonArray.map { LocalDateTime.parse(it.jsonPrimitive.content) }
        val levels = hourly.optArray("sea_level_height_msl") ?: return emptyList()
        val heights = times.indices.map { levels.doubleAt(it) }
        val frenchCoefs = french.flatMap { day ->
            day.extrema.orEmpty().filter { it.type == "PM" && it.coef != null }.map { Triple(day.date, it.time, it.coef!!) }
        }
        return tideDaysFromSeaLevel(times, heights, frenchCoefs)
    }

    private suspend fun httpGetText(url: String): String {
        val response = httpClient.get(url)
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) error("API Open-Meteo (${response.status.value})")
        return body
    }

    /** Réponse en cache encore fraîche (moins de [ttlMillis]) sinon réseau, avec repli sur un cache périmé. */
    @OptIn(ExperimentalTime::class)
    private suspend fun cachedText(id: String, ttlMillis: Long, fetch: suspend () -> String): String {
        val cached = readCached(id)
        if (cached != null && Clock.System.now().toEpochMilliseconds() - cached.first < ttlMillis) return cached.second
        return getTextWithFallback(id, fetch)
    }

    companion object {
        /** Au-delà, le site de marée français n'est plus représentatif du spot : on estime. */
        const val MAX_FRENCH_TIDE_KM = 80.0
    }
}

/** Distance approximative (km) entre deux points, suffisante pour choisir un port. */
internal fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371.0
    val dLat = (lat2 - lat1) * kotlin.math.PI / 180.0
    val dLon = (lon2 - lon1) * kotlin.math.PI / 180.0
    val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
        kotlin.math.cos(lat1 * kotlin.math.PI / 180.0) * kotlin.math.cos(lat2 * kotlin.math.PI / 180.0) *
        kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
    return 2 * r * kotlin.math.asin(kotlin.math.sqrt(a))
}

/**
 * Pleines et basses mers déduites d'une série horaire de hauteur d'eau (Open-Meteo, marée comprise).
 * Maximums et minimums locaux, affinés par une parabole sur 3 points (précision de l'ordre de 15 min).
 * Chaque PM reçoit le coefficient français de la même date le plus proche en heure ([frenchCoefs] :
 * date ISO, heure "HH:mm", coefficient). Hauteurs relatives (0 = plus basse mer de la période).
 */
internal fun tideDaysFromSeaLevel(
    times: List<LocalDateTime>,
    heights: List<Double?>,
    frenchCoefs: List<Triple<String, String, Int>>
): List<MareeDayData> {
    data class Raw(val date: LocalDate, val minutes: Int, val isHigh: Boolean, val height: Double)
    val raws = mutableListOf<Raw>()
    for (i in 1 until times.size - 1) {
        val a = heights[i - 1] ?: continue
        val b = heights[i] ?: continue
        val c = heights[i + 1] ?: continue
        val isHigh = b >= a && b > c
        val isLow = b <= a && b < c
        if (!isHigh && !isLow) continue
        val denom = a - 2 * b + c
        val offsetHours = if (denom != 0.0) (0.5 * (a - c) / denom).coerceIn(-0.5, 0.5) else 0.0
        val peak = b - 0.25 * (a - c) * offsetHours
        var minutes = times[i].hour * 60 + times[i].minute + (offsetHours * 60).roundToInt()
        var date = times[i].date
        if (minutes < 0) { minutes += 1440; date = date.plus(-1, DateTimeUnit.DAY) }
        if (minutes >= 1440) { minutes -= 1440; date = date.plus(1, DateTimeUnit.DAY) }
        raws.add(Raw(date, minutes, isHigh, peak))
    }
    // Une marée alterne PM / BM : si deux extremums du même type se suivent, on garde le plus marqué.
    val alternating = mutableListOf<Raw>()
    for (r in raws) {
        val last = alternating.lastOrNull()
        if (last != null && last.isHigh == r.isHigh) {
            val better = if (r.isHigh) r.height > last.height else r.height < last.height
            if (better) alternating[alternating.size - 1] = r
        } else {
            alternating.add(r)
        }
    }
    if (alternating.isEmpty()) return emptyList()
    val floor = alternating.minOf { it.height }
    fun hhmm(minutes: Int) = "${(minutes / 60).toString().padStart(2, '0')}:${(minutes % 60).toString().padStart(2, '0')}"
    return alternating.groupBy { it.date }.entries.sortedBy { it.key }.map { (date, list) ->
        MareeDayData(
            date = date.toString(),
            extrema = list.sortedBy { it.minutes }.map { r ->
                val time = hhmm(r.minutes)
                val coef = if (r.isHigh) {
                    frenchCoefs.filter { it.first == date.toString() }
                        .minByOrNull { kotlin.math.abs(minutesOf(it.second) - r.minutes) }?.third
                } else null
                MareeExtremum(
                    type = if (r.isHigh) "PM" else "BM",
                    time = time,
                    height = ((r.height - floor) * 100).roundToInt() / 100.0,
                    coef = coef
                )
            }
        )
    }
}

private fun minutesOf(hhmm: String): Int {
    val parts = hhmm.split(':')
    return (parts.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (parts.getOrNull(1)?.toIntOrNull() ?: 0)
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
