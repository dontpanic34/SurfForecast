package com.surfcast.surfforecast

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.core.content.edit
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

enum class Trend { RISING, FALLING, STABLE }

data class WidgetSnapshot(
    val waveHeight: Float,
    val waveTrend: Trend,
    val waveChangeTime: String?,
    val waveChangeHeight: Float?,
    val wavePeriod: Int,
    val periodTrend: Trend,
    val periodChangeTime: String?,
    val periodChangeValue: Int?,
    val temp: Int,
    val dirFr: String,
    val windSpeedKmh: Int,
    val windUnit: String,
    val windSpeedTrend: Trend,
    val windRotationToBucket: String?,
    val windRotationTime: String?,
    val windRotationSpeedKmh: Int?,
    val nextTideIsHigh: Boolean?,
    val nextTideTime: String?,
    val nextTideCoef: Int?,
    val lastUpdateTime: String
)

/**
 * Cache partagé par les widgets d'écran d'accueil : SurfViewModel y écrit le même
 * instantané que celui affiché par SurfLiveStripOverlay dès que le spot favori (fav_0)
 * a des données fraîches. Les widgets se contentent de relire ce cache, sans jamais
 * faire leur propre appel réseau depuis onUpdate().
 */
object WidgetDataCache {
    private const val PREFS = "surf_prefs"
    private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")
    // Secteurs de 90° (pas 45°) : un simple SSE -> SE reste un vent globalement "de sud",
    // pas un vrai changement de direction. Seul un vrai virement (typiquement Est -> Ouest)
    // doit déclencher la mention "vire ... à ...".
    private val COMPASS_4 = listOf("Nord", "Est", "Sud", "Ouest")

    private fun bucket4(dirRaw: String): String {
        val deg = SurfUnitsHelper.cardinalToDegrees(SurfUnitsHelper.formatCardinalFr(dirRaw))
        val idx = ((deg + 45f) / 90f).toInt() % 4
        return COMPASS_4[idx]
    }

    /**
     * Tendance d'une métrique (houle, période, vitesse du vent...) sur la moyenne des 3
     * prochaines heures (pas juste l'heure suivante, pour ne signaler qu'un vrai
     * changement), puis recherche dans les 12 prochaines heures du premier moment où elle
     * franchit vraiment ce seuil dans le sens de la tendance — pour donner une heure et
     * une valeur précises plutôt qu'un simple mot.
     */
    private fun trendAndChangeHour(
        hourly: HourlyUiModel,
        aheadHours: List<HourlyUiModel>,
        forecast: List<HourlyUiModel>,
        fullIdx: Int,
        threshold: Double,
        selector: (HourlyUiModel) -> Double
    ): Pair<Trend, HourlyUiModel?> {
        val current = selector(hourly)
        val trend = if (aheadHours.isEmpty()) {
            Trend.STABLE
        } else {
            val diff = aheadHours.map(selector).average() - current
            when {
                diff >= threshold -> Trend.RISING
                diff <= -threshold -> Trend.FALLING
                else -> Trend.STABLE
            }
        }

        val changeHour = if (trend == Trend.STABLE) {
            null
        } else {
            (1..12).mapNotNull { forecast.getOrNull(fullIdx + it) }
                .firstOrNull { h ->
                    val diff = selector(h) - current
                    if (trend == Trend.RISING) diff >= threshold else diff <= -threshold
                }
        }

        return trend to changeHour
    }

    fun push(
        context: Context,
        spotName: String,
        forecast: List<HourlyUiModel>,
        allTideExtrema: Map<LocalDate, List<MareeExtremum>>,
        windUnit: String
    ) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val widgetSpot = prefs.getString("fav_0", "Montalivet") ?: "Montalivet"
        if (widgetSpot != spotName) return

        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        val currentHour = now.hour

