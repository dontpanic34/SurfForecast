@file:Suppress("SpellCheckingInspection")
package com.surfcast.surfforecast

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.surfcast.surfforecast.ui.theme.AppColors
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private fun daylightHoursFor(
    date: LocalDate,
    groupedByDate: Map<LocalDate, List<HourlyUiModel>>,
    dailySunInfo: Map<LocalDate, DailySunInfo>
): List<HourlyUiModel> {
    val dayHours = groupedByDate[date] ?: return emptyList()
    val sun = dailySunInfo[date]
    return if (sun != null) {
        dayHours.filter {
            val t = it.rawTime.toLocalTime()
            !t.isBefore(sun.sunrise) && !t.isAfter(sun.sunset)
        }
    } else {
        dayHours.filter { it.rawTime.hour in 7..21 }
    }
}

@Composable
fun MainScreen(viewModel: SurfViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    var showSpotDialog by remember { mutableStateOf(false) }
    var showPreferencesDialog by remember { mutableStateOf(false) }
    var showSessionLogDialog by remember { mutableStateOf(false) }
    var showSessionLogEntry by remember { mutableStateOf(false) }
    var showQuiverDialog by remember { mutableStateOf(false) }
    var showLiveCam by rememberSaveable { mutableStateOf(false) }
    var showWebcamDirectoryDialog by remember { mutableStateOf(false) }
    var directWebcamSpot by remember { mutableStateOf<String?>(null) }

    val backgroundColor = MaterialTheme.colorScheme.background
    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val primaryColor = MaterialTheme.colorScheme.primary

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
            onOpenLiveCam = { spotWithCam ->
                directWebcamSpot = spotWithCam
                showLiveCam = true
            }
        )
    }

    if (showPreferencesDialog) {
        SurfPreferencesDialog(
            windUnit = viewModel.windUnit,
            onWindUnitSelected = { newUnit -> viewModel.changeWindUnit(newUnit) },
            showLiveOverlay = viewModel.showLiveOverlay,
            onToggleLiveOverlay = { viewModel.toggleLiveOverlay(it) },
            showWeeklyCard = viewModel.showWeeklyCard,
            onToggleWeeklyCard = { viewModel.toggleWeeklyCard(it) },
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
            onSurferLevelChanged = { viewModel.changeSurferLevel(it) },
            engineConfig = viewModel.engineConfig,
            onEngineConfigChanged = { viewModel.updateEngineConfig(it) },
            onViewLogs = { },
            onDismiss = { showPreferencesDialog = false }
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
                                                directWebcamSpot = spotEntry.spotDisplayName
                                                showWebcamDirectoryDialog = false
                                                showLiveCam = true
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
                val groupedByDate = state.hourlyForecast.groupBy { it.rawTime.toLocalDate() }
                val availableDates = groupedByDate.keys.toList()

                if (selectedDate == null && availableDates.isNotEmpty()) {
                    selectedDate = availableDates.first()
                }

                val currentTideInfo = state.dailyTides[LocalDate.now()] ?: state.dailyTides.values.firstOrNull()

                // Point 3 : angle de houle ideal du spot actif (peut etre null si pas encore renseigne)
                // et meilleur creneau du jour selectionne, pour le bandeau "Statut Flash".
                val idealSwellDirection = SurfDatabase.findSpotByName(state.spotName)?.idealSwellDirection
                val bestSlot = selectedDate?.let { date ->
                    findBestSlot(
                        dailyHours = daylightHoursFor(date, groupedByDate, state.dailySunInfo),
                        idealSwellDirection = idealSwellDirection,
                        surferLevel = viewModel.surferLevel,
                        dailyTide = state.dailyTides[date]
                    )
                }

                // Journal de session : meilleur "Pattern repere" dans les previsions a 7 jours
                // par rapport aux sessions passees notees >= 4/5.
                val referenceSessions by viewModel.referenceSessions.collectAsState()
                val bestPatternMatch = remember(state.hourlyForecast, state.dailyTides, idealSwellDirection, referenceSessions) {
                    viewModel.computePatternMatches(state.hourlyForecast, state.dailyTides, idealSwellDirection)
                        .maxByOrNull { it.score }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    if (showLiveCam) {
                        LiveCamOverlayScreen(
                            currentSpotName = directWebcamSpot ?: state.spotName,
                            onClose = {
                                showLiveCam = false
                                directWebcamSpot = null
                            },
                            onSwitchSpot = { newSpot -> directWebcamSpot = newSpot }
                        )
                    } else {
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

                        val dailyFeelsLike = availableDates.map { date ->
                            state.dailySummaries[date]?.avgFeelsLike ?: 20
                        }

                        val dailyWaterTemps = availableDates.map { date ->
                            state.dailySummaries[date]?.avgWaterTemp ?: 18
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .systemBarsPadding()
                        ) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = surfaceColor
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = state.spotName,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = onSurfaceColor,
                                            maxLines = 1,
                                            modifier = Modifier.padding(end = 6.dp)
                                        )
                                        val spotHasCam = SurfWebcamHelper.hasCamera(state.spotName)
                                        IconButton(
                                            onClick = {
                                                if (spotHasCam) {
                                                    directWebcamSpot = state.spotName
                                                    showLiveCam = true
                                                }
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            WebcamIcon(
                                                tint = if (spotHasCam) AppColors.WindMid else onSurfaceColor.copy(alpha = 0.3f),
                                                size = 15.dp
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                        NearbySpotsRow(
                                            currentSpotName = state.spotName,
                                            onSelectSpot = { spotName -> viewModel.previewSpot(spotName) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    // Point 3 : "Statut Flash" - meilleur creneau du jour selectionne.
                                    if (bestSlot != null) {
                                        val flashColor = when (scoreToColorCategory(bestSlot.averageScore)) {
                                            "red" -> AppColors.WindHigh
                                            "orange" -> AppColors.WindMid
                                            else -> AppColors.TideLow
                                        }
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 2.dp)
                                                .padding(bottom = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Canvas(modifier = Modifier.size(6.dp)) {
                                                drawCircle(color = flashColor, radius = size.minDimension / 2f)
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Column {
                                                Text(
                                                    text = "Meilleur créneau : ${bestSlot.startHour}h-${bestSlot.endHour}h (score ${bestSlot.averageScore})",
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = onSurfaceColor.copy(alpha = 0.75f),
                                                    maxLines = 1
                                                )
                                                Text(
                                                    text = bestSlot.recap,
                                                    fontSize = 9.5.sp,
                                                    color = onSurfaceColor.copy(alpha = 0.55f),
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }

                                    // Journal de session : bandeau "Pattern repere" si un creneau a venir
                                    // matche une session passee bien notee.
                                    if (bestPatternMatch != null) {
                                        val refDate = java.time.Instant.ofEpochMilli(bestPatternMatch.referenceSession.session.startTime)
                                            .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                                        val refDateFormatted = refDate.format(DateTimeFormatter.ofPattern("d MMMM", Locale.FRANCE))
                                        val matchDateFormatted = bestPatternMatch.hourlyModel.rawTime.toLocalDate()
                                            .format(DateTimeFormatter.ofPattern("EEE d", Locale.FRANCE))
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
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = onSurfaceColor.copy(alpha = 0.75f),
                                                maxLines = 2
                                            )
                                        }
                                    }
                                }
                            }

                            run {
                                val activeDayHours = groupedByDate[selectedDate ?: LocalDate.now()] ?: state.hourlyForecast
                                val currentHourNow = LocalTime.now().hour
                                val closestHourModel = activeDayHours.minByOrNull { abs(it.rawTime.hour - currentHourNow) } ?: activeDayHours.firstOrNull()

                                if (viewModel.showLiveOverlay && closestHourModel != null) {
                                    SurfLiveStripOverlay(
                                        hourlyModel = closestHourModel,
                                        tideInfo = currentTideInfo,
                                        windUnit = viewModel.windUnit,
                                        onOpenCam = {
                                            directWebcamSpot = state.spotName
                                            showLiveCam = true
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 6.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            LazyColumn(
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

                                        val systemDark = isSystemInDarkTheme()
                                        val isDarkActive = when (viewModel.themeMode) {
                                            "light" -> false
                                            "dark" -> true
                                            else -> systemDark
                                        }

                                        IconButton(
                                            onClick = {
                                                viewModel.changeThemeMode(if (isDarkActive) "light" else "dark")
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            ThemeToggleIcon(
                                                isDarkActive = isDarkActive,
                                                backgroundColor = backgroundColor,
                                                iconColor = onSurfaceColor,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        IconButton(onClick = { showSessionLogDialog = true }, modifier = Modifier.size(32.dp)) {
                                            JournalIcon(
                                                color = onSurfaceColor,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        IconButton(onClick = { showPreferencesDialog = true }, modifier = Modifier.size(32.dp)) {
                                            Icon(
                                                imageVector = Icons.Default.Settings,
                                                contentDescription = "Paramètres",
                                                tint = onSurfaceColor,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
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

                                    if (showQuiverDialog) {
                                        val quiverBoards by viewModel.quiverBoards.collectAsState()

                                        QuiverScreen(
                                            quiverBoards = quiverBoards,
                                            onAddBoard = { model, family, length, fins -> viewModel.addQuiverBoard(model, family, length, fins) },
                                            onDeleteBoard = { board -> viewModel.deleteQuiverBoard(board) },
                                            onDismiss = { showQuiverDialog = false }
                                        )
                                    }

                                    if (showSessionLogEntry) {
                                        val todayHours = groupedByDate[LocalDate.now()] ?: state.hourlyForecast
                                        val todayTide = state.dailyTides[LocalDate.now()]
                                        val quiverBoards by viewModel.quiverBoards.collectAsState()
                                        val microSpots by remember(state.spotName) { viewModel.microSpotsFor(state.spotName) }
                                            .collectAsState(initial = emptyList())

                                        SessionLogEntryDialog(
                                            spotName = state.spotName,
                                            todayHours = todayHours,
                                            tideInfo = todayTide,
                                            quiverBoards = quiverBoards,
                                            microSpots = microSpots,
                                            onAddMicroSpot = { name -> viewModel.addMicroSpot(state.spotName, name) },
                                            onSave = { startHour, endHour, microSpotId, quiverId, rating, comment, mediaUri ->
                                                val midpointHour = (startHour + endHour) / 2
                                                val hourlyModel = todayHours.minByOrNull { abs(it.rawTime.hour - midpointHour) }
                                                if (hourlyModel != null) {
                                                    viewModel.logSurfSession(
                                                        date = LocalDate.now(),
                                                        startHour = startHour,
                                                        endHour = endHour,
                                                        microSpotId = microSpotId,
                                                        quiverId = quiverId,
                                                        rating = rating,
                                                        comment = comment,
                                                        mediaUri = mediaUri,
                                                        hourlyModel = hourlyModel,
                                                        tideInfo = todayTide
                                                    )
                                                }
                                            },
                                            onDismiss = { showSessionLogEntry = false }
                                        )
                                    }

                                    if (state.lastUpdatedTime.isNotEmpty()) {
                                        Text(
                                            text = "Mis à jour à ${state.lastUpdatedTime}",
                                            fontSize = 9.sp,
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
                                            hoursForSelectedDay = hoursForSelectedDay,
                                            dailyTideInfo = dailyTide,
                                            isToday = (date == LocalDate.now()),
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
                                            dailyWaterTemps = dailyWaterTemps,
                                            fixedMaxScale = fixedMaxScale,
                                            selectedIndex = selectedIndex,
                                            primaryColor = primaryColor,
                                            surfaceColor = surfaceColor,
                                            onSurfaceColor = onSurfaceColor,
                                            idealSwellDirection = idealSwellDirection,
                                            surferLevel = viewModel.surferLevel
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
            }
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
                val angle = (i * 45f) * (Math.PI.toFloat() / 180f)
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
                    fontSize = 9.sp,
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
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = onSurfaceColor,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
fun DynamicCardsSection(
    hoursForSelectedDay: List<HourlyUiModel>,
    dailyTideInfo: DailyTideInfo?,
    isToday: Boolean,
    viewModel: SurfViewModel,
    availableDates: List<LocalDate>,
    groupedByDate: Map<LocalDate, List<HourlyUiModel>>,
    selectedDate: LocalDate?,
    onSelectDate: (LocalDate) -> Unit,
    dailyTides: Map<LocalDate, DailyTideInfo>,
    dailySunInfo: Map<LocalDate, DailySunInfo>,
    dailyPeriods: List<Int>,
    dailyHeights: List<Double>,
    dailyFeelsLike: List<Int>,
    dailyWaterTemps: List<Int>,
    fixedMaxScale: Float,
    selectedIndex: Int,
    primaryColor: Color,
    surfaceColor: Color,
    onSurfaceColor: Color,
    idealSwellDirection: Int?,
    surferLevel: String
) {
    val initialSelectedHour = remember(hoursForSelectedDay, isToday) {
        if (isToday) {
            val currentHour = LocalTime.now().hour
            hoursForSelectedDay.minByOrNull { abs(it.rawTime.hour - currentHour) } ?: hoursForSelectedDay.firstOrNull()
        } else {
            hoursForSelectedDay.firstOrNull()
        }
    }

    var selectedHourlyItem by remember(hoursForSelectedDay, isToday) { mutableStateOf(initialSelectedHour) }

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

    fun dragModifierFor(cardKey: String): Modifier = Modifier.pointerInput(cardKey) {
        detectDragGesturesAfterLongPress(
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

                val orderedKeys = renderableCardKeys()
                val currentIndex = orderedKeys.indexOf(cardKey)

                if (currentIndex != -1) {
                    if (dragOffsetY > 0f && currentIndex < orderedKeys.size - 1) {
                        val nextKey = orderedKeys[currentIndex + 1]
                        val nextHeight = (itemHeights[nextKey] ?: itemHeights[cardKey] ?: 0).toFloat()
                        if (nextHeight > 0f && dragOffsetY > nextHeight / 2f) {
                            viewModel.moveCardDown(cardKey)
                            dragOffsetY -= nextHeight
                        }
                    } else if (dragOffsetY < 0f && currentIndex > 0) {
                        val prevKey = orderedKeys[currentIndex - 1]
                        val prevHeight = (itemHeights[prevKey] ?: itemHeights[cardKey] ?: 0).toFloat()
                        if (prevHeight > 0f && -dragOffsetY > prevHeight / 2f) {
                            viewModel.moveCardUp(cardKey)
                            dragOffsetY += prevHeight
                        }
                    }
                }
            }
        )
    }

    val orderedKeys = renderableCardKeys()

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
                                dailySunInfo = dailySunInfo,
                                fixedMaxScale = fixedMaxScale,
                                selectedIndex = selectedIndex,
                                windUnit = viewModel.windUnit,
                                weeklyDensity = viewModel.weeklyDensity,
                                weeklyWindMode = viewModel.weeklyWindMode,
                                primaryColor = primaryColor,
                                surfaceColor = surfaceColor,
                                onSurfaceColor = onSurfaceColor,
                                isCollapsed = isCollapsed,
                                onToggleCollapse = { viewModel.toggleCardCollapsed(cardKey) },
                                dragHandleModifier = dragMod,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        "dailyTimeline" -> {
                            Spacer(modifier = Modifier.height(4.dp))
                            DailyTimelineCard(
                                selectedDate = selectedDate ?: availableDates.firstOrNull() ?: LocalDate.now(),
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
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
    }
}

@Composable
fun WeeklyForecastCard(
    availableDates: List<LocalDate>,
    groupedByDate: Map<LocalDate, List<HourlyUiModel>>,
    selectedDate: LocalDate?,
    onSelectDate: (LocalDate) -> Unit,
    dailyTides: Map<LocalDate, DailyTideInfo>,
    dailyPeriods: List<Int>,
    dailyHeights: List<Double>,
    dailyFeelsLike: List<Int>,
    dailyWaterTemps: List<Int>,
    dailySunInfo: Map<LocalDate, DailySunInfo>,
    fixedMaxScale: Float,
    selectedIndex: Int,
    windUnit: String,
    weeklyDensity: Int,
    weeklyWindMode: String,
    primaryColor: Color,
    surfaceColor: Color,
    onSurfaceColor: Color,
    isCollapsed: Boolean,
    onToggleCollapse: () -> Unit,
    dragHandleModifier: Modifier = Modifier,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = surfaceColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)) {
            CardControlsRow(
                title = "Prévisions de la semaine",
                isCollapsed = isCollapsed,
                onToggleCollapse = onToggleCollapse,
                dragHandleModifier = dragHandleModifier,
                modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp)
            )

            if (!isCollapsed) {
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val today = LocalDate.now()
                availableDates.forEachIndexed { index, date ->
                    val dayNum = date.dayOfMonth.toString()
                    val dayLabel = when {
                        date == today || index == 0 -> "Auj. $dayNum"
                        date == today.plusDays(1) || index == 1 -> "Dem. $dayNum"
                        else -> {
                            val dayName = date.format(DateTimeFormatter.ofPattern("EEE", Locale.FRANCE))
                                .replace(".", "")
                                .replaceFirstChar { it.uppercase() }
                            "$dayName. $dayNum"
                        }
                    }
                    val isSelected = date == selectedDate

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(if (isSelected) primaryColor.copy(alpha = 0.2f) else Color.Transparent)
                            .clickable { onSelectDate(date) }
                            .padding(vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = dayLabel,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) primaryColor else onSurfaceColor,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                availableDates.forEach { date ->
                    val dailyData = groupedByDate[date] ?: emptyList()
                    val isSelected = date == selectedDate

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isSelected) primaryColor.copy(alpha = 0.2f) else Color.Transparent)
                            .clickable { onSelectDate(date) }
                            .padding(horizontal = 1.dp, vertical = 1.dp)
                    ) {
                        WeatherCanvasMain(dayData = dailyData, density = weeklyDensity, modifier = Modifier.fillMaxSize())
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            if (weeklyWindMode != "none") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                availableDates.forEach { date ->
                    val dailyData = groupedByDate[date] ?: emptyList()
                    val isSelected = date == selectedDate

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(if (isSelected) primaryColor.copy(alpha = 0.2f) else Color.Transparent)
                            .clickable { onSelectDate(date) }
                            .padding(vertical = 1.dp)
                    ) {
                        DayWindThreeSlots(
                            dailyData = dailyData,
                            windUnit = windUnit,
                            density = weeklyDensity,
                            windMode = weeklyWindMode
                        )
                    }
                }
            }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
            ) {
                ContinuousWaveCanvas(
                    allHourlyData = availableDates.flatMap { daylightHoursFor(it, groupedByDate, dailySunInfo) },
                    dailyPeriods = dailyPeriods,
                    dailyHeights = dailyHeights,
                    dailyFeelsLike = dailyFeelsLike,
                    dailyWaterTemps = dailyWaterTemps,
                    maxScale = fixedMaxScale,
                    daysCount = availableDates.size,
                    selectedIndex = selectedIndex,
                    modifier = Modifier.fillMaxSize()
                )

                Row(modifier = Modifier.fillMaxSize()) {
                    availableDates.forEach { date ->
                        val isSelected = date == selectedDate
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(if (isSelected) primaryColor.copy(alpha = 0.12f) else Color.Transparent)
                                .clickable { onSelectDate(date) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(3.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                availableDates.forEach { date ->
                    val isSelected = date == selectedDate
                    val tideInfo = dailyTides[date]

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                            .background(if (isSelected) primaryColor.copy(alpha = 0.2f) else Color.Transparent)
                            .clickable { onSelectDate(date) }
                            .padding(vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        DailyTideCanvas(
                            tideInfo = tideInfo,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(26.dp)
                        )
                    }
                }
            }
            }
        }
    }
}

@Composable
fun WeatherCanvasMain(dayData: List<HourlyUiModel>, density: Int = 3, modifier: Modifier = Modifier) {
    if (dayData.isEmpty()) return

    val sampleHours = hoursForDensity(density)
    val slots = sampleHours.mapNotNull { targetHour ->
        dayData.minByOrNull { abs(it.rawTime.hour - targetHour) }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        slots.forEach { slot ->
            val emoji = SurfUnitsHelper.resolveRealWeatherEmoji(slot)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(text = emoji, fontSize = 13.sp)
                Text(
                    text = "${slot.temperature}°",
                    fontSize = 6.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    lineHeight = 8.sp
                )
            }
        }
    }
}

@Composable
fun ContinuousWaveCanvas(
    allHourlyData: List<HourlyUiModel>,
    dailyPeriods: List<Int>,
    dailyHeights: List<Double>,
    dailyFeelsLike: List<Int>,
    dailyWaterTemps: List<Int>,
    maxScale: Float,
    daysCount: Int,
    selectedIndex: Int,
    modifier: Modifier = Modifier
) {
    if (allHourlyData.isEmpty()) return

    val density = LocalDensity.current
    val tideColorInt = AppColors.TideHighDark.toArgb()

    val heightTextPaint = remember(density) {
        Paint().apply {
            color = tideColorInt
            textSize = with(density) { 8.5.sp.toPx() }
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
    }

    val periodTextPaint = remember(density) {
        Paint().apply {
            color = 0xFF90A4AE.toInt()
            textSize = with(density) { 8.sp.toPx() }
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
    }

    val feelsTextPaint = remember(density) {
        Paint().apply {
            color = AppColors.WindAccent.toArgb()
            textSize = with(density) { 7.5.sp.toPx() }
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
    }

    val waterTextPaint = remember(density) {
        Paint().apply {
            color = tideColorInt
            textSize = with(density) { 7.5.sp.toPx() }
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val padY = 8.dp.toPx()
        val usableH = h - (2 * padY)
        val baseY = padY + usableH

        var meter = 0f
        while (meter <= maxScale + 0.01f) {
            val y = padY + usableH * (1f - (meter / maxScale))
            val rem = meter % 1f
            val isInteger = rem !in 0.05f..0.95f
            drawLine(
                color = Color.Gray.copy(alpha = if (isInteger) 0.3f else 0.15f),
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = if (isInteger) 0.9f else 0.5f
            )
            meter += 0.5f
        }

        val dayWidth = w / daysCount.toFloat()
        if (daysCount > 1) {
            for (i in 1 until daysCount) {
                val x = i * dayWidth
                drawLine(
                    color = Color.Gray.copy(alpha = 0.3f),
                    start = Offset(x, 0f),
                    end = Offset(x, h),
                    strokeWidth = 0.8.dp.toPx()
                )
            }
        }

        val stepX = w / (allHourlyData.size - 1).coerceAtLeast(1).toFloat()
        val points = allHourlyData.mapIndexed { index, item ->
            val ratio = (item.waveHeight.toFloat() / maxScale).coerceIn(0f, 1f)
            val y = padY + usableH * (1f - ratio)
            Offset(index * stepX, y)
        }

        val fillPath = Path().apply {
            moveTo(0f, baseY)
            lineTo(points.first().x, points.first().y)
            for (i in 1 until points.size) {
                val prev = points[i - 1]
                val curr = points[i]
                val midX = (prev.x + curr.x) / 2f
                cubicTo(midX, prev.y, midX, curr.y, curr.x, curr.y)
            }
            lineTo(w, baseY)
            close()
        }

        drawPath(path = fillPath, color = AppColors.TideHigh.copy(alpha = 0.3f))

        val strokePath = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (i in 1 until points.size) {
                val prev = points[i - 1]
                val curr = points[i]
                val midX = (prev.x + curr.x) / 2f
                cubicTo(midX, prev.y, midX, curr.y, curr.x, curr.y)
            }
        }

        drawPath(path = strokePath, color = AppColors.TideHighDark, style = Stroke(width = 2.dp.toPx()))

        for (i in 0 until daysCount) {
            val colLeft = i * dayWidth + 3.dp.toPx()
            val feels = dailyFeelsLike.getOrNull(i) ?: 20
            val water = dailyWaterTemps.getOrNull(i) ?: 18

            val thX = colLeft + 2.dp.toPx()
            val thTop = padY + 1.2.dp.toPx()
            val thBottom = padY + 7.dp.toPx()
            val tubeW = 2.2.dp.toPx()

            drawRoundRect(
                color = AppColors.WindAccent.copy(alpha = 0.35f),
                topLeft = Offset(thX - tubeW / 2f, thTop),
                size = Size(tubeW, thBottom - thTop),
                cornerRadius = CornerRadius(tubeW / 2f, tubeW / 2f)
            )
            drawRoundRect(
                color = AppColors.WindAccent,
                topLeft = Offset(thX - tubeW / 2f, thTop),
                size = Size(tubeW, thBottom - thTop),
                cornerRadius = CornerRadius(tubeW / 2f, tubeW / 2f),
                style = Stroke(width = 0.7.dp.toPx())
            )
            drawCircle(
                color = AppColors.WindAccent,
                radius = 1.9.dp.toPx(),
                center = Offset(thX, thBottom + 1.dp.toPx())
            )

            drawContext.canvas.nativeCanvas.drawText(
                "$feels°",
                colLeft + 5.5.dp.toPx(),
                padY + 7.5.dp.toPx(),
                feelsTextPaint
            )

            val dropCenterX = colLeft + 2.dp.toPx()
            val dropTop = padY + 12.dp.toPx()
            val dropBottom = padY + 18.2.dp.toPx()
            val dropW = 2.3.dp.toPx()

            val dropPath = Path().apply {
                moveTo(dropCenterX, dropTop)
                cubicTo(
                    dropCenterX + dropW * 0.3f, dropTop + 1.8.dp.toPx(),
                    dropCenterX + dropW, dropBottom - 2.8.dp.toPx(),
                    dropCenterX + dropW, dropBottom - 1.4.dp.toPx()
                )
                quadraticBezierTo(
                    dropCenterX + dropW, dropBottom,
                    dropCenterX, dropBottom
                )
                quadraticBezierTo(
                    dropCenterX - dropW, dropBottom,
                    dropCenterX - dropW, dropBottom - 1.4.dp.toPx()
                )
                cubicTo(
                    dropCenterX - dropW, dropBottom - 2.8.dp.toPx(),
                    dropCenterX - dropW * 0.3f, dropTop + 1.8.dp.toPx(),
                    dropCenterX, dropTop
                )
                close()
            }

            drawPath(path = dropPath, color = AppColors.TideHighDark.copy(alpha = 0.25f))
            drawPath(path = dropPath, color = AppColors.TideHighDark, style = Stroke(width = 0.85.dp.toPx()))

            drawContext.canvas.nativeCanvas.drawText(
                "$water°",
                colLeft + 5.5.dp.toPx(),
                padY + 17.5.dp.toPx(),
                waterTextPaint
            )
        }

        for (i in 0 until daysCount) {
            val targetX = (i + 0.5f) * dayWidth
            val closestPoint = points.minByOrNull { abs(it.x - targetX) } ?: continue
            val waveHeight = dailyHeights.getOrNull(i) ?: 0.0

            val hText = String.format(Locale.US, "%.1fm", waveHeight)
            val hTextW = heightTextPaint.measureText(hText)
            val textY = (closestPoint.y - 4.dp.toPx()).coerceAtLeast(padY + 7.dp.toPx())

            if (i == selectedIndex) {
                drawCircle(color = Color.White, radius = 3.5.dp.toPx(), center = closestPoint)
                drawCircle(color = AppColors.TideHighDark, radius = 2.2.dp.toPx(), center = closestPoint)
            }

            drawContext.canvas.nativeCanvas.drawText(hText, targetX - hTextW / 2f, textY, heightTextPaint)
        }

        for (i in 0 until daysCount) {
            val targetX = (i + 0.5f) * dayWidth
            val period = dailyPeriods.getOrNull(i) ?: continue

            val pText = "${period}s"
            val pTextW = periodTextPaint.measureText(pText)
            val pY = baseY - 2.dp.toPx()

            drawContext.canvas.nativeCanvas.drawText(pText, targetX - pTextW / 2f, pY, periodTextPaint)
        }
    }
}

@Composable
fun DailyTideCanvas(
    tideInfo: DailyTideInfo?,
    modifier: Modifier = Modifier
) {
    if (tideInfo == null) return

    val density = LocalDensity.current
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val highTextPaint = remember(density) {
        Paint().apply {
            color = AppColors.TideHighDark.toArgb()
            textSize = with(density) { 7.2.sp.toPx() }
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
    }

    val lowTextPaint = remember(density) {
        Paint().apply {
            color = AppColors.TideLowDark.toArgb()
            textSize = with(density) { 7.2.sp.toPx() }
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
    }

    // Correctif contraste : l'ancien gris-bleu clair (0xFFB0BEC5) etait pense pour fond
    // sombre et devenait quasi invisible en theme clair. On suit desormais onSurface
    // (fonce en clair, clair en sombre), comme les autres textes du bloc marees.
    val coefTextPaint = remember(density, onSurfaceColor) {
        Paint().apply {
            color = onSurfaceColor.copy(alpha = 0.62f).toArgb()
            textSize = with(density) { 7.5.sp.toPx() }
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val highTimeStr = tideInfo.highTideTime ?: ""
        val lowTimeStr = tideInfo.lowTideTime ?: ""
        val coef = tideInfo.coefficient

        val iconW = 5.2.dp.toPx()
        val iconH = 4.8.dp.toPx()
        val iconGap = 1.2.dp.toPx()

        val highTextW = if (highTimeStr.isNotEmpty()) highTextPaint.measureText(highTimeStr) else 0f
        val lowTextW = if (lowTimeStr.isNotEmpty()) lowTextPaint.measureText(lowTimeStr) else 0f
        val maxTextW = maxOf(highTextW, lowTextW)

        val coefStr = if (coef != null) "$coef" else ""
        val coefTextW = if (coef != null) coefTextPaint.measureText(coefStr) else 0f
        val dotRadius = 1.6.dp.toPx()
        val dotGap = 2.dp.toPx()
        val coefBlockW = if (coef != null) coefTextW + dotGap + dotRadius * 2f else 0f

        val gapTimesCoef = 3.dp.toPx()
        val textBlockWidth = if (maxTextW > 0f) iconW + iconGap + maxTextW else 0f
        val totalWidth = if (coef != null) (textBlockWidth + gapTimesCoef + coefBlockW) else textBlockWidth

        val startX = ((w - totalWidth) / 2f).coerceAtLeast(0.5.dp.toPx())
        val textStartX = startX + iconW + iconGap

        val row1Y = h * 0.28f
        val row2Y = h * 0.72f

        if (highTimeStr.isNotEmpty()) {
            val iconRight = startX + iconW
            val arrowX = startX + iconW * 0.5f

            val wavePath = Path().apply {
                moveTo(startX, row1Y + iconH * 0.35f)
                quadraticBezierTo(
                    startX + iconW * 0.5f,
                    row1Y + iconH * 0.12f,
                    iconRight,
                    row1Y + iconH * 0.35f
                )
            }
            drawPath(wavePath, color = AppColors.TideHighDark.copy(alpha = 0.65f), style = Stroke(width = 0.75.dp.toPx()))

            val arrowBottom = row1Y + iconH * 0.35f
            val arrowTop = row1Y - iconH * 0.45f
            drawLine(
                color = AppColors.TideHighDark,
                start = Offset(arrowX, arrowBottom),
                end = Offset(arrowX, arrowTop),
                strokeWidth = 0.9.dp.toPx()
            )
            val headSize = 1.5.dp.toPx()
            val headPath = Path().apply {
                moveTo(arrowX - headSize, arrowTop + headSize)
                lineTo(arrowX, arrowTop)
                lineTo(arrowX + headSize, arrowTop + headSize)
            }
            drawPath(headPath, color = AppColors.TideHighDark, style = Stroke(width = 0.9.dp.toPx()))

            val highBaseline = row1Y - (highTextPaint.descent() + highTextPaint.ascent()) / 2f
            drawContext.canvas.nativeCanvas.drawText(highTimeStr, textStartX, highBaseline, highTextPaint)
        }

        if (lowTimeStr.isNotEmpty()) {
            val iconRight = startX + iconW
            val arrowX = startX + iconW * 0.5f

            val wavePath2 = Path().apply {
                moveTo(startX, row2Y - iconH * 0.35f)
                quadraticBezierTo(
                    startX + iconW * 0.5f,
                    row2Y - iconH * 0.12f,
                    iconRight,
                    row2Y - iconH * 0.35f
                )
            }
            drawPath(wavePath2, color = AppColors.TideLowDark.copy(alpha = 0.65f), style = Stroke(width = 0.75.dp.toPx()))

            val arrowTop = row2Y - iconH * 0.35f
            val arrowBottom = row2Y + iconH * 0.45f
            drawLine(
                color = AppColors.TideLowDark,
                start = Offset(arrowX, arrowTop),
                end = Offset(arrowX, arrowBottom),
                strokeWidth = 0.9.dp.toPx()
            )
            val headSize = 1.5.dp.toPx()
            val headPath2 = Path().apply {
                moveTo(arrowX - headSize, arrowBottom - headSize)
                lineTo(arrowX, arrowBottom)
                lineTo(arrowX + headSize, arrowBottom - headSize)
            }
            drawPath(headPath2, color = AppColors.TideLowDark, style = Stroke(width = 0.9.dp.toPx()))

            val lowBaseline = row2Y - (lowTextPaint.descent() + lowTextPaint.ascent()) / 2f
            drawContext.canvas.nativeCanvas.drawText(lowTimeStr, textStartX, lowBaseline, lowTextPaint)
        }

        if (coef != null) {
            val coefStartX = startX + textBlockWidth + gapTimesCoef
            val centerY = h / 2f

            val dotColor = when {
                coef < 55 -> AppColors.WindLow
                coef <= 80 -> AppColors.WindMid
                else -> AppColors.WindHigh
            }

            val coefBaseline = centerY - (coefTextPaint.descent() + coefTextPaint.ascent()) / 2f
            drawContext.canvas.nativeCanvas.drawText(coefStr, coefStartX, coefBaseline, coefTextPaint)

            val dotCenterX = coefStartX + coefTextW + dotGap + dotRadius
            val dotCenterY = centerY - 0.5.dp.toPx()
            drawCircle(color = dotColor, radius = dotRadius, center = Offset(dotCenterX, dotCenterY))
        }
    }
}

@Composable
fun DayWindThreeSlots(
    dailyData: List<HourlyUiModel>,
    windUnit: String,
    density: Int = 3,
    windMode: String = "both",
    modifier: Modifier = Modifier
) {
    val targetHours = hoursForDensity(density)
    val slots = targetHours.mapNotNull { hour -> dailyData.minByOrNull { abs(it.rawTime.hour - hour) } }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        slots.forEach { slot ->
            MiniWindSlot(slot = slot, windUnit = windUnit, windMode = windMode)
        }
    }
}

/** Point 2 : traduit la densite choisie (1/2/3 creneaux par jour) en heures cibles. */
private fun hoursForDensity(density: Int): List<Int> = when (density) {
    1 -> listOf(13)
    2 -> listOf(9, 18)
    else -> listOf(9, 13, 18)
}

@Composable
fun MiniWindSlot(slot: HourlyUiModel, windUnit: String, windMode: String = "both") {
    val dirFr = SurfUnitsHelper.formatCardinalFr(slot.windDirectionStr)
    val degrees = SurfUnitsHelper.cardinalToDegrees(dirFr)
    val rotationAngle = (degrees + 180f) % 360f
    val arrowColor = SurfUnitsHelper.getSurfWindColor(dirFr, slot.windSpeedKmh)
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    val formattedSpeed = SurfUnitsHelper.formatWindValue(slot.windSpeedKmh, windUnit)
    val showArrow = windMode == "arrow" || windMode == "both"
    val showText = windMode == "text" || windMode == "both"

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (showArrow) {
        Canvas(modifier = Modifier.size(12.dp)) {
            val w = size.width
            val h = size.height

            rotate(rotationAngle, pivot = Offset(w / 2f, h / 2f)) {
                val path = Path().apply {
                    moveTo(w * 0.5f, 0.5.dp.toPx())
                    lineTo(w * 0.95f, h * 0.48f)
                    lineTo(w * 0.68f, h * 0.48f)
                    lineTo(w * 0.68f, h * 0.98f)
                    lineTo(w * 0.32f, h * 0.98f)
                    lineTo(w * 0.32f, h * 0.48f)
                    lineTo(w * 0.05f, h * 0.48f)
                    close()
                }
                drawPath(path = path, color = arrowColor)
                drawPath(
                    path = path,
                    color = Color.Gray,
                    style = Stroke(width = 0.9.dp.toPx())
                )
            }
        }
        }

        if (showText) {
        Text(
            text = dirFr,
            fontSize = if (dirFr.length >= 3) 6.sp else 7.sp,
            fontWeight = FontWeight.Bold,
            color = if (windMode == "text") arrowColor else onSurfaceColor,
            maxLines = 1,
            lineHeight = 8.sp
        )
        }

        if (showText) {
        Text(
            text = formattedSpeed,
            fontSize = 6.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (windMode == "text") arrowColor else onSurfaceColor.copy(alpha = 0.7f),
            maxLines = 1,
            lineHeight = 8.sp
        )
        }
    }
}
