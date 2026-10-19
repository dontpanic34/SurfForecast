package com.surfcast.surfforecast

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.datetime.LocalDate

private fun meters(h: Double) = formatDecimal(h, 1).replace('.', ',') + " m"

private fun dayLabel(date: LocalDate) = "${frenchShortDayName(date)} ${date.day}"

/** Les conditions d'un spot en une ligne : « 1,8 m · 14 s · vent 8 km/h SE ». */
private fun conditionsLine(d: SpotDay) = "${meters(d.heightM)} · ${d.periodS.toInt()} s · ${tr("vent")} ${d.windKmh} km/h ${SurfUnitsHelper.formatCardinalFr(d.windDir)}"

/**
 * « Où surfer ? » : la semaine jour par jour. Chaque ligne dit combien d'étoiles ici, et si ça vaut le déplacement, le meilleur spot du
 * rayon avec ses étoiles. Toucher un jour montre les cinq meilleurs spots et le meilleur spot de chaque heure. Toucher un spot l'ouvre.
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
    var openDate by remember { mutableStateOf<LocalDate?>(null) }
    var showMap by remember { mutableStateOf(false) }
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
                    is WhereState.Ready -> ReadyContent(state.result, homeLat, homeLon, openDate, { openDate = if (openDate == it) null else it }, showMap, { showMap = it }, onPickSpot)
                }
            }
        }
    }
}

@Composable
private fun ReadyContent(
    result: WhereResult,
    homeLat: Double,
    homeLon: Double,
    openDate: LocalDate?,
    onToggle: (LocalDate) -> Unit,
    showMap: Boolean,
    onShowMap: (Boolean) -> Unit,
    onPickSpot: (String) -> Unit
) {
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
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        listOf(false to tr("Liste"), true to tr("Carte")).forEach { (isMap, label) ->
            val on = showMap == isMap
            Box(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (on) colors.primary else colors.surfaceVariant)
                    .clickable { onShowMap(isMap) }.padding(vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) { Text(label, fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, color = if (on) colors.onPrimary else colors.onSurface) }
        }
    }
    Spacer(Modifier.height(6.dp))
    if (showMap) {
        WhereToSurfMap(result, homeLat, homeLon, onPickSpot)
        return
    }
    result.days.forEach { day ->
        val open = openDate == day.date
        Column(modifier = Modifier.fillMaxWidth().clickable { onToggle(day.date) }.padding(vertical = 9.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(dayLabel(day.date), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.onSurface, modifier = Modifier.width(70.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(tr("ici"), fontSize = 11.sp, color = colors.onSurfaceVariant)
                    if (day.homeStars != null) StarsRow(day.homeStars) else Text("—", fontSize = 12.sp, color = colors.onSurfaceVariant)
                }
            }
            val best = day.best
            if (day.worthMoving && best != null) {
                Row(modifier = Modifier.fillMaxWidth().padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("→ ${best.spot.name} · ${best.spot.distanceKm} km", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = colors.primary, modifier = Modifier.weight(1f))
                    StarsRow(best.stars)
                }
            } else {
                Text(
                    if (day.homeStars != null && day.homeStars >= WhereToSurf.MOVE_MIN_STARS) tr("Reste ici, ça va") else tr("Rien de mieux ailleurs"),
                    fontSize = 12.5.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 3.dp)
                )
            }
            if (open) DayDetail(day, onPickSpot)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.onSurface.copy(alpha = 0.08f)))
    }
    Text(
        tr("Au-delà de 3 ou 4 jours, les prévisions sont moins sûres.") + " " + tr("Touche un jour pour le détail."),
        fontSize = 11.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun DayDetail(day: DayPick, onPickSpot: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        if (day.top.isEmpty()) {
            Text(tr("Pas assez de données pour ce jour."), fontSize = 12.sp, color = colors.onSurfaceVariant)
            return@Column
        }
        Text(tr("Les meilleurs spots du rayon"), fontSize = 11.5.sp, color = colors.onSurfaceVariant)
        day.top.forEach { d ->
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onPickSpot(d.spot.name) }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(d.spot.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                    Text("${d.spot.zone} · ${d.spot.distanceKm} km", fontSize = 11.sp, color = colors.onSurfaceVariant)
                    Text(conditionsLine(d), fontSize = 11.5.sp, color = colors.onSurfaceVariant)
                }
                StarsRow(d.stars)
            }
        }
        if (day.hours.isNotEmpty()) {
            Text(tr("Heure par heure : le meilleur spot du rayon"), fontSize = 11.5.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                day.hours.forEach { h ->
                    Column(
                        modifier = Modifier.width(60.dp).clip(RoundedCornerShape(8.dp)).background(scoreGradientColor(h.score))
                            .clickable { onPickSpot(h.spot.name) }.padding(vertical = 5.dp, horizontal = 3.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("${h.hour} h", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF061018))
                        Text(meters(h.heightM), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF061018))
                        Text(h.spot.name.take(9), fontSize = 9.5.sp, color = Color(0xFF061018), maxLines = 1)
                    }
                }
            }
        }
    }
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
