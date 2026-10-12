package com.surfcast.surfforecast

import kotlin.math.roundToInt

// Volume d'une planche : lecture des cotes (décimal ou fraction, comme écrit sur la planche) et estimation.

/**
 * Lit une cote en pouces : « 19 », « 2,15 », « 2.38 », « 2 5/16 », « 19 1/4 », « 5/8 ».
 * Renvoie null si le texte n'est pas une cote, 0.0 si vide.
 */
fun parseInches(text: String): Double? {
    val t = text.trim().replace(',', '.').replace('-', ' ')
    if (t.isEmpty()) return 0.0
    val parts = t.split(Regex("\\s+"))
    return when (parts.size) {
        1 -> parseFractionOrNumber(parts[0])
        2 -> {
            val whole = parts[0].toDoubleOrNull() ?: return null
            val frac = parseFractionOrNumber(parts[1]) ?: return null
            if (!parts[1].contains('/')) null else whole + frac
        }
        else -> null
    }
}

private fun parseFractionOrNumber(part: String): Double? {
    if (!part.contains('/')) return part.toDoubleOrNull()
    val (num, den) = part.split('/').takeIf { it.size == 2 } ?: return null
    val n = num.toDoubleOrNull() ?: return null
    val d = den.toDoubleOrNull() ?: return null
    return if (d == 0.0) null else n / d
}

/** Coefficient volume / (longueur x largeur x épaisseur) : 0,56 est la moyenne de plusieurs sources (grille d'un modèle réel, planche mesurée, calculateurs de boutiques), à ±1,5 L près. */
const val VOLUME_FILL_FACTOR = 0.56

/**
 * Volume estimé (litres) depuis les cotes : longueur x largeur x épaisseur x [VOLUME_FILL_FACTOR]. Ordre de
 * grandeur à quelques % près (le rocker, les rails et la forme du nez et du tail changent le résultat) :
 * seul le shaper connaît le volume exact, à saisir quand on le connaît.
 */
fun estimateVolumeL(
    lengthFeet: Double,
    lengthInches: Double,
    widthIn: Double,
    thicknessIn: Double,
    fillFactor: Double = VOLUME_FILL_FACTOR
): Double? {
    val length = (lengthFeet * 12.0 + lengthInches) * 2.54
    val width = widthIn * 2.54
    val thickness = thicknessIn * 2.54
    if (length <= 0.0 || width <= 0.0 || thickness <= 0.0) return null
    return length * width * thickness * fillFactor / 1000.0
}

/** Ratio litres par kilo, ou null si une donnée manque. */
fun volumeRatio(volumeL: Double?, weightKg: Int): Double? =
    if (volumeL == null || volumeL <= 0.0 || weightKg <= 0) null else volumeL / weightKg

/** Libellé du niveau de l'appli : « beginner » → « Débutant », « advanced+ » → « Avancé + » ; autre texte → « Personnalisé ». */
fun levelLabel(level: String): String {
    val i = LEVEL_KEYS.indexOf(level.removeSuffix(SurfProfile.PLUS_SUFFIX))
    if (i < 0) return "Personnalisé"
    return LEVEL_NAMES[i] + if (level.endsWith(SurfProfile.PLUS_SUFFIX) && i < LEVEL_KEYS.lastIndex) " +" else ""
}

/** Nombre à la française : 31,1 (une décimale par défaut). */
fun formatFr(value: Double, decimals: Int = 1): String = formatDecimal(value, decimals, decimalSeparator = ',')

/** « 6'0 x 19 x 2 5/16 » : les cotes telles que saisies, gardées en texte dans la fiche de la planche. */
fun formatDimensions(feet: String, inches: String, width: String, thickness: String): String {
    val f = feet.trim().ifEmpty { "0" }
    val i = inches.trim().ifEmpty { "0" }
    return "$f'$i x ${width.trim()} x ${thickness.trim()}"
}

/** Résumé court d'une planche : « ≈ 31,1 L · 0,41 L/kg » (selon les données connues). */
fun boardVolumeSummary(board: QuiverBoard, weightKg: Int): String? {
    val v = board.volumeL ?: return null
    val approx = if (board.volumeEstimated) "≈ " else ""
    val ratio = volumeRatio(v, weightKg)
    return approx + formatFr(v) + " L" + (ratio?.let { " · " + formatFr(it, 2) + " L/kg" } ?: "")
}

// --- Volume recommandé (recommandation, pas une vérité : seul le shaper connaît le bon volume) ---

