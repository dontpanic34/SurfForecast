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
 * glance") : une seule phrase lisible ("1.5m en hausse · 11s · 17°C · ESE 7km/h (forcit,
 * vire Sud à 14:00) · Marée basse 11h15 (92)") plutôt que des blocs séparés avec de
 * petites icônes. La rotation du vent donne l'heure exacte du changement de secteur
 * (premier moment dans les 12h à venir où il change vraiment de secteur), pas juste
 * "de/vers". Les
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

            val sentenceBuilder = StringBuilder()
            sentenceBuilder.append("$formattedH$waveTrendSuffix · ${snapshot.wavePeriod}s · ${snapshot.temp}°C")
            sentenceBuilder.append("  ·  ")
            sentenceBuilder.append("${snapshot.dirFr} $speed $unit$windSuffix")

            if (snapshot.nextTideTime != null) {
                val tideLabel = if (snapshot.nextTideIsHigh == true) "Marée haute" else "Marée basse"
                val coefSuffix = if (snapshot.nextTideCoef != null) " (${snapshot.nextTideCoef})" else ""
                sentenceBuilder.append("  ·  ")
                sentenceBuilder.append("$tideLabel ${snapshot.nextTideTime}$coefSuffix")
            }

            val sentence = sentenceBuilder.toString()

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

        fun pinWidget(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val provider = ComponentName(context, SurfOverlayWidgetProvider::class.java)
            if (appWidgetManager.isRequestPinAppWidgetSupported) {
                appWidgetManager.requestPinAppWidget(provider, null, null)
            }
        }
    }
}
