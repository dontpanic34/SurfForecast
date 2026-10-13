package com.surfcast.surfforecast

import kotlinx.datetime.LocalDateTime
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Une bouée Candhis (Cerema) en temps réel. */
data class Buoy(val code: String, val name: String, val latitude: Double, val longitude: Double)

/** Dernière mesure d'une bouée de référence (heure UTC), avec sa distance au spot. */
data class BuoyReading(
    val buoy: Buoy,
    val distanceKm: Int,
    val significantHeight: Double,
    val maxHeight: Double?,
    val period: Double?,
    val direction: Int?,
    val timeUtc: LocalDateTime
)

/**
 * Bouées temps réel de Candhis (liste du 13 octobre 2026, voir docs/BOUEES_CANDHIS.md).
 * Royan (à l'entrée de l'estuaire, 14 m de fond) et Le Havre (balise de port) ne voient pas la houle de la côte ouverte :
 * elles ne servent jamais de référence.
 */
object BuoyCatalog {
    val all: List<Buoy> = listOf(
        Buoy("08002", "Baie de Somme", 50.2500, 1.3353),
        Buoy("05008", "Cherbourg", 49.6941, -1.6214),
        Buoy("02922", "Ile de Batz", 48.7283, -4.0717),
        Buoy("02911", "Les Pierres Noires", 48.2903, -4.9683),
        Buoy("05602", "Belle-Ile", 47.2850, -3.2850),
        Buoy("04403", "Plateau du Four", 47.2398, -2.7805),
        Buoy("08505", "Noirmoutier", 46.9238, -2.4679),
        Buoy("08504", "Ile d'Yeu Nord", 46.8332, -2.2950),
        Buoy("03302", "Cap Ferret", 44.6525, -1.4467),
        Buoy("06403", "Saint-Jean-de-Luz", 43.4083, -1.6817),
        Buoy("03001", "Espiguette", 43.4110, 4.1625),
        Buoy("03404", "Sète", 43.3713, 3.7771),
        Buoy("01305", "Le Planier", 43.2083, 5.2300),
        Buoy("01102", "Port La Nouvelle", 43.0097, 3.0861),
        Buoy("08302", "Porquerolles", 42.9667, 6.2048),
        Buoy("01101", "Leucate", 42.9167, 3.1250),
        Buoy("06601", "Banyuls", 42.4895, 3.1677),
        Buoy("02B05", "Alistro", 42.2617, 9.6433),
        Buoy("02A01", "Bonifacio", 41.3226, 8.8775)
    )

    /** Au-delà de cette distance, la bouée ne dit plus rien du spot : on n'en propose pas. */
    const val MAX_DISTANCE_KM = 120

    /** La bouée de référence d'un point (la plus proche, à moins de [MAX_DISTANCE_KM]) et sa distance, ou null. */
    fun referenceFor(latitude: Double, longitude: Double): Pair<Buoy, Int>? =
        all.map { it to greatCircleKm(latitude, longitude, it.latitude, it.longitude) }
            .minByOrNull { it.second }
            ?.takeIf { it.second <= MAX_DISTANCE_KM }
}

internal fun greatCircleKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Int {
    val p1 = lat1 * PI / 180.0
    val p2 = lat2 * PI / 180.0
    val dp = p2 - p1
    val dl = (lon2 - lon1) * PI / 180.0
    val a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
    return (2 * 6371.0 * asin(sqrt(a))).roundToInt()
}
