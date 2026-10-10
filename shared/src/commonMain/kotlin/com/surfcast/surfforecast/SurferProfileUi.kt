@file:Suppress("SpellCheckingInspection")
package com.surfcast.surfforecast

import androidx.compose.foundation.background
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

private val LEVELS = listOf("beginner", "intermediate", "confirmed", "expert")
private val ONSHORE_CHOICES = listOf(10.0 to "Strict\n10\u00A0km/h", 12.0 to "Sensible\n12\u00A0km/h", 14.0 to "Normal\n14\u00A0km/h", 20.0 to "Tolérant\n20\u00A0km/h")
// (importance de la direction, facteur offshore soutenu) : Indifférent / Apprécié / Recherché.
private val OFFSHORE_CHOICES = listOf(
    Triple(0.25, 1.0, "Indifférent\nsans effet"),
    Triple(0.8, 0.85, "Apprécié\nmodéré"),
    Triple(1.0, 1.0, "Recherché\ntubes")
)
private val GUST_CHOICES = listOf(20.0 to "Sensible\n20\u00A0km/h", 25.0 to "Normal\n25\u00A0km/h", 30.0 to "Tolérant\n30\u00A0km/h")
private val CHOP_CHOICES = listOf(0.15 to "Strict\n0,15\u00A0m", 0.2 to "Sensible\n0,2\u00A0m", 0.3 to "Normal\n0,3\u00A0m", 0.6 to "Tolérant\n0,6\u00A0m")

