@file:Suppress("SpellCheckingInspection")
package com.surfcast.surfforecast

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.LocalDateTime
import kotlin.math.abs
import kotlin.math.roundToInt

// Mon style de surf : 4 profils de départ + un profil personnalisé (énergie, période, vent, rafales, clapot).

private val LEVELS = listOf("beginner", "intermediate", "confirmed", "expert")
private val MIN_ENERGIES = listOf(30.0, 80.0, 150.0, 250.0, 450.0, 700.0, 1500.0)
private val MAX_ENERGIES = listOf(150.0, 250.0, 450.0, 700.0, 1000.0, 1500.0, 3500.0, SurfProfile.NO_CAP)
private val ONSHORE_CHOICES = listOf(5.0 to "Aucun\n5\u00A0km/h", 8.0 to "Faible\n8\u00A0km/h", 12.0 to "Léger\n12\u00A0km/h", 20.0 to "Tolérant\n20\u00A0km/h")
// (importance de la direction, facteur offshore soutenu) : Indifférent / Apprécié / Recherché.
private val OFFSHORE_CHOICES = listOf(
    Triple(0.25, 1.0, "Indifférent\nsans effet"),
    Triple(0.8, 0.85, "Apprécié\nmodéré"),
    Triple(1.0, 1.0, "Recherché\ntubes")
)
private val GUST_CHOICES = listOf(20.0 to "Sensible\n20\u00A0km/h", 25.0 to "Normal\n25\u00A0km/h", 30.0 to "Tolérant\n30\u00A0km/h")
private val CHOP_CHOICES = listOf(0.15 to "Strict\n0,15\u00A0m", 0.2 to "Sensible\n0,2\u00A0m", 0.3 to "Normal\n0,3\u00A0m", 0.6 to "Tolérant\n0,6\u00A0m")

private fun energyName(w: Double) = when {
    w >= SurfProfile.NO_CAP -> "Aucune limite"
    w <= 80 -> "Très douce"
    w <= 150 -> "Douce"
    w <= 250 -> "Modérée"
    w <= 450 -> "Moyenne"
    w <= 700 -> "Soutenue"
    w <= 1500 -> "Puissante"
    else -> "Très puissante"
}

private fun energyLabel(w: Double) =
    if (w >= SurfProfile.NO_CAP) "Aucune limite" else energyName(w) + " · " + w.roundToInt().toString().reversed().chunked(3).joinToString(" ").reversed() + " kJ"

private fun nearestIndex(values: List<Double>, v: Double): Int =
    values.indices.minByOrNull { abs(values[it] - v) } ?: 0

/** Les 4 journées types de l'aperçu : conditions fixes, plage orientée plein ouest (vent d'est = offshore). */
private class PreviewDay(val title: String, val details: String, val hour: HourlyUiModel)

private fun previewDays(): List<PreviewDay> {
    fun h(height: Double, period: Double, wind: Int, gust: Int, dir: String, chop: Double) = HourlyUiModel(
        timeFormatted = "12h", rawTime = LocalDateTime(2026, 1, 1, 12, 0), waveHeight = height, wavePeriod = period,
        waveDirection = 270f, energyKj = calculateWaveEnergyReal(height, period), windSpeedKmh = wind,
        windDirectionStr = dir, weatherCode = 0, temperature = 18, windWaveHeight = chop, windGustKmh = gust
    )
    return listOf(
        PreviewDay("Petit jour propre", "0,8 m · 9 s · 6 km/h offshore · rafales 8", h(0.8, 9.0, 6, 8, "E", 0.0)),
        PreviewDay("Jour moyen", "1,5 m · 10 s · 15 km/h de travers · rafales 25", h(1.5, 10.0, 15, 25, "N", 0.2)),
        PreviewDay("Gros jour", "2,5 m · 14 s · 20 km/h offshore · rafales 30", h(2.5, 14.0, 20, 30, "E", 0.1)),
        PreviewDay("Mer hachée", "1,2 m · 7 s · 28 km/h onshore · rafales 40", h(1.2, 7.0, 28, 40, "O", 0.8))
    )
}

