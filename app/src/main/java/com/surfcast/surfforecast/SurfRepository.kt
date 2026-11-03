package com.surfcast.surfforecast

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.PI
import kotlin.math.roundToInt

private const val AROME_HD_MODEL = "meteofrance_arome_france_hd"

/** Source du vent quand aucun modele n'a de valeur pour cette heure (vent affiche : 0). */
const val WIND_SOURCE_MISSING = "manquant"

class SurfRepository {

    private val apiMareeToken = "0093ca14ffeffadcf739be7cc77f4738"

    private suspend fun httpGet(urlString: String): String = withContext(Dispatchers.IO) {
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 10000
        conn.readTimeout = 10000

        val responseCode = conn.responseCode
        val stream = if (responseCode in 200..299) {
            conn.inputStream
        } else {
            conn.errorStream ?: throw Exception("Erreur HTTP $responseCode")
        }

        val reader = BufferedReader(InputStreamReader(stream))
        val response = StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            response.append(line)
        }
        reader.close()

        if (responseCode !in 200..299) {
            val errorJson = try {
                JSONObject(response.toString()).optString("reason", response.toString())
            } catch (e: Exception) {
                response.toString()
            }
            throw Exception("API Open-Meteo ($responseCode): $errorJson")
        }

        response.toString()
    }

    /**
     * Indice d'energie des vagues, calibre sur l'echelle couramment affichee par les
     * outils de prevision surf grand public (Surfline, Windguru, Surf-Forecast...).
     * Ces sites utilisent H^2 x T^2 (periode au carre, pas le flux d'energie physique
     * lineaire en T) car ca correlle mieux avec la puissance ressentie par le surfeur
     * a la casse. Constante 1.962 validee par comparaison directe avec Surf-Forecast.com
     * (ex: 1.2m/14s -> ~526 kJ, 0.9m/12s -> ~235 kJ chez eux).
     */
    private fun calculateWaveEnergyReal(heightMeters: Double, periodSeconds: Double): Int {
        val energy = 1.962 * heightMeters * heightMeters * periodSeconds * periodSeconds
        return energy.roundToInt().coerceAtLeast(0)
    }

    private data class RawMarineHourly(
        val times: List<LocalDateTime>,
        val waveHeights: List<Double>,
        val wavePeriods: List<Double>,
        val waveDirections: List<Float>,
        // Mer de vent (clapot) : vagues courtes generees localement par le vent, distinctes
        // de la houle (swell) ci-dessus qui vient d'une tempete au large. Pas de fallback
        // sur le total combine ici (contrairement a la houle) : 0 est une valeur legitime
        // (pas de clapot du tout), pas une absence de donnee a masquer.
        val windWaveHeights: List<Double> = emptyList(),
        val windWavePeriods: List<Double> = emptyList(),
        val windWaveDirections: List<Float> = emptyList()
    )

    private data class RawWeatherHourly(
        val times: List<LocalDateTime>,
        val temperatures: List<Double>,
        val weatherCodes: List<Int>,
        val windSpeeds: List<Double>,
        val windDirections: List<Double>,
        val windGusts: List<Double>,
        val cloudCovers: List<Int>,
        val apparentTemperatures: List<Double> = emptyList(),
        val sunriseByDate: Map<LocalDate, LocalTime> = emptyMap(),
        val sunsetByDate: Map<LocalDate, LocalTime> = emptyMap()
    )

    private suspend fun fetchMarineBlock(
        lat: Double,
        lon: Double,
        waveModel: WaveModel,
        days: Int
    ): RawMarineHourly {
        val url = "https://marine-api.open-meteo.com/v1/marine?" +
                "latitude=$lat&longitude=$lon" +
                "&hourly=wave_height,wave_period,wave_direction,swell_wave_height,swell_wave_period,swell_wave_direction,swell_wave_peak_period,wind_wave_height,wind_wave_direction,wind_wave_peak_period,wind_wave_period" +
                "&models=${waveModel.apiParam}" +
                "&forecast_days=$days" +
                "&timezone=auto"

        val jsonStr = httpGet(url)
        val root = JSONObject(jsonStr)
        val hourly = root.getJSONObject("hourly")

        val timeArr = hourly.getJSONArray("time")

        val swellHArr = hourly.optJSONArray("swell_wave_height")
        val swellPArr = hourly.optJSONArray("swell_wave_period")
        val swellPeakArr = hourly.optJSONArray("swell_wave_peak_period")
        val windPeakArr = hourly.optJSONArray("wind_wave_peak_period")
        val windMeanPArr = hourly.optJSONArray("wind_wave_period")
        val swellDArr = hourly.optJSONArray("swell_wave_direction")
        val windWaveHArr = hourly.optJSONArray("wind_wave_height")
        val windWaveDArr = hourly.optJSONArray("wind_wave_direction")

        val waveHeightArr = hourly.getJSONArray("wave_height")
        val wavePeriodArr = hourly.getJSONArray("wave_period")
        val waveDirArr = hourly.getJSONArray("wave_direction")

        val times = mutableListOf<LocalDateTime>()
        val waveHeights = mutableListOf<Double>()
        val wavePeriods = mutableListOf<Double>()
        val waveDirs = mutableListOf<Float>()
        val windWaveHeights = mutableListOf<Double>()
        val windWavePeriods = mutableListOf<Double>()
        val windWaveDirs = mutableListOf<Float>()

        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

        for (i in 0 until timeArr.length()) {
            times.add(LocalDateTime.parse(timeArr.getString(i), formatter))

            val swellH = if (swellHArr != null && !swellHArr.isNull(i)) swellHArr.getDouble(i) else Double.NaN
            val swellP = if (swellPArr != null && !swellPArr.isNull(i)) swellPArr.getDouble(i) else Double.NaN
            val swellPeak = if (swellPeakArr != null && !swellPeakArr.isNull(i)) swellPeakArr.getDouble(i) else Double.NaN
            val windPeak = if (windPeakArr != null && !windPeakArr.isNull(i)) windPeakArr.getDouble(i) else Double.NaN
            val swellD = if (swellDArr != null && !swellDArr.isNull(i)) swellDArr.getDouble(i).toFloat() else Float.NaN

            val totalH = if (waveHeightArr.isNull(i)) 0.0 else waveHeightArr.getDouble(i)
            val totalP = if (wavePeriodArr.isNull(i)) 0.0 else wavePeriodArr.getDouble(i)
            val totalD = if (waveDirArr.isNull(i)) 0f else waveDirArr.getDouble(i).toFloat()

            val finalH = if (!swellH.isNaN() && swellH > 0.0) swellH else totalH
            // Periode de PIC (celle du train de houle le plus energetique, comme
            // affichee par les outils surf classiques) plutot que la periode moyenne.
            // Jamais la periode de la mer de vent (clapot, 4-8 s) : associee a la hauteur
            // de houle, elle affichait des "0.9m - 8s" qui ne correspondaient a rien.
            val finalP = when {
                !swellPeak.isNaN() && swellPeak > 0.0 -> swellPeak
                !swellP.isNaN() && swellP > 0.0 -> swellP
                else -> totalP
            }
            val finalD = if (!swellD.isNaN()) swellD else totalD

            waveHeights.add(finalH)
            wavePeriods.add(finalP)
            waveDirs.add(finalD)

            val windWaveH = if (windWaveHArr != null && !windWaveHArr.isNull(i)) windWaveHArr.getDouble(i) else 0.0
            val windWaveD = if (windWaveDArr != null && !windWaveDArr.isNull(i)) windWaveDArr.getDouble(i).toFloat() else 0f
            windWaveHeights.add(windWaveH)
            // Periode du clapot : pic, sinon periode moyenne, sinon 0 = inconnue (affichee "—").
            val windMeanP = if (windMeanPArr != null && !windMeanPArr.isNull(i)) windMeanPArr.getDouble(i) else Double.NaN
            windWavePeriods.add(
                when {
                    !windPeak.isNaN() && windPeak > 0.0 -> windPeak
                    !windMeanP.isNaN() && windMeanP > 0.0 -> windMeanP
                    else -> 0.0
                }
            )
            windWaveDirs.add(windWaveD)
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

        val jsonStr = httpGet(url)
        val root = JSONObject(jsonStr)
        val hourly = root.getJSONObject("hourly")

        val timeArr = hourly.getJSONArray("time")

        fun getArray(baseKey: String): JSONArray {
            if (hourly.has(baseKey)) return hourly.getJSONArray(baseKey)
            val modelKey = "${baseKey}_${weatherModel.apiParam}"
            if (hourly.has(modelKey)) return hourly.getJSONArray(modelKey)
            val keys = hourly.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                if (k.startsWith(baseKey)) return hourly.getJSONArray(k)
            }
            return JSONArray()
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

        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

        for (i in 0 until timeArr.length()) {
            times.add(LocalDateTime.parse(timeArr.getString(i), formatter))
            val temp = if (i < tempArr.length() && !tempArr.isNull(i)) tempArr.getDouble(i) else 20.0
            temps.add(temp)
            codes.add(if (i < codeArr.length() && !codeArr.isNull(i)) codeArr.getInt(i) else 0)
            // NaN = pas de valeur pour cette heure : c'est la fusion qui choisira un autre
            // modele, au lieu d'inventer un vent (ancien defaut : 10 km/h de Nord).
            windSpeeds.add(if (i < windSpeedArr.length() && !windSpeedArr.isNull(i)) windSpeedArr.getDouble(i) else Double.NaN)
            windDirs.add(if (i < windDirArr.length() && !windDirArr.isNull(i)) windDirArr.getDouble(i) else Double.NaN)
            windGusts.add(if (i < windGustArr.length() && !windGustArr.isNull(i)) windGustArr.getDouble(i) else Double.NaN)
            clouds.add(if (i < cloudArr.length() && !cloudArr.isNull(i)) cloudArr.getInt(i) else 0)
            // Repli sur la temperature de l'air si le ressenti n'est pas dispo pour cette
            // heure (plutot que 20.0 par defaut, qui n'a pas de sens comme "ressenti").
            apparentTemps.add(if (i < apparentArr.length() && !apparentArr.isNull(i)) apparentArr.getDouble(i) else temp)
        }

        val sunriseByDate = mutableMapOf<LocalDate, LocalTime>()
        val sunsetByDate = mutableMapOf<LocalDate, LocalTime>()
        if (includeDailySun) {
            val dailyObj = root.optJSONObject("daily")
            if (dailyObj != null) {
                val dTimeArr = dailyObj.optJSONArray("time")
                val sunriseArr = dailyObj.optJSONArray("sunrise")
                val sunsetArr = dailyObj.optJSONArray("sunset")
                if (dTimeArr != null && sunriseArr != null && sunsetArr != null) {
                    for (i in 0 until dTimeArr.length()) {
                        try {
                            val date = LocalDate.parse(dTimeArr.getString(i))
                            sunriseByDate[date] = LocalDateTime.parse(sunriseArr.getString(i), formatter).toLocalTime()
                            sunsetByDate[date] = LocalDateTime.parse(sunsetArr.getString(i), formatter).toLocalTime()
                        } catch (e: Exception) {
                            // Jour ignoré si le parsing echoue, le repli 7h-21h prend le relais en amont.
                        }
                    }
                }
            }
        }

        return RawWeatherHourly(times, temps, codes, windSpeeds, windDirs, windGusts, clouds, apparentTemps, sunriseByDate, sunsetByDate)
    }

    /**
     * Vent AROME HD (maille ~1.3 km au lieu de 2.5 km pour AROME "standard"), plus fin
     * pres des cotes. Ne demande que le vent : ce modele ne fournit pas toutes les autres
     * variables (nebulosite, code meteo...). Heures absentes -> non incluses dans la map.
     * Un echec (modele indisponible, reseau) ne doit jamais faire echouer la prevision :
     * on retombe simplement sur AROME standard.
     */
    private suspend fun fetchAromeHdWind(lat: Double, lon: Double): Map<LocalDateTime, Pair<Double, Double>> =
        runCatching {
            val url = "https://api.open-meteo.com/v1/forecast?" +
                    "latitude=$lat&longitude=$lon" +
                    "&hourly=wind_speed_10m,wind_direction_10m" +
                    "&models=$AROME_HD_MODEL" +
                    "&forecast_days=2" +
                    "&timezone=auto"
            val hourly = JSONObject(httpGet(url)).getJSONObject("hourly")
            fun arr(base: String): JSONArray? =
                hourly.optJSONArray(base) ?: hourly.optJSONArray("${base}_$AROME_HD_MODEL")
            val timeArr = hourly.getJSONArray("time")
            val speedArr = arr("wind_speed_10m") ?: return@runCatching emptyMap()
            val dirArr = arr("wind_direction_10m") ?: return@runCatching emptyMap()
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
            buildMap {
                for (i in 0 until timeArr.length()) {
                    if (i >= speedArr.length() || i >= dirArr.length()) break
                    if (speedArr.isNull(i) || dirArr.isNull(i)) continue
                    put(LocalDateTime.parse(timeArr.getString(i), formatter), speedArr.getDouble(i) to dirArr.getDouble(i))
                }
            }
        }.getOrDefault(emptyMap())

    suspend fun getHybridForecast(
        lat: Double,
        lon: Double,
        config: ForecastEngineConfig
    ): HybridForecastResult = withContext(Dispatchers.Default) {
        val today = LocalDate.now()
        val cutoffDate = today.plusDays(2)

        // Meme principe que pour la meteo : MFWAM (court terme) est plus precis sur la
        // bathymetrie cotiere francaise mais sa portee est limitee, ECMWF WAM (long terme)
        // prend le relais au-dela pour la houle longue distance.
        // Les 4 appels sont independants : on les lance en parallele plutot qu'en
        // sequence pour ne pas cumuler leurs latences reseau.
        val shortMarineDeferred = async { fetchMarineBlock(lat, lon, config.shortTermWave, 2) }
        val longMarineDeferred = async { fetchMarineBlock(lat, lon, config.longTermWave, 7) }
        val shortWeatherDeferred = async { fetchWeatherBlock(lat, lon, config.shortTermWeather, 2) }
        val aromeHdWindDeferred = async {
            if (config.shortTermWeather == WeatherModel.AROME) fetchAromeHdWind(lat, lon) else emptyMap()
        }
        // Le lever/coucher du soleil est une donnee purement astronomique (independante
        // du modele meteo) : on ne la demande qu'une fois, sur l'appel long terme qui
        // couvre deja les 7 jours.
        val longWeatherDeferred = async {
            fetchWeatherBlock(lat, lon, config.longTermWeather, 7, includeDailySun = true)
        }

        val shortMarine = shortMarineDeferred.await()
        val longMarine = longMarineDeferred.await()
        val shortWeather = shortWeatherDeferred.await()
        val longWeather = longWeatherDeferred.await()
        val aromeHdWind = aromeHdWindDeferred.await()

        val marineMapShort = shortMarine.times.indices.associate { i -> shortMarine.times[i] to i }
        val weatherMapShort = shortWeather.times.indices.associate { i -> shortWeather.times[i] to i }
        val weatherMapLong = longWeather.times.indices.associate { i -> longWeather.times[i] to i }

        val resultList = mutableListOf<HourlyUiModel>()

        for (i in longMarine.times.indices) {
            val t = longMarine.times[i]
            val isShortTerm = t.toLocalDate() < cutoffDate

            val (mIndex, mData) = if (isShortTerm && marineMapShort.containsKey(t)) {
                marineMapShort[t]!! to shortMarine
            } else {
                i to longMarine
            }

            val (wIndex, wData) = if (isShortTerm && weatherMapShort.containsKey(t)) {
                weatherMapShort[t]!! to shortWeather
            } else {
                (weatherMapLong[t] ?: 0) to longWeather
            }

            val h = mData.waveHeights[mIndex]
            val p = mData.wavePeriods[mIndex]
            val dirFloat = mData.waveDirections[mIndex]
            val windWaveH = mData.windWaveHeights.getOrElse(mIndex) { 0.0 }
            val windWaveP = mData.windWavePeriods.getOrElse(mIndex) { 0.0 }
            val windWaveDir = mData.windWaveDirections.getOrElse(mIndex) { 0f }
            // Vent : AROME HD en court terme si dispo, sinon le modele meteo de cette heure,
            // sinon le modele long terme. Jamais de valeur inventee ; la source est gardee
            // pour le journal des previsions.
            val longIndex = weatherMapLong[t]
            val hd = if (isShortTerm) aromeHdWind[t] else null
            val primarySpeed = wData.windSpeeds.getOrElse(wIndex) { Double.NaN }
            val primaryDir = wData.windDirections.getOrElse(wIndex) { Double.NaN }
            val longSpeed = longIndex?.let { longWeather.windSpeeds.getOrElse(it) { Double.NaN } } ?: Double.NaN
            val longDir = longIndex?.let { longWeather.windDirections.getOrElse(it) { Double.NaN } } ?: Double.NaN
            // Rafales : celles du modele meteo de l'heure, sinon du long terme ; jamais
            // inferieures au vent moyen affiche.
            val primaryGust = wData.windGusts.getOrElse(wIndex) { Double.NaN }
            val longGust = longIndex?.let { longWeather.windGusts.getOrElse(it) { Double.NaN } } ?: Double.NaN
            val gustRaw = when {
                !primaryGust.isNaN() -> primaryGust
                !longGust.isNaN() -> longGust
                else -> Double.NaN
            }
            val (windSpeedRaw, windDirDeg, windSource) = when {
                hd != null -> Triple(hd.first, hd.second, "AROME HD")
                !primarySpeed.isNaN() && !primaryDir.isNaN() ->
                    Triple(primarySpeed, primaryDir, if (wData === shortWeather) config.shortTermWeather.name else config.longTermWeather.name)
                !longSpeed.isNaN() && !longDir.isNaN() -> Triple(longSpeed, longDir, config.longTermWeather.name)
                else -> Triple(0.0, 0.0, WIND_SOURCE_MISSING)
            }
            val windKmh = windSpeedRaw.roundToInt()

            resultList.add(
                HourlyUiModel(
                    timeFormatted = formatHour(t),
                    rawTime = t,
                    waveHeight = h,
                    wavePeriod = p,
                    waveDirection = dirFloat,
                    windWaveHeight = windWaveH,
                    windWavePeriod = windWaveP,
                    windWaveDirection = windWaveDir,
                    energyKj = calculateWaveEnergyReal(h, p),
                    windSpeedKmh = windKmh,
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
                sunrise = longWeather.sunriseByDate[date] ?: LocalTime.of(7, 0),
                sunset = longWeather.sunsetByDate[date] ?: LocalTime.of(21, 0)
            )
        }

        HybridForecastResult(
            hourly = resultList.sortedBy { it.rawTime },
            dailySun = dailySun
        )
    }

    /**
     * Marées du jour : dailyByDate n'en garde qu'une haute + une basse par jour, dans une
     * plage horaire "de jour" (pensé pour le surf) — c'est ce qu'affiche le reste de
     * l'appli. rawByDate garde TOUTES les marées du jour (typiquement 4 : 2 PM + 2 BM),
     * sans filtrage horaire : c'est ce dont le widget a besoin pour connaître la VRAIE
     * prochaine marée à venir, même si elle a lieu de nuit. Les deux viennent du même
     * appel réseau pour ne pas le dupliquer.
     */
    data class TidesBundle(
        val dailyByDate: Map<LocalDate, DailyTideInfo>,
        val rawByDate: Map<LocalDate, List<MareeExtremum>>
    )

    suspend fun getTides(
        lat: Double,
        lon: Double,
        fromDate: String,
        toDate: String
    ): TidesBundle = withContext(Dispatchers.IO) {
        try {
            val sites = mareeSites()
            val closest = sites.minByOrNull { distanceKm(lat, lon, it.latitude, it.longitude) }
            val distance = closest?.let { distanceKm(lat, lon, it.latitude, it.longitude) }
            val days: List<MareeDayData> = if (closest == null || (distance ?: 0.0) <= MAX_FRENCH_TIDE_KM) {
                mareeExtrema(closest?.siteId ?: "cordouan", fromDate, toDate)
            } else {
                // Hors France : horaires estimes (hauteur d'eau Open-Meteo), coefficient repris de la
                // France (indice defini au port de Brest par le SHOM, valable sur toute la cote atlantique).
                val reference = sites.firstOrNull { it.siteId.contains("brest", ignoreCase = true) }
                    ?: closest
                val french = runCatching { mareeExtrema(reference.siteId, fromDate, toDate) }.getOrDefault(emptyList())
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
                val coef = dayPm?.coef ?: extrema.firstNotNullOfOrNull { it.coef }

                dailyMap[parsedDate] = DailyTideInfo(
                    highTideTime = dayPm?.time,
                    lowTideTime = dayBm?.time,
                    coefficient = coef
                )
            }
            TidesBundle(dailyMap, rawMap)
        } catch (e: Exception) {
            TidesBundle(emptyMap(), emptyMap())
        }
    }

    // Relais Cloudflare d'abord (jeton cote serveur + cache), appel direct en repli.
    private suspend fun mareeSites(): List<MareeSite> {
        val viaRelay = runCatching { RetrofitClient.apiService.getMareeSites("$MAREE_RELAY_BASE/sites") }.getOrNull()?.sites
        if (!viaRelay.isNullOrEmpty()) return viaRelay
        return RetrofitClient.apiService.getMareeSites().sites ?: emptyList()
    }

    private suspend fun mareeExtrema(siteId: String, from: String, to: String): List<MareeDayData> {
        val viaRelay = runCatching {
            RetrofitClient.apiService.getTideExtrema(url = "$MAREE_RELAY_BASE/tide-extrema", site = siteId, from = from, to = to, key = "")
        }.getOrNull()?.data
        if (viaRelay != null) return viaRelay
        return RetrofitClient.apiService.getTideExtrema(site = siteId, from = from, to = to, key = apiMareeToken).data ?: emptyList()
    }

    private suspend fun estimatedTideDays(lat: Double, lon: Double, french: List<MareeDayData>): List<MareeDayData> {
        val url = "https://marine-api.open-meteo.com/v1/marine?latitude=$lat&longitude=$lon" +
            "&hourly=sea_level_height_msl&past_days=1&forecast_days=8&timezone=auto"
        val hourly = JSONObject(httpGet(url)).optJSONObject("hourly") ?: return emptyList()
        val timeArr = hourly.getJSONArray("time")
        val levels = hourly.optJSONArray("sea_level_height_msl") ?: return emptyList()
        val times = (0 until timeArr.length()).map { LocalDateTime.parse(timeArr.getString(it)) }
        val heights = (0 until timeArr.length()).map { if (it < levels.length() && !levels.isNull(it)) levels.getDouble(it) else null }
        val frenchCoefs = french.flatMap { day ->
            day.extrema.orEmpty().filter { it.type == "PM" && it.coef != null }.map { Triple(day.date, it.time, it.coef!!) }
        }
        return tideDaysFromSeaLevel(times, heights, frenchCoefs)
    }
}

/** Relais Cloudflare des marees : jeton cote serveur + cache. Repli : appel direct. */
const val MAREE_RELAY_BASE = "https://surflog.fr/api/maree"

/** Au-dela, le site de maree francais n'est plus representatif du spot : on estime. */
const val MAX_FRENCH_TIDE_KM = 80.0

/** Distance approximative (km) entre deux points, suffisante pour choisir un port. */
internal fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
        Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2)
    return 2 * r * Math.asin(Math.sqrt(a))
}

