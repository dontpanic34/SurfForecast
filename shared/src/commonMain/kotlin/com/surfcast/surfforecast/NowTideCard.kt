package com.surfcast.surfforecast

import androidx.compose.foundation.layout.Arrangement
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
 * « Maintenant » + la marée, en deux lignes lisibles (remplace l'ancien bandeau sombre) :
 * houle / période / vent de l'heure actuelle, puis pleine mer, basse mer et coefficient avec leurs noms.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NowTideCard(
    hourlyModel: HourlyUiModel,
    tideInfo: DailyTideInfo?,
    windUnit: String,
    seaTemperature: Int? = null,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val dirFr = SurfUnitsHelper.formatCardinalFr(hourlyModel.windDirectionStr)
    val windColor = SurfUnitsHelper.getSurfWindColor(dirFr, hourlyModel.windSpeedKmh)
    val speed = SurfUnitsHelper.formatWindValue(hourlyModel.windSpeedKmh, windUnit)
    val unit = SurfUnitsHelper.getWindUnitSymbol(windUnit)
    val high = tideInfo?.highTideTime
    val low = tideInfo?.lowTideTime
    val coef = tideInfo?.coefficient

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Maintenant", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.onSurface.copy(alpha = 0.6f))
            Text(
                "${formatDecimal(hourlyModel.waveHeight, 1)} m · ${hourlyModel.wavePeriod.toInt()} s",
                fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.onSurface
            )
            Text("Vent $dirFr $speed $unit", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = windColor)
            if (seaTemperature != null) {
                Text("Eau $seaTemperature °C", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = waterTempColor(), maxLines = 1)
            }
        }
        if (high != null || low != null) {
            FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Marée", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.onSurface.copy(alpha = 0.6f))
                if (high != null) Text("▲ Haute $high", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF42A5F5), maxLines = 1)
                if (low != null) Text("▼ Basse $low", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF26A69A), maxLines = 1)
                if (coef != null) Text("Coef. $coef", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.onSurface, maxLines = 1)
            }
        }
    }
}
