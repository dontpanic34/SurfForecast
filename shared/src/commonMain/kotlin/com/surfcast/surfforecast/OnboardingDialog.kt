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
 * fait choisir l'unité du vent et le niveau, explique en une phrase ce que chacun change, et
 * résume d'où viennent les prévisions selon l'éloignement (aujourd'hui/demain, J+2 et au-delà).
 * Volontairement court : les gens le ferment vite, tout est modifiable ensuite dans les Paramètres.
 */
@Composable
fun OnboardingDialog(
    windUnit: String,
    onWindUnitSelected: (String) -> Unit,
    surferLevel: String,
    onSurferLevelChanged: (String) -> Unit,
    engineConfig: ForecastEngineConfig,
    onDone: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val shortWind = if (engineConfig.shortTermWeather == WeatherModel.AROME) {
        "AROME HD (maille ~1,3 km)"
    } else {
        engineConfig.shortTermWeather.displayName
    }

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
                        "Deux réglages et trois lignes à lire, puis c'est parti. Tout se change plus tard dans ⚙ Paramètres.",
                        fontSize = 12.sp,
                        color = colors.onSurfaceVariant
                    )

                    OnboardingSection(title = "1. Unité du vent") {
                        PillChoice(
                            options = listOf("kmh" to "km/h", "knots" to "Nœuds", "bft" to "Beaufort"),
                            selected = windUnit,
                            onSelect = onWindUnitSelected
                        )
                        Text(
                            "Ne change que l'affichage du vent (cartes, bandeau, widget) : les prévisions et le score restent " +
                                "identiques. Repère : 15 km/h ≈ 8 nœuds ≈ 3 Beaufort (brise légère).",
                            fontSize = 11.5.sp,
                            color = colors.onSurfaceVariant
                        )
                    }

                    OnboardingSection(title = "2. Ton niveau") {
                        PillChoice(
                            options = listOf("beginner" to "Débutant", "intermediate" to "Intermédiaire", "confirmed" to "Confirmé"),
                            selected = surferLevel,
                            onSelect = onSurferLevelChanged
                        )
                        Text(
                            "Il règle la note du « Meilleur créneau », pas les prévisions. Débutant : vagues petites et douces " +
                                "(≈ 1 m max). Confirmé : plus de puissance, et vent de terre (offshore) exigé. Intermédiaire : entre les deux.",
                            fontSize = 11.5.sp,
                            color = colors.onSurfaceVariant
                        )
                    }

                    OnboardingSection(title = "3. D'où viennent les prévisions") {
                        Bullet(
                            lead = "Aujourd'hui et demain",
                            text = "les modèles les plus fins : vent $shortWind, vagues ${engineConfig.shortTermWave.displayName}. " +
                                "Fiable à quelques heures près."
                        )
                        Bullet(
                            lead = "À partir de J+2",
                            text = "modèles plus larges (${engineConfig.longTermWeather.displayName}, ${engineConfig.longTermWave.displayName}) : " +
                                "bons pour la tendance, moins pour l'heure exacte."
                        )
                        Bullet(
                            lead = "Plus c'est loin, plus c'est incertain.",
                            text = "Ce sont des modèles, pas la réalité : regarde la mer et la webcam avant de partir."
                        )
                    }
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

@Composable
private fun Bullet(lead: String, text: String) {
    val colors = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("•", fontSize = 12.sp, color = colors.onBackground)
        Text(
            text = androidx.compose.ui.text.buildAnnotatedString {
                pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold, color = colors.onBackground))
                append(lead)
                pop()
                append(" ")
                append(text)
            },
            fontSize = 11.5.sp,
            color = colors.onSurfaceVariant
        )
    }
}
