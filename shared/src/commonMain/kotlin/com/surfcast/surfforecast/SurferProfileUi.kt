@file:Suppress("SpellCheckingInspection")
package com.surfcast.surfforecast

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt

// Mon style de surf : 4 profils de départ + un profil personnalisé (énergie, période, vent, rafales, clapot).

private val LEVELS = listOf("beginner", "intermediate", "confirmed", "advanced", "expert")
private val ONSHORE_CHOICES = listOf(10.0 to "Strict\n10\u00A0km/h", 12.0 to "Sensible\n12\u00A0km/h", 14.0 to "Normal\n14\u00A0km/h", 20.0 to "Tolérant\n20\u00A0km/h")
// (importance de la direction, facteur offshore soutenu) : Indifférent / Apprécié / Recherché.
private val OFFSHORE_CHOICES = listOf(
    Triple(0.25, 1.0, "Indifférent\nsans effet"),
    Triple(0.8, 0.85, "Apprécié\nmodéré"),
    Triple(1.0, 1.0, "Recherché\ntubes")
)
private val GUST_CHOICES = listOf(20.0 to "Sensible\n20\u00A0km/h", 25.0 to "Normal\n25\u00A0km/h", 30.0 to "Tolérant\n30\u00A0km/h")
private val CHOP_CHOICES = listOf(0.15 to "Strict\n0,15\u00A0m", 0.2 to "Sensible\n0,2\u00A0m", 0.3 to "Normal\n0,3\u00A0m", 0.6 to "Tolérant\n0,6\u00A0m")

/** Le vrai calcul de la note sur 5 vraies journées de Montalivet, pour le niveau donné (aussi utilisé par l'introduction). */
@Composable
internal fun ScorePreviewList(surferLevel: String) {
    val profile = remember(surferLevel) { SurfProfile.fromLevel(surferLevel) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        INTRO_DAYS.forEach { ProfileConditionRow(it, profile) }
    }
}

/** Pourquoi renseigner son profil : une phrase, et « Comment ça marche ? » pour le détail. */
@Composable
fun ProfileBenefitsCard() {
    val colors = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    SettingsSection("Pourquoi un profil") {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Tes notes, à ta mesure", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
            Text(
                "Dis-nous quelle vague tu cherches : les notes et le « trop gros » deviennent les tiens. Ça prend une minute.",
                fontSize = 12.5.sp, lineHeight = 17.sp, color = colors.onSurfaceVariant
            )
            Text(
                if (open) "Masquer" else "Comment ça marche ?", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.primary,
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable { open = !open }.padding(vertical = 4.dp)
            )
            if (open) {
                Text(
                    "Deux surfeurs ne cherchent pas la même vague. Une journée à 1,4 m et 10 s peut être parfaite pour l'un et trop grosse pour l'autre.",
                    fontSize = 12.5.sp, lineHeight = 17.sp, color = colors.onSurfaceVariant
                )
            }
        }
        if (open) {
            SettingsRow("Des notes faites pour toi", "La taille qui te convient, et « trop gros » (orange) quand ça dépasse ton niveau, sans que ce soit « mauvais ».", SettingsIcons.Tune)
            SettingsRow("Ta tolérance", "Vent, rafales, clapot et houles courtes : les notes et les étoiles en tiennent compte.", SettingsIcons.Wind)
            SettingsRow("Ton matériel", "Tes planches avec leur volume et leur ratio litres par kilo.", SettingsIcons.Board)
            SettingsRow("Le journal de bord", "Tu choisis ta planche en un geste, et l'appli retient avec quoi tu surfes le mieux.", SettingsIcons.Book)
        }
    }
}

/** Message qui remplace le « Meilleur créneau » tant que le profil n'est pas renseigné : toute la carte est cliquable. */
@Composable
fun ProfileNudgeCard(onOpen: () -> Unit, onLater: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .border(BorderStroke(1.dp, colors.outlineVariant), RoundedCornerShape(12.dp))
            .clickable(onClick = onOpen)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingsIcon(SettingsIcons.User, size = 20.dp)
            Text("Des prévisions calculées pour toi", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
        }
        Text(
            "Renseigne ton profil (niveau, vent, poids, planches) : sans lui, la note est celle d'un surfeur moyen. Une minute suffit.",
            fontSize = 12.sp, lineHeight = 16.sp, color = colors.onSurfaceVariant
        )
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Renseigner mon profil ›", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.primary, modifier = Modifier.weight(1f))
            Text(
                "Plus tard", fontSize = 12.sp, color = colors.onSurfaceVariant,
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onLater).padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

/**
 * Page « Mon profil » : le questionnaire de niveau (« Trouver mon niveau ») et son résultat. Rien d'autre sur le niveau.
 */
@Composable
fun ProfilePage(
    surferLevel: String,
    onInfo: () -> Unit,
    levelQuiz: LevelQuizHost? = null,
    // Ouvre directement le questionnaire (depuis « Renseigner mon profil »).
    startQuiz: Boolean = false,
    // Vrai quand le questionnaire occupe la page : l'écran parent masque alors le reste (Mon matériel).
    onFocusMode: (Boolean) -> Unit = {}
) {
    var mode by remember { mutableStateOf(if (startQuiz && levelQuiz != null) "quiz" else "overview") }
    LaunchedEffect(mode) { onFocusMode(mode == "quiz") }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (mode == "quiz" && levelQuiz != null) {
            LevelQuestionnaire(
                initial = levelQuiz.answers, initialShift = levelQuiz.shift, initialTolerance = levelQuiz.tolerance,
                onDone = { a, shift, tol -> levelQuiz.onSave(a, shift, tol); mode = "overview" },
                onClose = { mode = "overview" }
            )
        } else {
            ProfileBenefitsCard()
            val result = levelQuiz?.result?.takeIf { it.levelKey == surferLevel }
            if (result != null) {
                LevelCard(result)
                LevelLadder(result)
            }
            if (levelQuiz != null) SettingsButton(if (levelQuiz.result == null) "Trouver mon niveau" else "Refaire le questionnaire") { mode = "quiz" }
        }
    }
}
