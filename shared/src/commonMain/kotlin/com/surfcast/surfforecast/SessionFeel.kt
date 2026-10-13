package com.surfcast.surfforecast

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Le ressenti d'une session, dans les mots de l'appli : Mauvais, Médiocre, Correct, Bon, Très bon, Parfait, Challengeant.
 * Il est enregistré dans `SurfSession.rating` sous la forme 10 + rang ; les anciennes sessions (étoiles de 1 à 5) sont lues
 * comme 1 étoile = Médiocre … 5 étoiles = Parfait.
 */
val SESSION_FEELS: List<Pair<String, ScoreBand>> = listOf(
    "Mauvais" to ScoreBand.AVOID,
    "Médiocre" to ScoreBand.POOR,
    "Correct" to ScoreBand.FAIR,
    "Bon" to ScoreBand.GOOD,
    "Très bon" to ScoreBand.VERY_GOOD,
    "Parfait" to ScoreBand.EXCELLENT,
    "Challengeant" to ScoreBand.CHALLENGING
)

private const val FEEL_BASE = 10

/** La valeur enregistrée pour le ressenti de rang [index] (0 = Mauvais … 6 = Challengeant). */
fun encodeFeel(index: Int): Int = FEEL_BASE + index.coerceIn(0, SESSION_FEELS.lastIndex)

/** Le rang du ressenti d'une session, ou null si elle n'en a pas. Lit les anciennes étoiles. */
fun feelIndexOf(rating: Int): Int? = when {
    rating >= FEEL_BASE -> (rating - FEEL_BASE).takeIf { it in SESSION_FEELS.indices }
    rating in 1..5 -> rating // 1 étoile = Médiocre … 5 étoiles = Parfait
    else -> null
}

/** Une session de référence pour la ressemblance : Très bon ou Parfait (comme 4 et 5 étoiles avant). */
fun isReferenceFeel(rating: Int): Boolean = feelIndexOf(rating).let { it == 4 || it == 5 }

/** Le mot d'un ressenti. */
fun feelLabel(rating: Int): String? = feelIndexOf(rating)?.let { SESSION_FEELS[it].first }

/** La couleur du mot de la note [band] : éclaircie sur fond sombre, la couleur de la bande sur fond clair. */
@Composable
fun bandWordColor(band: ScoreBand): Color = if (isDarkSurfaceTheme()) band.textColor() else band.color().let {
    when (band) {
        ScoreBand.FAIR -> Color(0xFFB58900)
        ScoreBand.VERY_GOOD -> Color(0xFF0F8A52)
        else -> it
    }
}

/** La note prévue pour une session : la note moyenne (0-100, -1 = trop gros) en mot, « Correct à Bon » près d'une limite. */
fun forecastLabel(score: Int): String =
    if (score < 0) tr("Gros jour") else bandLabelWithTrend(SlotRating(score, false))

fun forecastBand(score: Int): ScoreBand = conditionBand(SlotRating(score.coerceAtLeast(0), score < 0))
