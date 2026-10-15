@file:Suppress("SpellCheckingInspection")
package com.surfcast.surfforecast

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import kotlinx.datetime.minus
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import com.surfcast.surfforecast.ui.theme.AppColors

@Composable
fun JournalIcon(
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = w * 0.09f)
        drawRoundRect(
            color = color,
            size = Size(w * 0.8f, h * 0.9f),
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.1f, h * 0.05f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.08f),
            style = stroke
        )
        val lineY = listOf(0.38f, 0.55f, 0.72f)
        lineY.forEach { fraction ->
            drawLine(
                color = color,
                start = androidx.compose.ui.geometry.Offset(w * 0.28f, h * fraction),
                end = androidx.compose.ui.geometry.Offset(w * 0.72f, h * fraction),
                strokeWidth = w * 0.06f
            )
        }
    }
}

@Composable
private fun PillSelector(
    label: String,
    options: List<Pair<Long, String>>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    onSurfaceColor: Color,
    primaryColor: Color,
    onAddNew: ((String) -> Unit)? = null,
    addPrompt: String = "",
    emptyHint: String? = null
) {
    var showAddField by remember { mutableStateOf(false) }
    var newValue by remember { mutableStateOf("") }
    val pillShape = RoundedCornerShape(50)

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = onSurfaceColor.copy(alpha = 0.7f))
        Spacer(modifier = Modifier.height(4.dp))

        if (options.isEmpty() && onAddNew == null && emptyHint != null) {
            Text(text = emptyHint, fontSize = 12.sp, color = onSurfaceColor.copy(alpha = 0.5f))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            options.forEach { (id, text) ->
                val isSelected = id == selectedId
                Box(
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .clip(pillShape)
                        .background(if (isSelected) primaryColor.copy(alpha = 0.2f) else onSurfaceColor.copy(alpha = 0.08f))
                        .clickable { onSelect(id) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(text = text, fontSize = 12.sp, color = if (isSelected) primaryColor else onSurfaceColor, maxLines = 1)
                }
            }
            if (onAddNew != null) {
                Box(
                    modifier = Modifier
                        .clip(pillShape)
                        .background(onSurfaceColor.copy(alpha = 0.08f))
                        .clickable { showAddField = true }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(text = "+ Ajouter", fontSize = 12.sp, color = onSurfaceColor)
                }
            }
        }
        if (showAddField && onAddNew != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newValue,
                    onValueChange = { newValue = it },
                    placeholder = { Text(addPrompt, fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = {
                    val trimmed = newValue.trim()
                    if (trimmed.isNotBlank()) {
                        val existing = options.firstOrNull { it.second.equals(trimmed, ignoreCase = true) }
                        if (existing != null) {
                            onSelect(existing.first)
                        } else {
                            onAddNew(trimmed)
                        }
                        newValue = ""
                        showAddField = false
                    }
                }) {
                    Text("OK")
                }
            }
        }
    }
}

@Composable
private fun HourRangeSelector(
    label: String,
    selectedHour: Int?,
    onSelect: (Int) -> Unit,
    onSurfaceColor: Color,
    primaryColor: Color
) {
    val pillShape = RoundedCornerShape(50)
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = onSurfaceColor.copy(alpha = 0.7f))
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            (0..23).forEach { hour ->
                val isSelected = hour == selectedHour
                Box(
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .clip(pillShape)
                        .background(if (isSelected) primaryColor.copy(alpha = 0.2f) else onSurfaceColor.copy(alpha = 0.08f))
                        .clickable { onSelect(hour) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(text = "${hour}h", fontSize = 12.sp, color = if (isSelected) primaryColor else onSurfaceColor)
                }
            }
        }
    }
}

@Composable
private fun StarRating(rating: Int, onRatingChanged: (Int) -> Unit) {
    Row {
        (1..5).forEach { star ->
            Text(
                text = if (star <= rating) "★" else "☆",
                fontSize = 26.sp,
                color = if (star <= rating) AppColors.WindHigh else Color.Gray,
                modifier = Modifier
                    .clickable { onRatingChanged(star) }
                    .padding(end = 4.dp)
            )
        }
    }
}

private fun hm(min: Int): String = (min / 60).toString() + " h " + (min % 60).toString().padStart(2, '0')

private fun durationLabel(min: Int): String = if (min < 60) "$min min" else (min / 60).toString() + " h" + (if (min % 60 != 0) " " + (min % 60).toString().padStart(2, '0') else "")

