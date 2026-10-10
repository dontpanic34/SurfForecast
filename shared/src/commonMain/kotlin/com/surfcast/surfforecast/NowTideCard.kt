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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Les conditions actuelles du spot, écrites comme un titre en haut à gauche : « Maintenant · Montalivet · 14 h », puis
 * houle, période, vent, eau, marée haute, marée basse et coefficient à la suite. À droite, le libellé de la note de l'heure.
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
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val dirFr = SurfUnitsHelper.formatCardinalFr(hourlyModel.windDirectionStr)
    val speed = SurfUnitsHelper.formatWindValue(hourlyModel.windSpeedKmh, windUnit)
    val unit = SurfUnitsHelper.getWindUnitSymbol(windUnit)
    val high = tideInfo?.highTideTime
    val low = tideInfo?.lowTideTime
    val coef = tideInfo?.coefficient
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
            if (rating != null) {
                val band = conditionBand(rating)
                Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(band.color()).padding(horizontal = 8.dp, vertical = 2.dp)) {
                    Text(bandLabelWithTrend(rating).uppercase(), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = band.onColor(), maxLines = 1)
                }
            }
        }
        FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "Houle ${formatDecimal(hourlyModel.waveHeight, 1)} m · ${hourlyModel.wavePeriod.toInt()} s",
                fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.onSurface
            )
            Text("Vent $dirFr $speed $unit", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
            if (seaTemperature != null) {
                Text("Eau $seaTemperature °C", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.onSurface, maxLines = 1)
            }
            if (high != null) Text("▲ Haute $high", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.onSurface, maxLines = 1)
            if (low != null) Text("▼ Basse $low", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.onSurface, maxLines = 1)
            if (coef != null) Text("Coef. $coef", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.onSurface, maxLines = 1)
        }
    }
}
