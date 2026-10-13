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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Les réglages qu'on peut changer depuis le ⚙ d'un encart (le contrôleur les branche). */
class CardSettingsControl(
    val windUnit: String,
    val onWindUnit: (String) -> Unit,
    val weeklyWindMode: String,
    val onWeeklyWindMode: (String) -> Unit,
    val weeklyDensity: Int,
    val onWeeklyDensity: (Int) -> Unit
)

val LocalCardSettings = compositionLocalOf<CardSettingsControl?> { null }

/** Une ligne « Comprendre » : un titre qu'on touche pour lire une ou deux phrases. */
class CardHelp(val title: String, val text: String)

/** Les explications de chaque encart, repliées par défaut dans le panneau de réglages. */
val CARD_HELP: Map<String, List<CardHelp>> = mapOf(
    "week" to listOf(
        CardHelp("Comment la note est calculée", "La note (1 à 5 étoiles) compare houle, période, direction et vent à ce que le spot aime. Elle est la même pour tout le monde : ni ton niveau ni ta motivation ne la changent."),
        CardHelp("Les trois traits", "Matin, midi, après-midi. Ils prennent la couleur de la condition à cette heure : vert propre, jaune moyen, rouge mauvais."),
        CardHelp("Le vent de la semaine", "La flèche donne d'où il vient et sa taille la force. C'est la moyenne de deux modèles (ECMWF et GFS)."),
        CardHelp("Les jours lointains", "À partir de J+4, les étoiles s'écrivent avec un « ~ » et sont plus pâles : c'est une tendance. Une houle lointaine peut arriver une demi-journée plus tôt ou plus tard que prévu.")
    ),
    "day" to listOf(
        CardHelp("Motivation : Flemme, Normal, Motivé", "Elle change seulement les couleurs et le libellé de l'heure (un peu plus exigeant ou plus indulgent). Elle ne change jamais les étoiles."),
        CardHelp("Comment le vent est calculé", "Moyenne des modèles ECMWF et GFS. Le chiffre affiché est le vent ressenti : le vent moyen plus 35 % de l'écart avec les rafales. La couleur dit s'il gêne."),
        CardHelp("Modèles de prévision", "Houle : consensus de trois modèles de vagues (MF-WAM, ECMWF, GFS), la hauteur et la période venant du même calcul. Vent : ECMWF + GFS. AROME HD (Météo-France) tourne en coulisses pour comparer."),
        CardHelp("Modèles partagés", "Quand les trois modèles de vagues donnent des hauteurs très différentes pour la journée, l'appli l'écrit. Dans ce cas, la prévision est moins sûre."),
        CardHelp("La courbe bleue et le coef", "La marée monte ou descend. Les triangles sont les pleines et basses mers, le coefficient dit la taille de la marée.")
    ),
    "swell" to listOf(
        CardHelp("Taille et période", "Hauteur de la houle principale en mètres et période en secondes. Plus la période est longue, plus la houle a de la puissance."),
        CardHelp("L'énergie en kJ", "Elle combine hauteur et période : deux houles de même hauteur n'ont pas la même force."),
        CardHelp("Houle secondaire", "Affichée dès 0,2 m et 7 s. Elle vient d'une autre direction et peut brouiller la houle principale.")
    ),
    "wind" to listOf(
        CardHelp("Comment le vent est calculé", "Moyenne ECMWF + GFS, direction en moyenne vectorielle. Le chiffre est le vent ressenti (rafales comprises)."),
        CardHelp("Les couleurs", "Jaune : propre. Orange : gênant. Rouge : fort ou onshore. Elles dépendent de la direction par rapport à la plage.")
    ),
    "weather" to listOf(
        CardHelp("D'où vient la météo", "Open-Meteo : ciel, température ressentie, pluie en mm, UV, nuages. Température de l'eau : modèle marin."),
        CardHelp("Température ressentie", "Elle tient compte du vent : plus fraîche qu'au thermomètre quand il souffle.")
    ),
    "windsea" to listOf(
        CardHelp("Le clapot", "Les petites vagues que le vent fabrique sur place, par-dessus la houle. Beaucoup de clapot, la vague est moins propre."),
        CardHelp("La flèche", "Direction du clapot ; grande s'il arrive bien face à la plage.")
    ),
    "hourly" to listOf(
        CardHelp("Heure par heure", "Une ligne par heure : houle, vent, météo. Touches-en une : tous les encarts suivent.")
    )
)

