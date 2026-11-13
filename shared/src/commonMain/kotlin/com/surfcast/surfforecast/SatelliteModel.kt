package com.surfcast.surfforecast

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.roundToInt

/**
 * « Vue satellite » (maquette `docs/maquettes/vue-satellite.html`) : la bande de côte photographiée par Sentinel-2 à chaque passage,
 * rangée par la collecte (`scripts/collect/satellite.py`) dans la branche `data`. Ici : le cadre de la bande, la liste des photos,
 * les points posés par l'utilisateur (gardés sur son téléphone, jamais dans le dépôt) et la lecture de coordonnées.
 */
object SatelliteSource {
    const val BASE_URL = "https://raw.githubusercontent.com/dontpanic34/SurfForecast/data/data/sat/montalivet/"
    const val INDEX_URL = BASE_URL + "index.json"

    /** Au-delà de cette part de nuages sur le point (en %), la photo compte comme nuageuse. */
    const val CLOUDY_PERCENT = 30

    /** Hauteur de la vue d'un point, en pixels de la bande (2,2 km). */
    const val VIEW_ROWS = 220
    const val VIEW_COLS = 180
}

/** La marée au moment de la photo : état « basse », « haute », « montante » ou « descendante », niveau (m) et marnage du jour (m). */
data class SatTide(val state: String, val level: Double?, val range: Double?) {
    val label: String
        get() = when (state) {
            "basse" -> "marée basse"
            "haute" -> "marée haute"
            "montante" -> "marée montante"
            else -> "marée descendante"
        } + (range?.let { " · marnage ${formatDecimal(it, 1).replace('.', ',')} m" } ?: "")
}

/** Une photo : date « AAAAMMJJ », heure UTC, fichier, part de nuages par tranche du nord au sud, marée. */
data class SatImage(val date: String, val time: String, val file: String, val cloud: List<Int>, val tide: SatTide?) {
    val year: Int get() = date.substring(0, 4).toInt()
    val month: Int get() = date.substring(4, 6).toInt()
    val day: Int get() = date.substring(6, 8).toInt()

    /** Numéro de jour approché (pour comparer des dates et placer les ronds de la ligne du temps). */
    val dayNumber: Int get() = year * 372 + (month - 1) * 31 + day
}

/** Le cadre de la bande : nord en haut, latitude et longitude linéaires. */
data class SatIndex(
    val latTop: Double, val latBottom: Double, val lonLeft: Double, val lonRight: Double,
    val width: Int, val height: Int, val blockRows: Int,
    val homeLat: Double, val homeLon: Double,
    val images: List<SatImage>
) {
    fun rowOf(lat: Double): Double = (latTop - lat) / (latTop - latBottom) * height
    fun colOf(lon: Double): Double = (lon - lonLeft) / (lonRight - lonLeft) * width
    fun latOf(row: Double): Double = latTop - row / height * (latTop - latBottom)
    fun lonOf(col: Double): Double = lonLeft + col / width * (lonRight - lonLeft)
    fun contains(lat: Double, lon: Double): Boolean = lat in latBottom..latTop && lon in lonLeft..lonRight

    /** Part de nuages (en %) autour du point, sur la hauteur de sa vue. */
    fun cloudAt(image: SatImage, lat: Double): Int {
        if (image.cloud.isEmpty()) return 0
        val center = rowOf(lat)
        val first = ((center - SatelliteSource.VIEW_ROWS / 2) / blockRows).toInt().coerceIn(0, image.cloud.size - 1)
        val last = ((center + SatelliteSource.VIEW_ROWS / 2) / blockRows).toInt().coerceIn(0, image.cloud.size - 1)
        return image.cloud.subList(first, last + 1).average().roundToInt()
    }

    fun isCloudy(image: SatImage, lat: Double): Boolean = cloudAt(image, lat) > SatelliteSource.CLOUDY_PERCENT

    /** Distance au point de repère de la bande, en mots : « 8 km au sud ». */
    fun distanceWord(lat: Double, lon: Double): String {
        val km = distanceKm(homeLat, homeLon, lat, lon)
        return if (km < 0.3) "au point de repère" else "${formatDecimal(km, 1).replace('.', ',')} km au ${if (lat > homeLat) "nord" else "sud"}"
    }
}

/** Un point posé sur la côte. */
data class SatPoint(val name: String, val lat: Double, val lon: Double)

