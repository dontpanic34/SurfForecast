package com.surfcast.surfforecast

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Ecran meteo detaille, en remplacement du radar (retire : juge peu utile par
 * l'utilisateur). Inspire de Google Meteo : vue "hero" plein ecran (temperature,
 * ressenti, min/max, vent) sur un degrade, puis prevision heure par heure et prevision
 * sur plusieurs jours dans des cartes sombres semi-transparentes -- volontairement un
 * habillage a part du reste de l'appli (qui suit le theme clair/sombre), pour un ecran
 * plus "esthetique" comme demande.
 *
 * Le bandeau "heure par heure" suit le jour selectionne dans le bandeau "plusieurs
 * jours" (tape sur un jour) : la section "hero" en haut, elle, reste toujours sur les
 * conditions actuelles (comme Google Meteo).
 */
@Composable
fun WeatherDetailScreen(
    spotName: String,
    groupedByDate: Map<LocalDate, List<HourlyUiModel>>,
    availableDates: List<LocalDate>,
    windUnit: String,
    onDismiss: () -> Unit
) {
    val today = nowLocalDateTime().date
    // Si les prévisions ne couvrent pas la date du jour (fuseau horaire du spot différent
    // de celui de l'appareil, ou données pas encore rafraîchies), on retombe sur la
    // première date disponible pour que la section "hero" et le bandeau horaire restent
    // cohérents entre eux (au lieu que l'un tombe sur aujourd'hui et l'autre sur une autre
    // date).
    val referenceDate = if (groupedByDate[today].isNullOrEmpty()) {
        availableDates.firstOrNull() ?: today
    } else {
        today
    }
    val todayHours = groupedByDate[referenceDate].orEmpty()
    val now = nowLocalDateTime().time
    val currentHourModel = if (referenceDate == today) {
        todayHours.minByOrNull { abs(it.rawTime.hour - now.hour) }
    } else {
        todayHours.firstOrNull()
    }

    var selectedDate by remember(referenceDate) { mutableStateOf(referenceDate) }

    val updateTime = remember { now.formatHHmm() }

    // Dialog plein ecran (comme QuiverScreen/SessionLogHistoryScreen) : sans ca, cet
    // ecran se contentait de s'inserer comme un item de plus dans la liste qui l'ouvre,
    // au lieu de recouvrir tout l'ecran par-dessus.
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF10163A), Color(0xFF2C3E8C), Color(0xFF5B6EC7))
                )
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 16.dp, top = 40.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(SurfIcons.Close, contentDescription = "Fermer", tint = Color.White)
                }
                Column(modifier = Modifier.padding(start = 2.dp)) {
                    Text(spotName, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = "Mise à jour : $updateTime",
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 11.sp
                    )
                }
            }

            if (currentHourModel == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Pas de données météo disponibles pour ce spot.",
                        color = Color.White.copy(alpha = 0.75f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(32.dp)
                    )
                }
            } else {
                // Si le jour selectionne a disparu des donnees (fenetre de prevision qui
                // a glisse suite a un rafraichissement en arriere-plan), on retombe sur
                // aujourd'hui plutot que de laisser le bandeau horaire vide indefiniment.
                val effectiveSelectedDate = if (selectedDate in availableDates) selectedDate else referenceDate
                val selectedDayHours = groupedByDate[effectiveSelectedDate].orEmpty()
                val isTodaySelected = effectiveSelectedDate == referenceDate
                val hourlyStripItems = if (isTodaySelected) {
                    (todayHours.filter { it.rawTime.hour >= currentHourModel.rawTime.hour } +
                        groupedByDate[referenceDate.plus(1, DateTimeUnit.DAY)].orEmpty()).take(24)
                } else {
                    selectedDayHours
                }
                val hourlyStripSubtitle = if (isTodaySelected) {
                    null
                } else {
                    "${frenchDayName(effectiveSelectedDate)} ${effectiveSelectedDate.day}".replaceFirstChar { it.uppercase() }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    WeatherHeroSection(
                        currentHourModel = currentHourModel,
                        todayHours = todayHours,
                        windUnit = windUnit
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    HourlyStripCard(
                        items = hourlyStripItems,
                        nowRawTime = if (isTodaySelected) currentHourModel.rawTime else null,
                        subtitle = hourlyStripSubtitle,
                        windUnit = windUnit
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    DailyStripCard(
                        availableDates = availableDates,
                        groupedByDate = groupedByDate,
                        selectedDate = effectiveSelectedDate,
                        onSelectDate = { date -> selectedDate = date }
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
    }
}

@Composable
private fun WeatherHeroSection(
    currentHourModel: HourlyUiModel,
    todayHours: List<HourlyUiModel>,
    windUnit: String
) {
    val maxTemp = todayHours.maxOfOrNull { it.temperature } ?: currentHourModel.temperature
    val minTemp = todayHours.minOfOrNull { it.temperature } ?: currentHourModel.temperature
    val dirFr = SurfUnitsHelper.formatCardinalFr(currentHourModel.windDirectionStr)
    val windSpeed = SurfUnitsHelper.formatWindValue(currentHourModel.windSpeedKmh, windUnit)
    val windUnitSymbol = SurfUnitsHelper.getWindUnitSymbol(windUnit)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = SurfUnitsHelper.resolveRealWeatherEmoji(currentHourModel), fontSize = 22.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = SurfUnitsHelper.weatherCodeLabel(currentHourModel.weatherCode),
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Text(
            text = "${currentHourModel.temperature}°",
            color = Color.White,
            fontSize = 78.sp,
            fontWeight = FontWeight.Light,
            lineHeight = 82.sp
        )

        Text(
            text = "Ressenti ${currentHourModel.feelsLike}°",
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 15.sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "Max. : ${maxTemp}° · Min. : ${minTemp}°",
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 13.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "💨", fontSize = 13.sp)
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "$dirFr · $windSpeed $windUnitSymbol",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun HourlyStripCard(
    items: List<HourlyUiModel>,
    nowRawTime: LocalDateTime?,
    subtitle: String?,
    windUnit: String
) {
    if (items.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.28f))
            .padding(vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "🕐", fontSize = 14.sp)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Prévisions par heure",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "· $subtitle",
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        val windUnitSymbol = SurfUnitsHelper.getWindUnitSymbol(windUnit)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            items(items) { hourly ->
                val isNow = nowRawTime != null && hourly.rawTime == nowRawTime
                val dirFr = SurfUnitsHelper.formatCardinalFr(hourly.windDirectionStr)
                val windColor = SurfUnitsHelper.getSurfWindColor(dirFr, hourly.windSpeedKmh)
                val windSpeed = SurfUnitsHelper.formatWindValue(hourly.windSpeedKmh, windUnit)

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${hourly.temperature}°",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = SurfUnitsHelper.resolveRealWeatherEmoji(hourly), fontSize = 20.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "$dirFr $windSpeed $windUnitSymbol",
                        color = windColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isNow) "Maintenant" else "${hourly.rawTime.hour.toString().padStart(2, '0')}h",
                        color = Color.White.copy(alpha = if (isNow) 1f else 0.7f),
                        fontSize = 11.sp,
                        fontWeight = if (isNow) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun DailyStripCard(
    availableDates: List<LocalDate>,
    groupedByDate: Map<LocalDate, List<HourlyUiModel>>,
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit
) {
    if (availableDates.isEmpty()) return
    val today = nowLocalDateTime().date

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.28f))
            .padding(vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "📅", fontSize = 14.sp)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Prévisions sur ${availableDates.size} jour${if (availableDates.size > 1) "s" else ""}",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(availableDates) { date ->
                val hours = groupedByDate[date].orEmpty()
                if (hours.isNotEmpty()) {
                    val maxT = hours.maxOf { it.temperature }
                    val minT = hours.minOf { it.temperature }
                    val representative = hours.minByOrNull { abs(it.rawTime.hour - 13) } ?: hours.first()
                    val rainPct = (hours.count { SurfUnitsHelper.isPrecipitationCode(it.weatherCode) }
                        .toFloat() / hours.size * 100).roundToInt()
                    val dayLabel = when (date) {
                        today -> "Auj."
                        today.plus(1, DateTimeUnit.DAY) -> "Dem."
                        else -> frenchShortDayName(date).replace(".", "").replaceFirstChar { it.uppercase() } + "."
                    }
                    val isSelected = date == selectedDate

                    Column(
                        modifier = Modifier
                            .width(58.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = if (isSelected) 0.2f else 0.08f))
                            .then(
                                if (isSelected) {
                                    Modifier.border(1.5.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(14.dp))
                                } else {
                                    Modifier
                                }
                            )
                            .clickable { onSelectDate(date) }
                            .padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "$maxT°", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(text = "$minT°", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = SurfUnitsHelper.resolveRealWeatherEmoji(representative), fontSize = 18.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "$rainPct %",
                            color = Color(0xFF80D8FF),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = dayLabel, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = "${date.day.toString().padStart(2, '0')}/${date.month.number.toString().padStart(2, '0')}",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 9.sp
                        )
                    }
                }
            }
        }
    }
}
