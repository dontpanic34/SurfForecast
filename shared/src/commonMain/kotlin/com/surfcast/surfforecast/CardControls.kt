@file:Suppress("SpellCheckingInspection")
package com.surfcast.surfforecast

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * En-tete reutilise par SurfCardComponent, WindCardComponent, WeatherCardComponent,
 * WeeklyForecastCard et l'encart horaire de MainScreen.kt (et son style est repris
 * "a la main" par DailyTimelineCard.kt qui a un en-tete plus complexe avec la maree).
 *
 * Affiche desormais une petite poignee (icone a 6 points) devant le titre pour
 * signaler visuellement que l'encart est deplacable. La zone d'appui long
 * (glisser-deposer, geste construit par l'appelant dans DynamicCardsSection de
 * MainScreen.kt et transmis via [dragHandleModifier]) couvre l'icone ET le titre.
 *
 * Le chevron reduire/developper est dessine a la main (Canvas) plutot que via les icones
 * Material ExpandLess/ExpandMore, qui ne sont pas disponibles dans la dependance icones de
 * ce projet (seul le sous-ensemble "core" est present, pas "extended").
 */
@Composable
fun CardControlsRow(
    title: String,
    isCollapsed: Boolean,
    onToggleCollapse: () -> Unit,
    dragHandleModifier: Modifier = Modifier,
    modifier: Modifier = Modifier
) {
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = dragHandleModifier,
            verticalAlignment = Alignment.CenterVertically
        ) {
            DragHandleIcon(color = onSurfaceColor.copy(alpha = 0.35f))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = onSurfaceColor.copy(alpha = 0.55f),
                maxLines = 1
            )
        }

        IconButton(
            onClick = onToggleCollapse,
            modifier = Modifier.size(22.dp)
        ) {
            Canvas(modifier = Modifier.size(12.dp)) {
                val w = size.width
                val h = size.height
                val path = Path().apply {
                    if (isCollapsed) {
                        // Pointe vers le bas : "developper"
                        moveTo(w * 0.2f, h * 0.35f)
                        lineTo(w * 0.8f, h * 0.35f)
                        lineTo(w * 0.5f, h * 0.75f)
                        close()
                    } else {
                        // Pointe vers le haut : "reduire"
                        moveTo(w * 0.2f, h * 0.65f)
                        lineTo(w * 0.8f, h * 0.65f)
                        lineTo(w * 0.5f, h * 0.25f)
                        close()
                    }
                }
                drawPath(path = path, color = onSurfaceColor.copy(alpha = 0.6f))
            }
        }
    }
}

/**
 * Petite poignee visuelle (6 points, 2 colonnes x 3 lignes) indiquant qu'un element
 * est deplacable par glisser-deposer. Visibilite par defaut (non "private") pour etre
 * reutilisable depuis DailyTimelineCard.kt, qui a son propre en-tete personnalise.
 */
@Composable
fun DragHandleIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(width = 10.dp, height = 14.dp)) {
        val w = size.width
        val h = size.height
        val dotRadius = w * 0.14f
        val colXs = listOf(w * 0.28f, w * 0.72f)
        val rowYs = listOf(h * 0.18f, h * 0.5f, h * 0.82f)
        colXs.forEach { x ->
            rowYs.forEach { y ->
                drawCircle(color = color, radius = dotRadius, center = Offset(x, y))
            }
        }
    }
}
