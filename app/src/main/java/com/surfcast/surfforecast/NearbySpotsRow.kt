package com.surfcast.surfforecast

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.surfcast.surfforecast.ui.theme.AppColors

/**
 * Renvoie une fenetre de spots proches (par latitude) autour du spot donne,
 * radius spots de chaque cote, spot courant inclus au centre.
 * Ne fait aucun appel reseau : uniquement les donnees deja en base (SurfDatabase).
 */
private fun nearbySpotsWindow(currentSpotName: String, radius: Int = 4): List<SurfSpotItem> {
    val allSorted = SurfDatabase.getAllSpots().sortedBy { it.latitude }
    val idx = allSorted.indexOfFirst { it.name.equals(currentSpotName, ignoreCase = true) }
    if (idx == -1) return emptyList()
    val start = (idx - radius).coerceAtLeast(0)
    val end = (idx + radius).coerceAtMost(allSorted.size - 1)
    return allSorted.subList(start, end + 1)
}

/**
 * Rangee compacte de spots proches, pensee pour s'inserer dans le bandeau-titre
 * (a droite du nom du spot actif) : environ 3 spots visibles a la fois, le reste
 * accessible par scroll horizontal.
 */
@Composable
fun NearbySpotsRow(
    currentSpotName: String,
    onSelectSpot: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val spots = nearbySpotsWindow(currentSpotName)
    if (spots.size <= 1) return

    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        contentPadding = PaddingValues(horizontal = 2.dp)
    ) {
        items(spots) { spot ->
            val isActive = spot.name.equals(currentSpotName, ignoreCase = true)
            val hasCam = SurfWebcamHelper.hasCamera(spot.name)

            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { if (!isActive) onSelectSpot(spot.name) },
                color = if (isActive) AppColors.WindMid.copy(alpha = 0.18f) else colors.surfaceVariant,
                border = if (isActive) BorderStroke(1.dp, AppColors.WindMid) else null
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(if (hasCam) AppColors.WindMid else colors.onSurfaceVariant.copy(alpha = 0.3f))
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = spot.name,
                        fontSize = 11.5.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                        color = if (isActive) AppColors.WindMid else colors.onBackground,
                        maxLines = 1
                    )
                }
            }
        }
    }
}