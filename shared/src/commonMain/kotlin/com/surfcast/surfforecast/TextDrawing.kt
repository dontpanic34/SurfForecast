package com.surfcast.surfforecast

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number

// Équivalents multiplateformes de android.graphics.Paint, pour porter les Canvas d'app/
// sans changer leurs coordonnées.

/** Comme Paint.drawText(text, x, y) : texte aligné à gauche, ligne de base à [baselineY]. */
fun DrawScope.drawTextAtBaseline(
    textMeasurer: TextMeasurer,
    text: String,
    style: TextStyle,
    x: Float,
    baselineY: Float
) {
    val layout = textMeasurer.measure(text, style)
    drawText(layout, topLeft = Offset(x, baselineY - layout.firstBaseline))
}

/** Équivalent de `paint.descent() + paint.ascent()` (sert à centrer verticalement un texte). */
fun TextMeasurer.fontAscentDescentSum(style: TextStyle): Float {
    val layout = measure("0", style)
    return layout.size.height - 2f * layout.firstBaseline
}

/** Comme DateTimeFormatter.ofPattern("EEE", Locale.FRANCE) : "lun.", "mar."... */
fun frenchShortDayName(date: LocalDate): String = when (date.dayOfWeek) {
    DayOfWeek.MONDAY -> "lun."
    DayOfWeek.TUESDAY -> "mar."
    DayOfWeek.WEDNESDAY -> "mer."
    DayOfWeek.THURSDAY -> "jeu."
    DayOfWeek.FRIDAY -> "ven."
    DayOfWeek.SATURDAY -> "sam."
    DayOfWeek.SUNDAY -> "dim."
}

/** Comme DateTimeFormatter.ofPattern("EEEE", Locale.FRANCE) : "lundi", "mardi"... */
fun frenchDayName(date: LocalDate): String = when (date.dayOfWeek) {
    DayOfWeek.MONDAY -> "lundi"
    DayOfWeek.TUESDAY -> "mardi"
    DayOfWeek.WEDNESDAY -> "mercredi"
    DayOfWeek.THURSDAY -> "jeudi"
    DayOfWeek.FRIDAY -> "vendredi"
    DayOfWeek.SATURDAY -> "samedi"
    DayOfWeek.SUNDAY -> "dimanche"
}

/** Comme DateTimeFormatter.ofPattern("MMM", Locale.FRANCE) : "janv.", "févr."... */
fun frenchShortMonthName(date: LocalDate): String = when (date.month.number) {
    1 -> "janv."
    2 -> "févr."
    3 -> "mars"
    4 -> "avr."
    5 -> "mai"
    6 -> "juin"
    7 -> "juil."
    8 -> "août"
    9 -> "sept."
    10 -> "oct."
    11 -> "nov."
    else -> "déc."
}
