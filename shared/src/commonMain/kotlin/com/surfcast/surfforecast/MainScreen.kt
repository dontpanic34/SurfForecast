@file:Suppress("SpellCheckingInspection")
package com.surfcast.surfforecast

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.positionInRoot
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

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
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
    install: AppInstall? = null
) {
    // "clock" = heure pleine la plus proche (10h44 -> 11h), qui avance toute seule : avant,
    // "maintenant" n'était calculé qu'au chargement.
    var clock by remember { mutableStateOf(nearestHourLocalDateTime()) }
    LaunchedEffect(appVersion, viewModel.levelAnswers, viewModel.levelShift) { viewModel.reportStats(appVersion) }
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
    // Jour proposé à l'ouverture de la saisie : 1 quand on répond « Noter ma session » à « Tu as surfé ? » (hier).
    var entryInitialOffset by remember { mutableStateOf(0) }
    var showQuiverDialog by remember { mutableStateOf(false) }
    // Page des Paramètres à ouvrir directement (« profile » depuis la visite guidée).
    var preferencesStartPage by remember { mutableStateOf<String?>(null) }
    var showWeatherDetail by remember { mutableStateOf(false) }
    val mainListState = rememberLazyListState()
    // Le profil pour le choix de la planche du jour (poids, forme, niveau).
    GearPrefs.body = viewModel.body
    GearPrefs.levelIndex = volumeLevelIndex(viewModel.surferLevel)
    val navScope = rememberCoroutineScope()
    var showWebcamDirectoryDialog by remember { mutableStateOf(false) }
    var showForecastHistory by remember { mutableStateOf(false) }
    // Le lecteur webcam intégré (WebView côté Android) n'est pas encore porté : on ouvre
    // la page de la webcam dans le navigateur, sur Android comme sur iOS.
    val uriHandler = LocalUriHandler.current
    fun openLiveCam(spotName: String) {
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
    if (!viewModel.spotChosen && uiState !is SurfUiState.Loading) {
        SpotSelectionDialog(
            currentFavorites = viewModel.favoriteSpots,
            onDismiss = {},
            onSpotSelected = {},
            onAssignToSlot = { _, _ -> },
            onRemoveFromSlot = {},
            onOpenLiveCam = {},
            mandatory = true,
            onChosen = { viewModel.chooseFirstSpot(it) }
        )
    } else if (viewModel.showOnboarding && uiState !is SurfUiState.Loading) {
        // Après le choix du spot : directement le questionnaire de niveau, puis la visite guidée de l'accueil.
        androidx.compose.ui.window.Dialog(
            onDismissRequest = {},
            properties = androidx.compose.ui.window.DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            androidx.compose.material3.Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.background) {
                Column(
                    modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LevelQuestionnaire(
                        initial = viewModel.levelAnswers, initialShift = viewModel.levelShift, initialTolerance = viewModel.tolerance,
                        onDone = { a, shift, tol -> viewModel.saveLevelQuestionnaire(a, shift, tol); viewModel.dismissOnboarding() },
                        onClose = { viewModel.dismissOnboarding() }
                    )
                }
            }
        }
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

    // La section « Comprendre » s'ouvre de partout (accueil, ⚙ des encarts, Réglages) par ComprendreState.open.
    ComprendreState.request?.let { start -> ComprendreDialog(startLesson = start, onDismiss = { ComprendreState.close() }) }

    if (showPreferencesDialog) {
        SurfPreferencesDialog(
            modelPreview = viewModel.modelPreview,
            onNeedModelPreview = { viewModel.loadModelPreview() },
            appVersion = appVersion,
            devUnlocked = viewModel.devUnlocked,
            onDevUnlock = { viewModel.unlockDev() },
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
            exampleAnswers = viewModel.exampleAnswers,
            onExampleAnswersChanged = { viewModel.changeExampleAnswers(it) },
            levelQuiz = LevelQuizHost(viewModel.levelAnswers, viewModel.levelShift, viewModel.tolerance, viewModel.levelResult) { a, shift, tol -> viewModel.saveLevelQuestionnaire(a, shift, tol) },
            engineConfig = viewModel.engineConfig,
            onEngineConfigChanged = { viewModel.updateEngineConfig(it) },
            onViewLogs = {
                showPreferencesDialog = false
                showForecastHistory = true
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
            onUpdateBoard = { viewModel.updateQuiverBoard(it) },
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
                                                uriHandler.openUri(SurfWebcamHelper.liveCamUrl(spotEntry.spotDisplayName, cam))
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

                // L'heure choisie (houle, vent, météo et la ligne de temps de l'encart Actuellement s'accordent dessus) : remontée ici
                // pour que la ligne de temps du bas puisse la changer. Elle repart de l'heure actuelle (ou de 14 h un autre jour) quand
                // le jour, l'heure ou les prévisions changent.
                val shownHours = selectedDate?.let { groupedByDate[it] }.orEmpty()
                val shownIsToday = selectedDate == today
                val pickedHourState = remember(shownHours, shownIsToday, clock.hour) {
                    mutableStateOf<HourlyUiModel?>(
                        if (shownIsToday) shownHours.minByOrNull { abs(it.rawTime.hour - clock.hour) } ?: shownHours.firstOrNull()
                        else shownHours.minByOrNull { abs(it.rawTime.hour - 14) } ?: shownHours.firstOrNull()
                    )
                }

                // Point 3 : angle de houle ideal du spot actif (peut etre null si pas encore renseigne)
                // et meilleur creneau du jour selectionne, pour le bandeau "Statut Flash".
                val idealSwellDirection = viewModel.facingFor(state.spotName)
                // Journal de session : meilleur "Pattern repere" dans les previsions a 7 jours
                // par rapport aux sessions passees notees >= 4/5.
                val referenceSessions by viewModel.referenceSessions.collectAsState()
                val bestPatternMatch = remember(state.hourlyForecast, state.dailyTides, state.dailySunInfo, idealSwellDirection, referenceSessions) {
                    viewModel.computePatternMatches(state.hourlyForecast, state.dailyTides, idealSwellDirection)
                        // Seulement les heures où l'on peut surfer : entre le lever et le coucher du soleil (pas à minuit).
                        .filter { m ->
                            val sun = state.dailySunInfo[m.hourlyModel.rawTime.date]
                            sun == null || m.hourlyModel.rawTime.hour in sun.sunrise.hour..sun.sunset.hour
                        }
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

                                    // Journal : « Tu as surfé ? » à trois moments fixes : le matin à partir du lever du soleil et vers midi (on parle d'hier),
                                    // puis le soir à partir du coucher du soleil (on parle d'aujourd'hui). Hors de ces créneaux, il reste caché.
                                    run {
                                        val nowT = remember(clock) { nowLocalDateTime().time }
                                        val sun = state.dailySunInfo[today]
                                        val sunriseMin = (sun?.sunrise?.let { it.hour * 60 + it.minute }) ?: (8 * 60)
                                        val sunsetMin = (sun?.sunset?.let { it.hour * 60 + it.minute }) ?: (19 * 60)
                                        val nowMin = nowT.hour * 60 + nowT.minute
                                        val slot = when {
                                            nowMin in sunriseMin until (sunriseMin + 120) -> 0
                                            nowMin in (12 * 60) until (14 * 60) -> 1
                                            nowMin in sunsetMin until (sunsetMin + 180) -> 2
                                            else -> -1
                                        }
                                        val evening = slot == 2
                                        val target = if (evening) today else today.minus(1, DateTimeUnit.DAY)
                                        val sessions by viewModel.allSessions.collectAsState()
                                        val alreadyLogged = sessions.any { epochMillisToLocalDateTime(it.session.startTime).date == target }
                                        val past = remember(state.spotName, target) { viewModel.pastConditions(state.spotName, target) }
                                        val bestTarget = past?.first?.takeIf { it.isNotEmpty() }?.let { hrs ->
                                            hrs.maxOf { calculateSlotRating(it, idealSwellDirection, viewModel.surferLevel).let { r -> if (r.tooBig) -1 else r.score } }
                                        }
                                        if (slot >= 0 && !alreadyLogged && viewModel.surfedAskDismissed != target.toString() && bestTarget != null && bestTarget >= 40) {
                                            val top = past!!.first.maxByOrNull { it.waveHeight }!!
                                            Column(
                                                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp)
                                                    .clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface).padding(12.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text((if (evening) tr("Aujourd'hui") else tr("Hier")) + ", ${state.spotName} · ${formatDecimal(top.waveHeight, 1).replace('.', ',')} m", fontSize = 12.sp, color = onSurfaceColor.copy(alpha = 0.65f))
                                                Text("Tu as surfé ?", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = onSurfaceColor)
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Text(
                                                        "Noter ma session", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        modifier = Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primaryContainer)
                                                            .clickable { viewModel.dismissSurfedAsk(target); entryInitialOffset = if (evening) 0 else 1; navScope.launch { mainListState.scrollToItem(0); showSessionLogEntry = true } }.padding(horizontal = 14.dp, vertical = 7.dp)
                                                    )
                                                    Text(
                                                        "Non", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.clip(RoundedCornerShape(50)).clickable { viewModel.dismissSurfedAsk(target) }.padding(horizontal = 14.dp, vertical = 7.dp)
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
                                                text = "$matchDateFormatted ${bestPatternMatch.hourlyModel.rawTime.hour} h ressemble à ta session du $refDateFormatted " +
                                                    "à ${bestPatternMatch.referenceSession.microSpot.name} (${bestPatternMatch.referenceSession.quiverBoard.model}), " +
                                                    "ressemblance ${bestPatternMatch.score} %",
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
                        // Note du jour (matin + après-midi, nuit offshore comprise) : elle donne les étoiles ET le libellé, pour que
                        // « Très bon » ne s'affiche pas avec 2,5 étoiles parce qu'une seule heure est bonne.
                        val dailyScores = availableDates.map { date ->
                            val hours = daylightHoursFor(date, scoringByDate, state.dailySunInfo)
                            // Nuit offshore : soirée de la veille (21 h et après) + petit matin (avant 7 h).
                            val night = groupedByDate[date.minus(1, DateTimeUnit.DAY)].orEmpty().filter { it.rawTime.hour >= 21 } +
                                groupedByDate[date].orEmpty().filter { it.rawTime.hour < 7 }
                            dayQualityScore(
                                hours, idealSwellDirection, viewModel.surferLevel, state.dailyTides[date],
                                offshoreNight = hasOffshoreNight(night, idealSwellDirection)
                            )
                        }
                        // Les étoiles sont les mêmes pour tout le monde : le jour noté avec un profil neutre. Seuls les trois segments
                        // (matin, midi, après-midi) prennent la couleur du niveau de chacun.
                        val dailyStars = availableDates.map { date ->
                            val hours = daylightHoursFor(date, scoringByDate, state.dailySunInfo)
                            val night = groupedByDate[date.minus(1, DateTimeUnit.DAY)].orEmpty().filter { it.rawTime.hour >= 21 } +
                                groupedByDate[date].orEmpty().filter { it.rawTime.hour < 7 }
                            dayQualityScore(
                                hours, idealSwellDirection, NEUTRAL_LEVEL, state.dailyTides[date],
                                offshoreNight = hasOffshoreNight(night, idealSwellDirection)
                            )?.let { sc -> starsForScore(sc, 0) }
                        }

                        // Libellé du jour : celui de la note du jour. Les cas à part (trop gros, trop petit, challengeant, trop de
                        // vent) gardent leur nom quand c'est le cas de la meilleure heure.
                        val dailyLabels: List<SlotRating?> = availableDates.mapIndexed { i, date ->
                            val best = bestRatingOfDay(daylightHoursFor(date, scoringByDate, state.dailySunInfo), idealSwellDirection, viewModel.surferLevel)
                            val score = dailyScores[i]
                            if (best == null) null else if (best.kind != ConditionKind.NORMAL || score == null) best else SlotRating(score, false, why = best.why)
                        }

                        val dailyBands: List<List<ScoreBand>> = availableDates.map { date ->
                            dayBandsInTimeOrder(daylightHoursFor(date, scoringByDate, state.dailySunInfo), idealSwellDirection, viewModel.surferLevel, viewModel.weeklyDensity)
                        }

                        val dailyFeelsLike = availableDates.map { date ->
                            // Le ressenti moyen des heures de jour (comme l'encart Météo), pas la moyenne de l'air sur 24 h.
                            daylightHoursFor(date, groupedByDate, state.dailySunInfo).map { it.feelsLike }.takeIf { it.isNotEmpty() }?.average()?.roundToInt()
                                ?: state.dailySummaries[date]?.avgFeelsLike ?: 20
                        }

                        val dailyWaterTemps = availableDates.map { date ->
                            state.dailySummaries[date]?.avgWaterTemp
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .systemBarsPadding()
                        ) {
                            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            Column(modifier = Modifier.fillMaxSize()) {
                            PullToRefreshBox(
                                isRefreshing = viewModel.isRefreshing,
                                onRefresh = { viewModel.refreshActiveSpot() },
                                modifier = Modifier.weight(1f).fillMaxWidth().onGloballyPositioned { CardGear.listTopY = it.positionInRoot().y }
                            ) {
                            CardGear.listState = mainListState
                            LazyColumn(
                                state = mainListState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 6.dp),
                                // Quand le tiroir de réglages d'un encart est ouvert, la liste s'allonge d'autant : l'encart peut monter au-dessus.
                                contentPadding = PaddingValues(bottom = 16.dp + CardGear.sheetHeightDp.dp)
                            ) {
                                stickyHeader {
                                    // L'icône montre le thème vers lequel on bascule : un soleil en thème sombre, une lune en thème clair.
                                    val darkTop = isDarkSurfaceTheme()
                                    SpotHeader(
                                        spotName = state.spotName,
                                        favorites = viewModel.favoriteSpots.take(3),
                                        activeIndex = viewModel.activeFavoriteIndex,
                                        showHint = !viewModel.spotHintDone,
                                        onHintShown = { viewModel.markSpotHintDone() },
                                        onSelectIndex = { viewModel.selectFavoriteIndex(it) },
                                        onOpenSpots = { showSpotDialog = true },
                                        onToggleFavorite = {
                                            val slot = viewModel.favoriteSpots.indexOf(state.spotName)
                                            if (slot >= 0) viewModel.removeFavoriteSlot(slot)
                                            else {
                                                val free = viewModel.favoriteSpots.take(3).indexOfFirst { it == null }
                                                if (free >= 0) viewModel.assignSpotToFavoriteSlot(state.spotName, free) else showSpotDialog = true
                                            }
                                        },
                                        themeToggle = {
                                            Column(
                                                modifier = Modifier.clip(RoundedCornerShape(10.dp))
                                                    .clickable { viewModel.changeThemeMode(if (darkTop) "light" else "dark") }.padding(horizontal = 8.dp, vertical = 6.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                ThemeToggleIcon(isDarkActive = !darkTop, backgroundColor = MaterialTheme.colorScheme.background, iconColor = onSurfaceColor, modifier = Modifier.size(24.dp))
                                            }
                                        }
                                    )
                                }
                                item {
                                    Spacer(modifier = Modifier.height(4.dp))

                                    if (showSessionLogDialog) {
                                        val allSessions by viewModel.allSessions.collectAsState()

                                        SessionLogHistoryScreen(
                                            allSessions = allSessions,
                                            onOpenNewEntry = { showSessionLogEntry = true },
                                            onOpenQuiver = { showQuiverDialog = true },
                                            onDismiss = { showSessionLogDialog = false },
                                            quiverBoards = viewModel.quiverBoards.collectAsState().value,
                                            onUpdateSession = { viewModel.updateSession(it) },
                                            onDeleteSession = { viewModel.deleteSession(it) }
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
                                            onUpdateBoard = { board -> viewModel.updateQuiverBoard(board) },
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
                                            (1..14).associateWith { offset ->
                                                viewModel.pastConditions(state.spotName, today.minus(offset, DateTimeUnit.DAY))
                                            }.filterValues { it != null && it.first.isNotEmpty() }
                                        }

                                        SessionLogEntryDialog(
                                            spotName = state.spotName,
                                            hoursFor = { offset -> if (offset == 0) todayHours else pastConditions[offset]?.first.orEmpty() },
                                            tideFor = { offset -> if (offset == 0) todayTide else pastConditions[offset]?.second },
                                            sunFor = { offset ->
                                                state.dailySunInfo[today.minus(offset, DateTimeUnit.DAY)] ?: state.dailySunInfo[today]
                                            },
                                            ratingOf = { h -> calculateSlotRating(h, idealSwellDirection, viewModel.surferLevel) },
                                            beachFacing = idealSwellDirection,
                                            nowHour = clock.hour,
                                            quiverBoards = quiverBoards,
                                            onAddBoard = { name -> viewModel.addQuiverBoard(name, "autre", "", "", null, false) },
                                            microSpots = microSpots,
                                            onAddMicroSpot = { name -> viewModel.addMicroSpot(state.spotName, name) },
                                            onUpdateMicroSpot = { viewModel.updateMicroSpot(it) },
                                            showTips = viewModel.showJournalTips,
                                            onDismissTips = { viewModel.dismissJournalTips() },
                                            availableDayOffsets = listOf(0) + pastConditions.keys.sorted(),
                                            initialDayOffset = entryInitialOffset,
                                            onSave = { dayOffset, startMin, endMin, microSpotId, quiverId, rating, comment, mediaUri, forecastScore ->
                                                val past = pastConditions[dayOffset]
                                                val dayHours = if (dayOffset == 0) todayHours else past?.first.orEmpty()
                                                val dayTide = if (dayOffset == 0) todayTide else past?.second
                                                val midpointHour = (startMin + endMin) / 2 / 60
                                                val hourlyModel = dayHours.minByOrNull { abs(it.rawTime.hour - midpointHour) }
                                                if (hourlyModel != null) {
                                                    viewModel.logSurfSession(
                                                        date = today.minus(dayOffset, DateTimeUnit.DAY),
                                                        startMinutes = startMin,
                                                        endMinutes = endMin,
                                                        microSpotId = microSpotId,
                                                        quiverId = quiverId,
                                                        rating = rating,
                                                        comment = comment,
                                                        mediaUri = mediaUri,
                                                        hourlyModel = hourlyModel,
                                                        tideInfo = dayTide,
                                                        forecastScore = forecastScore
                                                    )
                                                }
                                            },
                                            onDismiss = { showSessionLogEntry = false }
                                        )
                                    }

                                    viewModel.offlineSinceMillis?.let { ms ->
                                        val dt = epochMillisToLocalDateTime(ms)
                                        Text(
                                            text = "Pas de réseau : prévisions du ${dt.dayOfMonth}/${dt.monthNumber} à ${dt.hour}h${dt.minute.toString().padStart(2, '0')}",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(2.dp))
                                }

                                selectedDate?.let { date ->
                                    val hoursForSelectedDay = groupedByDate[date] ?: emptyList()
                                    val dailyTide = state.dailyTides[date]

                                    item {
                                        CompositionLocalProvider(
                                            LocalCardSettings provides CardSettingsControl(
                                                windUnit = viewModel.windUnit, onWindUnit = { viewModel.changeWindUnit(it) },
                                                weeklyWindMode = viewModel.weeklyWindMode, onWeeklyWindMode = { viewModel.changeWeeklyWindMode(it) },
                                                weeklyDensity = viewModel.weeklyDensity, onWeeklyDensity = { viewModel.changeWeeklyDensity(it) }
                                            )
                                        ) {
                                        DynamicCardsSection(
                                            listState = mainListState,
                                            selectedHourState = pickedHourState,
                                            onOpenProfile = { preferencesStartPage = "profileQuiz"; showPreferencesDialog = true },
                                            onOpenModelSettings = { preferencesStartPage = "forecast"; showPreferencesDialog = true },
                                            afterTimeline = bestInfo,
                                            hoursForSelectedDay = hoursForSelectedDay,
                                            boardOfDay = if (GearPrefs.boardDayMode == "header" || GearPrefs.boardDayMode == "both")
                                                boardOfTheDay(
                                                    daylightHoursFor(date, groupedByDate, state.dailySunInfo).let { h -> h.minByOrNull { abs(it.rawTime.hour - 13) } },
                                                    viewModel.quiverBoards.collectAsState().value, idealSwellDirection
                                                )?.model else null,
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
                                            dailyLabels = dailyLabels,
 dailyBands = dailyBands,
                                            dailyWaterTemps = dailyWaterTemps,
                                            fixedMaxScale = fixedMaxScale,
                                            selectedIndex = selectedIndex,
                                            primaryColor = primaryColor,
                                            surfaceColor = surfaceColor,
                                            onSurfaceColor = onSurfaceColor,
                                            idealSwellDirection = idealSwellDirection,
                                            surferLevel = viewModel.surferLevel,
                                            coachTargets = coachTargets,
                                            onOpenWeatherDetail = { navScope.launch { mainListState.scrollToItem(0); showWeatherDetail = true } }
                                        )
                                        }
                                    }
                                }
                            }
                            }

                            // « Maintenant » + marée + eau : toujours visible, juste au-dessus de la barre du bas.
                            run {
                                val todayHours = groupedByDate[today].orEmpty()
                                val cardHour = nowCardHourOf(clock, state.dailySunInfo[today])
                                val closest = todayHours.minByOrNull { abs(it.rawTime.hour - cardHour) }
                                    ?: state.hourlyForecast.firstOrNull()
                                if (viewModel.showLiveOverlay && closest != null) {
                                    NowTideCard(
                                        hourlyModel = closest,
                                        tideInfo = currentTideInfo,
                                        windUnit = viewModel.windUnit,
                                        seaTemperature = (closest.seaTemperature
                                            ?: todayHours.mapNotNull { it.seaTemperature }.takeIf { it.isNotEmpty() }?.average())
                                            ?.let { kotlin.math.round(it).toInt() },
                                        spotName = state.spotName,
                                        updatedText = state.lastUpdatedTime,
                                        updateStale = isUpdateStale(state.lastUpdatedTime, nowLocalDateTime()),
                                        isNight = isNowNight(clock, state.dailySunInfo[today]),
                                        nightText = nightPhrase(
                                            nightHours(state.hourlyForecast, state.dailySunInfo, clock),
                                            idealSwellDirection
                                        ),
                                        buoy = viewModel.buoyReading,
                                        observed = viewModel.observedNow,
                                        timeHours = shownHours,
                                        timeSun = shownHours.firstOrNull()?.let { state.dailySunInfo[it.rawTime.date] },
                                        pickedHour = pickedHourState.value,
                                        onPickHour = { pickedHourState.value = it },
                                        dawnText = if (isDawnWindow(clock, state.dailySunInfo[today]) && !isNowNight(clock, state.dailySunInfo[today]))
                                            nightPhrase(nightHours(state.hourlyForecast, state.dailySunInfo, clock), idealSwellDirection) else null,
                                        onRefresh = { viewModel.refreshActiveSpot() },
                                        boardOfDay = if (GearPrefs.boardDayMode == "now" || GearPrefs.boardDayMode == "both")
                                            boardOfTheDay(closest, viewModel.quiverBoards.collectAsState().value, idealSwellDirection)?.model else null,
                                        rating = calculateSlotRating(
                                            hoursWithWindMemory(today, groupedByDate).firstOrNull { it.rawTime == closest.rawTime } ?: closest,
                                            idealSwellDirection, viewModel.surferLevel
                                        ),
                                        previousRating = hoursWithWindMemory(today, groupedByDate).let { mem ->
                                            val i = mem.indexOfFirst { it.rawTime == closest.rawTime }
                                            mem.getOrNull(i - 1)?.takeIf { i > 0 }?.let { calculateSlotRating(it, idealSwellDirection, viewModel.surferLevel) }
                                        },
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)
                                    )
                                }
                            }
                            }
                            // Le tiroir de réglages de l'encart dont le ⚙ est ouvert : devant la carte Actuellement, l'encart reste en haut, entier et en direct.
                            CompositionLocalProvider(
                                LocalCardSettings provides CardSettingsControl(
                                    windUnit = viewModel.windUnit, onWindUnit = { viewModel.changeWindUnit(it) },
                                    weeklyWindMode = viewModel.weeklyWindMode, onWeeklyWindMode = { viewModel.changeWeeklyWindMode(it) },
                                    weeklyDensity = viewModel.weeklyDensity, onWeeklyDensity = { viewModel.changeWeeklyDensity(it) }
                                )
                            ) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) { CardSettingsSheet() }
                            }
                            }

                            // Barre du bas : Prévisions, Journal, Météo, Réglages (toujours visible, à portée de pouce).
                            // Journal et Météo s'ouvrent depuis le haut de la liste : on y remonte d'abord.
                            BottomNavBar(
                                onForecast = { navScope.launch { mainListState.animateScrollToItem(0) } },
                                onJournal = { navScope.launch { mainListState.scrollToItem(0); showSessionLogDialog = true } },
                                onAddSession = { entryInitialOffset = 0; navScope.launch { mainListState.scrollToItem(0); showSessionLogEntry = true } },
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
        // Visite guidée de la première utilisation (une fois l'écran de bienvenue fermé).
        if (viewModel.showHomeTour && !viewModel.showOnboarding) {
            CoachMarkOverlay(
                steps = listOf(
                    CoachStep(
                        "weekCard", "Étoiles et couleurs",
                        "Les étoiles sont les mêmes pour tous. Les segments suivent ton profil. Touche le ⚙ d'un encart : réglages et explications."
                    ),
                    CoachStep(
                        "cardHeader", "Régler, plier, déplacer",
                        "Le ⚙ d'un encart ouvre ses réglages et ses explications. La flèche ▾ le plie ou le déplie. Garde le doigt appuyé sur son titre pour le déplacer."
                    ),
                    CoachStep("add", "Noter une session", "Appuie sur le + pour noter une session en dix secondes : l'appli garde les conditions. Retrouve-les ensuite dans l'onglet Journal.")
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
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else surfaceColor,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = spotName ?: "",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else onSurfaceColor,
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
    onOpenProfile: () -> Unit = {},
    onOpenModelSettings: () -> Unit = {},
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
    boardOfDay: String? = null,
    dailyLabels: List<SlotRating?> = emptyList(),
    dailyBands: List<List<ScoreBand>> = emptyList(),
    dailyWaterTemps: List<Int?>,
    fixedMaxScale: Float,
    selectedIndex: Int,
    primaryColor: Color,
    surfaceColor: Color,
    onSurfaceColor: Color,
    idealSwellDirection: Int?,
    surferLevel: String,
    currentHour: Int = nowLocalDateTime().hour,
    coachTargets: MutableMap<String, androidx.compose.ui.geometry.Rect>? = null,
    onOpenWeatherDetail: (() -> Unit)? = null,
    listState: androidx.compose.foundation.lazy.LazyListState? = null,
    selectedHourState: androidx.compose.runtime.MutableState<HourlyUiModel?>
) {
    val boardsForBar by viewModel.quiverBoards.collectAsState()
    var selectedHourlyItem by selectedHourState

    var draggedKey by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val itemHeights = remember { mutableStateMapOf<String, Int>() }

    fun renderableCardKeys(): List<String> = viewModel.cardsOrder.filter { key ->
        when (key) {
            "weekly" -> viewModel.showWeeklyCard
            "dailyTimeline" -> viewModel.showDailyTimelineCard
            "surf" -> viewModel.showSurfCard
            "wind" -> viewModel.showWindCard
            "weather" -> viewModel.showWeatherCard
            "hourly" -> viewModel.showHourlyCard
            else -> true
        }
    }

    // Appui long sur un encart (n'importe où dessus) : il se détache, suit le doigt et les autres se décalent pour lui
    // faire de la place. Près du haut ou du bas de l'écran, la page défile toute seule pour continuer à le monter ou le descendre.
    val dragScope = rememberCoroutineScope()
    val cardCoords = remember { mutableMapOf<String, androidx.compose.ui.layout.LayoutCoordinates>() }
    var autoScroll by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var fingerY by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val windowHeight = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.height.toFloat()

    // Plusieurs encarts peuvent être franchis d'un seul geste rapide.
    fun reorder(cardKey: String) {
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

    fun stopDrag() {
        autoScroll?.cancel()
        autoScroll = null
        draggedKey = null
        dragOffsetY = 0f
        fingerY = windowHeight / 2f
    }

    fun dragModifierFor(cardKey: String): Modifier = Modifier.pointerInput(cardKey) {
        detectLongPressDragFirst(
            onDragStart = { start ->
                draggedKey = cardKey
                dragOffsetY = 0f
                // Le doigt est là où l'appui long a eu lieu : sans ça, la position du doigt restait celle du geste d'avant (ou 0, le haut de
                // l'écran) et la page défilait vers le haut toute seule en emportant l'encart.
                cardCoords[cardKey]?.let { fingerY = it.positionInWindow().y + start.y }
                val ls = listState
                if (ls != null) {
                    autoScroll = dragScope.launch {
                        val topZone = with(density) { 150.dp.toPx() }
                        val bottomZone = windowHeight - with(density) { 190.dp.toPx() }
                        val maxStep = with(density) { 14.dp.toPx() }
                        while (true) {
                            val step = when {
                                fingerY < topZone -> -maxStep * ((topZone - fingerY) / topZone).coerceIn(0.15f, 1f)
                                fingerY > bottomZone -> maxStep * ((fingerY - bottomZone) / (windowHeight - bottomZone)).coerceIn(0.15f, 1f)
                                else -> 0f
                            }
                            if (step != 0f) {
                                val consumed = ls.dispatchRawDelta(step)
                                // La page a bougé sous le doigt : l'encart doit rester sous lui.
                                dragOffsetY += consumed
                                reorder(cardKey)
                            }
                            delay(16)
                        }
                    }
                }
            },
            onDragEnd = { stopDrag() },
            onDragCancel = { stopDrag() },
            onDrag = { change, dragAmount ->
                change.consume()
                dragOffsetY += dragAmount.y
                cardCoords[cardKey]?.let { fingerY = it.positionInWindow().y + change.position.y }
                reorder(cardKey)
            }
        )
    }

    val orderedKeys = renderableCardKeys()
    val afterKey = if ("dailyTimeline" in orderedKeys) "dailyTimeline" else orderedKeys.lastOrNull()

    Column(modifier = Modifier.fillMaxWidth()) {
        orderedKeys.forEach { cardKey ->
          androidx.compose.runtime.key(cardKey) {
            val isCollapsed = viewModel.isCardCollapsed(cardKey)
            val isDragging = draggedKey == cardKey
            val dragMod = dragModifierFor(cardKey)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { cardCoords[cardKey] = it }
                    .then(if (coachTargets != null && cardKey == orderedKeys.firstOrNull { it != "weekly" }) Modifier.coachTarget("cardHeader", coachTargets) else Modifier)
                    .then(dragMod)
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
                                dailyLabels = dailyLabels,
 dailyBands = dailyBands,
                                dailySunInfo = dailySunInfo,
                                fixedMaxScale = fixedMaxScale,
                                selectedIndex = selectedIndex,
                                surferLevel = surferLevel,
                                windUnit = viewModel.windUnit,
                                idealSwellDirection = idealSwellDirection,
                                weeklyDensity = viewModel.weeklyDensity,
                                weeklyWindMode = viewModel.weeklyWindMode,
                                primaryColor = primaryColor,
                                surfaceColor = surfaceColor,
                                onSurfaceColor = onSurfaceColor,
                                isCollapsed = isCollapsed,
                                onToggleCollapse = { viewModel.toggleCardCollapsed(cardKey) },
                                dragHandleModifier = Modifier,
                                modifier = if (coachTargets != null) Modifier.fillMaxWidth().coachTarget("weekCard", coachTargets) else Modifier.fillMaxWidth(),
                                boardOfDay = boardOfDay,
                                toleranceLabel = null
                            )
                        }
                        "dailyTimeline" -> {
                            Spacer(modifier = Modifier.height(4.dp))
                            DailyTimelineCard(
                                onOpenModelSettings = onOpenModelSettings,
                                selectedDate = selectedDate ?: availableDates.firstOrNull() ?: nowLocalDateTime().date,
                                groupedByDate = groupedByDate,
                                dailySunInfo = dailySunInfo,
                                dailyTides = dailyTides,
                                windUnit = viewModel.windUnit,
                                idealSwellDirection = idealSwellDirection,
                                surferLevel = surferLevel,
                                selectedHour = selectedHourlyItem,
                                onHourSelected = { selectedHourlyItem = it },
                                isCollapsed = isCollapsed,
                                onToggleCollapse = { viewModel.toggleCardCollapsed(cardKey) },
                                dragHandleModifier = Modifier,
                                modifier = Modifier.fillMaxWidth(),
                                boardOf = if (GearPrefs.boardDayMode == "none") null else { h -> boardOfTheDay(h, boardsForBar, idealSwellDirection)?.model },
                                motivationLabel = ToleranceSettings.label(viewModel.toleranceMode),
                                onMotivationSelect = { viewModel.setTolerance(it) }
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
                                    dragHandleModifier = Modifier,
                                    sun = dailySunInfo[hourly.rawTime.date],
                                    modifier = Modifier.fillMaxWidth(),
                                    beachFacing = idealSwellDirection
                                )
                            }
                        }
                        "wind" -> {
                            Spacer(modifier = Modifier.height(4.dp))
                            selectedHourlyItem?.let { hourly ->
                                WindCardComponent(
                                    onOpenModelSettings = onOpenModelSettings,
                                    selectedHour = hourly,
                                    allHoursOfDay = hoursForSelectedDay,
                                    windUnit = viewModel.windUnit,
                                    onHourSelected = { selectedHourlyItem = it },
                                    isCollapsed = isCollapsed,
                                    onToggleCollapse = { viewModel.toggleCardCollapsed(cardKey) },
                                    dragHandleModifier = Modifier,
                                    sun = dailySunInfo[hourly.rawTime.date],
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
                                    dragHandleModifier = Modifier,
                                    modifier = Modifier.fillMaxWidth(),
                                    sun = dailySunInfo[selectedHourlyItem?.rawTime?.date],
                                    onOpenDetail = onOpenWeatherDetail
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
                                        dragHandleModifier = Modifier,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                                    )

                                    var showAllHours by remember { mutableStateOf(false) }
                                    val selIdx = hoursForSelectedDay.indexOfFirst { it.rawTime == selectedHourlyItem?.rawTime }.coerceAtLeast(0)
                                    // Replié : l'heure choisie (par défaut l'heure actuelle) et la suivante. Déplié : 2 heures avant, l'heure, 2 après.
                                    val shown = when {
                                        isCollapsed -> hoursForSelectedDay.drop(selIdx).take(2)
                                        showAllHours -> hoursForSelectedDay
                                        else -> {
                                            val start = (selIdx - 2).coerceIn(0, maxOf(hoursForSelectedDay.size - 5, 0))
                                            hoursForSelectedDay.drop(start).take(5)
                                        }
                                    }
                                    shown.forEach { hourlyData ->
                                        HourlyForecastRow(
                                            hourlyData = hourlyData,
                                            windUnit = viewModel.windUnit,
                                            modifier = Modifier.clickable { selectedHourlyItem = hourlyData },
                                            dailyTideInfo = dailyTideInfo,
                                            isSelected = hourlyData.rawTime == selectedHourlyItem?.rawTime
                                        )
                                    }
                                    if (!isCollapsed && hoursForSelectedDay.size > 5) {
                                        Text(
                                            if (showAllHours) "Réduire" else "Voir toute la journée", fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { showAllHours = !showAllHours }.padding(8.dp),
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                    // Profil pas encore renseigné : un message entre la semaine et le déroulé de la journée invite à le remplir.
                    if (cardKey == "weekly" && viewModel.showProfileNudge) {
                        ProfileNudgeCard(
                            onOpen = onOpenProfile,
                            onLater = { viewModel.hideProfileNudge() },
                            modifier = (if (coachTargets != null) Modifier.coachTarget("bestSlot", coachTargets) else Modifier).padding(horizontal = 6.dp, vertical = 6.dp)
                        )
                    }
                    if (cardKey == afterKey) afterTimeline()
                }
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
    onAddSession: () -> Unit,
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
                Item(tr("Prévisions"), true, onForecast) { WaveIcon(color = waterTempColor(), modifier = Modifier.size(20.dp)) }
                Item(tr("Webcam"), false, onWebcam) {
                    WebcamIcon(tint = if (hasWebcam) AppColors.WindMid else colors.onSurface.copy(alpha = 0.35f), size = 20.dp)
                }
                // Le « + » au centre exact de la barre (deux onglets de chaque côté) : une nouvelle session.
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier.coachTarget("add", coachTargets).size(42.dp).clip(CircleShape).background(colors.primary).clickable(onClick = onAddSession),
                        contentAlignment = Alignment.Center
                    ) { Text("+", fontSize = 26.sp, color = colors.onPrimary, fontWeight = FontWeight.Light) }
                }
                Item(tr("Journal"), false, onJournal, Modifier.coachTarget("journal", coachTargets)) {
                    JournalIcon(color = colors.onSurface, modifier = Modifier.size(20.dp))
                }
                Item(tr("Réglages"), false, onSettings, Modifier.coachTarget("settings", coachTargets)) {
                    Icon(imageVector = SurfIcons.Settings, contentDescription = null, tint = colors.onSurface, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

/**
 * Appui long puis glisser, comme [detectDragGesturesAfterLongPress], mais le parent voit le doigt AVANT ses enfants (passe « Initial ») :
 * l'appui long marche aussi sur un titre, une flèche ou tout ce qui est cliquable dans l'encart, et une fois l'encart pris, les
 * enfants ne reçoivent plus le geste (pas de pli ni de dépli en lâchant).
 */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.detectLongPressDragFirst(
    onDragStart: (androidx.compose.ui.geometry.Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onDrag: (androidx.compose.ui.input.pointer.PointerInputChange, androidx.compose.ui.geometry.Offset) -> Unit
) {
    val initial = androidx.compose.ui.input.pointer.PointerEventPass.Initial
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = initial)
        val early = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val ev = awaitPointerEvent(initial)
                val c = ev.changes.firstOrNull { it.id == down.id }
                if (c == null || !c.pressed || (c.position - down.position).getDistance() > viewConfiguration.touchSlop) return@withTimeoutOrNull Unit
            }
        }
        // Le doigt a bougé ou s'est levé avant la fin de l'appui long : ce n'est pas un déplacement.
        if (early != null) return@awaitEachGesture
        onDragStart(down.position)
        var finished = false
        try {
            while (true) {
                val ev = awaitPointerEvent(initial)
                val c = ev.changes.firstOrNull { it.id == down.id }
                if (c == null || !c.pressed) { c?.consume(); finished = true; onDragEnd(); return@awaitEachGesture }
                val delta = c.positionChange()
                c.consume()
                onDrag(c, delta)
            }
        } finally {
            if (!finished) onDragCancel()
        }
    }
}
