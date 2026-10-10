package com.surfcast.surfforecast

import androidx.compose.ui.graphics.Color

/**
 * Nuances de la note, du pire au meilleur, avec des couleurs bien contrastées : rouge foncé (mauvais), rouge (médiocre),
 * orange (correct), jaune (bon), vert (très bon), vert fluo (excellent) ; « trop gros » à part (violet).
 */
enum class ScoreBand(val label: String) {
    TOO_BIG("Trop gros"),
    AVOID("Mauvais"),
    POOR("Médiocre"),
    FAIR("Correct"),
    GOOD("Bon"),
    VERY_GOOD("Très bon"),
    EXCELLENT("Excellent")
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

fun ScoreBand.color(): Color = when (this) {
    ScoreBand.TOO_BIG -> Color(0xFF7E57C2)
    ScoreBand.AVOID -> Color(0xFF8E0000)
    ScoreBand.POOR -> Color(0xFFE53935)
    ScoreBand.FAIR -> Color(0xFFFB8C00)
    ScoreBand.GOOD -> Color(0xFFFFD600)
    ScoreBand.VERY_GOOD -> Color(0xFF2E9E4F)
    ScoreBand.EXCELLENT -> Color(0xFF00E5FF)
}
