package com.surfcast.surfforecast

import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * « Ce que ça donne au bord » (maquette `docs/maquettes/houle-au-bord.html`, décision du 13 novembre dans `DECISIONS.md`).
 *
 * À partir de la houle au large (hauteur, période) et du type de spot :
 * - taille des séries au bord : formule de Komar et Gaughan, calée sur l'observation du propriétaire
 *   à Montalivet (plage douce : 1,3 m à 14 s = 1,6 m au bord), puis proportionnelle au type de spot ;
 * - forme de la vague par le nombre d'Iribarren ;
 * - rythme des séries selon la période (ordres de grandeur d'observation) ;
 * - énergie par vague et par mètre de crête.
 * Les repères suivent l'usage des surfeurs, qui surfent fléchis : 1,5 m = taille d'homme, 2 m = overhead.
 */
enum class ShoreType(val label: String, val sizeFactor: Double, val slope: Double) {
    GENTLE("Plage douce", 1.00, 0.02),
    BARS("Bancs marqués", 1.16, 0.05),
    REEF("Récif, pointe", 1.25, 0.10)
}

enum class BreakerShape(val label: String, val feel: String) {
    SPILLING("vague glissante", "Vagues molles qui déroulent doucement."),
    PLUNGING("vague creuse", "Vagues creuses et puissantes."),
    SURGING("vague gonflée", "La vague gonfle et s'écrase au bord sans vraiment déferler.")
}

data class SwellAtShore(
    val seriesHeight: Double,
    val low: Double,
    val high: Double,
    val biggest: Double,
    val lull: Double,
    val wavesPerSet: IntRange,
    val minutesBetweenSets: IntRange,
    val setDurationSeconds: IntRange,
    val shape: BreakerShape,
    val swellName: String,
    val energyKj: Int
) {
    /** « Séries à la poitrine », « Séries overhead »… */
    val headline: String get() = "Séries " + SwellAtShoreCalc.bodyWord(seriesHeight)

    /** Phrase courte sous les chiffres. */
    val feel: String get() = if (minutesBetweenSets.first >= 15) "Séries rares : il faut être patient. ${shape.feel}" else shape.feel
}

object SwellAtShoreCalc {
    private const val G = 9.81
    private const val RHO = 1025.0

    private fun komarGaughan(hs: Double, t: Double): Double = 0.39 * G.pow(0.2) * (t * hs * hs).pow(0.4)

    /** Calage : à Montalivet (plage douce), 1,3 m à 14 s au large donne 1,6 m au bord. */
    val calibration: Double = 1.6 / komarGaughan(1.3, 14.0)

    private val scale = listOf(
        0.3 to "aux chevilles", 0.6 to "aux genoux", 0.8 to "à la taille", 1.1 to "à la poitrine",
        1.35 to "aux épaules", 1.7 to "taille d'homme", 2.5 to "overhead", 3.5 to "double overhead"
    )

    fun bodyWord(height: Double): String = scale.firstOrNull { height < it.first }?.second ?: "énormes, plus de 3,5 m"

    /** Type de spot par défaut : plage douce, sauf les fonds connus pour être plus raides. Modifiable dans la feuille. */
    fun defaultType(spotName: String): ShoreType = when (spotName) {
        "Hossegor", "Seignosse", "Capbreton", "Anglet", "Tarnos", "Boucau" -> ShoreType.BARS
        "Guéthary", "Mundaka", "Rodiles" -> ShoreType.REEF
        else -> ShoreType.GENTLE
    }

    fun compute(hs: Double, period: Double, type: ShoreType): SwellAtShore {
        val h = hs.coerceAtLeast(0.05)
        val t = period.coerceAtLeast(3.0)
        val hb = komarGaughan(h, t) * calibration * type.sizeFactor
        val l0 = G * t * t / (2 * PI)
        val iribarren = type.slope / sqrt(h / l0)
        val shape = when {
            iribarren < 0.5 -> BreakerShape.SPILLING
            iribarren <= 3.3 -> BreakerShape.PLUNGING
            else -> BreakerShape.SURGING
        }
        data class Rhythm(val waves: IntRange, val minutes: IntRange, val lullRatio: Double, val name: String)
        val r = when {
            t < 8 -> Rhythm(2..3, 2..4, 0.6, "mer du vent")
            t < 10 -> Rhythm(3..5, 4..7, 0.5, "houle courte")
            t < 12 -> Rhythm(4..6, 6..10, 0.45, "houle moyenne")
            t < 14 -> Rhythm(5..7, 8..12, 0.4, "houle longue")
            t < 16 -> Rhythm(6..8, 10..18, 0.3, "groundswell long")
            else -> Rhythm(6..10, 15..25, 0.25, "groundswell très long")
        }
        val energy = RHO * G * G * h * h * t * t / (32 * PI) / 1000.0
        return SwellAtShore(
            seriesHeight = hb,
            low = hb * 0.88,
            high = hb * 1.12,
            biggest = hb * 1.25,
            lull = hb * r.lullRatio,
            wavesPerSet = r.waves,
            minutesBetweenSets = r.minutes,
            setDurationSeconds = (r.waves.first * t).roundToInt()..(r.waves.last * t).roundToInt(),
            shape = shape,
            swellName = r.name,
            energyKj = energy.roundToInt()
        )
    }
}

/** Type de spot choisi par l'utilisateur, gardé par spot (sinon celui par défaut). */
object ShoreSettings {
    var store: KeyValueStore? = null

    private fun key(spotName: String) = "shore_type_$spotName"

    fun typeFor(spotName: String): ShoreType =
        store?.getString(key(spotName), null)?.let { saved -> ShoreType.entries.firstOrNull { it.name == saved } }
            ?: SwellAtShoreCalc.defaultType(spotName)

    fun setType(spotName: String, type: ShoreType) {
        store?.putString(key(spotName), type.name)
    }
}
