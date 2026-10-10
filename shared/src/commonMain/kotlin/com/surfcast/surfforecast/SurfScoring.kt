@file:Suppress("SpellCheckingInspection")
package com.surfcast.surfforecast

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.LocalTime
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt

// Portage 1:1 de app/.../SurfScoring.kt (mêmes seuils, mêmes coefficients).

data class BestSlotResult(
    val startHour: Int,
    val endHour: Int,
    val averageScore: Int,
    val recap: String
)

/** Ce que dit la note d'une heure, au-delà du niveau de qualité : la raison quand ce n'est ni petit ni grand ni venté « normalement ». */
enum class ConditionKind { NORMAL, TOO_SMALL, TOO_WINDY, CHALLENGING, TOO_BIG }

/**
 * Note d'une heure : [score] 0-100, [tooBig] = trop gros pour le niveau (ce n'est pas « mauvais », c'est trop).
 * [kind] dit pourquoi quand ce n'est pas une qualité ordinaire, [why] est la phrase courte qui l'explique (« Bonne
 * taille, vent propre »), [hollow] = challengeant parce que l'offshore rend la vague trop creuse pour le profil.
 */
data class SlotRating(
    val score: Int,
    val tooBig: Boolean,
    val kind: ConditionKind = if (tooBig) ConditionKind.TOO_BIG else ConditionKind.NORMAL,
    val why: String = "",
    val hollow: Boolean = false
)

/** Courbes du facteur vent (de 0 à 1) selon la vitesse effective (km/h) : offshore, travers, onshore. */
private val windCurves: Map<String, List<Pair<Double, Double>>> = mapOf(
    "offshore" to listOf(0.0 to 1.0, 15.0 to 1.0, 25.0 to 0.75, 35.0 to 0.45, 45.0 to 0.25, 55.0 to 0.15),
    // Vent de travers (side-shore) : plus sévère qu'avant (Surf-Forecast / Yadusurf le pénalisent tôt) ; 12 km/h ≈ 0,8.
    "cross" to listOf(0.0 to 1.0, 6.0 to 1.0, 12.0 to 0.8, 20.0 to 0.5, 30.0 to 0.25, 45.0 to 0.1),
    // Onshore : adouci (sur la côte atlantique il y en a souvent) : 14 km/h reste correct, 20 km/h médiocre, 30 km/h mauvais.
    "onshore" to listOf(0.0 to 0.95, 8.0 to 0.88, 14.0 to 0.65, 20.0 to 0.42, 30.0 to 0.15, 40.0 to 0.0)
)

internal fun interpolate(x: Double, points: List<Pair<Double, Double>>): Double {
    if (x <= points.first().first) return points.first().second
    for (i in 1 until points.size) {
        val (x0, y0) = points[i - 1]
        val (x1, y1) = points[i]
        if (x <= x1) return y0 + (y1 - y0) * (x - x0) / (x1 - x0)
    }
    return points.last().second
}

/**
 * Profil de surfeur : tout ce qui change la note d'une heure selon qui surfe.
 * Énergie (kJ) : [idealMin] = à partir de quand « ça ouvre » (0,8 m à 9 s ≈ 100) ; entre [idealMin] et [rampEnd], la
 * note monte de [rampStartFit] (environ 60) à 1 : un petit jour propre est « Bon », pas « Parfait ». [idealMax] = seuil
 * de confort : au-delà, c'est « challengeant ». [cap] = ton maximum : un peu au-delà (x [TOO_BIG_MARGIN]) c'est « trop
 * gros » ([NO_CAP] = jamais trop gros).
 * Tolérances : [minPeriod] (s), [windTolerance] (km/h, offshore et travers), [onshoreMax] (km/h),
 * [gustThreshold] (km/h, au-delà les rafales pénalisent), [chopThreshold] (m de mer de vent).
 * Notes perso : [scoreOffset] décale toutes les notes (en points), [comfortScale] déplace le seuil « challengeant ».
 */
