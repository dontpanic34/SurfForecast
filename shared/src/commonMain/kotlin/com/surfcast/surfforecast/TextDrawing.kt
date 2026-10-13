package com.surfcast.surfforecast

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
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
    val layout = textMeasurer.measure(tr(text), style)
    drawText(layout, topLeft = Offset(x, baselineY - layout.firstBaseline))
}

/**
 * Texte lisible sur n'importe quel fond : un contour fin de la couleur [halo] sous le texte.
 * Le contour est fait de copies décalées (8 directions) plutôt que d'un Stroke de texte, qui empâtait
 * les chiffres à 11-12 sp et les rendait illisibles.
 */
fun DrawScope.drawHaloTextAtBaseline(
    textMeasurer: TextMeasurer,
    text: String,
    style: TextStyle,
    halo: androidx.compose.ui.graphics.Color,
    x: Float,
    baselineY: Float
) {
    val outline = style.copy(color = halo)
    val r = 1.1.dp.toPx()
    val d = r * 0.7071f
    val offsets = floatArrayOf(r, 0f, -r, 0f, 0f, r, 0f, -r, d, d, d, -d, -d, d, -d, -d)
    var i = 0
    while (i < offsets.size) {
        drawTextAtBaseline(textMeasurer, text, outline, x + offsets[i], baselineY + offsets[i + 1])
        i += 2
    }
    drawTextAtBaseline(textMeasurer, text, style, x, baselineY)
}

/** Équivalent de `paint.descent() + paint.ascent()` (sert à centrer verticalement un texte). */
fun TextMeasurer.fontAscentDescentSum(style: TextStyle): Float {
    val layout = measure("0", style)
    return layout.size.height - 2f * layout.firstBaseline
}

/** Comme DateTimeFormatter.ofPattern("EEE", Locale.FRANCE) : "lun.", "mar."... */
fun frenchShortDayName(date: LocalDate): String = tr(when (date.dayOfWeek) {
    DayOfWeek.MONDAY -> "lun."
    DayOfWeek.TUESDAY -> "mar."
    DayOfWeek.WEDNESDAY -> "mer."
    DayOfWeek.THURSDAY -> "jeu."
    DayOfWeek.FRIDAY -> "ven."
    DayOfWeek.SATURDAY -> "sam."
    DayOfWeek.SUNDAY -> "dim."
})

/** Comme DateTimeFormatter.ofPattern("EEEE", Locale.FRANCE) : "lundi", "mardi"... */
fun frenchDayName(date: LocalDate): String = tr(when (date.dayOfWeek) {
    DayOfWeek.MONDAY -> "lundi"
    DayOfWeek.TUESDAY -> "mardi"
    DayOfWeek.WEDNESDAY -> "mercredi"
    DayOfWeek.THURSDAY -> "jeudi"
    DayOfWeek.FRIDAY -> "vendredi"
    DayOfWeek.SATURDAY -> "samedi"
    DayOfWeek.SUNDAY -> "dimanche"
})

/** Comme DateTimeFormatter.ofPattern("MMM", Locale.FRANCE) : "janv.", "févr."... */
fun frenchShortMonthName(date: LocalDate): String = tr(when (date.month.number) {
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
})

/** Comme Month.getDisplayName(TextStyle.FULL, Locale.FRANCE) : "janvier", "février"... */
fun frenchMonthName(date: LocalDate): String = tr(when (date.month.number) {
    1 -> "janvier"
    2 -> "février"
    3 -> "mars"
    4 -> "avril"
    5 -> "mai"
    6 -> "juin"
    7 -> "juillet"
    8 -> "août"
    9 -> "septembre"
    10 -> "octobre"
    11 -> "novembre"
    else -> "décembre"
})
