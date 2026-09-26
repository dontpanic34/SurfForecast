@file:Suppress("SpellCheckingInspection", "unused")
package com.surfcast.surfforecast

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
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

class SurfViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SurfRepository()
    private val prefs = application.getSharedPreferences("surf_prefs", Context.MODE_PRIVATE)
    private val sessionLogDao = SessionLogDatabase.getInstance(application).sessionLogDao()

    val quiverBoards: StateFlow<List<QuiverBoard>> = sessionLogDao.getAllQuiverBoards()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val referenceSessions: StateFlow<List<SurfSessionWithRelations>> = sessionLogDao.getReferenceSessions(4)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSessions: StateFlow<List<SurfSessionWithRelations>> = sessionLogDao.getAllSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun microSpotsFor(spotName: String) = sessionLogDao.getMicroSpotsForSpot(spotName)

    fun computePatternMatches(
        hourlyForecast: List<HourlyUiModel>,
        dailyTides: Map<LocalDate, DailyTideInfo>,
        idealSwellDirection: Int?
    ): List<PatternMatch> {
        val refs = referenceSessions.value
        if (refs.isEmpty()) return emptyList()

        return hourlyForecast.mapNotNull { hour ->
            val tideCoeff = dailyTides[hour.rawTime.toLocalDate()]?.coefficient
            val candidateVector = sessionVectorFromForecast(hour, idealSwellDirection, tideCoeff)
            val best = refs
                .map { ref -> ref to matchScore(sessionVectorFromCondition(ref.condition, idealSwellDirection), candidateVector) }
                .maxByOrNull { it.second }
            if (best != null && best.second >= PATTERN_MATCH_THRESHOLD) {
                PatternMatch(hourlyModel = hour, referenceSession = best.first, score = best.second.roundToInt())
            } else {
                null
            }
        }
    }

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

    // Point 2 : densite (1, 2 ou 3 creneaux/jour) et mode d'affichage du vent
    // pour l'encart "Prevision semaine" uniquement (n'affecte pas "Deroule de la journee").
    var weeklyDensity by mutableIntStateOf(prefs.getInt("weekly_density", 3).coerceIn(1, 3))
        private set

    var weeklyWindMode by mutableStateOf(prefs.getString("weekly_wind_mode", "both") ?: "both")
        private set

    // Point 3 : niveau du surfeur, utilise par SurfScoring.kt pour moduler le score
    // "Meilleur Creneau" (cible d'energie, tolerance au vent, penalites...).
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

    var cardsOrder = mutableStateListOf("weekly", "dailyTimeline", "surf", "wind", "weather", "hourly")
        private set

    // Tâche 2 : état réduit ("collapsed") de chaque encart, clé = cardKey ("surf", "wind", ...)
    var collapsedCards = mutableStateMapOf<String, Boolean>()
        private set

    var engineConfig by mutableStateOf(
        ForecastEngineConfig(
            shortTermWeather = WeatherModel.valueOf(prefs.getString("short_weather", WeatherModel.AROME.name) ?: WeatherModel.AROME.name),
            shortTermWave = WaveModel.valueOf(prefs.getString("short_wave", WaveModel.MFWAM.name) ?: WaveModel.MFWAM.name),
            longTermWeather = WeatherModel.valueOf(prefs.getString("long_weather", WeatherModel.ECMWF_IFS.name) ?: WeatherModel.ECMWF_IFS.name),
            longTermWave = WaveModel.valueOf(prefs.getString("long_wave", WaveModel.MFWAM.name) ?: WaveModel.MFWAM.name)
        )
    )
        private set

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
            val defaults = listOf("weekly", "dailyTimeline", "surf", "wind", "weather", "hourly")
            // On complète avec les nouvelles cles (weekly/dailyTimeline) si l'ordre sauvegarde
            // vient d'une version anterieure de l'app qui ne les connaissait pas encore.
            val merged = sanitized + defaults.filter { it !in sanitized }
            cardsOrder.addAll(merged.ifEmpty { defaults })
        }

        val savedCollapsed = prefs.getString("collapsed_cards", "") ?: ""
        collapsedCards.clear()
        savedCollapsed.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { collapsedCards[it] = true }
    }

    private fun saveFavorites() {
        prefs.edit {
            putString("fav_0", favoriteSpots.getOrNull(0))
            putString("fav_1", favoriteSpots.getOrNull(1))
            putString("fav_2", favoriteSpots.getOrNull(2))
            putInt("active_fav_index", activeFavoriteIndex)
        }
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

    /**
     * Charge un spot pour consultation rapide (ex: "Spots proches") SANS toucher
     * aux favoris ni a l'index actif : contrairement a updateActiveSpotFavorite,
     * ceci ne renomme jamais une pilule favorite existante.
     */
    fun previewSpot(spotName: String) {
        val spot = SurfDatabase.findSpotByName(spotName) ?: return
        viewModelScope.launch {
            isRefreshing = true
            loadSpotInternal(spot)
            isRefreshing = false
        }
    }

    fun refreshActiveSpot() {
        loadActiveSpot(initialLoad = false)
    }

    fun toggleLiveOverlay(show: Boolean) {
        showLiveOverlay = show
        prefs.edit { putBoolean("show_live_overlay", show) }
    }

    fun updateEngineConfig(newConfig: ForecastEngineConfig) {
        engineConfig = newConfig
        prefs.edit {
            putString("short_weather", newConfig.shortTermWeather.name)
            putString("short_wave", newConfig.shortTermWave.name)
            putString("long_weather", newConfig.longTermWeather.name)
            putString("long_wave", newConfig.longTermWave.name)
        }
        loadActiveSpot(initialLoad = false)
    }

    private fun loadActiveSpot(initialLoad: Boolean = false) {
        val spotName = favoriteSpots.getOrNull(activeFavoriteIndex) ?: "Montalivet"
        val spot = SurfDatabase.findSpotByName(spotName) ?: SurfDatabase.getAllSpots().first()

        viewModelScope.launch {
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
            val grouped = forecast.groupBy { it.rawTime.toLocalDate() }

            val summaries = grouped.mapValues { (_, hours) ->
                val avgTemp = if (hours.isNotEmpty()) hours.map { it.temperature }.average().roundToInt() else 20
                DailySummaryUiModel(avgFeelsLike = avgTemp, avgWaterTemp = 19)
            }

            val fromDate = grouped.keys.minOrNull()?.toString() ?: LocalDate.now().toString()
            val toDate = grouped.keys.maxOrNull()?.toString() ?: LocalDate.now().plusDays(7).toString()
            val tidesBundle = repository.getTides(spot.latitude, spot.longitude, fromDate, toDate)
            val tides = tidesBundle.dailyByDate

            val nowStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm", Locale.FRANCE))

            _uiState.value = SurfUiState.Success(
                spotName = spot.name,
                hourlyForecast = forecast,
                dailySummaries = summaries,
                dailyTides = tides,
                dailySunInfo = forecastResult.dailySun,
                lastUpdatedTime = nowStr
            )

            // Widget d'écran d'accueil : pousse le même instantané que SurfLiveStripOverlay
            // dès que ces données sont fraîches (heure la plus proche, tendance du vent,
            // prochaine marée à venir).
            WidgetDataCache.push(
                context = getApplication(),
                spotName = spot.name,
                forecast = forecast,
                allTideExtrema = tidesBundle.rawByDate,
                windUnit = windUnit
            )
        } catch (e: Exception) {
            if (_uiState.value !is SurfUiState.Success) {
                _uiState.value = SurfUiState.Error(e.message ?: "Erreur de connexion.")
            }
        }
    }

    fun changeThemeMode(mode: String) {
        themeMode = mode
        prefs.edit { putString("theme_mode", mode) }
    }

    fun changeWindUnit(unit: String) {
        windUnit = unit
        prefs.edit { putString("wind_unit", unit) }
    }

    fun toggleWeeklyCard(show: Boolean) {
        showWeeklyCard = show
        prefs.edit { putBoolean("show_weekly_card", show) }
    }

    fun toggleDailyTimelineCard(show: Boolean) {
        showDailyTimelineCard = show
        prefs.edit { putBoolean("show_daily_timeline_card", show) }
    }

    fun changeWeeklyDensity(density: Int) {
        weeklyDensity = density.coerceIn(1, 3)
        prefs.edit { putInt("weekly_density", weeklyDensity) }
    }

    fun changeWeeklyWindMode(mode: String) {
        weeklyWindMode = mode
        prefs.edit { putString("weekly_wind_mode", mode) }
    }

    fun changeSurferLevel(level: String) {
        surferLevel = level
        prefs.edit { putString("surfer_level", level) }
    }

    fun toggleSurfCard(show: Boolean) {
        showSurfCard = show
        prefs.edit { putBoolean("show_surf_card", show) }
    }

    fun toggleWindCard(show: Boolean) {
        showWindCard = show
        prefs.edit { putBoolean("show_wind_card", show) }
    }

    fun toggleWeatherCard(show: Boolean) {
        showWeatherCard = show
        prefs.edit { putBoolean("show_weather_card", show) }
    }

    fun addQuiverBoard(model: String, family: String, lengthLitrage: String, finSetup: String) {
        viewModelScope.launch {
            sessionLogDao.insertQuiverBoard(
                QuiverBoard(model = model, family = family, lengthLitrage = lengthLitrage, finSetup = finSetup)
            )
        }
    }

    fun deleteQuiverBoard(board: QuiverBoard) {
        viewModelScope.launch {
            try {
                sessionLogDao.deleteQuiverBoard(board)
            } catch (e: CancellationException) {
                // Ne jamais avaler une annulation de coroutine (ex: ViewModel efface
                // pendant la suppression) : elle doit continuer a se propager.
                throw e
            } catch (e: SQLiteConstraintException) {
                val current = _uiState.value
                if (current is SurfUiState.Success) {
                    _uiState.value = current.copy(
                        popupError = "Impossible de supprimer cette planche : elle est utilisee dans une session enregistree."
                    )
                }
            } catch (e: Exception) {
                val current = _uiState.value
                if (current is SurfUiState.Success) {
                    _uiState.value = current.copy(
                        popupError = "Impossible de supprimer cette planche : une erreur est survenue."
                    )
                }
            }
        }
    }

    fun addMicroSpot(parentSpotName: String, name: String) {
        viewModelScope.launch {
            sessionLogDao.insertMicroSpot(MicroSpot(parentSpotName = parentSpotName, name = name))
        }
    }

    fun logSurfSession(
        date: LocalDate,
        startHour: Int,
        endHour: Int,
        microSpotId: Long,
        quiverId: Long,
        rating: Int,
        comment: String?,
        mediaUri: String?,
        hourlyModel: HourlyUiModel,
        tideInfo: DailyTideInfo?
    ) {
        viewModelScope.launch {
            val snapshot = ConditionSnapshot(
                energyKj = hourlyModel.energyKj,
                waveHeight = hourlyModel.waveHeight,
                wavePeriod = hourlyModel.wavePeriod,
                waveDirection = hourlyModel.waveDirection.roundToInt(),
                windSpeedKmh = hourlyModel.windSpeedKmh,
                windDirection = SurfUnitsHelper.cardinalToDegrees(hourlyModel.windDirectionStr).roundToInt(),
                tideCoeff = tideInfo?.coefficient,
                isNearHighTide = isNearHighTide(hourlyModel, tideInfo)
            )
            sessionLogDao.logSession(
                startTime = date.atTime(startHour, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
                endTime = date.atTime(endHour, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
                microSpotId = microSpotId,
                quiverId = quiverId,
                condition = snapshot,
                rating = rating,
                comment = comment,
                mediaUri = mediaUri
            )
        }
    }

    fun toggleHourlyCard(show: Boolean) {
        showHourlyCard = show
        prefs.edit { putBoolean("show_hourly_card", show) }
    }

    fun moveCardUp(cardKey: String) {
        val index = cardsOrder.indexOf(cardKey)
        if (index > 0) {
            cardsOrder.removeAt(index)
            cardsOrder.add(index - 1, cardKey)
            prefs.edit { putString("cards_order", cardsOrder.joinToString(",")) }
        }
    }

    fun moveCardDown(cardKey: String) {
        val index = cardsOrder.indexOf(cardKey)
        if (index in 0 until cardsOrder.size - 1) {
            cardsOrder.removeAt(index)
            cardsOrder.add(index + 1, cardKey)
            prefs.edit { putString("cards_order", cardsOrder.joinToString(",")) }
        }
    }

    fun isCardCollapsed(cardKey: String): Boolean = collapsedCards[cardKey] ?: false

    fun toggleCardCollapsed(cardKey: String) {
        collapsedCards[cardKey] = !(collapsedCards[cardKey] ?: false)
        prefs.edit {
            putString("collapsed_cards", collapsedCards.filterValues { it }.keys.joinToString(","))
        }
    }

    fun dismissPopupError() {
        val current = _uiState.value
        if (current is SurfUiState.Success) {
            _uiState.value = current.copy(popupError = null)
        }
    }
}
