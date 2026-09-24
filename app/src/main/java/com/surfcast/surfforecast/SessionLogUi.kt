@file:Suppress("SpellCheckingInspection")
package com.surfcast.surfforecast

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.surfcast.surfforecast.ui.theme.AppColors
import java.time.LocalDate

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
            Text(text = emptyHint, fontSize = 11.sp, color = onSurfaceColor.copy(alpha = 0.5f))
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

@Composable
fun SessionLogEntryDialog(
    spotName: String,
    todayHours: List<HourlyUiModel>,
    tideInfo: DailyTideInfo?,
    quiverBoards: List<QuiverBoard>,
    microSpots: List<MicroSpot>,
    onAddMicroSpot: (name: String) -> Unit,
    onSave: (startHour: Int, endHour: Int, microSpotId: Long, quiverId: Long, rating: Int, comment: String?, mediaUri: String?) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current

    var startHour by remember { mutableStateOf<Int?>(null) }
    var endHour by remember { mutableStateOf<Int?>(null) }
    var selectedMicroSpotId by remember { mutableStateOf<Long?>(null) }
    var selectedQuiverId by remember { mutableStateOf<Long?>(null) }
    var rating by remember { mutableStateOf(0) }
    var comment by remember { mutableStateOf("") }
    var mediaUri by remember { mutableStateOf<Uri?>(null) }
    var pendingMicroSpotName by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(microSpots, pendingMicroSpotName) {
        val pending = pendingMicroSpotName
        if (pending != null) {
            val match = microSpots.firstOrNull { it.name.equals(pending, ignoreCase = true) }
            if (match != null) {
                selectedMicroSpotId = match.id
                pendingMicroSpotName = null
            }
        }
    }

    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {
                // Certains fournisseurs ne supportent pas la permission persistante, on garde l'URI quand meme.
            }
            mediaUri = uri
        }
    }

    val canSave = startHour != null && endHour != null && (endHour ?: 0) > (startHour ?: -1) &&
        selectedMicroSpotId != null && selectedQuiverId != null && rating > 0

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(16.dp)),
            color = colors.background
        ) {
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
                    TextButton(onClick = onDismiss) { Text("Fermer") }
                }
                Text(
                    text = "$spotName · Aujourd'hui",
                    fontSize = 12.sp,
                    color = colors.onBackground.copy(alpha = 0.6f)
                )

                Spacer(modifier = Modifier.height(16.dp))
                HourRangeSelector("Debut", startHour, { startHour = it }, colors.onBackground, colors.primary)
                Spacer(modifier = Modifier.height(12.dp))
                HourRangeSelector("Fin", endHour, { endHour = it }, colors.onBackground, colors.primary)

                Spacer(modifier = Modifier.height(16.dp))
                PillSelector(
                    label = "Sous-spot",
                    options = microSpots.map { it.id to it.name },
                    selectedId = selectedMicroSpotId,
                    onSelect = { selectedMicroSpotId = it },
                    onAddNew = { name ->
                        pendingMicroSpotName = name
                        onAddMicroSpot(name)
                    },
                    addPrompt = "Nom du sous-spot",
                    onSurfaceColor = colors.onBackground,
                    primaryColor = colors.primary
                )

                Spacer(modifier = Modifier.height(16.dp))
                PillSelector(
                    label = "Planche",
                    options = quiverBoards.map { it.id to "${it.model} (${boardFamilyLabel(it.family)})" },
                    selectedId = selectedQuiverId,
                    onSelect = { selectedQuiverId = it },
                    onSurfaceColor = colors.onBackground,
                    primaryColor = colors.primary,
                    emptyHint = "Aucune planche dans ton quiver. Ajoute-en une via l'icone Quiver."
                )

                Spacer(modifier = Modifier.height(16.dp))
                Text("Note", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground.copy(alpha = 0.7f))
                Spacer(modifier = Modifier.height(4.dp))
                StarRating(rating) { rating = it }

                Spacer(modifier = Modifier.height(16.dp))
                Text("Commentaire", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground.copy(alpha = 0.7f))
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    placeholder = { Text("C'etait le feu...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(90.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                )

                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { mediaPickerLauncher.launch(arrayOf("image/*", "video/*")) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (mediaUri == null) "Ajouter photo / video" else "1 media joint")
                }

                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = {
                        onSave(
                            startHour!!, endHour!!, selectedMicroSpotId!!, selectedQuiverId!!,
                            rating, comment.ifBlank { null }, mediaUri?.toString()
                        )
                        onDismiss()
                    },
                    enabled = canSave,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Enregistrer la session")
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}
