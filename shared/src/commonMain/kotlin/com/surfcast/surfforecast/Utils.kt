package com.surfcast.surfforecast

import kotlinx.datetime.LocalDateTime
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

fun getCardinalDirection(angle: Double): String {
    val directions = arrayOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSO", "SO", "OSO", "O", "ONO", "NO", "NNO")
    val index = ((angle % 360) / 22.5).roundToInt() % 16
    return directions[index]
}

fun formatHour(dateTime: LocalDateTime): String = "${dateTime.hour.toString().padStart(2, '0')}:00"

/**
 * Indice d'énergie des vagues H² x T² x 1.962, calibré sur l'échelle des outils surf grand
 * public (ex : 1.2m/14s -> ~554 kJ). Identique à SurfRepository.calculateWaveEnergyReal côté Android.
 */
fun calculateWaveEnergyReal(heightMeters: Double, periodSeconds: Double): Int {
    val energy = 1.962 * heightMeters * heightMeters * periodSeconds * periodSeconds
    return energy.roundToInt().coerceAtLeast(0)
}

/**
 * Remplace String.format("%.Nf") (JVM uniquement). Arrondi au plus proche comme
 * String.format ; `decimalSeparator` = ',' reproduit Locale.FRANCE, '.' Locale.US.
 */
fun formatDecimal(value: Double, decimals: Int, decimalSeparator: Char = '.'): String {
    var factor = 1L
    repeat(decimals) { factor *= 10 }
    // floor(x + 0.5) et non kotlin.math.round, qui arrondit les .5 au pair (0.25 -> "0.2").
    val scaled = floor(abs(value) * factor + 0.5).toLong()
    val intPart = scaled / factor
    val sign = if (value < 0 && scaled != 0L) "-" else ""
    if (decimals == 0) return "$sign$intPart"
    val fracPart = (scaled % factor).toString().padStart(decimals, '0')
    return "$sign$intPart$decimalSeparator$fracPart"
}
