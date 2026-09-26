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

enum class WindTrend { RISING, FALLING, STABLE }

data class WidgetSnapshot(
    val waveHeight: Float,
    val wavePeriod: Int,
    val temp: Int,
    val dirFr: String,
    val windSpeedKmh: Int,
    val windUnit: String,
    val windTrend: WindTrend,
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

        // Tendance du vent : on compare la vitesse une heure avant et une heure après
        // l'heure actuelle pour lisser le bruit d'une comparaison à l'heure suivante seule.
        val fullIdx = forecast.indexOfFirst { it.rawTime == hourly.rawTime }
        val prevSpeed = forecast.getOrNull(fullIdx - 1)?.windSpeedKmh ?: hourly.windSpeedKmh
        val nextSpeed = forecast.getOrNull(fullIdx + 1)?.windSpeedKmh ?: hourly.windSpeedKmh
        val windTrend = when {
            nextSpeed - prevSpeed >= 3 -> WindTrend.RISING
            nextSpeed - prevSpeed <= -3 -> WindTrend.FALLING
            else -> WindTrend.STABLE
        }

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
            putInt("widget_wave_period", hourly.wavePeriod.toInt())
            putInt("widget_temp", hourly.temperature)
            putString("widget_wind_dir_fr", SurfUnitsHelper.formatCardinalFr(hourly.windDirectionStr))
            putInt("widget_wind_speed_kmh", hourly.windSpeedKmh)
            putString("widget_wind_unit", windUnit)
            putString("widget_wind_trend", windTrend.name)
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

        return WidgetSnapshot(
            waveHeight = prefs.getFloat("widget_wave_height", 0f),
            wavePeriod = prefs.getInt("widget_wave_period", 0),
            temp = prefs.getInt("widget_temp", 0),
            dirFr = prefs.getString("widget_wind_dir_fr", "--") ?: "--",
            windSpeedKmh = prefs.getInt("widget_wind_speed_kmh", 0),
            windUnit = prefs.getString("widget_wind_unit", "kmh") ?: "kmh",
            windTrend = runCatching { WindTrend.valueOf(prefs.getString("widget_wind_trend", null) ?: "STABLE") }.getOrDefault(WindTrend.STABLE),
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
