package com.surfcast.surfforecast

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

/**
 * Journal des prévisions (portage de ForecastHistoryStore d'app/) : à chaque chargement
 * réussi, on garde ce qui était annoncé (vent + modèle source, houle, marées), 2 jours,
 * pour comparer après coup avec la réalité et pour le journal de bord (J-1/J-2).
 * Stocké en JSON dans le KeyValueStore (NSUserDefaults sur iOS).
 */
class ForecastHistoryStore(private val prefs: KeyValueStore) {

    @Serializable
    data class HourEntry(
        val time: LocalDateTime,
        val windKmh: Int,
        val windDir: String,
        val windSource: String,
        val waveHeight: Double,
        val wavePeriod: Double,
        val waveDirection: Float
    )

    @Serializable
    data class TideEntry(val high: String? = null, val low: String? = null, val coef: Int? = null)

    @Serializable
    data class Snapshot(
        val spotName: String,
        val loadedAt: LocalDateTime,
        val models: String,
        val hours: List<HourEntry>,
        // Clé = date ISO (yyyy-MM-dd).
        val tides: Map<String, TideEntry> = emptyMap()
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val snapshotsSerializer = ListSerializer(Snapshot.serializer())

    fun record(
        spotName: String,
        config: ForecastEngineConfig,
        forecast: List<HourlyUiModel>,
        tides: Map<LocalDate, DailyTideInfo>,
        now: LocalDateTime = nowLocalDateTime()
    ) {
        val firstDay = now.date
        val lastDay = firstDay.plus(2, DateTimeUnit.DAY)
        val snapshot = Snapshot(
            spotName = spotName,
            loadedAt = now,
            models = "Vent ${config.shortTermWeather.displayName} / ${config.longTermWeather.displayName} · " +
                "Houle ${config.shortTermWave.displayName} / ${config.longTermWave.displayName}",
            hours = forecast
                .filter { it.rawTime.date in firstDay..lastDay }
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
            tides = tides.filterKeys { it in firstDay..lastDay }
                .entries.associate { (date, t) -> date.toString() to TideEntry(t.highTideTime, t.lowTideTime, t.coefficient) }
        )
        val kept = (loadAll() + snapshot)
            .filter { isRecent(it.loadedAt, now) }
            .takeLast(MAX_SNAPSHOTS)
        prefs.putString(KEY, json.encodeToString(snapshotsSerializer, kept))
    }

    /** Copies encore gardées, de la plus récente à la plus ancienne. */
    fun load(now: LocalDateTime = nowLocalDateTime()): List<Snapshot> =
        loadAll().filter { isRecent(it.loadedAt, now) }.sortedByDescending { it.loadedAt }

    /**
     * Conditions d'un jour passé (journal de bord J-1/J-2) : la prévision la plus récente
     * faite pour ce jour-là. null si rien n'a été gardé.
     */
    fun conditionsFor(
        spotName: String,
        date: LocalDate,
        now: LocalDateTime = nowLocalDateTime()
    ): Pair<List<HourlyUiModel>, DailyTideInfo?>? {
        val snap = load(now).firstOrNull { s -> s.spotName == spotName && s.hours.any { it.time.date == date } }
            ?: return null
        val hours = snap.hours.filter { it.time.date == date }.map { h ->
            HourlyUiModel(
                timeFormatted = formatHour(h.time),
                rawTime = h.time,
                waveHeight = h.waveHeight,
                wavePeriod = h.wavePeriod,
                waveDirection = h.waveDirection,
                energyKj = (1.962 * h.waveHeight * h.waveHeight * h.wavePeriod * h.wavePeriod).roundToInt(),
                windSpeedKmh = h.windKmh,
                windDirectionStr = h.windDir,
                weatherCode = 0,
                temperature = 0,
                windSource = h.windSource
            )
        }
        val tide = snap.tides[date.toString()]?.let { DailyTideInfo(it.high, it.low, it.coef) }
        return hours to tide
    }

    private fun loadAll(): List<Snapshot> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        // Données corrompues : on repart de zéro plutôt que de planter.
        return runCatching { json.decodeFromString(snapshotsSerializer, raw) }.getOrDefault(emptyList())
    }

    private fun isRecent(loadedAt: LocalDateTime, now: LocalDateTime): Boolean =
        loadedAt >= LocalDateTime(now.date.minus(RETENTION_DAYS, DateTimeUnit.DAY), now.time)

    private companion object {
        const val KEY = "forecast_history_json"
        const val RETENTION_DAYS = 2
        const val MAX_SNAPSHOTS = 60
    }
}
