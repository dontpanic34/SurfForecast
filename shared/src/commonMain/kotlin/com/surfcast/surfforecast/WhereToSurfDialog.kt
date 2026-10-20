package com.surfcast.surfforecast

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * « Où surfer ? » : le meilleur coin de la semaine, puis la carte (voir [WhereToSurfMap]) avec le jour et le moment au choix.
 */
@Composable
fun WhereToSurfDialog(
    homeName: String,
    homeLat: Double,
    homeLon: Double,
    radiusKm: Int,
    state: WhereState,
    onRadius: (Int) -> Unit,
    onRetry: () -> Unit,
    onPickSpot: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxWidth(0.96f).clip(RoundedCornerShape(16.dp)), color = colors.surface) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Où surfer ?"), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = colors.onSurface)
                    Text("✕", fontSize = 16.sp, color = colors.onSurfaceVariant, modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onDismiss).padding(horizontal = 10.dp, vertical = 4.dp))
                }
                Text(
                    tr("Depuis") + " $homeName · " + tr("les spots du rayon, notés comme tes étoiles"),
                    fontSize = 11.5.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    WhereToSurf.RADII.forEach { r ->
                        val on = r == radiusKm
                        Box(
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(50))
                                .background(if (on) colors.primary else colors.surfaceVariant)
                                .clickable { onRadius(r) }.padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("$r km", fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, color = if (on) colors.onPrimary else colors.onSurface)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                when (state) {
                    is WhereState.Idle, is WhereState.Loading ->
                        Text(tr("Je compare les spots autour de toi…"), fontSize = 14.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(vertical = 24.dp).fillMaxWidth(), textAlign = TextAlign.Center)
                    is WhereState.Failed -> Column(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(tr(state.message), fontSize = 14.sp, color = colors.onSurface, textAlign = TextAlign.Center)
                        Text(
                            tr("Réessayer"), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.primary,
                            modifier = Modifier.padding(top = 10.dp).clip(RoundedCornerShape(50)).clickable(onClick = onRetry).padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                    is WhereState.Ready -> ReadyContent(state.result, homeLat, homeLon, onPickSpot)
                }
            }
        }
    }
}

@Composable
private fun ReadyContent(result: WhereResult, homeLat: Double, homeLon: Double, onPickSpot: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val bestDay = result.days.filter { it.worthMoving }.maxByOrNull { it.best?.score ?: 0 }
    val headline = bestDay?.best?.let { b ->
        "${frenchDayName(bestDay.date)} : ${b.spot.name} (${tr(WhereToSurf.directionWord(homeLat, homeLon, b.spot.lat, b.spot.lon))}, ${b.spot.distanceKm} km), ${starsText(b.stars)} ★"
    } ?: tr("Rien de mieux ailleurs dans ce rayon : reste ici.")
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.surfaceVariant).padding(12.dp)
    ) {
        Text(tr("Le meilleur coin de la semaine"), fontSize = 11.5.sp, color = colors.onSurfaceVariant)
        Text(headline, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
    }
    Spacer(Modifier.height(8.dp))
    WhereToSurfMap(result, homeLat, homeLon, onPickSpot)
    Text(
        tr("Au-delà de 3 ou 4 jours, les prévisions sont moins sûres."),
        fontSize = 11.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp)
    )
}

/** L'encart sous la vue semaine, seulement quand la semaine est mauvaise ici : « 4 jours sur 7 à moins de 2 étoiles ». */
@Composable
fun WhereToSurfEncart(lowDays: Int, totalDays: Int, onOpen: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(14.dp)).background(colors.surfaceVariant).clickable(onClick = onOpen).padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("$lowDays " + tr("jours sur") + " $totalDays " + tr("à moins de 2 étoiles ici"), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
            Text(tr("Il y a peut-être mieux ailleurs, près de toi."), fontSize = 11.5.sp, color = colors.onSurfaceVariant)
        }
        Text(tr("Où surfer ?") + " ›", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.primary)
    }
}
