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
 * glance"). Trois lignes lisibles plutôt qu'une seule phrase trop longue pour tenir à
 * l'écran, chacune commençant par son icône :
 *   🌙 Marée basse 11h15 (92) · 17°C          ↻ 14:32
 *   🌊 1.5m · 11s (↗1.7m à 15:00, ↘9s à 15:00)
 *   💨 SE 7km/h (vire Sud 12km/h à 11:00)
 * L'heure de dernière mise à jour réussie du widget est nichée à droite de la ligne
 * marée, en petit et à faible opacité (semi-camouflée), pour repérer d'un coup d'œil
 * des données devenues périmées sans polluer la lecture du reste.
 * La rotation du vent donne l'heure exacte du changement de secteur (premier moment
 * dans les 12h à venir où il change vraiment de secteur), pas juste "de/vers". La ligne
 * houle garde toujours "hauteur · période" d'abord, en clair ; une parenthèse liste
 * ensuite ce qui va vraiment changer (hauteur et/ou période, chacune avec sa propre
 * heure si elles ne basculent pas en même temps) — ↗/↘ + la future valeur + l'heure.
 * Les mentions de tendance ("forcit"/"tombe" pour le vent) ne s'affichent que
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

            val waveClauses = mutableListOf<String>()
            if (snapshot.waveTrend != Trend.STABLE && snapshot.waveChangeTime != null && snapshot.waveChangeHeight != null) {
                val arrow = if (snapshot.waveTrend == Trend.RISING) "↗" else "↘"
                val futureH = String.format(Locale.US, "%.1fm", snapshot.waveChangeHeight)
                waveClauses.add("$arrow$futureH à ${snapshot.waveChangeTime}")
            }
            if (snapshot.periodTrend != Trend.STABLE && snapshot.periodChangeTime != null && snapshot.periodChangeValue != null) {
                val arrow = if (snapshot.periodTrend == Trend.RISING) "↗" else "↘"
                waveClauses.add("$arrow${snapshot.periodChangeValue}s à ${snapshot.periodChangeTime}")
            }
            val waveSuffix = if (waveClauses.isNotEmpty()) " (${waveClauses.joinToString(", ")})" else ""

            val windClauses = mutableListOf<String>()
            when (snapshot.windSpeedTrend) {
                Trend.RISING -> windClauses.add("forcit")
                Trend.FALLING -> windClauses.add("tombe")
                Trend.STABLE -> {}
            }
            if (snapshot.windRotationToBucket != null && snapshot.windRotationTime != null) {
                val rotationClause = buildString {
                    append("vire ${snapshot.windRotationToBucket}")
                    if (snapshot.windRotationSpeedKmh != null) {
                        append(" ${SurfUnitsHelper.formatWindValue(snapshot.windRotationSpeedKmh, snapshot.windUnit)}$unit")
                    }
                    append(" à ${snapshot.windRotationTime}")
                }
                windClauses.add(rotationClause)
            }
            val windSuffix = if (windClauses.isNotEmpty()) " (${windClauses.joinToString(", ")})" else ""

            val tideLine = if (snapshot.nextTideTime != null) {
                val tideLabel = if (snapshot.nextTideIsHigh == true) "Marée haute" else "Marée basse"
                val coefSuffix = if (snapshot.nextTideCoef != null) " (${snapshot.nextTideCoef})" else ""
                "🌙 $tideLabel ${snapshot.nextTideTime}$coefSuffix · ${snapshot.temp}°C"
            } else {
                "🌙 Marée -- · ${snapshot.temp}°C"
            }

            val waveLine = "🌊 $formattedH · ${snapshot.wavePeriod}s$waveSuffix"
            val windLine = "💨 ${snapshot.dirFr} $speed $unit$windSuffix"
            val updatedLine = "↻ ${snapshot.lastUpdateTime}"

            for (appWidgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.widget_surf_live)
                views.setTextViewText(R.id.widget_text_tide, tideLine)
                views.setTextViewText(R.id.widget_text_wave, waveLine)
                views.setTextViewText(R.id.widget_text_wind, windLine)
                views.setTextViewText(R.id.widget_text_updated, updatedLine)

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
