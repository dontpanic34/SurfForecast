package com.surfcast.surfforecast

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt

/** La dernière mesure d'une station Météo-France proche du spot (vent, rafales, direction, pluie de l'heure), lue par le relais surflog.fr/api/obs. */
data class ObservedNow(
    val stationName: String,
    val distanceKm: Double,
    val timeUtc: LocalDateTime,
    val windKmh: Int,
    val gustKmh: Int?,
    val directionDeg: Int?,
    val rainMm: Double?,
    val tempC: Double?
)

/** « VENDAYS-MONTALIVE » devient « Vendays-Montalivet » (Météo-France écrit le nom sans son dernier T). */
internal fun niceStationName(raw: String): String {
    val nice = raw.lowercase().split(" ").joinToString(" ") { w -> w.split("-").joinToString("-") { p -> p.replaceFirstChar { it.uppercase() } } }
    // Météo-France écrit certains noms sans leur dernière lettre : on remet le vrai nom de la commune.
    return STATION_NAME_FIXES[nice] ?: nice
}

private val STATION_NAME_FIXES = mapOf("Vendays-Montalive" to "Vendays-Montalivet")

/** Une mesure plus vieille que cela (heures) n'est plus affichée : ce n'est plus ce qui se passe maintenant. */
const val OBSERVED_MAX_AGE_HOURS = 3

/** « Mesuré à 14 h 00, Vendays-Montalivet (4 km) : vent 7 km/h ONO, rafales 12 · pluie 0,4 mm » (heure locale). */
internal fun observedLine(o: ObservedNow, windUnit: String, zone: TimeZone = TimeZone.currentSystemDefault()): String {
    val local = o.timeUtc.toInstant(TimeZone.UTC).toLocalDateTime(zone)
    val unit = SurfUnitsHelper.getWindUnitSymbol(windUnit)
    val dir = o.directionDeg?.let { " " + SurfUnitsHelper.formatCardinalFr(getCardinalDirection(it.toDouble())) }.orEmpty()
    val gust = o.gustKmh?.takeIf { it > o.windKmh }?.let { ", rafales ${SurfUnitsHelper.formatWindValue(it, windUnit)} $unit" }.orEmpty()
    val rain = o.rainMm?.takeIf { it >= 0.1 }?.let { " · pluie ${formatDecimal(it, 1).replace('.', ',')} mm" }.orEmpty()
    return "Mesuré à ${local.hour} h ${local.minute.toString().padStart(2, '0')}, ${niceStationName(o.stationName)} (${o.distanceKm.roundToInt()} km) : " +
        "vent ${SurfUnitsHelper.formatWindValue(o.windKmh, windUnit)} $unit$dir$gust$rain"
}
