@file:Suppress("SpellCheckingInspection")
package com.surfcast.surfforecast

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Poignée : on glisse l'encart par là (le titre reste libre pour faire défiler la page).
            CardGrip(modifier = dragHandleModifier)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = onSurfaceColor.copy(alpha = 0.55f),
                maxLines = 1
            )
        }

        CardCollapseButton(isCollapsed = isCollapsed, onToggle = onToggleCollapse)
    }
}

/**
 * Poignée de déplacement bien visible : pastille arrondie avec une flèche vers le haut, quatre
 * petits carrés et une flèche vers le bas. On la touche et on glisse pour déplacer l'encart.
 */
@Composable
fun CardGrip(modifier: Modifier = Modifier) {
    val tint = MaterialTheme.colorScheme.onSurface
    Box(
        modifier = modifier
            .size(width = 32.dp, height = 36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(tint.copy(alpha = 0.10f)),
        contentAlignment = Alignment.Center
    ) {
        // Tout est dessiné autour de la même ligne centrale (cx) : flèche haut, trois traits, flèche bas.
        Canvas(modifier = Modifier.size(width = 16.dp, height = 26.dp)) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val color = tint.copy(alpha = 0.75f)
            drawPath(
                Path().apply {
                    moveTo(cx, 0f)
                    lineTo(w, h * 0.24f)
                    lineTo(0f, h * 0.24f)
                    close()
                },
                color
            )
            val stroke = 1.8.dp.toPx()
            listOf(0.38f, 0.50f, 0.62f).forEach { fy ->
                drawLine(
                    color = color,
                    start = Offset(0f, h * fy),
                    end = Offset(w, h * fy),
                    strokeWidth = stroke,
                    cap = androidx.compose.ui.graphics.StrokeCap.Butt
                )
            }
            drawPath(
                Path().apply {
                    moveTo(cx, h)
                    lineTo(w, h * 0.76f)
                    lineTo(0f, h * 0.76f)
                    close()
                },
                color
            )
        }
    }
}

/** Bouton réduire / agrandir : pastille colorée avec un chevron net (vers le bas = agrandir). */
@Composable
fun CardCollapseButton(isCollapsed: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(primary.copy(alpha = 0.16f))
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(16.dp)) {
            val w = size.width
            val h = size.height
            val path = Path().apply {
                if (isCollapsed) {
                    moveTo(w * 0.1f, h * 0.32f); lineTo(w * 0.5f, h * 0.72f); lineTo(w * 0.9f, h * 0.32f)
                } else {
                    moveTo(w * 0.1f, h * 0.68f); lineTo(w * 0.5f, h * 0.28f); lineTo(w * 0.9f, h * 0.68f)
                }
            }
            drawPath(
                path = path,
                color = primary,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
            )
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
