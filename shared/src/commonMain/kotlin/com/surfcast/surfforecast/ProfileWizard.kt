@file:Suppress("SpellCheckingInspection")
package com.surfcast.surfforecast

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.LocalDateTime
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sqrt

// Assistant « Mon profil » : un réglage par écran (niveau, vagues, vent, période, notes), tout est enregistré tout seul.
// Sous chaque écran, de vraies journées de Montalivet notées avec les réglages en cours.

/** Une condition d'exemple : titre, taille / houle et vent tels qu'on les affiche, et l'heure de prévision qui sert au calcul. */
internal class ProfileCondition(val title: String, val size: String, val wind: String, val hour: HourlyUiModel)

private fun condition(
    title: String, height: Double, period: Double, windKmh: Int, gustKmh: Int, dir: String, windText: String, sizeText: String? = null
): ProfileCondition {
    val hour = HourlyUiModel(
        timeFormatted = "12h", rawTime = LocalDateTime(2026, 1, 1, 12, 0), waveHeight = height, wavePeriod = period,
        waveDirection = 270f, energyKj = calculateWaveEnergyReal(height, period), windSpeedKmh = windKmh,
        windDirectionStr = dir, weatherCode = 0, temperature = 18, windWaveHeight = 0.0, windGustKmh = gustKmh
    )
    val size = sizeText ?: (formatFr(height, 1) + " m · " + formatPeriod(period) + " s")
    return ProfileCondition(title, size, windText, hour)
}

private fun formatPeriod(p: Double): String = if (p == p.roundToInt().toDouble()) p.roundToInt().toString() else formatFr(p, 1)

/** De vraies journées de Montalivet (houle Météo-France, vent AROME), du plus petit au plus gros. */
internal val PROFILE_DAYS: List<ProfileCondition> = listOf(
    condition("Très petit jour", 0.54, 6.05, 8, 14, "ESE", "8 km/h ESE, offshore", "0,5 m · 6 s"),
    condition("Petit jour propre", 1.0, 10.0, 6, 12, "SE", "6 km/h SE, offshore"),
    condition("Jour moyen, onshore fort", 1.0, 8.3, 23, 30, "NO", "23 km/h NO, onshore", "1,0 m · 8 s"),
    condition("Belle journée propre", 1.3, 11.7, 9, 16, "E", "9 km/h E, offshore", "1,3 m · 12 s"),
    condition("Belle journée, onshore léger", 1.3, 10.8, 12, 20, "ONO", "12 km/h ONO, onshore", "1,3 m · 11 s"),
    condition("Jour moyen, vent moyen", 1.2, 7.5, 16, 22, "O", "16 km/h O, onshore"),
    condition("Gros jour, onshore léger", 1.9, 11.7, 17, 22, "NO", "17 km/h NO, onshore", "1,9 m · 12 s"),
    condition("Gros jour propre", 2.0, 11.85, 10, 14, "ENE", "10 km/h ENE, offshore", "2,0 m · 12 s")
)

/** Cinq de ces journées pour l'introduction. */
internal val INTRO_DAYS: List<ProfileCondition> = listOf(PROFILE_DAYS[1], PROFILE_DAYS[3], PROFILE_DAYS[5], PROFILE_DAYS[2], PROFILE_DAYS[6])

/** Les 10 vagues à noter : (taille, vent) ; la même vague avec des vents différents. */
internal val PROFILE_EXAMPLES: List<ProfileCondition> = listOf(
    condition("Petite vague", 0.7, 8.0, 6, 8, "E", "6 km/h offshore", "0,7 m · 8 s"),
    condition("Petite vague", 0.7, 8.0, 15, 21, "O", "15 km/h onshore", "0,7 m · 8 s"),
    condition("Vague moyenne", 1.0, 10.0, 10, 14, "E", "10 km/h offshore", "1,0 m · 10 s"),
    condition("Vague moyenne", 1.0, 10.0, 20, 28, "O", "20 km/h onshore", "1,0 m · 10 s"),
    condition("Belle vague", 1.35, 10.0, 15, 21, "E", "15 km/h offshore", "1,35 m · 10 s"),
    condition("Belle vague", 1.35, 10.0, 15, 21, "O", "15 km/h onshore", "1,35 m · 10 s"),
    condition("Grosse vague", 1.7, 11.0, 12, 17, "E", "12 km/h offshore", "1,7 m · 11 s"),
    condition("Grosse vague", 1.7, 11.0, 12, 17, "O", "12 km/h onshore", "1,7 m · 11 s"),
    condition("Gros jour", 2.0, 12.0, 12, 17, "E", "12 km/h offshore", "2,0 m · 12 s"),
    condition("Gros jour", 2.0, 12.0, 12, 17, "O", "12 km/h onshore", "2,0 m · 12 s")
)

/** Les cinq réponses possibles : (libellé, couleur). 0 = J'y vais pas, 1 = Pas mal, 2 = Parfait, 3 = Challengeant, 4 = Trop gros. */
internal val EXAMPLE_ANSWERS: List<Pair<String, Color>> = listOf(
    "J'y vais pas" to ScoreBand.AVOID.color(),
    "Pas mal" to ScoreBand.GOOD.color(),
    "Parfait" to ScoreBand.EXCELLENT.color(),
    "Challengeant" to ScoreBand.CHALLENGING.color(),
    "Gros jour" to ScoreBand.TOO_BIG.color()
)