        val todayHours = forecast.filter { it.rawTime.toLocalDate() == today }
        val closestIdx = todayHours.indices.minByOrNull { abs(todayHours[it].rawTime.hour - currentHour) }
        val hourly = closestIdx?.let { todayHours[it] } ?: forecast.firstOrNull() ?: return

        val fullIdx = forecast.indexOfFirst { it.rawTime == hourly.rawTime }
        val aheadHours = (1..3).mapNotNull { forecast.getOrNull(fullIdx + it) }

        val (waveTrend, waveChangeHour) = trendAndChangeHour(hourly, aheadHours, forecast, fullIdx, 0.2) { it.waveHeight }
        val (periodTrend, periodChangeHour) = trendAndChangeHour(hourly, aheadHours, forecast, fullIdx, 1.5) { it.wavePeriod }
        val (windSpeedTrend, _) = trendAndChangeHour(hourly, aheadHours, forecast, fullIdx, 8.0) { it.windSpeedKmh.toDouble() }

        // Rotation du vent : on cherche, dans les 12 prochaines heures, le premier moment où
        // le vent change vraiment de secteur (par quart de rose des vents, 90°, pour ignorer
        // les petits décalages du type SSE -> SE) — et on retient l'heure exacte de ce moment.
        val currentBucket = bucket4(hourly.windDirectionStr)
        val rotationHour = (1..12)
            .mapNotNull { forecast.getOrNull(fullIdx + it) }
            .firstOrNull { bucket4(it.windDirectionStr) != currentBucket }

        // Prochaine marée (haute ou basse, quel que soit l'ordre) à venir par rapport à
        // maintenant — sur TOUTES les marées du jour (typiquement 4 : 2 PM + 2 BM), pas
        // seulement celles de jour comme dans le reste de l'appli : le widget doit donner
        // l'heure réelle de la prochaine marée, même si elle a lieu de nuit.
        data class Candidate(val isHigh: Boolean, val time: String, val coef: Int?, val at: LocalDateTime)

        fun candidatesFor(date: LocalDate): List<Candidate> {
            val extrema = allTideExtrema[date] ?: return emptyList()
            // L'API ne renseigne le coefficient que sur les entrées PM (pleine mer) ; une
            // entrée BM (basse mer) arrive avec coef=null. On retombe sur le coefficient
            // du jour (celui de la PM, ou à défaut n'importe quelle entrée qui en a un)
            // pour ne pas perdre l'info quand la prochaine marée est une basse mer.
            val dayCoef = extrema.firstOrNull { it.type == "PM" }?.coef ?: extrema.firstNotNullOfOrNull { it.coef }
            return extrema.mapNotNull { e ->
                runCatching { LocalDateTime.of(date, LocalTime.parse(e.time, TIME_FMT)) }.getOrNull()
                    ?.let { Candidate(e.type == "PM", e.time, e.coef ?: dayCoef, it) }
            }
        }

        val nextTide = (candidatesFor(today) + candidatesFor(today.plusDays(1)))
            .filter { it.at.isAfter(now) }
            .minByOrNull { it.at }

