package com.surfcast.surfforecast

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Coquille Android : toute l'interface et la logique viennent du module partagé (SurfLogApp, la même
 * app que le site). Ici seulement ce qui est propre à Android : base Room du journal, widget d'écran
 * d'accueil, lecteur webcam intégré, mise à jour de l'APK.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        WidgetRefreshWorker.schedule(applicationContext)

        val prefs = SharedPreferencesStore(applicationContext)
        val sessionLogStore = createSessionLogStore(applicationContext)
        val appVersion = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "?"

        setContent {
            SurfLogApp(
                prefs = prefs,
                sessionLogStore = sessionLogStore,
                onPinWidget = { SurfOverlayWidgetProvider.pinWidget(this) },
                appVersion = appVersion,
                onForecastLoaded = { success, tides ->
                    WidgetDataCache.push(
                        context = applicationContext,
                        spotName = success.spotName,
                        forecast = success.hourlyForecast,
                        allTideExtrema = tides.rawByDate,
                        windUnit = prefs.getString("wind_unit", "kmh") ?: "kmh"
                    )
                },
                liveCamOverlay = { cam ->
                    LiveCamOverlayScreen(
                        currentSpotName = cam.spotName,
                        onClose = cam.onClose,
                        onSwitchSpot = cam.onSwitchSpot,
                        showLiveOverlay = cam.showLiveOverlay,
                        windUnit = cam.windUnit,
                        loadLiveConditions = cam.loadLiveConditions
                    )
                },
                updateBanner = { UpdateBanner() }
            )
        }
    }

    /** « Nouvelle version disponible » : compare avec apk-version.json publié avec le site. */
    @androidx.compose.runtime.Composable
    private fun UpdateBanner() {
        val installedCode = remember {
            runCatching { PackageInfoCompat.getLongVersionCode(packageManager.getPackageInfo(packageName, 0)) }.getOrDefault(0L)
        }
        var newVersionName by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(Unit) {
            newVersionName = withContext(Dispatchers.IO) {
                runCatching {
                    val conn = URL("https://surflog.fr/apk-version.json").openConnection() as HttpURLConnection
                    conn.connectTimeout = 8000
                    conn.readTimeout = 8000
                    val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    if (json.getLong("versionCode") > installedCode && installedCode > 0) json.getString("versionName") else null
                }.getOrNull()
            }
        }
        newVersionName?.let { v ->
            Text(
                text = "⬆ Nouvelle version disponible (v$v) · Mettre à jour",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://surflog.fr/surflog.apk"))) }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            )
        }
    }
}
