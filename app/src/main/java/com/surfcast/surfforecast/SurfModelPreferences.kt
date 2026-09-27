package com.surfcast.surfforecast

import android.content.SharedPreferences

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
        apiParam = "ecmwf_wave",
        displayName = "ECMWF WAM Europe",
        resolution = "14 km",
        description = "Traqueur de grand large, référence sur la houle longue et l'énergie océanique."
    )
}

data class ForecastEngineConfig(
    val shortTermWeather: WeatherModel = WeatherModel.AROME,
    val shortTermWave: WaveModel = WaveModel.MFWAM,
    val longTermWeather: WeatherModel = WeatherModel.ECMWF_IFS,
    val longTermWave: WaveModel = WaveModel.ECMWF_WAM
)

private inline fun <reified T : Enum<T>> safeEnumValueOf(name: String?, default: T): T =
    name?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default

/**
 * Reconstruit le ForecastEngineConfig persisté dans les préférences, partagé entre
 * SurfViewModel (au chargement de l'appli) et WidgetRefreshWorker (rafraîchissement en
 * arrière-plan) pour ne pas dupliquer cette lecture ni risquer qu'elle diverge entre les
 * deux. Une valeur de préférence corrompue ou obsolète retombe sur la valeur par défaut
 * au lieu de faire planter l'appelant.
 */
fun loadEngineConfigFromPrefs(prefs: SharedPreferences): ForecastEngineConfig = ForecastEngineConfig(
    shortTermWeather = safeEnumValueOf(prefs.getString("short_weather", null), WeatherModel.AROME),
    shortTermWave = safeEnumValueOf(prefs.getString("short_wave", null), WaveModel.MFWAM),
    longTermWeather = safeEnumValueOf(prefs.getString("long_weather", null), WeatherModel.ECMWF_IFS),
    longTermWave = safeEnumValueOf(prefs.getString("long_wave", null), WaveModel.MFWAM)
)