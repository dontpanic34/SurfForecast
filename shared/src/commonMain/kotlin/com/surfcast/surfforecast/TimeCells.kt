package com.surfcast.surfforecast

import kotlin.math.ceil
import kotlin.math.floor

/**
 * Une case de la ligne de temps : une heure (le jour) ou un bloc de plusieurs heures (la nuit). [first] et [last] sont des
 * indices dans la liste des heures du jour (inclus).
 */
data class TimeCell(val first: Int, val last: Int, val isBlock: Boolean) { // isBlock : une case de nuit, même quand elle ne couvre qu'une heure
    val size: Int get() = last - first + 1
}

/** Largeur d'un bloc de nuit, en heures. */
const val NIGHT_BLOCK_HOURS = 3

/** Le jour garde l'heure par heure deux heures avant le lever et deux heures après le coucher. */
const val DAY_MARGIN_HOURS = 2

/**
 * Découpe les heures d'un jour en cases : heure par heure de (lever − 2 h) à (coucher + 2 h), le reste (la nuit) par blocs de
 * [NIGHT_BLOCK_HOURS] heures. Sans lever ni coucher connus, tout reste heure par heure.
 */
fun buildTimeCells(hours: List<HourlyUiModel>, sun: DailySunInfo?): List<TimeCell> {
    if (hours.isEmpty()) return emptyList()
    if (sun == null) return hours.indices.map { TimeCell(it, it, false) }
    val lowHour = floor(sun.sunrise.hour + sun.sunrise.minute / 60.0).toInt() - DAY_MARGIN_HOURS
    val highHour = ceil(sun.sunset.hour + sun.sunset.minute / 60.0).toInt() + DAY_MARGIN_HOURS
    val cells = ArrayList<TimeCell>()
    var i = 0
    // La nuit d'avant le lever.
    while (i < hours.size && hours[i].rawTime.hour < lowHour) {
        var j = i
        while (j + 1 < hours.size && hours[j + 1].rawTime.hour < lowHour && j + 1 - i < NIGHT_BLOCK_HOURS) j++
        cells += TimeCell(i, j, true)
        i = j + 1
    }
    // Le jour, heure par heure.
    while (i < hours.size && hours[i].rawTime.hour <= highHour) {
        cells += TimeCell(i, i, false)
        i++
    }
    // La nuit d'après le coucher.
    while (i < hours.size) {
        val j = minOf(i + NIGHT_BLOCK_HOURS - 1, hours.size - 1)
        cells += TimeCell(i, j, true)
        i = j + 1
    }
    return cells
}

/** L'indice de la case qui contient l'heure d'indice [hourIndex]. */
fun cellIndexOf(cells: List<TimeCell>, hourIndex: Int): Int =
    cells.indexOfFirst { hourIndex in it.first..it.last }.coerceAtLeast(0)

/** L'heure qui représente une case quand on la touche : l'heure même, ou l'heure du milieu d'un bloc. */
fun TimeCell.representativeIndex(): Int = (first + last) / 2

/** Position (0 à 1) d'une heure décimale [hour] (par exemple 8,28 pour 8 h 17) sur la ligne de temps. */
fun timeFraction(cells: List<TimeCell>, hours: List<HourlyUiModel>, hour: Double): Float {
    if (cells.isEmpty() || hours.isEmpty()) return 0f
    val k = cells.indexOfFirst { c -> hour < hours[c.last].rawTime.hour + 1.0 }.let { if (it < 0) cells.lastIndex else it }
    val c = cells[k]
    val start = hours[c.first].rawTime.hour.toDouble()
    val end = hours[c.last].rawTime.hour + 1.0
    val inside = ((hour - start) / (end - start)).coerceIn(0.0, 1.0)
    return ((k + inside) / cells.size).toFloat()
}
