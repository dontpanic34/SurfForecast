package com.surfcast.surfforecast

import kotlinx.datetime.LocalDate
import kotlin.math.roundToInt

/** Un spot du catalogue vu par « Où surfer ? » : sa zone (sous-région), sa position, son orientation et sa distance au spot de départ. */
data class ScanSpot(
    val name: String,
    val zone: String,
    val country: String,
    val lat: Double,
    val lon: Double,
    val facing: Int?,
    val distanceKm: Int
)

/** Ce que vaut un spot un jour donné : la note du jour, ses étoiles et les conditions de sa meilleure heure. */
data class SpotDay(
    val spot: ScanSpot,
    val score: Int,
    val stars: Float,
    val heightM: Double,
    val periodS: Double,
    val windKmh: Int,
    val windDir: String
)

/** Le meilleur spot du rayon à une heure donnée (pour le détail heure par heure d'un jour). */
data class HourPick(val hour: Int, val spot: ScanSpot, val score: Int, val heightM: Double)

/** Un jour de la semaine : les étoiles ici, le meilleur spot du rayon, et le haut du classement. */
data class DayPick(
    val date: LocalDate,
    val homeStars: Float?,
    val best: SpotDay?,
    val worthMoving: Boolean,
    val top: List<SpotDay>,
    val hours: List<HourPick>
)

/** [scans] : les spots du rayon avec leurs heures (pour la carte) ; [home] : le spot de départ avec les heures de l'appli. */
data class WhereResult(
    val radiusKm: Int,
    val days: List<DayPick>,
    val spotsScanned: Int,
    val scans: List<Pair<ScanSpot, List<HourlyUiModel>>> = emptyList(),
    val home: Pair<ScanSpot, List<HourlyUiModel>>? = null
)

/** État de l'écran « Où surfer ? ». */
sealed class WhereState {
    object Idle : WhereState()
    data class Loading(val radiusKm: Int) : WhereState()
    data class Ready(val result: WhereResult) : WhereState()
    data class Failed(val message: String) : WhereState()
}

object WhereToSurf {
    val RADII = listOf(50, 100, 300, 500)
    const val DEFAULT_RADIUS = 300

    /** Spots interrogés au plus (un appel par lot de 25) : au-delà, on garde les plus proches. */
    const val MAX_POINTS = 60

    /** Spots gardés par zone : assez pour couvrir plusieurs orientations de plage sans tout interroger. */
    private const val PER_ZONE = 3

    /** Heures de jour retenues pour noter une journée (le lever et le coucher exacts ne comptent pas ici). */
    private val DAY_HOURS = 7..19

    /** Un jour « vaut le déplacement » si ailleurs est au moins à [MOVE_MIN_STARS] et gagne [MOVE_GAIN_STARS] sur ici. */
    const val MOVE_MIN_STARS = 3.0f
    const val MOVE_GAIN_STARS = 1.0f

    /** L'entrée « Où surfer ? » apparaît d'elle-même quand au moins [NEEDED_DAYS] jours de la semaine sont sous [LOW_STARS] étoiles. */
    const val LOW_STARS = 2.0f
    const val NEEDED_DAYS = 3

    fun needsWhereToSurf(stars: List<Float?>): Boolean = stars.count { it != null && it < LOW_STARS } >= NEEDED_DAYS

    /**
     * Les spots du catalogue à moins de [radiusKm] du point de départ, au plus [PER_ZONE] par zone (les plus proches d'abord), sans le spot
     * de départ lui-même (l'appli le connaît déjà). Au plus [MAX_POINTS] au total.
     */
    fun spotsAround(lat: Double, lon: Double, radiusKm: Int, excludeName: String?): List<ScanSpot> {
        val found = ArrayList<ScanSpot>()
        for (country in SurfDatabase.countries) for (region in country.regions) for (sub in region.subRegions) {
            sub.spots.asSequence()
                .filter { it.latitude != 0.0 && !it.name.equals(excludeName, ignoreCase = true) }
                .map { ScanSpot(it.name, sub.name, country.name, it.latitude, it.longitude, it.idealSwellDirection, distanceKm(lat, lon, it.latitude, it.longitude).roundToInt()) }
                .filter { it.distanceKm <= radiusKm }
                .sortedBy { it.distanceKm }
                .take(PER_ZONE)
                .forEach { found.add(it) }
        }
        return found.sortedBy { it.distanceKm }.take(MAX_POINTS)
    }

