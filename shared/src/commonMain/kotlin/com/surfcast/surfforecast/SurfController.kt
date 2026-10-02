@file:Suppress("SpellCheckingInspection")
package com.surfcast.surfforecast

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.math.roundToInt

sealed class SurfUiState {
    object Loading : SurfUiState()
    data class Success(
        val spotName: String,
        val hourlyForecast: List<HourlyUiModel>,
        val dailySummaries: Map<LocalDate, DailySummaryUiModel>,
        val dailyTides: Map<LocalDate, DailyTideInfo>,
        val dailySunInfo: Map<LocalDate, DailySunInfo> = emptyMap(),
        val lastUpdatedTime: String,
        val popupError: String? = null
    ) : SurfUiState()
    data class Error(val message: String) : SurfUiState()
}

data class DailySummaryUiModel(
    val avgFeelsLike: Int,
    val avgWaterTemp: Int
)

/**
 * Portage multiplateforme de SurfViewModel (app/) : même état, mêmes clés de préférences,
 * mêmes règles. Le journal de sessions (Room), le cache hors ligne et le widget restent
 * pour l'instant côté Android ; [onForecastLoaded] permet à l'hôte de s'y brancher.
 */
class SurfController(
    private val scope: CoroutineScope,
    private val prefs: KeyValueStore,
    private val repository: SurfRepository = SurfRepository(),
    private val onForecastLoaded: (SurfUiState.Success, SurfRepository.TidesBundle) -> Unit = { _, _ -> }
) {

    private val _uiState = MutableStateFlow<SurfUiState>(SurfUiState.Loading)
    val uiState: StateFlow<SurfUiState> = _uiState.asStateFlow()

    var isRefreshing by mutableStateOf(false)
        private set

    var favoriteSpots = mutableStateListOf<String?>("Montalivet", "Lacanau", "Capbreton")
        private set

    var activeFavoriteIndex by mutableIntStateOf(0)
        private set

    var themeMode by mutableStateOf(prefs.getString("theme_mode", "system") ?: "system")
        private set

    var windUnit by mutableStateOf(prefs.getString("wind_unit", "kmh") ?: "kmh")
        private set

    var showLiveOverlay by mutableStateOf(prefs.getBoolean("show_live_overlay", true))
        private set

    var showWeeklyCard by mutableStateOf(prefs.getBoolean("show_weekly_card", true))
        private set

    var showDailyTimelineCard by mutableStateOf(prefs.getBoolean("show_daily_timeline_card", true))
        private set

    var weeklyDensity by mutableIntStateOf(prefs.getInt("weekly_density", 3).coerceIn(1, 3))
        private set

    var weeklyWindMode by mutableStateOf(prefs.getString("weekly_wind_mode", "both") ?: "both")
        private set

    var surferLevel by mutableStateOf(prefs.getString("surfer_level", "intermediate") ?: "intermediate")
        private set

    var showSurfCard by mutableStateOf(prefs.getBoolean("show_surf_card", true))
        private set

    var showWindCard by mutableStateOf(prefs.getBoolean("show_wind_card", true))
        private set

    var showWeatherCard by mutableStateOf(prefs.getBoolean("show_weather_card", true))
        private set

    var showHourlyCard by mutableStateOf(prefs.getBoolean("show_hourly_card", true))
        private set

    var showWindSeaCard by mutableStateOf(prefs.getBoolean("show_wind_sea_card", true))
        private set

    var cardsOrder = mutableStateListOf(*DEFAULT_CARDS_ORDER.toTypedArray())
        private set

    var collapsedCards = mutableStateMapOf<String, Boolean>()
        private set

    var engineConfig by mutableStateOf(loadEngineConfigFromPrefs(prefs))
        private set

    // Dernier spot chargé avec succès (favori ou spot consulté) et quand : sert à recharger
    // ce même spot quand l'appli revient au premier plan.
    private var lastLoadedSpot: SurfSpotItem? = null
    private var lastLoadedAt: kotlinx.datetime.LocalDateTime? = null

    init {
        loadPreferences()
        loadActiveSpot(initialLoad = true)
    }

    private fun loadPreferences() {
        val savedFav0 = prefs.getString("fav_0", "Montalivet")
        val savedFav1 = prefs.getString("fav_1", "Lacanau")
        val savedFav2 = prefs.getString("fav_2", "Capbreton")
        favoriteSpots.clear()
        favoriteSpots.addAll(listOf(savedFav0, savedFav1, savedFav2))

        activeFavoriteIndex = prefs.getInt("active_fav_index", 0).coerceIn(0, 2)

        val savedOrder = prefs.getString("cards_order", null)
        if (savedOrder != null) {
            cardsOrder.clear()
            val sanitized = savedOrder.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            val merged = sanitized + DEFAULT_CARDS_ORDER.filter { it !in sanitized }
            cardsOrder.addAll(merged.ifEmpty { DEFAULT_CARDS_ORDER })
        }

        val savedCollapsed = prefs.getString("collapsed_cards", "") ?: ""
        collapsedCards.clear()
        savedCollapsed.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { collapsedCards[it] = true }
    }

    private fun saveFavorites() {
        prefs.putString("fav_0", favoriteSpots.getOrNull(0))
        prefs.putString("fav_1", favoriteSpots.getOrNull(1))
        prefs.putString("fav_2", favoriteSpots.getOrNull(2))
        prefs.putInt("active_fav_index", activeFavoriteIndex)
    }

    fun selectFavoriteIndex(index: Int) {
        if (index in favoriteSpots.indices && favoriteSpots[index] != null) {
            activeFavoriteIndex = index
            saveFavorites()
            loadActiveSpot(initialLoad = false)
        }
    }

    fun assignSpotToFavoriteSlot(spotName: String, slotIndex: Int) {
        if (slotIndex in favoriteSpots.indices) {
            favoriteSpots[slotIndex] = spotName
            saveFavorites()
            if (activeFavoriteIndex == slotIndex) {
                loadActiveSpot(initialLoad = false)
            }
        }
    }

    fun removeFavoriteSlot(slotIndex: Int) {
        if (slotIndex in favoriteSpots.indices) {
            favoriteSpots[slotIndex] = null
            saveFavorites()
        }
    }

    fun updateActiveSpotFavorite(newSpotName: String) {
        if (activeFavoriteIndex in favoriteSpots.indices) {
            favoriteSpots[activeFavoriteIndex] = newSpotName
            saveFavorites()
            loadActiveSpot(initialLoad = false)
        }
    }

    /** Consultation rapide d'un spot (ex: "Spots proches") sans toucher aux favoris. */
    fun previewSpot(spotName: String) {
        val spot = SurfDatabase.findSpotByName(spotName) ?: return
        scope.launch {
            isRefreshing = true
            loadSpotInternal(spot)
            isRefreshing = false
        }
    }

    fun refreshActiveSpot() {
        loadActiveSpot(initialLoad = false)
    }

    /**
     * À chaque retour de l'appli au premier plan : recharge si les prévisions ont plus de
     * 15 min, pour ne plus afficher un "maintenant" vieux de plusieurs heures.
     */
    fun onAppResumed() {
        if (isRefreshing) return
        val loadedAt = lastLoadedAt ?: return
        val spot = lastLoadedSpot ?: return
        val now = nowLocalDateTime()
        val ageMinutes = (now.date.toEpochDays() - loadedAt.date.toEpochDays()) * 24 * 60 +
            (now.hour * 60 + now.minute) - (loadedAt.hour * 60 + loadedAt.minute)
        if (ageMinutes < RESUME_REFRESH_AFTER_MINUTES) return
        scope.launch {
            isRefreshing = true
            loadSpotInternal(spot)
            isRefreshing = false
        }
    }

    fun toggleLiveOverlay(show: Boolean) {
        showLiveOverlay = show
        prefs.putBoolean("show_live_overlay", show)
    }

    fun updateEngineConfig(newConfig: ForecastEngineConfig) {
        engineConfig = newConfig
        prefs.putString("short_weather", newConfig.shortTermWeather.name)
        prefs.putString("short_wave", newConfig.shortTermWave.name)
        prefs.putString("long_weather", newConfig.longTermWeather.name)
        prefs.putString("long_wave", newConfig.longTermWave.name)
        loadActiveSpot(initialLoad = false)
    }

    private fun loadActiveSpot(initialLoad: Boolean = false) {
        val spotName = favoriteSpots.getOrNull(activeFavoriteIndex) ?: "Montalivet"
        val spot = SurfDatabase.findSpotByName(spotName) ?: SurfDatabase.getAllSpots().first()

        scope.launch {
            isRefreshing = true
            if (initialLoad && _uiState.value !is SurfUiState.Success) {
                _uiState.value = SurfUiState.Loading
            }
            loadSpotInternal(spot)
            isRefreshing = false
        }
    }

    private suspend fun loadSpotInternal(spot: SurfSpotItem) {
        try {
            val forecastResult = repository.getHybridForecast(spot.latitude, spot.longitude, engineConfig)
            val forecast = forecastResult.hourly
            val grouped = forecast.groupBy { it.rawTime.date }

            val summaries = grouped.mapValues { (_, hours) ->
                val avgTemp = if (hours.isNotEmpty()) hours.map { it.temperature }.average().roundToInt() else 20
                DailySummaryUiModel(avgFeelsLike = avgTemp, avgWaterTemp = 19)
            }

            val today = nowLocalDateTime().date
            val fromDate = grouped.keys.minOrNull()?.toString() ?: today.toString()
            val toDate = grouped.keys.maxOrNull()?.toString() ?: today.plus(7, DateTimeUnit.DAY).toString()
            val tidesBundle = repository.getTides(spot.latitude, spot.longitude, fromDate, toDate)

            val successState = SurfUiState.Success(
                spotName = spot.name,
                hourlyForecast = forecast,
                dailySummaries = summaries,
                dailyTides = tidesBundle.dailyByDate,
                dailySunInfo = forecastResult.dailySun,
                lastUpdatedTime = nowLocalDateTime().formatHHmm()
            )
            _uiState.value = successState
            lastLoadedSpot = spot
            lastLoadedAt = nowLocalDateTime()
            // Ne doit jamais faire échouer le chargement (cache, widget... côté hôte).
            runCatching { onForecastLoaded(successState, tidesBundle) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (_uiState.value !is SurfUiState.Success) {
                _uiState.value = SurfUiState.Error(e.message ?: "Erreur de connexion.")
            }
        }
    }

    fun changeThemeMode(mode: String) {
        themeMode = mode
        prefs.putString("theme_mode", mode)
    }

    fun changeWindUnit(unit: String) {
        windUnit = unit
        prefs.putString("wind_unit", unit)
    }

    fun toggleWeeklyCard(show: Boolean) {
        showWeeklyCard = show
        prefs.putBoolean("show_weekly_card", show)
    }

    fun toggleDailyTimelineCard(show: Boolean) {
        showDailyTimelineCard = show
        prefs.putBoolean("show_daily_timeline_card", show)
    }

    fun changeWeeklyDensity(density: Int) {
        weeklyDensity = density.coerceIn(1, 3)
        prefs.putInt("weekly_density", weeklyDensity)
    }

    fun changeWeeklyWindMode(mode: String) {
        weeklyWindMode = mode
        prefs.putString("weekly_wind_mode", mode)
    }

    fun changeSurferLevel(level: String) {
        surferLevel = level
        prefs.putString("surfer_level", level)
    }

    fun toggleSurfCard(show: Boolean) {
        showSurfCard = show
        prefs.putBoolean("show_surf_card", show)
    }

    fun toggleWindCard(show: Boolean) {
        showWindCard = show
        prefs.putBoolean("show_wind_card", show)
    }

    fun toggleWeatherCard(show: Boolean) {
        showWeatherCard = show
        prefs.putBoolean("show_weather_card", show)
    }

    fun toggleWindSeaCard(show: Boolean) {
        showWindSeaCard = show
        prefs.putBoolean("show_wind_sea_card", show)
    }

    fun toggleHourlyCard(show: Boolean) {
        showHourlyCard = show
        prefs.putBoolean("show_hourly_card", show)
    }

    fun moveCardUp(cardKey: String) {
        val index = cardsOrder.indexOf(cardKey)
        if (index > 0) {
            cardsOrder.removeAt(index)
            cardsOrder.add(index - 1, cardKey)
            prefs.putString("cards_order", cardsOrder.joinToString(","))
        }
    }

    fun moveCardDown(cardKey: String) {
        val index = cardsOrder.indexOf(cardKey)
        if (index in 0 until cardsOrder.size - 1) {
            cardsOrder.removeAt(index)
            cardsOrder.add(index + 1, cardKey)
            prefs.putString("cards_order", cardsOrder.joinToString(","))
        }
    }

    fun isCardCollapsed(cardKey: String): Boolean = collapsedCards[cardKey] ?: false

    fun toggleCardCollapsed(cardKey: String) {
        collapsedCards[cardKey] = !(collapsedCards[cardKey] ?: false)
        prefs.putString("collapsed_cards", collapsedCards.filterValues { it }.keys.joinToString(","))
    }

    fun dismissPopupError() {
        val current = _uiState.value
        if (current is SurfUiState.Success) {
            _uiState.value = current.copy(popupError = null)
        }
    }

    companion object {
        private const val RESUME_REFRESH_AFTER_MINUTES = 15
        val DEFAULT_CARDS_ORDER = listOf("weekly", "dailyTimeline", "surf", "wind", "windSea", "weather", "hourly")
    }
}