        prefs.edit {
            putFloat("widget_wave_height", hourly.waveHeight.toFloat())
            putString("widget_wave_trend", waveTrend.name)
            if (waveChangeHour != null) {
                putString("widget_wave_change_time", waveChangeHour.rawTime.format(TIME_FMT))
                putFloat("widget_wave_change_height", waveChangeHour.waveHeight.toFloat())
            } else {
                remove("widget_wave_change_time")
                remove("widget_wave_change_height")
            }
            putInt("widget_wave_period", hourly.wavePeriod.roundToInt())
            putString("widget_period_trend", periodTrend.name)
            if (periodChangeHour != null) {
                putString("widget_period_change_time", periodChangeHour.rawTime.format(TIME_FMT))
                putInt("widget_period_change_value", periodChangeHour.wavePeriod.roundToInt())
            } else {
                remove("widget_period_change_time")
                remove("widget_period_change_value")
            }
            putInt("widget_temp", hourly.temperature)
            putString("widget_wind_dir_fr", SurfUnitsHelper.formatCardinalFr(hourly.windDirectionStr))
            putInt("widget_wind_speed_kmh", hourly.windSpeedKmh)
            putString("widget_wind_unit", windUnit)
            putString("widget_wind_speed_trend", windSpeedTrend.name)
            if (rotationHour != null) {
                putString("widget_wind_rotation_to", bucket4(rotationHour.windDirectionStr))
                putString("widget_wind_rotation_time", rotationHour.rawTime.format(TIME_FMT))
                putInt("widget_wind_rotation_speed_kmh", rotationHour.windSpeedKmh)
            } else {
                remove("widget_wind_rotation_to")
                remove("widget_wind_rotation_time")
                remove("widget_wind_rotation_speed_kmh")
            }
            if (nextTide != null) {
                putBoolean("widget_next_tide_is_high", nextTide.isHigh)
                putString("widget_next_tide_time", nextTide.time)
                if (nextTide.coef != null) putInt("widget_next_tide_coef", nextTide.coef) else remove("widget_next_tide_coef")
            } else {
                remove("widget_next_tide_is_high")
                remove("widget_next_tide_time")
                remove("widget_next_tide_coef")
            }
            putString("widget_last_update", now.format(TIME_FMT))
            putBoolean("widget_has_data", true)
        }

        refreshAll(context)
    }

    fun read(context: Context): WidgetSnapshot? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean("widget_has_data", false)) return null

        fun trendOf(key: String) = runCatching { Trend.valueOf(prefs.getString(key, null) ?: "STABLE") }.getOrDefault(Trend.STABLE)

        return WidgetSnapshot(
            waveHeight = prefs.getFloat("widget_wave_height", 0f),
            waveTrend = trendOf("widget_wave_trend"),
            waveChangeTime = prefs.getString("widget_wave_change_time", null),
            waveChangeHeight = if (prefs.contains("widget_wave_change_height")) prefs.getFloat("widget_wave_change_height", 0f) else null,
            wavePeriod = prefs.getInt("widget_wave_period", 0),
            periodTrend = trendOf("widget_period_trend"),
            periodChangeTime = prefs.getString("widget_period_change_time", null),
            periodChangeValue = if (prefs.contains("widget_period_change_value")) prefs.getInt("widget_period_change_value", 0) else null,
            temp = prefs.getInt("widget_temp", 0),
            dirFr = prefs.getString("widget_wind_dir_fr", "--") ?: "--",
            windSpeedKmh = prefs.getInt("widget_wind_speed_kmh", 0),
            windUnit = prefs.getString("widget_wind_unit", "kmh") ?: "kmh",
            windSpeedTrend = trendOf("widget_wind_speed_trend"),
            windRotationToBucket = prefs.getString("widget_wind_rotation_to", null),
            windRotationTime = prefs.getString("widget_wind_rotation_time", null),
            windRotationSpeedKmh = if (prefs.contains("widget_wind_rotation_speed_kmh")) prefs.getInt("widget_wind_rotation_speed_kmh", 0) else null,
            nextTideIsHigh = if (prefs.contains("widget_next_tide_is_high")) prefs.getBoolean("widget_next_tide_is_high", true) else null,
            nextTideTime = prefs.getString("widget_next_tide_time", null),
            nextTideCoef = if (prefs.contains("widget_next_tide_coef")) prefs.getInt("widget_next_tide_coef", 0) else null,
            lastUpdateTime = prefs.getString("widget_last_update", "--:--") ?: "--:--"
        )
    }

    fun refreshAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)

        val slimIds = manager.getAppWidgetIds(ComponentName(context, SurfOverlayWidgetProvider::class.java))
        if (slimIds.isNotEmpty()) {
            SurfOverlayWidgetProvider.renderWidgets(context, manager, slimIds)
        }
    }
}
