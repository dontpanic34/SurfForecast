package com.surfcast.surfforecast

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