/** L'engrenage ouvert (un seul à la fois) : son panneau s'affiche sous le titre de l'encart. */
object CardGear {
    var open by mutableStateOf<String?>(null)
}

/** L'engrenage d'un encart : il ouvre ou ferme ses réglages, juste sous le titre, l'encart reste visible et bouge en direct. */
@Composable
fun CardInfoButton(guideKey: String) {
    val on = CardGear.open == guideKey
    val colors = MaterialTheme.colorScheme
    Text(
        "⚙", fontSize = 17.sp, color = if (on) colors.onPrimaryContainer else colors.onSurface.copy(alpha = 0.55f),
        modifier = Modifier.clip(CircleShape).background(if (on) colors.primaryContainer else Color.Transparent)
            .clickable { CardGear.open = if (on) null else guideKey }.padding(horizontal = 7.dp, vertical = 2.dp)
    )
}

/** Le panneau de réglages de l'encart, à placer sous son titre. N'affiche rien si l'engrenage n'est pas ouvert. */
@Composable
fun CardSettingsPanel(key: String) {
    if (CardGear.open != key) return
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CardGuideSettings(key)
        CARD_HELP[key]?.let { items ->
            Text("COMPRENDRE", fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp, color = colors.primary)
            items.forEach { h ->
                var more by remember { mutableStateOf(false) }
                Column(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(colors.surfaceVariant)
                        .clickable { more = !more }.padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(h.title, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                        Text(if (more) "–" else "+", fontSize = 14.sp, color = colors.primary)
                    }
                    if (more) Text(h.text, fontSize = 12.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        Text(
            "Comprendre en détail ›", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = colors.primary,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { ComprendreState.open(ComprendreState.lessonForCard(key)) }.padding(vertical = 4.dp, horizontal = 2.dp)
        )
        Text(
            "Fermer", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = colors.primary,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { CardGear.open = null }.padding(vertical = 4.dp, horizontal = 2.dp)
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
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (on) colors.primaryContainer else colors.surfaceVariant)
                        .clickable { onSelect(k) }.padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) { Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (on) colors.onPrimaryContainer else colors.onSurfaceVariant, maxLines = 1) }
            }
        }
    }
    val c = LocalCardSettings.current
    @Composable
    fun Label(t: String) = Text(t, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.primary)
    @Composable
    fun Pct(label: String, value: Int, range: IntRange, set: (Int) -> Unit) {
        Label(label)
        Slider(value = value.toFloat(), onValueChange = { set(it.toInt()) }, valueRange = range.first.toFloat()..range.last.toFloat())
    }
    when (key) {
        "wind" -> if (c != null) {
            Label("Unité du vent")
            Chips(listOf("kmh" to "km/h", "knots" to "nœuds", "bft" to "Beaufort"), c.windUnit, c.onWindUnit)
        }
        "week" -> {
            if (c != null) {
                Label("Indicateurs du vent")
                Chips(listOf("arrow" to "Flèche", "text" to "Texte", "both" to "Les deux", "none" to "Aucun"), c.weeklyWindMode, c.onWeeklyWindMode)
                Label("Créneaux par jour")
                Chips(listOf("1" to "1", "2" to "2", "3" to "3"), c.weeklyDensity.toString()) { c.onWeeklyDensity(it.toInt()) }
            }
            Pct("Hauteur de l'encart (graphique)", SkyGraphSettings.weekPct, 25..70) { SkyGraphSettings.setWeek(it) }
            Pct("Échelle de la mer", SkyGraphSettings.waterWeekPct, 20..60) { SkyGraphSettings.setWaterWeek(it) }
        }
        "day" -> {
            Pct("Hauteur de l'encart (graphique)", SkyGraphSettings.dayPct, 25..70) { SkyGraphSettings.setDay(it) }
            Pct("Échelle de la mer", SkyGraphSettings.waterPct, 20..60) { SkyGraphSettings.setWater(it) }
            Pct("Hauteur de la courbe de marée", SkyGraphSettings.tideDp, 24..60) { SkyGraphSettings.setTide(it) }
            Label("Marées affichées")
            Chips(listOf("sun" to "Journée de soleil", "all" to "Toutes"), if (SkyGraphSettings.tideAll) "all" else "sun") { SkyGraphSettings.chooseTideAll(it == "all") }
        }
    }
    if (key == "week" || key == "day") {
        Text(
            "Valeurs par défaut", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.primary,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { if (key == "week") SkyGraphSettings.resetWeek() else SkyGraphSettings.resetDay() }.padding(vertical = 2.dp)
        )
    }
}

