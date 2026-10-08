package com.surfcast.surfforecast

import androidx.compose.ui.graphics.Color
import com.surfcast.surfforecast.ui.theme.AppColors

/** Nuances de la note : plus fines que rouge / orange / vert, plus « trop gros » à part (violet). */
enum class ScoreBand(val label: String) {
    TOO_BIG("Trop gros"),
    RED("À éviter"),
    ORANGE("Médiocre"),
    YELLOW("Correct"),
    LIGHT_GREEN("Bon"),
    GREEN("Très bon"),
    EXCELLENT("Excellent")
}

/** [score] négatif ou [tooBig] = trop gros pour ce niveau. */
fun scoreBand(score: Int, tooBig: Boolean = false): ScoreBand = when {
    tooBig || score < 0 -> ScoreBand.TOO_BIG
    score < 20 -> ScoreBand.RED
    score < 40 -> ScoreBand.ORANGE
    score < 55 -> ScoreBand.YELLOW
    score < 70 -> ScoreBand.LIGHT_GREEN
    score < 85 -> ScoreBand.GREEN
    else -> ScoreBand.EXCELLENT
}

fun ScoreBand.color(): Color = when (this) {
    ScoreBand.TOO_BIG -> Color(0xFF7E57C2)
    ScoreBand.RED -> AppColors.WindHigh
    ScoreBand.ORANGE -> AppColors.WindMid
    ScoreBand.YELLOW -> AppColors.WindLow
    ScoreBand.LIGHT_GREEN -> Color(0xFF9CCC65)
    ScoreBand.GREEN -> AppColors.TideLow
    ScoreBand.EXCELLENT -> Color(0xFF00C853)
}
