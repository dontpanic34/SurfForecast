package com.surfcast.surfforecast

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import com.surfcast.surfforecast.ui.theme.AppColors
import java.util.Locale

/**
 * Bandeau fin sans fond, pensé pour être glissé au-dessus de l'horloge (zone "at a
 * glance") : une seule phrase lisible ("1.5m · 11s · ESE 7km/h ↗ · Marée basse 11h15
 * (92)") plutôt que des blocs séparés avec de petites icônes. Ne fait plus aucun appel
 * réseau : il relit WidgetDataCache, alimenté par SurfViewModel dès que l'appli a des
 * données fraîches pour le spot favori.
 */
@Suppress("SpellCheckingInspection")
class SurfOverlayWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        renderWidgets(context, appWidgetManager, appWidgetIds)
    }

    companion object {

        fun renderWidgets(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
            val snapshot = WidgetDataCache.read(context) ?: return

            val formattedH = String.format(Locale.US, "%.1fm", snapshot.waveHeight)
            val windColorInt = SurfUnitsHelper.getSurfWindColor(snapshot.dirFr, snapshot.windSpeedKmh).toArgb()
            val speed = SurfUnitsHelper.formatWindValue(snapshot.windSpeedKmh, snapshot.windUnit)
            val unit = when (snapshot.windUnit) {
                "knots" -> "kts"
                "bft" -> "bft"
                else -> "km/h"
            }
            val trendArrow = when (snapshot.windTrend) {
                WindTrend.RISING -> " ↗"
                WindTrend.FALLING -> " ↘"
                WindTrend.STABLE -> " →"
            }

            val sentence = SpannableStringBuilder().apply {
                append("$formattedH · ${snapshot.wavePeriod}s · ${snapshot.temp}°C")
                append("  ·  ")
                appendColored("${snapshot.dirFr} $speed $unit$trendArrow", windColorInt)

                if (snapshot.nextTideTime != null) {
                    append("  ·  ")
                    val isHigh = snapshot.nextTideIsHigh == true
                    val tideLabel = if (isHigh) "Marée haute" else "Marée basse"
                    val tideColorInt = if (isHigh) AppColors.TideHigh.toArgb() else AppColors.TideLow.toArgb()
                    // Le coefficient de marée fourni par l'API n'est renseigné que pour la
                    // pleine mer (cf SurfRepository.getTides) : on ne l'affiche donc que là,
                    // comme partout ailleurs dans l'appli, pour ne pas laisser croire qu'il
                    // décrit spécifiquement la basse mer.
                    val coefSuffix = if (isHigh && snapshot.nextTideCoef != null) " (${snapshot.nextTideCoef})" else ""
                    appendColored("$tideLabel ${snapshot.nextTideTime}$coefSuffix", tideColorInt)
                }
            }

            for (appWidgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.widget_surf_live)
                views.setTextViewText(R.id.widget_text_sentence, sentence)

                val clickIntent = Intent(context, MainActivity::class.java)
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    clickIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }

        private fun SpannableStringBuilder.appendColored(text: String, colorInt: Int) {
            val start = length
            append(text)
            setSpan(ForegroundColorSpan(colorInt), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        fun pinWidget(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val provider = ComponentName(context, SurfOverlayWidgetProvider::class.java)
            if (appWidgetManager.isRequestPinAppWidgetSupported) {
                appWidgetManager.requestPinAppWidget(provider, null, null)
            }
        }
    }
}