data class SurfProfile(
    val idealMin: Double,
    val idealMax: Double,
    val cap: Double,
    val rampStartFit: Double = 0.6,
    val rampEnd: Double = idealMin * RAMP_FACTOR,
    val minPeriod: Double = 7.0,
    val windTolerance: Double = 25.0,
    val onshoreMax: Double = 12.0,
    val gustThreshold: Double = 25.0,
    val chopThreshold: Double = 0.3,
    val beginnerExtras: Boolean = false,
    // Importance de la direction du vent (0 = seule la force compte, 1 = offshore / travers / onshore comptent à plein).
    val directionMatters: Double = 1.0,
    // Ancien réglage (offshore soutenu), gardé pour relire les profils enregistrés : sans effet.
    val offshoreMinFactor: Double = 1.0,
    // Les rafales comptent dans la note (la moitié de leur écart avec le vent moyen).
    val countGusts: Boolean = true,
    val scoreOffset: Int = 0,
    val comfortScale: Double = 1.0
) {
    val hasCap: Boolean get() = cap < NO_CAP

    /** Forme texte stockée dans le niveau : "custom:" + 16 nombres séparés par « ; ». */
    fun serialize(): String = "$CUSTOM_PREFIX" + listOf(
        idealMin, idealMax, cap, rampStartFit, rampEnd, minPeriod, windTolerance, onshoreMax,
        gustThreshold, chopThreshold, if (beginnerExtras) 1.0 else 0.0, directionMatters, offshoreMinFactor,
        if (countGusts) 1.0 else 0.0, scoreOffset.toDouble(), comfortScale
    ).joinToString(";")

    companion object {
        const val NO_CAP = 1e9
        const val CUSTOM_PREFIX = "custom:"
        /** Plafond « trop gros » minimal par rapport au minimum d'énergie. */
        const val MIN_CAP_RATIO = 1.5
        /** Où la note atteint son maximum : 2,5 fois l'énergie minimale. */
        const val RAMP_FACTOR = 2.5
        /** Au-delà de [cap] x cette marge, c'est « trop gros » ; entre les deux, la note baisse progressivement. */
        const val TOO_BIG_MARGIN = 1.3

        /**
         * Les 4 profils de départ. Le niveau ne se résume pas à l'énergie, et tout ce qui dégrade la vague (clapot,
         * onshore, rafales, période courte) ne pèse pas pareil : un expert cherche le plein potentiel de la vague et
         * y est donc le plus sensible ; un débutant qui prend de la mousse s'en moque presque.
         */
        fun preset(level: String): SurfProfile = when (level) {
            // Débutant : les mousses, les petites vagues douces ; la qualité de la vague compte peu (max 1,3 m à 10 s).
            "beginner" -> SurfProfile(31.0, 200.0, 330.0, minPeriod = 5.0, windTolerance = 30.0, onshoreMax = 20.0,
                gustThreshold = 30.0, chopThreshold = 0.6, beginnerExtras = true, directionMatters = 0.25)
            // Intermédiaire : commence à aller au large et à suivre les vagues (max 1,7 m à 10 s).
            "intermediate" -> SurfProfile(100.0, 340.0, 570.0, minPeriod = 6.0, windTolerance = 25.0, onshoreMax = 14.0,
                gustThreshold = 25.0, chopThreshold = 0.3, directionMatters = 0.8)
            // Confirmé : autonome, surfe seul, préfère un peu de puissance et de la vague propre (max 2,0 m à 11 s).
            "confirmed" -> SurfProfile(100.0, 570.0, 950.0, minPeriod = 7.0,
                windTolerance = 25.0, onshoreMax = 12.0, gustThreshold = 25.0, chopThreshold = 0.2)
            // Expert : plein potentiel de la vague, pas de limite de taille, mais le plus exigeant sur la qualité.
            "expert" -> SurfProfile(100.0, 1300.0, NO_CAP, minPeriod = 8.0,
                windTolerance = 20.0, onshoreMax = 10.0, gustThreshold = 20.0, chopThreshold = 0.15)
            else -> preset("intermediate")
        }

        /** Profil d'un niveau : « beginner »… « expert » ou « custom:… » (profil personnalisé). */
        fun fromLevel(level: String): SurfProfile {
            if (level.startsWith(CUSTOM_PREFIX)) {
                val v = level.removePrefix(CUSTOM_PREFIX).split(";").mapNotNull { it.toDoubleOrNull() }
                // 11 nombres = ancien format (sans direction du vent), 13 = avant les notes perso : les réglages
                // manquants gardent leur valeur par défaut.
                if (v.size == 11 || v.size == 13 || v.size == 16) {
                    // Un maximum au ras du minimum rendrait « trop gros » presque tous les jours : marge d'au moins 50 %.
                    val cap = if (v[2] >= NO_CAP) v[2] else maxOf(v[2], v[0] * MIN_CAP_RATIO)
                    return SurfProfile(
                        v[0], v[1], cap, v[3], v[4], v[5], v[6], v[7], v[8], v[9], v[10] > 0.5,
                        directionMatters = v.getOrElse(11) { 1.0 }, offshoreMinFactor = v.getOrElse(12) { 1.0 },
                        countGusts = v.getOrElse(13) { 1.0 } > 0.5,
                        scoreOffset = v.getOrElse(14) { 0.0 }.roundToInt(),
                        comfortScale = v.getOrElse(15) { 1.0 }
                    )
                }
                return preset("intermediate")
            }
            return preset(level)
        }
    }
}

