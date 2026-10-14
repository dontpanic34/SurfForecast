package com.surfcast.surfforecast

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

// Portage multiplateforme des modèles de app/ (mêmes noms, mêmes champs) :
// java.time remplacé par kotlinx-datetime pour compiler aussi sur iOS.

/** La prévision d'un modèle pour une heure : hauteur (m) ou vent (km/h) dans [value], période (s) et direction (°, NaN = inconnue). */
data class ModelVoice(val label: String, val value: Double, val period: Double = 0.0, val direction: Float = Float.NaN)

data class HourlyUiModel(
    val timeFormatted: String,
    val rawTime: LocalDateTime,
    val waveHeight: Double,
    val wavePeriod: Double,
    val waveDirection: Float,
    // Mer de vent (clapot) : 0 = pas de clapot (valeur légitime, pas une donnée manquante).
    val windWaveHeight: Double = 0.0,
    val windWavePeriod: Double = 0.0,
    val windWaveDirection: Float = 0f,
    // Houle secondaire (hauteur m, période s, direction °) : hauteur 0 = pas de seconde houle.
    val secondaryHeight: Double = 0.0,
    val secondaryPeriod: Double = 0.0,
    val secondaryDirection: Float = 0f,
    val energyKj: Int,
    val windSpeedKmh: Int,
    val windDirectionStr: String,
    val weatherCode: Int,
    val temperature: Int,
    val cloudCover: Int = 0,
    // Indice UV et pluie de l'heure (mm) : 0 quand le modèle ne les donne pas.
    val uvIndex: Double = 0.0,
    val precipitationMm: Double = 0.0,
    val feelsLike: Int = temperature,
    // Modèle d'où vient le vent de cette heure ("AROME HD", "AROME", "ECMWF_IFS"...,
    // ou WIND_SOURCE_MISSING) : sert au journal des prévisions. Vide = inconnu.
    val windSource: String = "",
    // Rafales (km/h) : par défaut égales au vent moyen quand la source n'en donne pas.
    val windGustKmh: Int = windSpeedKmh,
    // Le vent réel des trois heures d'avant (pour la note : « Parfait » demande une mer que le vent n'a pas touchée).
    val pastWind: List<WindSample> = emptyList(),
    // Température de surface de la mer (°C, Open-Meteo Marine) : null = non fournie pour ce point.
    val seaTemperature: Double? = null,
    // Désaccord des modèles de vagues pour cette heure : (plus grande - plus petite hauteur) / moyenne. 0 = un seul modèle ou d'accord.
    val modelSpread: Double = 0.0,
    // Période la plus courte et la plus longue annoncées par les modèles de vagues (s) : 0 = un seul modèle.
    val periodMin: Double = 0.0,
    val periodMax: Double = 0.0,
    // Vent le plus faible et le plus fort annoncés par les modèles (km/h) : 0 = un seul modèle.
    val windMinKmh: Int = 0,
    val windMaxKmh: Int = 0,
    // Ce que dit chaque modèle pour cette heure (pour la feuille « Modèles partagés ») ; vide = un seul modèle.
    val waveVoices: List<ModelVoice> = emptyList(),
    val windVoiceList: List<ModelVoice> = emptyList()
)

data class DailyTideInfo(
    val highTideTime: String? = null,
    val lowTideTime: String? = null,
    val coefficient: Int? = null,
    // Toutes les marées du jour, nuit comprise : le déroulé en retient les deux qui tombent le mieux dans la journée.
    val extrema: List<MareeExtremum> = emptyList(),
    // Horaires estimés (hauteur d'eau Open-Meteo) parce que le service des marées ne répond pas : à afficher avec « ≈ ».
    val estimated: Boolean = false
)

data class DailySunInfo(
    val sunrise: LocalTime,
    val sunset: LocalTime
)

data class HybridForecastResult(
    val hourly: List<HourlyUiModel>,
    val dailySun: Map<LocalDate, DailySunInfo>
)

