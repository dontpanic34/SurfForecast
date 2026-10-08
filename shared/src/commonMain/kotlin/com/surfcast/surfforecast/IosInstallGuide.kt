package com.surfcast.surfforecast

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Carte guidée pour iPhone : Apple ne permet pas d'ajouter l'icône automatiquement. */
@Composable
fun IosInstallGuideDialog(onDismiss: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajouter Surf Log à l'écran d'accueil", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                GuideStep(1, "Touche le bouton Partager de Safari (le carré avec une flèche vers le haut), en bas de l'écran.") {
                    Canvas(modifier = Modifier.size(26.dp)) {
                        val w = size.width
                        val h = size.height
                        val stroke = Stroke(width = 2.dp.toPx())
                        // Boîte ouverte en haut
                        drawPath(
                            Path().apply {
                                moveTo(w * 0.28f, h * 0.42f); lineTo(w * 0.12f, h * 0.42f)
                                lineTo(w * 0.12f, h * 0.95f); lineTo(w * 0.88f, h * 0.95f)
                                lineTo(w * 0.88f, h * 0.42f); lineTo(w * 0.72f, h * 0.42f)
                            },
                            color = primary, style = stroke
                        )
                        // Flèche vers le haut
                        drawLine(primary, Offset(w * 0.5f, h * 0.62f), Offset(w * 0.5f, h * 0.05f), strokeWidth = 2.dp.toPx())
                        drawPath(
                            Path().apply {
                                moveTo(w * 0.32f, h * 0.22f); lineTo(w * 0.5f, h * 0.05f); lineTo(w * 0.68f, h * 0.22f)
                            },
                            color = primary, style = stroke
                        )
                    }
                }
                GuideStep(2, "Fais défiler le menu et choisis « Sur l'écran d'accueil ».") {
                    Text("➕", fontSize = 22.sp)
                }
                GuideStep(3, "Touche « Ajouter » en haut à droite. L'icône Surf Log arrive sur ton écran d'accueil.") {
                    Text("✅", fontSize = 22.sp)
                }
                Text(
                    "Astuce : à faire depuis Safari, avec le site ouvert (pas depuis une application qui l'ouvre en fenêtre intégrée).",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("J'ai compris") } }
    )
}

@Composable
private fun GuideStep(number: Int, text: String, icon: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(modifier = Modifier.size(30.dp), contentAlignment = Alignment.Center) { icon() }
        Text("$number. $text", fontSize = 12.5.sp, modifier = Modifier.weight(1f))
    }
}