/** Énergie (kJ) d'une houle de face : 1,962 x hauteur² x période². Ex. 1,5 m à 10 s ≈ 441. */
fun waveEnergyKj(heightM: Double, periodS: Double): Double = 1.962 * heightM * heightM * periodS * periodS

/** Hauteur (m) qui donne cette énergie pour une période donnée : l'inverse de [waveEnergyKj]. */
fun heightForEnergy(energyKj: Double, periodS: Double): Double = kotlin.math.sqrt(energyKj / (1.962 * periodS * periodS))

/** Zone d'une énergie pour un profil : 0 = sous le minimum, 1 = dans la zone idéale, 2 = challengeant (au-dessus du confort), 3 = trop gros. */
fun energyZone(energyKj: Double, profile: SurfProfile): Int = when {
    profile.hasCap && energyKj > profile.cap * SurfProfile.TOO_BIG_MARGIN -> 3
    energyKj > profile.idealMax * profile.comfortScale -> 2
    energyKj >= profile.idealMin -> 1
    else -> 0
}

fun isCustomLevel(level: String) = level.startsWith(SurfProfile.CUSTOM_PREFIX)

private fun profileFor(level: String) = SurfProfile.fromLevel(level)

fun calculateSlotScore(
    hourlyModel: HourlyUiModel,
    idealSwellDirection: Int?,
    surferLevel: String,
    isHighTide: Boolean,
    tidePreference: String = "any",
    tide: DailyTideInfo? = null
): Int = calculateSlotRating(hourlyModel, idealSwellDirection, surferLevel, isHighTide, tidePreference, tide).score

/**
 * Note d'une heure de prévision (sans connaître le spot réel : bancs de sable, courants...).
 * Énergie de la houle selon le niveau, direction par rapport à la plage, vent (rafales comprises,
 * offshore / onshore selon l'orientation, atténué quand la vague est grosse) et clapot. [isHighTide], [tidePreference]
 * et [tide] sont ignorés : la marée n'entre plus dans la note.
 */
