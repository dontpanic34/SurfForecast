package com.surfcast.surfforecast

import java.time.LocalTime
import java.util.Locale

// Fiche d'un banc (MicroSpot) : tidePhase / minHeight / maxHeight décrivent quand il marche.
// Ici : phase de marée à une heure donnée, et créneaux des prévisions où la fiche est respectée.

private const val NEAR_EXTREME_MINUTES = 45

private fun minutesOf(time: String?): Int? = time?.let {
    runCatching { LocalTime.parse(it) }.getOrNull()?.let { t -> t.hour * 60 + t.minute }
}

/**
 * Phase de marée à [time] : "high" / "low" (à ±45 min de l'étale), sinon "rising" (montant) ou
 * "falling" (descendant). Null si on ne connaît pas une pleine ET une basse mer ce jour-là.
 * Approximation : une seule pleine et basse mer par jour sont connues.
 */
fun tidePhaseAt(time: LocalTime, tide: DailyTideInfo?): String? {
    val high = minutesOf(tide?.highTideTime) ?: return null
    val low = minutesOf(tide?.lowTideTime) ?: return null
    val now = time.hour * 60 + time.minute

    fun gap(a: Int, b: Int): Int {
        val d = ((a - b) % 1440 + 1440) % 1440
        return minOf(d, 1440 - d)
    }
    if (gap(now, high) <= NEAR_EXTREME_MINUTES) return "high"
    if (gap(now, low) <= NEAR_EXTREME_MINUTES) return "low"

    val sinceLow = ((now - low) % 1440 + 1440) % 1440
    val lowToHigh = ((high - low) % 1440 + 1440) % 1440
    return if (sinceLow < lowToHigh) "rising" else "falling"
}

val MicroSpot.hasProfile: Boolean
    get() = tidePhase != "any" || minHeight != null || maxHeight != null

fun tidePhaseLabel(phase: String): String = when (phase) {
    "rising" -> "montant"
    "falling" -> "descendant"
    "high" -> "pleine mer"
    "low" -> "basse mer"
    else -> "toute marée"
}

/** Vrai si cette heure respecte la fiche du banc (critères non renseignés ignorés). */
fun MicroSpot.matches(hour: HourlyUiModel, tide: DailyTideInfo?): Boolean {
    if (!hasProfile) return false
    if (minHeight != null && hour.waveHeight < minHeight) return false
    if (maxHeight != null && hour.waveHeight > maxHeight) return false
    if (tidePhase != "any") {
        val phase = tidePhaseAt(hour.rawTime.toLocalTime(), tide) ?: return false
        if (phase != tidePhase) return false
    }
    return true
}

/** Créneaux consécutifs (heure de début, heure de fin incluse) où la fiche est respectée. */
fun MicroSpot.matchingWindows(hours: List<HourlyUiModel>, tide: DailyTideInfo?): List<IntRange> {
    val ok = hours.filter { matches(it, tide) }.map { it.rawTime.hour }.distinct().sorted()
    if (ok.isEmpty()) return emptyList()
    val windows = mutableListOf<IntRange>()
    var start = ok.first()
    var prev = start
    for (h in ok.drop(1)) {
        if (h != prev + 1) {
            windows += start..prev
            start = h
        }
        prev = h
    }
    windows += start..prev
    return windows
}

/** Résumé court de la fiche : « descendant · 1,0–1,6 m ». */
fun MicroSpot.profileSummary(): String {
    val parts = mutableListOf<String>()
    if (tidePhase != "any") parts += tidePhaseLabel(tidePhase)
    val min = minHeight?.let { String.format(Locale.FRANCE, "%.1f", it) }
    val max = maxHeight?.let { String.format(Locale.FRANCE, "%.1f", it) }
    when {
        min != null && max != null -> parts += "$min–$max m"
        min != null -> parts += "≥ $min m"
        max != null -> parts += "≤ $max m"
    }
    return parts.joinToString(" · ")
}
