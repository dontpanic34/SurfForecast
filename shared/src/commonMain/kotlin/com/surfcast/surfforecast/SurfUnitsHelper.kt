package com.surfcast.surfforecast

import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt

// Portage 1:1 de SurfUnitsHelper (app/).
object SurfUnitsHelper {

    fun formatWindValue(speedKmh: Double, unit: String): String {
        return when (unit.lowercase()) {
            "mph" -> "${(speedKmh * 0.621371).roundToInt()}"
            "noeuds", "knots", "kts" -> "${(speedKmh * 0.539957).roundToInt()}"
            "beaufort", "bft" -> "${kmhToBeaufort(speedKmh.roundToInt())}"
            else -> "${speedKmh.roundToInt()}"
        }
    }

    fun formatWindValue(speedKmh: Int, unit: String): String = formatWindValue(speedKmh.toDouble(), unit)

    fun getWindUnitSymbol(unit: String): String {
        return when (unit.lowercase()) {
            "mph" -> "mph"
            "noeuds", "knots", "kts" -> "kts"
            "beaufort", "bft" -> "Bft"
            else -> "km/h"
        }
    }

    fun kmhToBeaufort(kmh: Int): Int {
        return when {
            kmh < 1 -> 0
            kmh <= 5 -> 1
            kmh <= 11 -> 2
            kmh <= 19 -> 3
            kmh <= 28 -> 4
            kmh <= 38 -> 5
            kmh <= 49 -> 6
            kmh <= 61 -> 7
            kmh <= 74 -> 8
            kmh <= 88 -> 9
            kmh <= 102 -> 10
            kmh <= 117 -> 11
            else -> 12
        }
    }

    fun formatCardinalFr(direction: String): String {
        val fr = direction.uppercase().trim()
            .replace("WEST", "O")
            .replace("W", "O")
        // Anglais, allemand et néerlandais écrivent l'ouest « W » ; français et espagnol « O ».
        return if (I18n.language in setOf("en", "de", "nl")) fr.replace("O", "W") else fr
    }

    fun cardinalToDegrees(direction: String): Float {
        return when (direction.uppercase().replace("O", "W")) {
            "N" -> 0f
            "NNE" -> 22.5f
            "NE" -> 45f
            "ENE" -> 67.5f
            "E" -> 90f
            "ESE" -> 112.5f
            "SE" -> 135f
            "SSE" -> 157.5f
            "S" -> 180f
            "SSW", "SSO" -> 202.5f
            "SW", "SO" -> 225f
            "WSW", "OSO" -> 247.5f
            "W", "O" -> 270f
            "WNW", "ONO" -> 292.5f
            "NW", "NO" -> 315f
            "NNW", "NNO" -> 337.5f
            else -> 0f
        }
    }

    /**
     * Code couleur vent officiel de l'appli : la force ET la direction comptent. À afficher tel quel partout : ne jamais
     * assombrir ces teintes sur fond sombre (les trois paliers deviendraient indistinguables).
     *
     * - Calme (6 km/h ou moins) : jaune, quelle que soit la direction.
     * - Vent de terre (offshore) : jaune jusqu'à 20 km/h, orange jusqu'à 30, rouge au-delà.
     * - Vent de côté (side-shore : N, S, NNE, SSE) : jaune jusqu'à 10 km/h, orange jusqu'à 20, rouge au-delà.
     * - Vent de mer (onshore) : orange jusqu'à 15 km/h, rouge au-delà.
     */
    fun getSurfWindColor(directionFr: String, speedKmh: Int): Color {
        val dir = directionFr.uppercase().trim()
        val offshore = dir in OFFSHORE_DIRECTIONS
        val side = dir in SIDE_DIRECTIONS
        return when {
            speedKmh <= WIND_CALM_KMH -> WIND_YELLOW
            offshore -> when { speedKmh <= 20 -> WIND_YELLOW; speedKmh <= 30 -> WIND_ORANGE; else -> WIND_RED }
            side -> when { speedKmh <= 10 -> WIND_YELLOW; speedKmh <= 20 -> WIND_ORANGE; else -> WIND_RED }
            else -> if (speedKmh <= 15) WIND_ORANGE else WIND_RED
        }
    }

    /** Teinte du texte du vent : les mêmes paliers, assombris sur fond clair pour rester lisibles. */
    fun windTextColor(windColor: Color, isDarkTheme: Boolean): Color {
        if (isDarkTheme) return windColor
        return when (windColor) {
            WIND_YELLOW -> Color(0xFFBF8F00)
            WIND_ORANGE -> Color(0xFFE65100)
            else -> Color(0xFFD32F2F)
        }
    }

    private val OFFSHORE_DIRECTIONS = setOf("E", "ENE", "ESE", "SE", "NE")
    private val SIDE_DIRECTIONS = setOf("N", "S", "NNE", "SSE")
    const val WIND_CALM_KMH = 6
    val WIND_YELLOW = Color(0xFFFDD835)
    val WIND_ORANGE = Color(0xFFFF9800)
    val WIND_RED = Color(0xFFE53935)

    fun calculateWaveEnergy(heightMeters: Double, periodSeconds: Double): Int {
        val energy = 10.76 * (heightMeters * heightMeters) * periodSeconds
        return energy.roundToInt().coerceAtLeast(0)
    }

    fun resolveRealWeatherEmoji(hourly: HourlyUiModel): String {
        val isDay = hourly.rawTime.hour in 7..20
        return when (hourly.weatherCode) {
            0 -> if (isDay) "☀️" else "🌙"
            1, 2 -> if (isDay) "🌤️" else "☁️"
            3 -> "☁️"
            45, 48 -> "🌫️"
            // Forte pluie : codes de pluie forte, ou au moins 4 mm dans l'heure ; sinon pluie, ou averse avec du soleil.
            65, 67, 82 -> "☔"
            51, 53, 55, 61, 63 -> if (hourly.precipitationMm >= 4.0) "☔" else "🌧️"
            71, 73, 75, 77, 85, 86 -> "❄️"
            80, 81 -> if (hourly.precipitationMm >= 4.0) "☔" else "🌦️"
            95, 96, 99 -> "⛈️"
            else -> if (hourly.cloudCover > 60) "☁️" else if (isDay) "☀️" else "🌙"
        }
    }

    fun weatherCodeLabel(code: Int): String = when (code) {
        0 -> "Dégagé"
        1, 2 -> "Partiellement nuageux"
        3 -> "Nuageux"
        45, 48 -> "Brouillard"
        51, 53, 55, 61, 63, 65 -> "Pluie"
        71, 73, 75, 77, 85, 86 -> "Neige"
        80, 81, 82 -> "Averses"
        95, 96, 99 -> "Orage"
        else -> "Variable"
    }

    fun isPrecipitationCode(code: Int): Boolean = code in intArrayOf(
        51, 53, 55, 61, 63, 65, 71, 73, 75, 77, 80, 81, 82, 85, 86, 95, 96, 99
    )
}
