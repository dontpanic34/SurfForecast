package com.surfcast.surfforecast

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Écran de bienvenue (première ouverture, puis Paramètres > « Revoir l'introduction ») :
 * un seul choix, le niveau, et dessous l'aperçu en direct des scores pour ce niveau.
 * Volontairement minimal : trop de texte et les gens le passent. Le reste se règle dans les Paramètres.
 */
@Composable
fun OnboardingDialog(
    displaySize: String,
    onDisplaySizeChanged: (String) -> Unit,
    surferLevel: String,
    onSurferLevelChanged: (String) -> Unit,
    onDone: () -> Unit
) {
    val colors = MaterialTheme.colorScheme

    Dialog(
        onDismissRequest = onDone,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.9f)
                .clip(RoundedCornerShape(16.dp)),
            color = colors.background
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("Bienvenue sur Surf Log 🏄", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
                    Text(
                        "L'appli de surf qui s'adapte à ton niveau !",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.primary
                    )

                    OnboardingSection(title = "Taille de l'affichage") {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("normal" to 18, "large" to 26, "xlarge" to 36).forEach { (key, letter) ->
                                val on = displaySize == key
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable { onDisplaySizeChanged(key) },
                                    color = if (on) colors.primary else colors.surfaceVariant
                                ) {
                                    Box(modifier = Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                        Text(
                                            "A", fontSize = letter.sp, fontWeight = FontWeight.Bold,
                                            color = if (on) colors.onPrimary else colors.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                        Text(
                            "Touche la taille qui te va : tout l'écran grandit. Tu pourras la changer avec le bouton « Aa » sur l'écran principal.",
                            fontSize = 12.sp,
                            color = colors.onSurfaceVariant
                        )
                    }

                    OnboardingSection(title = "Ton niveau") {
                        PillChoice(
                            options = listOf("beginner" to "Débutant", "intermediate" to "Interméd.", "confirmed" to "Confirmé", "expert" to "Expert"),
                            selected = surferLevel,
                            onSelect = onSurferLevelChanged
                        )
                    }

                    OnboardingSection(title = "Les scores pour toi") {
                        Text(
                            "Touche un niveau : les notes changent. Le même jour peut être parfait pour l'un et « trop gros » (orange) pour l'autre.",
                            fontSize = 12.sp,
                            color = colors.onSurfaceVariant
                        )
                        ScorePreviewList(surferLevel)
                    }

                    Text(
                        "Tu pourras affiner (taille des vagues, vent, planches…) dans ⚙ Réglages › Mon profil.",
                        fontSize = 12.sp,
                        color = colors.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                    Text("C'est parti", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun OnboardingSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

@Composable
private fun PillChoice(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val pillShape = RoundedCornerShape(50)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (key, label) ->
            val isSelected = selected == key
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(pillShape)
                    .clickable { onSelect(key) },
                color = if (isSelected) colors.primary else colors.surfaceVariant
            ) {
                Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) colors.onPrimary else colors.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
