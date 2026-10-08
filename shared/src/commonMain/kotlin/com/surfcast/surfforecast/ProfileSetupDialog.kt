@file:Suppress("SpellCheckingInspection")
package com.surfcast.surfforecast

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Écran de la mise à jour « Mon profil » : montré à tout le monde (anciens comme nouveaux utilisateurs) pour
 * encourager fortement à renseigner son profil, sans jamais l'imposer : « Plus tard » est toujours là (l'écran
 * revient alors aux prochaines ouvertures, trois fois au plus). Le poids suffit pour valider ; l'âge, la taille
 * et la forme physique affinent le volume recommandé. Ensuite, on propose d'aller ajouter son matériel.
 */
@Composable
fun ProfileSetupDialog(
    surferLevel: String,
    onLevelChanged: (String) -> Unit,
    body: BodyState,
    onBodyChanged: (BodyState) -> Unit,
    onDone: (openGear: Boolean) -> Unit,
    onLater: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val numeric = KeyboardOptions(keyboardType = KeyboardType.Number)
    val levels = listOf("beginner", "intermediate", "confirmed", "expert")
    val ready = body.weightKg > 0

    Dialog(
        onDismissRequest = onLater,
        properties = DialogProperties(dismissOnClickOutside = false, usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.92f).clip(RoundedCornerShape(16.dp)),
            color = colors.background
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("🏄 Nouveau : ton profil", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
                    Text("L'appli de surf qui s'adapte à ton niveau !", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.primary)
                    Text(
                        "Grosse mise à jour : l'appli calcule maintenant ses notes pour toi (énergie de vague qui te convient, vent, rafales, clapot, « trop gros » " +
                            "quand ça dépasse ton niveau) et gère ton matériel : tes planches, leur volume, et le volume recommandé pour toi. " +
                            "Il lui faut quelques informations. Ça prend une minute, elles restent sur ton appareil et tu pourras tout changer dans " +
                            "⚙ Paramètres › Mon profil.",
                        fontSize = 12.5.sp, color = colors.onSurfaceVariant
                    )

                    Text("1. Ton niveau", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
                    PillRow(levels.map { levelLabel(it) }, levels.indexOf(surferLevel)) { onLevelChanged(levels[it]) }
                    Text(
                        text = when (surferLevel) {
                            "beginner" -> "Tu surfes plutôt les mousses."
                            "intermediate" -> "Tu commences à aller au large et à suivre les vagues."
                            "confirmed" -> "Tu es autonome dans l'eau et tu surfes seul."
                            "expert" -> "Tu utilises le plein potentiel de la vague et tu cherches la puissance."
                            else -> "Profil personnalisé conservé : tu pourras l'ajuster dans Mon profil."
                        },
                        fontSize = 12.sp, color = colors.onSurfaceVariant
                    )

                    Text("2. Toi (le poids suffit, le reste affine)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        var ageText by remember { mutableStateOf(if (body.ageYears > 0) body.ageYears.toString() else "") }
                        var heightText by remember { mutableStateOf(if (body.heightCm > 0) body.heightCm.toString() else "") }
                        var weightText by remember { mutableStateOf(if (body.weightKg > 0) body.weightKg.toString() else "") }
                        fun push() = onBodyChanged(
                            body.copy(ageYears = ageText.toIntOrNull() ?: 0, heightCm = heightText.toIntOrNull() ?: 0, weightKg = weightText.toIntOrNull() ?: 0)
                        )
                        OutlinedTextField(
                            value = ageText, onValueChange = { v -> ageText = v.filter { it.isDigit() }.take(3); push() },
                            label = { Text("Âge") }, suffix = { Text("Ans") }, singleLine = true, keyboardOptions = numeric, modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = heightText, onValueChange = { v -> heightText = v.filter { it.isDigit() }.take(3); push() },
                            label = { Text("Taille") }, suffix = { Text("Cm") }, singleLine = true, keyboardOptions = numeric, modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = weightText, onValueChange = { v -> weightText = v.filter { it.isDigit() }.take(3); push() },
                            label = { Text("Poids") }, suffix = { Text("Kg") }, singleLine = true, keyboardOptions = numeric, modifier = Modifier.weight(1f)
                        )
                    }
                    Text("Forme physique", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground)
                    PillRow(FITNESS_LEVELS.map { it.first }, body.fitness) { onBodyChanged(body.copy(fitness = it)) }

                    recommendedVolumeL(body, body.levelIndex(surferLevel))?.let { rec ->
                        Column(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(colors.primary.copy(alpha = 0.12f)).padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Ton volume de planche recommandé", fontSize = 12.sp, color = colors.onSurfaceVariant)
                            Text(formatFr(rec, 1) + " L", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = colors.primary)
                            Text("Pour une planche courte. Tu pourras comparer chacune de tes planches.", fontSize = 11.sp, color = colors.onSurfaceVariant)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                if (!ready) {
                    Text("Renseigne au moins ton poids pour valider.", fontSize = 12.sp, color = colors.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(6.dp))
                }
                Button(onClick = { onDone(true) }, enabled = ready, modifier = Modifier.fillMaxWidth()) {
                    Text("Valider et ajouter mon matériel")
                }
                TextButton(onClick = { onDone(false) }, enabled = ready, modifier = Modifier.fillMaxWidth()) {
                    Text("Valider, j'ajouterai mon matériel plus tard")
                }
                TextButton(onClick = onLater, modifier = Modifier.fillMaxWidth()) {
                    Text("Plus tard", fontSize = 12.sp, color = colors.onSurfaceVariant)
                }
            }
        }
    }
}