@Suppress("UNUSED_PARAMETER")
fun calculateSlotRating(
    hourlyModel: HourlyUiModel,
    idealSwellDirection: Int?,
    surferLevel: String,
    isHighTide: Boolean = false,
    tidePreference: String = "any",
    tide: DailyTideInfo? = null
): SlotRating {
    val h = hourlyModel.waveHeight
    val t = hourlyModel.wavePeriod
    val profile = profileFor(surferLevel)
    if (h < 0.4) return SlotRating(0, false, ConditionKind.TOO_SMALL, WHY_TOO_SMALL)

    // idealSwellDirection = orientation de la plage : elle définit aussi offshore / onshore.
    val windCategory = windCategoryFor(hourlyModel.windDirectionStr, idealSwellDirection)

    val directionDiff = idealSwellDirection?.let {
        angularDifference(hourlyModel.waveDirection.toDouble(), it.toDouble())
    } ?: 0.0
    val coeffDirection = if (directionDiff >= 90.0) 0.0 else cos(directionDiff * PI / 180.0)

    val energyKj = waveEnergyKj(h, t) * coeffDirection
    // Un offshore creuse la vague (plus puissante, plus creuse), un vent de mer l'écrase : à taille égale, la vague
    // « pèse » plus ou moins pour le surfeur.
    val powerFactor = when (windCategory) {
        "offshore" -> if (hourlyModel.windSpeedKmh >= 10) 1.25 else 1.1
        "onshore" -> 0.85
        else -> 1.0
    }
    val felt = energyKj * powerFactor
    val capEnd = profile.cap * SurfProfile.TOO_BIG_MARGIN
    if (profile.hasCap && energyKj > capEnd) return SlotRating(0, true, ConditionKind.TOO_BIG, WHY_TOO_BIG)
    // Pas plus gros que le maximum, mais l'offshore le rend trop creux pour ce profil : médiocre et challengeant.
    if (profile.hasCap && felt > capEnd) return SlotRating(30, false, ConditionKind.CHALLENGING, WHY_TOO_HOLLOW, hollow = true)

    val fit = when {
        energyKj < profile.idealMin -> profile.rampStartFit * (energyKj / profile.idealMin).coerceIn(0.0, 1.0)
        energyKj < profile.rampEnd && profile.rampEnd > profile.idealMin ->
            profile.rampStartFit + (1.0 - profile.rampStartFit) * (energyKj - profile.idealMin) / (profile.rampEnd - profile.idealMin)
        else -> 1.0
    }

    var score = fit * 100.0
    // Un peu au-delà du maximum : la note baisse progressivement jusqu'à « trop gros ».
    if (profile.hasCap && felt > profile.cap) score *= ((capEnd - felt) / (capEnd - profile.cap)).coerceIn(0.0, 1.0)

    // Clapot (mer de vent) : une houle propre coiffée de clapot est moins bonne qu'une houle seule.
    val chopF = chopFactor(h, hourlyModel.windWaveHeight, profile.chopThreshold)
    score *= chopF
    // Vent « ressenti » sur l'eau : les rafales comptent à moitié. Plus la vague est grosse, moins le vent la gêne
    // (les faces sont assez hautes pour que 16 km/h restent surfables à 1,5 m).
    val windKmh = effectiveWindKmh(hourlyModel, if (profile.countGusts) GUST_WEIGHT else 0.0) / windSizeDivisor(h)
    // La tolérance au vent décale les courbes : un profil tolérant « voit » moins de vent.
    val scaledWind = if (windCategory == "onshore") windKmh * 12.0 / profile.onshoreMax else windKmh * 25.0 / profile.windTolerance
    val categoryFactor = interpolate(scaledWind, windCurves.getValue(windCategory))
    // Direction : un débutant (mousse) s'en moque, un confirmé / expert cherche l'offshore qui creuse la vague.
    val neutralFactor = interpolate(windKmh * 25.0 / profile.windTolerance, windCurves.getValue("offshore"))
    // L'onshore, lui, abîme la vague pour tout le monde (tolérance [SurfProfile.onshoreMax]) : seule la différence
    // offshore / travers est modulée par le niveau.
    val windFactor = if (windCategory == "onshore") categoryFactor
    else neutralFactor + (categoryFactor - neutralFactor) * profile.directionMatters
    score *= windFactor
    // Rafales fortes : mauvaises pour tout le monde, quelle que soit la direction.
    val gust = hourlyModel.windGustKmh
    val gustF = if (profile.countGusts && gust > profile.gustThreshold) (1.0 - (gust - profile.gustThreshold) / 30.0).coerceAtLeast(0.3) else 1.0
    score *= gustF
    // Période trop courte pour le profil : houle de clapot, vagues moins organisées.
    val periodF = if (t < profile.minPeriod) (t / profile.minPeriod).coerceAtLeast(0.3) else 1.0
    score *= periodF
    // Houle de travers : en plus de l'énergie réduite, la vague est moins bien formée.
    val directionF = 1.0 - 0.3 * minOf(directionDiff, 90.0) / 90.0
    score *= directionF

    if (profile.beginnerExtras) {
        if (h > 1.5) score *= 0.7
        if (t > 13.0) score *= 0.8
    }

    val final = (score + profile.scoreOffset).roundToInt().coerceIn(0, 100)
    val kind = when {
        energyKj < profile.idealMin * TOO_SMALL_RATIO -> ConditionKind.TOO_SMALL
        windFactor < TOO_WINDY_FACTOR -> ConditionKind.TOO_WINDY
        felt > profile.idealMax * profile.comfortScale -> ConditionKind.CHALLENGING
        else -> ConditionKind.NORMAL
    }
    val limits = ScoreLimits(fit, windFactor, gustF, chopF, periodF, directionF)
    return SlotRating(final, false, kind, whyFor(kind, windCategory, limits))
}

