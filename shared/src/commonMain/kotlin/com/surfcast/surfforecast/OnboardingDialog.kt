package com.surfcast.surfforecast

import androidx.compose.foundation.background
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Écran de bienvenue (première ouverture, puis Paramètres > « Revoir l'introduction ») : trois cartes courtes, une idée
 * chacune, et un geste à faire pour passer à la suivante (toucher un niveau, toucher un jour). Le geste retient
 * l'attention mieux qu'un bouton « Suivant » qu'on clique sans lire.
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
    var card by remember { mutableStateOf(0) }
    var levelTouched by remember { mutableStateOf(false) }
    var dayTouched by remember { mutableStateOf<Int?>(null) }

    Dialog(onDismissRequest = onDone, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.9f).clip(RoundedCornerShape(16.dp)),
            color = colors.background
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("${card + 1} sur 3", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.primary)
                    when (card) {
                        0 -> {
                            Text("Choisis ton niveau", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = colors.onBackground)
                            PillChoice(
                                options = listOf("beginner" to "Débutant", "intermediate" to "Interméd.", "confirmed" to "Confirmé", "advanced" to "Avancé", "expert" to "Expert"),
                                selected = if (levelTouched) surferLevel else "",
                                onSelect = { levelTouched = true; onSurferLevelChanged(it) }
                            )
                            Text("Le même jour, la note change avec ton niveau :", fontSize = 13.sp, color = colors.onSurfaceVariant)
                            ProfileDaysPreview(SurfProfile.fromLevel(surferLevel), listOf(INTRO_DAYS[2], INTRO_DAYS[3]))
                        }
                        1 -> {
                            Text("Touche un jour", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = colors.onBackground)
                            listOf("Aujourd'hui" to "★ 4", "Demain" to "★ 3,5", "Après-demain" to "★ 5").forEachIndexed { i, (day, stars) ->
                                val on = dayTouched == i
                                Row(
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                        .background(if (on) colors.primary.copy(alpha = 0.2f) else colors.surfaceVariant)
                                        .clickable { dayTouched = i }.padding(horizontal = 14.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(day, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
                                    Text(stars, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFFFC107))
                                }
                            }
                            if (dayTouched != null) {
                                Text("Les étoiles sont les mêmes pour tout le monde.", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
                                Text("Les couleurs, elles, suivent ton niveau.", fontSize = 14.sp, color = colors.onBackground)
                            }
                        }
                        else -> {
                            Text("Note tes sessions", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = colors.onBackground)
                            Text("Dix secondes après ta session : l'appli garde la houle, le vent et la marée.", fontSize = 15.sp, color = colors.onBackground)
                            Text("Plus tard, elle te dit quand un jour ressemble à ton meilleur.", fontSize = 15.sp, color = colors.onBackground)
                            Text("La taille de l'affichage se règle dans Réglages.", fontSize = 12.sp, color = colors.onSurfaceVariant)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                val ready = when (card) { 0 -> levelTouched; 1 -> dayTouched != null; else -> true }
                Button(
                    onClick = { if (card < 2) card++ else onDone() },
                    enabled = ready,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        when {
                            !ready && card == 0 -> "Touche ton niveau"
                            !ready -> "Touche un jour"
                            card < 2 -> "Suivant"
                            else -> "C'est parti"
                        },
                        fontWeight = FontWeight.Bold
                    )
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
