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
        if (spotName.isNotBlank()) append(" · ").append(spotName)
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
        if (boardOfDay != null) Text("Planche du jour : $boardOfDay", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.primary, maxLines = 1)
        FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "Houle ${formatDecimal(hourlyModel.waveHeight, 1)} m · ${hourlyModel.wavePeriod.toInt()} s",
                fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.onSurface
            )
            Text("Vent $dirFr $speed $unit", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
            if (coef != null || next != null) {
                Text(
                    buildAnnotatedString {
                        if (coef != null) { withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) { append("COEF $coef") } }
                        if (next != null) { if (coef != null) append(" "); append(if (next.first) "▲ " else "▼ "); append(next.second) }
                    },
                    fontSize = 13.sp, color = colors.onSurface, maxLines = 1
                )
            }
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
