package com.surfcast.surfforecast

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// Questionnaire de niveau (voir docs/maquettes/profil-questions.html et organigramme.html).
// Le niveau est la marche la plus basse parmi ce que le surfeur vise et les limites que ses réponses posent.
// Les réponses sur la forme (ressenti à la barre, entraînement, où il surfe) ne changent jamais le niveau :
// elles placent la note à l'intérieur de sa tranche. Neuf tranches égales sur 100.

/** Les neuf tranches : nom, début, fin, niveau principal (0 = Débutant … 4 = Expert). */
internal class LevelBand(val name: String, val from: Int, val to: Int, val level: Int)

internal val LEVEL_BANDS: List<LevelBand> = listOf(
    LevelBand("Débutant", 0, 16, 0), LevelBand("Débutant +", 16, 21, 0),
    LevelBand("Intermédiaire", 21, 37, 1), LevelBand("Intermédiaire +", 37, 42, 1),
    LevelBand("Confirmé", 42, 58, 2), LevelBand("Confirmé +", 58, 63, 2),
    LevelBand("Avancé", 63, 79, 3), LevelBand("Avancé +", 79, 84, 3),
    LevelBand("Expert", 84, 100, 4)
)

/** Réponses au questionnaire ; un index = la position de la réponse dans la liste d'options de l'écran. */
data class LevelAnswers(
    /** 0 = la mousse, 1 = la réforme, 2 = le large. */
    val what: Int = 2,
    /** Taille de la barre passée : 0 = pas encore … 5 = au-dessus de la tête (environ 2 m). */
    val barre: Int? = null,
    /** Take-off : 0 = un seul temps, tout le temps ; 1 = souvent ; 2 = parfois ou en plusieurs temps. */
    val takeOff: Int? = null,
    /** Où on se place : 0 = je me fie aux gens ; 1 = je repère le pic ; 2 = je sais où est le pic. */
    val peak: Int? = null,
    /** Ce qu'on cherche sur la vague (plusieurs réponses) : 0 = take-offs … 3 = affiner son surf. */
    val goals: List<Int> = emptyList(),
    /** Manœuvres (seulement si on vise « radical » ou plus) : 0 = pas là … 3 = replaque à chaque fois. */
    val maneuvers: Int? = null,
    /** Mousse ou réforme : veut passer au large ? 0 = oui bientôt, 1 = pas pour l'instant. */
    val wantLarge: Int? = null,
    /** Forme, sans effet sur le niveau : ressenti à la barre (0 = prêt … 3 = au bon moment). */
    val feel: Int? = null,
    /** Entraînement hors surf : 0 = non, 1 = de temps en temps, 2 = régulièrement. */
    val training: Int? = null,
    /** Où on surfe (plusieurs réponses) : 0 = chez moi avec du monde … 3 = surf trips. */
    val where: List<Int> = emptyList()
)

/** Résultat : niveau principal, « + », tranche, note sur 100 (jamais affichée en clair), points qui retiennent. */
data class LevelResult(
    val level: Int,
    val plus: Boolean,
    val bandIndex: Int,
    val score: Int,
    val name: String,
    /** Ce qui retient au niveau actuel, en français, pour l'encart « Pour passer à … ». */
    val holdBack: List<String>
) {
    /** Clé du niveau pour le moteur de notation : « beginner » … « expert », suivie de « + » si besoin. */
    val levelKey: String get() = LEVEL_KEYS[level] + if (plus && level < 4) "+" else ""
    val nextName: String? get() = LEVEL_BANDS.getOrNull(bandIndex + 1)?.name
}

internal val LEVEL_KEYS = listOf("beginner", "intermediate", "confirmed", "advanced", "expert")
internal val LEVEL_NAMES = listOf("Débutant", "Intermédiaire", "Confirmé", "Avancé", "Expert")

private class Limit(val value: Int, val label: String, val position: Double = value.toDouble())

private val FEEL_POINTS = doubleArrayOf(1.0, 0.6, 0.3, 0.5)
private val TRAIN_POINTS = doubleArrayOf(0.0, 0.5, 1.0)
private val WHERE_POINTS = doubleArrayOf(0.0, 0.35, 0.7, 1.0)

/**
 * Calcule le niveau. [shift] décale la tranche (−1 = une marche en dessous, +1 = une au-dessus) quand le
 * surfeur n'est pas d'accord avec le résultat. Accepte des réponses incomplètes (résultat provisoire).
 */