private fun parseNumber(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()

private fun roundedEnergy(e: Double): Double = (e / 10.0).roundToInt() * 10.0

/** « ≈ 1,3 m à 10 s » : ce que représente une énergie pour quelqu'un qui pense en hauteur et en période. */
private fun energyAsWave(e: Double): String =
    if (e >= SurfProfile.NO_CAP) "" else "≈ ${formatFr(heightForEnergy(e, 10.0))} m à 10 s"

/** Calculette : on entre la hauteur et la période d'une vague de référence, l'appli en tire l'énergie. */
@Composable
private fun EnergyCalculator(onUseAsMin: (Double) -> Unit, onUseAsMax: (Double) -> Unit) {
    val colors = MaterialTheme.colorScheme
    var heightText by remember { mutableStateOf("") }
    var periodText by remember { mutableStateOf("") }
    val h = parseNumber(heightText)
    val t = parseNumber(periodText)
    val energy = if (h != null && t != null && h > 0.0 && t > 0.0) roundedEnergy(waveEnergyKj(h, t)).coerceAtLeast(10.0) else null
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(colors.background).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("Je ne connais pas mon énergie : je la calcule", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground)
        Text(
            "Entre la hauteur et la période d'une vague que tu aimes (ou que tu ne veux pas dépasser), l'appli fait le calcul.",
            fontSize = 11.sp, color = colors.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = heightText, onValueChange = { heightText = it }, label = { Text("Hauteur") }, suffix = { Text("M") },
                singleLine = true, isError = heightText.isNotBlank() && h == null, modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = periodText, onValueChange = { periodText = it }, label = { Text("Période") }, suffix = { Text("S") },
                singleLine = true, isError = periodText.isNotBlank() && t == null, modifier = Modifier.weight(1f)
            )
        }
        if (energy != null) {
            Text("= ${energy.roundToInt()} kJ · ${energyName(energy)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.primary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onUseAsMin(energy) }, modifier = Modifier.weight(1f)) { Text("Mon minimum", fontSize = 12.sp, maxLines = 1) }
                OutlinedButton(onClick = { onUseAsMax(energy) }, modifier = Modifier.weight(1f)) { Text("Mon maximum", fontSize = 12.sp, maxLines = 1) }
            }
        }
    }
}

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
    onInfo: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val custom = isCustomLevel(surferLevel)
    val profile = remember(surferLevel) { SurfProfile.fromLevel(surferLevel) }
    fun update(p: SurfProfile) = onLevelChanged(p.serialize())

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
                                if (key == "custom") { if (!custom) update(profile) } else onLevelChanged(key)
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
            if (!custom) {
                Text(
                    "Ce profil règle : énergie ${profile.idealMin.roundToInt()} à " +
                        (if (profile.hasCap) "${profile.cap.roundToInt()} kJ" else "aucune limite") +
                        " · vent ${profile.windTolerance.roundToInt()} km/h · onshore ${profile.onshoreMax.roundToInt()} km/h" +
                        " · rafales ${profile.gustThreshold.roundToInt()} km/h · clapot ${formatFr(profile.chopThreshold, 2)} m · période ${profile.minPeriod.roundToInt()} s min.",
                    fontSize = 11.sp, color = colors.onSurfaceVariant.copy(alpha = 0.85f)
                )
            }
        }

        if (custom) {
            SettingBlock(
                "Énergie de la vague",
                hint = "Hauteur et période résumées en un seul chiffre (en kJ). Par exemple 1,5 m à 10 s ≈ 440 kJ."
            ) {
                val minIdx = nearestIndex(MIN_ENERGIES, profile.idealMin)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Mon minimum", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground)
                    Text(energyLabel(profile.idealMin), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.primary)
                }
                Text(energyAsWave(profile.idealMin), fontSize = 11.sp, color = colors.onSurfaceVariant)
                Slider(
                    value = minIdx.toFloat(), onValueChange = { i ->
                        val v = MIN_ENERGIES[i.roundToInt().coerceIn(0, MIN_ENERGIES.lastIndex)]
                        update(profile.copy(idealMin = v, rampEnd = maxOf(v, minOf(profile.rampEnd, profile.idealMax)), cap = maxOf(profile.cap, v)))
                    },
                    valueRange = 0f..6f, steps = 5
                )
                val maxIdx = if (!profile.hasCap) MAX_ENERGIES.lastIndex else nearestIndex(MAX_ENERGIES, profile.cap)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Mon maximum (au-delà : trop gros)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground, modifier = Modifier.weight(1f))
                    Text(energyLabel(profile.cap), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.primary)
                }
                Text(energyAsWave(profile.cap), fontSize = 11.sp, color = colors.onSurfaceVariant)
                Slider(
                    value = maxIdx.toFloat(), onValueChange = { i ->
                        val cap = MAX_ENERGIES[i.roundToInt().coerceIn(0, MAX_ENERGIES.lastIndex)]
                        update(
                            profile.copy(
                                cap = cap,
                                idealMax = if (cap >= SurfProfile.NO_CAP) 4000.0 else maxOf(cap / 2.2, profile.idealMin),
                                idealMin = minOf(profile.idealMin, cap)
                            )
                        )
                    },
                    valueRange = 0f..7f, steps = 6
                )
                EnergyCalculator(
                    onUseAsMin = { e -> update(profile.copy(idealMin = e, rampEnd = e, cap = maxOf(profile.cap, e), idealMax = maxOf(profile.idealMax, e))) },
                    onUseAsMax = { e ->
                        update(profile.copy(cap = e, idealMax = maxOf(e / 2.2, minOf(profile.idealMin, e)), idealMin = minOf(profile.idealMin, e), rampEnd = minOf(profile.rampEnd, e)))
                    }
                )
                Text(
                    "Repères : très douce ≈ 80 (0,6 m 8 s, mousses) · douce ≈ 150 (Oléron, Montalivet) · modérée ≈ 250 · moyenne ≈ 450 (1,5 m 10 s, beach breaks landais) · " +
                        "soutenue ≈ 700 · puissante ≈ 1 500 (La Gravière, Hossegor) · très puissante ≈ 3 500 (gros jours).",
                    fontSize = 11.sp, color = colors.onSurfaceVariant
                )
            }
            SettingBlock("Période minimum", "${profile.minPeriod.roundToInt()} s", "Houle courte et clapoteuse ↔ houle longue.") {
                Slider(
                    value = profile.minPeriod.toFloat(),
                    onValueChange = { update(profile.copy(minPeriod = it.roundToInt().toDouble())) },
                    valueRange = 4f..14f, steps = 9
                )
            }
            SettingBlock("Vent", "${profile.windTolerance.roundToInt()} km/h", "Force de vent bien tolérée (offshore et de travers). Plus on cherche la qualité de la vague, plus le vent, le clapot et l'onshore pèsent.") {
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
                Text("Clapot gênant à partir de", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground)
                ChoiceRow(CHOP_CHOICES, profile.chopThreshold) { update(profile.copy(chopThreshold = it)) }
            }
        }

        // Aperçu : le vrai calcul, sur 4 journées types, avec les réglages en cours.
        SettingBlock("Aperçu en direct", hint = "Le score de 4 journées types avec ton profil.") {
            previewDays().forEach { day ->
                val rating = calculateSlotRating(day.hour, 270, surferLevel, false)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val band = scoreBand(rating.score, rating.tooBig)
                    Box(
                        modifier = Modifier.size(44.dp).clip(CircleShape).background(band.color()),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (rating.tooBig) "↑" else rating.score.toString(), fontSize = 16.sp, fontWeight = FontWeight.Bold,
                            color = androidx.compose.ui.graphics.Color(0xFF04212A)
                        )
                    }
                    Column {
                        Text(day.title + if (rating.tooBig) " · Trop gros pour moi" else "", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground)
                        Text(day.details, fontSize = 11.sp, color = colors.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** Pourquoi renseigner son profil : l'argument montré en tête de la page, repris par la visite guidée. */
@Composable
fun ProfileBenefitsCard() {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.primary.copy(alpha = 0.12f)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("⭐ Recommandé : renseigne ton profil", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
        Text(
            "Deux surfeurs ne cherchent pas la même vague. Une journée à 1,4 m et 10 s peut être parfaite pour l'un et trop grosse pour l'autre : " +
                "plus l'appli te connaît, plus ses notes te ressemblent.",
            fontSize = 12.sp, color = colors.onSurfaceVariant
        )
        listOf(
            "🎯 Des scores faits pour toi : l'énergie qui te convient, et « trop gros » (violet) quand ça dépasse ton niveau, sans que ce soit « mauvais ».",
            "💨 Ta tolérance au vent, aux rafales, au clapot et aux houles courtes : le meilleur créneau tient compte de tout ça.",
            "🏄 Ton matériel : tes planches avec leur volume et leur ratio litres par kilo, pour voir d'un coup d'œil ce que tu peux surfer.",
            "📓 Le journal de bord : tu choisis ta planche en un geste, et l'appli retient avec quoi tu surfes le mieux."
        ).forEach { Text(it, fontSize = 12.sp, color = colors.onBackground) }
        Text("Ça prend une minute, et tu peux tout changer plus tard.", fontSize = 11.5.sp, color = colors.onSurfaceVariant)
    }
}