/** Coefficient de remplissage selon la famille : une planche plus « pleine » (fish, mid-length, longboard) a un coefficient plus haut. */
fun fillFactorFor(family: String): Double = when (family) {
    "shortboard" -> VOLUME_FILL_FACTOR
    "groveler", "twin", "fish" -> 0.58
    "mid-length" -> 0.60
    "longboard", "mousse" -> 0.62
    else -> VOLUME_FILL_FACTOR
}

/** Types proposés pour le volume recommandé : (famille, libellé). */
val RECOMMENDATION_FAMILIES = listOf("shortboard" to "Shortboard", "fish" to "Fish, twin, groveler", "mid-length" to "Mid-length", "longboard" to "Longboard", "mousse" to "Mousse")

/** Planche « longue » : le volume ne dépend plus du niveau mais de la longueur de la planche. */
fun isLongFamily(family: String): Boolean = family == "longboard" || family == "mousse" || family == "mid-length"

/**
 * Litres par kilo d'un shortboard selon le niveau (débutant, intermédiaire, confirmé, avancé, expert). Débutant à
 * confirmé : calculateurs publics ; expert : planches de pilotes pros (JS Industries, Fanning, Smith, Slater : de 0,34
 * à 0,42 L/kg selon le poids, voir docs/JOURNAL.md).
 */
val VOLUME_RATIOS: List<Double> = listOf(0.66, 0.48, 0.41, 0.37, 0.35)

/** Forme physique : libellé et coefficient (moins en forme = plus de volume). */
val FITNESS_LEVELS: List<Pair<String, Double>> = listOf(
    "Excellente" to 1.0,
    "Bonne" to 1.05,
    "Moyenne" to 1.10,
    "Faible" to 1.18
)

/** Âge : aucune majoration jusqu'à 35 ans (0 = non renseigné), puis un peu plus. Mesuré sur des pros en forme : environ +3 % à 50 ans. */
fun ageFactor(ageYears: Int): Double = when {
    ageYears <= 35 -> 1.0
    ageYears <= 50 -> 1.03
    ageYears <= 60 -> 1.07
    else -> 1.10
}

/** Les surfeurs légers ont besoin de plus de litres par kilo : +20 % à 55 kg, dégressif jusqu'à 85 kg (pilotes JS : 0,42 L/kg à 59 kg, 0,35 à 80 kg). */
fun weightFactor(weightKg: Double): Double = 1.0 + 0.2 * ((85.0 - weightKg) / 30.0).coerceIn(0.0, 1.0)

/** Rang du niveau de l'appli (« beginner »… « expert », « confirmed+ »…) dans [VOLUME_RATIOS] ; un profil perso compte comme intermédiaire. */
fun volumeLevelIndex(surferLevel: String): Int =
    LEVEL_KEYS.indexOf(surferLevel.removeSuffix(SurfProfile.PLUS_SUFFIX)).let { if (it < 0) 1 else it }

/** Informations du surfeur (facultatives sauf le poids) ; 0 = non renseigné. [fitness] = rang dans [FITNESS_LEVELS]. */
data class BodyState(
    val ageYears: Int = 0,
    val weightKg: Int = 0,
    val fitness: Int = 1
)

/** Forme physique déduite de la réponse « Tu t'entraînes en dehors du surf ? » du profil (0 non, 1 de temps en temps, 2 régulièrement ; null = inconnu). */
fun fitnessFromTraining(training: Int?): Int = when (training) {
    0 -> 2
    2 -> 0
    else -> 1
}

/**
 * Fourchette recommandée (L) pour une famille de planche, ou null sans poids (et pour la mousse). Shortboard : poids x
 * ratio du niveau x poids léger x âge x forme, à ± 5 %. Fish, twin, groveler : le shortboard + 1 à 2 L (≈ +3 % chez les
 * pilotes JS). Mid-length : 0,55 à 0,80 L/kg et longboard : 55 à 80 L et plus, sans tenir compte du niveau.
 */
fun recommendedVolumeRange(body: BodyState, levelIndex: Int, family: String = "shortboard"): ClosedFloatingPointRange<Double>? {
    if (body.weightKg <= 0) return null
    val w = body.weightKg.toDouble()
    return when (family) {
        "mousse" -> null
        "mid-length" -> (w * 0.55)..(w * 0.80)
        "longboard" -> 55.0..80.0
        else -> {
            val ratio = VOLUME_RATIOS[levelIndex.coerceIn(0, VOLUME_RATIOS.lastIndex)]
            val fitness = FITNESS_LEVELS[body.fitness.coerceIn(0, FITNESS_LEVELS.lastIndex)].second
            val base = w * ratio * weightFactor(w) * ageFactor(body.ageYears) * fitness
            val extra = if (family == "fish" || family == "twin" || family == "groveler") 1.0 to 2.0 else 0.0 to 0.0
            (base * 0.95 + extra.first)..(base * 1.05 + extra.second)
        }
    }
}

