package com.surfcast.surfforecast

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
fun estimateVolumeL(lengthFeet: Double, lengthInches: Double, widthIn: Double, thicknessIn: Double): Double? {
    val length = (lengthFeet * 12.0 + lengthInches) * 2.54
    val width = widthIn * 2.54
    val thickness = thicknessIn * 2.54
    if (length <= 0.0 || width <= 0.0 || thickness <= 0.0) return null
    return length * width * thickness * VOLUME_FILL_FACTOR / 1000.0
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