@Composable
private fun ChoiceRow(choices: List<Pair<Double, String>>, current: Double, onPick: (Double) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        choices.forEach { (value, label) ->
            val selected = abs(value - current) < 0.001
            Surface(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(50)).clickable { onPick(value) },
                color = if (selected) colors.primary else colors.background
            ) {
                Box(modifier = Modifier.padding(horizontal = 4.dp, vertical = 7.dp), contentAlignment = Alignment.Center) {
                    Text(
                        label, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 2,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = if (selected) colors.onPrimary else colors.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingBlock(title: String, valueText: String? = null, hint: String? = null, content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(colors.surfaceVariant).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground, modifier = Modifier.weight(1f))
            if (valueText != null) Text(valueText, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.primary)
        }
        if (hint != null) Text(hint, fontSize = 11.5.sp, color = colors.onSurfaceVariant)
        content()
    }
}

/**
 * Mon profil : 4 profils de départ (Débutant, Intermédiaire, Confirmé, Expert) + « Personnalisé ».
 * Un profil règle plus que l'énergie : vent, onshore, rafales, clapot et période suivent. Le niveau
 * choisi est stocké sous forme de texte (« beginner »… ou « custom:… »), voir [SurfProfile.fromLevel].
 */
@Composable
fun SurferProfileSection(
    surferLevel: String,
    onLevelChanged: (String) -> Unit,
    // Profil Personnalisé gardé à part (texte « custom:… », vide = aucun) et sa sauvegarde.
    savedCustom: String,
    onCustomSaved: (String) -> Unit,
    onInfo: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val custom = isCustomLevel(surferLevel)
    val profile = remember(surferLevel) { SurfProfile.fromLevel(surferLevel) }
    fun update(p: SurfProfile) {
        val serialized = p.serialize()
        onLevelChanged(serialized)
        onCustomSaved(serialized)
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(colors.surfaceVariant).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Mon profil", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.onBackground, modifier = Modifier.weight(1f))
                IconButton(onClick = onInfo, modifier = Modifier.size(18.dp)) {
                    Icon(SurfIcons.Info, contentDescription = "Comment le score est calculé", tint = colors.primary, modifier = Modifier.size(14.dp))
                }
            }
            val pills = LEVELS.map { it to levelLabel(it) } + ("custom" to "Personnalisé")
            pills.chunked(3).forEach { rowPills ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    rowPills.forEach { (key, label) ->
                        val selected = if (key == "custom") custom else surferLevel == key
                        Surface(
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(50)).clickable {
                                if (key == "custom") { if (!custom) update(if (savedCustom.isNotBlank()) SurfProfile.fromLevel(savedCustom) else profile) } else onLevelChanged(key)
                            },
                            color = if (selected) colors.primary else colors.background
                        ) {
                            Box(modifier = Modifier.padding(vertical = 7.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    label, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                                    color = if (selected) colors.onPrimary else colors.onSurfaceVariant
                                )
                            }
                        }
                    }
                    repeat(3 - rowPills.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
            }
            Text(
                text = when (surferLevel) {
                    "beginner" -> "Tu surfes plutôt les mousses."
                    "intermediate" -> "Tu commences à aller au large et à suivre les vagues."
                    "confirmed" -> "Tu es autonome dans l'eau et tu surfes seul."
                    "expert" -> "Tu utilises le plein potentiel de la vague et tu cherches la puissance."
                    else -> "Pars du profil choisi, puis ajuste à ta sauce."
                },
                fontSize = 12.sp, color = colors.onSurfaceVariant
            )
            if (!custom && savedCustom.isNotBlank()) {
                Text(
                    "Ton profil Personnalisé est gardé : touche « Personnalisé » pour le retrouver tel que tu l'as réglé.",
                    fontSize = 11.sp, color = colors.primary
                )
            }
            if (!custom) {
                Text(
                    "Ce profil règle : vagues " + waveRangeText(profile) +
                        " · vent ${profile.windTolerance.roundToInt()} km/h · onshore ${profile.onshoreMax.roundToInt()} km/h" +
                        " · rafales ${profile.gustThreshold.roundToInt()} km/h · clapot ${formatFr(profile.chopThreshold, 2)} m · période ${profile.minPeriod.roundToInt()} s min.",
                    fontSize = 11.sp, color = colors.onSurfaceVariant.copy(alpha = 0.85f)
                )
            }
        }

        if (custom) {
            SettingBlock("Vagues", hint = "Ta plus petite vague intéressante et ta plus grosse vague, en hauteur et en période.") {
                WaveRangeEditor(profile, "custom") { update(it) }
            }
            SettingBlock("Période minimum", "${profile.minPeriod.roundToInt()} s", "Houle courte et clapoteuse ↔ houle longue.") {
                Slider(
                    value = profile.minPeriod.toFloat(),
                    onValueChange = { update(profile.copy(minPeriod = it.roundToInt().toDouble())) },
                    valueRange = 4f..14f, steps = 9
                )
            }
            SettingBlock("Vent", "${profile.windTolerance.roundToInt()} km/h", "Force de vent bien tolérée (offshore et side-shore). Plus on cherche la qualité de la vague, plus le vent, le clapot et l'onshore pèsent.") {
                Slider(
                    value = profile.windTolerance.toFloat(),
                    onValueChange = { update(profile.copy(windTolerance = (it / 5f).roundToInt() * 5.0)) },
                    valueRange = 10f..50f, steps = 7
                )
                Text("Vent de mer (onshore) toléré jusqu'à", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground)
                ChoiceRow(ONSHORE_CHOICES, profile.onshoreMax) { update(profile.copy(onshoreMax = it)) }
                Text("Vent de terre (offshore)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground)
                Text(
                    "L'offshore creuse la vague et fait les tubes : un débutant n'y est pas sensible, un intermédiaire l'apprécie avec modération, un confirmé ou un expert le recherche.",
                    fontSize = 11.sp, color = colors.onSurfaceVariant
                )
                ChoiceRow(OFFSHORE_CHOICES.map { it.first to it.third }, profile.directionMatters) { dm ->
                    val choice = OFFSHORE_CHOICES.first { it.first == dm }
                    update(profile.copy(directionMatters = choice.first, offshoreMinFactor = choice.second))
                }
                Text("Rafales gênantes à partir de", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground)
                ChoiceRow(GUST_CHOICES, profile.gustThreshold) { update(profile.copy(gustThreshold = it)) }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Compter les rafales dans la note", fontSize = 12.sp, color = colors.onBackground, modifier = Modifier.weight(1f))
                    Switch(checked = profile.countGusts, onCheckedChange = { update(profile.copy(countGusts = it)) })
                }
                Text("Clapot gênant à partir de", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground)
                ChoiceRow(CHOP_CHOICES, profile.chopThreshold) { update(profile.copy(chopThreshold = it)) }
            }
        }

        // Aperçu : le vrai calcul, sur 5 journées types, avec les réglages en cours.
        SettingBlock("Aperçu en direct", hint = "La note de vraies journées avec ton profil.") {
            ScorePreviewList(surferLevel)
        }
    }
}

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
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.primary.copy(alpha = 0.12f)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("Tes notes, à ta mesure", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
        Text(
            "Dis-nous quelle vague tu cherches : les notes et le « trop gros » deviennent les tiens. Ça prend une minute.",
            fontSize = 12.5.sp, color = colors.onSurfaceVariant
        )
        Text(
            if (open) "Masquer" else "Comment ça marche ?", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.primary,
            modifier = Modifier.clip(RoundedCornerShape(50)).clickable { open = !open }.padding(vertical = 2.dp)
        )
        if (open) {
            Text(
                "Deux surfeurs ne cherchent pas la même vague. Une journée à 1,4 m et 10 s peut être parfaite pour l'un et trop grosse pour l'autre.",
                fontSize = 12.sp, color = colors.onSurfaceVariant
            )
            listOf(
                "🎯 Des notes faites pour toi : la taille qui te convient, et « trop gros » (violet) quand ça dépasse ton niveau, sans que ce soit « mauvais ».",
                "💨 Ta tolérance au vent, aux rafales, au clapot et aux houles courtes : les notes et les étoiles en tiennent compte.",
                "🏄 Ton matériel : tes planches avec leur volume et leur ratio litres par kilo.",
                "📓 Le journal de bord : tu choisis ta planche en un geste, et l'appli retient avec quoi tu surfes le mieux."
            ).forEach { Text(it, fontSize = 12.sp, color = colors.onBackground) }
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
            .background(colors.primary.copy(alpha = 0.14f))
            .clickable(onClick = onOpen)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("⭐ Des prévisions calculées pour toi", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
        Text(
            "Renseigne ton profil (niveau, vent, poids, planches) : sans lui, la note est celle d'un surfeur moyen. Une minute suffit.",
            fontSize = 11.5.sp, color = colors.onSurfaceVariant
        )
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Renseigner mon profil ›", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = colors.primary, modifier = Modifier.weight(1f))
            Text(
                "Plus tard", fontSize = 11.sp, color = colors.onSurfaceVariant,
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onLater).padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

/**
 * Page « Mon profil » : un résumé et les vraies journées notées, puis « Personnaliser » (assistant en étapes) ou
 * « Tout voir d'un coup » (tous les réglages sur une page, pour les habitués).
 */
@Composable
fun ProfilePage(
    surferLevel: String,
    onLevelChanged: (String) -> Unit,
    savedCustom: String,
    onCustomSaved: (String) -> Unit,
    exampleAnswers: String,
    onExampleAnswersChanged: (String) -> Unit,
    onInfo: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    // « overview », « wizard » ou « advanced ».
    var mode by remember { mutableStateOf("overview") }
    val profile = remember(surferLevel) { SurfProfile.fromLevel(surferLevel) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (mode) {
            "wizard" -> ProfileWizard(
                level = surferLevel,
                answers = exampleAnswers,
                onLevelChanged = onLevelChanged,
                onCustomSaved = onCustomSaved,
                onAnswersChanged = onExampleAnswersChanged,
                onClose = { mode = "overview" }
            )
            "advanced" -> {
                Text(
                    "‹ Retour", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.primary,
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable { mode = "overview" }.padding(vertical = 4.dp, horizontal = 4.dp)
                )
                SurferProfileSection(
                    surferLevel = surferLevel,
                    onLevelChanged = onLevelChanged,
                    savedCustom = savedCustom,
                    onCustomSaved = onCustomSaved,
                    onInfo = onInfo
                )
            }
            else -> {
                ProfileBenefitsCard()
                Column(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.surfaceVariant).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Mon profil", fontSize = 13.sp, color = colors.onSurfaceVariant)
                    Text(
                        if (isCustomLevel(surferLevel)) "Personnalisé" else levelLabel(surferLevel),
                        fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.onBackground
                    )
                    Text("Vagues : " + waveRangeText(profile), fontSize = 12.sp, color = colors.onSurfaceVariant)
                    Text(
                        "Vent de mer gênant dès ${profile.onshoreMax.roundToInt()} km/h · période ${profile.minPeriod.roundToInt()} s minimum",
                        fontSize = 12.sp, color = colors.onSurfaceVariant
                    )
                }
                ProfileDaysPreview(profile)
                Button(onClick = { mode = "wizard" }, modifier = Modifier.fillMaxWidth()) {
                    Text("Personnaliser mon profil", fontWeight = FontWeight.Bold)
                }
                Text(
                    "Tout voir d'un coup", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.primary,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).clickable { mode = "advanced" }.padding(8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}
