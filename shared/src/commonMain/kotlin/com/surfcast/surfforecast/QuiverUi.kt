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
import kotlin.math.roundToInt
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
    "fish" to "Fish",
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

/** Pastilles à choix unique, sur plusieurs lignes si besoin : tout reste visible, rien ne se cache hors de l'écran. */
@Composable
internal fun PillRow(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val columns = when (options.size) {
        in 0..3 -> options.size.coerceAtLeast(1)
        4 -> 2
        else -> 3
    }
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.withIndex().toList().chunked(columns).forEach { rowItems ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rowItems.forEach { (i, label) ->
                    val on = i == selected
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(50))
                            .background(if (on) colors.primary else colors.onBackground.copy(alpha = 0.08f))
                            .clickable { onSelect(i) }
                            .padding(horizontal = 6.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            color = if (on) colors.onPrimaryContainer else colors.onBackground
                        )
                    }
                }
                repeat(columns - rowItems.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

/** Curseur de cote à la Alltroc : la valeur en grand (fraction, comme sur la planche), sa traduction décimale dessous. */
@Composable
private fun DimensionSlider(
    title: String,
    big: String,
    decimal: String,
    value: Int,
    range: IntRange,
    minLabel: String,
    maxLabel: String,
    onChange: (Int) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface.copy(alpha = 0.7f))
        Text(big, fontSize = 24.sp, color = colors.onSurface)
        Text(decimal, fontSize = 12.sp, color = colors.onSurface.copy(alpha = 0.65f))
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt().coerceIn(range.first, range.last)) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = range.last - range.first - 1
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(minLabel, fontSize = 10.5.sp, color = colors.onSurface.copy(alpha = 0.55f))
            Text(maxLabel, fontSize = 10.5.sp, color = colors.onSurface.copy(alpha = 0.55f))
        }
    }
}

/** Barre de la fourchette recommandée sur une échelle de 15 à 90 litres. */
@Composable
private fun VolumeGauge(range: ClosedFloatingPointRange<Double>) {
    val colors = MaterialTheme.colorScheme
    fun frac(v: Double) = ((v - 15.0) / 75.0).coerceIn(0.0, 1.0).toFloat()
    val start = frac(range.start)
    val end = frac(range.endInclusive).coerceAtLeast(start + 0.02f)
    Row(modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(colors.onSurface.copy(alpha = 0.10f))) {
        if (start > 0f) Spacer(modifier = Modifier.weight(start))
        Box(modifier = Modifier.weight(end - start).fillMaxHeight().clip(RoundedCornerShape(5.dp)).background(colors.primaryContainer))
        if (end < 1f) Spacer(modifier = Modifier.weight(1f - end))
    }
}

private fun volumeVerdict(volumeL: Double, range: ClosedFloatingPointRange<Double>): String = when {
    volumeL in range -> "Dans ta fourchette"
    volumeL > range.endInclusive -> "${formatFr(percentFromRecommended(volumeL, range.endInclusive), 0)} % au-dessus de ta fourchette"
    else -> "${formatFr(-percentFromRecommended(volumeL, range.start), 0)} % en dessous de ta fourchette"
}

/**
 * Mon matériel : mes planches (volume et verdict), un seul chiffre de volume en gros, puis mon poids et mon âge
 * (facultatif). Le détail (autres types de planche, calcul, pilotes pros de mon gabarit) est derrière « Aller plus loin ».
 * Le niveau vient du profil ; le volume ne définit jamais le niveau. Même contenu dans les Paramètres et dans le journal.
 */