/** Les rafales comptent à moitié de leur écart avec le vent moyen. */
const val GUST_WEIGHT = 0.5
private const val TOO_SMALL_RATIO = 0.85
private const val TOO_WINDY_FACTOR = 0.4
const val WHY_TOO_SMALL = "Trop petit pour toi"
const val WHY_TOO_BIG = "Trop gros pour toi"
const val WHY_TOO_HOLLOW = "Trop creux pour toi"

/** Diviseur du vent selon la hauteur : 1 sous 1 m, jusqu'à 1,6 pour les grosses vagues. */
private fun windSizeDivisor(heightM: Double): Double = heightM.pow(0.9).coerceIn(0.8, 1.6)

/** Les facteurs (de 0 à 1) qui ont fait la note : celui qui pèse le plus donne la raison affichée. */
private class ScoreLimits(val size: Double, val wind: Double, val gusts: Double, val chop: Double, val period: Double, val direction: Double)

private const val MINOR_LIMIT = 0.85
private const val STRONG_LIMIT = 0.55

/**
 * Phrase courte qui dit pourquoi la note est ce qu'elle est (voir les maquettes du profil) : ce qui pèse vraiment
 * (taille, vent, rafales, clapot, période, direction de la houle), ou « Bonne taille, vent propre » quand rien ne pèse.
 */
private fun whyFor(kind: ConditionKind, windCategory: String, l: ScoreLimits): String = when (kind) {
    ConditionKind.TOO_SMALL -> WHY_TOO_SMALL
    ConditionKind.TOO_WINDY -> when (windCategory) {
        "onshore" -> "Onshore abîme la vague"
        "cross" -> "Sideshore abîme la vague"
        else -> "Offshore trop fort"
    }
    ConditionKind.TOO_BIG -> WHY_TOO_BIG
    ConditionKind.CHALLENGING -> when (windCategory) {
        "offshore" -> "Creux et puissant"
        "onshore" -> "Gros, accessible"
        else -> "Gros, vent de côté"
    }
    ConditionKind.NORMAL -> {
        val factors = listOf("size" to l.size, "wind" to l.wind, "gusts" to l.gusts, "chop" to l.chop, "period" to l.period, "direction" to l.direction)
        val worst = factors.minByOrNull { it.second }!!
        if (worst.second >= MINOR_LIMIT) {
            if (l.wind >= 0.9) "Bonne taille, vent propre" else "Bonne taille, vent léger"
        } else {
            val strong = worst.second < STRONG_LIMIT
            when (worst.first) {
                "size" -> if (strong) "Petite pour toi" else "Un peu petite pour toi"
                "wind" -> when (windCategory) {
                    "onshore" -> if (strong) "Onshore, gâche la vague" else "Un peu de vent de mer"
                    "cross" -> if (strong) "Vent de côté fort" else "Vent de côté qui gêne"
                    else -> if (strong) "Offshore trop fort" else "Offshore un peu fort"
                }
                "gusts" -> if (strong) "Rafales fortes" else "Des rafales gênent"
                "chop" -> if (strong) "Beaucoup de clapot" else "Un peu de clapot"
                "period" -> if (strong) "Houle trop courte" else "Houle un peu courte"
                else -> "Houle de biais"
            }
        }
    }
}

/** Vent pris en compte : vent moyen + [gustWeight] x l'écart avec les rafales (la moitié par défaut). */
fun effectiveWindKmh(hourly: HourlyUiModel, gustWeight: Double = GUST_WEIGHT): Double {
    val wind = hourly.windSpeedKmh.toDouble()
    val gust = hourly.windGustKmh.toDouble()
    return if (gust > wind) wind + gustWeight * (gust - wind) else wind
}

