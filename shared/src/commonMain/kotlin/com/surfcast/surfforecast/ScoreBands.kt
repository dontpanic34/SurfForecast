package com.surfcast.surfforecast

import androidx.compose.ui.graphics.Color

/**
 * Nuances de la note, du meilleur (cyan clair) au pire (rouge), comme une carte de vent : cyan (parfait), vert (très
 * bon), vert-jaune (bon), jaune (correct), orange (médiocre), rouge (mauvais). À part : violet (trop gros), rose
 * (challengeant : costaud mais faisable), gris clair (trop petit), orange foncé (trop de vent).
 */
enum class ScoreBand(val label: String) {
    TOO_BIG("Trop gros"),
    AVOID("Mauvais"),
    POOR("Médiocre"),
    FAIR("Correct"),
    GOOD("Bon"),
    VERY_GOOD("Très bon"),
    EXCELLENT("Parfait"),
    CHALLENGING("Challengeant"),
    TOO_SMALL("Trop petit"),
    TOO_WINDY("Trop de vent")
}

/** [score] négatif ou [tooBig] = trop gros pour ce niveau. */
fun scoreBand(score: Int, tooBig: Boolean = false): ScoreBand = when {
    tooBig || score < 0 -> ScoreBand.TOO_BIG
    score < 20 -> ScoreBand.AVOID
    score < 40 -> ScoreBand.POOR
    score < 55 -> ScoreBand.FAIR
    score < 70 -> ScoreBand.GOOD
    score < 85 -> ScoreBand.VERY_GOOD
    else -> ScoreBand.EXCELLENT
}

/** Nuance d'une heure : le niveau de qualité, sauf quand la raison est la taille ou le vent. */
fun conditionBand(rating: SlotRating): ScoreBand = when (rating.kind) {
    ConditionKind.TOO_BIG -> ScoreBand.TOO_BIG
    ConditionKind.TOO_SMALL -> ScoreBand.TOO_SMALL
    ConditionKind.TOO_WINDY -> ScoreBand.TOO_WINDY
    ConditionKind.CHALLENGING -> ScoreBand.CHALLENGING
    ConditionKind.NORMAL -> scoreBand(rating.score)
}

fun ScoreBand.color(): Color = when (this) {
    ScoreBand.TOO_BIG -> Color(0xFF7B2CBF)
    ScoreBand.AVOID -> Color(0xFFE53935)
    ScoreBand.POOR -> Color(0xFFFF9A1F)
    ScoreBand.FAIR -> Color(0xFFFFE033)
    ScoreBand.GOOD -> Color(0xFFB8E04A)
    ScoreBand.VERY_GOOD -> Color(0xFF3DDC84)
    ScoreBand.EXCELLENT -> Color(0xFF7EE7FF)
    ScoreBand.CHALLENGING -> Color(0xFFD81B60)
    ScoreBand.TOO_SMALL -> Color(0xFFCFD8E3)
    ScoreBand.TOO_WINDY -> Color(0xFFF57C00)
}

/** Couleur de texte lisible sur [color] de cette nuance. */
fun ScoreBand.onColor(): Color = when (this) {
    ScoreBand.TOO_BIG, ScoreBand.CHALLENGING, ScoreBand.AVOID -> Color.White
    else -> Color(0xFF061018)
}

private val BAND_ORDER = listOf(
    ScoreBand.AVOID, ScoreBand.POOR, ScoreBand.FAIR, ScoreBand.GOOD, ScoreBand.VERY_GOOD, ScoreBand.EXCELLENT
)
private val BOUNDS = listOf(20, 40, 55, 70, 85)

/**
 * Libellé avec tendance, comme « Fair to Good » chez Surfline : quand la note est tout près d'une limite (à 4 points
 * près), il dit vers où ça va (« Correct à Bon »). Sinon le niveau seul. Les cas à part (trop gros, challengeant...)
 * n'ont pas de tendance.
 */
fun bandLabelWithTrend(rating: SlotRating): String {
    val band = conditionBand(rating)
    if (band !in BAND_ORDER) return band.label
    for ((i, bound) in BOUNDS.withIndex()) {
        if (kotlin.math.abs(rating.score - bound) <= 4) return BAND_ORDER[i].label + " à " + BAND_ORDER[i + 1].label
    }
    return band.label
}

private val STOPS = listOf(
    10 to 0xFFE53935, 30 to 0xFFFF9A1F, 47 to 0xFFFFE033, 62 to 0xFFB8E04A, 77 to 0xFF3DDC84, 92 to 0xFF7EE7FF
)

/** Couleur d'une note sur le dégradé continu (rouge -> orange -> jaune -> vert-jaune -> vert -> cyan). */
fun scoreGradientColor(score: Int): Color {
    val s = score.coerceIn(STOPS.first().first, STOPS.last().first)
    for (i in 1 until STOPS.size) {
        val (x1, c1) = STOPS[i]
        if (s <= x1) {
            val (x0, c0) = STOPS[i - 1]
            val t = (s - x0).toFloat() / (x1 - x0)
            return androidx.compose.ui.graphics.lerp(Color(c0), Color(c1), t)
        }
    }
    return Color(STOPS.last().second)
}

/** Couleur d'une heure pour le liseré : dégradé continu pour une qualité ordinaire, couleur de la raison sinon. */
fun conditionColor(rating: SlotRating): Color =
    if (rating.kind == ConditionKind.NORMAL) scoreGradientColor(rating.score) else conditionBand(rating).color()
