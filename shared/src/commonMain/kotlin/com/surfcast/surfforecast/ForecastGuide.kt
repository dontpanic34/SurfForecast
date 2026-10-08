package com.surfcast.surfforecast

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

private data class GuideSection(val title: String, val text: String)

private val guideSections = listOf(
    GuideSection(
        "🌊 Houle ou mer de vent ?",
        "La houle est née loin, au large : elle arrive en lignes régulières et propres. La mer de vent (le clapot) est créée " +
            "par le vent local : courte, désordonnée, elle hache la surface. Une bonne session = de la houle, peu de clapot. " +
            "L'appli affiche les deux, et le score baisse quand le clapot devient gros par rapport à la houle."
    ),
    GuideSection(
        "⏱ La période (en secondes)",
        "C'est le temps entre deux vagues, et elle compte autant que la hauteur. Sous 8 s : vagues molles et faibles. " +
            "10 à 14 s : vagues puissantes et bien formées. Plus de 14 s : très puissantes, attention sur un petit gabarit. " +
            "Une houle de 1 m à 12 s est bien plus costaude qu'une houle de 1,5 m à 7 s."
    ),
    GuideSection(
        "⚡ L'énergie (en kJ)",
        "Elle résume hauteur et période en un chiffre : la force réelle de la houle. Repères : 0,8 m à 9 s ≈ 100 kJ (ça ouvre, pour " +
            "tout le monde) ; 1,2 m à 11 s ≈ 285 ; 2 m à 12 s ≈ 1100 ; 2,5 m à 14 s ≈ 2400. Chaque profil a son plafond : " +
            "au-delà, la note est « trop gros » (violet). Dans Paramètres › Mon profil, tu peux régler ton énergie minimum et maximum."
    ),
    GuideSection(
        "🧭 La direction",
        "On donne toujours la direction d'où vient la houle (ou le vent). Une houle qui arrive de face sur la plage " +
            "entre bien. De travers, elle est partiellement coupée ; à 90° de la plage, elle ne rentre plus. " +
            "C'est pour ça que chaque spot a une orientation (Paramètres > Prévisions) : tu peux la corriger."
    ),
    GuideSection(
        "💨 Le vent",
        "Offshore = le vent vient de la terre et souffle contre la vague : il la lisse et la tient debout, c'est le meilleur. " +
            "Onshore = il vient de la mer : il écrase et hache les vagues. Travers = entre les deux. " +
            "Moins de 8 km/h est léger, 8 à 18 modéré, plus de 18 fort. Les rafales comptent : un vent de 15 avec des rafales " +
            "à 35 gâche la surface. Le « forcit / vire » du widget annonce un changement de vent à venir."
    ),
    GuideSection(
        "🌗 La marée et le coefficient",
        "Chaque spot marche mieux à certains moments de la marée (haute, basse, mi-marée) : c'est ce que note la fiche du banc " +
            "dans le journal. Le coefficient (de 20 à 120) mesure l'amplitude : au-dessus de 90 (vives-eaux), la mer monte et descend " +
            "beaucoup, avec du courant ; sous 45 (mortes-eaux), elle bouge peu. Le coefficient est le même pour toute la côte atlantique. " +
            "Hors de France, les heures de marée sont estimées."
    ),
    GuideSection(
        "🎯 Le score et le meilleur créneau",
        "Chaque heure reçoit une note de 0 à 100 selon ton niveau : énergie de la houle, direction, vent (avec rafales) et clapot. " +
            "Les couleurs : gris = à éviter, rouge = médiocre, orange = correct, jaune = bon, vert = très bon, vert fluo = excellent. " +
            "Violet = trop gros pour ton niveau : ce n'est pas mauvais, c'est simplement au-dessus de ce que ton niveau gère " +
            "(plafond d'énergie : débutant 250 kJ, intermédiaire 450, confirmé 700, expert aucun). Le profil règle aussi l'importance de l'offshore (il creuse la vague et fait les tubes : réservé surtout aux confirmés et experts) et la tolérance au vent, aux rafales, au clapot et à la période (plus on cherche la qualité de la vague, plus ces défauts pèsent : un expert est plus exigeant qu'un débutant), et le profil Personnalisé te laisse tout ajuster. " +
            "Le meilleur créneau est la fenêtre de 2 à 3 heures avec la meilleure moyenne. " +
            "La note ne connaît pas ton spot (bancs de sable, courants) : c'est à toi de juger s'il marche. Regarde la webcam avant de partir."
    ),
    GuideSection(
        "📅 Quelle confiance accorder à chaque jour ?",
        "Aujourd'hui et demain : fiable (modèle Météo-France AROME pour le vent, maille 1,3 km). Après-demain et J+3 : bon. " +
            "À partir de J+4 : une tendance, pas une promesse, qui se précise en se rapprochant. Compare toujours le jour J-1."
    ),
    GuideSection(
        "📡 D'où viennent les données",
        "Prévisions de houle et de vent : Open-Meteo.com (licence CC BY 4.0), à partir des modèles Météo-France (AROME, ARPEGE, MFWAM) " +
            "et ECMWF. Marées et coefficients en France : api-maree.fr, à partir des composantes harmoniques Ifremer / PREVIMER (licence CC BY 4.0) ; ce sont des prévisions calculées, à vérifier avant de partir. Hors de France : estimation Open-Meteo. Température de la mer : Open-Meteo Marine."
    )
)

/** Page « Comprendre les prévisions » (Paramètres > Aide). */
@Composable
fun ForecastGuideContent() {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Touche un titre pour lire l'explication.", fontSize = 12.5.sp, color = colors.onSurfaceVariant)
        guideSections.forEach { section ->
            var open by remember { mutableStateOf(false) }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surfaceVariant)
                    .clickable { open = !open }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(section.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.onBackground, modifier = Modifier.weight(1f))
                    Text(if (open) "⌃" else "⌄", fontSize = 18.sp, color = colors.primary)
                }
                if (open) {
                    Text(section.text, fontSize = 12.5.sp, lineHeight = 17.sp, color = colors.onSurfaceVariant)
                }
            }
        }
    }
}
