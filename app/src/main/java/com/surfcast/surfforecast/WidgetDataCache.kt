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

enum class Trend { RISING, FALLING, STABLE }

data class WidgetSnapshot(
    val waveHeight: Float,
    val waveTrend: Trend,
    val wavePeriod: Int,
    val temp: Int,
    val dirFr: String,
    val windSpeedKmh: Int,
    val windUnit: String,
    val windSpeedTrend: Trend,
    val windRotationToBucket: String?,
    val windRotationTime: String?,
    val nextTideIsHigh: Boolean?,
    val nextTideTime: String?,
    val nextTideCoef: Int?
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
    private val COMPASS_8 = listOf("Nord", "Nord-Est", "Est", "Sud-Est", "Sud", "Sud-Ouest", "Ouest", "Nord-Ouest")

    private fun bucket8(dirRaw: String): String {
        val deg = SurfUnitsHelper.cardinalToDegrees(SurfUnitsHelper.formatCardinalFr(dirRaw))
        val idx = ((deg + 22.5f) / 45f).toInt() % 8
        return COMPASS_8[idx]
    }

    fun push(
        context: Context,
        spotName: String,
        forecast: List<HourlyUiModel>,
        tides: Map<LocalDate, DailyTideInfo>,
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

        // Tendance de la houle et du vent : on regarde la moyenne des 3 prochaines heures
        // (pas juste l'heure suivante) pour ne signaler qu'un vrai changement à venir, et
        // rester muet si ça reste stable. Seuils volontairement significatifs.
        val aheadHours = (1..3).mapNotNull { forecast.getOrNull(fullIdx + it) }

        val waveTrend = if (aheadHours.isEmpty()) {
            Trend.STABLE
        } else {
            val diff = aheadHours.map { it.waveHeight }.average() - hourly.waveHeight
            when {
                diff >= 0.2 -> Trend.RISING
                diff <= -0.2 -> Trend.FALLING
                else -> Trend.STABLE
            }
        }

        val windSpeedTrend = if (aheadHours.isEmpty()) {
            Trend.STABLE
        } else {
            val diff = aheadHours.map { it.windSpeedKmh }.average() - hourly.windSpeedKmh
            when {
                diff >= 8 -> Trend.RISING
                diff <= -8 -> Trend.FALLING
                else -> Trend.STABLE
            }
        }

        // Rotation du vent : on cherche, dans les 12 prochaines heures, le premier moment où
        // le vent change vraiment de secteur (par tranche de 45°, pour ignorer le bruit entre
        // deux points de rose des vents voisins) — et on retient l'heure exacte de ce moment.
        val currentBucket = bucket8(hourly.windDirectionStr)
        val rotationHour = (1..12)
            .mapNotNull { forecast.getOrNull(fullIdx + it) }
            .firstOrNull { bucket8(it.windDirectionStr) != currentBucket }

        // Prochaine marée (haute ou basse, quel que soit l'ordre) à venir par rapport à
        // maintenant, en regardant aujourd'hui et demain au besoin.
        data class Candidate(val isHigh: Boolean, val time: String, val coef: Int?, val at: LocalDateTime)

        fun candidatesFor(date: LocalDate): List<Candidate> {
            val info = tides[date] ?: return emptyList()
            return listOfNotNull(
                info.highTideTime?.let { t ->
                    runCatching { LocalDateTime.of(date, LocalTime.parse(t, TIME_FMT)) }.getOrNull()
                        ?.let { Candidate(true, t, info.coefficient, it) }
                },
                info.lowTideTime?.let { t ->
                    runCatching { LocalDateTime.of(date, LocalTime.parse(t, TIME_FMT)) }.getOrNull()
                        ?.let { Candidate(false, t, info.coefficient, it) }
                }
            )
        }

        val nextTide = (candidatesFor(today) + candidatesFor(today.plusDays(1)))
            .filter { it.at.isAfter(now) }
            .minByOrNull { it.at }

        prefs.edit {
            putFloat("widget_wave_height", hourly.waveHeight.toFloat())
            putString("widget_wave_trend", waveTrend.name)
            putInt("widget_wave_period", hourly.wavePeriod.toInt())
            putInt("widget_temp", hourly.temperature)
            putString("widget_wind_dir_fr", SurfUnitsHelper.formatCardinalFr(hourly.windDirectionStr))
            putInt("widget_wind_speed_kmh", hourly.windSpeedKmh)
            putString("widget_wind_unit", windUnit)
            putString("widget_wind_speed_trend", windSpeedTrend.name)
            if (rotationHour != null) {
                putString("widget_wind_rotation_to", bucket8(rotationHour.windDirectionStr))
                putString("widget_wind_rotation_time", rotationHour.rawTime.format(TIME_FMT))
            } else {
                remove("widget_wind_rotation_to")
                remove("widget_wind_rotation_time")
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
            wavePeriod = prefs.getInt("widget_wave_period", 0),
            temp = prefs.getInt("widget_temp", 0),
            dirFr = prefs.getString("widget_wind_dir_fr", "--") ?: "--",
            windSpeedKmh = prefs.getInt("widget_wind_speed_kmh", 0),
            windUnit = prefs.getString("widget_wind_unit", "kmh") ?: "kmh",
            windSpeedTrend = trendOf("widget_wind_speed_trend"),
            windRotationToBucket = prefs.getString("widget_wind_rotation_to", null),
            windRotationTime = prefs.getString("widget_wind_rotation_time", null),
            nextTideIsHigh = if (prefs.contains("widget_next_tide_is_high")) prefs.getBoolean("widget_next_tide_is_high", true) else null,
            nextTideTime = prefs.getString("widget_next_tide_time", null),
            nextTideCoef = if (prefs.contains("widget_next_tide_coef")) prefs.getInt("widget_next_tide_coef", 0) else null
        )
    }

    fun refreshAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)

        val slimIds = manager.getAppWidgetIds(ComponentName(context, SurfOverlayWidgetProvider::class.java))
        if (slimIds.isNotEmpty()) {
            SurfOverlayWidgetProvider.renderWidgets(context, manager, slimIds)
        }

        val compactIds = manager.getAppWidgetIds(ComponentName(context, SurfCompactWidgetProvider::class.java))
        if (compactIds.isNotEmpty()) {
            SurfCompactWidgetProvider.renderWidgets(context, manager, compactIds)
        }
    }
}
