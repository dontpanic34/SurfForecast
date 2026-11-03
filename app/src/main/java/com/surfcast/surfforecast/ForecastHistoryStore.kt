package com.surfcast.surfforecast

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Journal des previsions : a chaque chargement reussi, on garde une copie de ce qui etait
 * annonce (vent, houle, heure par heure, avec le modele d'ou vient le vent), pour pouvoir
 * comparer apres coup avec la realite ou une autre appli ("qu'annoncait l'app hier soir
 * pour ce matin ?"), et pour le journal de bord.
 *
 * Retention : 2 jours (J-2). Seules les heures du jour du chargement jusqu'a J+2 sont
 * gardees, pour que le fichier reste petit.
 */
object ForecastHistoryStore {

    data class HourEntry(
        val time: LocalDateTime,
        val windKmh: Int,
        val windDir: String,
        val windSource: String,
        val waveHeight: Double,
        val wavePeriod: Double,
        val waveDirection: Float
    )

    data class Snapshot(
        val spotName: String,
        val loadedAt: LocalDateTime,
        val models: String,
        val hours: List<HourEntry>,
        // Marees des jours couverts : pour pouvoir noter apres coup une session de J-1/J-2.
        val tides: Map<LocalDate, DailyTideInfo> = emptyMap()
    )

    private const val FILE_NAME = "forecast_history.json"
    private const val RETENTION_DAYS = 2L
    private const val MAX_SNAPSHOTS = 120
    private val lock = Any()

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    fun record(
        context: Context,
        spotName: String,
        config: ForecastEngineConfig,
        forecast: List<HourlyUiModel>,
        tides: Map<LocalDate, DailyTideInfo> = emptyMap()
    ) {
        val now = LocalDateTime.now()
        val lastDay = now.toLocalDate().plusDays(2)
        val snapshot = Snapshot(
            spotName = spotName,
            loadedAt = now,
            models = "Vent ${config.shortTermWeather.displayName} / ${config.longTermWeather.displayName} · " +
                "Houle ${config.shortTermWave.displayName} / ${config.longTermWave.displayName}",
            hours = forecast
                .filter { it.rawTime.toLocalDate() >= now.toLocalDate() && it.rawTime.toLocalDate() <= lastDay }
                .map {
                    HourEntry(
                        time = it.rawTime,
                        windKmh = it.windSpeedKmh,
                        windDir = it.windDirectionStr,
                        windSource = it.windSource,
                        waveHeight = it.waveHeight,
                        wavePeriod = it.wavePeriod,
                        waveDirection = it.waveDirection
                    )
                },
            tides = tides.filterKeys { it >= now.toLocalDate() && it <= lastDay }
        )
        synchronized(lock) {
            val kept = (loadAll(context) + snapshot)
                .filter { it.loadedAt >= now.minusDays(RETENTION_DAYS) }
                .takeLast(MAX_SNAPSHOTS)
            file(context).writeText(toJson(kept).toString())
        }
    }

    /** Toutes les copies encore gardees, de la plus recente a la plus ancienne. */
    fun load(context: Context): List<Snapshot> = synchronized(lock) {
        val cutoff = LocalDateTime.now().minusDays(RETENTION_DAYS)
        loadAll(context).filter { it.loadedAt >= cutoff }.sortedByDescending { it.loadedAt }
    }

    private fun loadAll(context: Context): List<Snapshot> {
        val f = file(context)
        if (!f.exists()) return emptyList()
        // Fichier corrompu (ecriture interrompue...) : on repart de zero plutot que de planter.
        return runCatching { fromJson(JSONArray(f.readText())) }.getOrDefault(emptyList())
    }

    private fun toJson(snapshots: List<Snapshot>): JSONArray = JSONArray().apply {
        snapshots.forEach { s ->
            put(JSONObject().apply {
                put("spot", s.spotName)
                put("loadedAt", s.loadedAt.toString())
                put("models", s.models)
                put("tides", JSONObject().apply {
                    s.tides.forEach { (date, t) ->
                        put(date.toString(), JSONObject().apply {
                            if (t.highTideTime != null) put("high", t.highTideTime)
                            if (t.lowTideTime != null) put("low", t.lowTideTime)
                            if (t.coefficient != null) put("coef", t.coefficient)
                        })
                    }
                })
                put("hours", JSONArray().apply {
                    s.hours.forEach { h ->
                        put(JSONObject().apply {
                            put("t", h.time.toString())
                            put("w", h.windKmh)
                            put("wd", h.windDir)
                            put("ws", h.windSource)
                            put("h", h.waveHeight)
                            put("p", h.wavePeriod)
                            put("d", h.waveDirection.toDouble())
                        })
                    }
                })
            })
        }
    }

    private fun fromJson(arr: JSONArray): List<Snapshot> = (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        val hoursArr = o.getJSONArray("hours")
        Snapshot(
            spotName = o.getString("spot"),
            loadedAt = LocalDateTime.parse(o.getString("loadedAt")),
            models = o.optString("models"),
            tides = o.optJSONObject("tides")?.let { tObj ->
                tObj.keys().asSequence().associate { key ->
                    val t = tObj.getJSONObject(key)
                    LocalDate.parse(key) to DailyTideInfo(
                        highTideTime = if (t.has("high")) t.getString("high") else null,
                        lowTideTime = if (t.has("low")) t.getString("low") else null,
                        coefficient = if (t.has("coef")) t.getInt("coef") else null
                    )
                }
            } ?: emptyMap(),
            hours = (0 until hoursArr.length()).map { j ->
                val h = hoursArr.getJSONObject(j)
                HourEntry(
                    time = LocalDateTime.parse(h.getString("t")),
                    windKmh = h.getInt("w"),
                    windDir = h.getString("wd"),
                    windSource = h.optString("ws"),
                    waveHeight = h.getDouble("h"),
                    wavePeriod = h.getDouble("p"),
                    waveDirection = h.getDouble("d").toFloat()
                )
            }
        )
    }

    /**
     * Conditions d'un jour passe (J-1, J-2) pour le journal de bord : la prevision la plus
     * recente faite pour ce jour-la (donc souvent chargee ce jour-la), convertie en heures
     * utilisables par la saisie de session. null si l'app n'a rien garde pour ce jour.
     */
    fun conditionsFor(context: Context, spotName: String, date: LocalDate): Pair<List<HourlyUiModel>, DailyTideInfo?>? {
        val snap = load(context).firstOrNull { s -> s.spotName == spotName && s.hours.any { it.time.toLocalDate() == date } }
            ?: return null
        val hours = snap.hours.filter { it.time.toLocalDate() == date }.map { h ->
            HourlyUiModel(
                timeFormatted = formatHour(h.time),
                rawTime = h.time,
                waveHeight = h.waveHeight,
                wavePeriod = h.wavePeriod,
                waveDirection = h.waveDirection,
                energyKj = (1.962 * h.waveHeight * h.waveHeight * h.wavePeriod * h.wavePeriod).toInt(),
                windSpeedKmh = h.windKmh,
                windDirectionStr = h.windDir,
                weatherCode = 0,
                temperature = 0,
                windSource = h.windSource
            )
        }
        return hours to snap.tides[date]
    }
}
