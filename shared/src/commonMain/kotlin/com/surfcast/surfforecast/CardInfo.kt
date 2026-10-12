package com.surfcast.surfforecast

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Les réglages qu'on peut changer depuis le « i » d'un encart (le contrôleur les branche). */
class CardSettingsControl(
    val windUnit: String,
    val onWindUnit: (String) -> Unit,
    val weeklyWindMode: String,
    val onWeeklyWindMode: (String) -> Unit,
    val weeklyDensity: Int,
    val onWeeklyDensity: (Int) -> Unit
)

val LocalCardSettings = compositionLocalOf<CardSettingsControl?> { null }

/** Une visite guidée de l'encart : un titre et des étapes courtes, lues dans l'ordre de ce qu'on voit à l'écran. */
class CardGuide(val title: String, val steps: List<String>)

/** Les visites guidées, par clé d'encart : « week », « day », « swell », « wind », « weather », « windsea », « hourly ». */
val CARD_GUIDES: Map<String, CardGuide> = mapOf(
    "week" to CardGuide("La semaine", listOf(
        "En haut, la phrase du jour que tu as touché : le titre, la houle, le vent.",
        "★ La note du jour, la même pour tout le monde. Dessous, trois traits : matin, midi, après-midi, dans la couleur de ton niveau.",
        "La flèche et le mot sous chaque jour : d'où vient le vent. Plus la flèche est grande, plus il souffle.",
        "Le graphique : la houle du jour (taille, période), le ciel, l'énergie en kJ en bas.",
        "Touche un jour pour le détailler."
    )),
    "day" to CardGuide("Le déroulé de la journée", listOf(
        "En haut : l'heure choisie, sa note et la raison, par exemple « Un peu d'onshore ».",
        "Motivation : Flemme, Normal ou Motivé. Elle change seulement les couleurs, jamais les étoiles.",
        "Les flèches du vent, heure par heure. Grande flèche = vent fort ; la couleur dit s'il gêne (jaune, orange, rouge).",
        "Le graphique : la houle (1,5 m, 10 s) sur le ciel et la mer. Glisse ton doigt pour changer d'heure.",
        "La petite courbe bleue : la marée. Ça monte ou ça descend ; les triangles sont les pleines et basses mers.",
        "La bande jaune : les heures de jour. Plus elle est claire, plus le soleil est haut."
    )),
    "swell" to CardGuide("La houle", listOf(
        "À gauche, la taille et la période de la houle principale, plus grosses que la secondaire en dessous.",
        "À droite : d'où elle vient, sa flèche et son énergie en kJ.",
        "La flèche est grande si la houle arrive bien face à ta plage, petite si elle arrive de biais.",
        "Les barres : la houle sur la journée. Touche-en une pour changer d'heure."
    )),
    "wind" to CardGuide("Le vent", listOf(
        "Le chiffre : le vent ressenti, c'est le vent moyen plus 35 % de l'écart avec les rafales (8 km/h de moyenne et rafales à 22 donnent 13).",
        "La flèche : d'où il vient. Sa taille dit sa force, sa couleur dit s'il gêne : jaune propre, orange gênant, rouge fort.",
        "La zone grise derrière les barres : les rafales.",
        "Change l'unité en bas : kilomètre par heure, nœuds ou Beaufort. Ça change partout dans l'appli."
    )),
    "weather" to CardGuide("La météo", listOf(
        "L'icône du ciel et la température ressentie de l'heure ; la pluie s'ajoute à côté quand il pleut.",
        "La température de l'eau est juste à côté.",
        "À droite, l'indice UV et les nuages ; dessous, lever et coucher du soleil.",
        "« Détail sur la semaine » ouvre la météo des jours à venir."
    )),
    "windsea" to CardGuide("La mer de vent", listOf(
        "C'est le clapot : les petites vagues que le vent fabrique sur place, par-dessus la houle.",
        "La flèche donne la direction du clapot ; elle est grande s'il arrive bien face à la plage.",
        "Beaucoup de clapot, la vague est moins propre."
    )),
    "hourly" to CardGuide("Heure par heure", listOf(
        "Une ligne par heure : houle, vent, météo.",
        "Touche une ligne pour la choisir : tous les encarts suivent."
    ))
)

/** Le petit « i » d'un encart : il ouvre sa visite guidée et les réglages qui le concernent. */
@Composable
fun CardInfoButton(guideKey: String) {
    val guide = CARD_GUIDES[guideKey] ?: return
    var open by remember { mutableStateOf(false) }
    Text(
        "ⓘ", fontSize = 16.sp, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { open = true }.padding(horizontal = 8.dp, vertical = 2.dp)
    )
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(guide.title, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    guide.steps.forEachIndexed { i, s ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("${i + 1}.", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                            Text(s, fontSize = 13.sp)
                        }
                    }
                    CardGuideSettings(guideKey)
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Compris") } }
        )
    }
}

/** Les réglages liés à l'encart, sous sa visite guidée. */
@Composable
private fun CardGuideSettings(key: String) {
    val colors = MaterialTheme.colorScheme
    @Composable
    fun Chips(choices: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            choices.forEach { (k, label) ->
                val on = k == selected
                Box(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (on) colors.primary else colors.surfaceVariant)
                        .clickable { onSelect(k) }.padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) { Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (on) colors.onPrimary else colors.onSurfaceVariant, maxLines = 1) }
            }
        }
    }
    val c = LocalCardSettings.current
    when (key) {
        "wind" -> if (c != null) {
            Text("Unité du vent", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.primary)
            Chips(listOf("kmh" to "km/h", "knots" to "nœuds", "bft" to "Beaufort"), c.windUnit, c.onWindUnit)
        }
        "week" -> if (c != null) {
            Text("Indicateurs du vent", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.primary)
            Chips(listOf("arrow" to "Flèche", "text" to "Texte", "both" to "Les deux", "none" to "Aucun"), c.weeklyWindMode, c.onWeeklyWindMode)
            Text("Créneaux par jour", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.primary)
            Chips(listOf("1" to "1", "2" to "2", "3" to "3"), c.weeklyDensity.toString()) { c.onWeeklyDensity(it.toInt()) }
        }
        "day" -> {
            Text("Marées affichées", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.primary)
            Chips(listOf("sun" to "Journée de soleil", "all" to "Toutes"), if (SkyGraphSettings.tideAll) "all" else "sun") { SkyGraphSettings.chooseTideAll(it == "all") }
            Text("Hauteur de la courbe de marée", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.primary)
            Slider(value = SkyGraphSettings.tideDp.toFloat(), onValueChange = { SkyGraphSettings.setTide(it.toInt()) }, valueRange = 24f..60f)
        }
        "swell" -> {
            Text("Hauteur de l'échelle des graphiques", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.primary)
            Slider(value = SkyGraphSettings.waterPct.toFloat(), onValueChange = { SkyGraphSettings.setWater(it.toInt()) }, valueRange = 20f..60f)
        }
    }
}

/** Les trois unités du vent, en petites pastilles dans l'en-tête de l'encart Vent : on touche, ça change partout. */
@Composable
fun WindUnitChips() {
    val c = LocalCardSettings.current ?: return
    val colors = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        listOf("kmh" to "km/h", "knots" to "nœuds", "bft" to "Beaufort").forEach { (k, label) ->
            val on = k == c.windUnit
            Text(
                label, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = if (on) colors.onPrimary else colors.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) colors.primary else colors.onSurface.copy(alpha = 0.08f))
                    .clickable { c.onWindUnit(k) }.padding(horizontal = 7.dp, vertical = 3.dp)
            )
        }
    }
}