/**
 * Pleines et basses mers deduites d'une serie horaire de hauteur d'eau (Open-Meteo, maree comprise) :
 * maximums et minimums locaux, affines par une parabole sur 3 points (~15 min). Chaque PM recoit le
 * coefficient francais de la meme date le plus proche en heure. Hauteurs relatives (0 = plus basse mer).
 */
internal fun tideDaysFromSeaLevel(
    times: List<LocalDateTime>,
    heights: List<Double?>,
    frenchCoefs: List<Triple<String, String, Int>>
): List<MareeDayData> {
    class Raw(val date: LocalDate, val minutes: Int, val isHigh: Boolean, val height: Double)
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
        var date = times[i].toLocalDate()
        if (minutes < 0) { minutes += 1440; date = date.minusDays(1) }
        if (minutes >= 1440) { minutes -= 1440; date = date.plusDays(1) }
        raws.add(Raw(date, minutes, isHigh, peak))
    }
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
    fun hhmm(minutes: Int) = "%02d:%02d".format(minutes / 60, minutes % 60)
    fun minutesOf(text: String): Int {
        val parts = text.split(':')
        return (parts.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (parts.getOrNull(1)?.toIntOrNull() ?: 0)
    }
    return alternating.groupBy { it.date }.entries.sortedBy { it.key }.map { (date, list) ->
        MareeDayData(
            date = date.toString(),
            extrema = list.sortedBy { it.minutes }.map { r ->
                val coef = if (r.isHigh) {
                    frenchCoefs.filter { it.first == date.toString() }
                        .minByOrNull { Math.abs(minutesOf(it.second) - r.minutes) }?.third
                } else null
                MareeExtremum(
                    type = if (r.isHigh) "PM" else "BM",
                    time = hhmm(r.minutes),
                    height = ((r.height - floor) * 100).roundToInt() / 100.0,
                    coef = coef
                )
            }
        )
    }
}
