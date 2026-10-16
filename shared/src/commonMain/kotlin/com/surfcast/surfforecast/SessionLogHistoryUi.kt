@file:Suppress("SpellCheckingInspection")
package com.surfcast.surfforecast

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
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
import kotlin.math.roundToInt
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
    onDismiss: () -> Unit,
    quiverBoards: List<QuiverBoard> = emptyList(),
    onUpdateSession: (SurfSession) -> Unit = {},
    onDeleteSession: (Long) -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    var editing by remember { mutableStateOf<SurfSessionWithRelations?>(null) }
    var deleting by remember { mutableStateOf<SurfSessionWithRelations?>(null) }
    editing?.let { e -> SessionEditDialog(e, quiverBoards, { onUpdateSession(it); editing = null }) { editing = null } }
    deleting?.let { d ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Supprimer cette session ?") },
            text = { Text("Elle sera définitivement retirée de ton journal.") },
            confirmButton = { TextButton(onClick = { onDeleteSession(d.session.id); deleting = null }) { Text("Supprimer", color = AppColors.WindHigh) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Annuler") } }
        )
    }
    val sessionsByDate = remember(allSessions) {
        allSessions.groupBy { epochMillisToLocalDateTime(it.session.startTime).date }
    }

    var currentMonth by remember { mutableStateOf(firstOfMonth(nowLocalDateTime().date)) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    var statsOpen by remember { mutableStateOf(false) }
    var listOpen by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("all") }

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

                    val today = nowLocalDateTime().date
                    val grid = remember(currentMonth) { monthDaysGrid(currentMonth) }
                    grid.chunked(7).forEach { week ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            week.forEach { date ->
                                val day = date?.let { sessionsByDate[it] }.orEmpty()
                                val best = day.mapNotNull { feelIndexOf(it.session.rating) }.minByOrNull { FEEL_QUALITY_ORDER.indexOf(it) }
                                val isSelected = date != null && date == selectedDate
                                val isFuture = date != null && date > today
                                val isToday = date != null && date == today
                                val cellColor = if (best != null) bandWordColor(SESSION_FEELS[best].second) else colors.surfaceVariant
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1.1f)
                                        .padding(2.5.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (date == null) Color.Transparent else cellColor.copy(alpha = if (isFuture) 0.35f else 1f))
                                        // Aujourd'hui est toujours entouré (c'est le repère de départ) ; le jour choisi pour filtrer la liste a un trait plus épais.
                                        .then(if (isSelected) Modifier.border(3.dp, colors.primary, RoundedCornerShape(10.dp))
                                            else if (isToday) Modifier.border(2.dp, colors.primary, RoundedCornerShape(10.dp)) else Modifier)
                                        .then(if (date != null) Modifier.clickable { selectedDate = if (selectedDate == date) null else date; if (selectedDate != null) listOpen = true } else Modifier),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (date != null) Text(
                                        text = date.day.toString(), fontSize = 12.sp,
                                        fontWeight = if (best != null || isToday) FontWeight.ExtraBold else FontWeight.Normal,
                                        color = if (best != null) Color.White else if (isToday) colors.primary else colors.onBackground.copy(alpha = if (isFuture) 0.4f else 1f)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Le mois affiché, la semaine en cours : heures, répartition, idéal (le détail s'ouvre au toucher).
                    val monthSessions = remember(allSessions, currentMonth) {
                        allSessions.filter { epochMillisToLocalDateTime(it.session.startTime).date.let { d -> d.year == currentMonth.year && d.month == currentMonth.month } }
                    }
                    val weekStart = today.minus(today.dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)
                    val weekSessions = remember(allSessions, today) {
                        allSessions.filter { epochMillisToLocalDateTime(it.session.startTime).date.let { d -> d >= weekStart && d <= today } }
                    }
                    val isThisMonth = currentMonth.year == today.year && currentMonth.month == today.month
                    JournalSummary(monthSessions, weekSessions, isThisMonth, frenchMonthName(currentMonth), statsOpen) { statsOpen = !statsOpen }

                    Spacer(modifier = Modifier.height(10.dp))
                    SessionsFold(
                        monthCount = monthSessions.size, weekCount = weekSessions.size, isThisMonth = isThisMonth,
                        open = listOpen || selectedDate != null, onToggle = { listOpen = !listOpen; if (!listOpen) selectedDate = null }
                    )
                    if (listOpen || selectedDate != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        val boards = monthSessions.map { it.quiverBoard.model.replaceFirstChar { c -> c.uppercase() } }.distinct()
                        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            (listOf("all" to tr("Tout"), "good" to tr("Bon et mieux"), "motiv" to ("💪 " + tr("Motivation"))) + boards.map { it to it }).forEach { (k, label) ->
                                Text(
                                    label, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                                    color = if (filter == k) colors.onPrimary else colors.onSurfaceVariant,
                                    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (filter == k) colors.primary else colors.surfaceVariant)
                                        .clickable { filter = k }.padding(horizontal = 12.dp, vertical = 7.dp)
                                )
                            }
                        }
                        val shown = monthSessions.filter { se ->
                            (selectedDate == null || epochMillisToLocalDateTime(se.session.startTime).date == selectedDate) && when (filter) {
                                "all" -> true
                                "good" -> feelIndexOf(se.session.rating) in 3..5
                                "motiv" -> feelIndexOf(se.session.rating).let { it != null && it <= 2 }
                                else -> se.quiverBoard.model.replaceFirstChar { c -> c.uppercase() } == filter
                            }
                        }.sortedByDescending { it.session.startTime }
                        if (selectedDate != null) {
                            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                                Text(tr("Tout voir"), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.primary,
                                    modifier = Modifier.clip(CircleShape).clickable { selectedDate = null }.padding(horizontal = 10.dp, vertical = 6.dp))
                            }
                        }
                        if (shown.isEmpty()) {
                            Text(
                                text = tr(if (selectedDate != null) "Aucune session ce jour-là avec ce filtre." else "Aucune session avec ce filtre."),
                                fontSize = 12.sp, color = colors.onBackground.copy(alpha = 0.6f), modifier = Modifier.padding(top = 10.dp)
                            )
                        } else {
                            shown.groupBy { epochMillisToLocalDateTime(it.session.startTime).date }.forEach { (date, list) ->
                                val mins = list.sumOf { ((it.session.endTime - it.session.startTime) / 60_000L).toInt() }
                                Row(modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp, start = 2.dp, end = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("${frenchShortDayName(date)} ${date.day} ${frenchMonthName(date)}".uppercase(), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = colors.onBackground.copy(alpha = 0.6f))
                                    Text(durationHm(mins), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = colors.onBackground.copy(alpha = 0.6f))
                                }
                                list.forEach { session ->
                                    SessionRecapCard(session, onEdit = { editing = session }, onDelete = { deleting = session })
                                    Spacer(modifier = Modifier.height(10.dp))
                                }
                            }
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
private fun SessionRecapCard(session: SurfSessionWithRelations, onEdit: () -> Unit = {}, onDelete: () -> Unit = {}) {
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
                    text = hmShort(startTime) + " → " + hmShort(endTime) + " · " + session.microSpot.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface
                )
                feelIndexOf(session.session.rating)?.let { i ->
                    Text(tr(SESSION_FEELS[i].first), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = bandWordColor(SESSION_FEELS[i].second))
                }
            }
            val forecast = session.condition.forecastScore
            if (forecast != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(tr("Prévu"), fontSize = 11.5.sp, color = colors.onSurface.copy(alpha = 0.7f))
                    Text(forecastLabel(forecast), fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, color = bandWordColor(forecastBand(forecast)))
                    Text("· " + tr("Vécu"), fontSize = 11.5.sp, color = colors.onSurface.copy(alpha = 0.7f))
                    feelIndexOf(session.session.rating)?.let { i ->
                        Text(tr(SESSION_FEELS[i].first), fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, color = bandWordColor(SESSION_FEELS[i].second))
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = tr("Planche") + " : " + session.quiverBoard.model.replaceFirstChar { it.uppercase() },
                fontSize = 12.sp,
                color = colors.onSurface.copy(alpha = 0.7f)
            )
            Text(
                text = if (session.condition.hasConditions) conditionsLine(session.condition) else tr("Conditions non gardées (saisie à la main)."),
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
            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
                Text(
                    tr("Modifier"), fontSize = 12.sp, color = colors.primary,
                    modifier = Modifier.clip(CircleShape).clickable(onClick = onEdit).padding(horizontal = 10.dp, vertical = 6.dp)
                )
                Text(
                    tr("Supprimer"), fontSize = 12.sp, color = AppColors.WindHigh,
                    modifier = Modifier.clip(CircleShape).clickable(onClick = onDelete).padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

private fun hmShort(t: kotlinx.datetime.LocalDateTime): String = t.hour.toString() + " h " + t.minute.toString().padStart(2, '0')

/** « Conditions : 441 kJ · houle 1,4 m · 12 s · WNW · vent 7 km/h ESE » : période arrondie à la seconde, direction de la houle comprise. */
private fun conditionsLine(c: ConditionSnapshot): String {
    val height = formatDecimal(c.waveHeight, 1).replace('.', ',')
    val swellDir = SurfUnitsHelper.formatCardinalFr(getCardinalDirection(c.waveDirection.toDouble()))
    val windDir = SurfUnitsHelper.formatCardinalFr(getCardinalDirection(c.windDirection.toDouble()))
    return "Conditions : ${c.energyKj} kJ · houle $height m · ${c.wavePeriod.roundToInt()} s · $swellDir (${c.waveDirection}°) · vent ${c.windSpeedKmh} km/h $windDir"
}

/** Du meilleur au moins bon : Parfait, Très bon, Bon, Challengeant, Correct, Médiocre, Mauvais (rangs de [SESSION_FEELS]). */
private val FEEL_QUALITY_ORDER = listOf(5, 4, 3, 6, 2, 1, 0)

private fun sessionMinutes(s: SurfSessionWithRelations): Int = ((s.session.endTime - s.session.startTime) / 60_000L).toInt().coerceAtLeast(0)

/** « 2 h 30 » : une durée en minutes. */
private fun durationHm(min: Int): String = (min / 60).toString() + " h " + (min % 60).toString().padStart(2, '0')

@OptIn(ExperimentalLayoutApi::class)
/** Le résumé : les heures de la semaine et du mois sur une ligne ; au toucher, la répartition, la motivation, l'idéal et la planche. */
@Composable
private fun JournalSummary(
    month: List<SurfSessionWithRelations>, week: List<SurfSessionWithRelations>, isThisMonth: Boolean, monthName: String,
    open: Boolean, onToggle: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val monthMin = month.sumOf { sessionMinutes(it) }
    val weekMin = week.sumOf { sessionMinutes(it) }
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surfaceVariant).clickable(onClick = onToggle).padding(horizontal = 14.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(tr("Cette semaine").uppercase(), fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = colors.onSurfaceVariant)
                Text(durationHm(weekMin), fontSize = 20.sp, fontWeight = FontWeight.Medium, color = colors.onSurface, maxLines = 1)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text((if (isThisMonth) tr("Ce mois-ci") else monthName.replaceFirstChar { it.uppercase() }).uppercase(), fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = colors.onSurfaceVariant, maxLines = 1)
                Text(durationHm(monthMin), fontSize = 20.sp, fontWeight = FontWeight.Medium, color = colors.onSurface, maxLines = 1)
            }
            CollapseChevron(!open, onToggle)
        }
        if (open) {
            Spacer(Modifier.height(12.dp))
            if (month.isEmpty()) {
                Text(tr("Pas encore de session ce mois-ci."), fontSize = 12.5.sp, color = colors.onSurfaceVariant)
            } else {
                val byFeel = FEEL_QUALITY_ORDER.map { i -> i to month.filter { feelIndexOf(it.session.rating) == i }.sumOf { sessionMinutes(it) } }.filter { it.second > 0 }
                Text(tr("Répartition").uppercase(), fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = colors.onSurfaceVariant)
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).height(12.dp).clip(RoundedCornerShape(6.dp))) {
                    byFeel.forEach { (i, m) -> Box(Modifier.weight(m.toFloat()).fillMaxHeight().background(bandWordColor(SESSION_FEELS[i].second))) }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    byFeel.forEach { (i, m) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(bandWordColor(SESSION_FEELS[i].second)))
                            Text(" " + tr(SESSION_FEELS[i].first) + " " + durationHm(m), fontSize = 11.5.sp, color = colors.onSurface)
                        }
                    }
                }
                val motiv = month.filter { feelIndexOf(it.session.rating).let { f -> f != null && f <= 2 } }.sumOf { sessionMinutes(it) }
                if (motiv > 0) Text("💪 " + tr("Motivation") + " : " + durationHm(motiv) + " " + tr("dans l'eau alors que les vagues n'étaient pas terribles."), fontSize = 12.5.sp, color = colors.onSurface, modifier = Modifier.padding(top = 10.dp))
                val good = month.filter { feelIndexOf(it.session.rating) in 3..5 && it.condition.hasConditions }
                if (good.isNotEmpty()) {
                    Text(tr("Ton idéal").uppercase() + " · " + tr("sessions Bon et mieux"), fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
                    val hs = good.map { it.condition.waveHeight }; val ps = good.map { it.condition.wavePeriod.roundToInt() }
                    fun r1(x: Double) = formatDecimal(x, 1).replace('.', ',')
                    val windDir = good.groupingBy { SurfUnitsHelper.formatCardinalFr(getCardinalDirection(it.condition.windDirection.toDouble())) }.eachCount().maxByOrNull { it.value }?.key
                    val windAvg = good.map { it.condition.windSpeedKmh }.average().roundToInt()
                    val coefs = good.mapNotNull { it.condition.tideCoeff }
                    val boardTop = good.groupingBy { it.quiverBoard.model.replaceFirstChar { c -> c.uppercase() } }.eachCount().maxByOrNull { it.value }
                    val lines = buildList {
                        add(tr("Houle") + " " + r1(hs.min()) + " " + tr("à") + " " + r1(hs.max()) + " m · " + ps.min() + " " + tr("à") + " " + ps.max() + " s")
                        add(tr("Vent") + " " + windDir.orEmpty() + ", ~" + windAvg + " km/h")
                        if (coefs.isNotEmpty()) add(tr("Marée") + " coef " + coefs.min() + " " + tr("à") + " " + coefs.max())
                        boardTop?.let { add(tr("Planche") + " " + it.key + " (" + it.value + " / " + good.size + ")") }
                    }
                    lines.forEach { Text(it, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.primary, modifier = Modifier.padding(top = 3.dp)) }
                }
            }
        }
    }
}

/** La ligne « Sessions · 8 ce mois-ci · 2 cette semaine » qui ouvre ou replie la liste des jours. */
@Composable
private fun SessionsFold(monthCount: Int, weekCount: Int, isThisMonth: Boolean, open: Boolean, onToggle: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surfaceVariant).clickable(onClick = onToggle).padding(start = 14.dp, top = 4.dp, bottom = 4.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(tr("Sessions").uppercase(), fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = colors.onSurfaceVariant)
            Text(
                "$monthCount " + tr(if (isThisMonth) "ce mois-ci" else "ce mois") + " · $weekCount " + tr("cette semaine"),
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface
            )
        }
        CollapseChevron(!open, onToggle)
    }
}
