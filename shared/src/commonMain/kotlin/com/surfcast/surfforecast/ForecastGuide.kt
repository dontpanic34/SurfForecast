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

private data class GuideSection(val icon: String, val title: String, val text: String)

private val guideSections = listOf(
    GuideSection(
        "🌊", "Houle ou mer de vent ?",
        "• La houle : née loin, au large. Elle arrive en lignes régulières et propres.\n" +
            "• La mer de vent (clapot) : créée par le vent local. Courte et désordonnée, elle hache la surface.\n" +
            "En résumé : beaucoup de houle et peu de clapot = bonne session. L'appli affiche les deux, et le score baisse quand le clapot devient gros par rapport à la houle."
    ),
    GuideSection(
        "⏱️", "La période (en secondes)",
        "C'est le temps entre deux vagues. Elle compte autant que la hauteur : elle donne la puissance réelle.\n" +
            "• Moins de 8 s : vagues molles et faibles.\n" +
            "• 10 à 14 s : vagues puissantes et bien formées.\n" +
            "• Plus de 14 s : très puissantes, attention si tu débutes.\n" +
            "Exemple : une houle de 1 m à 12 s est bien plus costaude qu'une houle de 1,5 m à 7 s."
    ),
    GuideSection(
        "⚡", "L'énergie (en kJ)",
        "Elle combine hauteur et période en un seul chiffre : la force réelle de la houle.\n" +
            "• 0,8 m à 9 s ≈ 100 kJ : accessible à tous.\n" +
            "• 1,2 m à 11 s ≈ 285 kJ : pour surfeurs à l'aise.\n" +
            "• 2 m à 12 s ≈ 1100 kJ : massif et très puissant.\n" +
            "Chaque profil a son plafond : au-delà, la note passe à « trop gros » (violet). Tu règles tes limites dans Paramètres › Mon profil."
    ),
    GuideSection(
        "🧭", "La direction",
        "On donne toujours la direction d'où vient la houle (ou le vent).\n" +
            "• Plein face à la plage : la houle rentre bien.\n" +
            "• De travers : une partie de la force est perdue.\n" +
            "• À 90° de la plage (parallèle à la côte) : elle ne rentre plus.\n" +
            "Chaque spot a une orientation que tu peux corriger dans Paramètres › Prévisions."
    ),
    GuideSection(
        "💨", "Le vent",
        "• Offshore (vent de terre) : il souffle contre la vague, la lisse et la tient debout. C'est le meilleur.\n" +
            "• Onshore (vent de mer) : il écrase et hache les vagues.\n" +
            "• Travers : entre les deux.\n" +
            "Moins de 8 km/h est léger, 8 à 18 modéré, plus de 18 fort. Les rafales comptent : un vent de 15 avec des rafales à 35 gâche la surface. " +
            "Le « forcit / vire » du widget annonce un changement de vent à venir."
    ),
    GuideSection(
        "🌗", "La marée et le coefficient",
        "• La marée : selon les bancs de sable, un spot marche mieux à marée basse, mi-marée ou haute (la fiche du banc la note dans le journal).\n" +
            "• Le coefficient (de 20 à 120) mesure l'amplitude. Au-dessus de 90 (vives-eaux) : la mer monte et descend vite, avec du courant. Sous 45 (mortes-eaux) : peu de mouvement d'eau.\n" +
            "Le coefficient est le même pour toute la côte atlantique. Hors de France, les heures de marée sont estimées."
    ),
    GuideSection(
        "🎯", "Le score et les étoiles",
        "Chaque heure reçoit une note de 0 à 100 selon ton niveau : énergie de la houle, direction, vent (rafales comprises) et clapot.\n" +
            "• Gris : à éviter. Rouge : médiocre. Orange : correct.\n" +
            "• Jaune : bon. Vert : très bon. Turquoise : excellent.\n" +
            "• Violet : trop gros pour ton niveau (plafond : débutant 250 kJ, intermédiaire 450, confirmé 700, expert aucun). Ce n'est pas mauvais, c'est juste au-dessus de ce que ton niveau gère.\n" +
            "Ton profil règle aussi l'importance de l'offshore (il creuse la vague : surtout pour confirmés et experts) et la tolérance au vent, aux rafales, au clapot et à la période : un expert est plus exigeant qu'un débutant. Le profil Personnalisé te laisse tout ajuster.\n" +
            "Les étoiles du jour (0,5 à 5) mélangent le meilleur moment et la durée des bonnes conditions : un jour propre toute la journée vaut plus qu'une seule bonne heure.\n" +
            "La note ne connaît pas ton spot (bancs de sable, courants) : regarde la webcam avant de partir."
    ),
    GuideSection(
        "📅", "Quelle confiance accorder à chaque jour ?",
        "• Aujourd'hui et demain : fiable (modèle Météo-France AROME pour le vent, maille 1,3 km).\n" +
            "• Après-demain et J+3 : bon indicateur.\n" +
            "• À partir de J+4 : une tendance, pas une promesse. Elle se précise en se rapprochant : compare toujours la veille."
    ),
    GuideSection(
        "📡", "D'où viennent les données",
        "• Houle et vent : Open-Meteo.com (CC BY 4.0), à partir des modèles Météo-France (AROME, ARPEGE, MFWAM) et ECMWF.\n" +
            "• Marées et coefficients en France : api-maree.fr, à partir des composantes harmoniques Ifremer / PREVIMER (CC BY 4.0). Ce sont des prévisions calculées, à vérifier avant de partir. Hors de France : estimation Open-Meteo.\n" +
            "• Température de la mer : Open-Meteo Marine."
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
                    Text(section.icon, fontSize = 18.sp, modifier = Modifier.padding(end = 10.dp))
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
