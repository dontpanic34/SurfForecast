@file:Suppress("SpellCheckingInspection")
package com.surfcast.surfforecast

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.surfcast.surfforecast.ui.theme.AppColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun MainScreen(
    viewModel: SurfController,
    // Épinglage du widget d'accueil (Android seulement) : null = bouton masqué.
    onPinWidget: (() -> Unit)? = null,
    // Version installée, affichée à côté de "Mis à jour à" (ex: "1.0.19").
    appVersion: String? = null,
    // Sauvegarde du journal dans un fichier (version web seulement).
    backup: SessionLogBackup? = null,
    // Installation du site comme appli (version web seulement).
    install: AppInstall? = null,
    // Hôte (Android) : lecteur webcam intégré. null = on ouvre la page de la webcam dans le navigateur.
    liveCamOverlay: (@Composable (LiveCamContext) -> Unit)? = null,
    // Hôte (Android) : bandeau « nouvelle version disponible » (APK).
    updateBanner: (@Composable () -> Unit)? = null
) {
    var showLiveCam by remember { mutableStateOf(false) }
    var directWebcamSpot by remember { mutableStateOf<String?>(null) }
    // "clock" = heure pleine la plus proche (10h44 -> 11h), qui avance toute seule : avant,
    // "maintenant" n'était calculé qu'au chargement.
    var clock by remember { mutableStateOf(nearestHourLocalDateTime()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            val nearest = nearestHourLocalDateTime()
            if (nearest != clock) clock = nearest
        }
    }
    val today = nowLocalDateTime().date

    // Positions à l'écran des éléments éclairés par la visite guidée.
    val coachTargets = remember { mutableStateMapOf<String, androidx.compose.ui.geometry.Rect>() }

    val uiState by viewModel.uiState.collectAsState()
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    var showSpotDialog by remember { mutableStateOf(false) }
    var showPreferencesDialog by remember { mutableStateOf(false) }
    var showSessionLogDialog by remember { mutableStateOf(false) }
    var showSessionLogEntry by remember { mutableStateOf(false) }
    var showQuiverDialog by remember { mutableStateOf(false) }
    // Page des Paramètres à ouvrir directement (« profile » depuis la visite guidée).
    var preferencesStartPage by remember { mutableStateOf<String?>(null) }
    var showWeatherDetail by remember { mutableStateOf(false) }
    val mainListState = rememberLazyListState()
    val navScope = rememberCoroutineScope()
    var showWebcamDirectoryDialog by remember { mutableStateOf(false) }
    var showForecastHistory by remember { mutableStateOf(false) }
    // Le lecteur webcam intégré (WebView côté Android) n'est pas encore porté : on ouvre
    // la page de la webcam dans le navigateur, sur Android comme sur iOS.
    val uriHandler = LocalUriHandler.current
    fun openLiveCam(spotName: String) {
        if (liveCamOverlay != null) {
            directWebcamSpot = spotName
            showLiveCam = true
            return
        }
        SurfWebcamHelper.getCamerasForSpot(spotName).cameras.firstOrNull()?.let { uriHandler.openUri(SurfWebcamHelper.liveCamUrl(spotName, it)) }
    }

    var showNoWebcam by remember { mutableStateOf(false) }
    if (showNoWebcam) {
        AlertDialog(
            onDismissRequest = { showNoWebcam = false },
            title = { Text("Pas de webcam", fontWeight = FontWeight.Bold) },
            text = { Text("Ce spot n'a pas encore de webcam dans l'appli. Choisis un autre spot pour voir sa caméra en direct.") },
            confirmButton = { TextButton(onClick = { showNoWebcam = false }) { Text("OK") } }
        )
    }

    val backgroundColor = MaterialTheme.colorScheme.background
    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val primaryColor = MaterialTheme.colorScheme.primary

    // Première ouverture : unité du vent, niveau et origine des prévisions (une fois les données là,
    // pour ne pas masquer l'écran de démarrage).
    if (viewModel.showOnboarding && uiState !is SurfUiState.Loading) {
        OnboardingDialog(
            displaySize = viewModel.displaySize,
            onDisplaySizeChanged = { viewModel.changeDisplaySize(it) },
            surferLevel = viewModel.surferLevel,
            onSurferLevelChanged = { viewModel.changeSurferLevel(it) },
            onDone = { viewModel.dismissOnboarding() }
        )
    }

    val popupError = (uiState as? SurfUiState.Success)?.popupError
    if (popupError != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissPopupError() },
            title = { Text(text = "Attention", fontWeight = FontWeight.Bold) },
            text = { Text(text = popupError) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissPopupError() }) {
                    Text("OK")
                }
            }
        )
    }

    if (showSpotDialog) {
        SpotSelectionDialog(
            currentFavorites = viewModel.favoriteSpots,
            onDismiss = { showSpotDialog = false },
            onSpotSelected = { newSpot -> viewModel.updateActiveSpotFavorite(newSpot) },
            onAssignToSlot = { spotName, slotIndex -> viewModel.assignSpotToFavoriteSlot(spotName, slotIndex) },
            onRemoveFromSlot = { slotIndex -> viewModel.removeFavoriteSlot(slotIndex) },
            onOpenLiveCam = { spotWithCam -> openLiveCam(spotWithCam) }
        )
    }

    if (showPreferencesDialog) {
        SurfPreferencesDialog(
            appVersion = appVersion,
            windUnit = viewModel.windUnit,
            onWindUnitSelected = { newUnit -> viewModel.changeWindUnit(newUnit) },
            showLiveOverlay = viewModel.showLiveOverlay,
            onToggleLiveOverlay = { viewModel.toggleLiveOverlay(it) },
            showWeeklyCard = viewModel.showWeeklyCard,
            onToggleWeeklyCard = { viewModel.toggleWeeklyCard(it) },
            cardsOrder = viewModel.cardsOrder.toList(),
            onMoveCardUp = { viewModel.moveCardUp(it) },
            onMoveCardDown = { viewModel.moveCardDown(it) },
            displaySize = viewModel.displaySize,
            onDisplaySizeChanged = { viewModel.changeDisplaySize(it) },
            starsOffset = viewModel.starsOffset,
            onStarsOffsetChanged = { viewModel.changeStarsOffset(it) },
            weeklyDensity = viewModel.weeklyDensity,
            onWeeklyDensityChanged = { viewModel.changeWeeklyDensity(it) },
            weeklyWindMode = viewModel.weeklyWindMode,
            onWeeklyWindModeChanged = { viewModel.changeWeeklyWindMode(it) },
            showDailyTimelineCard = viewModel.showDailyTimelineCard,
            onToggleDailyTimelineCard = { viewModel.toggleDailyTimelineCard(it) },
            showSurfCard = viewModel.showSurfCard,
            onToggleSurfCard = { viewModel.toggleSurfCard(it) },
            showWindCard = viewModel.showWindCard,
            onToggleWindCard = { viewModel.toggleWindCard(it) },
            showWindSeaCard = viewModel.showWindSeaCard,
            onToggleWindSeaCard = { viewModel.toggleWindSeaCard(it) },
            showWeatherCard = viewModel.showWeatherCard,
            onToggleWeatherCard = { viewModel.toggleWeatherCard(it) },
            showHourlyCard = viewModel.showHourlyCard,
            onToggleHourlyCard = { viewModel.toggleHourlyCard(it) },
            surferLevel = viewModel.surferLevel,
            onSurferLevelChanged = { viewModel.changeSurferLevel(it); viewModel.markProfileReviewed() },
            themeMode = viewModel.themeMode,
            language = viewModel.language,
            onLanguageChanged = { viewModel.changeLanguage(it) },
            onThemeModeChanged = { viewModel.changeThemeMode(it) },
            customProfile = viewModel.customProfile,
            onCustomProfileSaved = { viewModel.saveCustomProfile(it) },
            tidePreference = viewModel.tidePreference,
            onTidePreferenceChanged = { viewModel.changeTidePreference(it); viewModel.markProfileReviewed() },
            engineConfig = viewModel.engineConfig,
            onEngineConfigChanged = { viewModel.updateEngineConfig(it) },
            onViewLogs = {
                showPreferencesDialog = false
                showForecastHistory = true
            },
            onShowIntro = {
                showPreferencesDialog = false
                viewModel.showOnboardingAgain()
            },
            spotName = (uiState as? SurfUiState.Success)?.spotName ?: "",
            beachFacing = (uiState as? SurfUiState.Success)?.spotName?.let { viewModel.facingFor(it) },
            defaultBeachFacing = (uiState as? SurfUiState.Success)?.spotName?.let { viewModel.defaultFacingFor(it) },
            onBeachFacingChanged = (uiState as? SurfUiState.Success)?.spotName?.let { name -> { deg: Int? -> viewModel.setFacing(name, deg) } },
            onShowTour = {
                showPreferencesDialog = false
                viewModel.showHomeTourAgain()
            },
            onDismiss = { showPreferencesDialog = false; preferencesStartPage = null },
            onPinWidget = onPinWidget,
            backup = backup,
            install = install,
            quiverBoards = viewModel.quiverBoards.collectAsState().value,
            body = viewModel.body,
            onBodyChanged = { viewModel.changeBody(it); viewModel.markProfileReviewed() },
            onAddBoard = { model, family, dims, fins, vol, est -> viewModel.addQuiverBoard(model, family, dims, fins, vol, est); viewModel.markProfileReviewed() },
            onDeleteBoard = { viewModel.deleteQuiverBoard(it) },
            startPage = preferencesStartPage,
            profileReviewed = viewModel.profileReviewed
        )
    }

    if (showForecastHistory) {
        val snapshots = remember { viewModel.loadForecastHistory() }
        ForecastHistoryScreen(
            snapshots = snapshots,
            currentSpotName = (uiState as? SurfUiState.Success)?.spotName,
            onDismiss = { showForecastHistory = false }
        )
    }

    if (showWebcamDirectoryDialog) {
        AlertDialog(
            onDismissRequest = { showWebcamDirectoryDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WebcamIcon(tint = AppColors.WindMid, size = 20.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Webcams Disponibles", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    SurfWebcamHelper.allSpotsWithCameras.forEach { spotEntry ->
                        item {
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Text(
                                    text = spotEntry.spotDisplayName,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                spotEntry.cameras.forEach { cam ->
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable {
                                                showWebcamDirectoryDialog = false
                                                if (liveCamOverlay != null) {
                                                    directWebcamSpot = spotEntry.spotDisplayName
                                                    showLiveCam = true
                                                } else {
                                                    uriHandler.openUri(SurfWebcamHelper.liveCamUrl(spotEntry.spotDisplayName, cam))
                                                }
                                            },
                                        color = MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            WebcamIcon(tint = onSurfaceColor, size = 15.dp)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(text = cam.camName, fontSize = 12.sp, color = onSurfaceColor)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showWebcamDirectoryDialog = false }) {
                    Text("Fermer")
                }
            }
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = backgroundColor
    ) {
        val isLoading = uiState is SurfUiState.Loading
        Crossfade(
            targetState = isLoading,
            animationSpec = tween(durationMillis = 450),
            label = "splash_to_main"
        ) { loading ->
            if (loading) {
                SplashScreen()
            } else {
        when (val state = uiState) {
            is SurfUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = primaryColor)
                }
            }
            is SurfUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = state.message, color = MaterialTheme.colorScheme.error)
                }
            }
            is SurfUiState.Success -> {
                val groupedByDate = state.hourlyForecast.groupBy { it.rawTime.date }
                val availableDates = groupedByDate.keys.toList()

                // Aussi après minuit ou un rechargement : un jour qui n'est plus dans les
                // prévisions ne doit pas rester sélectionné (cartes vides).
                if ((selectedDate == null || selectedDate !in availableDates) && availableDates.isNotEmpty()) {
                    selectedDate = availableDates.first()
                }

                val currentTideInfo = state.dailyTides[today] ?: state.dailyTides.values.firstOrNull()

                // Point 3 : angle de houle ideal du spot actif (peut etre null si pas encore renseigne)
                // et meilleur creneau du jour selectionne, pour le bandeau "Statut Flash".
                val idealSwellDirection = viewModel.facingFor(state.spotName)
                // Journal de session : meilleur "Pattern repere" dans les previsions a 7 jours
                // par rapport aux sessions passees notees >= 4/5.
                val referenceSessions by viewModel.referenceSessions.collectAsState()
                val bestPatternMatch = remember(state.hourlyForecast, state.dailyTides, idealSwellDirection, referenceSessions) {
                    viewModel.computePatternMatches(state.hourlyForecast, state.dailyTides, idealSwellDirection)
                        .maxByOrNull { it.score }
                }

                // Fiches des bancs du spot : créneaux du jour sélectionné où la fiche est respectée.
                val spotMicroSpots by remember(state.spotName) { viewModel.microSpotsFor(state.spotName) }
                    .collectAsState(initial = emptyList())

                // Invitation au profil, bancs et « pattern » : après le déroulé de la journée,
                // pour que les vagues (semaine, journée) soient la première chose qu'on voit.
                val bestInfo: @Composable () -> Unit = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(surfaceColor)
                    ) {
                                    // Profil pas encore renseigné : un message cliquable l'invite à le remplir.
                                    if (viewModel.showProfileNudge) {
                                        ProfileNudgeCard(
                                            onOpen = { preferencesStartPage = "profile"; showPreferencesDialog = true },
                                            onLater = { viewModel.hideProfileNudge() },
                                            modifier = Modifier.coachTarget("bestSlot", coachTargets).padding(horizontal = 10.dp, vertical = 2.dp).padding(bottom = 6.dp)
                                        )
                                    }

                                    // Fiches de bancs : « Le banc magique : 16h–18h (descendant · 1,0–1,6 m) ».
                                    run {
                                        val day = selectedDate ?: today
                                        val dayHours = daylightHoursFor(day, groupedByDate, state.dailySunInfo)
                                        spotMicroSpots.filter { it.hasProfile }.forEach { spot ->
                                            val windows = spot.matchingWindows(dayHours, state.dailyTides[day])
                                            if (windows.isNotEmpty()) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 10.dp, vertical = 2.dp)
                                                        .padding(bottom = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Canvas(modifier = Modifier.size(6.dp)) {
                                                        drawCircle(color = AppColors.WindMid, radius = size.minDimension / 2f)
                                                    }
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = "${spot.name} : " +
                                                            windows.joinToString(", ") { "${it.first}h–${it.last + 1}h" } +
                                                            " (${spot.profileSummary()})",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = onSurfaceColor.copy(alpha = 0.75f),
                                                        maxLines = 2
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Journal de session : bandeau "Pattern repere" si un creneau a venir
                                    // matche une session passee bien notee.
                                    if (bestPatternMatch != null) {
                                        val refDate = epochMillisToLocalDateTime(bestPatternMatch.referenceSession.session.startTime).date
                                        val refDateFormatted = "${refDate.day} ${frenchMonthName(refDate)}"
                                        val matchDate = bestPatternMatch.hourlyModel.rawTime.date
                                        val matchDateFormatted = "${frenchShortDayName(matchDate)} ${matchDate.day}"
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 2.dp)
                                                .padding(bottom = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Canvas(modifier = Modifier.size(6.dp)) {
                                                drawCircle(color = AppColors.TideLow, radius = size.minDimension / 2f)
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Pattern repéré $matchDateFormatted ${bestPatternMatch.hourlyModel.rawTime.hour}h : " +
                                                    "match ${bestPatternMatch.score}% avec ta session du $refDateFormatted à " +
                                                    "${bestPatternMatch.referenceSession.microSpot.name} (${bestPatternMatch.referenceSession.quiverBoard.model})",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = onSurfaceColor.copy(alpha = 0.75f),
                                                maxLines = 2
                                            )
                                        }
                                    }
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    run {
                        val selectedIndex = availableDates.indexOf(selectedDate).coerceAtLeast(0)
                        val fixedMaxScale = 4.0f

                        val dailyPeriods = availableDates.map { date ->
                            val dailyData = daylightHoursFor(date, groupedByDate, state.dailySunInfo)
                            dailyData.maxByOrNull { it.waveHeight }?.wavePeriod?.roundToInt() ?: 10
                        }

                        val dailyHeights = availableDates.map { date ->
                            val dailyData = daylightHoursFor(date, groupedByDate, state.dailySunInfo)
                            dailyData.maxByOrNull { it.waveHeight }?.waveHeight ?: 0.0
                        }

                        // Notation avec la mémoire du vent (la mer garde le vent qui vient de souffler).
                        val scoringByDate = availableDates.associateWith { hoursWithWindMemory(it, groupedByDate) }
                        val dailyStars = availableDates.map { date ->
                            val hours = daylightHoursFor(date, scoringByDate, state.dailySunInfo)
                            // Nuit offshore : soirée de la veille (21 h et après) + petit matin (avant 7 h).
                            val night = groupedByDate[date.minus(1, DateTimeUnit.DAY)].orEmpty().filter { it.rawTime.hour >= 21 } +
                                groupedByDate[date].orEmpty().filter { it.rawTime.hour < 7 }
                            dayQualityScore(
                                hours, idealSwellDirection, viewModel.surferLevel, state.dailyTides[date], viewModel.tidePreference,
                                offshoreNight = hasOffshoreNight(night, idealSwellDirection)
                            )?.let { starsForScore(it) }
                        }

                        val dailyFeelsLike = availableDates.map { date ->
                            state.dailySummaries[date]?.avgFeelsLike ?: 20
                        }

                        val dailyWaterTemps = availableDates.map { date ->
                            state.dailySummaries[date]?.avgWaterTemp
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .systemBarsPadding()
                        ) {
                            LazyColumn(
                                state = mainListState,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp),
                                contentPadding = PaddingValues(bottom = 16.dp)
                            ) {
                                item {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 4.dp, vertical = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        FavoritesHeaderRow(
                                            favoriteSpots = viewModel.favoriteSpots.take(3),
                                            activeFavoriteIndex = viewModel.activeFavoriteIndex,
                                            onSelectFavorite = { index -> viewModel.selectFavoriteIndex(index) },
                                            onOpenSpotDialog = { showSpotDialog = true },
                                            surfaceColor = surfaceColor,
                                            onSurfaceColor = onSurfaceColor,
                                            modifier = Modifier.weight(1f)
                                        )

                                    }

                                    if (showSessionLogDialog) {
                                        val allSessions by viewModel.allSessions.collectAsState()

                                        SessionLogHistoryScreen(
                                            allSessions = allSessions,
                                            onOpenNewEntry = { showSessionLogEntry = true },
                                            onOpenQuiver = { showQuiverDialog = true },
                                            onDismiss = { showSessionLogDialog = false }
                                        )
                                    }

                                    if (showWeatherDetail) {
                                        WeatherDetailScreen(
                                            spotName = state.spotName,
                                            groupedByDate = groupedByDate,
                                            availableDates = availableDates,
                                            windUnit = viewModel.windUnit,
                                            onDismiss = { showWeatherDetail = false }
                                        )
                                    }

                                    if (showQuiverDialog) {
                                        val quiverBoards by viewModel.quiverBoards.collectAsState()

                                        QuiverScreen(
                                            quiverBoards = quiverBoards,
                                            surferLevel = viewModel.surferLevel,
                                            body = viewModel.body,
                                            onBodyChanged = { viewModel.changeBody(it) },
                                            onAddBoard = { model, family, dims, fins, vol, est -> viewModel.addQuiverBoard(model, family, dims, fins, vol, est) },
                                            onDeleteBoard = { board -> viewModel.deleteQuiverBoard(board) },
                                            onDismiss = { showQuiverDialog = false }
                                        )
                                    }

                                    if (showSessionLogEntry) {
                                        val todayHours = groupedByDate[today] ?: state.hourlyForecast
                                        val todayTide = state.dailyTides[today]
                                        val quiverBoards by viewModel.quiverBoards.collectAsState()
                                        val microSpots by remember(state.spotName) { viewModel.microSpotsFor(state.spotName) }
                                            .collectAsState(initial = emptyList())

                                        // J-1 / J-2 : conditions gardees par le journal des previsions
                                        // (la derniere prevision faite pour ce jour-la).
                                        val pastConditions = remember(state.spotName, today) {
                                            (1..2).associateWith { offset ->
                                                viewModel.pastConditions(state.spotName, today.minus(offset, DateTimeUnit.DAY))
                                            }.filterValues { it != null && it.first.isNotEmpty() }
                                        }

                                        SessionLogEntryDialog(
                                            spotName = state.spotName,
                                            todayHours = todayHours,
                                            tideInfo = todayTide,
                                            quiverBoards = quiverBoards,
                                            weightKg = viewModel.body.weightKg,
                                            onOpenGear = { showQuiverDialog = true },
                                            microSpots = microSpots,
                                            onAddMicroSpot = { name -> viewModel.addMicroSpot(state.spotName, name) },
                                            onUpdateMicroSpot = { viewModel.updateMicroSpot(it) },
                                            showTips = viewModel.showJournalTips,
                                            onDismissTips = { viewModel.dismissJournalTips() },
                                            availableDayOffsets = listOf(0) + pastConditions.keys.sorted(),
                                            onSave = { dayOffset, startHour, endHour, microSpotId, quiverId, rating, comment, mediaUri ->
                                                val past = pastConditions[dayOffset]
                                                val dayHours = if (dayOffset == 0) todayHours else past?.first.orEmpty()
                                                val dayTide = if (dayOffset == 0) todayTide else past?.second
                                                val midpointHour = (startHour + endHour) / 2
                                                val hourlyModel = dayHours.minByOrNull { abs(it.rawTime.hour - midpointHour) }
                                                if (hourlyModel != null) {
                                                    viewModel.logSurfSession(
                                                        date = today.minus(dayOffset, DateTimeUnit.DAY),
                                                        startHour = startHour,
                                                        endHour = endHour,
                                                        microSpotId = microSpotId,
                                                        quiverId = quiverId,
                                                        rating = rating,
                                                        comment = comment,
                                                        mediaUri = mediaUri,
                                                        hourlyModel = hourlyModel,
                                                        tideInfo = dayTide,
                                                        forecastScore = calculateSlotRating(
                                                            hourlyModel, idealSwellDirection, viewModel.surferLevel,
                                                            isNearHighTide(hourlyModel, dayTide), viewModel.tidePreference, dayTide
                                                        ).let { if (it.tooBig) -1 else it.score }
                                                    )
                                                }
                                            },
                                            onDismiss = { showSessionLogEntry = false }
                                        )
                                    }

                                    updateBanner?.invoke()

                                    viewModel.offlineSinceMillis?.let { ms ->
                                        val dt = epochMillisToLocalDateTime(ms)
                                        Text(
                                            text = "📴 Pas de réseau : prévisions du ${dt.dayOfMonth}/${dt.monthNumber} à ${dt.hour}h${dt.minute.toString().padStart(2, '0')}",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }

                                    run {
                                        val updated = if (state.lastUpdatedTime.isNotEmpty()) "Mis à jour à ${state.lastUpdatedTime}" else ""
                                        Text(
                                            text = updated,
                                            fontSize = 10.5.sp,
                                            color = onSurfaceColor.copy(alpha = 0.5f),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(2.dp))
                                }

                                selectedDate?.let { date ->
                                    val hoursForSelectedDay = groupedByDate[date] ?: emptyList()
                                    val dailyTide = state.dailyTides[date]

                                    item {
                                        DynamicCardsSection(
                                            afterTimeline = bestInfo,
                                            hoursForSelectedDay = hoursForSelectedDay,
                                            dailyTideInfo = dailyTide,
                                            isToday = (date == today),
                                            currentHour = clock.hour,
                                            viewModel = viewModel,
                                            availableDates = availableDates,
                                            groupedByDate = groupedByDate,
                                            selectedDate = selectedDate,
                                            onSelectDate = { newDate -> selectedDate = newDate },
                                            dailyTides = state.dailyTides,
                                            dailySunInfo = state.dailySunInfo,
                                            dailyPeriods = dailyPeriods,
                                            dailyHeights = dailyHeights,
                                            dailyFeelsLike = dailyFeelsLike,
                                            dailyStars = dailyStars,
                                            dailyWaterTemps = dailyWaterTemps,
                                            fixedMaxScale = fixedMaxScale,
                                            selectedIndex = selectedIndex,
                                            primaryColor = primaryColor,
                                            surfaceColor = surfaceColor,
                                            onSurfaceColor = onSurfaceColor,
                                            idealSwellDirection = idealSwellDirection,
                                            surferLevel = viewModel.surferLevel,
                                            tidePreference = viewModel.tidePreference,
                                            coachTargets = coachTargets
                                        )
                                    }
                                }
                            }

                            // « Maintenant » + marée + eau : toujours visible, juste au-dessus de la barre du bas.
                            run {
                                val todayHours = groupedByDate[today].orEmpty()
                                val closest = todayHours.minByOrNull { abs(it.rawTime.hour - clock.hour) }
                                    ?: state.hourlyForecast.firstOrNull()
                                if (viewModel.showLiveOverlay && closest != null) {
                                    NowTideCard(
                                        hourlyModel = closest,
                                        tideInfo = currentTideInfo,
                                        windUnit = viewModel.windUnit,
                                        seaTemperature = (closest.seaTemperature
                                            ?: todayHours.mapNotNull { it.seaTemperature }.takeIf { it.isNotEmpty() }?.average())
                                            ?.let { kotlin.math.round(it).toInt() },
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)
                                    )
                                }
                            }

                            // Barre du bas : Prévisions, Journal, Météo, Réglages (toujours visible, à portée de pouce).
                            // Journal et Météo s'ouvrent depuis le haut de la liste : on y remonte d'abord.
                            BottomNavBar(
                                onForecast = { navScope.launch { mainListState.animateScrollToItem(0) } },
                                onJournal = { navScope.launch { mainListState.scrollToItem(0); showSessionLogDialog = true } },
                                onWeather = { navScope.launch { mainListState.scrollToItem(0); showWeatherDetail = true } },
                                onSettings = { preferencesStartPage = null; showPreferencesDialog = true },
                                hasWebcam = SurfWebcamHelper.hasCamera(state.spotName),
                                onWebcam = { if (SurfWebcamHelper.hasCamera(state.spotName)) openLiveCam(state.spotName) else showNoWebcam = true },
                                coachTargets = coachTargets
                            )
                        }
                    }
                }
            }
        }
            }
        }
        if (showLiveCam && liveCamOverlay != null) {
            liveCamOverlay(
                LiveCamContext(
                    spotName = directWebcamSpot ?: ((uiState as? SurfUiState.Success)?.spotName ?: ""),
                    onClose = {
                        showLiveCam = false
                        directWebcamSpot = null
                    },
                    onSwitchSpot = { newSpot -> directWebcamSpot = newSpot },
                    showLiveOverlay = viewModel.showLiveOverlay,
                    windUnit = viewModel.windUnit,
                    loadLiveConditions = { spot -> viewModel.liveConditionsFor(spot) }
                )
            )
        }
        // Visite guidée de la première utilisation (une fois l'écran de bienvenue fermé).
        if (viewModel.showHomeTour && !viewModel.showOnboarding) {
            CoachMarkOverlay(
                steps = listOf(
                    CoachStep(
                        "settings", "⭐ Des prévisions 100 % sur-mesure",
                        "Renseigner ton profil est LE réglage à faire en premier.\n" +
                            "• Sans profil : les notes sont celles d'un surfeur moyen, et un message t'invite à remplir ton profil.\n" +
                            "• Avec ton profil : les scores, les couleurs et la limite violette « trop gros » sont calculés pour toi.\n" +
                            "• Ce qu'il prend en compte : ton niveau (Débutant, Intermédiaire, Confirmé, Expert) ou ton réglage personnalisé (énergie de vague, tolérance au vent, aux rafales, au clapot).\n" +
                            "• Tes planches : ajoute-les avec leur volume, tu les retrouveras dans le journal.\n" +
                            "Une minute suffit, et les scores, les étoiles et le « trop gros » deviennent les tiens.",
                        actionLabel = "Renseigner mon profil maintenant",
                        onAction = { preferencesStartPage = "profile"; showPreferencesDialog = true }
                    ),
                    CoachStep("journal", "📓 Le journal de bord", "Note tes sessions en quelques secondes : l'appli enregistre toute seule la houle, le vent et la marée, et compare ce qu'elle avait prévu avec ton ressenti (tes étoiles). Au fil du temps, tu vois dans quelles conditions tu surfes le mieux."),
                    CoachStep(
                        "weekCard", "📅 Un écran à ta façon",
                        "Touche un jour de la semaine pour le détailler. Chaque encart (semaine, déroulé, vagues, vent, météo…) s'adapte à toi :\n" +
                            "• Masquer ou afficher : dans Paramètres › Affichage.\n" +
                            "• Replier : touche la flèche de l'encart pour ne garder que son titre.\n" +
                            "• Déplacer : fais glisser la poignée de l'encart pour changer l'ordre.\n" +
                            "Garde seulement ce qui t'intéresse."
                    ),
                    CoachStep("settings", "⚙️ Les paramètres", "Mon profil et mon matériel, modèles de prévision, orientation de la plage, unités, affichage, sauvegarde de tes données. Tu peux aussi y rejouer cette visite (Aide).")
                ),
                targets = coachTargets,
                onFinish = { viewModel.dismissHomeTour() }
            )
        }
    }
}