/** Un pilote pro et sa planche, d'après les fiches « team rider reference » de JS Industries (poids en kg, volume en litres). */
class ProReference(val name: String, val weightKg: Int, val volumeL: Double, val family: String)

val PRO_REFERENCES: List<ProReference> = listOf(
    ProReference("Aelan Vaast", 59, 24.5, "fish"), ProReference("Pete Devries", 67, 26.0, "fish"),
    ProReference("Ace", 73, 27.5, "fish"), ProReference("Conner Coffin", 75, 27.5, "fish"),
    ProReference("Julian Wilson", 84, 29.0, "fish"), ProReference("Parko", 87, 32.0, "fish"), ProReference("Luke", 90, 33.5, "fish"),
    ProReference("Hiroto Ohhara", 64, 24.6, "shortboard"), ProReference("Kai Hing", 68, 26.4, "shortboard"),
    ProReference("Jeremy Flores", 70, 25.6, "shortboard"), ProReference("Conner Coffin", 75, 27.0, "shortboard"),
    ProReference("Mikey Wright", 75, 27.0, "shortboard"), ProReference("Adrian Buchan", 76, 26.3, "shortboard"),
    ProReference("Julian Wilson", 80, 28.6, "shortboard"), ProReference("Joel Parkinson", 83, 28.3, "shortboard"),
    ProReference("Jason Stevenson", 88, 32.7, "shortboard"), ProReference("Mark Occhilupo", 95, 35.6, "shortboard"),
    ProReference("Harrison Roach (7'6)", 72, 50.0, "mid-length"), ProReference("Luke Egan (7'0)", 90, 48.7, "mid-length"),
    ProReference("Joel Parkinson (6'8)", 87, 38.4, "mid-length")
)

/** Les [count] pros de la famille dont le poids est le plus proche de [weightKg] ; vide pour le longboard et la mousse. */
fun nearestPros(family: String, weightKg: Int, count: Int = 3): List<ProReference> {
    val key = when (family) { "twin", "groveler", "fish" -> "fish"; "mid-length" -> "mid-length"; "shortboard" -> "shortboard"; else -> return emptyList() }
    return PRO_REFERENCES.filter { it.family == key }.sortedBy { kotlin.math.abs(it.weightKg - weightKg) }.take(count)
}

/** Volume central de la fourchette recommandée, ou null. */
fun recommendedVolumeL(body: BodyState, levelIndex: Int, family: String = "shortboard"): Double? =
    recommendedVolumeRange(body, levelIndex, family)?.let { (it.start + it.endInclusive) / 2.0 }

/** Écart en % d'un volume par rapport au recommandé. */
fun percentFromRecommended(volumeL: Double, recommendedL: Double): Double = (volumeL / recommendedL - 1.0) * 100.0

/** « 31 à 34 L » : fourchette en litres entiers ; le longboard s'écrit « 55 à 80 L et plus ». */
fun volumeRangeLabel(range: ClosedFloatingPointRange<Double>, family: String = "shortboard"): String =
    "${range.start.roundToInt()} à ${range.endInclusive.roundToInt()} L" + if (family == "longboard") " et plus" else ""

private fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)

/** Pouces en fraction depuis un nombre d'unités : (163, 8) -> « 20 3/8 », (40, 16) -> « 2 1/2 », (160, 8) -> « 20 ». */
fun formatInchesFraction(units: Int, denominator: Int): String {
    val whole = units / denominator
    val rem = units % denominator
    if (rem == 0) return whole.toString()
    val g = gcd(rem, denominator)
    return "${if (whole > 0) "$whole " else ""}${rem / g}/${denominator / g}"
}

/** Longueur en pieds et pouces : 68 pouces -> « 5'8 ». */
fun formatLengthFeet(totalInches: Int): String = "${totalInches / 12}'${totalInches % 12}"

/** Cotes lues sur les curseurs, telles qu'on les écrit sur une planche : « 5'8 x 20 3/8 x 2 1/2 ». */
fun formatSliderDimensions(lengthInches: Int, widthEighths: Int, thicknessSixteenths: Int): String =
    "${formatLengthFeet(lengthInches)} x ${formatInchesFraction(widthEighths, 8)} x ${formatInchesFraction(thicknessSixteenths, 16)}"

/** Fourchette de volume en litres entiers, ± 5 % : 28,0 -> « 27–29 ». Plus honnête qu'un chiffre unique. */
fun volumeRangeText(volumeL: Double): String = "${(volumeL * 0.95).roundToInt()}–${(volumeL * 1.05).roundToInt()}"
