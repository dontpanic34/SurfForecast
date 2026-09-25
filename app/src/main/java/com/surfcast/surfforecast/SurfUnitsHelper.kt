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
     * Classe une direction cardinale FR en vent de terre (offshore, cote large ouest de
     * la France) ou vent de mer (onshore), pour les deux palettes de couleur vent
     * ci-dessous. Partagee entre [getSurfWindColor] et [getSurfWindTextColor] pour que les
     * deux palettes restent toujours d'accord sur la classification d'une direction.
     */
    private enum class WindShoreCategory { OFFSHORE, ONSHORE, OTHER }

    private fun windShoreCategoryFor(directionFr: String): WindShoreCategory {
        val dir = directionFr.uppercase().trim()

        val isVentDeTerre = dir in listOf("E", "ENE", "ESE", "SE", "NE")
        val isVentDeMer = dir in listOf("O", "W", "ONO", "WNW", "OSO", "WSW", "NO", "NW", "NNO", "NNW", "SO", "SW", "SSO", "SSW", "NNE", "SSE", "S", "N")

        return when {
            isVentDeTerre -> WindShoreCategory.OFFSHORE
            isVentDeMer -> WindShoreCategory.ONSHORE
            else -> WindShoreCategory.OTHER
        }
    }

    fun getSurfWindColor(directionFr: String, speedKmh: Int): Color {
        return when (windShoreCategoryFor(directionFr)) {
            WindShoreCategory.OFFSHORE -> Color(0xFFFDD835)
            WindShoreCategory.ONSHORE -> if (speedKmh <= 22) Color(0xFFFF9800) else Color(0xFFE53935)
            WindShoreCategory.OTHER -> Color(0xFFE53935)
        }
    }

    /**
     * Variante de getSurfWindColor foncee pour du texte/icones de petite taille : la
     * palette ci-dessus est pensee pour de larges surfaces (barres, grosses fleches) sur
     * fond sombre, et devient a peine lisible en petit sur fond clair (notamment le jaune
     * "vent de terre"). A utiliser pour tout texte/etiquette de vent affiche sur le theme
     * clair de l'appli.
     */
    fun getSurfWindTextColor(directionFr: String, speedKmh: Int): Color {
        return when (windShoreCategoryFor(directionFr)) {
            WindShoreCategory.OFFSHORE -> Color(0xFF8F6300)
            WindShoreCategory.ONSHORE -> if (speedKmh <= 22) Color(0xFFB35400) else Color(0xFFC62828)
            WindShoreCategory.OTHER -> Color(0xFFC62828)
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
}