/**
 * Facteur (0.4 à 1) lié au clapot : 1 sous 0,3 m de mer de vent, puis baisse avec le rapport
 * clapot / houle (clapot de 0,6 m sur 1,2 m de houle : environ 0,8 ; égal à la houle : 0,4).
 */
fun chopFactor(swellHeight: Double, windWaveHeight: Double, threshold: Double = 0.3): Double {
    if (windWaveHeight <= threshold) return 1.0
    val ratio = (windWaveHeight - threshold) / maxOf(swellHeight, 0.5)
    return (1.0 - ratio * 0.8).coerceIn(0.4, 1.0)
}

/**
 * Vrai si le vent a soufflé offshore (et pas trop fort) pendant la nuit : la mer est alors lisse et les vagues
 * mieux formées le matin. [nightHours] = soirée de la veille et début de matinée ; il en faut au moins 4, offshore à 60 %.
 */
fun hasOffshoreNight(nightHours: List<HourlyUiModel>, beachFacing: Int?): Boolean {
    if (nightHours.size < 4 || beachFacing == null) return false
    val offshore = nightHours.count {
        it.windSpeedKmh in 2..30 && windCategoryFor(it.windDirectionStr, beachFacing) == "offshore"
    }
    return offshore >= nightHours.size * 0.6
}

/**
 * La mer garde en mémoire le vent qui vient de souffler : 30 km/h à 8 h, ce n'est pas « glacé » à 9 h. Pour la
 * NOTATION (pas pour l'affichage), le vent d'une heure est au moins [keep] x celui de l'heure précédente (et garde sa
 * direction) : 30 km/h de vent de mer donne environ 22, 17, 13 km/h les heures suivantes, même si le vent est tombé.
 * [hours] doit couvrir assez de temps avant la période notée pour amorcer la mémoire.
 */
fun windMemoryAdjusted(hours: List<HourlyUiModel>, keep: Double = 0.75): List<HourlyUiModel> {
    var memSpeed = 0.0
    var memGust = 0.0
    var memDir = ""
    return hours.sortedBy { it.rawTime }.map { h ->
        val speed = h.windSpeedKmh.toDouble()
        val decayedSpeed = memSpeed * keep
        val decayedGust = memGust * keep
        if (speed >= decayedSpeed) {
            memSpeed = speed
            memDir = h.windDirectionStr
        } else {
            memSpeed = decayedSpeed
        }
        memGust = maxOf(h.windGustKmh.toDouble(), decayedGust)
        if (memSpeed.roundToInt() == h.windSpeedKmh && memGust.roundToInt() == h.windGustKmh && memDir == h.windDirectionStr) h
        else h.copy(windSpeedKmh = memSpeed.roundToInt(), windGustKmh = memGust.roundToInt(), windDirectionStr = memDir)
    }
}

/**
 * Les heures de [date] avec la mémoire du vent appliquée (amorcée par l'après-midi / la soirée de la veille).
 * À utiliser pour NOTER ; l'affichage garde le vent réel.
 */
fun hoursWithWindMemory(date: LocalDate, grouped: Map<LocalDate, List<HourlyUiModel>>): List<HourlyUiModel> {
    val previous = grouped[date.minus(1, DateTimeUnit.DAY)].orEmpty().filter { it.rawTime.hour >= 12 }
    val today = grouped[date].orEmpty()
    val adjusted = windMemoryAdjusted(previous + today)
    val dates = today.map { it.rawTime }.toSet()
    return adjusted.filter { it.rawTime in dates }
}

/**
 * Qualité d'une JOURNÉE (0-100) : « bonne journée ou pas ? ». Moyenne de la meilleure fenêtre du MATIN et de la
 * meilleure de l'APRÈS-MIDI (un jour bon la moitié du temps vaut la moitié), plus 5 points si le vent a soufflé offshore
 * la nuit. Passer des heures déjà corrigées par [windMemoryAdjusted]. Null = trop gros pour le profil toute la
 * journée, ou pas de données.
 */
