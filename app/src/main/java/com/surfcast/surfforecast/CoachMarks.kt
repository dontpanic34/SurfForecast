package com.surfcast.surfforecast

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Une étape de la visite guidée : l'élément à éclairer (clé enregistrée par [coachTarget]) + son explication. */
data class CoachStep(val key: String, val title: String, val text: String)

/** Enregistre la position à l'écran de cet élément pour que la visite guidée puisse l'éclairer. */
fun Modifier.coachTarget(key: String, targets: MutableMap<String, Rect>): Modifier =
    onGloballyPositioned { targets[key] = it.boundsInRoot() }

/**
 * Visite guidée de la première utilisation : écran assombri, un trou lumineux sur l'élément
 * expliqué et une bulle avec « Suivant » / « Passer ». Les étapes dont l'élément n'est pas
 * visible à l'écran sont sautées. À placer en dernier dans un Box plein écran.
 */
@Composable
fun CoachMarkOverlay(
    steps: List<CoachStep>,
    targets: Map<String, Rect>,
    onFinish: () -> Unit
) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    var index by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInRoot() }
            // Absorbe les touches : on ne clique pas l'appli à travers la visite.
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        val heightPx = with(density) { maxHeight.toPx() }
        val visible = steps.filter { step ->
            targets[step.key]?.let { r -> r.height > 0f && r.bottom > origin.y && r.top < origin.y + heightPx } == true
        }
        if (visible.isEmpty()) return@BoxWithConstraints
        val step = visible[index.coerceIn(0, visible.lastIndex)]
        val rect = targets.getValue(step.key)
        val pad = with(density) { 6.dp.toPx() }
        val hole = Rect(
            rect.left - origin.x - pad, rect.top - origin.y - pad,
            rect.right - origin.x + pad, rect.bottom - origin.y + pad
        )

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                // Calque séparé : nécessaire pour que le trou (BlendMode.Clear) perce vraiment le voile.
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        ) {
            drawRect(Color.Black.copy(alpha = 0.78f))
            drawRoundRect(
                color = Color.Black,
                topLeft = hole.topLeft,
                size = Size(hole.width, hole.height),
                cornerRadius = CornerRadius(12.dp.toPx()),
                blendMode = BlendMode.Clear
            )
        }

        val bubbleBelow = hole.center.y < heightPx / 2f
        val bubbleModifier = if (bubbleBelow) {
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = with(density) { (hole.bottom + 12.dp.toPx()).toDp() })
        } else {
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = with(density) { (heightPx - hole.top + 12.dp.toPx()).toDp() })
        }

        Card(
            modifier = bubbleModifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(step.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(4.dp))
                Text(step.text, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f))
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${index.coerceIn(0, visible.lastIndex) + 1}/${visible.size}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = onFinish) { Text("Passer") }
                    val last = index >= visible.lastIndex
                    Button(onClick = { if (last) onFinish() else index++ }) { Text(if (last) "Terminé" else "Suivant") }
                }
            }
        }
    }
}

/** Encart d'astuces affiché en tête du journal la première fois qu'on l'ouvre. */
@Composable
fun JournalTipsCard(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("📓 Comment ça marche", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            val tip = TextStyle12
            Text("1. Choisis le jour (aujourd'hui, hier ou avant-hier) et tes heures dans l'eau.", style = tip)
            Text("2. Choisis le sous-spot et ta planche. Touche « Fiche du banc » pour noter quand il marche (marée, houle).", style = tip)
            Text("3. Note ta session de 1 à 5. Les conditions se remplissent toutes seules.", style = tip)
            Text("4. Avec tes sessions notées 4 ou 5, l'appli te signale les prévisions qui leur ressemblent.", style = tip)
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Compris") }
        }
    }
}

private val TextStyle12 @Composable get() = MaterialTheme.typography.bodySmall.copy(
    fontSize = 12.sp,
    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
)
