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

/**
 * Volume estimé (litres) depuis les cotes : longueur x largeur x épaisseur x 0,6 (coefficient moyen d'un
 * shortboard). Ordre de grandeur seulement : seul le shaper connaît le volume exact.
 */
fun estimateVolumeL(lengthFeet: Double, lengthInches: Double, widthIn: Double, thicknessIn: Double): Double? {
    val length = (lengthFeet * 12.0 + lengthInches) * 2.54
    val width = widthIn * 2.54
    val thickness = thicknessIn * 2.54
    if (length <= 0.0 || width <= 0.0 || thickness <= 0.0) return null
    return length * width * thickness * 0.6 / 1000.0
}

/** Ratio litres par kilo, ou null si une donnée manque. */
fun volumeRatio(volumeL: Double?, weightKg: Int): Double? =
    if (volumeL == null || volumeL <= 0.0 || weightKg <= 0) null else volumeL / weightKg

/** Profil que suggère un ratio L/kg (repère indicatif, pas un jugement de niveau). */
fun levelForRatio(ratio: Double): String = when {
    ratio >= 0.6 -> "beginner"
    ratio >= 0.45 -> "intermediate"
    ratio >= 0.38 -> "confirmed"
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
