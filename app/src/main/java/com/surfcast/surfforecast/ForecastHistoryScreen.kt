package com.surfcast.surfforecast

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.surfcast.surfforecast.ui.theme.AppColors
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Journal des previsions (Parametres > "Voir les logs d'actualisation") : chaque
 * chargement des 2 derniers jours, avec ce qui etait annonce heure par heure (vent et
 * modele d'ou il vient, houle). Permet de comparer apres coup avec la realite.
 */
@Composable
fun ForecastHistoryScreen(
    snapshots: List<ForecastHistoryStore.Snapshot>,
    currentSpotName: String?,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    var onlyCurrentSpot by remember { mutableStateOf(currentSpotName != null) }
    var expanded by remember { mutableStateOf<ForecastHistoryStore.Snapshot?>(null) }
    val loadFormatter = remember { DateTimeFormatter.ofPattern("EEE d MMM 'à' HH:mm", Locale.FRANCE) }
    val dayFormatter = remember { DateTimeFormatter.ofPattern("EEE d", Locale.FRANCE) }

    val shown = if (onlyCurrentSpot && currentSpotName != null) {
        snapshots.filter { it.spotName == currentSpotName }
    } else {
        snapshots
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .systemBarsPadding()
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Journal des prévisions",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onBackground,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onDismiss) { Text("Fermer") }
            }
            Text(
                text = "Ce que l'app annonçait à chaque actualisation (gardé 2 jours).",
                fontSize = 12.sp,
                color = colors.onSurfaceVariant
            )
            if (currentSpotName != null) {
                Row(modifier = Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterPill(currentSpotName, onlyCurrentSpot) { onlyCurrentSpot = true }
                    FilterPill("Tous les spots", !onlyCurrentSpot) { onlyCurrentSpot = false }
                }
            }

            if (shown.isEmpty()) {
                Text(
                    text = "Rien d'enregistré pour l'instant : le journal se remplit à chaque actualisation.",
                    fontSize = 12.sp,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(shown) { snap ->
                    val isOpen = expanded == snap
                    val missing = snap.hours.count { it.windSource == WIND_SOURCE_MISSING }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.surface)
                            .clickable { expanded = if (isOpen) null else snap }
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "${snap.spotName} · ${snap.loadedAt.format(loadFormatter)}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface
                        )
                        Text(text = snap.models, fontSize = 12.sp, color = colors.onSurfaceVariant)
                        if (missing > 0) {
                            Text(
                                text = "⚠ Vent manquant sur $missing h",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppColors.WindHigh
                            )
                        }
                        if (isOpen) {
                            Spacer(modifier = Modifier.height(6.dp))
                            snap.hours.groupBy { it.time.toLocalDate() }.forEach { (date, hours) ->
                                Text(
                                    text = date.format(dayFormatter).replaceFirstChar { it.uppercase() },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.primary,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                                )
                                hours.filter { it.time.hour in 6..21 }.forEach { h ->
                                    HistoryHourRow(h)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Text(
        text = label,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = if (selected) colors.onPrimary else colors.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) colors.primary else colors.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}

@Composable
private fun HistoryHourRow(h: ForecastHistoryStore.HourEntry) {
    val colors = MaterialTheme.colorScheme
    val isMissing = h.windSource == WIND_SOURCE_MISSING
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(
            text = "${h.time.hour.toString().padStart(2, '0')}h",
            fontSize = 12.sp,
            color = colors.onSurfaceVariant,
            modifier = Modifier.width(34.dp)
        )
        Text(
            text = if (isMissing) "vent : manquant" else "${h.windDir} ${h.windKmh} km/h",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isMissing) AppColors.WindHigh else colors.onSurface,
            modifier = Modifier.width(96.dp)
        )
        Text(
            text = h.windSource.ifEmpty { "?" },
            fontSize = 11.5.sp,
            color = colors.onSurfaceVariant,
            modifier = Modifier.width(72.dp)
        )
        Text(
            text = String.format(Locale.US, "%.1fm %ds %d°", h.waveHeight, h.wavePeriod.toInt(), h.waveDirection.toInt()),
            fontSize = 12.sp,
            color = colors.onSurface
        )
    }
}
