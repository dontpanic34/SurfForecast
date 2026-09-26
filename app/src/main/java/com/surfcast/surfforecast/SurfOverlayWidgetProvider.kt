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
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withRotation
import com.surfcast.surfforecast.ui.theme.AppColors
import java.util.Locale

/**
 * Bandeau fin sans fond, pensé pour être glissé au-dessus de l'horloge (zone "at a
 * glance"). Ne fait plus aucun appel réseau : il relit WidgetDataCache, alimenté par
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

            val degrees = SurfUnitsHelper.cardinalToDegrees(snapshot.dirFr)
            val rotationAngle = (degrees + 180f) % 360f
            val arrowColorInt = SurfUnitsHelper.getSurfWindColor(snapshot.dirFr, snapshot.windSpeedKmh).toArgb()

            val formattedH = String.format(Locale.US, "%.1fm", snapshot.waveHeight)
            val speed = SurfUnitsHelper.formatWindValue(snapshot.windSpeedKmh, snapshot.windUnit)
            val unit = when (snapshot.windUnit) {
                "knots" -> "kts"
                "bft" -> "bft"
                else -> "km/h"
            }

            val breezeBitmap = createBreezeBitmap(context)
            val arrowBitmap = createArrowBitmap(context, rotationAngle, arrowColorInt)
            val highTideBitmap = createHighTideBitmap(context)
            val lowTideBitmap = createLowTideBitmap(context)

            for (appWidgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.widget_surf_live)

                views.setTextViewText(R.id.widget_text_swell, "$formattedH - ${snapshot.wavePeriod}s - ${snapshot.temp}°C")
                views.setImageViewBitmap(R.id.widget_img_tide_high, highTideBitmap)
                views.setTextViewText(
                    R.id.widget_text_tide_high,
                    if (snapshot.tideHighTime != null) "${snapshot.tideHighTime} (${snapshot.tideHighCoef ?: "-"})" else "--"
                )
                views.setTextColor(R.id.widget_text_tide_high, AppColors.TideHighDark.toArgb())

                views.setImageViewBitmap(R.id.widget_img_tide_low, lowTideBitmap)
                views.setTextViewText(R.id.widget_text_tide_low, snapshot.tideLowTime ?: "--")
                views.setTextColor(R.id.widget_text_tide_low, AppColors.TideLowDark.toArgb())

                views.setImageViewBitmap(R.id.widget_img_wind_breeze, breezeBitmap)
                views.setTextViewText(R.id.widget_text_wind_dir, snapshot.dirFr)
                views.setTextColor(R.id.widget_text_wind_dir, arrowColorInt)

                views.setImageViewBitmap(R.id.widget_img_wind_arrow, arrowBitmap)
                views.setTextViewText(R.id.widget_text_wind_speed, "$speed $unit")
                views.setTextColor(R.id.widget_text_wind_speed, arrowColorInt)

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

        private fun createHighTideBitmap(context: Context): Bitmap {
            val density = context.resources.displayMetrics.density
            val sizePx = (12 * density).toInt().coerceAtLeast(1)
            val bitmap = createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val w = sizePx.toFloat()
            val h = sizePx.toFloat()
            val colorHigh = AppColors.TideHigh.toArgb()

            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colorHigh
                style = Paint.Style.STROKE
                strokeWidth = 1.1f * density
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }

            val wavePath = Path().apply {
                moveTo(0f, h * 0.70f)
                quadTo(w * 0.5f, h * 0.35f, w, h * 0.70f)
            }
            canvas.drawPath(wavePath, strokePaint)

            val arrowX = w * 0.5f
            canvas.drawLine(arrowX, h * 0.70f, arrowX, h * 0.10f, strokePaint)

            val headSize = 2f * density
            val headPath = Path().apply {
                moveTo(arrowX - headSize, h * 0.10f + headSize)
                lineTo(arrowX, h * 0.10f)
                lineTo(arrowX + headSize, h * 0.10f + headSize)
            }
            canvas.drawPath(headPath, strokePaint)

            return bitmap
        }

        private fun createLowTideBitmap(context: Context): Bitmap {
            val density = context.resources.displayMetrics.density
            val sizePx = (12 * density).toInt().coerceAtLeast(1)
            val bitmap = createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val w = sizePx.toFloat()
            val h = sizePx.toFloat()
            val colorLow = AppColors.TideLow.toArgb()

            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colorLow
                style = Paint.Style.STROKE
                strokeWidth = 1.1f * density
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }

            val wavePath = Path().apply {
                moveTo(0f, h * 0.30f)
                quadTo(w * 0.5f, h * 0.65f, w, h * 0.30f)
            }
            canvas.drawPath(wavePath, strokePaint)

            val arrowX = w * 0.5f
            canvas.drawLine(arrowX, h * 0.30f, arrowX, h * 0.90f, strokePaint)

            val headSize = 2f * density
            val headPath = Path().apply {
                moveTo(arrowX - headSize, h * 0.90f - headSize)
                lineTo(arrowX, h * 0.90f)
                lineTo(arrowX + headSize, h * 0.90f - headSize)
            }
            canvas.drawPath(headPath, strokePaint)

            return bitmap
        }

        private fun createBreezeBitmap(context: Context): Bitmap {
            val density = context.resources.displayMetrics.density
            val sizePx = (13 * density).toInt().coerceAtLeast(1)
            val bitmap = createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AppColors.TideHigh.toArgb()
                style = Paint.Style.STROKE
                strokeWidth = 1.2f * density
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }

            val w = sizePx.toFloat()
            val h = sizePx.toFloat()

            val p1 = Path().apply {
                moveTo(0f, h * 0.32f)
                lineTo(w * 0.65f, h * 0.32f)
                quadTo(w * 0.95f, h * 0.32f, w * 0.95f, h * 0.15f)
                quadTo(w * 0.95f, 0f, w * 0.75f, 0f)
            }
            canvas.drawPath(p1, paint)

            val p2 = Path().apply {
                moveTo(w * 0.15f, h * 0.65f)
                lineTo(w * 0.80f, h * 0.65f)
                quadTo(w * 1.05f, h * 0.65f, w * 1.05f, h * 0.85f)
                quadTo(w * 1.05f, h, w * 0.85f, h)
            }
            canvas.drawPath(p2, paint)

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

        fun pinWidget(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val provider = ComponentName(context, SurfOverlayWidgetProvider::class.java)
            if (appWidgetManager.isRequestPinAppWidgetSupported) {
                appWidgetManager.requestPinAppWidget(provider, null, null)
            }
        }
    }
}
