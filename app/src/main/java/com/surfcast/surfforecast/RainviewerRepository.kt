package com.surfcast.surfforecast

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class RadarFrame(val time: Long, val path: String)

data class RainviewerFrames(
    val host: String,
    val past: List<RadarFrame>,
    val nowcast: List<RadarFrame>,
    val satellite: List<RadarFrame>
) {
    val all: List<RadarFrame> get() = past + nowcast
    val nowIndex: Int get() = (past.size - 1).coerceAtLeast(0)
}

class RainviewerRepository {

    suspend fun fetchFrames(): RainviewerFrames = withContext(Dispatchers.IO) {
        val url = URL("https://api.rainviewer.com/public/weather-maps.json")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 10000
        conn.readTimeout = 10000

        val responseCode = conn.responseCode
        val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
            ?: throw Exception("Erreur HTTP $responseCode")

        val reader = BufferedReader(InputStreamReader(stream))
        val response = StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) response.append(line)
        reader.close()

        if (responseCode !in 200..299) throw Exception("API RainViewer ($responseCode)")

        val json = JSONObject(response.toString())
        val host = json.getString("host")
        val radar = json.getJSONObject("radar")
        val satellite = json.optJSONObject("satellite")

        fun parseFrames(arr: org.json.JSONArray?) = arr?.let {
            (0 until it.length()).map { i ->
                val obj = it.getJSONObject(i)
                RadarFrame(time = obj.getLong("time"), path = obj.getString("path"))
            }
        } ?: emptyList()

        RainviewerFrames(
            host = host,
            past = parseFrames(radar.optJSONArray("past")),
            nowcast = parseFrames(radar.optJSONArray("nowcast")),
            satellite = parseFrames(satellite?.optJSONArray("infrared"))
        )
    }
}

/** Calque "Precipitation" : taille 256px, palette 2 (Universal Blue), lissage+neige actives. */
fun radarTileUrlTemplate(host: String, frame: RadarFrame): String {
    return "$host${frame.path}/256/{z}/{x}/{y}/2/1_1.png"
}

/** Calque "Nuages" (satellite infrarouge) : pas de palette couleur/options, juste taille+position. */
fun satelliteTileUrlTemplate(host: String, frame: RadarFrame): String {
    return "$host${frame.path}/256/{z}/{x}/{y}/0/0_0.png"
}

/**
 * Calque "Temperature" : tuiles OpenWeatherMap (fournisseur tiers, cle API gratuite requise,
 * cf. OPENWEATHERMAP_API_KEY dans local.properties). Contrairement a la precipitation et aux
 * nuages, ce calque n'a pas d'historique/prevision chez OpenWeatherMap gratuit : une seule
 * image "actuelle", pas de defilement temporel.
 */
fun temperatureTileUrlTemplate(apiKey: String): String {
    return "https://tile.openweathermap.org/map/temp_new/{z}/{x}/{y}.png?appid=$apiKey"
}
