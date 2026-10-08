package com.surfcast.surfforecast

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val compassPoints = listOf(
    "N" to 0, "NE" to 45, "E" to 90, "SE" to 135,
    "S" to 180, "SO" to 225, "O" to 270, "NO" to 315
)

/** Nom du point cardinal le plus proche (8 points). */
fun compassName(degrees: Int): String {
    val d = ((degrees % 360) + 360) % 360
    return compassPoints.minByOrNull { angularDifference(d.toDouble(), it.second.toDouble()) }!!.first
}

/**
 * Orientation de la plage du spot (Paramètres > Prévisions) : vers où elle regarde. Le score s'en
 * sert pour juger la direction de la houle et ce qui est offshore / onshore.
 * [onChange] reçoit les degrés, ou null pour revenir à la valeur par défaut.
 */
@Composable
fun BeachFacingCard(
    spotName: String,
    facing: Int?,
    defaultFacing: Int?,
    onChange: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val current = facing ?: 270
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("📍 Orientation de $spotName", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
        Text(
            "Vers où regarde la plage (275° = plein ouest). Le score s'en sert pour savoir si la houle arrive de face " +
                "et si le vent est offshore (de la terre) ou onshore (de la mer). Valeur estimée : corrige-la si tu connais la plage.",
            fontSize = 12.sp,
            color = colors.onSurfaceVariant
        )
        compassPoints.chunked(4).forEach { rowPoints ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                rowPoints.forEach { (label, deg) ->
                    val selected = compassName(current) == label
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(50))
                            .background(if (selected) colors.primary else colors.background)
                            .clickable { onChange(deg) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (selected) colors.onPrimary else colors.onBackground)
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = { onChange(((current - 5) % 360 + 360) % 360) }) { Text("− 5°", fontSize = 13.sp) }
            Text("$current° (${compassName(current)})", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
            TextButton(onClick = { onChange((current + 5) % 360) }) { Text("+ 5°", fontSize = 13.sp) }
        }
        if (defaultFacing != null && facing != defaultFacing) {
            TextButton(onClick = { onChange(null) }, modifier = Modifier.padding(0.dp)) {
                Text("Rétablir la valeur par défaut ($defaultFacing°)", fontSize = 12.sp)
            }
        }
    }
}