@Composable
fun ThemeToggleIcon(
    isDarkActive: Boolean,
    backgroundColor: Color,
    iconColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val center = Offset(w / 2f, h / 2f)

        if (isDarkActive) {
            val mainRadius = w * 0.36f
            drawCircle(color = iconColor, radius = mainRadius, center = center)

            val cutoutRadius = w * 0.30f
            val cutoutCenter = Offset(center.x + w * 0.16f, center.y - h * 0.12f)
            drawCircle(color = backgroundColor, radius = cutoutRadius, center = cutoutCenter)
        } else {
            val sunRadius = w * 0.22f
            drawCircle(color = iconColor, radius = sunRadius, center = center)

            val rayStart = w * 0.32f
            val rayLength = w * 0.16f
            for (i in 0 until 8) {
                val angle = (i * 45f) * (PI.toFloat() / 180f)
                val dx = cos(angle)
                val dy = sin(angle)
                val start = Offset(center.x + dx * rayStart, center.y + dy * rayStart)
                val end = Offset(center.x + dx * (rayStart + rayLength), center.y + dy * (rayStart + rayLength))
                drawLine(
                    color = iconColor,
                    start = start,
                    end = end,
                    strokeWidth = 1.4.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

@Composable
fun WebcamIcon(tint: Color, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.08f, h * 0.22f),
            size = Size(w * 0.58f, h * 0.56f),
            cornerRadius = CornerRadius(w * 0.1f, w * 0.1f)
        )

        val lensPath = Path().apply {
            moveTo(w * 0.66f, h * 0.36f)
            lineTo(w * 0.92f, h * 0.24f)
            lineTo(w * 0.92f, h * 0.76f)
            lineTo(w * 0.66f, h * 0.64f)
            close()
        }
        drawPath(lensPath, color = tint)

        drawCircle(
            color = Color.Red,
            radius = w * 0.075f,
            center = Offset(w * 0.25f, h * 0.40f)
        )
    }
}

@Composable
fun FavoritesHeaderRow(
    favoriteSpots: List<String?>,
    activeFavoriteIndex: Int,
    onSelectFavorite: (Int) -> Unit,
    onOpenSpotDialog: () -> Unit,
    surfaceColor: Color,
    onSurfaceColor: Color,
    modifier: Modifier = Modifier
) {
    val activeFavorites = favoriteSpots.mapIndexed { index, name -> index to name }.filter { it.second != null }

    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        activeFavorites.forEach { (index, spotName) ->
            val isSelected = activeFavoriteIndex == index
            Surface(
                modifier = Modifier.clickable { onSelectFavorite(index) },
                color = if (isSelected) MaterialTheme.colorScheme.primary else surfaceColor,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = spotName ?: "",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else onSurfaceColor,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }
        }

        Surface(
            modifier = Modifier.clickable { onOpenSpotDialog() },
            color = surfaceColor,
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = "...",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                color = onSurfaceColor,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
fun DynamicCardsSection(
    afterTimeline: @Composable () -> Unit,
    hoursForSelectedDay: List<HourlyUiModel>,
    dailyTideInfo: DailyTideInfo?,
    isToday: Boolean,
    viewModel: SurfController,
    availableDates: List<LocalDate>,
    groupedByDate: Map<LocalDate, List<HourlyUiModel>>,
    selectedDate: LocalDate?,
    onSelectDate: (LocalDate) -> Unit,
    dailyTides: Map<LocalDate, DailyTideInfo>,
    dailySunInfo: Map<LocalDate, DailySunInfo>,
    dailyPeriods: List<Int>,
    dailyHeights: List<Double>,
    dailyFeelsLike: List<Int>,
    dailyStars: List<Float?>,
    dailyWaterTemps: List<Int?>,
    fixedMaxScale: Float,
    selectedIndex: Int,
    primaryColor: Color,
    surfaceColor: Color,
    onSurfaceColor: Color,
    idealSwellDirection: Int?,
    surferLevel: String,
    tidePreference: String = "any",
    currentHour: Int = nowLocalDateTime().hour,
    coachTargets: MutableMap<String, androidx.compose.ui.geometry.Rect>? = null
) {
    // currentHour en clé : à chaque changement d'heure (ou rechargement), la sélection
    // revient sur l'heure actuelle au lieu de rester figée sur celle du chargement.
    val initialSelectedHour = remember(hoursForSelectedDay, isToday, currentHour) {
        if (isToday) {
            hoursForSelectedDay.minByOrNull { abs(it.rawTime.hour - currentHour) } ?: hoursForSelectedDay.firstOrNull()
        } else {
            hoursForSelectedDay.firstOrNull()
        }
    }

    var selectedHourlyItem by remember(hoursForSelectedDay, isToday, currentHour) { mutableStateOf(initialSelectedHour) }

    var draggedKey by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val itemHeights = remember { mutableStateMapOf<String, Int>() }

    fun renderableCardKeys(): List<String> = viewModel.cardsOrder.filter { key ->
        when (key) {
            "weekly" -> viewModel.showWeeklyCard
            "dailyTimeline" -> viewModel.showDailyTimelineCard
            "surf" -> viewModel.showSurfCard
            "wind" -> viewModel.showWindCard
            "windSea" -> viewModel.showWindSeaCard
            "weather" -> viewModel.showWeatherCard
            "hourly" -> viewModel.showHourlyCard
            else -> true
        }
    }

    // Glisser-déposer libre : on attrape la poignée, l'encart suit le doigt et les autres se
    // décalent pour lui faire de la place, quelle que soit la distance parcourue.
    fun dragModifierFor(cardKey: String): Modifier = Modifier.pointerInput(cardKey) {
        detectDragGestures(
            onDragStart = {
                draggedKey = cardKey
                dragOffsetY = 0f
            },
            onDragEnd = {
                draggedKey = null
                dragOffsetY = 0f
            },
            onDragCancel = {
                draggedKey = null
                dragOffsetY = 0f
            },
            onDrag = { change, dragAmount ->
                change.consume()
                dragOffsetY += dragAmount.y

                // Plusieurs encarts peuvent être franchis d'un seul geste rapide.
                var guard = 0
                while (guard++ < 12) {
                    val keys = renderableCardKeys()
                    val currentIndex = keys.indexOf(cardKey)
                    if (currentIndex == -1) break
                    if (dragOffsetY > 0f && currentIndex < keys.size - 1) {
                        val nextHeight = (itemHeights[keys[currentIndex + 1]] ?: 0).toFloat()
                        if (nextHeight > 0f && dragOffsetY > nextHeight / 2f) {
                            viewModel.moveCardDown(cardKey)
                            dragOffsetY -= nextHeight
                            continue
                        }
                    } else if (dragOffsetY < 0f && currentIndex > 0) {
                        val prevHeight = (itemHeights[keys[currentIndex - 1]] ?: 0).toFloat()
                        if (prevHeight > 0f && -dragOffsetY > prevHeight / 2f) {
                            viewModel.moveCardUp(cardKey)
                            dragOffsetY += prevHeight
                            continue
                        }
                    }
                    break
                }
            }
        )
    }

    val orderedKeys = renderableCardKeys()
    val afterKey = if ("dailyTimeline" in orderedKeys) "dailyTimeline" else orderedKeys.lastOrNull()

    Column(modifier = Modifier.fillMaxWidth()) {
        orderedKeys.forEach { cardKey ->
            val isCollapsed = viewModel.isCardCollapsed(cardKey)
            val isDragging = draggedKey == cardKey
            val dragMod = dragModifierFor(cardKey)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(if (isDragging) 1f else 0f)
                    .offset { IntOffset(0, (if (isDragging) dragOffsetY else 0f).roundToInt()) }
                    .then(if (isDragging) Modifier.shadow(6.dp) else Modifier)
                    .onGloballyPositioned { coordinates ->
                        itemHeights[cardKey] = coordinates.size.height
                    }
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    when (cardKey) {
                        "weekly" -> {
                            Spacer(modifier = Modifier.height(4.dp))
                            WeeklyForecastCard(
                                availableDates = availableDates,
                                groupedByDate = groupedByDate,
                                selectedDate = selectedDate,
                                onSelectDate = onSelectDate,
                                dailyTides = dailyTides,
                                dailyPeriods = dailyPeriods,
                                dailyHeights = dailyHeights,
                                dailyFeelsLike = dailyFeelsLike,
                                dailyWaterTemps = dailyWaterTemps,
                                dailyStars = dailyStars,
                                dailySunInfo = dailySunInfo,
                                fixedMaxScale = fixedMaxScale,
                                selectedIndex = selectedIndex,
                                surferLevel = surferLevel,
                                windUnit = viewModel.windUnit,
                                weeklyDensity = viewModel.weeklyDensity,
                                weeklyWindMode = viewModel.weeklyWindMode,
                                primaryColor = primaryColor,
                                surfaceColor = surfaceColor,
                                onSurfaceColor = onSurfaceColor,
                                isCollapsed = isCollapsed,
                                onToggleCollapse = { viewModel.toggleCardCollapsed(cardKey) },
                                dragHandleModifier = dragMod,
                                modifier = if (coachTargets != null) Modifier.fillMaxWidth().coachTarget("weekCard", coachTargets) else Modifier.fillMaxWidth()
                            )
                        }
                        "dailyTimeline" -> {
                            Spacer(modifier = Modifier.height(4.dp))
                            DailyTimelineCard(
                                selectedDate = selectedDate ?: availableDates.firstOrNull() ?: nowLocalDateTime().date,
                                groupedByDate = groupedByDate,
                                dailySunInfo = dailySunInfo,
                                dailyTides = dailyTides,
                                windUnit = viewModel.windUnit,
                                idealSwellDirection = idealSwellDirection,
                                surferLevel = surferLevel,
                                tidePreference = tidePreference,
                                scaleRawMax = availableDates.flatMap { daylightHoursFor(it, groupedByDate, dailySunInfo) }
                                    .maxOfOrNull { it.waveHeight },
                                selectedHour = selectedHourlyItem,
                                onHourSelected = { selectedHourlyItem = it },
                                isCollapsed = isCollapsed,
                                onToggleCollapse = { viewModel.toggleCardCollapsed(cardKey) },
                                dragHandleModifier = dragMod,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        "surf" -> {
                            Spacer(modifier = Modifier.height(4.dp))
                            selectedHourlyItem?.let { hourly ->
                                SurfCardComponent(
                                    selectedHour = hourly,
                                    allHoursOfDay = hoursForSelectedDay,
                                    onHourSelected = { selectedHourlyItem = it },
                                    isCollapsed = isCollapsed,
                                    onToggleCollapse = { viewModel.toggleCardCollapsed(cardKey) },
                                    dragHandleModifier = dragMod,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                        "wind" -> {
                            Spacer(modifier = Modifier.height(4.dp))
                            selectedHourlyItem?.let { hourly ->
                                WindCardComponent(
                                    selectedHour = hourly,
                                    allHoursOfDay = hoursForSelectedDay,
                                    windUnit = viewModel.windUnit,
                                    onHourSelected = { selectedHourlyItem = it },
                                    isCollapsed = isCollapsed,
                                    onToggleCollapse = { viewModel.toggleCardCollapsed(cardKey) },
                                    dragHandleModifier = dragMod,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                        "windSea" -> {
                            Spacer(modifier = Modifier.height(4.dp))
                            selectedHourlyItem?.let { hourly ->
                                WindSeaCardComponent(
                                    selectedHour = hourly,
                                    allHoursOfDay = hoursForSelectedDay,
                                    onHourSelected = { selectedHourlyItem = it },
                                    isCollapsed = isCollapsed,
                                    onToggleCollapse = { viewModel.toggleCardCollapsed(cardKey) },
                                    dragHandleModifier = dragMod,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                        "weather" -> {
                            Spacer(modifier = Modifier.height(4.dp))
                            selectedHourlyItem?.let { hourly ->
                                WeatherCardComponent(
                                    selectedHour = hourly,
                                    allHoursOfDay = hoursForSelectedDay,
                                    onHourSelected = { selectedHourlyItem = it },
                                    isCollapsed = isCollapsed,
                                    onToggleCollapse = { viewModel.toggleCardCollapsed(cardKey) },
                                    dragHandleModifier = dragMod,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                        "hourly" -> {
                            Spacer(modifier = Modifier.height(4.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = RoundedCornerShape(10.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp)) {
                                    CardControlsRow(
                                        title = "Prévision heure par heure",
                                        isCollapsed = isCollapsed,
                                        onToggleCollapse = { viewModel.toggleCardCollapsed(cardKey) },
                                        dragHandleModifier = dragMod,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                                    )

                                    if (!isCollapsed) {
                                        hoursForSelectedDay.forEach { hourlyData ->
                                            val isCurrentOrSelected = hourlyData.rawTime == selectedHourlyItem?.rawTime

                                            HourlyForecastRow(
                                                hourlyData = hourlyData,
                                                windUnit = viewModel.windUnit,
                                                modifier = Modifier.clickable {
                                                    selectedHourlyItem = hourlyData
                                                },
                                                dailyTideInfo = dailyTideInfo,
                                                isSelected = isCurrentOrSelected
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (cardKey == afterKey) afterTimeline()
                }
            }
        }
        if (afterKey == null) afterTimeline()
        Spacer(modifier = Modifier.height(6.dp))
    }
}


/** Barre de navigation du bas : l'écran des prévisions, le journal, la météo détaillée et les réglages, avec leur nom. */
@Composable
private fun BottomNavBar(
    onForecast: () -> Unit,
    onJournal: () -> Unit,
    onWeather: () -> Unit,
    onSettings: () -> Unit,
    hasWebcam: Boolean,
    onWebcam: () -> Unit,
    coachTargets: MutableMap<String, androidx.compose.ui.geometry.Rect>
) {
    val colors = MaterialTheme.colorScheme
    Surface(color = colors.surface, modifier = Modifier.fillMaxWidth()) {
        Column {
            HorizontalDivider(color = colors.onSurface.copy(alpha = 0.1f))
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                @Composable
                fun Item(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, icon: @Composable () -> Unit) {
                    Column(
                        modifier = modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onClick)
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(modifier = Modifier.height(22.dp), contentAlignment = Alignment.Center) { icon() }
                        Text(
                            label, fontSize = 10.5.sp, maxLines = 1,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) colors.primary else colors.onSurface.copy(alpha = 0.75f)
                        )
                    }
                }
                Item("Prévisions", true, onForecast) { WaveIcon(color = waterTempColor(), modifier = Modifier.size(20.dp)) }
                Item("Webcam", false, onWebcam) {
                    WebcamIcon(tint = if (hasWebcam) AppColors.WindMid else colors.onSurface.copy(alpha = 0.35f), size = 20.dp)
                }
                Item("Journal", false, onJournal, Modifier.coachTarget("journal", coachTargets)) {
                    JournalIcon(color = colors.onSurface, modifier = Modifier.size(20.dp))
                }
                Item("Météo", false, onWeather) { WeatherIcon("🌤️", 22.dp) }
                Item("Réglages", false, onSettings, Modifier.coachTarget("settings", coachTargets)) {
                    Icon(imageVector = SurfIcons.Settings, contentDescription = null, tint = colors.onSurface, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
