package com.surfcast.surfforecast

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.core.content.edit

data class WidgetSnapshot(
    val waveHeight: Float,
    val wavePeriod: Int,
    val temp: Int,
    val dirFr: String,
    val windSpeedKmh: Int,
    val windUnit: String,
    val tideHighTime: String?,
    val tideHighCoef: Int?,
    val tideLowTime: String?
)

/**
 * Cache partagé par les widgets d'écran d'accueil : SurfViewModel y écrit le même
 * instantané que celui affiché par SurfLiveStripOverlay dès que le spot favori (fav_0)
 * a des données fraîches. Les widgets (bandeau fin et bulle compacte) se contentent de
 * relire ce cache, sans jamais faire leur propre appel réseau depuis onUpdate().
 */
object WidgetDataCache {
    private const val PREFS = "surf_prefs"

    fun push(context: Context, spotName: String, hourly: HourlyUiModel, tide: DailyTideInfo?, windUnit: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val widgetSpot = prefs.getString("fav_0", "Montalivet") ?: "Montalivet"
        if (widgetSpot != spotName) return

        prefs.edit {
            putFloat("widget_wave_height", hourly.waveHeight.toFloat())
            putInt("widget_wave_period", hourly.wavePeriod.toInt())
            putInt("widget_temp", hourly.temperature)
            putString("widget_wind_dir_fr", SurfUnitsHelper.formatCardinalFr(hourly.windDirectionStr))
            putInt("widget_wind_speed_kmh", hourly.windSpeedKmh)
            putString("widget_wind_unit", windUnit)
            putString("widget_tide_high_time", tide?.highTideTime)
            if (tide?.coefficient != null) putInt("widget_tide_high_coef", tide.coefficient) else remove("widget_tide_high_coef")
            putString("widget_tide_low_time", tide?.lowTideTime)
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
            tideHighTime = prefs.getString("widget_tide_high_time", null),
            tideHighCoef = if (prefs.contains("widget_tide_high_coef")) prefs.getInt("widget_tide_high_coef", 0) else null,
            tideLowTime = prefs.getString("widget_tide_low_time", null)
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
