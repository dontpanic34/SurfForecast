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
import androidx.compose.foundation.layout.padding
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

    // Titre seul : réduire, masquer et réordonner les encarts se fait dans Paramètres › Affichage.
    Text(
        text = title,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = onSurfaceColor.copy(alpha = 0.55f),
        maxLines = 1,
        modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)
    )
}

/**
 * Poignée de déplacement : deux petits chevrons empilés (⌃ au-dessus de ⌄), sans fond. Discrète mais
 * explicite (ça monte, ça descend), avec une zone de toucher confortable autour.
 */
@Composable
fun CardGrip(modifier: Modifier = Modifier) {
    val tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
    Box(
        modifier = modifier.size(width = 30.dp, height = 36.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(width = 14.dp, height = 20.dp)) {
            val w = size.width
            val h = size.height
            val style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 2.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round
            )
            drawPath(
                Path().apply {
                    moveTo(w * 0.05f, h * 0.38f); lineTo(w * 0.5f, h * 0.05f); lineTo(w * 0.95f, h * 0.38f)
                },
                color = tint, style = style
            )
            drawPath(
                Path().apply {
                    moveTo(w * 0.05f, h * 0.62f); lineTo(w * 0.5f, h * 0.95f); lineTo(w * 0.95f, h * 0.62f)
                },
                color = tint, style = style
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
