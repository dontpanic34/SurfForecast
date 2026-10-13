package com.surfcast.surfforecast

import androidx.compose.ui.graphics.Color

/**
 * Neuf libellés, trois teintes à deux nuances : vert vif (parfait, très bon) et vert sombre (bon), jaune (correct) et
 * orange (pas pour toi : challengeant, trop petit, trop gros), rouge (médiocre, trop de vent) et bordeaux (mauvais).
 */
enum class ScoreBand(val label: String) {
    TOO_BIG("Gros jour"),
    AVOID("Mauvais"),
    POOR("Médiocre"),
    FAIR("Correct"),
    GOOD("Bon"),
    VERY_GOOD("Très bon"),
    EXCELLENT("Parfait"),
    CHALLENGING("Challengeant"),
    TOO_SMALL("Petit jour"),
    TOO_WINDY("Trop de vent")
}

/** Échelle des notes (choisie par le propriétaire le 13 octobre 2026) : Mauvais sous 25, Médiocre jusqu'à 45, Correct jusqu'à 62, Bon jusqu'à 80, Très bon jusqu'à 94, Parfait au-delà. */
const val POOR_MIN = 25
const val FAIR_MIN = 45

/** Note à partir de laquelle une heure est « Bon ». */
const val GOOD_MIN = 62

/** Note à partir de laquelle une heure est « Très bon ». */
const val VERY_GOOD_MIN = 80

/** Note à partir de laquelle une heure est « Parfait ». */
const val PERFECT_MIN = 94

/** Zone de tendance : à cette distance d'une limite, le libellé dit « Correct à Bon ». */
const val TREND_ZONE = 3

/** [score] négatif ou [tooBig] = trop gros pour ce niveau. */
fun scoreBand(rawScore: Int, tooBig: Boolean = false): ScoreBand = scoreBandShifted(rawScore, tooBig, ToleranceSettings.mode * 8)

private fun scoreBandShifted(rawScore: Int, tooBig: Boolean, shift: Int): ScoreBand {
    val score = if (rawScore < 0) rawScore else rawScore + shift
    return when {
    tooBig || score < 0 -> ScoreBand.TOO_BIG
    score < POOR_MIN -> ScoreBand.AVOID
    score < FAIR_MIN -> ScoreBand.POOR
    score < GOOD_MIN -> ScoreBand.FAIR
    score < VERY_GOOD_MIN -> ScoreBand.GOOD
    score < PERFECT_MIN -> ScoreBand.VERY_GOOD
    else -> ScoreBand.EXCELLENT
    }
}

/** Nuance d'une heure : le niveau de qualité, sauf quand la raison est la taille ou le vent. */
fun conditionBand(rating: SlotRating): ScoreBand = when (rating.kind) {
    ConditionKind.TOO_BIG -> ScoreBand.TOO_BIG
    ConditionKind.TOO_SMALL -> ScoreBand.TOO_SMALL
    ConditionKind.TOO_WINDY -> ScoreBand.TOO_WINDY
    ConditionKind.CHALLENGING -> ScoreBand.CHALLENGING
    ConditionKind.NORMAL -> scoreBand(rating.score)
}

private val GREEN = Color(0xFF2BD47A)
private val GREEN_DEEP = Color(0xFF0F8A52)
private val TEAL = Color(0xFF1A9C82)
private val YELLOW = Color(0xFFFFD21F)
private val ORANGE = Color(0xFFFF9A1F)
private val RED = Color(0xFFE53935)
private val BORDEAUX = Color(0xFF9B1C31)

fun ScoreBand.color(): Color = when (this) {
    ScoreBand.VERY_GOOD -> GREEN
    ScoreBand.EXCELLENT -> GREEN_DEEP
    ScoreBand.GOOD -> TEAL
    ScoreBand.FAIR -> YELLOW
    ScoreBand.CHALLENGING, ScoreBand.TOO_SMALL, ScoreBand.TOO_BIG -> ORANGE
    ScoreBand.POOR, ScoreBand.TOO_WINDY -> RED
    ScoreBand.AVOID -> BORDEAUX
}

/** Couleur du libellé sur le fond sombre des cartes : le vert sombre, le rouge et le bordeaux y sont éclaircis. */
fun ScoreBand.textColor(): Color = when (this) {
    ScoreBand.GOOD -> Color(0xFF3FD0B0)
    ScoreBand.EXCELLENT -> Color(0xFF1FAA68)
    ScoreBand.POOR, ScoreBand.TOO_WINDY -> Color(0xFFFF5A55)
    ScoreBand.AVOID -> Color(0xFFFF6F86)
    else -> color()
}

