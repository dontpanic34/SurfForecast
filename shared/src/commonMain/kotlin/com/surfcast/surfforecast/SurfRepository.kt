package com.surfcast.surfforecast

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
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
private const val GFS_MODEL = "gfs_seamless"

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

    /** Envoie les statistiques anonymes (niveau, version) au relais surflog.fr/api/stats ; true si bien reçu. */
    suspend fun postStats(json: String): Boolean {
        val response = httpClient.post("https://surflog.fr/api/stats/report") {
            contentType(ContentType.Application.Json)
            setBody(json)
        }
        return response.status.value in 200..299
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
        val windWaveDirections: List<Float>,
        // Houle secondaire (Open-Meteo secondary_swell_wave_*) : 0 = pas de seconde houle.
        val secHeights: List<Double> = emptyList(),
        val secPeriods: List<Double> = emptyList(),
        val secDirections: List<Float> = emptyList(),
        // Désaccord des modèles par heure (consensus) : (max - min) / moyenne des hauteurs ; vide hors consensus.
        val spreads: List<Double> = emptyList(),
        // Période la plus courte et la plus longue entre les modèles (consensus) ; vide hors consensus.
        val periodMins: List<Double> = emptyList(),
        val periodMaxs: List<Double> = emptyList(),
        // Ce que dit chaque modèle pour chaque heure (consensus) ; vide hors consensus.
        val voices: List<List<ModelVoice>> = emptyList()
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
        val sunsetByDate: Map<LocalDate, LocalTime>,
        val uvIndexes: List<Double> = emptyList(),
        val precipitations: List<Double> = emptyList()
    )

    private suspend fun fetchMarineBlock(lat: Double, lon: Double, waveModel: WaveModel, days: Int): RawMarineHourly {
        val url = "https://marine-api.open-meteo.com/v1/marine?" +
            "latitude=$lat&longitude=$lon" +
            "&hourly=wave_height,wave_period,wave_direction,swell_wave_height,swell_wave_period,swell_wave_direction,swell_wave_peak_period,wave_peak_period,wind_wave_height,wind_wave_direction,wind_wave_peak_period,wind_wave_period,secondary_swell_wave_height,secondary_swell_wave_period,secondary_swell_wave_direction" +
            "&models=${waveModel.apiParam}" +
            "&forecast_days=$days" +
            "&timezone=auto"

        val hourly = httpGet(url).getValue("hourly").jsonObject
        val timeArr = hourly.getValue("time").jsonArray
        val meanToPeak = if (waveModel == WaveModel.GFS_WAVE) 1.0 else MEAN_TO_PEAK_PERIOD

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
        val secHArr = hourly.optArray("secondary_swell_wave_height")
        val secPArr = hourly.optArray("secondary_swell_wave_period")
        val secDArr = hourly.optArray("secondary_swell_wave_direction")
        val secHeights = mutableListOf<Double>()
        val secPeriods = mutableListOf<Double>()
        val secDirs = mutableListOf<Float>()

        for (i in timeArr.indices) {
            secHeights.add(secHArr.doubleAt(i) ?: 0.0)
            secPeriods.add(secPArr.doubleAt(i) ?: 0.0)
            secDirs.add(secDArr.doubleAt(i)?.toFloat() ?: 0f)
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
                // GFS Wave donne déjà des périodes de pic : aucune conversion.
                !swellP.isNaN() && swellP > 0.0 -> swellP * meanToPeak
                else -> totalP * meanToPeak
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

        return RawMarineHourly(times, waveHeights, wavePeriods, waveDirs, windWaveHeights, windWavePeriods, windWaveDirs, secHeights, secPeriods, secDirs)
    }

    /**
     * Les valeurs de chaque modèle pour un même moment (14 h aujourd'hui, demain et J+3), pour les montrer sous le choix
     * manuel des sources : on voit ce que chaque modèle annonce avant de le choisir. Un modèle qui échoue ou qui ne
     * couvre pas l'échéance est simplement absent.
     */
    suspend fun fetchModelPreview(latitude: Double, longitude: Double, today: LocalDate): ModelPreview = coroutineScope {
        val waveJobs = listOf("MF-WAM" to WaveModel.MFWAM, "ECMWF Wave" to WaveModel.ECMWF_WAM, "GFS Wave" to WaveModel.GFS_WAVE).map { (label, m) ->
            async { label to runCatching { fetchMarineBlock(latitude, longitude, m, 4) }.getOrNull() }
        }
        val windJobs = listOf("AROME" to WeatherModel.AROME, "ARPEGE" to WeatherModel.ARPEGE, "ECMWF" to WeatherModel.ECMWF_IFS, "GFS" to WeatherModel.GFS).map { (label, m) ->
            async { label to runCatching { fetchWeatherBlock(latitude, longitude, m, 4) }.getOrNull() }
        }
        val waves = HashMap<String, Map<Int, PreviewWave>>()
        waveJobs.forEach { j ->
            val (label, b) = j.await()
            if (b != null) {
                val byDay = HashMap<Int, PreviewWave>()
                for (d in 0..3) {
                    val i = b.times.indexOfFirst { it.date == today.plus(d, DateTimeUnit.DAY) && it.hour == 14 }
                    val h = b.waveHeights.getOrNull(i)
                    if (i >= 0 && h != null && h > 0.0) byDay[d] = PreviewWave(h, b.wavePeriods.getOrNull(i) ?: 0.0)
                }
                if (byDay.isNotEmpty()) waves[label] = byDay
            }
        }
        val winds = HashMap<String, Map<Int, PreviewWind>>()
        windJobs.forEach { j ->
            val (label, b) = j.await()
            if (b != null) {
                val byDay = HashMap<Int, PreviewWind>()
                for (d in 0..3) {
                    val i = b.times.indexOfFirst { it.date == today.plus(d, DateTimeUnit.DAY) && it.hour == 14 }
                    val v = b.windSpeeds.getOrNull(i)
                    if (i >= 0 && v != null && !v.isNaN()) {
                        byDay[d] = PreviewWind(
                            speedKmh = v.roundToInt(), directionDeg = (b.windDirections.getOrNull(i) ?: Double.NaN).takeIf { !it.isNaN() }?.roundToInt(),
                            temperature = b.temperatures.getOrNull(i)?.takeIf { !it.isNaN() }?.roundToInt(),
                            cloudCover = b.cloudCovers.getOrNull(i), rainMm = b.precipitations.getOrNull(i)?.takeIf { !it.isNaN() }
                        )
                    }
                }
                if (byDay.isNotEmpty()) winds[label] = byDay
            }
        }
        ModelPreview(waves, winds)
    }

    /**
     * Dernière mesure de la bouée de référence du point, lue par le relais surflog.fr/api/bouee (cache de 15 minutes).
     * Null sans bouée proche, sans réseau ou sans mesure : la carte n'affiche alors rien.
     */
    suspend fun fetchBuoyReading(latitude: Double, longitude: Double): BuoyReading? {
        val (buoy, km) = BuoyCatalog.referenceFor(latitude, longitude) ?: return null
        return try {
            val points = httpGet("https://surflog.fr/api/bouee/${buoy.code}")["points"]?.jsonArray ?: return null
            val last = points.mapNotNull { it as? JsonObject }.lastOrNull { it["hs"]?.jsonPrimitive?.doubleOrNull != null } ?: return null
            BuoyReading(
                buoy = buoy, distanceKm = km,
                significantHeight = last["hs"]!!.jsonPrimitive.double,
                maxHeight = last["hmax"]?.jsonPrimitive?.doubleOrNull,
                period = last["th"]?.jsonPrimitive?.doubleOrNull,
                direction = last["dir"]?.jsonPrimitive?.doubleOrNull?.roundToInt(),
                timeUtc = LocalDateTime.parse(last["t"]!!.jsonPrimitive.content)
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {   // sur le web, un échec réseau est une kotlin.Error, pas une Exception
            null
        }
    }

    /**
     * Dernière mesure de la station Météo-France la plus proche qui mesure le vent, lue par le relais surflog.fr/api/obs
     * (cache de 10 minutes). Null sans station à moins de 35 km, sans réseau, ou si la mesure date de plus de 3 heures.
     */
    suspend fun fetchObservedNow(latitude: Double, longitude: Double): ObservedNow? {
        return try {
            val o = httpGet("https://surflog.fr/api/obs/near?lat=$latitude&lon=$longitude")
            val time = LocalDateTime.parse(o["time"]!!.jsonPrimitive.content.removeSuffix("Z"))
            val age = nowLocalDateTime().toInstant(TimeZone.currentSystemDefault()).let { it - time.toInstant(TimeZone.UTC) }.inWholeHours
            if (age >= OBSERVED_MAX_AGE_HOURS) return null
            val st = o["station"]!!.jsonObject
            ObservedNow(
                stationName = st["name"]!!.jsonPrimitive.content,
                distanceKm = st["km"]!!.jsonPrimitive.double,
                timeUtc = time,
                windKmh = o["windKmh"]!!.jsonPrimitive.int,
                gustKmh = o["gustKmh"]?.jsonPrimitive?.intOrNull,
                directionDeg = o["dirDeg"]?.jsonPrimitive?.intOrNull,
                rainMm = o["rainMm"]?.jsonPrimitive?.doubleOrNull,
                tempC = o["tempC"]?.jsonPrimitive?.doubleOrNull
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {   // sur le web, un échec réseau est une kotlin.Error, pas une Exception
            null
        }
    }

    /**
     * Consensus des trois modèles de vagues, heure par heure : la hauteur, la période et la direction viennent du même
     * calcul. Court terme (jusqu'à 36 h) : MF-WAM et ECMWF à parts égales, GFS Wave écarté (il surestimait la période
     * de près de 2 s sur la bouée de Cap Ferret). Long terme (dès 60 h) : les trois à parts égales. Entre les deux,
     * un fondu, pour ne pas voir un saut. Clapot et houle secondaire : du modèle le plus pesant qui les donne.
     */
    private suspend fun buildConsensusMarine(lat: Double, lon: Double, today: LocalDate): RawMarineHourly = coroutineScope {
        val models = listOf(WaveModel.MFWAM, WaveModel.ECMWF_WAM, WaveModel.GFS_WAVE)
        val blocks = models.map { m -> async { runCatching { fetchMarineBlock(lat, lon, m, 7) }.getOrNull() } }.map { it.await() }
        val ok = models.indices.filter { blocks[it] != null }
        if (ok.isEmpty()) throw IllegalStateException("aucun modèle de vagues disponible")
        val base = blocks[ok.first()]!!
        val maps = blocks.map { b -> b?.times?.indices?.associateBy { b.times[it] } }
        val times = base.times
        val heights = ArrayList<Double>(); val periods = ArrayList<Double>(); val dirs = ArrayList<Float>()
        val wwH = ArrayList<Double>(); val wwP = ArrayList<Double>(); val wwD = ArrayList<Float>()
        val sH = ArrayList<Double>(); val sP = ArrayList<Double>(); val sD = ArrayList<Float>()
        val spreads = ArrayList<Double>()
        val periodMins = ArrayList<Double>(); val periodMaxs = ArrayList<Double>()
        val voices = ArrayList<List<ModelVoice>>()
        val voiceLabels = listOf("MF-WAM", "ECMWF", "GFS")
        for (t in times) {
            val leadHours = ((t.date.toEpochDays() - today.toEpochDays()) * 24L + t.hour).toDouble()
            val weights = waveConsensusWeights(leadHours)
            var sw = 0.0; var hSum = 0.0; var pSum = 0.0; var pw = 0.0; var vx = 0.0; var vy = 0.0
            var hMin = Double.MAX_VALUE; var hMax = 0.0; var hCount = 0; var hMean = 0.0
            var pMin = Double.MAX_VALUE; var pMax = 0.0; var pCount = 0
            var best = -1
            val hourVoices = ArrayList<ModelVoice>()
            for (k in models.indices) {
                val b = blocks[k] ?: continue
                val idx = maps[k]?.get(t) ?: continue
                val h = b.waveHeights.getOrNull(idx) ?: continue
                if (h.isNaN() || h <= 0.0) continue
                hourVoices += ModelVoice(voiceLabels[k], h, b.wavePeriods.getOrNull(idx) ?: 0.0, b.waveDirections.getOrNull(idx) ?: Float.NaN)
                val w = weights[k]
                if (w > 0.0) {
                    sw += w; hSum += w * h
                    val p = b.wavePeriods.getOrNull(idx) ?: 0.0
                    if (p > 0.0) { pSum += w * p; pw += w }
                    val d = (b.waveDirections.getOrNull(idx) ?: Float.NaN).toDouble()
                    if (!d.isNaN()) { vx += w * cos(d * PI / 180.0); vy += w * sin(d * PI / 180.0) }
                    if (best < 0 || w > weights[best]) best = k
                }
                hMin = minOf(hMin, h); hMax = maxOf(hMax, h); hMean += h; hCount++
                (b.wavePeriods.getOrNull(idx) ?: 0.0).let { pp -> if (pp > 0.0) { pMin = minOf(pMin, pp); pMax = maxOf(pMax, pp); pCount++ } }
            }
            if (sw <= 0.0) {
                // Aucun modèle pesant n'a cette heure : on prend le premier qui l'a, sans consensus.
                val k = ok.firstOrNull { maps[it]?.containsKey(t) == true }
                val idx = k?.let { maps[it]?.get(t) }
                val b = k?.let { blocks[it] }
                heights += b?.waveHeights?.getOrNull(idx ?: -1) ?: 0.0
                periods += b?.wavePeriods?.getOrNull(idx ?: -1) ?: 0.0
                dirs += b?.waveDirections?.getOrNull(idx ?: -1) ?: 0f
                wwH += 0.0; wwP += 0.0; wwD += 0f; sH += 0.0; sP += 0.0; sD += 0f; spreads += 0.0
                periodMins += 0.0; periodMaxs += 0.0; voices += emptyList<ModelVoice>()
                continue
            }
            heights += hSum / sw
            periods += if (pw > 0.0) pSum / pw else 0.0
            dirs += ((atan2(vy, vx) * 180.0 / PI + 360.0) % 360.0).toFloat()
            // Clapot et houle secondaire : du modèle le plus pesant (à égalité, MF-WAM en court terme, ECMWF ensuite).
            val order = (if (leadHours < 48) listOf(0, 1, 2) else listOf(1, 0, 2)).filter { blocks[it] != null && maps[it]?.containsKey(t) == true }
            val src = order.firstOrNull { k -> (maps[k]!![t]!!).let { i -> (blocks[k]!!.secHeights.getOrNull(i) ?: 0.0) > 0.0 } } ?: order.firstOrNull() ?: best
            val bs = blocks[src]!!; val si = maps[src]!![t]!!
            val wsrc = blocks[order.firstOrNull() ?: best]!!; val wi = maps[order.firstOrNull() ?: best]!![t]!!
            wwH += wsrc.windWaveHeights.getOrNull(wi) ?: 0.0; wwP += wsrc.windWavePeriods.getOrNull(wi) ?: 0.0; wwD += wsrc.windWaveDirections.getOrNull(wi) ?: 0f
            sH += bs.secHeights.getOrNull(si) ?: 0.0; sP += bs.secPeriods.getOrNull(si) ?: 0.0; sD += bs.secDirections.getOrNull(si) ?: 0f
            spreads += if (hCount >= 2 && hMean > 0.0) (hMax - hMin) / (hMean / hCount) else 0.0
            periodMins += if (pCount >= 2) pMin else 0.0; periodMaxs += if (pCount >= 2) pMax else 0.0
            voices += hourVoices
        }
        RawMarineHourly(times, heights, periods, dirs, wwH, wwP, wwD, sH, sP, sD, spreads, periodMins, periodMaxs, voices)
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
            "&hourly=temperature_2m,weather_code,wind_speed_10m,wind_direction_10m,wind_gusts_10m,cloudcover,apparent_temperature,uv_index,precipitation" +
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
        val uvArr = getArray("uv_index")
        val precipArr = getArray("precipitation")
        val uvs = mutableListOf<Double>()
        val precips = mutableListOf<Double>()

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
            uvs.add(uvArr.doubleAt(i) ?: 0.0)
            precips.add(precipArr.doubleAt(i) ?: 0.0)
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

        return RawWeatherHourly(times, temps, codes, windSpeeds, windDirs, windGusts, clouds, apparentTemps, sunriseByDate, sunsetByDate, uvs, precips)
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
     * Vent du modèle global GFS (vitesse, direction, rafales) sur [days] jours : il est moyenné avec ECMWF. Un échec ne fait
     * jamais échouer la prévision : on garde alors ECMWF seul.
     */
    private suspend fun fetchGfsWind(lat: Double, lon: Double, days: Int): Map<LocalDateTime, Triple<Double, Double, Double>> {
        return try {
            val url = "https://api.open-meteo.com/v1/forecast?" +
                "latitude=$lat&longitude=$lon" +
                "&hourly=wind_speed_10m,wind_direction_10m,wind_gusts_10m" +
                "&models=$GFS_MODEL" +
                "&forecast_days=$days" +
                "&timezone=auto"
            val hourly = httpGet(url).getValue("hourly").jsonObject
            val timeArr = hourly.getValue("time").jsonArray
            val speedArr = hourly.optArray("wind_speed_10m") ?: hourly.optArray("wind_speed_10m_$GFS_MODEL")
            val dirArr = hourly.optArray("wind_direction_10m") ?: hourly.optArray("wind_direction_10m_$GFS_MODEL")
            val gustArr = hourly.optArray("wind_gusts_10m") ?: hourly.optArray("wind_gusts_10m_$GFS_MODEL")
            if (speedArr == null || dirArr == null) return emptyMap()
            buildMap {
                for (i in timeArr.indices) {
                    val speed = speedArr.doubleAt(i) ?: continue
                    val dir = dirArr.doubleAt(i) ?: continue
                    put(LocalDateTime.parse(timeArr[i].jsonPrimitive.content), Triple(speed, dir, gustArr.doubleAt(i) ?: speed))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /** Moyenne de deux directions de vent (en degrés) par les vecteurs, pour que 350° et 10° donnent 0° et non 180°. */
    private fun meanDirection(a: Double, b: Double): Double {
        val ra = a * PI / 180.0
        val rb = b * PI / 180.0
        val x = cos(ra) + cos(rb)
        val y = sin(ra) + sin(rb)
        if (x * x + y * y < 1e-9) return a
        val deg = atan2(y, x) * 180.0 / PI
        return (deg + 360.0) % 360.0
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
            val consensusDeferred = if (config.consensus) async { buildConsensusMarine(lat, lon, today) } else null
            val shortMarineDeferred = async { consensusDeferred?.await() ?: fetchMarineBlock(lat, lon, config.shortTermWave, 2) }
            val longMarineDeferred = async { consensusDeferred?.await() ?: fetchMarineBlock(lat, lon, config.longTermWave, 7) }
            val shortWeatherDeferred = async { fetchWeatherBlock(lat, lon, config.shortTermWeather, 2) }
            val aromeHdWindDeferred = async {
                if (config.shortTermWeather == WeatherModel.AROME) fetchAromeHdWind(lat, lon) else emptyMap()
            }
            // Lever/coucher du soleil : donnée astronomique, demandée une seule fois (appel 7 jours).
            val longWeatherDeferred = async {
                fetchWeatherBlock(lat, lon, config.longTermWeather, 7, includeDailySun = true)
            }

            // Le ciel, la température, la pluie et les nuages viennent de Météo-France : AROME en court terme, puis ARPEGE aussi loin qu'il va
            // (environ 4 jours) ; au-delà, ECMWF prend le relais. Le vent reste la moyenne ECMWF + GFS.
            val mfWeatherDeferred = async {
                runCatching { fetchWeatherBlock(lat, lon, WeatherModel.ARPEGE, 7) }.getOrNull()
            }
            val gfsWindDeferred = async { fetchGfsWind(lat, lon, 7) }
            val seaTempDeferred = async { fetchSeaTemperature(lat, lon, 7) }
            val referencePeriodsDeferred = async { if (config.consensus) emptyMap() else fetchReferencePeriods(lat, lon, 7) }

            val shortMarine = shortMarineDeferred.await()
            val longMarine = longMarineDeferred.await()
            val shortWeather = shortWeatherDeferred.await()
            val longWeather = longWeatherDeferred.await()
            val mfWeather = mfWeatherDeferred.await()
            val aromeHdWind = aromeHdWindDeferred.await()
            val gfsWind = gfsWindDeferred.await()
            val seaTemps = seaTempDeferred.await()
            val referencePeriods = referencePeriodsDeferred.await()

            val marineMapShort = shortMarine.times.indices.associateBy { shortMarine.times[it] }
            val weatherMapShort = shortWeather.times.indices.associateBy { shortWeather.times[it] }
            val weatherMapLong = longWeather.times.indices.associateBy { longWeather.times[it] }
            val weatherMapMf = mfWeather?.let { mf -> mf.times.indices.associateBy { mf.times[it] } }.orEmpty()

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

                // Source du ciel de cette heure : le modèle court terme (AROME), sinon ARPEGE s'il a l'heure, sinon le long terme.
                val mfIndex = weatherMapMf[t]
                val mfHasHour = mfWeather != null && mfIndex != null && !mfWeather.windSpeeds.getOrElse(mfIndex) { Double.NaN }.isNaN()
                val (wxData, wxIndex) = when {
                    wData === shortWeather -> wData to wIndex
                    mfHasHour -> mfWeather!! to mfIndex!!
                    else -> wData to wIndex
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
                val gfsGust = gfsWind[t]?.third
                val gustRaw = when {
                    !longGust.isNaN() && gfsGust != null && ecmwfOkEarly(longSpeed, longDir) -> (longGust + gfsGust) / 2.0
                    gfsGust != null && longGust.isNaN() -> gfsGust
                    !longGust.isNaN() && ecmwfOkEarly(longSpeed, longDir) -> longGust
                    !primaryGust.isNaN() -> primaryGust
                    !longGust.isNaN() -> longGust
                    else -> Double.NaN
                }
                // Vent affiché : la moyenne des deux modèles globaux (ECMWF et GFS), plus crédible que l'AROME sur la côte (relevé du
                // 12 octobre : 8 km/h contre 15 à 19 sur les autres sites). L'AROME HD n'est plus qu'une comparaison, notée dans la source.
                val gfs = gfsWind[t]
                val ecmwfOk = !longSpeed.isNaN() && !longDir.isNaN()
                val hdNote = hd?.let { " · AROME HD ${it.first.roundToInt()}" }.orEmpty()
                val globalWind: Triple<Double, Double, String>? = when {
                    ecmwfOk && gfs != null -> Triple((longSpeed + gfs.first) / 2.0, meanDirection(longDir, gfs.second), "ECMWF+GFS")
                    ecmwfOk -> Triple(longSpeed, longDir, "ECMWF")
                    gfs != null -> Triple(gfs.first, gfs.second, "GFS")
                    else -> null
                }
                val (windSpeedRaw, windDirDeg, windSource) = when {
                    globalWind != null -> Triple(globalWind.first, globalWind.second, globalWind.third + hdNote)
                    hd != null -> Triple(hd.first, hd.second, "AROME HD")
                    !primarySpeed.isNaN() && !primaryDir.isNaN() -> Triple(
                        primarySpeed,
                        primaryDir,
                        if (wData === shortWeather) config.shortTermWeather.name else config.longTermWeather.name
                    )
                    !longSpeed.isNaN() && !longDir.isNaN() -> Triple(longSpeed, longDir, config.longTermWeather.name)
                    else -> Triple(0.0, 0.0, WIND_SOURCE_MISSING)
                }

                // Les vents annoncés par chaque modèle pour cette heure (ECMWF, GFS, AROME HD, AROME) : leur écart dit si le vent est incertain.
                val windVoices = listOfNotNull(
                    longSpeed.takeIf { ecmwfOk },
                    gfs?.first,
                    hd?.first,
                    primarySpeed.takeIf { isShortTerm && wData === shortWeather && !it.isNaN() }
                )
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
                        secondaryHeight = mData.secHeights.getOrElse(mIndex) { 0.0 },
                        secondaryPeriod = mData.secPeriods.getOrElse(mIndex) { 0.0 },
                        secondaryDirection = mData.secDirections.getOrElse(mIndex) { 0f },
                        modelSpread = mData.spreads.getOrElse(mIndex) { 0.0 },
                        periodMin = mData.periodMins.getOrElse(mIndex) { 0.0 },
                        periodMax = mData.periodMaxs.getOrElse(mIndex) { 0.0 },
                        windMinKmh = if (windVoices.size >= 2) windVoices.min().roundToInt() else 0,
                        windMaxKmh = if (windVoices.size >= 2) windVoices.max().roundToInt() else 0,
                        waveVoices = mData.voices.getOrElse(mIndex) { emptyList() }.takeIf { it.size >= 2 } ?: emptyList(),
                        windVoiceList = listOfNotNull(
                            if (ecmwfOk) ModelVoice("ECMWF", longSpeed, direction = longDir.toFloat()) else null,
                            gfs?.let { ModelVoice("GFS", it.first, direction = it.second.toFloat()) },
                            hd?.let { ModelVoice("AROME HD", it.first, direction = it.second.toFloat()) },
                            if (isShortTerm && wData === shortWeather && !primarySpeed.isNaN()) ModelVoice("AROME", primarySpeed, direction = primaryDir.toFloat()) else null
                        ).takeIf { it.size >= 2 } ?: emptyList(),
                        energyKj = calculateWaveEnergyReal(h, p),
                        windSpeedKmh = windSpeedRaw.roundToInt(),
                        windGustKmh = (if (gustRaw.isNaN()) windSpeedRaw else maxOf(gustRaw, windSpeedRaw)).roundToInt(),
                        windDirectionStr = getCardinalDirection(windDirDeg),
                        windSource = windSource,
                        weatherCode = wxData.weatherCodes.getOrElse(wxIndex) { 0 },
                        temperature = wxData.temperatures.getOrElse(wxIndex) { 20.0 }.roundToInt(),
                        cloudCover = wxData.cloudCovers.getOrElse(wxIndex) { 0 },
                        // L'indice UV : celui de Météo-France s'il en donne un, sinon celui du long terme.
                        uvIndex = wxData.uvIndexes.getOrElse(wxIndex) { 0.0 }.takeIf { it > 0.0 }
                            ?: wData.uvIndexes.getOrElse(wIndex) { 0.0 },
                        precipitationMm = wxData.precipitations.getOrElse(wxIndex) { 0.0 },
                        feelsLike = wxData.apparentTemperatures.getOrElse(wxIndex) {
                            wxData.temperatures.getOrElse(wxIndex) { 20.0 }
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
                var estimated = false
                val rawDays: List<MareeDayData> = if (nearest == null || (distance ?: 0.0) <= MAX_FRENCH_TIDE_KM) {
                    // Le service des marées en panne (et pas de copie de secours) : on estime les marées plutôt que de n'en montrer aucune.
                    runCatching { fetchMareeExtrema(nearest?.siteId ?: "cordouan", fromDate, toDate) }.getOrNull()
                        ?: estimatedTideDays(lat, lon, runCatching { estimatedBrestCoefs() }.getOrDefault(emptyList())).also { estimated = true }
                } else {
                    // Hors France : on lit le coefficient chez le port français le plus proche (même date,
                    // PM la plus proche en heure). Le SHOM le définit à partir de Brest, mais chaque port
                    // le publie rattaché à sa propre marée : le port voisin est le plus fidèle.
                    val referenceSite = nearest
                    val french = runCatching { fetchMareeExtrema(referenceSite.siteId, fromDate, toDate) }.getOrDefault(emptyList())
                    estimatedTideDays(lat, lon, french)
                }

                // Le site de référence peut être dans l'estuaire alors que le spot est sur la côte ouverte : la marée y arrive plus tard.
                val shiftMinutes = nearest?.takeIf { (distance ?: 0.0) in 15.0..MAX_FRENCH_TIDE_KM }?.let { SITE_TIDE_SHIFT_MINUTES[it.siteId] } ?: 0
                val days = if (shiftMinutes != 0 && !estimated) shiftMareeDays(rawDays, shiftMinutes) else rawDays

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
                        coefficient = coef,
                        extrema = extrema,
                        estimated = estimated
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
        // La hauteur d'eau d'Open-Meteo est celle du large : la marée y arrive plus tôt que sur la plage (retard de la côte).
        val shift = estimatedTideShiftMinutes(lat, lon)
        val shifted = if (shift == 0) times else times.map {
            it.toInstant(TimeZone.UTC).plus(shift, DateTimeUnit.MINUTE, TimeZone.UTC).toLocalDateTime(TimeZone.UTC)
        }
        return tideDaysFromSeaLevel(shifted, heights, frenchCoefs)
    }

    /**
     * Coefficients estimés quand api-maree.fr ne répond pas : le SHOM définit le coefficient à partir du marnage de Brest
     * (marnage ÷ 6,1 m × 100). On lit la hauteur d'eau d'Open-Meteo à Brest et on en tire le marnage de chaque pleine mer.
     */
    private suspend fun estimatedBrestCoefs(): List<MareeDayData> {
        val url = "https://marine-api.open-meteo.com/v1/marine?latitude=48.38&longitude=-4.49" +
            "&hourly=sea_level_height_msl&past_days=1&forecast_days=8&timezone=auto"
        val root = Json.parseToJsonElement(cachedText(url, 6 * 3600_000L) { httpGetText(url) }).jsonObject
        val hourly = root["hourly"]?.jsonObject ?: return emptyList()
        val times = hourly.getValue("time").jsonArray.map { LocalDateTime.parse(it.jsonPrimitive.content) }
        val levels = hourly.optArray("sea_level_height_msl") ?: return emptyList()
        return brestCoefDays(times, times.indices.map { levels.doubleAt(it) })
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

/**
 * Retard (minutes) à ajouter aux marées estimées avec la hauteur d'eau du large d'Open-Meteo. Mesuré le 13 octobre 2026 à Montalivet
 * contre les horaires du SHOM : pleines mers estimées à 06:03 et 18:22 pour 07:11 et 19:28, basse mer 12:04 pour 13:18, soit environ
 * 1 h 08 de retard. Appliqué à la côte des Landes et de la Gironde ; ailleurs, aucune correction connue (à mesurer quand api-maree.fr revient).
 */
internal fun estimatedTideShiftMinutes(lat: Double, lon: Double): Int =
    if (lat in 43.3..46.1 && lon in -2.1..-0.9) 68 else 0

/**
 * Écart (minutes) entre l'horaire d'un site de référence d'api-maree.fr et celui d'une plage voisine sur la côte ouverte.
 * Richards est dans l'estuaire de la Gironde : pour Montalivet, l'appli donnait 13 h 48 pour une basse mer annoncée à 13 h 11
 * ailleurs (13 octobre 2026), soit environ 33 minutes de trop.
 */
internal val SITE_TIDE_SHIFT_MINUTES: Map<String, Int> = mapOf("richards" to -33)

/** Décale tous les horaires de marée de [minutes] (les dates changent si on passe minuit). Hauteurs et coefficients restent. */
internal fun shiftMareeDays(days: List<MareeDayData>, minutes: Int): List<MareeDayData> {
    class T(val at: LocalDateTime, val e: MareeExtremum)
    val shifted = days.flatMap { day ->
        val date = LocalDate.parse(day.date)
        day.extrema.orEmpty().map { e ->
            val base = LocalDateTime(date, LocalTime(e.time.substringBefore(':').toInt(), e.time.substringAfter(':').toInt()))
            T(base.toInstant(TimeZone.UTC).plus(minutes, DateTimeUnit.MINUTE, TimeZone.UTC).toLocalDateTime(TimeZone.UTC), e)
        }
    }
    return shifted.groupBy { it.at.date }.entries.sortedBy { it.key }.map { (date, list) ->
        MareeDayData(date.toString(), list.sortedBy { it.at }.map { t ->
            t.e.copy(time = t.at.hour.toString().padStart(2, '0') + ":" + t.at.minute.toString().padStart(2, '0'))
        })
    }
}

/** Le marnage de référence du SHOM : un coefficient de 100 vaut 6,1 m de marnage à Brest. */
internal const val BREST_REFERENCE_RANGE_M = 6.1

/**
 * Les pleines mers de Brest avec leur coefficient estimé : marnage (pleine mer moins la moyenne des basses mers voisines)
 * divisé par [BREST_REFERENCE_RANGE_M], entre 20 et 120. Sert de coefficient de secours, repris par les ports voisins.
 */
internal fun brestCoefDays(times: List<LocalDateTime>, heights: List<Double?>): List<MareeDayData> {
    class T(val date: String, val time: String, val isHigh: Boolean, val height: Double)
    val all = tideDaysFromSeaLevel(times, heights, emptyList()).flatMap { day ->
        day.extrema.orEmpty().map { T(day.date, it.time, it.type == "PM", it.height) }
    }
    val withCoef = ArrayList<Pair<T, Int>>()
    for (i in all.indices) {
        if (!all[i].isHigh) continue
        val lows = listOfNotNull(all.getOrNull(i - 1)?.takeIf { !it.isHigh }, all.getOrNull(i + 1)?.takeIf { !it.isHigh })
        if (lows.isEmpty()) continue
        val range = all[i].height - lows.map { it.height }.average()
        withCoef += all[i] to (range / BREST_REFERENCE_RANGE_M * 100).roundToInt().coerceIn(20, 120)
    }
    return withCoef.groupBy { it.first.date }.map { (date, list) ->
        MareeDayData(date, list.map { (t, c) -> MareeExtremum(type = "PM", time = t.time, coef = c) })
    }.sortedBy { it.date }
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

private fun ecmwfOkEarly(speed: Double, dir: Double) = !speed.isNaN() && !dir.isNaN()