@Composable
fun GearContent(
    quiverBoards: List<QuiverBoard>,
    surferLevel: String,
    body: BodyState,
    onBodyChanged: (BodyState) -> Unit,
    onAddBoard: (model: String, family: String, lengthLitrage: String, finSetup: String, volumeL: Double?, volumeEstimated: Boolean) -> Unit,
    onDeleteBoard: (QuiverBoard) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val numeric = KeyboardOptions(keyboardType = KeyboardType.Number)
    val myLevelIdx = volumeLevelIndex(surferLevel)
    // Comparaison : un autre niveau que le sien, au même poids et au même âge (null = mon niveau calculé par l'appli).
    val levelIdx = myLevelIdx
    // Le volume en gros : celui de la famille de la première planche du quiver (shortboard à défaut).
    val firstFamily = quiverBoards.firstOrNull()?.family
    val mainFamily = when (firstFamily) {
        null, "mousse" -> "shortboard"
        "twin", "groveler" -> "fish"
        else -> firstFamily
    }
    val mainRange = recommendedVolumeRange(body, levelIdx, mainFamily)
    var showInfo by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }

    var showAddForm by remember { mutableStateOf(false) }
    var model by remember { mutableStateOf("") }
    var family by remember { mutableStateOf("shortboard") }
    var lengthIn by remember { mutableStateOf(68) }
    var widthEighths by remember { mutableStateOf(163) }
    var thicknessSixteenths by remember { mutableStateOf(40) }
    var exactText by remember { mutableStateOf("") }
    var finSetup by remember { mutableStateOf("") }
    var boardPendingDelete by remember { mutableStateOf<QuiverBoard?>(null) }

    fun resetForm() {
        model = ""; family = "shortboard"; lengthIn = 68; widthEighths = 163; thicknessSixteenths = 40; exactText = ""; finSetup = ""
        showAddForm = false
    }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // --- Mon volume : un seul chiffre en gros ---
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Mon volume", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
            IconButton(onClick = { showInfo = !showInfo }, modifier = Modifier.size(24.dp)) {
                Icon(SurfIcons.Info, contentDescription = "Comment ce volume est calculé", tint = colors.primary, modifier = Modifier.size(15.dp))
            }
        }
        if (showInfo) {
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(colors.surface).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Comment on l'a calculé", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                Text("Volume = poids × ratio de ton niveau × petites corrections.", fontSize = 12.sp, color = colors.onSurface)
                Text(
                    "• Ratio de ton niveau (litres par kilo, shortboard) : débutant 0,66 · intermédiaire 0,48 · confirmé 0,41 · avancé 0,37 · expert 0,35.\n" +
                        "• Poids : les surfeurs légers ont besoin de plus de litres par kilo, jusqu'à +20 % à 55 kg, rien à partir de 85 kg.\n" +
                        "• Âge : aucune majoration jusqu'à 35 ans, puis un peu plus avec les années. Facultatif.\n" +
                        "• Forme : déduite de ton entraînement hors surf (profil).\n" +
                        "• Fish, twin, groveler : le shortboard + 1 à 2 L. Mid-length : 0,55 à 0,80 L par kilo. Longboard : 55 à 80 L et plus.",
                    fontSize = 12.sp, color = colors.onSurface
                )
                Text(
                    "Vérifié sur les planches de pilotes pros de JS Industries (0,34 à 0,42 L/kg selon le poids). Une recommandation, pas une vérité : " +
                        "seul ton shaper connaît le bon volume pour toi. Le volume ne définit jamais ton niveau.",
                    fontSize = 11.5.sp, color = colors.onSurface.copy(alpha = 0.65f)
                )
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(colors.surface).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (mainRange != null) {
                Text(
                    RECOMMENDATION_FAMILIES.firstOrNull { it.first == mainFamily }?.second.orEmpty() + " · ${body.weightKg} kg" +
                        (if (body.ageYears > 0) " · ${body.ageYears} ans" else ""),
                    fontSize = 12.sp, color = colors.onSurface.copy(alpha = 0.65f)
                )
                Text(volumeRangeLabel(mainRange, mainFamily), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                VolumeGauge(mainRange)
                if (body.ageYears <= 0) Text("Ajoute ton âge pour affiner.", fontSize = 12.sp, color = colors.primary)
            } else {
                Text("Ajoute ton poids", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                Text("Il suffit du poids pour voir ton volume conseillé.", fontSize = 12.sp, color = colors.onSurface.copy(alpha = 0.65f))
            }
            if (mainRange != null) Text(
                if (showMore) "Masquer le détail" else "Aller plus loin",
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.primary,
                modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(50)).clickable { showMore = !showMore }.padding(vertical = 4.dp)
            )
            if (showMore && mainRange != null) {
                if (body.weightKg > 0) {
                    RECOMMENDATION_FAMILIES.filter { it.first != mainFamily && it.first != "mousse" }.forEach { (fam, label) ->
                        recommendedVolumeRange(body, levelIdx, fam)?.let { r ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(label, fontSize = 13.sp, color = colors.onSurface.copy(alpha = 0.7f))
                                Text(volumeRangeLabel(r, fam), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                            }
                        }
                    }
                    val pros = nearestPros(mainFamily, body.weightKg)
                    if (pros.isNotEmpty()) {
                        Text("Des pros de ton gabarit (fiches JS Industries)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface, modifier = Modifier.padding(top = 8.dp))
                        pros.forEach { p ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${p.name} · ${p.weightKg} kg", fontSize = 12.5.sp, color = colors.onSurface.copy(alpha = 0.7f))
                                Text(formatFr(p.volumeL) + " L", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                            }
                        }
                    }
                } else {
                    Text("Ajoute ton poids pour voir le détail.", fontSize = 12.sp, color = colors.onSurface.copy(alpha = 0.65f))
                }
            }
        }

        run {
        // --- Mes mensurations : poids, âge (facultatif) ---
        Text("Mes mensurations", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            var weightText by remember { mutableStateOf(if (body.weightKg > 0) body.weightKg.toString() else "") }
            var ageText by remember { mutableStateOf(if (body.ageYears > 0) body.ageYears.toString() else "") }
            fun push() = onBodyChanged(body.copy(ageYears = ageText.toIntOrNull() ?: 0, weightKg = weightText.toIntOrNull() ?: 0))
            OutlinedTextField(
                value = weightText, onValueChange = { v -> weightText = v.filter { it.isDigit() }.take(3); push() },
                label = { Text("Poids") }, suffix = { Text("Kg") }, singleLine = true, keyboardOptions = numeric, modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = ageText, onValueChange = { v -> ageText = v.filter { it.isDigit() }.take(3); push() },
                label = { Text("Âge (facultatif)") }, suffix = { Text("Ans") }, singleLine = true, keyboardOptions = numeric, modifier = Modifier.weight(1f)
            )
        }
        Text(
            "Ces données restent sur ton appareil et servent à calculer ton volume conseillé.",
            fontSize = 11.5.sp, color = colors.onBackground.copy(alpha = 0.6f)
        )
        }
        run {
        // --- Mes planches ---
        Text("Mes planches", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
        if (quiverBoards.isEmpty()) {
            Text(
                text = "Aucune planche pour l'instant. Ajoute ta première planche ci-dessous.",
                fontSize = 12.sp, color = colors.onBackground.copy(alpha = 0.6f)
            )
        }
        quiverBoards.forEach { board ->
            val boardRange = recommendedVolumeRange(body, levelIdx, board.family)
            Surface(modifier = Modifier.fillMaxWidth(), color = colors.surface, shape = RoundedCornerShape(10.dp)) {
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
                        boardVolumeSummary(board, body.weightKg)?.let { summary ->
                            val verdict = if (board.volumeL != null && boardRange != null) " · " + volumeVerdict(board.volumeL, boardRange) else ""
                            Text(summary + verdict, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.primary)
                        }
                        BoardConditionChips(board.id)
                    }
                    Text(
                        text = "Supprimer", fontSize = 12.sp, color = AppColors.WindHigh,
                        modifier = Modifier.clip(CircleShape).clickable { boardPendingDelete = board }.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        if (!showAddForm) {
            OutlinedButton(onClick = { showAddForm = true }, modifier = Modifier.fillMaxWidth()) { Text("+ Ajouter une planche") }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(colors.surface).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Nom vide : l'utilisateur écrit le nom de sa planche.
                OutlinedTextField(
                    value = model, onValueChange = { model = it },
                    placeholder = { Text("Nom de la planche (ex. Xero Gravity)", fontSize = 13.sp) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                GearLabel("Type de planche")
                FamilySelector(selectedFamily = family, onSelect = { family = it }, onSurfaceColor = colors.onBackground, primaryColor = colors.primary)

                val widthIn = widthEighths / 8.0
                val thicknessIn = thicknessSixteenths / 16.0
                DimensionSlider(
                    "Longueur", formatLengthFeet(lengthIn) + "\"",
                    "= ${formatFr(lengthIn * 2.54, 1)} cm · $lengthIn pouces", lengthIn, 60..119, "5'0\"", "9'11\""
                ) { lengthIn = it }
                DimensionSlider(
                    "Largeur", formatInchesFraction(widthEighths, 8) + "\"",
                    "= ${formatFr(widthIn, 3)} po · ${formatFr(widthIn * 2.54, 1)} cm", widthEighths, 136..192, "17\"", "24\""
                ) { widthEighths = it }
                DimensionSlider(
                    "Épaisseur", formatInchesFraction(thicknessSixteenths, 16) + "\"",
                    "= ${formatFr(thicknessIn, 3)} po · ${formatFr(thicknessIn * 2.54, 2)} cm", thicknessSixteenths, 32..64, "2\"", "4\""
                ) { thicknessSixteenths = it }

                GearLabel("Je connais le volume précis de ma planche")
                val typedExact = parseInches(exactText)
                OutlinedTextField(
                    value = exactText, onValueChange = { exactText = it },
                    placeholder = { Text("Ex. 31,5", fontSize = 12.sp) }, suffix = { Text("Litres") },
                    singleLine = true, isError = typedExact == null, modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Saisis-le tel qu'il est inscrit sur la planche : il remplace le calcul. Sinon, laisse vide : l'appli le calcule à partir des cotes.",
                    fontSize = 11.5.sp, color = colors.onSurface.copy(alpha = 0.6f)
                )

                val exact = typedExact?.takeIf { it > 0.0 }
                val estimated = estimateVolumeL(0.0, lengthIn.toDouble(), widthIn, thicknessIn, fillFactorFor(family))
                val volumeL = exact ?: estimated
                val isEstimated = exact == null
                volumeL?.let { v ->
                    val boardRange = recommendedVolumeRange(body, levelIdx, family)
                    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text((if (isEstimated) "≈ " else "") + formatFr(v) + " L", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = colors.primary)
                        Text(
                            if (isEstimated) "Calculé à partir des cotes (± 5 %)" else "Volume précis saisi par toi",
                            fontSize = 11.5.sp, color = colors.onSurface.copy(alpha = 0.65f)
                        )
                        if (boardRange != null) {
                            Text(volumeVerdict(v, boardRange) + ".", fontSize = 12.sp, color = colors.onSurface.copy(alpha = 0.75f))
                        }
                    }
                }

                GearLabel("Ailerons")
                OutlinedTextField(
                    value = finSetup, onValueChange = { finSetup = it },
                    placeholder = { Text("Twin, Thruster, Quad...", fontSize = 12.sp) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )

                Row(modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { resetForm() }, modifier = Modifier.weight(1f)) { Text("Annuler") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val dims = formatSliderDimensions(lengthIn, widthEighths, thicknessSixteenths)
                            onAddBoard(model.trim(), family, dims, finSetup.trim(), volumeL, isEstimated && volumeL != null)
                            resetForm()
                        },
                        enabled = model.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) { Text("Ajouter") }
                }
            }
        }

            if (quiverBoards.size >= 2) BoardDayModeChooser()
        }
    }

    boardPendingDelete?.let { board ->
        AlertDialog(
            onDismissRequest = { boardPendingDelete = null },
            title = { Text("Supprimer cette planche ?") },
            text = { Text("${board.model} sera définitivement supprimée de ton matériel.") },
            confirmButton = {
                TextButton(onClick = { onDeleteBoard(board); boardPendingDelete = null }) { Text("Supprimer", color = AppColors.WindHigh) }
            },
            dismissButton = { TextButton(onClick = { boardPendingDelete = null }) { Text("Annuler") } }
        )
    }
}

/** Mon matériel en fenêtre : ouverte depuis le journal de bord (même contenu que dans les Paramètres). */
@Composable
fun QuiverScreen(
    quiverBoards: List<QuiverBoard>,
    surferLevel: String,
    body: BodyState,
    onBodyChanged: (BodyState) -> Unit,
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
                GearContent(quiverBoards, surferLevel, body, onBodyChanged, onAddBoard, onDeleteBoard)
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

