package com.surfcast.surfforecast

/** Écart (km/h) entre le plus faible et le plus fort des modèles de vent à partir duquel l'appli le dit. */
const val WIND_SPREAD_MIN_KMH = 7

/** Écart (s) entre la période la plus courte et la plus longue des modèles de vagues : au-delà, la mer est mélangée. */
const val PERIOD_SPREAD_MIN_S = 3.0

/** Les modèles de vent ne s'accordent pas : « vent incertain, 3 à 12 km/h ». */
fun windModelsDisagree(h: HourlyUiModel): Boolean = h.windMaxKmh - h.windMinKmh >= WIND_SPREAD_MIN_KMH

/** Les modèles de vagues voient des périodes très différentes : plusieurs trains de houle mélangés, mer brouillonne. */
fun seaIsMixed(h: HourlyUiModel): Boolean = h.waveHeight >= 0.3 && h.periodMax > 0.0 && h.periodMax - h.periodMin >= PERIOD_SPREAD_MIN_S
