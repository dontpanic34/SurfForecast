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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * L'en-tête du spot, épinglé en haut : ‹ Montalivet ▾ ☆ › et Thème. Le nom ouvre la liste des spots, l'étoile ajoute ou retire des
 * favoris, les flèches (ou un balayage de l'en-tête) passent au favori d'à côté. Les points disent combien de favoris on parcourt.
 */
@Composable
fun SpotHeader(
    spotName: String,
    favorites: List<String?>,
    activeIndex: Int,
    updatedText: String,
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
        Row(
            modifier = Modifier.fillMaxWidth().offset { IntOffset(with(density) { nudge.value.dp.roundToPx() }, 0) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("‹", fontSize = 26.sp, color = colors.primary, modifier = Modifier.clip(CircleShape).clickable { step(-1) }.padding(horizontal = 10.dp))
            Row(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable(onClick = onOpenSpots).padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
            ) {
                Text(spotName, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = colors.onBackground, maxLines = 1)
                Text(" ▾", fontSize = 16.sp, color = colors.onSurface.copy(alpha = 0.6f))
            }
            Text(
                if (isFavorite) "★" else "☆", fontSize = 24.sp, color = if (isFavorite) Color(0xFFFFC107) else colors.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.clip(CircleShape).clickable(onClick = onToggleFavorite).padding(horizontal = 6.dp)
            )
            Text("›", fontSize = 26.sp, color = colors.primary, modifier = Modifier.clip(CircleShape).clickable { step(+1) }.padding(horizontal = 10.dp))
            Box(modifier = Modifier.padding(start = 4.dp)) { themeToggle() }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            filled.forEach { i ->
                Box(
                    modifier = Modifier.padding(horizontal = 3.dp).size(if (i == activeIndex) 7.dp else 5.dp).clip(CircleShape)
                        .background(if (i == activeIndex) colors.primary else colors.onSurface.copy(alpha = 0.25f))
                )
            }
            if (updatedText.isNotEmpty()) Text("   $updatedText", fontSize = 10.sp, color = colors.onSurface.copy(alpha = 0.5f), maxLines = 1)
        }
    }
}