    private fun dayHours(hours: List<HourlyUiModel>, date: LocalDate): List<HourlyUiModel> {
        val grouped = hours.groupBy { it.rawTime.date }
        return hoursWithWindMemory(date, grouped).filter { it.rawTime.hour in DAY_HOURS }
    }

    /** La journée d'un spot (profil neutre, comme les étoiles de la vue semaine) ; null sans assez d'heures ou trop gros toute la journée. */
    fun spotDay(spot: ScanSpot, hours: List<HourlyUiModel>, date: LocalDate): SpotDay? {
        val day = dayHours(hours, date)
        if (day.size < 6) return null
        val score = dayQualityScore(day, spot.facing, NEUTRAL_LEVEL, null) ?: return null
        val bestHour = day.maxByOrNull { calculateSlotRating(it, spot.facing, NEUTRAL_LEVEL, bandShift = 0).score } ?: return null
        return SpotDay(spot, score, starsForScore(score, 0), bestHour.waveHeight, bestHour.wavePeriod, bestHour.windSpeedKmh, bestHour.windDirectionStr)
    }

    /**
     * Rassemble le tout : pour chaque jour de [homeStars] (les étoiles de la vue semaine), le meilleur spot du rayon, ce que ça vaut par rapport
     * à ici, les cinq meilleurs et le meilleur spot de chaque heure.
     */
    fun analyse(
        radiusKm: Int,
        homeStars: Map<LocalDate, Float?>,
        scans: List<Pair<ScanSpot, List<HourlyUiModel>>>,
        home: Pair<ScanSpot, List<HourlyUiModel>>? = null
    ): WhereResult {
        val days = homeStars.keys.sorted().take(7).map { date ->
            val perSpot = scans.mapNotNull { (spot, hours) -> spotDay(spot, hours, date) }.sortedByDescending { it.score }
            val best = perSpot.firstOrNull()
            val here = homeStars[date]
            val worth = best != null && best.stars >= MOVE_MIN_STARS && (here == null || best.stars - here >= MOVE_GAIN_STARS)
            val hours = (6..21).mapNotNull { h ->
                scans.mapNotNull { (spot, hrs) ->
                    val hour = hrs.firstOrNull { it.rawTime.date == date && it.rawTime.hour == h } ?: return@mapNotNull null
                    val rating = calculateSlotRating(hour, spot.facing, NEUTRAL_LEVEL, bandShift = 0)
                    if (rating.tooBig) null else HourPick(h, spot, rating.score, hour.waveHeight)
                }.maxByOrNull { it.score }
            }
            DayPick(date, here, best, worth, perSpot.take(5), hours)
        }
        return WhereResult(radiusKm, days, scans.size, scans, home)
    }

    /** Le sens à prendre pour aller de [fromLat],[fromLon] à [toLat],[toLon], en deux mots (« au sud », « au nord-est »). */
    fun directionWord(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): String {
        val dy = toLat - fromLat
        val dx = (toLon - fromLon) * kotlin.math.cos(fromLat * kotlin.math.PI / 180.0)
        val angle = (kotlin.math.atan2(dx, dy) * 180.0 / kotlin.math.PI + 360.0) % 360.0
        return arrayOf("au nord", "au nord-est", "à l'est", "au sud-est", "au sud", "au sud-ouest", "à l'ouest", "au nord-ouest")[((angle + 22.5) / 45.0).toInt() % 8]
    }
}
