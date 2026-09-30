package com.surfcast.surfforecast

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
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
        val hours: List<HourEntry>
    )

    private const val FILE_NAME = "forecast_history.json"
    private const val RETENTION_DAYS = 2L
    private const val MAX_SNAPSHOTS = 120
    private val lock = Any()

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    fun record(context: Context, spotName: String, config: ForecastEngineConfig, forecast: List<HourlyUiModel>) {
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
                }
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
}
