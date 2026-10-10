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
    condition("Jour moyen, vent de mer fort", 1.0, 8.3, 23, 30, "NO", "23 km/h NO, onshore", "1,0 m · 8 s"),
    condition("Belle journée propre", 1.3, 11.7, 9, 16, "E", "9 km/h E, offshore", "1,3 m · 12 s"),
    condition("Belle journée, vent de mer léger", 1.3, 10.8, 12, 20, "ONO", "12 km/h ONO, onshore", "1,3 m · 11 s"),
    condition("Jour moyen, vent moyen", 1.2, 7.5, 16, 22, "O", "16 km/h O, onshore"),
    condition("Gros jour, vent de mer léger", 1.9, 11.7, 17, 22, "NO", "17 km/h NO, onshore", "1,9 m · 12 s"),
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
    "J'y vais pas" to Color(0xFFD32F2F),
    "Pas mal" to Color(0xFFD4E157),
    "Parfait" to Color(0xFF7EE7FF),
    "Challengeant" to Color(0xFF2F6BFF),
    "Trop gros" to Color(0xFF7B2CBF)
)

private fun answerTextColor(index: Int): Color = if (index == 0 || index == 3 || index == 4) Color.White else Color(0xFF061018)

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

@Composable
private fun RoundButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(if (enabled) colors.primary else colors.surfaceVariant)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(label, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = if (enabled) colors.onPrimary else colors.onSurfaceVariant) }
}

/** − valeur + : un réglage qui se lit en gros. */
@Composable
private fun Stepper(modifier: Modifier = Modifier, value: String, caption: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier.clip(RoundedCornerShape(14.dp)).background(colors.surfaceVariant).padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        RoundButton("−", onClick = onMinus)
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.onBackground, maxLines = 1)
            Text(caption, fontSize = 10.5.sp, color = colors.onSurfaceVariant, maxLines = 1)
        }
        RoundButton("+", onClick = onPlus)
    }
}

private fun sizeWord(heightM: Double): String = when {
    heightM < 0.8 -> "Petite vague"
    heightM < 1.2 -> "Vague moyenne"
    heightM < 1.6 -> "Belle vague"
    heightM < 2.2 -> "Grosse vague"
    else -> "Très grosse vague"
}

/** Hauteur + période d'une vague : ce qu'un surfeur lit, au lieu des kJ. */
@Composable
private fun WaveEditor(
    title: String, subtitle: String?, height: Double, period: Int, onHeight: (Double) -> Unit, onPeriod: (Int) -> Unit,
    enabled: Boolean = true
) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.alpha(if (enabled) 1f else 0.4f)) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
        Text(formatFr(height, 1) + " m à " + period + " s", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = colors.primary)
        Text(sizeWord(height) + (subtitle?.let { " · $it" } ?: ""), fontSize = 12.sp, color = colors.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Stepper(Modifier.weight(1f), formatFr(height, 1) + " m", "hauteur",
                { if (enabled) onHeight((height - 0.1).coerceAtLeast(0.3)) }, { if (enabled) onHeight((height + 0.1).coerceAtMost(8.0)) })
            Stepper(Modifier.weight(1f), "$period s", "période",
                { if (enabled) onPeriod((period - 1).coerceAtLeast(4)) }, { if (enabled) onPeriod((period + 1).coerceAtMost(18)) })
        }
    }
}

private fun round1(v: Double): Double = (v * 10.0).roundToInt() / 10.0

/**
 * Hauteur et période dont l'énergie est la plus proche de [e] (kJ), pour relire un profil enregistré en mètres et en
 * secondes. À énergie égale, la période la plus plausible pour cette hauteur l'emporte.
 */
private fun waveFromEnergy(e: Double): Pair<Double, Int> {
    var best = 0.8 to 9
    var bestScore = Double.MAX_VALUE
    for (tenths in 3..80) {
        val h = tenths / 10.0
        for (p in 4..18) {
            val err = kotlin.math.abs(waveEnergyKj(h, p.toDouble()) - e) / e
            val score = err + 0.002 * kotlin.math.abs(p - (7.5 + 1.8 * h))
            if (score < bestScore) {
                bestScore = score
                best = h to p
            }
        }
    }
    return best
}

/** Plus petite / plus grosse vague d'un profil, en hauteur et période. */
private class WaveRange(val h1: Double, val p1: Int, val h2: Double, val p2: Int, val noCap: Boolean)