private fun capital(s: String): String = s.replaceFirstChar { it.uppercase() }

/** La barre du jour (comme celle d'Actuellement) pour choisir l'heure de début : on touche ou on glisse ; la plage de la session est cernée. */
@Composable
private fun StartRuler(
    hours: List<HourlyUiModel>,
    cells: List<TimeCell>,
    startMin: Int,
    endMin: Int,
    onPick: (Int) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    fun hourAt(c: TimeCell): Int = hours[c.representativeIndex()].rawTime.hour
    fun pick(x: Float, width: Float) {
        if (cells.isEmpty() || width <= 0f) return
        val i = ((x / width) * cells.size).toInt().coerceIn(0, cells.lastIndex)
        onPick(hourAt(cells[i]))
    }
    Row(
        modifier = Modifier.fillMaxWidth()
            .pointerInput(cells) { detectTapGestures { pick(it.x, size.width.toFloat()) } }
            .pointerInput(cells) { detectHorizontalDragGestures { change, _ -> pick(change.position.x, size.width.toFloat()) } },
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        cells.forEach { c ->
            val first = hours[c.first].rawTime.hour
            val last = hours[c.last].rawTime.hour
            val inside = startMin < (last + 1) * 60 && endMin > first * 60
            val base = if (c.isBlock) colors.onSurface.copy(alpha = 0.18f) else Color(0xFFD9A94A)
            Box(
                modifier = Modifier.weight(1f).height(20.dp).clip(RoundedCornerShape(6.dp))
                    .background(base)
                    .then(if (inside) Modifier.border(2.dp, Color.White, RoundedCornerShape(6.dp)) else Modifier)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SessionLogEntryDialog(
    spotName: String,
    // Par jour (0 = aujourd'hui, 1 = hier, 2 = avant-hier) : les heures prévues, la marée et le soleil.
    hoursFor: (Int) -> List<HourlyUiModel>,
    tideFor: (Int) -> DailyTideInfo?,
    sunFor: (Int) -> DailySunInfo?,
    ratingOf: (HourlyUiModel) -> SlotRating,
    beachFacing: Int?,
    nowHour: Int,
    quiverBoards: List<QuiverBoard>,
    onAddBoard: (name: String) -> Unit,
    microSpots: List<MicroSpot>,
    onAddMicroSpot: (name: String) -> Unit,
    onUpdateMicroSpot: (MicroSpot) -> Unit = {},
    // Astuces affichées en tête la première fois qu'on ouvre le journal.
    showTips: Boolean = false,
    onDismissTips: () -> Unit = {},
    onSave: (dayOffset: Int, startMin: Int, endMin: Int, microSpotId: Long, quiverId: Long, rating: Int, comment: String?, mediaUri: String?, forecastScore: Int?) -> Unit,
    onDismiss: () -> Unit,
    // Jours proposés : 0 = aujourd'hui, 1 = hier, 2 = avant-hier (seulement si l'app a gardé les conditions de ce jour).
    availableDayOffsets: List<Int> = listOf(0),
    initialDayOffset: Int = 0
) {
    val colors = MaterialTheme.colorScheme

    var dayOffset by remember { mutableStateOf(initialDayOffset) }
    var startHour by remember { mutableStateOf<Int?>(null) }
    var durationMin by remember { mutableStateOf(80) }
    var selectedQuiverId by remember { mutableStateOf<Long?>(null) }
    var feel by remember { mutableStateOf<Int?>(null) }
    var comment by remember { mutableStateOf("") }
    var mediaUri by remember { mutableStateOf<String?>(null) }
    var showBoardField by remember { mutableStateOf(false) }
    var newBoard by remember { mutableStateOf("") }
    var pendingBoardName by remember { mutableStateOf<String?>(null) }
    var selectedMicroSpotId by remember { mutableStateOf<Long?>(null) }
    var askedDefaultSpot by remember { mutableStateOf(false) }

    val dayHours = hoursFor(dayOffset)
    val sun = sunFor(dayOffset)
    val cells = remember(dayHours, sun) { buildTimeCells(dayHours, sun) }

    // Heure de début par défaut : aujourd'hui, 2 h avant maintenant ; les autres jours, une heure après le lever.
    LaunchedEffect(dayOffset, dayHours.size) {
        if (startHour == null || startHour !in dayHours.map { it.rawTime.hour }) {
            val sunrise = sun?.sunrise?.hour ?: 7
            startHour = if (dayOffset == 0) (nowHour - 2).coerceIn(sunrise, 22) else (sunrise + 1).coerceIn(0, 22)
        }
    }
    // Un seul banc, ou aucun : on ne le demande pas ; on en crée un au nom du spot et on le choisit.
    LaunchedEffect(microSpots) {
        if (microSpots.isEmpty() && !askedDefaultSpot) { askedDefaultSpot = true; onAddMicroSpot(spotName) }
        if (selectedMicroSpotId == null || microSpots.none { it.id == selectedMicroSpotId }) selectedMicroSpotId = microSpots.firstOrNull()?.id
    }
    // Une planche tout juste ajoutée : on la choisit dès qu'elle apparaît.
    LaunchedEffect(quiverBoards, pendingBoardName) {
        val pending = pendingBoardName
        if (pending != null) {
            quiverBoards.firstOrNull { it.model.equals(pending, ignoreCase = true) }?.let { selectedQuiverId = it.id; pendingBoardName = null }
        } else if (selectedQuiverId == null || quiverBoards.none { it.id == selectedQuiverId }) {
            selectedQuiverId = quiverBoards.firstOrNull()?.id
        }
    }

    val openMediaPicker = rememberMediaPicker { mediaUri = it }

    val startMin = (startHour ?: 8) * 60
    val maxDuration = (minOf(300, 1439 - startMin) / 20 * 20).coerceAtLeast(20)
    if (durationMin > maxDuration) durationMin = maxDuration
    val endMin = startMin + durationMin
    val window = sessionWindowHours(dayHours, startMin, endMin)
    val sentence = sessionSentence(window, beachFacing, tidePhaseAt(kotlinx.datetime.LocalTime(((startMin + endMin) / 2 / 60).coerceIn(0, 23), (startMin + endMin) / 2 % 60), tideFor(dayOffset)))
    val forecastScore = windowForecastScore(window, ratingOf)
    val canSave = startHour != null && selectedMicroSpotId != null && selectedQuiverId != null && feel != null

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.94f).heightIn(max = 760.dp).clip(RoundedCornerShape(16.dp)),
            color = colors.surface
        ) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Session") + " · " + spotName, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = colors.onSurface)
                    Text("✕", fontSize = 16.sp, color = colors.onSurfaceVariant, modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onDismiss).padding(horizontal = 8.dp, vertical = 2.dp))
                }
                if (showTips) {
                    Spacer(modifier = Modifier.height(8.dp))
                    JournalTipsCard(onDismiss = onDismissTips)
                }

                // Jour
                Spacer(modifier = Modifier.height(8.dp))
                SettingsSegments(
                    listOf(2, 1, 0).filter { it in availableDayOffsets }.map { it.toString() to dayOffsetLabel(it) },
                    dayOffset.toString()
                ) { dayOffset = it.toInt(); startHour = null }
                // Les jours plus anciens (jusqu'à 14 jours), seulement si l'appli en a gardé les conditions.
                val olderDays = availableDayOffsets.filter { it >= 3 }.sorted()
                if (olderDays.isNotEmpty()) {
                    var showOlder by remember { mutableStateOf(dayOffset >= 3) }
                    Text(
                        tr(if (showOlder) "Jours plus anciens ▴" else "Jours plus anciens ▾"), fontSize = 12.sp, color = colors.primary,
                        modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(50)).clickable { showOlder = !showOlder }.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                    if (showOlder) {
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            olderDays.forEach { off ->
                                val d = nowLocalDateTime().date.minus(off, kotlinx.datetime.DateTimeUnit.DAY)
                                val on = dayOffset == off
                                Text(
                                    "${frenchShortDayName(d)} ${d.day}", fontSize = 13.sp, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.Normal,
                                    color = if (on) colors.primary else colors.onSurface,
                                    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) colors.primary.copy(alpha = 0.15f) else colors.surfaceVariant)
                                        .clickable { dayOffset = off; startHour = null }.padding(horizontal = 12.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }
                }

                // Début : la barre du jour
                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(tr("DÉBUT"), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.onSurfaceVariant)
                    Text(hm(startMin), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = colors.onSurface)
                }
                Spacer(modifier = Modifier.height(4.dp))
                if (cells.isNotEmpty()) StartRuler(dayHours, cells, startMin, endMin) { startHour = it }

                // Durée
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(tr("DURÉE"), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.onSurfaceVariant)
                    Text(durationLabel(durationMin), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = colors.onSurface)
                }
                if (maxDuration > 20) {
                    Slider(
                        value = durationMin.toFloat(),
                        onValueChange = { durationMin = ((it / 20f).roundToInt() * 20).coerceIn(20, maxDuration) },
                        valueRange = 20f..maxDuration.toFloat(),
                        steps = ((maxDuration - 20) / 20 - 1).coerceAtLeast(0),
                        colors = SliderDefaults.colors(activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent),
                        modifier = Modifier.height(28.dp)
                    )
                }
                Text(
                    tr("de") + " " + hm(startMin) + " " + tr("à") + " " + hm(endMin),
                    fontSize = 12.sp, color = colors.onSurfaceVariant, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                // Planche : le nom, rien d'autre
                Spacer(modifier = Modifier.height(10.dp))
                Text(tr("PLANCHE"), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    quiverBoards.forEach { board ->
                        val on = board.id == selectedQuiverId
                        Text(
                            capital(board.model), fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, maxLines = 1,
                            color = if (on) colors.onPrimary else colors.onSurface,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) colors.primary else colors.surfaceVariant)
                                .clickable { selectedQuiverId = board.id; showBoardField = false }.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                    Text(
                        "+ " + tr("Planche"), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.primary,
                        modifier = Modifier.clip(RoundedCornerShape(50)).border(1.5.dp, colors.primary, RoundedCornerShape(50))
                            .clickable { showBoardField = !showBoardField }.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
                if (showBoardField) {
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newBoard, onValueChange = { newBoard = it }, singleLine = true,
                            placeholder = { Text(tr("Nom de la planche"), fontSize = 12.sp) }, modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = {
                            val name = newBoard.trim()
                            if (name.isNotBlank()) {
                                val existing = quiverBoards.firstOrNull { it.model.equals(name, ignoreCase = true) }
                                if (existing != null) selectedQuiverId = existing.id else { pendingBoardName = name; onAddBoard(name) }
                                newBoard = ""; showBoardField = false
                            }
                        }) { Text("OK") }
                    }
                }

                // Conditions prévues sur le créneau, puis Prévu / Vécu
                if (sentence != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        sentence, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = colors.onSurface,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(colors.surfaceVariant).padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Row {
                        Text(tr("Prévu") + " ", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurfaceVariant)
                        if (forecastScore != null) Text(forecastLabel(forecastScore), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = bandWordColor(forecastBand(forecastScore)))
                        else Text("…", fontSize = 13.sp, color = colors.onSurfaceVariant)
                    }
                    Row {
                        Text(tr("Vécu") + " ", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurfaceVariant)
                        val f = feel
                        if (f != null) Text(tr(SESSION_FEELS[f].first), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = bandWordColor(SESSION_FEELS[f].second))
                        else Text("…", fontSize = 13.sp, color = colors.onSurfaceVariant)
                    }
                }
                Spacer(modifier = Modifier.height(5.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    SESSION_FEELS.forEachIndexed { i, (name, band) ->
                        val on = feel == i
                        val predicted = forecastScore != null && forecastBand(forecastScore) == band
                        Text(
                            tr(name), fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                            color = if (on) band.onColor() else colors.onSurfaceVariant,
                            modifier = Modifier.clip(RoundedCornerShape(50))
                                .background(if (on) band.color() else colors.surfaceVariant)
                                .border(if (predicted && !on) 2.dp else 1.dp, if (on) Color.White else band.color().copy(alpha = if (predicted) 1f else 0.4f), RoundedCornerShape(50))
                                .clickable { feel = i }.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = comment, onValueChange = { comment = it }, singleLine = true,
                    placeholder = { Text(tr("Une note perso (facultatif)"), fontSize = 12.5.sp) },
                    modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                )
                if (openMediaPicker != null) {
                    TextButton(onClick = openMediaPicker) {
                        Text(if (mediaUri == null) tr("Ajouter photo / video") else tr("1 media joint"), fontSize = 12.5.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        onSave(dayOffset, startMin, endMin, selectedMicroSpotId!!, selectedQuiverId!!, encodeFeel(feel!!), comment.ifBlank { null }, mediaUri, forecastScore)
                        onDismiss()
                    },
                    enabled = canSave, modifier = Modifier.fillMaxWidth()
                ) { Text(tr("Enregistrer")) }
            }
        }
    }
}

private fun dayOffsetLabel(offset: Int): String = when (offset) {
    0 -> "Aujourd'hui"
    1 -> "Hier"
    else -> "Avant-hier"
}
