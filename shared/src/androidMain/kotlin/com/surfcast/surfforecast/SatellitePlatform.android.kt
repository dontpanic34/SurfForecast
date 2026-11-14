package com.surfcast.surfforecast

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Les aides du téléphone pour la vue satellite : « Ma position » (GPS, autorisation demandée à ce moment-là), la position enregistrée
 * dans une photo, la lecture d'un lien court Google Maps et le bouton retour. L'écran lui-même est dans le code commun (site et appli).
 */
@Composable
fun rememberAndroidSatellitePlatform(): SatellitePlatform {
    val context = LocalContext.current
    val pendingLocate = remember { mutableStateOf<SatelliteLocationResult?>(null) }
    val pendingPhoto = remember { mutableStateOf<SatelliteLocationResult?>(null) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        val callback = pendingLocate.value ?: return@rememberLauncherForActivityResult
        pendingLocate.value = null
        if (granted.values.any { it }) {
            currentLocation(context) { loc ->
                if (loc == null) callback(null, "Position introuvable : active la localisation du téléphone.")
                else callback(loc.latitude to loc.longitude, null)
            }
        } else callback(null, "Sans l'autorisation de localisation, pose le point à la main ou colle des coordonnées.")
    }
    val photo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val callback = pendingPhoto.value ?: return@rememberLauncherForActivityResult
        pendingPhoto.value = null
        if (uri == null) return@rememberLauncherForActivityResult
        val latLon = photoLatLon(context, uri)
        if (latLon == null) callback(null, "Cette photo n'a pas de position enregistrée (Android la retire parfois). Essaie « Ma position » ou des coordonnées.")
        else callback(latLon, null)
    }

    return remember {
        SatellitePlatform(
            locate = { callback ->
                pendingLocate.value = callback
                permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            },
            photoPosition = { callback ->
                pendingPhoto.value = callback
                photo.launch("image/*")
            },
            resolveLink = { text -> withContext(Dispatchers.IO) { resolveMapsLink(text) } },
            backHandler = { enabled, onBack -> BackHandler(enabled = enabled, onBack = onBack) }
        )
    }
}

/** Position du téléphone, une seule fois (GPS, sinon réseau). */
@SuppressLint("MissingPermission")
private fun currentLocation(context: Context, onResult: (Location?) -> Unit) {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return onResult(null)
    val provider = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).firstOrNull { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        ?: return onResult(null)
    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            manager.getCurrentLocation(provider, null, context.mainExecutor) { loc -> onResult(loc ?: manager.getLastKnownLocation(provider)) }
        } else {
            val last = manager.getLastKnownLocation(provider)
            if (last != null) onResult(last)
            else {
                @Suppress("DEPRECATION")
                manager.requestSingleUpdate(provider, object : LocationListener {
                    override fun onLocationChanged(location: Location) = onResult(location)
                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                }, Looper.getMainLooper())
            }
        }
    }.onFailure { onResult(null) }
}

/** Latitude et longitude enregistrées dans une photo, si Android les laisse lire. */
private fun photoLatLon(context: Context, uri: Uri): Pair<Double, Double>? = runCatching {
    val original = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) runCatching { MediaStore.setRequireOriginal(uri) }.getOrDefault(uri) else uri
    val stream = runCatching { context.contentResolver.openInputStream(original) }.getOrNull() ?: context.contentResolver.openInputStream(uri)
    stream?.use { input ->
        val out = FloatArray(2)
        if (ExifInterface(input).getLatLong(out) && (out[0] != 0f || out[1] != 0f)) out[0].toDouble() to out[1].toDouble() else null
    }
}.getOrNull()

/**
 * Suit un lien de partage Google Maps (maps.app.goo.gl/…) jusqu'à son adresse finale et y lit les coordonnées.
 * Sert pour une photo : dans Google Photos, « Ouvrir dans Maps », Partager, copier le lien, puis le coller dans « Coordonnées ».
 */
private fun resolveMapsLink(text: String): Pair<Double, Double>? = runCatching {
    var url = Regex("https?://\\S+").find(text)?.value ?: return null
    repeat(6) {
        SatelliteModel.parseCoordinates(java.net.URLDecoder.decode(url, "UTF-8"))?.let { return it }
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = false
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13; Mobile)")
        val next = conn.getHeaderField("Location")
        val body = if (next == null && conn.responseCode in 200..299) conn.inputStream.bufferedReader().use { it.readText().take(400_000) } else null
        conn.disconnect()
        if (next != null) url = URL(URL(url), next).toString()
        else return body?.let { page ->
            val t = java.net.URLDecoder.decode(page.replace("\\u003d", "="), "UTF-8")
            val found = Regex("!3d-?\\d+\\.\\d+!4d-?\\d+\\.\\d+").find(t)?.value ?: Regex("@-?\\d+\\.\\d+,-?\\d+\\.\\d+").find(t)?.value ?: ""
            SatelliteModel.parseCoordinates(found)
        }
    }
    null
}.getOrNull()