fun computeLevel(a: LevelAnswers, shift: Int = 0): LevelResult {
    val largeWater = a.what == 2
    val limits = ArrayList<Limit>()
    var forcePlus = false
    if (!largeWater) {
        limits += Limit(0, if (a.takeOff != null && a.takeOff != 0) "tu ne surfes que la mousse ou la réforme et ton take-off n'est pas encore sûr" else "tu ne surfes que la mousse ou la réforme")
        forcePlus = a.takeOff == 0 && a.wantLarge == 0
    } else {
        if (a.goals.isNotEmpty()) {
            val top = a.goals.max()
            limits += Limit(top + 1, "ce que tu cherches", min(4.0, top + 1 + 0.25 * (a.goals.size - 1)))
        }
        a.barre?.let { limits += Limit(intArrayOf(0, 0, 1, 2, 3, 4)[it.coerceIn(0, 5)], if (it == 0) "tu ne passes pas encore la barre" else "la taille où tu passes la barre") }
        a.takeOff?.let { limits += Limit(intArrayOf(4, 1, 0)[it.coerceIn(0, 2)], "ton take-off") }
        a.peak?.let { limits += Limit(intArrayOf(1, 2, 4)[it.coerceIn(0, 2)], "l'endroit où tu te places dans l'eau (le pic)") }
        a.maneuvers?.let { limits += Limit(intArrayOf(2, 2, 3, 4)[it.coerceIn(0, 3)], "tes manœuvres (pas encore replaquées à chaque fois)") }
    }
    val m = limits.minOfOrNull { it.value } ?: 4
    val binding = limits.filter { it.value == m }
    val baseLevel = m.coerceIn(0, 4)
    val plusBase = ((largeWater && binding.size == 1 && m <= 3) || forcePlus) && baseLevel < 4

    val formPoints = ArrayList<Double>()
    a.feel?.let { formPoints += FEEL_POINTS[it.coerceIn(0, 3)] }
    a.training?.let { formPoints += TRAIN_POINTS[it.coerceIn(0, 2)] }
    if (a.where.isNotEmpty()) {
        val best = a.where.maxOf { WHERE_POINTS[it.coerceIn(0, 3)] }
        formPoints += min(1.0, best + 0.05 * (a.where.size - 1))
    }
    val pp = if (formPoints.isEmpty()) 0.5 else formPoints.average()
    val above = if (m >= 4 || limits.isEmpty()) 1.0 else {
        val mean = limits.sumOf { it.position } / limits.size
        max(0.0, (mean - m) / (4 - m)) * 0.7 + pp * 0.3
    }

    val baseIndex = if (baseLevel == 4) 8 else baseLevel * 2 + if (plusBase) 1 else 0
    val bi = (baseIndex + shift).coerceIn(0, 8)
    val band = LEVEL_BANDS[bi]
    val span = (band.to - band.from).toDouble()
    val score = when {
        bi == 8 -> 84 + 16 * pp
        shift != 0 -> band.from + span / 2
        !largeWater -> if (forcePlus) band.from + 3.0 else band.from + intArrayOf(5, 11, 16)[(a.takeOff ?: 2).coerceIn(0, 2)] * span / 16
        else -> band.from + min(0.98, above) * span
    }
    return LevelResult(
        level = band.level, plus = bi % 2 == 1 && bi < 8, bandIndex = bi, score = score.roundToInt(),
        name = band.name, holdBack = binding.map { it.label }
    )
}

/** La question « maneuvers » ne s'affiche que si on cherche au moins à devenir radical, critique et engagé. */
fun showManeuvers(a: LevelAnswers): Boolean = a.what == 2 && a.goals.isNotEmpty() && a.goals.max() >= 2

/** Texte stocké : « what;barre;takeOff;peak;goals;maneuvers;wantLarge;feel;training;where » (listes séparées par « , », vide = pas de réponse). */
fun LevelAnswers.serialize(): String = listOf(
    what.toString(), barre?.toString() ?: "", takeOff?.toString() ?: "", peak?.toString() ?: "", goals.joinToString(","),
    maneuvers?.toString() ?: "", wantLarge?.toString() ?: "", feel?.toString() ?: "", training?.toString() ?: "", where.joinToString(",")
).joinToString(";")

/** Relit [serialize] ; null si le texte est vide ou invalide. */
fun parseLevelAnswers(text: String): LevelAnswers? {
    val p = text.split(";")
    if (p.size != 10) return null
    fun int(i: Int) = p[i].toIntOrNull()
    fun list(i: Int) = p[i].split(",").mapNotNull { it.toIntOrNull() }
    val what = int(0) ?: return null
    return LevelAnswers(
        what = what, barre = int(1), takeOff = int(2), peak = int(3), goals = list(4), maneuvers = int(5),
        wantLarge = int(6), feel = int(7), training = int(8), where = list(9)
    )
}
