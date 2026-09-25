package com.surfcast.surfforecast

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Bundle
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withRotation
import com.surfcast.surfforecast.ui.theme.AppColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

@Suppress("SpellCheckingInspection")
class SurfOverlayWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val prefs = context.getSharedPreferences("surf_prefs", Context.MODE_PRIVATE)
        val spotName = prefs.getString("fav_0", "Montalivet") ?: "Montalivet"
        val spot = SurfDatabase.findSpotByName(spotName) ?: SurfDatabase.getAllSpots().first()
        val windUnit = prefs.getString("wind_unit", "kmh") ?: "kmh"

        val repository = SurfRepository()
        val config = ForecastEngineConfig()

        // Correctif critique : onUpdate() est un BroadcastReceiver.onReceive(). Une fois qu'il
        // revient, Android considere le receiver termine et peut tuer le processus a tout
        // moment -- y compris avant qu'une coroutine "fire and forget" lancee ici n'ait eu le
        // temps de terminer ses appels reseau. Resultat observe : le widget reste bloque sur
        // les valeurs par defaut ("--m") quasi a chaque fois, meme apres plusieurs secondes.
        // goAsync() indique explicitement a Android d'attendre la fin du travail (fenetre
        // etendue) avant de pouvoir tuer le processus ; pendingResult.finish() la libere.
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Borne le travail reseau total : goAsync() n'accorde qu'une fenetre limitee
                // avant qu'Android ne considere le broadcast en timeout (ANR). Sans cette
                // limite, un reseau lent (getHybridForecast + getTides sont enchaines, chacun
                // avec un connectTimeout/readTimeout de 10s) pourrait depasser cette fenetre
                // et empecher pendingResult.finish() d'etre appele a temps.
                withTimeoutOrNull(15_000) {
                    val forecast = repository.getHybridForecast(spot.latitude, spot.longitude, config).hourly
                    val today = LocalDate.now()

                    val tides = repository.getTides(
                        spot.latitude,
                        spot.longitude,
                        today.toString(),
                        today.plusDays(1).toString()
                    )
                    val todayTide = tides[today]

                    val currentHour = LocalTime.now().hour

                    val currentHourModel = forecast.firstOrNull {
                        it.rawTime.toLocalDate() == today && it.rawTime.hour == currentHour
                    } ?: forecast.firstOrNull()

                    if (currentHourModel != null) {
                        val formattedH = String.format(Locale.US, "%.1fm", currentHourModel.waveHeight)
                        val periodSec = currentHourModel.wavePeriod.toInt()
                        val tempVal = currentHourModel.temperature.toInt()

                        val dirFr = SurfUnitsHelper.formatCardinalFr(currentHourModel.windDirectionStr)
                        val degrees = SurfUnitsHelper.cardinalToDegrees(dirFr)
                        val rotationAngle = (degrees + 180f) % 360f

                        val arrowColorCompose = SurfUnitsHelper.getSurfWindColor(dirFr, currentHourModel.windSpeedKmh)
                        val arrowColorInt = arrowColorCompose.toArgb()

                        val speed = SurfUnitsHelper.formatWindValue(currentHourModel.windSpeedKmh, windUnit)
                        val unit = when (windUnit) {
                            "knots" -> "kts"
                            "bft" -> "bft"
                            else -> "km/h"
                        }

                        val data = CachedWidgetData(
                            formattedH = formattedH,
                            periodSec = periodSec,
                            tempVal = tempVal,
                            dirFr = dirFr,
                            rotationAngle = rotationAngle,
                            arrowColorInt = arrowColorInt,
                            speed = speed,
                            unit = unit,
                            todayTide = todayTide
                        )
                        cachedData = data
                        renderWidgets(context, appWidgetManager, appWidgetIds, data)
                    }
                }
            } catch (_: Exception) {
            } finally {
                pendingResult.finish()
            }
        }
    }

    /**
     * Donnees deja calculees (issues du dernier fetch reseau reussi), mises en cache pour
     * pouvoir redessiner le widget (ex: redimensionnement) sans refaire un appel reseau.
     */
    private data class CachedWidgetData(
        val formattedH: String,
        val periodSec: Int,
        val tempVal: Int,
        val dirFr: String,
        val rotationAngle: Float,
        val arrowColorInt: Int,
        val speed: String,
        val unit: String,
        val todayTide: DailyTideInfo?
    )

    private fun renderWidgets(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
        data: CachedWidgetData
    ) {
        val arrowBitmap = createArrowBitmap(context, data.rotationAngle, data.arrowColorInt)
        val tidePhase = computeTidePhase(LocalTime.now(), data.todayTide)

        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_surf_live)

            val (ringW, ringH) = ringSizePx(context, appWidgetManager, appWidgetId)
            val ringBitmap = createTideRingBitmap(
                widthPx = ringW,
                heightPx = ringH,
                density = context.resources.displayMetrics.density,
                phase = tidePhase,
                tideInfo = data.todayTide
            )
            views.setImageViewBitmap(R.id.widget_img_tide_ring, ringBitmap)

            views.setTextViewText(R.id.widget_text_wave, data.formattedH)
            views.setTextViewText(R.id.widget_text_wave_sub, "${data.periodSec}s · ${data.tempVal}°C")

            views.setImageViewBitmap(R.id.widget_img_wind_arrow, arrowBitmap)
            views.setTextViewText(R.id.widget_text_wind_dir, data.dirFr)
            views.setTextColor(R.id.widget_text_wind_dir, data.arrowColorInt)
            views.setTextViewText(R.id.widget_text_wind_speed, "${data.speed} ${data.unit}")
            views.setTextColor(R.id.widget_text_wind_speed, data.arrowColorInt)

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

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        // Le widget est redimensionnable (cercle) : on regenere l'anneau a la nouvelle taille
        // exacte pour qu'il reste net plutot que de laisser Android l'etirer. On reutilise les
        // dernieres donnees recuperees (pas de nouvel appel reseau juste pour un redimensionnement,
        // ce qui serait declenche en rafale pendant un drag de redimensionnement).
        val data = cachedData
        if (data != null) {
            renderWidgets(context, appWidgetManager, intArrayOf(appWidgetId), data)
        } else {
            onUpdate(context, appWidgetManager, intArrayOf(appWidgetId))
        }
    }

    /**
     * Taille en pixels a utiliser pour le bitmap de l'anneau : la taille reelle actuelle
     * du widget sur l'ecran d'accueil (fournie par le launcher), avec un repli raisonnable
     * si elle n'est pas encore disponible.
     */
    private fun ringSizePx(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int): Pair<Int, Int> {
        val density = context.resources.displayMetrics.density
        val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
        val minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        val minHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
        val widthPx = if (minWidthDp > 0) (minWidthDp * density).toInt() else (150 * density).toInt()
        val heightPx = if (minHeightDp > 0) (minHeightDp * density).toInt() else (150 * density).toInt()
        return widthPx.coerceAtLeast(1) to heightPx.coerceAtLeast(1)
    }

    /**
     * Parsing tolerant ("07h30", "7h30", "07:30", "7:30"), copie locale du meme
     * utilitaire que HourlyForecastRow.kt (petit helper prive, pas de dependance
     * croisee entre fichiers pour un si petit bout de code).
     */
    private fun parseTimeStringSafe(timeStr: String): LocalTime? {
        val clean = timeStr.trim().lowercase().replace("h", ":")
        val parts = clean.split(":")
        if (parts.size < 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        return try {
            LocalTime.of(h.coerceIn(0, 23), m.coerceIn(0, 59))
        } catch (_: Exception) {
            null
        }
    }

    private data class TidePhase(val fraction: Float, val rising: Boolean)

    /**
     * Position dans le cycle de maree courant : fraction 0..1 entre la derniere basse/pleine
     * mer et la prochaine, et le sens (montant/descendant). On ne connait que les heures du
     * jour, donc on extrapole sur plusieurs cycles (~6h12,5 par demi-cycle), comme deja fait
     * pour isTideRising dans HourlyForecastRow.kt, pour couvrir n'importe quelle heure.
     */
    private fun computeTidePhase(now: LocalTime, tideInfo: DailyTideInfo?): TidePhase? {
        val highSec = tideInfo?.highTideTime?.let { parseTimeStringSafe(it) }?.toSecondOfDay()
        val lowSec = tideInfo?.lowTideTime?.let { parseTimeStringSafe(it) }?.toSecondOfDay()
        if (highSec == null || lowSec == null) return null

        val halfCycle = (6.21 * 3600).toInt()
        val nowSec = now.toSecondOfDay()

        data class Event(val sec: Int, val isHigh: Boolean)

        val events = mutableListOf<Event>()
        for (k in -4..4) {
            events.add(Event(highSec + k * (2 * halfCycle), true))
            events.add(Event(lowSec + k * (2 * halfCycle), false))
        }
        events.sortBy { it.sec }

        val prev = events.lastOrNull { it.sec <= nowSec } ?: return null
        val next = events.firstOrNull { it.sec > nowSec } ?: return null
        val span = (next.sec - prev.sec).coerceAtLeast(1)
        val legFraction = ((nowSec - prev.sec).toFloat() / span).coerceIn(0f, 1f)

        val rising = !prev.isHigh
        return TidePhase(legFraction, rising)
    }

    /**
     * Anneau qui fait le tour du widget : trace de fond (cercle complet, discret), puis un
     * arc colore representant la progression dans la phase actuelle -- bas du widget = basse
     * mer, haut = pleine mer. Montant : l'arc balaie par la gauche du bas vers le haut.
     * Descendant : par la droite du haut vers le bas. Un point lumineux marque la position
     * actuelle, et les heures de pleine/basse mer sont ecrites directement sur l'anneau.
     */
    private fun createTideRingBitmap(
        widthPx: Int,
        heightPx: Int,
        density: Float,
        phase: TidePhase?,
        tideInfo: DailyTideInfo?
    ): Bitmap {
        val bitmap = createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val strokeW = 5.5f * density
        val inset = strokeW / 2f + 3f * density
        // Anneau toujours parfaitement circulaire, quel que soit le cadre reellement
        // accorde par le launcher (certains n'accordent pas un carre exact, cf. Niagara/
        // launcher par defaut qui ont tous deux etire l'ancien ovale sur tout le cadre) :
        // on se base sur la plus petite dimension et on centre, sans jamais etirer.
        val diameter = (minOf(widthPx, heightPx) - 2 * inset).coerceAtLeast(1f)
        val cx = widthPx / 2f
        val cy = heightPx / 2f
        val bounds = RectF(cx - diameter / 2f, cy - diameter / 2f, cx + diameter / 2f, cy + diameter / 2f)

        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokeW
            strokeCap = Paint.Cap.ROUND
            color = android.graphics.Color.argb(28, 255, 255, 255)
        }
        canvas.drawOval(bounds, trackPaint)

        if (phase != null) {
            val arcColor = if (phase.rising) AppColors.TideHigh.toArgb() else AppColors.TideLow.toArgb()
            val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = strokeW
                strokeCap = Paint.Cap.ROUND
                color = arcColor
            }

            // Angles Android : 0=droite, 90=bas, 180=gauche, 270=haut, sens horaire.
            val startAngle = if (phase.rising) 90f else 270f
            val sweepAngle = phase.fraction * 180f
            canvas.drawArc(bounds, startAngle, sweepAngle, false, arcPaint)

            val markerAngleRad = Math.toRadians((startAngle + sweepAngle).toDouble())
            // bounds est toujours centre sur (cx, cy) (cf. plus haut) : pas besoin de
            // recalculer via bounds.centerX()/centerY().
            val rx = bounds.width() / 2f
            val ry = bounds.height() / 2f
            val markerX = (cx + rx * cos(markerAngleRad)).toFloat()
            val markerY = (cy + ry * sin(markerAngleRad)).toFloat()

            val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = arcColor
                alpha = 90
                style = Paint.Style.FILL
            }
            canvas.drawCircle(markerX, markerY, strokeW * 1.4f, haloPaint)
            val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                style = Paint.Style.FILL
            }
            canvas.drawCircle(markerX, markerY, strokeW * 0.55f, markerPaint)
        }

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = 9.5f * density
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            alpha = 215
        }

        val highLabel = tideInfo?.highTideTime?.let { t ->
            val coef = tideInfo.coefficient
            if (coef != null) "$t ($coef)" else t
        }
        if (highLabel != null) {
            canvas.drawText(highLabel, cx, bounds.top + labelPaint.textSize + 2f * density, labelPaint)
        }

        val lowLabel = tideInfo?.lowTideTime
        if (lowLabel != null) {
            canvas.drawText(lowLabel, cx, bounds.bottom - 2f * density, labelPaint)
        }

        return bitmap
    }

    private fun createArrowBitmap(context: Context, rotationAngle: Float, colorInt: Int): Bitmap {
        val density = context.resources.displayMetrics.density
        val sizePx = (11 * density).toInt().coerceAtLeast(1)
        val bitmap = createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val w = sizePx.toFloat()
        val h = sizePx.toFloat()

        canvas.withRotation(rotationAngle, w / 2f, h / 2f) {
            val path = Path().apply {
                moveTo(w * 0.5f, 0.5f * density)
                lineTo(w * 0.95f, h * 0.48f)
                lineTo(w * 0.68f, h * 0.48f)
                lineTo(w * 0.68f, h * 0.98f)
                lineTo(w * 0.32f, h * 0.98f)
                lineTo(w * 0.32f, h * 0.48f)
                lineTo(w * 0.05f, h * 0.48f)
                close()
            }

            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colorInt
                style = Paint.Style.FILL
            }
            drawPath(path, fillPaint)

            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.argb(128, 255, 255, 255)
                style = Paint.Style.STROKE
                strokeWidth = 0.8f * density
            }
            drawPath(path, strokePaint)
        }

        return bitmap
    }

    companion object {
        @Volatile
        private var cachedData: CachedWidgetData? = null

        fun pinWidget(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val provider = ComponentName(context, SurfOverlayWidgetProvider::class.java)
            if (appWidgetManager.isRequestPinAppWidgetSupported) {
                appWidgetManager.requestPinAppWidget(provider, null, null)
            }
        }
    }
}
