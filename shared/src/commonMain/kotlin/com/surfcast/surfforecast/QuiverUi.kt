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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
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

/** Bouton « Mon matériel » : l'icône de planche accompagnée de son libellé (jamais l'icône seule). */
@Composable
fun MyGearButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        QuiverIcon(color = colors.onBackground, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("Mon matériel", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground, maxLines = 1)
    }
}

@Composable
private fun GearLabel(text: String) {
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
}

/**
 * Mon matériel : qui je suis (âge, taille, poids) et mon quiver (planches avec volume, ratio L/kg et profil
 * que ce ratio évoque). Même contenu dans les Paramètres et dans le journal de bord.
 */
@Composable
fun GearContent(
    quiverBoards: List<QuiverBoard>,
    age: Int,
    heightCm: Int,
    weightKg: Int,
    onBodyChanged: (age: Int, heightCm: Int, weightKg: Int) -> Unit,
    onAddBoard: (model: String, family: String, lengthLitrage: String, finSetup: String, volumeL: Double?, volumeEstimated: Boolean) -> Unit,
    onDeleteBoard: (QuiverBoard) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    var showAddForm by remember { mutableStateOf(false) }
    var model by remember { mutableStateOf("") }
    var family by remember { mutableStateOf<String?>(null) }
    var knowsVolume by remember { mutableStateOf(false) }
    var feet by remember { mutableStateOf("6") }
    var inches by remember { mutableStateOf("0") }
    var width by remember { mutableStateOf("19") }
    var thickness by remember { mutableStateOf("2 5/16") }
    var volumeText by remember { mutableStateOf("") }
    var finSetup by remember { mutableStateOf("") }
    var boardPendingDelete by remember { mutableStateOf<QuiverBoard?>(null) }

    fun resetForm() {
        model = ""; family = null; knowsVolume = false
        feet = "6"; inches = "0"; width = "19"; thickness = "2 5/16"; volumeText = ""; finSetup = ""
        showAddForm = false
    }

    val numeric = KeyboardOptions(keyboardType = KeyboardType.Number)

    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Moi", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            var ageText by remember { mutableStateOf(if (age > 0) age.toString() else "") }
            var heightText by remember { mutableStateOf(if (heightCm > 0) heightCm.toString() else "") }
            var weightText by remember { mutableStateOf(if (weightKg > 0) weightKg.toString() else "") }
            fun push() = onBodyChanged(ageText.toIntOrNull() ?: 0, heightText.toIntOrNull() ?: 0, weightText.toIntOrNull() ?: 0)
            OutlinedTextField(
                value = ageText, onValueChange = { v -> ageText = v.filter { it.isDigit() }.take(3); push() },
                label = { Text("Âge") }, suffix = { Text("Ans") }, singleLine = true, keyboardOptions = numeric,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = heightText, onValueChange = { v -> heightText = v.filter { it.isDigit() }.take(3); push() },
                label = { Text("Taille") }, suffix = { Text("Cm") }, singleLine = true, keyboardOptions = numeric,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = weightText, onValueChange = { v -> weightText = v.filter { it.isDigit() }.take(3); push() },
                label = { Text("Poids") }, suffix = { Text("Kg") }, singleLine = true, keyboardOptions = numeric,
                modifier = Modifier.weight(1f)
            )
        }
        Text(
            "Facultatif. Ces données restent sur ton appareil et servent à calculer le ratio volume / poids de tes planches.",
            fontSize = 11.5.sp, color = colors.onBackground.copy(alpha = 0.6f), modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))
        Text("Mes planches", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
        Spacer(modifier = Modifier.height(6.dp))

        if (quiverBoards.isEmpty()) {
            Text(
                text = "Aucune planche pour l'instant. Ajoute ta première planche ci-dessous.",
                fontSize = 12.sp, color = colors.onBackground.copy(alpha = 0.6f)
            )
        }
        quiverBoards.forEach { board ->
            val ratio = volumeRatio(board.volumeL, weightKg)
            Surface(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                color = colors.surface,
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(board.model, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                        Text(
                            text = boardFamilyLabel(board.family) +
                                (if (board.lengthLitrage.isNotBlank()) " · ${board.lengthLitrage}" else "") +
                                (if (board.finSetup.isNotBlank()) " · ${board.finSetup}" else ""),
                            fontSize = 12.sp, color = colors.onSurface.copy(alpha = 0.65f)
                        )
                        boardVolumeSummary(board, weightKg)?.let { summary ->
                            Text(
                                text = summary + (ratio?.let { " · Ratio de surfeur ${levelLabel(levelForRatio(it)).lowercase()}" } ?: ""),
                                fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.primary
                            )
                        }
                    }
                    Text(
                        text = "Supprimer", fontSize = 12.sp, color = AppColors.WindHigh,
                        modifier = Modifier.clip(CircleShape).clickable { boardPendingDelete = board }.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (!showAddForm) {
            OutlinedButton(onClick = { showAddForm = true }, modifier = Modifier.fillMaxWidth()) {
                Text("+ Ajouter une planche")
            }
        } else {
            GearLabel("Modèle")
            OutlinedTextField(
                value = model, onValueChange = { model = it },
                placeholder = { Text("G Skate, Taco Grinder...", fontSize = 12.sp) },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            GearLabel("Famille")
            Spacer(modifier = Modifier.height(4.dp))
            FamilySelector(selectedFamily = family, onSelect = { family = it }, onSurfaceColor = colors.onBackground, primaryColor = colors.primary)

            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(false to "Je connais les cotes", true to "Je connais le volume").forEach { (vol, label) ->
                    val selected = knowsVolume == vol
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (selected) colors.primary else colors.onBackground.copy(alpha = 0.08f))
                            .clickable { knowsVolume = vol }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (selected) colors.onPrimary else colors.onBackground)
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            val ft = parseInches(feet)
            val inch = parseInches(inches)
            val w = parseInches(width)
            val t = parseInches(thickness)
            val typedVolume = parseInches(volumeText)
            val volumeL: Double? = if (knowsVolume) typedVolume?.takeIf { it > 0.0 }
            else if (ft != null && inch != null && w != null && t != null) estimateVolumeL(ft, inch, w, t) else null

            if (knowsVolume) {
                GearLabel("Volume écrit sur la planche")
                OutlinedTextField(
                    value = volumeText, onValueChange = { volumeText = it },
                    placeholder = { Text("Ex. 30,5", fontSize = 12.sp) }, suffix = { Text("Litres") },
                    singleLine = true, isError = typedVolume == null, modifier = Modifier.fillMaxWidth()
                )
            } else {
                GearLabel("Longueur")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = feet, onValueChange = { feet = it }, suffix = { Text("Pieds") },
                        singleLine = true, isError = ft == null, modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = inches, onValueChange = { inches = it }, suffix = { Text("Pouces") },
                        singleLine = true, isError = inch == null, modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        GearLabel("Largeur")
                        OutlinedTextField(
                            value = width, onValueChange = { width = it }, suffix = { Text("Po") },
                            singleLine = true, isError = w == null, modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        GearLabel("Épaisseur")
                        OutlinedTextField(
                            value = thickness, onValueChange = { thickness = it }, suffix = { Text("Po") },
                            singleLine = true, isError = t == null, modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Text(
                    "Décimal (2,15 · 2,38) ou fraction comme sur la planche (2 5/16 · 19 1/4).",
                    fontSize = 11.5.sp, color = colors.onBackground.copy(alpha = 0.6f), modifier = Modifier.padding(top = 4.dp)
                )
            }

            volumeL?.let { v ->
                val ratio = volumeRatio(v, weightKg)
                Text(
                    text = (if (knowsVolume) "" else "≈ ") + formatFr(v) + " L" +
                        (ratio?.let { " · " + formatFr(it, 2) + " L/kg · Ratio de surfeur ${levelLabel(levelForRatio(it)).lowercase()}" } ?: ""),
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.primary, modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            GearLabel("Ailerons")
            OutlinedTextField(
                value = finSetup, onValueChange = { finSetup = it },
                placeholder = { Text("Twin, Thruster, Quad...", fontSize = 12.sp) },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { resetForm() }, modifier = Modifier.weight(1f)) { Text("Annuler") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        val dims = if (knowsVolume) "" else formatDimensions(feet, inches, width, thickness)
                        onAddBoard(model.trim(), family ?: "shortboard", dims, finSetup.trim(), volumeL, !knowsVolume && volumeL != null)
                        resetForm()
                    },
                    enabled = model.isNotBlank() && family != null,
                    modifier = Modifier.weight(1f)
                ) { Text("Ajouter") }
            }
        }
    }

    boardPendingDelete?.let { board ->
        AlertDialog(
            onDismissRequest = { boardPendingDelete = null },
            title = { Text("Supprimer cette planche ?") },
            text = { Text("${board.model} sera définitivement supprimée de ton matériel.") },
            confirmButton = {
                TextButton(onClick = { onDeleteBoard(board); boardPendingDelete = null }) {
                    Text("Supprimer", color = AppColors.WindHigh)
                }
            },
            dismissButton = { TextButton(onClick = { boardPendingDelete = null }) { Text("Annuler") } }
        )
    }
}

/** Mon matériel en fenêtre : ouverte depuis le journal de bord (même contenu que dans les Paramètres). */
@Composable
fun QuiverScreen(
    quiverBoards: List<QuiverBoard>,
    age: Int,
    heightCm: Int,
    weightKg: Int,
    onBodyChanged: (age: Int, heightCm: Int, weightKg: Int) -> Unit,
    onAddBoard: (model: String, family: String, lengthLitrage: String, finSetup: String, volumeL: Double?, volumeEstimated: Boolean) -> Unit,
    onDeleteBoard: (QuiverBoard) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.88f).clip(RoundedCornerShape(16.dp)),
            color = colors.background
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        QuiverIcon(color = colors.onBackground, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Mon matériel", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
                    }
                    TextButton(onClick = onDismiss) { Text("Fermer") }
                }
                Spacer(modifier = Modifier.height(12.dp))
                GearContent(quiverBoards, age, heightCm, weightKg, onBodyChanged, onAddBoard, onDeleteBoard)
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
