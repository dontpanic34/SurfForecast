package com.surfcast.surfforecast

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import java.util.concurrent.TimeUnit

/**
 * Rafraîchit le widget d'écran d'accueil en arrière-plan, même appli fermée. Sans ça,
 * WidgetDataCache n'est alimenté que quand SurfController charge des données (donc à
 * l'ouverture de l'appli) — le widget peut alors afficher des données vieilles de
 * plusieurs heures. Refait le même appel réseau que SurfController.loadSpotInternal()
 * pour le spot favori (fav_0) et pousse le résultat vers WidgetDataCache.
 */
class WidgetRefreshWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val prefs = applicationContext.getSharedPreferences("surf_prefs", Context.MODE_PRIVATE)
            val spotName = prefs.getString("fav_0", "Montalivet") ?: "Montalivet"
            // Si le spot favori ne correspond plus à rien de connu, on ne sait pas quoi
            // rafraîchir : inutile de gaspiller un appel réseau pour un autre spot, dont
            // le nom ne matchera de toute façon jamais fav_0 dans WidgetDataCache.push().
            val spot = SurfDatabase.findSpotByName(spotName) ?: return Result.success()
            val windUnit = prefs.getString("wind_unit", "kmh") ?: "kmh"
            val store = SharedPreferencesStore(prefs)
            val engineConfig = loadEngineConfigFromPrefs(store)

            val repository = SurfRepository()
            val forecastResult = repository.getHybridForecast(spot.latitude, spot.longitude, engineConfig)
            val forecast = forecastResult.hourly
            val grouped = forecast.groupBy { it.rawTime.date }

            val fromDate = grouped.keys.minOrNull()?.toString() ?: nowLocalDateTime().date.toString()
            val toDate = grouped.keys.maxOrNull()?.toString() ?: nowLocalDateTime().date.plus(7, DateTimeUnit.DAY).toString()
            val tidesBundle = repository.getTides(spot.latitude, spot.longitude, fromDate, toDate)

            // Le rafraichissement en arriere-plan alimente aussi le journal des previsions
            // (et donc les conditions J-1/J-2 du journal de bord), meme sans ouvrir l'app.
            runCatching {
                ForecastHistoryStore(store).record(spot.name, engineConfig, forecast, tidesBundle.dailyByDate)
            }

            WidgetDataCache.push(
                context = applicationContext,
                spotName = spot.name,
                forecast = forecast,
                allTideExtrema = tidesBundle.rawByDate,
                windUnit = windUnit
            )

            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "widget_refresh"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
