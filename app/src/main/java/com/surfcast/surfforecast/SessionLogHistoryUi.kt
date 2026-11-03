@file:Suppress("SpellCheckingInspection")
package com.surfcast.surfforecast

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.surfcast.surfforecast.ui.theme.AppColors
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

private fun monthDaysGrid(yearMonth: YearMonth): List<LocalDate?> {
    val firstOfMonth = yearMonth.atDay(1)
    // Lundi = 0 ... Dimanche = 6
    val leadingBlanks = (firstOfMonth.dayOfWeek.value - 1).coerceIn(0, 6)
    val daysInMonth = yearMonth.lengthOfMonth()
    val days = (1..daysInMonth).map { yearMonth.atDay(it) }
    val cells = mutableListOf<LocalDate?>()
    repeat(leadingBlanks) { cells.add(null) }
    cells.addAll(days)
    while (cells.size % 7 != 0) cells.add(null)
    return cells
}

@Composable
fun SessionLogHistoryScreen(
    allSessions: List<SurfSessionWithRelations>,
    onOpenNewEntry: () -> Unit,
    onOpenQuiver: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val zone = ZoneId.systemDefault()

    val sessionsByDate = remember(allSessions) {
        allSessions.groupBy { Instant.ofEpochMilli(it.session.startTime).atZone(zone).toLocalDate() }
    }

    val initialSelectedDate = remember(sessionsByDate) { sessionsByDate.keys.maxOrNull() }
    var currentMonth by remember { mutableStateOf(initialSelectedDate?.let { YearMonth.from(it) } ?: YearMonth.now()) }
    var selectedDate by remember { mutableStateOf(initialSelectedDate) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(16.dp)),
            color = colors.background
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Journal de session", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onOpenQuiver, modifier = Modifier.size(32.dp)) {
                                QuiverIcon(color = colors.onBackground, modifier = Modifier.size(20.dp))
                            }
                            TextButton(onClick = onDismiss) { Text("Fermer") }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "‹",
                            fontSize = 22.sp,
                            color = colors.onBackground,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { currentMonth = currentMonth.minusMonths(1) }
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                        Text(
                            text = currentMonth.month.getDisplayName(TextStyle.FULL, Locale.FRANCE)
                                .replaceFirstChar { it.uppercase() } + " ${currentMonth.year}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.onBackground
                        )
                        Text(
                            text = "›",
                            fontSize = 22.sp,
                            color = colors.onBackground,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { currentMonth = currentMonth.plusMonths(1) }
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        listOf("L", "M", "M", "J", "V", "S", "D").forEach { label ->
                            Text(
                                text = label,
                                fontSize = 11.5.sp,
                                color = colors.onBackground.copy(alpha = 0.5f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    val grid = remember(currentMonth) { monthDaysGrid(currentMonth) }
                    grid.chunked(7).forEach { week ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            week.forEach { date ->
                                val hasSession = date != null && sessionsByDate.containsKey(date)
                                val isSelected = date != null && date == selectedDate
                                val isToday = date != null && date == LocalDate.now()
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .padding(2.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                isSelected -> colors.primary.copy(alpha = 0.25f)
                                                else -> Color.Transparent
                                            }
                                        )
                                        .then(
                                            if (date != null) Modifier.clickable { selectedDate = date } else Modifier
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (date != null) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = date.dayOfMonth.toString(),
                                                fontSize = 12.sp,
                                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) colors.primary else colors.onBackground
                                            )
                                            if (hasSession) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(4.dp)
                                                        .clip(CircleShape)
                                                        .background(AppColors.TideLow)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = colors.onBackground.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(12.dp))

                    val sessionsForSelectedDate = selectedDate?.let { sessionsByDate[it] } ?: emptyList()

                    if (selectedDate == null) {
                        Text(
                            text = "Selectionne un jour dans le calendrier pour voir tes sessions.",
                            fontSize = 12.sp,
                            color = colors.onBackground.copy(alpha = 0.6f)
                        )
                    } else if (sessionsForSelectedDate.isEmpty()) {
                        Text(
                            text = "Aucune session enregistree ce jour-la.",
                            fontSize = 12.sp,
                            color = colors.onBackground.copy(alpha = 0.6f)
                        )
                    } else {
                        sessionsForSelectedDate.forEach { session ->
                            SessionRecapCard(session)
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(72.dp))
                }

                FloatingActionButton(
                    onClick = onOpenNewEntry,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(20.dp)
                ) {
                    Text("+", fontSize = 24.sp)
                }
            }
        }
    }
}

@Composable
private fun SessionRecapCard(session: SurfSessionWithRelations) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val startTime = Instant.ofEpochMilli(session.session.startTime).atZone(zone).toLocalTime()
    val endTime = Instant.ofEpochMilli(session.session.endTime).atZone(zone).toLocalTime()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.surface,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${startTime.hour}h-${endTime.hour}h · ${session.microSpot.name}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface
                )
                val rating = session.session.rating.coerceIn(0, 5)
                Text(
                    text = "★".repeat(rating) + "☆".repeat(5 - rating),
                    fontSize = 13.sp,
                    color = AppColors.WindHigh
                )
            }

            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Planche : ${session.quiverBoard.model}",
                fontSize = 12.sp,
                color = colors.onSurface.copy(alpha = 0.7f)
            )
            Text(
                text = "Conditions : ${session.condition.energyKj} kJ · vent ${session.condition.windSpeedKmh} km/h · houle ${session.condition.waveHeight}m/${session.condition.wavePeriod}s",
                fontSize = 12.sp,
                color = colors.onSurface.copy(alpha = 0.7f)
            )

            if (!session.session.comment.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = session.session.comment,
                    fontSize = 12.sp,
                    color = colors.onSurface
                )
            }

            val mediaUriString = session.session.mediaUri
            if (!mediaUriString.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                // La permission de lecture persistante n'est pas garantie (SessionLogUi.kt
                // garde l'URI meme si takePersistableUriPermission echoue) : getType() peut
                // donc lever une SecurityException apres redemarrage du process.
                val isVideo = remember(mediaUriString) {
                    mediaUriString.contains("video") || try {
                        context.contentResolver.getType(android.net.Uri.parse(mediaUriString))?.startsWith("video") == true
                    } catch (_: SecurityException) {
                        false
                    }
                }

                if (isVideo) {
                    OutlinedButton(onClick = {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(android.net.Uri.parse(mediaUriString), "video/*")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        try {
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            // Aucune appli pour lire la video (ActivityNotFoundException) ou
                            // permission perdue (SecurityException) : on ignore plutot que de crasher.
                        }
                    }) {
                        Text("Lire la video", fontSize = 12.sp)
                    }
                } else {
                    AsyncImage(
                        model = android.net.Uri.parse(mediaUriString),
                        contentDescription = "Photo de session",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                }
            }
        }
    }
}
