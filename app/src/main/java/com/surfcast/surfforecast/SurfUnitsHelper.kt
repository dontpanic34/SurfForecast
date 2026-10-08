package com.surfcast.surfforecast

import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt

object SurfUnitsHelper {

    fun formatWindValue(speedKmh: Double, unit: String): String {
        return when (unit.lowercase()) {
            "mph" -> "${(speedKmh * 0.621371).roundToInt()}"
            "noeuds", "knots", "kts" -> "${(speedKmh * 0.539957).roundToInt()}"
            "beaufort", "bft" -> "${kmhToBeaufort(speedKmh.roundToInt())}"
            else -> "${speedKmh.roundToInt()}"
        }
    }

    fun formatWindValue(speedKmh: Int, unit: String): String {
        return formatWindValue(speedKmh.toDouble(), unit)
    }

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
        return direction.uppercase().trim()
            .replace("WEST", "O")
            .replace("W", "O")
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
     * Code couleur vent officiel de l'appli, base sur la direction ET la force : jaune =
     * vent de terre (offshore, favorable, quelle que soit sa force), orange = vent de mer
     * (onshore) modere, rouge = vent de mer fort. A afficher tel quel partout (icones ET
     * texte) : ne jamais assombrir ces teintes pour la lisibilite sur fond clair, ca rend
     * les 3 paliers indistinguables entre eux. Pour du texte/icone de petite taille sur fond
     * clair, mettre un petit fond sombre derriere (cf. DailyTimelineCard/HourlyForecastRow)
     * plutot que de changer la couleur.
     */
    fun getSurfWindColor(directionFr: String, speedKmh: Int): Color {
        val dir = directionFr.uppercase().trim()

        val isVentDeTerre = dir in listOf("E", "ENE", "ESE", "SE", "NE")
        val isVentDeMer = dir in listOf("O", "W", "ONO", "WNW", "OSO", "WSW", "NO", "NW", "NNO", "NNW", "SO", "SW", "SSO", "SSW", "NNE", "SSE", "S", "N")

        return when {
            isVentDeTerre -> Color(0xFFFDD835)
            isVentDeMer && speedKmh <= 22 -> Color(0xFFFF9800)
            else -> Color(0xFFE53935)
        }
    }

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
            51, 53, 55, 61, 63, 65 -> "🌧️"
            71, 73, 75, 77, 85, 86 -> "❄️"
            80, 81, 82 -> "🌦️"
            95, 96, 99 -> "⛈️"
            else -> if (hourly.cloudCover > 60) "☁️" else if (isDay) "☀️" else "🌙"
        }
    }

    /** Libelle FR du temps, meme decoupage de codes WMO que resolveRealWeatherEmoji. */
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

    /** Vrai si le code WMO correspond a une forme de precipitation (pluie, averse, neige, orage). */
    fun isPrecipitationCode(code: Int): Boolean = code in intArrayOf(
        51, 53, 55, 61, 63, 65, 71, 73, 75, 77, 80, 81, 82, 85, 86, 95, 96, 99
    )
}