package com.surfcast.surfforecast

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
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
 * « Où surfer ? » (maquette `ou-surfer-lisible.html`) : la carte en premier avec la réponse du jour, les spots numérotés, les jours,
 * puis pourquoi ça vaut (ou pas) le déplacement, le classement et le conseil du coin.
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
                    is WhereState.Ready -> ReadyContent(state.result, homeName, homeLat, homeLon, onPickSpot)
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ReadyContent(result: WhereResult, homeName: String, homeLat: Double, homeLon: Double, onPickSpot: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    var dayIndex by remember(result) { mutableIntStateOf(result.days.indexOfFirst { it.worthMoving }.coerceAtLeast(0)) }
    val day = result.days.getOrNull(dayIndex)
    val best = day?.best
    val dayLabel = day?.let { "${frenchShortDayName(it.date)} ${it.date.day}" } ?: ""
    val here = day?.homeStars

    // 1. La carte, avec la réponse du jour écrite dessus.
    val banner = buildAnnotatedString {
        append("$dayLabel : ")
        if (day != null && day.worthMoving && best != null) {
            withStyle(SpanStyle(color = WhereGood, fontWeight = FontWeight.Bold)) { append("${best.spot.name}, ${starsText(best.stars)} ★") }
            append(" · ${best.spot.distanceKm} km, ${WhereToSurf.routeText(best.spot.distanceKm)}")
            here?.let { append(" (" + tr("ici") + " ${starsText(it)} ★)") }
        } else {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(tr("reste à") + " $homeName") }
            here?.let { append(" · " + tr("ici") + " ${starsText(it)} ★") }
            best?.let { append(", " + tr("ailleurs") + " ${starsText(it.stars)} ★ " + tr("au mieux")) }
        }
    }
    WhereToSurfMap(result, homeLat, homeLon, day, banner)

    // 2. Les noms des spots numérotés, juste sous la carte.
    val top = day?.top.orEmpty()
    if (top.isNotEmpty()) {
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            top.forEachIndexed { i, sd ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(17.dp).clip(CircleShape).background(if (i == 0) WhereGood else colors.primary),
                        contentAlignment = Alignment.Center
                    ) { Text("${i + 1}", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF061018)) }
                    Spacer(Modifier.width(4.dp))
                    Text("${sd.spot.name} ${starsText(sd.stars)} ★", fontSize = 12.sp, color = colors.onSurface)
                }
            }
        }
    }

    // 3. Les jours, en pastilles : « ici x ★ », ou « → y ★ » en vert quand ça vaut le déplacement.
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        result.days.forEachIndexed { i, d ->
            val on = i == dayIndex
            Column(
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (on) colors.primary else colors.surfaceVariant)
                    .clickable { dayIndex = i }.padding(horizontal = 11.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("${frenchShortDayName(d.date)} ${d.date.day}", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = if (on) colors.onPrimary else colors.onSurface)
                val away = d.best?.takeIf { d.worthMoving }
                if (away != null) Text("→ ${starsText(away.stars)} ★", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = if (on) colors.onPrimary else WhereGood)
                else Text(tr("ici") + " ${d.homeStars?.let { starsText(it) } ?: "–"} ★", fontSize = 11.5.sp, color = if (on) colors.onPrimary else colors.onSurfaceVariant)
            }
        }
    }
    Spacer(Modifier.height(10.dp))

    // 4. Pourquoi ça vaut (ou pas) le déplacement.
    if (day != null) {
        Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.surfaceVariant).padding(12.dp)) {
            if (day.worthMoving && best != null) {
                Text(tr("Ça vaut le déplacement"), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                Text(
                    "${best.spot.name} : ${WhereToSurf.reasonFor(best)}." + (best.bestHour?.let { " " + tr("Le mieux vers") + " ${it.rawTime.hour} h." } ?: ""),
                    fontSize = 12.5.sp, color = colors.onSurfaceVariant
                )
            } else {
                Text(tr("Pas la peine de bouger"), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                Text(
                    best?.let { tr("Le meilleur dans") + " ${result.radiusKm} km " + tr("fait") + " ${starsText(it.stars)} ★ (${it.spot.name}), " + tr("pas assez pour faire la route.") }
                        ?: (tr("Aucun spot mieux noté dans") + " ${result.radiusKm} km."),
                    fontSize = 12.5.sp, color = colors.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    // 5. Le classement du jour, puis chez toi pour comparer.
    top.forEachIndexed { i, sd -> RankRow(i + 1, sd, home = false) { onPickSpot(sd.spot.name) } }
    day?.homeDay?.let { RankRow(null, it, home = true, homeName = homeName) {} }
    Spacer(Modifier.height(8.dp))

    // 6. Le conseil du coin, à la meilleure heure du meilleur spot.
    if (day != null) LocalAdvice(result, day.date, best?.bestHour?.rawTime?.hour ?: 13)
    Text(
        tr("Au-delà de 3 ou 4 jours, les prévisions sont moins sûres. Temps de route estimé."),
        fontSize = 11.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp)
    )
}

/** Une ligne du classement : numéro, nom, distance, temps de route, meilleur moment, raison, étoiles. Chez toi : en pointillé, sans numéro. */
@Composable
private fun RankRow(rank: Int?, sd: SpotDay, home: Boolean, homeName: String = "", onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(12.dp))
            .then(
                if (home) Modifier.border(1.5.dp, colors.onSurfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                else Modifier.background(colors.surfaceVariant).clickable(onClick = onClick)
            )
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(26.dp).clip(CircleShape)
                .then(if (home) Modifier.border(2.dp, colors.onSurfaceVariant, CircleShape) else Modifier.background(if (rank == 1) WhereGood else colors.primary)),
            contentAlignment = Alignment.Center
        ) {
            Text(if (home) "⌂" else "$rank", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = if (home) colors.onSurfaceVariant else Color(0xFF061018))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(if (home) "$homeName (" + tr("chez toi") + ")" else sd.spot.name, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
            if (!home) Text(
                "${sd.spot.distanceKm} km · ${WhereToSurf.routeText(sd.spot.distanceKm)}" + (sd.bestHour?.let { " · " + tr("vers") + " ${it.rawTime.hour} h" } ?: ""),
                fontSize = 12.sp, color = colors.onSurfaceVariant
            )
            Text(WhereToSurf.reasonFor(sd), fontSize = 12.sp, color = colors.onSurface)
        }
        Column(horizontalAlignment = Alignment.End) {
            StarsRow(sd.stars)
            Text("${starsText(sd.stars)} / 5", fontSize = 11.sp, color = colors.onSurfaceVariant)
        }
    }
}
