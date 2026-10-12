package com.surfcast.surfforecast

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Les conditions actuelles du spot, écrites comme un titre en haut à gauche : « Maintenant · Montalivet · 14 h », puis
 * houle, période, vent et l'état de la marée (montante, descendante, pleine haute, pleine basse). À droite, le libellé de la note de l'heure.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NowTideCard(
    hourlyModel: HourlyUiModel,
    tideInfo: DailyTideInfo?,
    windUnit: String,
    seaTemperature: Int? = null,
    // La note de l'heure, avec son libellé et sa tendance (null = pas de libellé).
    rating: SlotRating? = null,
    spotName: String = "",
    boardOfDay: String? = null,
    // « + Session » : ouvre la saisie d'une session du journal (null = pas de bouton).
    onAddSession: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val dirFr = SurfUnitsHelper.formatCardinalFr(hourlyModel.windDirectionStr)
    val speed = SurfUnitsHelper.formatWindValue(effectiveWindKmh(hourlyModel), windUnit)
    val unit = SurfUnitsHelper.getWindUnitSymbol(windUnit)
    val title = buildString {
        append("Maintenant")
        append(" · ").append(hourlyModel.rawTime.hour).append(" h")
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.onSurface.copy(alpha = 0.6f),
                maxLines = 1, modifier = Modifier.weight(1f)
            )
            if (onAddSession != null) {
                Text(
                    "+ Session", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.primary,
                    modifier = Modifier.padding(end = 6.dp).clip(RoundedCornerShape(50)).background(colors.primary.copy(alpha = 0.15f))
                        .clickable(onClick = onAddSession).padding(horizontal = 9.dp, vertical = 2.dp)
                )
            }
            if (rating != null) {
                val band = conditionBand(rating)
                Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(band.color()).padding(horizontal = 8.dp, vertical = 2.dp)) {
                    Text(bandLabelWithTrend(rating).uppercase(), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = band.onColor(), maxLines = 1)
                }
            }
        }
        val next = nextTide(hourlyModel.rawTime.time, tideInfo)
        val coef = tideInfo?.coefficient
        val rising = isTideRising(hourlyModel.rawTime.time, tideInfo)
        val windKmh = effectiveWindKmh(hourlyModel)
        val strength = when { windKmh < 10 -> "faible"; windKmh < 20 -> "modéré"; windKmh < 30 -> "fort"; else -> "très fort" }
        val swell = "${formatDecimal(hourlyModel.waveHeight, 1).replace('.', ',')} m, ${hourlyModel.wavePeriod.toInt()} s"
        Text(
            "$swell, vent ${cardinalWordFr(dirFr)} $strength $speed $unit", fontSize = 13.sp, fontWeight = FontWeight.Bold,
            color = colors.onSurface
        )
        if (rising != null || coef != null || next != null) {
            Text(
                buildAnnotatedString {
                    val parts = mutableListOf<String>()
                    if (rising != null) parts.add(if (rising) "Marée montante" else "Marée descendante")
                    var first = true
                    fun sep() { if (!first) append(", "); first = false }
                    parts.forEach { sep(); append(it) }
                    if (coef != null) { sep(); append("coef "); withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) { append("$coef") } }
                    if (next != null) { sep(); append(if (next.first) "pleine haute" else "pleine basse"); append(" à "); append(next.second) }
                },
                fontSize = 13.sp, color = colors.onSurface
            )
        }
    }
}

/** La prochaine marée après [now] : (true = pleine mer, « 14 h 05 »), ou null sans horaires. */
internal fun nextTide(now: kotlinx.datetime.LocalTime, info: DailyTideInfo?): Pair<Boolean, String>? {
    if (info == null) return null
    val all = info.extrema.ifEmpty {
        listOfNotNull(info.highTideTime?.let { MareeExtremum("PM", it) }, info.lowTideTime?.let { MareeExtremum("BM", it) })
    }
    val cands = all.mapNotNull { e -> parseTimeStringSafe(e.time)?.let { e to it } }
    val n = cands.filter { it.second.toSecondOfDay() > now.toSecondOfDay() }.minByOrNull { it.second.toSecondOfDay() } ?: return null
    val t = n.second
    return (n.first.type == "PM") to "${t.hour} h ${t.minute.toString().padStart(2, '0')}"
}

/** Le point cardinal en toutes lettres : « S » devient « sud », « OSO » devient « ouest-sud-ouest ». */
internal fun cardinalWordFr(c: String): String {
    val names = mapOf("N" to "nord", "S" to "sud", "E" to "est", "O" to "ouest")
    fun one(ch: Char) = names[ch.toString()] ?: ch.toString()
    return when (c.length) {
        1 -> one(c[0])
        2 -> one(c[0]) + "-" + one(c[1])
        3 -> one(c[0]) + "-" + one(c[1]) + "-" + one(c[2])
        else -> c
    }.let { if (c.length == 2) it.replace("nord-", "nord-").replace("sud-", "sud-") else it }
}
