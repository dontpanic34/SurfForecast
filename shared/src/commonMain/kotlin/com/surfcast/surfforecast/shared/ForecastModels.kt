package com.surfcast.surfforecast.shared

import kotlinx.datetime.LocalDateTime

/**
 * Slice minimal partagé Android/iOS : un point horaire de prévision (houle + météo,
 * modèle unique). Ne reprend pas encore la fusion multi-modèles (AROME/ECMWF/MFWAM
 * court terme vs long terme) de SurfRepository côté Android — c'est la prochaine étape
 * une fois ce socle validé sur les deux plateformes.
 */
data class HourlyForecastPoint(
    val time: LocalDateTime,
    val waveHeight: Double,
    val wavePeriod: Double,
    val waveDirection: Double,
    val windSpeedKmh: Double,
    val windDirectionDeg: Double,
    val temperature: Double,
    val weatherCode: Int
)

data class SpotCoordinates(
    val latitude: Double,
    val longitude: Double
)
