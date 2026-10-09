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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
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
    // Température de la mer moyenne du jour (°C), null si le service ne la fournit pas.
    val avgWaterTemp: Int?
)

/**
 * Portage multiplateforme de SurfViewModel (app/) : même état, mêmes clés de préférences,
 * mêmes règles. Le journal de sessions (Room), le cache hors ligne et le widget restent
 * pour l'instant côté Android ; [onForecastLoaded] permet à l'hôte de s'y brancher.
 */
class SurfController(
    private val scope: CoroutineScope,
    private val prefs: KeyValueStore,
    private val repository: SurfRepository = SurfRepository(defaultHttpClient(), prefs),
    private val onForecastLoaded: (SurfUiState.Success, SurfRepository.TidesBundle) -> Unit = { _, _ -> },
    // Journal de bord (Room sur mobile, JSON dans le navigateur) : null = pas de stockage (tests).
    private val sessionLogStore: SessionLogStore? = null
) {

    val quiverBoards: StateFlow<List<QuiverBoard>> = (sessionLogStore?.getAllQuiverBoards() ?: flowOf(emptyList()))
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    val referenceSessions: StateFlow<List<SurfSessionWithRelations>> =
        (sessionLogStore?.getReferenceSessions(4) ?: flowOf(emptyList()))
            .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSessions: StateFlow<List<SurfSessionWithRelations>> = (sessionLogStore?.getAllSessions() ?: flowOf(emptyList()))
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Orientation des plages corrigée par l'utilisateur (Paramètres > Prévisions) : spot -> degrés.
    private val facingOverrides = mutableStateMapOf<String, Int>().also { map ->
        SurfDatabase.getAllSpots().forEach { spot ->
            val key = facingKey(spot.name)
            if (prefs.contains(key)) map[spot.name] = prefs.getInt(key, 270)
        }
    }

    private fun facingKey(spotName: String) = "spot_facing_$spotName"

    fun defaultFacingFor(spotName: String): Int? = SurfDatabase.findSpotByName(spotName)?.idealSwellDirection

    /** Orientation utilisée pour le score : celle de l'utilisateur, sinon celle du catalogue. */
    fun facingFor(spotName: String): Int? = facingOverrides[spotName] ?: defaultFacingFor(spotName)

    /** [degrees] null = revenir à la valeur par défaut du catalogue. */
    fun setFacing(spotName: String, degrees: Int?) {
        if (degrees == null) {
            facingOverrides.remove(spotName)
            prefs.remove(facingKey(spotName))
        } else {
            facingOverrides[spotName] = degrees
            prefs.putInt(facingKey(spotName), degrees)
        }
    }

    fun microSpotsFor(spotName: String): Flow<List<MicroSpot>> =
        sessionLogStore?.getMicroSpotsForSpot(spotName) ?: flowOf(emptyList())

    fun computePatternMatches(
        hourlyForecast: List<HourlyUiModel>,
        dailyTides: Map<LocalDate, DailyTideInfo>,
        idealSwellDirection: Int?
    ): List<PatternMatch> {
        val refs = referenceSessions.value
        if (refs.isEmpty()) return emptyList()

        return hourlyForecast.mapNotNull { hour ->
            val tideCoeff = dailyTides[hour.rawTime.date]?.coefficient
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

    // Écran de bienvenue (unité du vent, niveau, origine des prévisions) : affiché tant qu'il n'a
    // pas été fermé une fois, et rouvrable depuis les Paramètres.
    var showOnboarding by mutableStateOf(!prefs.getBoolean("onboarding_v1_done", false))
        private set

    fun dismissOnboarding() {
        showOnboarding = false
        prefs.putBoolean("onboarding_v1_done", true)
    }

    fun showOnboardingAgain() {
        showOnboarding = true
    }

    // Visite guidée de la première utilisation (accueil) et astuces du journal : une seule fois,
    // rouvrable depuis les Paramètres.
    var showHomeTour by mutableStateOf(!prefs.getBoolean("coach_home_v1_done", false))
        private set

    fun dismissHomeTour() {
        showHomeTour = false
        prefs.putBoolean("coach_home_v1_done", true)
    }

    fun showHomeTourAgain() {
        showHomeTour = true
    }

    var showJournalTips by mutableStateOf(!prefs.getBoolean("coach_journal_v1_done", false))
        private set

    fun dismissJournalTips() {
        showJournalTips = false
        prefs.putBoolean("coach_journal_v1_done", true)
    }

    var windUnit by mutableStateOf(prefs.getString("wind_unit", "kmh") ?: "kmh")
        private set

    var showLiveOverlay by mutableStateOf(prefs.getBoolean("show_live_overlay", true))
        private set

    var showWeeklyCard by mutableStateOf(prefs.getBoolean("show_weekly_card", true))
        private set

    var showDailyTimelineCard by mutableStateOf(prefs.getBoolean("show_daily_timeline_card", true))
        private set

    // Vue de l'écran principal : « simple » (semaine + journée) ou « detailed » (tous les encarts).
    // Taille de l'affichage : "normal", "large" ou "xlarge". Nouveau (introduction pas encore faite) : « large ».
    var displaySize by mutableStateOf(
        (prefs.getString("display_size", null) ?: (if (prefs.getBoolean("onboarding_v1_done", false)) "normal" else "large"))
            .also { if (prefs.getString("display_size", null) == null) prefs.putString("display_size", it) }
    )
        private set
    var viewMode by mutableStateOf(prefs.getString("view_mode", "simple") ?: "simple")
        private set
    // Vue simple : les meilleurs créneaux sont réduits à une ligne, qu'on ouvre si on veut.
    var bestSlotsOpen by mutableStateOf(prefs.getBoolean("best_slots_open", false))
        private set
    var starsOffset by mutableIntStateOf((prefs.getInt("stars_offset", 0)).coerceIn(-15, 15).also { StarsSettings.offset = it })
        private set
    var weeklyDensity by mutableIntStateOf(prefs.getInt("weekly_density", 1).coerceIn(1, 3))
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
    private val history = ForecastHistoryStore(prefs)

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

        // Première utilisation : seuls la semaine et le déroulé de la journée sont ouverts ;
        // houle, vent, mer de vent, météo et heure par heure sont repliés (chaque réglage
        // ensuite mémorisé).
        val savedCollapsed = prefs.getString("collapsed_cards", null) ?: DEFAULT_COLLAPSED_CARDS
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

    /** Non null = pas de réseau : les prévisions affichées datent de ce moment (ms). */
    var offlineSinceMillis by mutableStateOf<Long?>(null)
        private set

    private suspend fun loadSpotInternal(spot: SurfSpotItem) {
        try {
            repository.resetStale()
            val forecastResult = repository.getHybridForecast(spot.latitude, spot.longitude, engineConfig)
            offlineSinceMillis = repository.staleSinceMillis
            val forecast = forecastResult.hourly
            val grouped = forecast.groupBy { it.rawTime.date }

            val summaries = grouped.mapValues { (_, hours) ->
                val avgTemp = if (hours.isNotEmpty()) hours.map { it.temperature }.average().roundToInt() else 20
                DailySummaryUiModel(
                    avgFeelsLike = avgTemp,
                    avgWaterTemp = hours.mapNotNull { it.seaTemperature }.takeIf { it.isNotEmpty() }?.average()?.roundToInt()
                )
            }

            val today = nowLocalDateTime().date
            val fromDate = grouped.keys.minOrNull()?.toString() ?: today.toString()
            val toDate = grouped.keys.maxOrNull()?.toString() ?: today.plus(7, DateTimeUnit.DAY).toString()
            // Marées facultatives : un échec (API indisponible, navigateur qui bloque l'appel)
            // ne doit pas empêcher d'afficher houle et vent.
            val tidesBundle = try {
                repository.getTides(spot.latitude, spot.longitude, fromDate, toDate)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SurfRepository.TidesBundle(emptyMap(), emptyMap())
            }

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
            // Journal des prévisions (gardé 2 jours) : ne doit jamais faire échouer le chargement.
            runCatching { history.record(spot.name, engineConfig, forecast, tidesBundle.dailyByDate) }
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

    // Langue de l'appli (voir APP_LANGUAGES ; seul le français est traduit pour l'instant ; les autres langues viendront).
    var language by mutableStateOf(prefs.getString("app_language", "fr") ?: "fr")
        private set

    fun changeLanguage(code: String) {
        language = code
        prefs.putString("app_language", code)
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

    fun changeDisplaySize(size: String) {
        displaySize = if (size in listOf("normal", "large", "xlarge")) size else "normal"
        prefs.putString("display_size", displaySize)
    }

    fun cycleDisplaySize() = changeDisplaySize(when (displaySize) { "normal" -> "large"; "large" -> "xlarge"; else -> "normal" })

    fun changeViewMode(mode: String) {
        viewMode = if (mode == "detailed") "detailed" else "simple"
        prefs.putString("view_mode", viewMode)
    }

    fun toggleBestSlotsOpen() {
        bestSlotsOpen = !bestSlotsOpen
        prefs.putBoolean("best_slots_open", bestSlotsOpen)
    }

    fun changeStarsOffset(offset: Int) {
        starsOffset = offset.coerceIn(-15, 15)
        StarsSettings.offset = starsOffset
        prefs.putInt("stars_offset", starsOffset)
    }

    fun changeWeeklyDensity(density: Int) {
        weeklyDensity = density.coerceIn(1, 3)
        prefs.putInt("weekly_density", weeklyDensity)
    }

    fun changeWeeklyWindMode(mode: String) {
        weeklyWindMode = mode
        prefs.putString("weekly_wind_mode", mode)
    }

    // Mon profil : le badge « Recommandé » reste tant que l'utilisateur n'a rien réglé dans sa page.
    var profileReviewed by mutableStateOf(prefs.getBoolean("profile_reviewed_v1", false))
        private set

    fun markProfileReviewed() {
        if (profileReviewed) return
        profileReviewed = true
        prefs.putBoolean("profile_reviewed_v1", true)
    }

    // Mon matériel : âge, taille (cm), poids (kg), forme physique et niveau du calcul de volume (0 = non renseigné).
    // Restent sur l'appareil.
    var body by mutableStateOf(
        BodyState(
            ageYears = prefs.getInt("body_age", 0),
            heightCm = prefs.getInt("body_height_cm", 0),
            weightKg = prefs.getInt("body_weight_kg", 0),
            fitness = prefs.getInt("body_fitness", 0),
            volumeLevel = prefs.getInt("volume_level_idx", -1)
        )
    )
        private set

    fun changeBody(newBody: BodyState) {
        body = newBody
        prefs.putInt("body_age", newBody.ageYears)
        prefs.putInt("body_height_cm", newBody.heightCm)
        prefs.putInt("body_weight_kg", newBody.weightKg)
        prefs.putInt("body_fitness", newBody.fitness)
        prefs.putInt("volume_level_idx", newBody.volumeLevel)
    }

    // Tant que le profil n'est pas renseigné, un message cliquable remplace le « Meilleur créneau » de l'écran
    // principal. « Plus tard » le masque pour la session ; il revient à l'ouverture suivante.
    private var profileNudgeHidden by mutableStateOf(false)
    val showProfileNudge: Boolean get() = !profileReviewed && !profileNudgeHidden

    fun hideProfileNudge() {
        profileNudgeHidden = true
    }

    // Marée préférée : "any", "rising" (montant), "falling" (descendant), "high" (pleine mer), "low" (basse mer).
    var tidePreference by mutableStateOf(prefs.getString("tide_preference", "any") ?: "any")
        private set

    fun changeTidePreference(preference: String) {
        tidePreference = preference
        prefs.putString("tide_preference", preference)
    }

    // Profil Personnalisé gardé à part : passer sur Débutant (pour montrer à un élève) ne l'efface pas.
    var customProfile by mutableStateOf(
        (prefs.getString("custom_profile", "") ?: "").ifBlank { if (isCustomLevel(surferLevel)) surferLevel else "" }
    )
        private set

    fun saveCustomProfile(serialized: String) {
        customProfile = serialized
        prefs.putString("custom_profile", serialized)
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

    fun loadForecastHistory(): List<ForecastHistoryStore.Snapshot> =
        runCatching { history.load() }.getOrDefault(emptyList())

    /** Conditions gardées pour un jour passé (journal de bord J-1/J-2), ou null. */
    fun pastConditions(spotName: String, date: LocalDate): Pair<List<HourlyUiModel>, DailyTideInfo?>? =
        runCatching { history.conditionsFor(spotName, date) }.getOrNull()

    fun addQuiverBoard(
        model: String,
        family: String,
        lengthLitrage: String,
        finSetup: String,
        volumeL: Double? = null,
        volumeEstimated: Boolean = false
    ) {
        val dao = sessionLogStore ?: return
        scope.launch {
            dao.insertQuiverBoard(
                QuiverBoard(
                    model = model, family = family, lengthLitrage = lengthLitrage, finSetup = finSetup,
                    volumeL = volumeL, volumeEstimated = volumeEstimated
                )
            )
        }
    }

    fun deleteQuiverBoard(board: QuiverBoard) {
        val dao = sessionLogStore ?: return
        scope.launch {
            try {
                dao.deleteQuiverBoard(board)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Le plus souvent la contrainte de clé étrangère : planche utilisée par une session.
                val current = _uiState.value
                if (current is SurfUiState.Success) {
                    _uiState.value = current.copy(
                        popupError = "Impossible de supprimer cette planche : elle est utilisee dans une session enregistree."
                    )
                }
            }
        }
    }

    fun addMicroSpot(parentSpotName: String, name: String) {
        val dao = sessionLogStore ?: return
        scope.launch { dao.insertMicroSpot(MicroSpot(parentSpotName = parentSpotName, name = name)) }
    }

    fun updateMicroSpot(spot: MicroSpot) {
        val dao = sessionLogStore ?: return
        scope.launch { dao.updateMicroSpot(spot) }
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
        tideInfo: DailyTideInfo?,
        forecastScore: Int? = null
    ) {
        val dao = sessionLogStore ?: return
        scope.launch {
            val snapshot = ConditionSnapshot(
                energyKj = hourlyModel.energyKj,
                waveHeight = hourlyModel.waveHeight,
                wavePeriod = hourlyModel.wavePeriod,
                waveDirection = hourlyModel.waveDirection.roundToInt(),
                windSpeedKmh = hourlyModel.windSpeedKmh,
                windDirection = SurfUnitsHelper.cardinalToDegrees(hourlyModel.windDirectionStr).roundToInt(),
                tideCoeff = tideInfo?.coefficient,
                isNearHighTide = isNearHighTide(hourlyModel, tideInfo),
                tidePhase = tidePhaseAt(hourlyModel.rawTime.time, tideInfo),
                forecastScore = forecastScore
            )
            dao.logSession(
                startTime = LocalDateTime(date, LocalTime(startHour, 0)).toEpochMillis(),
                endTime = LocalDateTime(date, LocalTime(endHour.coerceAtMost(23), 0)).toEpochMillis(),
                microSpotId = microSpotId,
                quiverId = quiverId,
                condition = snapshot,
                rating = rating,
                comment = comment,
                mediaUri = mediaUri
            )
        }
    }

    fun dismissPopupError() {
        val current = _uiState.value
        if (current is SurfUiState.Success) {
            _uiState.value = current.copy(popupError = null)
        }
    }

    companion object {
        private const val RESUME_REFRESH_AFTER_MINUTES = 15
        const val DEFAULT_COLLAPSED_CARDS = "surf,wind,windSea,weather,hourly"
        val DEFAULT_CARDS_ORDER = listOf("weekly", "dailyTimeline", "surf", "wind", "windSea", "weather", "hourly")
    }
}