// Les valeurs de départ des quatre niveaux, telles qu'on les lit (0,8 m à 9 s, etc.).
private val PRESET_WAVES = mapOf(
    "beginner" to WaveRange(0.5, 8, 1.3, 10, false),
    "intermediate" to WaveRange(0.8, 9, 1.7, 10, false),
    "confirmed" to WaveRange(0.8, 9, 2.0, 11, false),
    "expert" to WaveRange(0.8, 9, 3.0, 12, true)
)

private fun waveRangeFor(p: SurfProfile): WaveRange {
    for ((level, range) in PRESET_WAVES) {
        val ref = SurfProfile.preset(level)
        val sameMin = kotlin.math.abs(ref.idealMin - p.idealMin) < 1.0
        val sameMax = if (ref.hasCap) p.hasCap && kotlin.math.abs(ref.cap - p.cap) < 1.0 else !p.hasCap
        if (sameMin && sameMax) return range
    }
    val (h1, t1) = waveFromEnergy(p.idealMin)
    if (!p.hasCap) return WaveRange(h1, t1, 3.0, 12, true)
    val (h2, t2) = waveFromEnergy(p.cap)
    return WaveRange(h1, t1, h2, t2, false)
}

/** Les chips de choix (une seule sélection). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceChips(labels: List<String>, selected: Int, onPick: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) colors.primary else colors.surfaceVariant)
                    .clickable { onPick(i) }.padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (on) colors.onPrimary else colors.onBackground)
            }
        }
    }
}

/** « 0,8 m · 9 s  →  1,7 m · 10 s » : la plage de vagues d'un profil, en hauteur et période. */
internal fun waveRangeText(p: SurfProfile): String {
    val r = waveRangeFor(p)
    val min = formatFr(r.h1, 1) + " m · " + r.p1 + " s"
    return if (r.noCap) "$min  →  aucune limite" else min + "  →  " + formatFr(r.h2, 1) + " m · " + r.p2 + " s"
}

/**
 * Plus petite et plus grosse vague d'un profil, en hauteur et période (au lieu des kJ). [presetKey] change quand on
 * repart d'un autre niveau : les valeurs affichées sont alors relues du profil. Chaque changement est renvoyé tout de suite.
 */
@Composable
internal fun WaveRangeEditor(base: SurfProfile, presetKey: String, onChange: (SurfProfile) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val start = remember(presetKey) { waveRangeFor(base) }
    var h1 by remember(presetKey) { mutableStateOf(start.h1) }
    var p1 by remember(presetKey) { mutableStateOf(start.p1) }
    var noCap by remember(presetKey) { mutableStateOf(start.noCap) }
    var h2 by remember(presetKey) { mutableStateOf(start.h2) }
    var p2 by remember(presetKey) { mutableStateOf(start.p2) }

    fun push() {
        val min = waveEnergyKj(h1, p1.toDouble())
        var cap = if (noCap) SurfProfile.NO_CAP else waveEnergyKj(h2, p2.toDouble())
        // Le maximum reste toujours au moins 50 % au-dessus du minimum : sinon « trop gros » presque partout.
        if (!noCap && cap < SurfProfile.MIN_CAP_RATIO * min) {
            h2 = ceil(sqrt(SurfProfile.MIN_CAP_RATIO * min / (1.962 * p2 * p2)) * 10.0) / 10.0
            cap = waveEnergyKj(h2, p2.toDouble())
        }
        val comfort = if (noCap) base.idealMax else cap * 0.6
        onChange(base.copy(idealMin = min, rampEnd = min * SurfProfile.RAMP_FACTOR, cap = cap, idealMax = comfort))
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        WaveEditor("Ma plus petite vague intéressante", null, h1, p1,
            onHeight = { h1 = round1(it); push() }, onPeriod = { p1 = it; push() })
        WaveEditor("Ma plus grosse vague", "juste en dessous, c'est challengeant", h2, p2,
            onHeight = { h2 = round1(it); push() }, onPeriod = { p2 = it; push() }, enabled = !noCap)
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.surfaceVariant)
                .clickable { noCap = !noCap; push() }.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Aucune limite de taille", fontSize = 13.sp, color = colors.onBackground, modifier = Modifier.weight(1f))
            Switch(checked = noCap, onCheckedChange = { noCap = it; push() })
        }
    }
}