enum class WeatherModel(
    val apiParam: String,
    val displayName: String,
    val resolution: String,
    val description: String
) {
    AROME(
        apiParam = "meteofrance_arome_france",
        displayName = "Météo-France AROME",
        resolution = "1.3 km",
        description = "Ultra-précis sur les thermiques et brises côtières (J0-J1)."
    ),
    ECMWF_IFS(
        apiParam = "ecmwf_ifs025",
        displayName = "ECMWF IFS Europe",
        resolution = "9 km",
        description = "Vue d'ensemble globale européenne, très stable à moyen terme (J2-J6)."
    ),
    GFS(
        apiParam = "gfs_seamless",
        displayName = "NOAA GFS",
        resolution = "13 km",
        description = "Modèle américain, utilisé avec ECMWF pour la moyenne du vent."
    ),
    ARPEGE(
        apiParam = "meteofrance_arpege_europe",
        displayName = "Météo-France ARPEGE",
        resolution = "11 km",
        description = "Alternative régionale Météo-France."
    )
}

enum class WaveModel(
    val apiParam: String,
    val displayName: String,
    val resolution: String,
    val description: String
) {
    MFWAM(
        apiParam = "meteofrance_wave",
        displayName = "Météo-France MFWAM",
        resolution = "8 km",
        description = "Spécialiste de la bathymétrie côtière et des bancs de sable français."
    ),
    ECMWF_WAM(
        apiParam = "ecmwf_wam",
        displayName = "ECMWF WAM Europe",
        resolution = "14 km",
        description = "Traqueur de grand large, référence sur la houle longue et l'énergie océanique."
    ),
    GFS_WAVE(
        apiParam = "ncep_gfswave016",
        displayName = "NOAA GFS Wave (WaveWatch III)",
        resolution = "16 km",
        description = "Le modèle de vagues américain, celui de Windguru : il voit loin, mais surestime souvent la période près de nos côtes."
    )
}

data class ForecastEngineConfig(
    val shortTermWeather: WeatherModel = WeatherModel.AROME,
    val shortTermWave: WaveModel = WaveModel.MFWAM,
    val longTermWeather: WeatherModel = WeatherModel.ECMWF_IFS,
    val longTermWave: WaveModel = WaveModel.ECMWF_WAM,
    // Consensus des trois modèles de vagues (Auto) : hauteur et période d'un même calcul, au lieu d'un modèle choisi.
    val consensus: Boolean = true
)

/** Un vent passé : vitesse et rafales (km/h) et direction. */
data class WindSample(val speedKmh: Int, val gustKmh: Int, val direction: String)

/**
 * Poids des trois modèles de vagues (MF-WAM, ECMWF Wave, GFS Wave) dans le consensus, selon l'échéance en heures depuis
 * le début du jour : jusqu'à 36 h, MF-WAM et ECMWF à parts égales et GFS Wave à zéro ; dès 60 h, trois parts égales ; fondu entre les deux.
 */
internal fun waveConsensusWeights(leadHours: Double): DoubleArray {
    val fade = ((leadHours - 36.0) / 24.0).coerceIn(0.0, 1.0)
    val fine = 0.5 * (1 - fade) + fade / 3.0
    return doubleArrayOf(fine, fine, fade / 3.0)
}

/** Ce qu'un modèle de vagues annonce pour un moment : hauteur (m) et période (s). */
data class PreviewWave(val height: Double, val period: Double)

/** Ce qu'un modèle de vent et de météo annonce pour un moment. */
data class PreviewWind(val speedKmh: Int, val directionDeg: Int?, val temperature: Int?, val cloudCover: Int?, val rainMm: Double?)

/** Les annonces de chaque modèle (par nom, puis par jour : 0 aujourd'hui, 1 demain, 3 dans trois jours) à 14 h. */
data class ModelPreview(val waves: Map<String, Map<Int, PreviewWave>>, val winds: Map<String, Map<Int, PreviewWind>>)
