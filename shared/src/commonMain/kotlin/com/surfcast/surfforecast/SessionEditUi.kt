package com.surfcast.surfforecast

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/** Modifier une session déjà notée : ressenti, début et durée, planche, commentaire. Les conditions d'origine restent. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SessionEditDialog(
    session: SurfSessionWithRelations,
    boards: List<QuiverBoard>,
    onSave: (SurfSession) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val s = session.session
    var feel by remember { mutableStateOf(feelIndexOf(s.rating)) }
    var startMs by remember { mutableStateOf(s.startTime) }
    var durationMin by remember { mutableIntStateOf(((s.endTime - s.startTime) / 60_000L).toInt().coerceIn(20, 300)) }
    var boardId by remember { mutableStateOf(s.quiverId) }
    var comment by remember { mutableStateOf(s.comment.orEmpty()) }

    fun startLabel(): String {
        val t = epochMillisToLocalDateTime(startMs)
        return "${t.date.dayOfMonth}/${t.date.monthNumber} · ${t.hour} h ${t.minute.toString().padStart(2, '0')}"
    }

    @Composable
    fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 13.sp, color = colors.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onClick = onMinus) { Text("−", fontSize = 20.sp) }
                Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                TextButton(onClick = onPlus) { Text("+", fontSize = 20.sp) }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Modifier la session", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Ressenti", fontSize = 12.sp, color = colors.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SESSION_FEELS.forEachIndexed { i, (word, band) ->
                        val on = feel == i
                        Text(
                            tr(word), fontSize = 13.sp, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.Normal,
                            color = if (on) bandWordColor(band) else colors.onSurface,
                            modifier = Modifier.clip(RoundedCornerShape(50))
                                .background(if (on) colors.primary.copy(alpha = 0.15f) else colors.surfaceVariant)
                                .clickable { feel = i }.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
                Stepper("Début", startLabel(), { startMs -= 5 * 60_000L }, { startMs += 5 * 60_000L })
                Stepper("Durée", "${durationMin / 60} h ${(durationMin % 60).toString().padStart(2, '0')}",
                    { durationMin = (durationMin - 5).coerceAtLeast(20) }, { durationMin = (durationMin + 5).coerceAtMost(300) })
                if (boards.isNotEmpty()) {
                    Text("Planche", fontSize = 12.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        boards.forEach { b ->
                            val on = b.id == boardId
                            Text(
                                b.model.replaceFirstChar { it.uppercase() }, fontSize = 13.sp,
                                fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                                color = if (on) colors.primary else colors.onSurface,
                                modifier = Modifier.clip(RoundedCornerShape(50))
                                    .background(if (on) colors.primary.copy(alpha = 0.15f) else colors.surfaceVariant)
                                    .clickable { boardId = b.id }.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = comment, onValueChange = { comment = it },
                    placeholder = { Text("Note personnelle", fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    s.copy(
                        rating = feel?.let { encodeFeel(it) } ?: s.rating, startTime = startMs, endTime = startMs + durationMin * 60_000L,
                        quiverId = boardId, comment = comment.trim().ifBlank { null }
                    )
                )
            }) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}