/** Niveau de la jauge à 5 segments : Mauvais 1, Médiocre 2, Correct 3, Bon 4, Très bon et Parfait 5 ; les conditions hors zone (challengeant, trop petit, trop gros) 3, trop de vent 2. */
fun gaugeLevel(rating: SlotRating): Int = when (conditionBand(rating)) {
    ScoreBand.EXCELLENT, ScoreBand.VERY_GOOD -> 5
    ScoreBand.GOOD -> 4
    ScoreBand.FAIR, ScoreBand.CHALLENGING, ScoreBand.TOO_BIG, ScoreBand.TOO_SMALL -> 3
    ScoreBand.POOR, ScoreBand.TOO_WINDY -> 2
    ScoreBand.AVOID -> 1
}

/** Couleur de texte lisible sur [color] de cette nuance. */
fun ScoreBand.onColor(): Color = when (this) {
    ScoreBand.GOOD, ScoreBand.EXCELLENT, ScoreBand.AVOID, ScoreBand.POOR, ScoreBand.TOO_WINDY -> Color.White
    else -> Color(0xFF061018)
}

private val BAND_ORDER = listOf(
    ScoreBand.AVOID, ScoreBand.POOR, ScoreBand.FAIR, ScoreBand.GOOD, ScoreBand.VERY_GOOD, ScoreBand.EXCELLENT
)
private val BOUNDS = listOf(POOR_MIN, FAIR_MIN, GOOD_MIN, VERY_GOOD_MIN, PERFECT_MIN)

/**
 * Libellé avec tendance, comme « Fair to Good » chez Surfline : quand la note est tout près d'une limite (à 3 points
 * près), il dit d'où l'on vient et où l'on va, selon l'heure d'avant (« Correct à Bon » si ça monte, « Bon à Correct » si ça baisse). Sinon le niveau seul. Les cas à part (trop gros, challengeant...)
 * n'ont pas de tendance.
 */
fun bandLabelWithTrend(rating: SlotRating, previous: SlotRating? = null): String {
    val band = conditionBand(rating)
    if (band !in BAND_ORDER) return tr(band.label)
    for ((i, bound) in BOUNDS.withIndex()) {
        if (kotlin.math.abs(rating.score - bound) <= TREND_ZONE) {
            // Le libellé va de l'heure d'avant vers celle-ci : si ça baisse, « Bon à Correct » ; sinon « Correct à Bon ».
            val falling = previous != null && previous.score > rating.score
            return if (falling) tr(BAND_ORDER[i + 1].label) + tr(" à ") + tr(BAND_ORDER[i].label) else tr(BAND_ORDER[i].label) + tr(" à ") + tr(BAND_ORDER[i + 1].label)
        }
    }
    return tr(band.label)
}

/**
 * Les deux niveaux d'un libellé avec tendance (« Très bon à Parfait » -> très bon, parfait), ou le niveau seul :
 * le fond du libellé est un dégradé de l'un à l'autre.
 */
fun trendBands(rating: SlotRating): List<ScoreBand> {
    val band = conditionBand(rating)
    if (band !in BAND_ORDER) return listOf(band)
    for ((i, bound) in BOUNDS.withIndex()) {
        if (kotlin.math.abs(rating.score - bound) <= TREND_ZONE) return listOf(BAND_ORDER[i], BAND_ORDER[i + 1])
    }
    return listOf(band)
}

/** Couleur de texte lisible sur le fond d'un libellé (une ou deux nuances). */
fun trendOnColor(bands: List<ScoreBand>): Color {
    val darkCount = bands.count { it.onColor() == Color.White }
    return if (darkCount * 2 > bands.size) Color.White else Color(0xFF061018)
}

private val STOPS = listOf(
    12 to 0xFF9B1C31, 35 to 0xFFE53935, 53 to 0xFFFFD21F, 71 to 0xFF1A9C82, 85 to 0xFF2BD47A, 92 to 0xFF2BD47A, 97 to 0xFF0F8A52
)

/** Couleur d'une note sur le dégradé continu (bordeaux -> rouge -> jaune -> vert sombre -> vert vif). */
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
