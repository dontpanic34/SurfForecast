package com.surfcast.surfforecast

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.material3.Icon
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * L'en-tête du spot, épinglé en haut : une loupe (choix du spot), les favoris en pastilles (l'actif est plein, un tap passe à un
 * autre, un balayage de l'en-tête aussi) et le thème. Le spot ouvert qui n'est pas un favori prend une pastille active.
 */
@Composable
fun SpotHeader(
    spotName: String,
    favorites: List<String?>,
    activeIndex: Int,
    updatedText: String = "",
    showHint: Boolean,
    onHintShown: () -> Unit,
    onSelectIndex: (Int) -> Unit,
    onOpenSpots: () -> Unit,
    onToggleFavorite: () -> Unit,
    themeToggle: @Composable () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val filled = favorites.mapIndexedNotNull { i, n -> if (n != null) i else null }
    val isFavorite = favorites.any { it == spotName }
    fun step(delta: Int) {
        if (filled.size < 2) return
        val pos = filled.indexOf(activeIndex).let { if (it < 0) 0 else it }
        onSelectIndex(filled[(pos + delta + filled.size) % filled.size])
    }
    // Mouvement d'accroche, une seule fois : l'en-tête se balance de côté pour dire « je me balaie ».
    val nudge = remember { Animatable(0f) }
    LaunchedEffect(showHint) {
        if (showHint) {
            nudge.animateTo(-14f, tween(260)); nudge.animateTo(14f, tween(420)); nudge.animateTo(0f, tween(260))
            onHintShown()
        }
    }
    var drag by remember { mutableStateOf(0f) }
    var menuOpen by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    Column(
        modifier = Modifier.fillMaxWidth().background(colors.background).padding(horizontal = 4.dp, vertical = 2.dp)
            .pointerInput(filled, activeIndex) {
                detectHorizontalDragGestures(
                    onDragStart = { drag = 0f },
                    onDragEnd = { if (drag < -60f) step(+1) else if (drag > 60f) step(-1); drag = 0f },
                    onDragCancel = { drag = 0f }
                ) { _, dx -> drag += dx }
            }
    ) {
        // Loupe à gauche, les favoris en pastilles au centre (l'actif est plein), le thème à droite. Plus de titre ni de chevrons :
        // le nom du spot actif est dans sa pastille ; la loupe ouvre le choix du spot.
        Row(
            modifier = Modifier.fillMaxWidth().offset { IntOffset(with(density) { nudge.value.dp.roundToPx() }, 0) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onOpenSpots), contentAlignment = Alignment.Center) {
                Icon(SurfIcons.Search, contentDescription = tr("Choisir un spot"), tint = colors.onBackground, modifier = Modifier.size(22.dp))
            }
            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                val names = filled.map { favorites[it]!! to (it == activeIndex && favorites[it] == spotName) }.toMutableList()
                // Le spot ouvert n'est pas un favori : on l'ajoute, actif, pour toujours voir où on est.
                if (names.none { it.second }) { if (names.size >= 3) names.removeAt(names.lastIndex); names += spotName to true }
                names.forEach { (name, on) ->
                    val idx = filled.firstOrNull { favorites[it] == name }
                    Box(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            name, fontSize = 13.sp, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.Medium, maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            color = if (on) colors.onPrimary else colors.onBackground.copy(alpha = 0.7f),
                            modifier = Modifier.clip(RoundedCornerShape(50))
                                .background(if (on) colors.primary else colors.surfaceVariant)
                                // Le spot actif ouvre un petit menu (changer de spot, voir où l'appli le place) ; un autre favori s'ouvre directement.
                                .clickable { if (on) menuOpen = true else if (idx == null) onOpenSpots() else onSelectIndex(idx) }
                                .padding(horizontal = 11.dp, vertical = 7.dp)
                        )
                        if (on) SpotMenu(spotName, menuOpen, { menuOpen = false }, onOpenSpots)
                    }
                }
            }
            Box(modifier = Modifier.width(44.dp), contentAlignment = Alignment.Center) { themeToggle() }
        }
    }
}

/**
 * Le petit menu du spot actif : changer de spot, ou voir sur Google Maps l'endroit où l'appli le place (pour vérifier sa position), avec
 * les coordonnées et l'orientation de la plage utilisées pour la note.
 */
@Composable
private fun SpotMenu(spotName: String, open: Boolean, onClose: () -> Unit, onOpenSpots: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    val spot = SurfDatabase.findSpotByName(spotName)
    androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = onClose) {
        androidx.compose.material3.DropdownMenuItem(text = { Text(tr("Changer de spot")) }, onClick = { onClose(); onOpenSpots() })
        if (spot != null) {
            androidx.compose.material3.DropdownMenuItem(
                text = { Text(tr("Voir sur Google Maps")) },
                onClick = {
                    onClose()
                    uri.openUri("https://www.google.com/maps/search/?api=1&query=${spot.latitude},${spot.longitude}")
                }
            )
            Text(
                "${formatDecimal(spot.latitude, 4).replace('.', ',')} ; ${formatDecimal(spot.longitude, 4).replace('.', ',')}" +
                    (spot.idealSwellDirection?.let { " · " + tr("plage tournée vers") + " $it°" } ?: ""),
                fontSize = 11.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }
    }
}
