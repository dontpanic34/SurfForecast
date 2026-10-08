package com.surfcast.surfforecast

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val TIDE_CHOICES = listOf("any", "rising", "falling", "high", "low")

private fun parseHeight(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }

private fun heightText(value: Double?): String = value?.let { formatDecimal(it, 1, decimalSeparator = ',') }.orEmpty()

/**
 * Fiche d'un banc : phase de marée où il marche, fourchette de hauteur de houle, notes libres.
 * Sert aux « créneaux du banc » affichés sur l'accueil et à lire le journal.
 */
@Composable
fun MicroSpotSheetDialog(
    spot: MicroSpot,
    onSave: (MicroSpot) -> Unit,
    onDismiss: () -> Unit
) {
    var tidePhase by remember(spot.id) { mutableStateOf(spot.tidePhase) }
    var minText by remember(spot.id) { mutableStateOf(heightText(spot.minHeight)) }
    var maxText by remember(spot.id) { mutableStateOf(heightText(spot.maxHeight)) }
    var notes by remember(spot.id) { mutableStateOf(spot.notes) }

    val min = parseHeight(minText)
    val max = parseHeight(maxText)
    val rangeInvalid = min != null && max != null && min > max

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Fiche : ${spot.name}", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Dis quand ce banc marche : l'appli te prévient quand les prévisions collent.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                )
                Text("Marée", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                // Pastilles sur deux lignes pour tenir sur un écran de téléphone.
                TIDE_CHOICES.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { choice ->
                            val selected = choice == tidePhase
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                                    )
                                    .clickable { tidePhase = choice }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    tidePhaseLabel(choice),
                                    fontSize = 12.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
                Text("Houle (mètres)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = minText,
                        onValueChange = { minText = it },
                        label = { Text("Mini") },
                        singleLine = true,
                        isError = rangeInvalid,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = maxText,
                        onValueChange = { maxText = it },
                        label = { Text("Maxi") },
                        singleLine = true,
                        isError = rangeInvalid,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rangeInvalid) {
                    Text("Le mini doit être inférieur au maxi.", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (ex : le banc magique, creux au pied de la dune)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !rangeInvalid,
                onClick = {
                    onSave(spot.copy(tidePhase = tidePhase, minHeight = min, maxHeight = max, notes = notes.trim()))
                    onDismiss()
                }
            ) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}