private val WIZARD_TITLES = listOf("Ton niveau", "Tes vagues", "Le vent", "La qualité de la mer", "Tes notes", "Résumé")
private val ONSHORE_STEP_CHOICES = listOf(10.0 to "Strict · 10 km/h", 12.0 to "Sensible · 12 km/h", 14.0 to "Normal · 14 km/h", 20.0 to "Tolérant · 20 km/h")
private val WIZARD_LEVELS = listOf("beginner", "intermediate", "confirmed", "expert")

/**
 * Assistant en étapes. [level] est le niveau enregistré (préréglage ou « custom:… »), [answers] les réponses de l'étape 5.
 * Chaque changement est enregistré tout de suite ([onLevelChanged], [onCustomSaved], [onAnswersChanged]).
 */
@Composable
fun ProfileWizard(
    level: String,
    answers: String,
    onLevelChanged: (String) -> Unit,
    onCustomSaved: (String) -> Unit,
    onAnswersChanged: (String) -> Unit,
    onClose: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    var step by remember { mutableStateOf(0) }
    val answerMap = remember(answers) { parseExampleAnswers(answers) }
    // Le profil de base (réglages des étapes 1 à 4), sans l'effet des notes de l'étape 5.
    val base = remember(level) { SurfProfile.fromLevel(level).withoutPersonalNotes() }
    val effective = remember(base, answerMap) { applyExampleAnswers(base, answerMap) }
    // Change quand on repart d'un autre niveau de départ : les valeurs des vagues sont alors relues du profil.
    var wavesKey by remember { mutableStateOf(0) }

    fun save(newBase: SurfProfile, newAnswers: Map<Int, Int> = answerMap) {
        val finalProfile = applyExampleAnswers(newBase, newAnswers)
        val text = finalProfile.serialize()
        onLevelChanged(text)
        onCustomSaved(text)
        onAnswersChanged(serializeExampleAnswers(newAnswers))
    }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Progression
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
            WIZARD_TITLES.indices.forEach { i ->
                Box(modifier = Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(if (i <= step) colors.primary else colors.surfaceVariant))
            }
        }
        Column {
            Text(if (step == 5) "DERNIÈRE ÉTAPE" else "ÉTAPE ${step + 1} SUR 5", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurfaceVariant, letterSpacing = 0.8.sp)
            Text(WIZARD_TITLES[step], fontSize = 22.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
        }

        when (step) {
            0 -> {
                Text("Choisis un point de départ. Tu pourras tout ajuster ensuite.", fontSize = 13.sp, color = colors.onSurfaceVariant)
                ChoiceChips(WIZARD_LEVELS.map { levelLabel(it) }, WIZARD_LEVELS.indexOf(level), onPick = { i ->
                    onLevelChanged(WIZARD_LEVELS[i])
                    onAnswersChanged("")
                    wavesKey += 1
                })
            }
            1 -> {
                Text("À partir de quelle vague ça vaut le coup, et au-delà de laquelle c'est trop gros ?", fontSize = 13.sp, color = colors.onSurfaceVariant)
                WaveRangeEditor(base, wavesKey.toString()) { save(it) }
            }
            2 -> {
                Text("Quel vent peux-tu supporter ?", fontSize = 13.sp, color = colors.onSurfaceVariant)
                Text("Vent de mer (onshore) : gênant à partir de", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
                ChoiceChips(ONSHORE_STEP_CHOICES.map { it.second }, ONSHORE_STEP_CHOICES.indexOfFirst { it.first == base.onshoreMax }, onPick = { i ->
                    save(base.copy(onshoreMax = ONSHORE_STEP_CHOICES[i].first))
                })
                Text("Plus la vague est grosse, moins le vent la gêne : 16 km/h à 1,5 m restent surfables.", fontSize = 11.5.sp, color = colors.onSurfaceVariant)
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.surfaceVariant)
                        .clickable { save(base.copy(countGusts = !base.countGusts)) }.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Compter les rafales dans la note", fontSize = 13.sp, color = colors.onBackground)
                        Text("Elles comptent à moitié de leur écart avec le vent moyen.", fontSize = 11.sp, color = colors.onSurfaceVariant)
                    }
                    Switch(checked = base.countGusts, onCheckedChange = { save(base.copy(countGusts = it)) })
                }
            }
            3 -> {
                Text("Une houle courte et clapoteuse, ça te dérange ?", fontSize = 13.sp, color = colors.onSurfaceVariant)
                Text("Période minimum", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
                Text(base.minPeriod.roundToInt().toString() + " s", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = colors.primary)
                Stepper(Modifier.fillMaxWidth(), base.minPeriod.roundToInt().toString() + " s", "période minimum",
                    { save(base.copy(minPeriod = (base.minPeriod - 1).coerceAtLeast(4.0))) },
                    { save(base.copy(minPeriod = (base.minPeriod + 1).coerceAtMost(12.0))) })
            }
            4 -> {
                Text("Voilà 10 vagues avec la réponse que l'appli te donne. Change celles qui ne te ressemblent pas : ça compte vraiment.", fontSize = 13.sp, color = colors.onSurfaceVariant)
                var lastGroup = ""
                PROFILE_EXAMPLES.forEachIndexed { i, item ->
                    if (item.title != lastGroup) {
                        Text(item.title + " · " + item.size, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.onBackground, modifier = Modifier.padding(top = 4.dp))
                        lastGroup = item.title
                    }
                    val auto = autoExampleAnswer(calculateSlotRating(item.hour, 270, base.serialize()))
                    val current = answerMap[i] ?: auto
                    val shownRating = calculateSlotRating(item.hour, 270, effective.serialize())
                    Column(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.background).padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(item.wind, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground)
                        WhyBox(shownRating)
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.fillMaxWidth()) {
                            EXAMPLE_ANSWERS.forEachIndexed { a, (label, color) ->
                                val on = a == current
                                Box(
                                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(color)
                                        .alpha(if (on) 1f else 0.6f)
                                        .then(if (on) Modifier.border(2.dp, colors.onBackground, RoundedCornerShape(8.dp)) else Modifier)
                                        .clickable {
                                            val updated = answerMap.toMutableMap()
                                            if (a == auto) updated.remove(i) else updated[i] = a
                                            save(base, updated)
                                        }.padding(vertical = 9.dp, horizontal = 1.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(label, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = answerTextColor(a), maxLines = 1, textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }
                }
                val changed = answerMap.size
                Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.primary.copy(alpha = 0.14f)).padding(10.dp)) {
                    Text(
                        when {
                            changed == 0 -> "Aucune réponse changée : le profil reste tel que réglé."
                            else -> buildString {
                                append("Tes réponses pèsent : ")
                                append(when {
                                    effective.scoreOffset > 0 -> "les notes montent. "
                                    effective.scoreOffset < 0 -> "les notes baissent. "
                                    else -> "les couleurs changent peu. "
                                })
                                append(when {
                                    effective.comfortScale < 0.97 -> "Ton confort max baisse : plus de journées « Challengeant »."
                                    effective.comfortScale > 1.03 -> "Ton confort max monte : moins de journées « Challengeant »."
                                    else -> ""
                                })
                            }
                        },
                        fontSize = 12.5.sp, fontWeight = FontWeight.Medium, color = colors.onBackground
                    )
                }
            }
            else -> {
                Text("Voilà ton profil. Touche une ligne pour la modifier.", fontSize = 13.sp, color = colors.onSurfaceVariant)
                val rows = listOf(
                    "Niveau de départ" to (if (level in WIZARD_LEVELS) levelLabel(level) else "Personnalisé") to 0,
                    "Vagues" to waveRangeText(base) to 1,
                    "Vent" to ("Vent de mer gênant dès " + base.onshoreMax.roundToInt() + " km/h · rafales " + (if (base.countGusts) "comptées" else "ignorées")) to 2,
                    "Période minimum" to (base.minPeriod.roundToInt().toString() + " s") to 3,
                    "Tes réponses" to (answerMap.size.toString() + " changée" + (if (answerMap.size > 1) "s" else "")) to 4
                )
                rows.forEach { (labelValue, target) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.surfaceVariant).padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(labelValue.first, fontSize = 12.sp, color = colors.onSurfaceVariant)
                            Text(labelValue.second, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
                        }
                        Text("Modifier", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.primary,
                            modifier = Modifier.clip(RoundedCornerShape(50)).clickable { step = target }.padding(8.dp))
                    }
                }
            }
        }

        // Aperçu : de vraies journées, notées avec les réglages en cours.
        ProfileDaysPreview(effective)

        Text("✓ Enregistré automatiquement", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF2E9E4F))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { step = (step - 1).coerceAtLeast(0) }, enabled = step > 0) { Text("Précédent") }
            Button(onClick = { if (step == 5) onClose() else step += 1 }, modifier = Modifier.weight(1f)) {
                Text(if (step == 5) "Terminer" else "Suivant", fontWeight = FontWeight.Bold)
            }
        }
    }
}
