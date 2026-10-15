package com.surfcast.surfforecast

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
    const val DEFAULT_RAMP = 14

    var weekPct by mutableIntStateOf(DEFAULT_WEEK)
        private set
    var dayPct by mutableIntStateOf(DEFAULT_DAY)
        private set
    /** Échelle de la mer du jour (la semaine a la sienne : [waterWeekPct]). */
    var waterPct by mutableIntStateOf(DEFAULT_WATER)
        private set
    var waterWeekPct by mutableIntStateOf(DEFAULT_WATER)
        private set

    /** Hauteur de la petite courbe de marée du déroulé (dp). */
    /** Marées de la nuit aussi (« hors soleil ») sur la ligne du coefficient. */
    var tideAll by mutableStateOf(false)
        private set

    /** Pastille « Clapot » dans l'encart Houle (réglage de l'engrenage de l'encart). */
    var showChop by mutableStateOf(true)
        private set

    var tideDp by mutableIntStateOf(DEFAULT_TIDE)
        private set

    /** Hauteur de la rampe de marée du déroulé (dp). */
    var rampDp by mutableIntStateOf(DEFAULT_RAMP)
        private set

    /** Branchée par le contrôleur : enregistre un réglage (clé, valeur). */
    var persist: (String, Int) -> Unit = { _, _ -> }

    fun load(week: Int, day: Int, water: Int, waterWeek: Int = water) {
        weekPct = week.coerceIn(25, 70)
        dayPct = day.coerceIn(25, 70)
        waterPct = water.coerceIn(20, 60)
        waterWeekPct = waterWeek.coerceIn(20, 60)
    }

    fun loadTide(dp: Int) { tideDp = dp.coerceIn(24, 60) }
    fun loadRamp(dp: Int) { rampDp = dp.coerceIn(8, 28) }
    fun setRamp(dp: Int) { rampDp = dp.coerceIn(8, 28); persist("graph_ramp_dp", rampDp) }
    fun loadShowChop(v: Boolean) { showChop = v }
    fun chooseShowChop(v: Boolean) { showChop = v; persist("show_chop", if (v) 1 else 0) }
    fun loadTideAll(v: Boolean) { tideAll = v }
    fun chooseTideAll(v: Boolean) { tideAll = v; persist("graph_tide_all", if (v) 1 else 0) }
    fun setTide(dp: Int) { tideDp = dp.coerceIn(24, 60); persist("graph_tide_dp", tideDp) }
    fun setWeek(p: Int) { weekPct = p.coerceIn(25, 70); persist("graph_week_pct", weekPct) }
    fun setDay(p: Int) { dayPct = p.coerceIn(25, 70); persist("graph_day_pct", dayPct) }
    fun setWater(p: Int) { waterPct = p.coerceIn(20, 60); persist("graph_water_pct", waterPct) }
    fun setWaterWeek(p: Int) { waterWeekPct = p.coerceIn(20, 60); persist("graph_water_week_pct", waterWeekPct) }
    /** Remet à zéro les réglages du jour (hauteur, échelle de la mer, marée). */
    fun resetDay() { setDay(DEFAULT_DAY); setWater(DEFAULT_WATER); setTide(DEFAULT_TIDE); setRamp(DEFAULT_RAMP) }
    /** Remet à zéro les réglages de la semaine (hauteur, échelle de la mer). */
    fun resetWeek() { setWeek(DEFAULT_WEEK); setWaterWeek(DEFAULT_WATER) }
    fun reset() { resetDay(); resetWeek() }

    /** Hauteur de l'échelle : part du graphique à laquelle arrive une houle de 2 m (de 0 à 1). */
    val waterFraction: Float get() = waterPct / 100f
    val waterWeekFraction: Float get() = waterWeekPct / 100f

    // Le reste de l'encart (titre, jours, vent, énergie ; titre, jauge, bande horaire) fait environ 155 dp pour la semaine et
    // 224 dp pour le jour : la hauteur du graphique en découle pour une part donnée de l'encart.
    fun weekHeightDp(): Float = weekPct.toFloat() / (100 - weekPct) * 155f
    fun dayPlotDp(): Float = dayPct.toFloat() / (100 - dayPct) * 224f
}
