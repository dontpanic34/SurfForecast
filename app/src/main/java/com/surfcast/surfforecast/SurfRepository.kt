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
        val waveDirections: List<Float>
    )

    private data class RawWeatherHourly(
        val times: List<LocalDateTime>,
        val temperatures: List<Double>,
        val weatherCodes: List<Int>,
        val windSpeeds: List<Double>,
        val windDirections: List<Double>,
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
                "&hourly=wave_height,wave_period,wave_direction,swell_wave_height,swell_wave_period,swell_wave_direction,swell_wave_peak_period,wind_wave_peak_period" +
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
        val swellDArr = hourly.optJSONArray("swell_wave_direction")

        val waveHeightArr = hourly.getJSONArray("wave_height")
        val wavePeriodArr = hourly.getJSONArray("wave_period")
        val waveDirArr = hourly.getJSONArray("wave_direction")

        val times = mutableListOf<LocalDateTime>()
        val waveHeights = mutableListOf<Double>()
        val wavePeriods = mutableListOf<Double>()
        val waveDirs = mutableListOf<Float>()

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
        }

        return RawMarineHourly(times, waveHeights, wavePeriods, waveDirs)
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
        val cloudArr = getArray("cloudcover")
        val apparentArr = getArray("apparent_temperature")

        val times = mutableListOf<LocalDateTime>()
        val temps = mutableListOf<Double>()
        val codes = mutableListOf<Int>()
        val windSpeeds = mutableListOf<Double>()
        val windDirs = mutableListOf<Double>()
        val clouds = mutableListOf<Int>()
        val apparentTemps = mutableListOf<Double>()

        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

        for (i in 0 until timeArr.length()) {
            times.add(LocalDateTime.parse(timeArr.getString(i), formatter))
            val temp = if (i < tempArr.length() && !tempArr.isNull(i)) tempArr.getDouble(i) else 20.0
            temps.add(temp)
            codes.add(if (i < codeArr.length() && !codeArr.isNull(i)) codeArr.getInt(i) else 0)
            windSpeeds.add(if (i < windSpeedArr.length() && !windSpeedArr.isNull(i)) windSpeedArr.getDouble(i) else 10.0)
            windDirs.add(if (i < windDirArr.length() && !windDirArr.isNull(i)) windDirArr.getDouble(i) else 0.0)
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

        return RawWeatherHourly(times, temps, codes, windSpeeds, windDirs, clouds, apparentTemps, sunriseByDate, sunsetByDate)
    }

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
            val windKmh = wData.windSpeeds.getOrElse(wIndex) { 10.0 }.roundToInt()
            val windDirDeg = wData.windDirections.getOrElse(wIndex) { 0.0 }

            resultList.add(
                HourlyUiModel(
                    timeFormatted = formatHour(t),
                    rawTime = t,
                    waveHeight = h,
                    wavePeriod = p,
                    waveDirection = dirFloat,
                    energyKj = calculateWaveEnergyReal(h, p),
                    windSpeedKmh = windKmh,
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
                sunrise = longWeather.sunriseByDate[date] ?: LocalTime.of(7, 0),
                sunset = longWeather.sunsetByDate[date] ?: LocalTime.of(21, 0)
            )
        }

        HybridForecastResult(
            hourly = resultList.sortedBy { it.rawTime },
            dailySun = dailySun
        )
    }

    suspend fun getTides(
        lat: Double,
        lon: Double,
        fromDate: String,
        toDate: String
    ): Map<LocalDate, DailyTideInfo> = withContext(Dispatchers.IO) {
        try {
            val sitesRes = RetrofitClient.apiService.getMareeSites()
            val closest = sitesRes.sites?.minByOrNull { site ->
                val dLat = site.latitude - lat
                val dLon = site.longitude - lon
                dLat * dLat + dLon * dLon
            }
            val targetSiteId = closest?.siteId ?: "cordouan"

            val extremaRes = RetrofitClient.apiService.getTideExtrema(
                site = targetSiteId,
                from = fromDate,
                to = toDate,
                key = apiMareeToken
            )

            val map = mutableMapOf<LocalDate, DailyTideInfo>()
            extremaRes.data?.forEach { dayData ->
                val parsedDate = LocalDate.parse(dayData.date)
                val extrema = dayData.extrema ?: emptyList()

                val dayPm = extrema.firstOrNull { it.type == "PM" && it.time >= "06:00" && it.time <= "21:30" }
                    ?: extrema.firstOrNull { it.type == "PM" }
                val dayBm = extrema.firstOrNull { it.type == "BM" && it.time >= "06:00" && it.time <= "21:30" }
                    ?: extrema.firstOrNull { it.type == "BM" }
                val coef = dayPm?.coef ?: extrema.firstNotNullOfOrNull { it.coef }

                map[parsedDate] = DailyTideInfo(
                    highTideTime = dayPm?.time,
                    lowTideTime = dayBm?.time,
                    coefficient = coef
                )
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }
}