private val EXAMPLE_ANSWER_BANDS = listOf(ScoreBand.AVOID, ScoreBand.GOOD, ScoreBand.EXCELLENT, ScoreBand.CHALLENGING, ScoreBand.TOO_BIG)

private fun answerTextColor(index: Int): Color = EXAMPLE_ANSWER_BANDS[index].onColor()

/** Réponse que donne l'appli à cette vague avec ce profil (celle que l'utilisateur change s'il n'est pas d'accord). */
internal fun autoExampleAnswer(rating: SlotRating): Int = when {
    rating.kind == ConditionKind.TOO_BIG -> 4
    rating.hollow -> 3
    rating.kind == ConditionKind.CHALLENGING && rating.score >= 40 -> 3
    rating.kind == ConditionKind.TOO_SMALL || rating.kind == ConditionKind.TOO_WINDY || rating.score < 40 -> 0
    rating.score >= 70 -> 2
    else -> 1
}

/** « 2:0,5:2 » -> {2 to 0, 5 to 2} : les réponses changées par l'utilisateur (indice de l'exemple -> réponse). */
internal fun parseExampleAnswers(text: String): Map<Int, Int> = text.split(",").mapNotNull { part ->
    val bits = part.split(":")
    val i = bits.getOrNull(0)?.trim()?.toIntOrNull()
    val a = bits.getOrNull(1)?.trim()?.toIntOrNull()
    if (i != null && a != null && i in PROFILE_EXAMPLES.indices && a in EXAMPLE_ANSWERS.indices) i to a else null
}.toMap()

internal fun serializeExampleAnswers(answers: Map<Int, Int>): String = answers.entries.sortedBy { it.key }.joinToString(",") { "${it.key}:${it.value}" }

/** Profil sans l'effet des notes perso. */
internal fun SurfProfile.withoutPersonalNotes(): SurfProfile = copy(scoreOffset = 0, comfortScale = 1.0)

private const val POINTS_PER_LEVEL = 15

/**
 * Les réponses changées pèsent vraiment sur les notes : quand l'utilisateur est plus généreux (ou plus exigeant) que
 * l'appli sur la qualité, toutes les notes montent (baissent) ; quand il dit « challengeant » plus (moins) souvent,
 * son seuil de confort baisse (monte).
 */
internal fun applyExampleAnswers(base: SurfProfile, answers: Map<Int, Int>): SurfProfile {
    val clean = base.withoutPersonalNotes()
    if (answers.isEmpty()) return clean
    var quality = 0.0
    var qualityCount = 0
    var size = 0.0
    var sizeCount = 0
    answers.forEach { (i, user) ->
        val auto = autoExampleAnswer(calculateSlotRating(PROFILE_EXAMPLES[i].hour, 270, clean.serialize()))
        if (auto <= 2 && user <= 2) {
            quality += (user - auto)
            qualityCount++
        } else {
            size += (if (user >= 3) 1 else 0) - (if (auto >= 3) 1 else 0)
            sizeCount++
        }
    }
    val levels = if (qualityCount > 0) (quality / qualityCount).roundToInt() else 0
    val sizeMean = if (sizeCount > 0) size / sizeCount else 0.0
    return clean.copy(
        scoreOffset = (levels * POINTS_PER_LEVEL).coerceIn(-30, 30),
        comfortScale = (1.0 - 0.3 * sizeMean).coerceIn(0.6, 1.5)
    )
}

// ---------------------------------------------------------------------------------------------------------------
// Éléments d'affichage

/** Une journée d'exemple : titre, pastille de la note, taille + houle en gros, vent dessous, phrase colorée. */
@Composable
internal fun ProfileConditionRow(item: ProfileCondition, profile: SurfProfile) {
    val colors = MaterialTheme.colorScheme
    val rating = remember(item, profile) { calculateSlotRating(item.hour, 270, profile.serialize()) }
    val band = conditionBand(rating)
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(item.title.uppercase(), fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurfaceVariant, letterSpacing = 0.6.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier.width(92.dp).clip(RoundedCornerShape(10.dp)).background(band.color()).padding(vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(band.label, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = band.onColor(), maxLines = 1)
            }
            Column {
                Text(item.size, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = colors.onBackground)
                Text(item.wind, fontSize = 12.sp, color = colors.onSurfaceVariant)
            }
        }
        WhyBox(rating)
    }
}

/** Phrase qui explique la note, dans un encart de la couleur de la note. */
@Composable
internal fun WhyBox(rating: SlotRating, modifier: Modifier = Modifier) {
    val band = conditionBand(rating)
    Box(modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(band.color()).padding(horizontal = 10.dp, vertical = 6.dp)) {
        Text(rating.why, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = band.onColor())
    }
}

/** Les vraies journées, notées avec le profil en cours. */
@Composable
internal fun ProfileDaysPreview(profile: SurfProfile, items: List<ProfileCondition> = PROFILE_DAYS) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.background).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Des vraies journées à Montalivet, notées avec ton réglage :", fontSize = 11.5.sp, color = colors.onSurfaceVariant)
        items.forEach { ProfileConditionRow(it, profile) }
    }
}