fun dayQualityScore(
    daylightHours: List<HourlyUiModel>,
    idealSwellDirection: Int?,
    surferLevel: String,
    dailyTide: DailyTideInfo?,
    tidePreference: String = "any",
    offshoreNight: Boolean = false
): Int? {
    if (daylightHours.isEmpty()) return null
    val ratings = daylightHours.map {
        calculateSlotRating(it, idealSwellDirection, surferLevel, isNearHighTide(it, dailyTide), tidePreference, dailyTide)
    }
    if (ratings.all { it.tooBig }) return null
    val slots = findBestSlotsOfDay(daylightHours, idealSwellDirection, surferLevel, dailyTide, tidePreference)
    val halves = listOf(slots.morning, slots.afternoon).map { it?.averageScore }
    // Une moitié sans créneau jouable compte 0 ; si une moitié n'a pas de données (jour en cours), on ne la compte pas.
    val hasMorning = daylightHours.any { it.rawTime.hour < 13 }
    val hasAfternoon = daylightHours.any { it.rawTime.hour >= 13 }
    val parts = buildList {
        if (hasMorning) add(halves[0] ?: 0)
        if (hasAfternoon) add(halves[1] ?: 0)
    }
    var day = if (parts.isEmpty()) 0.0 else parts.average()
    if (offshoreNight && day > 0) day += 5.0
    return day.roundToInt().coerceIn(0, 100)
}

/**
 * La meilleure heure d'une journée (celle de plus haute note), pour le libellé du jour. Null sans données ;
 * « trop gros » (tooBig) si toutes les heures le sont.
 */
fun bestRatingOfDay(daylightHours: List<HourlyUiModel>, idealSwellDirection: Int?, surferLevel: String): SlotRating? {
    if (daylightHours.isEmpty()) return null
    val ratings = daylightHours.map { calculateSlotRating(it, idealSwellDirection, surferLevel) }
    return ratings.filter { !it.tooBig }.maxByOrNull { it.score } ?: ratings.first()
}

/** Les teintes de la journée dans l'ordre des horaires (matin, milieu, après-midi), sans doublon consécutif : 1 à 3 bandes. */
fun dayBandsInTimeOrder(daylightHours: List<HourlyUiModel>, idealSwellDirection: Int?, surferLevel: String): List<ScoreBand> {
    if (daylightHours.isEmpty()) return emptyList()
    val ratings = daylightHours.sortedBy { it.rawTime }.map { calculateSlotRating(it, idealSwellDirection, surferLevel) }
    val n = ratings.size
    val chunks = if (n >= 3) 3 else n
    val bands = (0 until chunks).map { c ->
        val part = ratings.subList(c * n / chunks, (c + 1) * n / chunks)
        val best = part.filter { !it.tooBig }.maxByOrNull { it.score } ?: part.first()
        conditionBand(best)
    }
    return bands.fold(emptyList()) { acc, b -> if (acc.lastOrNull() == b) acc else acc + b }
}

fun findBestSlot(
    dailyHours: List<HourlyUiModel>,
    idealSwellDirection: Int?,
    surferLevel: String,
    dailyTide: DailyTideInfo?,
    tidePreference: String = "any"
): BestSlotResult? {
    if (dailyHours.size < 2) return null

    val sorted = dailyHours.sortedBy { it.rawTime }
    val scores = sorted.map { hourly ->
        calculateSlotScore(hourly, idealSwellDirection, surferLevel, isNearHighTide(hourly, dailyTide), tidePreference, dailyTide)
    }

    var best: BestSlotResult? = null
    var bestAvg = Double.NEGATIVE_INFINITY

    for (windowSize in listOf(3, 2)) {
        if (sorted.size < windowSize) continue
        for (start in 0..(sorted.size - windowSize)) {
            val window = scores.subList(start, start + windowSize)
            val avg = window.average()
            if (avg <= 0.0) continue
            if (best == null || avg > bestAvg) {
                val windowHours = sorted.subList(start, start + windowSize)
                bestAvg = avg
                best = BestSlotResult(
                    startHour = sorted[start].rawTime.hour,
                    endHour = sorted[start + windowSize - 1].rawTime.hour,
                    averageScore = avg.roundToInt(),
                    recap = buildSlotRecap(windowHours, idealSwellDirection)
                )
            }
        }
    }

    return best
}

