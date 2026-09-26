package com.surfcast.surfforecast

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.util.Locale

/**
 * Bandeau fin sans fond, pensé pour être glissé au-dessus de l'horloge (zone "at a
 * glance"). Deux lignes lisibles plutôt qu'une seule phrase trop longue pour tenir à
 * l'écran :
 *   Marée basse 11h15 (92)
 *   🌊 1.5m en hausse · 11s · 17°C · SE 7km/h (vire Sud à 11:00)
 * La rotation du vent donne l'heure exacte du changement de secteur (premier moment
 * dans les 12h à venir où il change vraiment de secteur), pas juste "de/vers". Les
 * mentions de tendance ("en hausse"/"en baisse", "forcit"/"tombe") ne s'affichent que
 * s'il y a un vrai changement à venir sur les prochaines heures ; rien n'est écrit si
 * ça reste stable. Tout en blanc (pas de code couleur vent/marée ici) : sur une photo
 * de fond d'écran quelconque, des teintes claires comme le jaune ou le vert deviennent
 * illisibles. Ne fait plus aucun appel réseau : il relit WidgetDataCache, alimenté par
 * SurfViewModel dès que l'appli a des données fraîches pour le spot favori.
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
            val speed = SurfUnitsHelper.formatWindValue(snapshot.windSpeedKmh, snapshot.windUnit)
            val unit = when (snapshot.windUnit) {
                "knots" -> "kts"
                "bft" -> "bft"
                else -> "km/h"
            }

            val waveTrendSuffix = when (snapshot.waveTrend) {
                Trend.RISING -> " en hausse"
                Trend.FALLING -> " en baisse"
                Trend.STABLE -> ""
            }

            val windClauses = mutableListOf<String>()
            when (snapshot.windSpeedTrend) {
                Trend.RISING -> windClauses.add("forcit")
                Trend.FALLING -> windClauses.add("tombe")
                Trend.STABLE -> {}
            }
            if (snapshot.windRotationToBucket != null && snapshot.windRotationTime != null) {
                windClauses.add("vire ${snapshot.windRotationToBucket} à ${snapshot.windRotationTime}")
            }
            val windSuffix = if (windClauses.isNotEmpty()) " (${windClauses.joinToString(", ")})" else ""

            val tideLine = if (snapshot.nextTideTime != null) {
                val tideLabel = if (snapshot.nextTideIsHigh == true) "Marée haute" else "Marée basse"
                val coefSuffix = if (snapshot.nextTideCoef != null) " (${snapshot.nextTideCoef})" else ""
                "$tideLabel ${snapshot.nextTideTime}$coefSuffix"
            } else {
                "Marée --"
            }

            val waveWindLine = "🌊 $formattedH$waveTrendSuffix · ${snapshot.wavePeriod}s · ${snapshot.temp}°C · ${snapshot.dirFr} $speed $unit$windSuffix"

            for (appWidgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.widget_surf_live)
                views.setTextViewText(R.id.widget_text_tide, tideLine)
                views.setTextViewText(R.id.widget_text_wave_wind, waveWindLine)

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

        fun pinWidget(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val provider = ComponentName(context, SurfOverlayWidgetProvider::class.java)
            if (appWidgetManager.isRequestPinAppWidgetSupported) {
                appWidgetManager.requestPinAppWidget(provider, null, null)
            }
        }
    }
}
