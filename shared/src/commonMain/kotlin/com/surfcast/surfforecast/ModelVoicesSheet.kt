package com.surfcast.surfforecast

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** « Modèles partagés » : ce que dit chaque modèle pour l'heure choisie (lecture seule ; le choix des modèles est dans Réglages). */
@Composable
fun ModelVoicesDialog(hour: HourlyUiModel, windUnit: String, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val unit = SurfUnitsHelper.getWindUnitSymbol(windUnit)
    @Composable
    fun Line(label: String, value: String) {
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 14.sp, color = colors.onSurface)
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
        title = { Text("Modèles à ${hour.rawTime.hour} h", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (hour.waveVoices.isNotEmpty()) {
                    Text("Houle", fontSize = 12.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                    hour.waveVoices.forEach { v ->
                        val per = if (v.period > 0.0) " · ${v.period.roundToInt()} s" else ""
                        val dir = if (!v.direction.isNaN()) " · ${SurfUnitsHelper.formatCardinalFr(getCardinalDirection(v.direction.toDouble()))}" else ""
                        Line(v.label, "${formatDecimal(v.value, 1).replace('.', ',')} m$per$dir")
                    }
                }
                if (hour.windVoiceList.isNotEmpty()) {
                    Text("Vent", fontSize = 12.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                    hour.windVoiceList.forEach { v ->
                        val dir = if (!v.direction.isNaN()) " ${SurfUnitsHelper.formatCardinalFr(getCardinalDirection(v.direction.toDouble()))}" else ""
                        Line(v.label, "${SurfUnitsHelper.formatWindValue(v.value, windUnit)} $unit$dir")
                    }
                }
                Text(
                    "Pour changer de modèle : Réglages.", fontSize = 12.sp, color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
        }
    )
}