/** Meilleurs créneaux d'un jour : un le matin (avant 13 h), un l'après-midi (13 h et après). */
data class BestSlotsOfDay(val morning: BestSlotResult?, val afternoon: BestSlotResult?)

fun findBestSlotsOfDay(
    dailyHours: List<HourlyUiModel>,
    idealSwellDirection: Int?,
    surferLevel: String,
    dailyTide: DailyTideInfo?,
    tidePreference: String = "any"
): BestSlotsOfDay = BestSlotsOfDay(
    morning = findBestSlot(dailyHours.filter { it.rawTime.hour < 13 }, idealSwellDirection, surferLevel, dailyTide, tidePreference),
    afternoon = findBestSlot(dailyHours.filter { it.rawTime.hour >= 13 }, idealSwellDirection, surferLevel, dailyTide, tidePreference)
)

fun angularDifference(a: Double, b: Double): Double {
    var diff = abs(a - b) % 360.0
    if (diff > 180.0) diff = 360.0 - diff
    return diff
}

fun windCategoryFor(directionStr: String, beachFacing: Int? = null): String {
    val dirFr = SurfUnitsHelper.formatCardinalFr(directionStr)
    val degrees = SurfUnitsHelper.cardinalToDegrees(dirFr)
    return windCategoryFromDegrees(degrees, beachFacing)
}

/**
 * [degrees] : direction d'où vient le vent. [beachFacing] : direction vers laquelle regarde la
 * plage. Vent venant de la mer (écart <= 45 degrés avec l'orientation) = onshore, venant de
 * derrière la plage (>= 135 degrés) = offshore, sinon travers. Sans orientation connue, ancienne
 * règle : plage orientée plein ouest (270).
 */
fun windCategoryFromDegrees(degrees: Float, beachFacing: Int? = null): String {
    val diff = angularDifference(degrees.toDouble(), (beachFacing ?: 270).toDouble())
    return when {
        diff >= 135.0 -> "offshore"
        diff <= 45.0 -> "onshore"
        else -> "cross"
    }
}

// Récap court des conditions attendues sur un créneau, ex : "1,0m / 10s · vent léger offshore".
private fun buildSlotRecap(windowHours: List<HourlyUiModel>, beachFacing: Int?): String {
    val avgHeight = windowHours.map { it.waveHeight }.average()
    val avgPeriod = windowHours.map { it.wavePeriod }.average()
    val avgWindKmh = windowHours.map { it.windSpeedKmh }.average().roundToInt()
    val midHour = windowHours[windowHours.size / 2]
    val windCategory = windCategoryFor(midHour.windDirectionStr, beachFacing)

    val windIntensity = when {
        avgWindKmh < 8 -> "léger"
        avgWindKmh <= 18 -> "modéré"
        else -> "fort"
    }
    val windCategoryLabel = when (windCategory) {
        "offshore" -> "offshore"
        "onshore" -> "onshore"
        else -> "side-shore"
    }

    // Virgule décimale : l'original utilisait Locale.FRANCE.
    val heightStr = formatDecimal(avgHeight, 1, decimalSeparator = ',')
    val periodStr = avgPeriod.roundToInt()

    return "${heightStr}m / ${periodStr}s · vent $windIntensity $windCategoryLabel"
}

fun isNearHighTide(hourly: HourlyUiModel, dailyTide: DailyTideInfo?): Boolean {
    val highTimeStr = dailyTide?.highTideTime ?: return false
    return try {
        val highTime = LocalTime.parse(highTimeStr)
        val hourlyTime = hourly.rawTime.time
        val highMinutes = highTime.hour * 60 + highTime.minute
        val hourlyMinutes = hourlyTime.hour * 60 + hourlyTime.minute
        val rawDiff = abs(highMinutes - hourlyMinutes)
        val diffMinutes = minOf(rawDiff, 1440 - rawDiff)
        diffMinutes <= 60
    } catch (e: IllegalArgumentException) {
        false
    }
}
