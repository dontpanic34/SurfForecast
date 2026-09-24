@file:Suppress("SpellCheckingInspection")
package com.surfcast.surfforecast

import kotlin.math.abs

// Vecteur de features normalisees decrivant les conditions d'un creneau (passe ou a venir),
// utilise pour le pattern matching entre sessions notees et previsions futures.
data class SessionVector(
    val energyKj: Int,
    val windSpeedKmh: Int,
    val windCategory: String, // "offshore" / "cross" / "onshore"
    val waveDirDelta: Double, // ecart angulaire vs houle ideale du spot, 0 si spot sans direction ideale
    val tideCoeff: Int?
)

private const val WEIGHT_ENERGY = 0.35
private const val WEIGHT_WIND_SPEED = 0.25
private const val WEIGHT_WIND_CATEGORY = 0.20
private const val WEIGHT_WAVE_DIR = 0.15
private const val WEIGHT_TIDE = 0.05

private const val ENERGY_RANGE_KJ = 600.0
private const val WIND_SPEED_RANGE_KMH = 40.0
private const val TIDE_COEFF_RANGE = 120.0

fun sessionVectorFromCondition(condition: ConditionSnapshot, idealSwellDirection: Int?): SessionVector {
    val waveDirDelta = idealSwellDirection?.let {
        angularDifference(condition.waveDirection.toDouble(), it.toDouble())
    } ?: 0.0
    return SessionVector(
        energyKj = condition.energyKj,
        windSpeedKmh = condition.windSpeedKmh,
        windCategory = windCategoryFromDegrees(condition.windDirection.toFloat()),
        waveDirDelta = waveDirDelta,
        tideCoeff = condition.tideCoeff
    )
}

fun sessionVectorFromForecast(hourlyModel: HourlyUiModel, idealSwellDirection: Int?, tideCoeff: Int?): SessionVector {
    val waveDirDelta = idealSwellDirection?.let {
        angularDifference(hourlyModel.waveDirection.toDouble(), it.toDouble())
    } ?: 0.0
    return SessionVector(
        energyKj = hourlyModel.energyKj,
        windSpeedKmh = hourlyModel.windSpeedKmh,
        windCategory = windCategoryFor(hourlyModel.windDirectionStr),
        waveDirDelta = waveDirDelta,
        tideCoeff = tideCoeff
    )
}

// Distance categorielle entre categories de vent : meme categorie = 0, categories adjacentes
// (offshore/cross ou cross/onshore) = 0.5, opposees (offshore/onshore) = 1.
private fun windCategoryDistance(a: String, b: String): Double {
    if (a == b) return 0.0
    val opposite = (a == "offshore" && b == "onshore") || (a == "onshore" && b == "offshore")
    return if (opposite) 1.0 else 0.5
}

// Score de similarite 0-100 entre une session de reference (bien notee) et un candidat
// (creneau des previsions a venir). Distance euclidienne ponderee sur les features normalisees.
fun matchScore(reference: SessionVector, candidate: SessionVector): Double {
    val dEnergy = abs(reference.energyKj - candidate.energyKj) / ENERGY_RANGE_KJ
    val dWindSpeed = abs(reference.windSpeedKmh - candidate.windSpeedKmh) / WIND_SPEED_RANGE_KMH
    val dWindCategory = windCategoryDistance(reference.windCategory, candidate.windCategory)
    val dWaveDir = abs(reference.waveDirDelta - candidate.waveDirDelta) / 180.0
    val dTide = if (reference.tideCoeff != null && candidate.tideCoeff != null) {
        abs(reference.tideCoeff - candidate.tideCoeff) / TIDE_COEFF_RANGE
    } else {
        0.0
    }

    val weightedDistance = WEIGHT_ENERGY * dEnergy.coerceIn(0.0, 1.0) +
        WEIGHT_WIND_SPEED * dWindSpeed.coerceIn(0.0, 1.0) +
        WEIGHT_WIND_CATEGORY * dWindCategory +
        WEIGHT_WAVE_DIR * dWaveDir.coerceIn(0.0, 1.0) +
        WEIGHT_TIDE * dTide.coerceIn(0.0, 1.0)

    return ((1.0 - weightedDistance) * 100.0).coerceIn(0.0, 100.0)
}

data class PatternMatch(
    val hourlyModel: HourlyUiModel,
    val referenceSession: SurfSessionWithRelations,
    val score: Int
)

const val PATTERN_MATCH_THRESHOLD = 85.0
