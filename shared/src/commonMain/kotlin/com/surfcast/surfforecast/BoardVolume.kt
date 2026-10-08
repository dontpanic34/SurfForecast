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

/** Fourchette de ratio L/kg par profil (repères de boutiques, adulte en forme) : [min, max]. */
fun ratioRangeFor(level: String): ClosedFloatingPointRange<Double> = when (level) {
    "beginner" -> 0.58..0.80
    "intermediate" -> 0.43..0.58
    "confirmed" -> 0.35..0.43
    else -> 0.28..0.35
}

/** Volume (L) correspondant à un profil pour ce poids : fourchette min-max. */
fun volumeRangeFor(level: String, weightKg: Int): ClosedFloatingPointRange<Double> {
    val r = ratioRangeFor(level)
    return (r.start * weightKg)..(r.endInclusive * weightKg)
}

/** Profil que suggère un ratio L/kg (repère indicatif, pas un jugement de niveau). */
fun levelForRatio(ratio: Double): String = when {
    ratio >= 0.58 -> "beginner"
    ratio >= 0.43 -> "intermediate"
    ratio >= 0.35 -> "confirmed"
    else -> "expert"
}

fun levelLabel(level: String): String = when (level) {
    "beginner" -> "Débutant"
    "intermediate" -> "Intermédiaire"
    "confirmed" -> "Confirmé"
    "expert" -> "Expert"
    else -> "Personnalisé"
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

// --- Coefficient selon le type de planche, volume recommandé, curseurs de cotes ---

/** Coefficient de remplissage selon la famille : une planche plus « pleine » (fish, mid-length, longboard) a un coefficient plus haut. */
fun fillFactorFor(family: String): Double = when (family) {
    "shortboard" -> VOLUME_FILL_FACTOR
    "groveler", "twin", "fish" -> 0.58
    "mid-length" -> 0.60
    "longboard", "mousse" -> 0.62
    else -> VOLUME_FILL_FACTOR
}

/** Planche « longue » : le volume recommandé est plus élevé que pour une planche courte. */
fun isLongFamily(family: String): Boolean = family == "longboard" || family == "mousse" || family == "mid-length"

/** Profils du calcul de volume : libellé et litres par kilo (adulte de 60 kg et plus, en forme). */
val VOLUME_LEVELS: List<Pair<String, Double>> = listOf(
    "Débutant" to 0.68,
    "Débutant / Interm." to 0.54,
    "Intermédiaire" to 0.42,
    "Interm. / Confirmé" to 0.38,
    "Confirmé" to 0.35,
    "Expert" to 0.31
)

/** Forme physique : libellé et coefficient (moins en forme = plus de volume). */
val FITNESS_LEVELS: List<Pair<String, Double>> = listOf(
    "Excellente" to 1.0,
    "Bonne" to 1.05,
    "Moyenne" to 1.10,
    "Faible" to 1.18
)

fun ageFactor(ageYears: Int): Double = when {
    ageYears <= 30 -> 1.0
    ageYears <= 50 -> 1.08
    else -> 1.15
}

/** Les surfeurs légers ont besoin de plus de litres par kilo : +20 % à 35 kg, rien à partir de 60 kg. */
fun weightFactor(weightKg: Double): Double = 1.0 + 0.2 * ((60.0 - weightKg) / 25.0).coerceIn(0.0, 1.0)

/** Profil du calcul de volume proposé par défaut pour un niveau de l'appli. */
fun defaultVolumeLevel(surferLevel: String): Int = when (surferLevel) {
    "beginner" -> 0
    "confirmed" -> 4
    "expert" -> 5
    else -> 2
}

/** Informations du surfeur (facultatives sauf pour le volume recommandé) ; 0 = non renseigné, [volumeLevel] -1 = déduit du niveau. */
data class BodyState(
    val ageYears: Int = 0,
    val heightCm: Int = 0,
    val weightKg: Int = 0,
    val fitness: Int = 0,
    val volumeLevel: Int = -1
) {
    fun levelIndex(surferLevel: String): Int =
        if (volumeLevel in VOLUME_LEVELS.indices) volumeLevel else defaultVolumeLevel(surferLevel)
}

/** Volume recommandé (L) : poids x litres par kilo du profil x âge x forme x (type de planche), ou null sans poids. */
fun recommendedVolumeL(body: BodyState, levelIndex: Int, longBoard: Boolean = false): Double? {
    if (body.weightKg <= 0) return null
    val ratio = VOLUME_LEVELS[levelIndex.coerceIn(0, VOLUME_LEVELS.lastIndex)].second
    val fitness = FITNESS_LEVELS[body.fitness.coerceIn(0, FITNESS_LEVELS.lastIndex)].second
    return body.weightKg * ratio * weightFactor(body.weightKg.toDouble()) * ageFactor(body.ageYears) * fitness *
        (if (longBoard) 1.35 else 1.0)
}

/** Plage recommandée : ± 2,5 % autour du volume recommandé. */
fun recommendedRange(volumeL: Double): ClosedFloatingPointRange<Double> = (volumeL * 0.975)..(volumeL * 1.025)

/** Écart en % d'un volume par rapport au recommandé. */
fun percentFromRecommended(volumeL: Double, recommendedL: Double): Double = (volumeL / recommendedL - 1.0) * 100.0

/** Volume du tableau niveau x poids : adulte de moins de 30 ans en excellente forme, planche courte. */
fun volumeTableValue(levelIndex: Int, weightKg: Int): Double =
    weightKg * VOLUME_LEVELS[levelIndex].second * weightFactor(weightKg.toDouble())

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
