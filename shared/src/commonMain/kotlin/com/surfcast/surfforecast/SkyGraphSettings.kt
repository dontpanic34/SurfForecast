package com.surfcast.surfforecast

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * Réglages de place des graphiques de houle : part de l'encart prise par le graphique de la semaine et par celui du jour,
 * et part du graphique prise par l'eau (le reste est du ciel). Modifiables dans Affichage.
 */
object SkyGraphSettings {
    const val DEFAULT_WEEK = 43
    const val DEFAULT_DAY = 43
    const val DEFAULT_WATER = 40
    const val DEFAULT_TIDE = 30

    var weekPct by mutableIntStateOf(DEFAULT_WEEK)
        private set
    var dayPct by mutableIntStateOf(DEFAULT_DAY)
        private set
    var waterPct by mutableIntStateOf(DEFAULT_WATER)
        private set

    /** Hauteur de la petite courbe de marée du déroulé (dp). */
    var tideDp by mutableIntStateOf(DEFAULT_TIDE)
        private set

    /** Branchée par le contrôleur : enregistre un réglage (clé, valeur). */
    var persist: (String, Int) -> Unit = { _, _ -> }

    fun load(week: Int, day: Int, water: Int) {
        weekPct = week.coerceIn(25, 70)
        dayPct = day.coerceIn(25, 70)
        waterPct = water.coerceIn(20, 60)
    }

    fun loadTide(dp: Int) { tideDp = dp.coerceIn(24, 60) }
    fun setTide(dp: Int) { tideDp = dp.coerceIn(24, 60); persist("graph_tide_dp", tideDp) }
    fun setWeek(p: Int) { weekPct = p.coerceIn(25, 70); persist("graph_week_pct", weekPct) }
    fun setDay(p: Int) { dayPct = p.coerceIn(25, 70); persist("graph_day_pct", dayPct) }
    fun setWater(p: Int) { waterPct = p.coerceIn(20, 60); persist("graph_water_pct", waterPct) }
    fun reset() { setWeek(DEFAULT_WEEK); setDay(DEFAULT_DAY); setWater(DEFAULT_WATER); setTide(DEFAULT_TIDE) }

    /** Hauteur de l'échelle : part du graphique à laquelle arrive une houle de 2 m (de 0 à 1). */
    val waterFraction: Float get() = waterPct / 100f

    // Le reste de l'encart (titre, jours, vent, énergie ; titre, jauge, bande horaire) fait environ 155 dp pour la semaine et
    // 224 dp pour le jour : la hauteur du graphique en découle pour une part donnée de l'encart.
    fun weekHeightDp(): Float = weekPct.toFloat() / (100 - weekPct) * 155f
    fun dayPlotDp(): Float = dayPct.toFloat() / (100 - dayPct) * 224f
}