object SatelliteModel {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseIndex(text: String): SatIndex? = runCatching {
        val o = json.parseToJsonElement(text).jsonObject
        fun d(k: String) = o.getValue(k).jsonPrimitive.doubleOrNull ?: error(k)
        SatIndex(
            latTop = d("lat_top"), latBottom = d("lat_bottom"), lonLeft = d("lon_left"), lonRight = d("lon_right"),
            width = o.getValue("width").jsonPrimitive.int, height = o.getValue("height").jsonPrimitive.int,
            blockRows = o["block_rows"]?.jsonPrimitive?.intOrNull ?: 50,
            homeLat = d("lat"), homeLon = d("lon"),
            images = o.getValue("images").jsonArray.map { e ->
                val i = e.jsonObject
                val tide = (i["tide"] as? kotlinx.serialization.json.JsonObject)?.let { t ->
                    SatTide(t.getValue("state").jsonPrimitive.content, t["level"]?.jsonPrimitive?.doubleOrNull, t["range"]?.jsonPrimitive?.doubleOrNull)
                }
                SatImage(
                    date = i.getValue("date").jsonPrimitive.content,
                    time = i["time"]?.jsonPrimitive?.content ?: "",
                    file = i.getValue("file").jsonPrimitive.content,
                    cloud = i["cloud"]?.jsonArray?.map { it.jsonPrimitive.int } ?: emptyList(),
                    tide = tide
                )
            }.sortedBy { it.date }
        )
    }.getOrNull()

    /**
     * Les photos à montrer pour un point : sur [months] mois avant la plus récente, sans les nuageuses si [clearOnly],
     * à marée basse seulement si [lowTideOnly].
     */
    fun frames(index: SatIndex, lat: Double, months: Int, clearOnly: Boolean, lowTideOnly: Boolean): List<SatImage> {
        val last = index.images.lastOrNull() ?: return emptyList()
        val from = last.dayNumber - months * 31
        return index.images.filter {
            it.dayNumber >= from && (!clearOnly || !index.isCloudy(it, lat)) && (!lowTideOnly || it.tide?.state == "basse")
        }
    }

    /**
     * Lit une latitude et une longitude dans un texte : « 45.31, -1.16 », « 45,31 -1,16 » ou un lien Google Maps
     * (…/@45.31,-1.16,15z, …?q=45.31,-1.16, …!3d45.31!4d-1.16). Null si rien de lisible (un lien court ne contient pas de coordonnées).
     */
    fun parseCoordinates(text: String): Pair<Double, Double>? {
        fun ok(lat: Double?, lon: Double?) = if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) lat to lon else null
        Regex("!3d(-?\\d+\\.\\d+)!4d(-?\\d+\\.\\d+)").find(text)?.let { m -> ok(m.groupValues[1].toDoubleOrNull(), m.groupValues[2].toDoubleOrNull())?.let { return it } }
        Regex("[@=](-?\\d+\\.\\d+),(-?\\d+\\.\\d+)").find(text)?.let { m -> ok(m.groupValues[1].toDoubleOrNull(), m.groupValues[2].toDoubleOrNull())?.let { return it } }
        val numbers = Regex("-?\\d{1,3}[.,]\\d{2,}").findAll(text).map { it.value.replace(',', '.').toDoubleOrNull() }.toList()
        return if (numbers.size >= 2) ok(numbers[0], numbers[1]) else null
    }

    private const val KEY = "sat_points"

    /** Les points de l'utilisateur (une ligne « nom|lat|lon » par point), gardés sur l'appareil. */
    fun loadPoints(prefs: KeyValueStore): List<SatPoint> =
        (prefs.getString(KEY, "") ?: "").lines().mapNotNull { line ->
            val p = line.split('|')
            val lat = p.getOrNull(1)?.toDoubleOrNull()
            val lon = p.getOrNull(2)?.toDoubleOrNull()
            if (p.size == 3 && p[0].isNotBlank() && lat != null && lon != null) SatPoint(p[0], lat, lon) else null
        }

    fun savePoints(prefs: KeyValueStore, points: List<SatPoint>) {
        prefs.putString(KEY, points.joinToString("\n") { "${it.name.replace('|', ' ').replace('\n', ' ').trim()}|${it.lat}|${it.lon}" })
    }
}
