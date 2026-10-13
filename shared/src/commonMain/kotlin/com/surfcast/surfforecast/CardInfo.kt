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

private val CARD_TITLES = mapOf(
    "week" to "de la semaine", "day" to "du déroulé de la journée", "swell" to "de la houle", "wind" to "du vent",
    "weather" to "de la météo", "windsea" to "du vent et de la mer", "hourly" to "de l'heure par heure"
)

/** Le panneau de réglages de l'encart, à placer sous son titre. N'affiche rien si l'engrenage n'est pas ouvert. */
@Composable
fun CardSettingsPanel(key: String) {
    if (CardGear.open != key) return
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Réglages " + (CARD_TITLES[key] ?: "de l'encart"), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
            Box(modifier = Modifier.size(36.dp).clickable { CardGear.open = null }, contentAlignment = Alignment.Center) {
                SettingsIcon(SettingsIcons.Close, colors.onSurfaceVariant, 20.dp)
            }
        }
        CardGuideSettings(key)
        CARD_HELP[key]?.let { items ->
            SettingsSection("Comprendre") {
                items.forEachIndexed { i, h ->
                    var more by remember { mutableStateOf(false) }
                    if (i > 0) SettingsDivider()
                    Column(modifier = Modifier.fillMaxWidth().clickable { more = !more }.padding(horizontal = 14.dp, vertical = 11.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(h.title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.onSurface, modifier = Modifier.weight(1f))
                            Text(if (more) "–" else "+", fontSize = 16.sp, color = colors.primary)
                        }
                        if (more) Text(h.text, fontSize = 12.5.sp, lineHeight = 17.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
        SettingsHelpLink("Comprendre en détail") { ComprendreState.open(ComprendreState.lessonForCard(key)) }
    }
}

/** Les réglages liés à l'encart. */
@Composable
private fun CardGuideSettings(key: String) {
    val c = LocalCardSettings.current
    when (key) {
        "wind" -> if (c != null) {
            SettingsSection("Affichage") {
                SettingsField("Unité du vent", top = false) {
                    SettingsSegments(listOf("kmh" to "km/h", "knots" to "nœuds", "bft" to "Beaufort"), c.windUnit, c.onWindUnit)
                }
            }
        }
        "week" -> {
            SettingsSection("Affichage") {
                var first = true
                if (c != null) {
                    SettingsField("Indicateurs du vent", top = false) {
                        SettingsSegments(listOf("arrow" to "Flèche", "text" to "Texte", "both" to "Les deux", "none" to "Aucun"), c.weeklyWindMode, c.onWeeklyWindMode)
                    }
                    SettingsField("Créneaux par jour") {
                        SettingsSegments(listOf("1" to "1", "2" to "2", "3" to "3"), c.weeklyDensity.toString()) { c.onWeeklyDensity(it.toInt()) }
                    }
                    first = false
                }
                SettingsSliderField("Hauteur de l'encart", "${SkyGraphSettings.weekPct} %", SkyGraphSettings.weekPct.toFloat(), 25f..70f, top = !first) { SkyGraphSettings.setWeek(it.toInt()) }
                SettingsSliderField("Échelle de la mer", "${SkyGraphSettings.waterWeekPct} %", SkyGraphSettings.waterWeekPct.toFloat(), 20f..60f) { SkyGraphSettings.setWaterWeek(it.toInt()) }
                SettingsRow("Valeurs par défaut", icon = SettingsIcons.Tune, chevron = false) { SkyGraphSettings.resetWeek() }
            }
        }
        "day" -> {
            SettingsSection("Affichage") {
                SettingsSliderField("Hauteur de l'encart", "${SkyGraphSettings.dayPct} %", SkyGraphSettings.dayPct.toFloat(), 25f..70f, top = false) { SkyGraphSettings.setDay(it.toInt()) }
                SettingsSliderField("Échelle de la mer", "${SkyGraphSettings.waterPct} %", SkyGraphSettings.waterPct.toFloat(), 20f..60f) { SkyGraphSettings.setWater(it.toInt()) }
                SettingsSliderField("Hauteur de la courbe de marée", "${SkyGraphSettings.tideDp}", SkyGraphSettings.tideDp.toFloat(), 24f..60f) { SkyGraphSettings.setTide(it.toInt()) }
                SettingsField("Marées affichées") {
                    SettingsSegments(listOf("sun" to "Journée de soleil", "all" to "Toutes"), if (SkyGraphSettings.tideAll) "all" else "sun") { SkyGraphSettings.chooseTideAll(it == "all") }
                }
                SettingsRow("Valeurs par défaut", icon = SettingsIcons.Tune, chevron = false) { SkyGraphSettings.resetDay() }
            }
        }
    }
}
