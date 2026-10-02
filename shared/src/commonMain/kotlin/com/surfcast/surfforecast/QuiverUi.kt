@file:Suppress("SpellCheckingInspection")
package com.surfcast.surfforecast

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.surfcast.surfforecast.ui.theme.AppColors

// Familles de planches fixes : (id stocke en base, libelle affiche).
val BOARD_FAMILIES = listOf(
    "longboard" to "Longboard",
    "mousse" to "Mousse",
    "mid-length" to "Mid-length",
    "twin" to "Twin",
    "groveler" to "Groveler",
    "shortboard" to "Shortboard"
)

fun boardFamilyLabel(family: String): String = BOARD_FAMILIES.firstOrNull { it.first == family }?.second ?: family

@Composable
fun QuiverIcon(
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        // Silhouette de planche de surf : ovale allonge avec pointe en haut.
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.5f, h * 0.02f)
            cubicTo(w * 0.82f, h * 0.18f, w * 0.72f, h * 0.5f, w * 0.62f, h * 0.98f)
            cubicTo(w * 0.55f, h * 1.04f, w * 0.45f, h * 1.04f, w * 0.38f, h * 0.98f)
            cubicTo(w * 0.28f, h * 0.5f, w * 0.18f, h * 0.18f, w * 0.5f, h * 0.02f)
            close()
        }
        drawPath(path = path, color = color, style = Stroke(width = w * 0.07f))
        drawLine(
            color = color,
            start = Offset(w * 0.5f, h * 0.18f),
            end = Offset(w * 0.5f, h * 0.88f),
            strokeWidth = w * 0.04f
        )
    }
}

@Composable
private fun FamilySelector(
    selectedFamily: String?,
    onSelect: (String) -> Unit,
    onSurfaceColor: Color,
    primaryColor: Color
) {
    val pillShape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BOARD_FAMILIES.forEach { (id, label) ->
            val isSelected = id == selectedFamily
            Box(
                modifier = Modifier
                    .padding(end = 6.dp)
                    .clip(pillShape)
                    .background(if (isSelected) primaryColor.copy(alpha = 0.2f) else onSurfaceColor.copy(alpha = 0.08f))
                    .clickable { onSelect(id) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(text = label, fontSize = 12.sp, color = if (isSelected) primaryColor else onSurfaceColor, maxLines = 1)
            }
        }
    }
}

@Composable
fun QuiverScreen(
    quiverBoards: List<QuiverBoard>,
    onAddBoard: (model: String, family: String, lengthLitrage: String, finSetup: String) -> Unit,
    onDeleteBoard: (QuiverBoard) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme

    var showAddForm by remember { mutableStateOf(false) }
    var model by remember { mutableStateOf("") }
    var family by remember { mutableStateOf<String?>(null) }
    var dimensions by remember { mutableStateOf("") }
    var finSetup by remember { mutableStateOf("") }
    var boardPendingDelete by remember { mutableStateOf<QuiverBoard?>(null) }

    fun resetForm() {
        model = ""
        family = null
        dimensions = ""
        finSetup = ""
        showAddForm = false
    }

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
                    Text("Quiver", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
                    TextButton(onClick = onDismiss) { Text("Fermer") }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (quiverBoards.isEmpty()) {
                    Text(
                        text = "Aucune planche pour l'instant. Ajoute ta premiere planche ci-dessous.",
                        fontSize = 12.sp,
                        color = colors.onBackground.copy(alpha = 0.6f)
                    )
                } else {
                    quiverBoards.forEach { board ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            color = colors.surface,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = board.model,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.onSurface
                                    )
                                    Text(
                                        text = boardFamilyLabel(board.family) +
                                            (if (board.lengthLitrage.isNotBlank()) " · ${board.lengthLitrage}" else "") +
                                            (if (board.finSetup.isNotBlank()) " · ${board.finSetup}" else ""),
                                        fontSize = 11.sp,
                                        color = colors.onSurface.copy(alpha = 0.65f)
                                    )
                                }
                                Text(
                                    text = "Supprimer",
                                    fontSize = 11.sp,
                                    color = AppColors.WindHigh,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable { boardPendingDelete = board }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = colors.onBackground.copy(alpha = 0.1f))
                Spacer(modifier = Modifier.height(16.dp))

                if (!showAddForm) {
                    OutlinedButton(
                        onClick = { showAddForm = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("+ Ajouter une planche")
                    }
                } else {
                    Text("Modele", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground.copy(alpha = 0.7f))
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        placeholder = { Text("G Skate, Taco Grinder...", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Famille", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground.copy(alpha = 0.7f))
                    Spacer(modifier = Modifier.height(4.dp))
                    FamilySelector(
                        selectedFamily = family,
                        onSelect = { family = it },
                        onSurfaceColor = colors.onBackground,
                        primaryColor = colors.primary
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Cotes", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground.copy(alpha = 0.7f))
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = dimensions,
                        onValueChange = { dimensions = it },
                        placeholder = { Text("5'8 x 19 1/4 x 2 3/8 - 28L", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Ailerons", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground.copy(alpha = 0.7f))
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = finSetup,
                        onValueChange = { finSetup = it },
                        placeholder = { Text("Twin, Thruster, Quad...", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = { resetForm() }, modifier = Modifier.weight(1f)) {
                            Text("Annuler")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                onAddBoard(model.trim(), family ?: "shortboard", dimensions.trim(), finSetup.trim())
                                resetForm()
                            },
                            enabled = model.isNotBlank() && family != null,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Ajouter")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    boardPendingDelete?.let { board ->
        AlertDialog(
            onDismissRequest = { boardPendingDelete = null },
            title = { Text("Supprimer cette planche ?") },
            text = { Text("${board.model} sera definitivement supprimee du quiver.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteBoard(board)
                    boardPendingDelete = null
                }) {
                    Text("Supprimer", color = AppColors.WindHigh)
                }
            },
            dismissButton = {
                TextButton(onClick = { boardPendingDelete = null }) {
                    Text("Annuler")
                }
            }
        )
    }
}
