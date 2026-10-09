@file:Suppress("SpellCheckingInspection")
package com.surfcast.surfforecast

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import com.surfcast.surfforecast.ui.theme.AppColors

// Mois représenté par son 1er jour.
private fun monthDaysGrid(firstOfMonth: LocalDate): List<LocalDate?> {
    // Lundi = 0 ... Dimanche = 6
    val leadingBlanks = (firstOfMonth.dayOfWeek.isoDayNumber - 1).coerceIn(0, 6)
    val daysInMonth = firstOfMonth.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY).day
    val days = (1..daysInMonth).map { LocalDate(firstOfMonth.year, firstOfMonth.month, it) }
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
    val sessionsByDate = remember(allSessions) {
        allSessions.groupBy { epochMillisToLocalDateTime(it.session.startTime).date }
    }

    val initialSelectedDate = remember(sessionsByDate) { sessionsByDate.keys.maxOrNull() }
    var currentMonth by remember { mutableStateOf(firstOfMonth(initialSelectedDate ?: nowLocalDateTime().date)) }
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
                            MyGearButton(onClick = onOpenQuiver)
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
                                .clickable { currentMonth = currentMonth.minus(1, DateTimeUnit.MONTH) }
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                        Text(
                            text = frenchMonthName(currentMonth).replaceFirstChar { it.uppercase() } + " ${currentMonth.year}",
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
                                .clickable { currentMonth = currentMonth.plus(1, DateTimeUnit.MONTH) }
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
                                val isToday = date != null && date == nowLocalDateTime().date
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
                                                text = date.day.toString(),
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
    val startTime = epochMillisToLocalDateTime(session.session.startTime)
    val endTime = epochMillisToLocalDateTime(session.session.endTime)

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
                StarsRow(value = session.session.rating.coerceIn(0, 5).toFloat(), starSize = 14.dp)
            }
            val forecast = session.condition.forecastScore
            if (forecast != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Prévu", fontSize = 11.sp, color = colors.onSurface.copy(alpha = 0.7f))
                    if (forecast < 0) {
                        Text("trop gros", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ScoreBand.TOO_BIG.color())
                    } else {
                        StarsRow(value = starsForScore(forecast), starSize = 11.dp)
                    }
                    Text("· Vécu", fontSize = 11.sp, color = colors.onSurface.copy(alpha = 0.7f))
                    StarsRow(value = session.session.rating.coerceIn(0, 5).toFloat(), starSize = 11.dp)
                }
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
                SessionMediaView(mediaUriString)
            }
        }
    }
}
