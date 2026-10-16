@file:Suppress("SpellCheckingInspection")
package com.surfcast.surfforecast

import kotlin.math.abs

// Vecteur de features normalisees decrivant les conditions d'un creneau (passe ou a venir),
// utilise pour le pattern matching entre sessions notees et previsions futures.
data class SessionVector(
    val energyKj: Int,
    val windSpeedKmh: Int,
    val windCategory: String, // "offshore" / "side-off" / "side" / "side-on" / "onshore"
    val waveDirDelta: Double, // ecart angulaire vs houle ideale du spot, 0 si spot sans direction ideale
    val tideCoeff: Int?,
    val wavePeriod: Double = 0.0 // 0 = inconnue
)

// Poids de chaque dimension (la somme des dimensions connues est ramenée à 1).
private const val WEIGHT_ENERGY = 0.30
private const val WEIGHT_PERIOD = 0.10
private const val WEIGHT_WIND_SPEED = 0.15
private const val WEIGHT_WIND_CATEGORY = 0.15
private const val WEIGHT_WAVE_DIR = 0.15
private const val WEIGHT_TIDE = 0.15

// Tolérance de chaque dimension : à cet écart, la dimension ne ressemble plus du tout (0).
private const val ENERGY_RATIO_TOLERANCE = 0.92 // ln(2,5) : une énergie 2,5 fois plus grosse ou plus petite
private const val PERIOD_TOLERANCE_S = 3.0
private const val WIND_SPEED_TOLERANCE_KMH = 10.0
private const val WAVE_DIR_TOLERANCE_DEG = 30.0
private const val TIDE_COEFF_TOLERANCE = 30.0

fun sessionVectorFromCondition(condition: ConditionSnapshot, idealSwellDirection: Int?): SessionVector {
    val waveDirDelta = idealSwellDirection?.let {
        angularDifference(condition.waveDirection.toDouble(), it.toDouble())
    } ?: 0.0
    return SessionVector(
        energyKj = condition.energyKj,
        windSpeedKmh = condition.windSpeedKmh,
        windCategory = windCategoryFromDegrees(condition.windDirection.toFloat(), idealSwellDirection),
        waveDirDelta = waveDirDelta,
        tideCoeff = condition.tideCoeff,
        wavePeriod = condition.wavePeriod
    )
}

fun sessionVectorFromForecast(hourlyModel: HourlyUiModel, idealSwellDirection: Int?, tideCoeff: Int?): SessionVector {
    val waveDirDelta = idealSwellDirection?.let {
        angularDifference(hourlyModel.waveDirection.toDouble(), it.toDouble())
    } ?: 0.0
    return SessionVector(
        energyKj = hourlyModel.energyKj,
        windSpeedKmh = kotlin.math.round(effectiveWindKmh(hourlyModel)).toInt(),
        windCategory = windCategoryFor(hourlyModel.windDirectionStr, idealSwellDirection),
        waveDirDelta = waveDirDelta,
        tideCoeff = tideCoeff,
        wavePeriod = hourlyModel.wavePeriod
    )
}

// Nombre de crans entre deux types de vent : offshore, side-off, side, side-on, onshore.
private fun windCategorySteps(a: String, b: String): Int {
    fun pos(c: String) = when (c) { "offshore" -> 0.0; "side-off" -> 0.5; "side", "cross" -> 1.0; "side-on" -> 1.5; else -> 2.0 }
    return kotlin.math.round(abs(pos(a) - pos(b)) * 2.0).toInt()
}

// Ressemblance 0-1 d'une dimension : 1 = pareil, 0 = écart de la tolérance ; la perte est douce près de 0 et rapide après (carré).
private fun similar(gap: Double, tolerance: Double): Double {
    val x = (gap / tolerance).coerceIn(0.0, 1.0)
    return 1.0 - x * x
}

/**
 * Score de ressemblance 0-100 entre une session de référence (bien notée) et un créneau à venir.
 * Chaque dimension (énergie en proportion, période, force du vent, type de vent, direction de la houle, marée) donne une
 * ressemblance de 0 à 1 ; le score est leur moyenne pondérée. Une dimension absente d'un côté (marée, période) est ignorée.
 * Les grosses différences sur l'énergie, la force ou le type de vent plafonnent le score sous le seuil de [PATTERN_MATCH_THRESHOLD] :
 * un vent identique ne rattrape pas une vague deux fois plus petite.
 */
fun matchScore(reference: SessionVector, candidate: SessionVector): Double {
    val ratio = kotlin.math.ln((candidate.energyKj.coerceAtLeast(1).toDouble()) / reference.energyKj.coerceAtLeast(1).toDouble())
    val sEnergy = similar(abs(ratio), ENERGY_RATIO_TOLERANCE)
    val sWindSpeed = similar(abs(reference.windSpeedKmh - candidate.windSpeedKmh).toDouble(), WIND_SPEED_TOLERANCE_KMH)
    val sWindCategory = when (windCategorySteps(reference.windCategory, candidate.windCategory)) { 0 -> 1.0; 1 -> 0.55; 2 -> 0.15; else -> 0.0 }
    val sWaveDir = similar(abs(reference.waveDirDelta - candidate.waveDirDelta), WAVE_DIR_TOLERANCE_DEG)

    val dimensions = mutableListOf(
        WEIGHT_ENERGY to sEnergy,
        WEIGHT_WIND_SPEED to sWindSpeed,
        WEIGHT_WIND_CATEGORY to sWindCategory,
        WEIGHT_WAVE_DIR to sWaveDir
    )
    if (reference.wavePeriod > 0.0 && candidate.wavePeriod > 0.0) {
        dimensions += WEIGHT_PERIOD to similar(abs(reference.wavePeriod - candidate.wavePeriod), PERIOD_TOLERANCE_S)
    }
    if (reference.tideCoeff != null && candidate.tideCoeff != null) {
        dimensions += WEIGHT_TIDE to similar(abs(reference.tideCoeff - candidate.tideCoeff).toDouble(), TIDE_COEFF_TOLERANCE)
    }

    val totalWeight = dimensions.sumOf { it.first }
    var score = dimensions.sumOf { (weight, sim) -> weight * sim } / totalWeight * 100.0

    // Plafonds : un seul point très différent suffit à écarter la phrase « ressemble à ».
    if (sEnergy < 0.5) score = minOf(score, PATTERN_MATCH_THRESHOLD - 5.0)
    if (sWindCategory < 0.5 || sWindSpeed < 0.3) score = minOf(score, PATTERN_MATCH_THRESHOLD - 1.0)
    return score.coerceIn(0.0, 100.0)
}

data class PatternMatch(
    val hourlyModel: HourlyUiModel,
    val referenceSession: SurfSessionWithRelations,
    val score: Int
)

const val PATTERN_MATCH_THRESHOLD = 85.0
