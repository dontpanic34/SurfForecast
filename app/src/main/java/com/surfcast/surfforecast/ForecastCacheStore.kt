package com.surfcast.surfforecast

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Dernier SurfUiState.Success connu, persisté sur disque pour pouvoir afficher quelque
 * chose de significatif (plutôt qu'un écran d'erreur vide) si l'appli démarre sans
 * réseau : au premier chargement d'une session, aucune donnée n'est encore en mémoire,
 * donc sans ce cache un échec réseau bloquait complètement l'appli.
 */
object ForecastCacheStore {
    private const val PREFS = "surf_prefs"
    private const val KEY = "forecast_cache_json"
    private val ISO_DATE_TIME = java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME

    fun save(context: Context, state: SurfUiState.Success) {
        val root = JSONObject()
        root.put("spotName", state.spotName)
        root.put("lastUpdatedTime", state.lastUpdatedTime)

        val hourlyArr = JSONArray()
        state.hourlyForecast.forEach { h ->
            hourlyArr.put(
                JSONObject().apply {
                    put("timeFormatted", h.timeFormatted)
                    put("rawTime", h.rawTime.format(ISO_DATE_TIME))
                    put("waveHeight", h.waveHeight)
                    put("wavePeriod", h.wavePeriod)
                    put("waveDirection", h.waveDirection.toDouble())
                    put("windWaveHeight", h.windWaveHeight)
                    put("windWavePeriod", h.windWavePeriod)
                    put("windWaveDirection", h.windWaveDirection.toDouble())
                    put("energyKj", h.energyKj)
                    put("windSpeedKmh", h.windSpeedKmh)
                    put("windDirectionStr", h.windDirectionStr)
                    put("weatherCode", h.weatherCode)
                    put("temperature", h.temperature)
                    put("cloudCover", h.cloudCover)
                    put("feelsLike", h.feelsLike)
                }
            )
        }
        root.put("hourlyForecast", hourlyArr)

        val summariesObj = JSONObject()
        state.dailySummaries.forEach { (date, s) ->
            summariesObj.put(
                date.toString(),
                JSONObject().apply {
                    put("avgFeelsLike", s.avgFeelsLike)
                    put("avgWaterTemp", s.avgWaterTemp)
                }
            )
        }
        root.put("dailySummaries", summariesObj)

        val tidesObj = JSONObject()
        state.dailyTides.forEach { (date, t) ->
            tidesObj.put(
                date.toString(),
                JSONObject().apply {
                    if (t.highTideTime != null) put("highTideTime", t.highTideTime)
                    if (t.lowTideTime != null) put("lowTideTime", t.lowTideTime)
                    if (t.coefficient != null) put("coefficient", t.coefficient)
                }
            )
        }
        root.put("dailyTides", tidesObj)

        val sunObj = JSONObject()
        state.dailySunInfo.forEach { (date, s) ->
            sunObj.put(
                date.toString(),
                JSONObject().apply {
                    put("sunrise", s.sunrise.toString())
                    put("sunset", s.sunset.toString())
                }
            )
        }
        root.put("dailySunInfo", sunObj)

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY, root.toString())
        }
    }

    fun load(context: Context): SurfUiState.Success? {
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return null
        return try {
            val root = JSONObject(json)

            val hourlyArr = root.getJSONArray("hourlyForecast")
            val hourly = (0 until hourlyArr.length()).map { i ->
                val o = hourlyArr.getJSONObject(i)
                HourlyUiModel(
                    timeFormatted = o.getString("timeFormatted"),
                    rawTime = LocalDateTime.parse(o.getString("rawTime")),
                    waveHeight = o.getDouble("waveHeight"),
                    wavePeriod = o.getDouble("wavePeriod"),
                    waveDirection = o.getDouble("waveDirection").toFloat(),
                    windWaveHeight = o.optDouble("windWaveHeight", 0.0),
                    windWavePeriod = o.optDouble("windWavePeriod", 0.0),
                    windWaveDirection = o.optDouble("windWaveDirection", 0.0).toFloat(),
                    energyKj = o.getInt("energyKj"),
                    windSpeedKmh = o.getInt("windSpeedKmh"),
                    windDirectionStr = o.getString("windDirectionStr"),
                    weatherCode = o.getInt("weatherCode"),
                    temperature = o.getInt("temperature"),
                    cloudCover = o.optInt("cloudCover", 0),
                    feelsLike = if (o.has("feelsLike")) o.getInt("feelsLike") else o.getInt("temperature")
                )
            }

            val summariesObj = root.getJSONObject("dailySummaries")
            val summaries = summariesObj.keys().asSequence().associate { key ->
                val o = summariesObj.getJSONObject(key)
                LocalDate.parse(key) to DailySummaryUiModel(
                    avgFeelsLike = o.getInt("avgFeelsLike"),
                    avgWaterTemp = o.getInt("avgWaterTemp")
                )
            }

            val tidesObj = root.getJSONObject("dailyTides")
            val tides = tidesObj.keys().asSequence().associate { key ->
                val o = tidesObj.getJSONObject(key)
                LocalDate.parse(key) to DailyTideInfo(
                    highTideTime = if (o.has("highTideTime")) o.getString("highTideTime") else null,
                    lowTideTime = if (o.has("lowTideTime")) o.getString("lowTideTime") else null,
                    coefficient = if (o.has("coefficient")) o.getInt("coefficient") else null
                )
            }

            val sunObj = root.getJSONObject("dailySunInfo")
            val sun = sunObj.keys().asSequence().associate { key ->
                val o = sunObj.getJSONObject(key)
                LocalDate.parse(key) to DailySunInfo(
                    sunrise = LocalTime.parse(o.getString("sunrise")),
                    sunset = LocalTime.parse(o.getString("sunset"))
                )
            }

            SurfUiState.Success(
                spotName = root.getString("spotName"),
                hourlyForecast = hourly,
                dailySummaries = summaries,
                dailyTides = tides,
                dailySunInfo = sun,
                lastUpdatedTime = root.getString("lastUpdatedTime")
            )
        } catch (e: Exception) {
            null
        }
    }
}
