package com.surfcast.surfforecast

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.surfcast.surfforecast.ui.theme.AppColors
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.datetime.LocalDate
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus

internal fun daylightHoursFor(
    date: LocalDate,
    groupedByDate: Map<LocalDate, List<HourlyUiModel>>,
    dailySunInfo: Map<LocalDate, DailySunInfo>
): List<HourlyUiModel> {
    val dayHours = groupedByDate[date] ?: return emptyList()
    val sun = dailySunInfo[date]
    return if (sun != null) {
        dayHours.filter {
            val t = it.rawTime.time
            t >= sun.sunrise && t <= sun.sunset
        }
    } else {
        dayHours.filter { it.rawTime.hour in 7..21 }
    }
}


@Composable
fun WeeklyForecastCard(
    availableDates: List<LocalDate>,
    groupedByDate: Map<LocalDate, List<HourlyUiModel>>,
    selectedDate: LocalDate?,
    onSelectDate: (LocalDate) -> Unit,
    dailyTides: Map<LocalDate, DailyTideInfo>,
    dailyPeriods: List<Int>,
    dailyHeights: List<Double>,
    dailyFeelsLike: List<Int>,
    dailyWaterTemps: List<Int?>,
    dailySunInfo: Map<LocalDate, DailySunInfo>,
    fixedMaxScale: Float,
    selectedIndex: Int,
    windUnit: String,
    weeklyDensity: Int,
    weeklyWindMode: String,
    primaryColor: Color,
    surfaceColor: Color,
    onSurfaceColor: Color,
    isCollapsed: Boolean,
    onToggleCollapse: () -> Unit,
    dragHandleModifier: Modifier = Modifier,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = surfaceColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)) {
            CardControlsRow(
                title = "Prévisions de la semaine",
                isCollapsed = isCollapsed,
                onToggleCollapse = onToggleCollapse,
                dragHandleModifier = dragHandleModifier,
                modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp)
            )

            if (!isCollapsed) {
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val today = nowLocalDateTime().date
                availableDates.forEachIndexed { index, date ->
                    val dayNum = date.dayOfMonth.toString()
                    val dayLabel = when {
                        date == today || index == 0 -> "Auj. $dayNum"
                        date == today.plus(1, DateTimeUnit.DAY) || index == 1 -> "Dem. $dayNum"
                        else -> {
                            val dayName = frenchShortDayName(date)
                                .replace(".", "")
                                .replaceFirstChar { it.uppercase() }
                            "$dayName. $dayNum"
                        }
                    }
                    val isSelected = date == selectedDate

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(if (isSelected) primaryColor.copy(alpha = 0.2f) else Color.Transparent)
                            .clickable { onSelectDate(date) }
                            .padding(vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = dayLabel,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) primaryColor else onSurfaceColor,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                availableDates.forEach { date ->
                    val dailyData = groupedByDate[date] ?: emptyList()
                    val isSelected = date == selectedDate

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isSelected) primaryColor.copy(alpha = 0.2f) else Color.Transparent)
                            .clickable { onSelectDate(date) }
                            .padding(horizontal = 1.dp, vertical = 1.dp)
                    ) {
                        WeatherCanvasMain(dayData = dailyData, density = weeklyDensity, modifier = Modifier.fillMaxSize())
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            if (weeklyWindMode != "none") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                availableDates.forEach { date ->
                    val dailyData = groupedByDate[date] ?: emptyList()
                    val isSelected = date == selectedDate

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(if (isSelected) primaryColor.copy(alpha = 0.2f) else Color.Transparent)
                            .clickable { onSelectDate(date) }
                            .padding(vertical = 1.dp)
                    ) {
                        DayWindThreeSlots(
                            dailyData = dailyData,
                            windUnit = windUnit,
                            density = weeklyDensity,
                            windMode = weeklyWindMode
                        )
                    }
                }
            }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(132.dp)
            ) {
                ContinuousWaveCanvas(
                    allHourlyData = availableDates.flatMap { daylightHoursFor(it, groupedByDate, dailySunInfo) },
                    dailyPeriods = dailyPeriods,
                    dailyHeights = dailyHeights,
                    dailyFeelsLike = dailyFeelsLike,
                    dailyWaterTemps = dailyWaterTemps,
                    dailyEnergies = availableDates.map { date ->
                        daylightHoursFor(date, groupedByDate, dailySunInfo).maxOfOrNull { it.energyKj } ?: 0
                    },
                    maxScale = fixedMaxScale,
                    daysCount = availableDates.size,
                    selectedIndex = selectedIndex,
                    modifier = Modifier.fillMaxSize()
                )

                Row(modifier = Modifier.fillMaxSize()) {
                    availableDates.forEach { date ->
                        val isSelected = date == selectedDate
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(if (isSelected) primaryColor.copy(alpha = 0.12f) else Color.Transparent)
                                .clickable { onSelectDate(date) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(3.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                availableDates.forEach { date ->
                    val isSelected = date == selectedDate
                    val tideInfo = dailyTides[date]

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                            .background(if (isSelected) primaryColor.copy(alpha = 0.2f) else Color.Transparent)
                            .clickable { onSelectDate(date) }
                            .padding(vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        DailyTideCanvas(
                            tideInfo = tideInfo,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(26.dp)
                        )
                    }
                }
            }
            }
        }
    }
}

@Composable
fun WeatherCanvasMain(dayData: List<HourlyUiModel>, density: Int = 3, modifier: Modifier = Modifier) {
    if (dayData.isEmpty()) return

    val sampleHours = hoursForDensity(density)
    val slots = sampleHours.mapNotNull { targetHour ->
        dayData.minByOrNull { abs(it.rawTime.hour - targetHour) }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        slots.forEach { slot ->
            val emoji = SurfUnitsHelper.resolveRealWeatherEmoji(slot)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(text = emoji, fontSize = 13.sp)
                Text(
                    text = "${slot.temperature}°",
                    fontSize = 6.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    lineHeight = 8.sp
                )
            }
        }
    }
}

/**
 * True quand le theme (fonce/clair/systeme, apres arbitrage utilisateur dans
 * SurfViewModel/MainActivity) rend actuellement une surface sombre. On se base sur la
 * luminance reelle de MaterialTheme.colorScheme.surface plutot que sur
 * isSystemInDarkTheme(), qui ignorerait un theme force manuellement par l'utilisateur.
 */
@Composable
private fun isDarkSurfaceTheme(): Boolean =
    MaterialTheme.colorScheme.surface.luminance() < 0.5f

@Composable
fun ContinuousWaveCanvas(
    allHourlyData: List<HourlyUiModel>,
    dailyPeriods: List<Int>,
    dailyHeights: List<Double>,
    dailyFeelsLike: List<Int>,
    dailyWaterTemps: List<Int?>,
    // Énergie de la houle au pic de chaque jour (kJ) : petite valeur discrète sous la période.
    dailyEnergies: List<Int> = emptyList(),
    maxScale: Float,
    daysCount: Int,
    selectedIndex: Int,
    modifier: Modifier = Modifier
) {
    if (allHourlyData.isEmpty()) return

    val density = LocalDensity.current
    // Remplace les Paint Android (non multiplateformes) : mêmes tailles, graisses et positions.
    val textMeasurer = rememberTextMeasurer()
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    // Texte simple, sans fond ni ombre : on choisit la variante claire ou foncee de
    // chaque couleur maree selon le theme actif, pour rester lisible sur fond blanc
    // comme sur fond quasi noir sans alterer l'identite de la couleur elle-meme.
    val isDarkTheme = isDarkSurfaceTheme()
    val tideLineColor = if (isDarkTheme) AppColors.TideHigh else AppColors.TideHighDark

    val heightTextPaint = TextStyle(color = tideLineColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)

    // Etiquette neutre (pas de code couleur impose) : suit onSurface, lisible nativement
    // dans les deux themes sans besoin de chip.
    val periodTextPaint = TextStyle(color = onSurfaceColor.copy(alpha = 0.55f), fontSize = 11.sp, fontWeight = FontWeight.Bold)

    // Température de l'eau : bleu dédié, avec la vague pour icône.
    val waterColor = waterTempColor()
    val waterTextPaint = TextStyle(color = waterColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val padY = 8.dp.toPx()
        val usableH = h - (2 * padY)
        val baseY = padY + usableH

        var meter = 0f
        while (meter <= maxScale + 0.01f) {
            val y = padY + usableH * (1f - (meter / maxScale))
            val rem = meter % 1f
            val isInteger = rem !in 0.05f..0.95f
            drawLine(
                color = Color.Gray.copy(alpha = if (isInteger) 0.3f else 0.15f),
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = if (isInteger) 0.9f else 0.5f
            )
            meter += 0.5f
        }

        val dayWidth = w / daysCount.toFloat()
        if (daysCount > 1) {
            for (i in 1 until daysCount) {
                val x = i * dayWidth
                drawLine(
                    color = Color.Gray.copy(alpha = 0.3f),
                    start = Offset(x, 0f),
                    end = Offset(x, h),
                    strokeWidth = 0.8.dp.toPx()
                )
            }
        }

        val stepX = w / (allHourlyData.size - 1).coerceAtLeast(1).toFloat()
        val points = allHourlyData.mapIndexed { index, item ->
            val ratio = (item.waveHeight.toFloat() / maxScale).coerceIn(0f, 1f)
            val y = padY + usableH * (1f - ratio)
            Offset(index * stepX, y)
        }

        val fillPath = Path().apply {
            moveTo(0f, baseY)
            lineTo(points.first().x, points.first().y)
            for (i in 1 until points.size) {
                val prev = points[i - 1]
                val curr = points[i]
                val midX = (prev.x + curr.x) / 2f
                cubicTo(midX, prev.y, midX, curr.y, curr.x, curr.y)
            }
            lineTo(w, baseY)
            close()
        }

        drawPath(path = fillPath, color = AppColors.TideHigh.copy(alpha = 0.3f))

        val strokePath = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (i in 1 until points.size) {
                val prev = points[i - 1]
                val curr = points[i]
                val midX = (prev.x + curr.x) / 2f
                cubicTo(midX, prev.y, midX, curr.y, curr.x, curr.y)
            }
        }

        drawPath(path = strokePath, color = tideLineColor, style = Stroke(width = 2.5.dp.toPx()))

        for (i in 0 until daysCount) {
            val colLeft = i * dayWidth + 3.dp.toPx()
            // Température de l'eau (vraie prévision Open-Meteo) : vague + chiffre en bleu, rien si inconnue.
            val water = dailyWaterTemps.getOrNull(i)
            if (water != null) {
                val iconSize = 9.dp.toPx()
                drawWaveIcon(Offset(colLeft, padY + 1.5.dp.toPx()), iconSize, waterColor)
                drawTextAtBaseline(textMeasurer, "$water°", waterTextPaint, colLeft + iconSize + 2.dp.toPx(), padY + 9.3.dp.toPx())
            }
        }

        for (i in 0 until daysCount) {
            val targetX = (i + 0.5f) * dayWidth
            val closestPoint = points.minByOrNull { abs(it.x - targetX) } ?: continue
            val waveHeight = dailyHeights.getOrNull(i) ?: 0.0

            val hText = "${formatDecimal(waveHeight, 1)}m"
            val hTextW = textMeasurer.measure(hText, heightTextPaint).size.width.toFloat()
            val textY = (closestPoint.y - 5.dp.toPx()).coerceAtLeast(padY + 18.dp.toPx())

            if (i == selectedIndex) {
                drawCircle(color = Color.White, radius = 4.4.dp.toPx(), center = closestPoint)
                drawCircle(color = tideLineColor, radius = 2.8.dp.toPx(), center = closestPoint)
            }

            // Taille de la houle au-dessus de la courbe, période juste à côté (le tout centré).
            val period = dailyPeriods.getOrNull(i)
            val pText = period?.let { "${it}s" }
            val gap = 3.dp.toPx()
            val pTextW = pText?.let { textMeasurer.measure(it, periodTextPaint).size.width.toFloat() } ?: 0f
            val totalW = hTextW + if (pText != null) gap + pTextW else 0f
            val startX = targetX - totalW / 2f
            drawTextAtBaseline(textMeasurer, hText, heightTextPaint, startX, textY)
            if (pText != null) {
                drawTextAtBaseline(textMeasurer, pText, periodTextPaint, startX + hTextW + gap, textY)
            }
        }

        // Énergie de la houle au pic du jour, à l'ancienne place de la période (bas de la courbe).
        for (i in 0 until daysCount) {
            val targetX = (i + 0.5f) * dayWidth
            val energy = dailyEnergies.getOrNull(i)?.takeIf { it > 0 } ?: continue
            val eText = "${energy}kJ"
            val eStyle = energyTextPaint(energy)
            val eW = textMeasurer.measure(eText, eStyle).size.width.toFloat()
            drawTextAtBaseline(textMeasurer, eText, eStyle, targetX - eW / 2f, baseY - 3.dp.toPx())
        }
    }
}

// Teinte de l'énergie de houle : faible (neutre), moyenne (orange), forte (rouge).
private fun energyTextPaint(energyKj: Int): TextStyle {
    val color = when {
        energyKj >= 400 -> Color(0xFFE53935)
        energyKj >= 150 -> Color(0xFFFB8C00)
        else -> Color(0xFF78909C)
    }
    return TextStyle(color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
}

@Composable
fun DailyTideCanvas(
    tideInfo: DailyTideInfo?,
    modifier: Modifier = Modifier
) {
    if (tideInfo == null) return

    val density = LocalDensity.current
    // Remplace les Paint Android (non multiplateformes) : mêmes tailles, graisses et positions.
    val textMeasurer = rememberTextMeasurer()
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    // Texte simple, sans fond ni ombre : variante claire ou foncee selon le theme actif
    // (meme couleur pour le texte et les traits/icones), pour rester lisible sur fond
    // blanc comme sur fond quasi noir.
    val isDarkTheme = isDarkSurfaceTheme()
    val highIconColor = if (isDarkTheme) AppColors.TideHigh else AppColors.TideHighDark
    val lowIconColor = if (isDarkTheme) AppColors.TideLow else AppColors.TideLowDark

    val highTextPaint = TextStyle(color = highIconColor, fontSize = 7.2.sp, fontWeight = FontWeight.Bold)

    val lowTextPaint = TextStyle(color = lowIconColor, fontSize = 7.2.sp, fontWeight = FontWeight.Bold)

    // Correctif contraste : l'ancien gris-bleu clair (0xFFB0BEC5) etait pense pour fond
    // sombre et devenait quasi invisible en theme clair. On suit desormais onSurface
    // (fonce en clair, clair en sombre), comme les autres textes du bloc marees.
    val coefTextPaint = TextStyle(color = onSurfaceColor.copy(alpha = 0.62f), fontSize = 7.5.sp, fontWeight = FontWeight.Bold)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val highTimeStr = tideInfo.highTideTime ?: ""
        val lowTimeStr = tideInfo.lowTideTime ?: ""
        val coef = tideInfo.coefficient

        val iconW = 5.2.dp.toPx()
        val iconH = 4.8.dp.toPx()
        val iconGap = 3.2.dp.toPx()

        val highTextW = if (highTimeStr.isNotEmpty()) textMeasurer.measure(highTimeStr, highTextPaint).size.width.toFloat() else 0f
        val lowTextW = if (lowTimeStr.isNotEmpty()) textMeasurer.measure(lowTimeStr, lowTextPaint).size.width.toFloat() else 0f
        val maxTextW = maxOf(highTextW, lowTextW)

        val coefStr = if (coef != null) "$coef" else ""
        val coefTextW = if (coef != null) textMeasurer.measure(coefStr, coefTextPaint).size.width.toFloat() else 0f
        val dotRadius = 1.6.dp.toPx()
        val dotGap = 2.dp.toPx()
        val coefBlockW = if (coef != null) coefTextW + dotGap + dotRadius * 2f else 0f

        val gapTimesCoef = 3.dp.toPx()
        val textBlockWidth = if (maxTextW > 0f) iconW + iconGap + maxTextW else 0f
        val totalWidth = if (coef != null) (textBlockWidth + gapTimesCoef + coefBlockW) else textBlockWidth

        val startX = ((w - totalWidth) / 2f).coerceAtLeast(0.5.dp.toPx())
        val textStartX = startX + iconW + iconGap

        val row1Y = h * 0.28f
        val row2Y = h * 0.72f

        if (highTimeStr.isNotEmpty()) {
            val iconRight = startX + iconW
            val arrowX = startX + iconW * 0.5f

            val wavePath = Path().apply {
                moveTo(startX, row1Y + iconH * 0.35f)
                quadraticBezierTo(
                    startX + iconW * 0.5f,
                    row1Y + iconH * 0.12f,
                    iconRight,
                    row1Y + iconH * 0.35f
                )
            }
            drawPath(wavePath, color = highIconColor.copy(alpha = 0.65f), style = Stroke(width = 0.75.dp.toPx()))

            val arrowBottom = row1Y + iconH * 0.35f
            val arrowTop = row1Y - iconH * 0.45f
            drawLine(
                color = highIconColor,
                start = Offset(arrowX, arrowBottom),
                end = Offset(arrowX, arrowTop),
                strokeWidth = 0.9.dp.toPx()
            )
            val headSize = 1.5.dp.toPx()
            val headPath = Path().apply {
                moveTo(arrowX - headSize, arrowTop + headSize)
                lineTo(arrowX, arrowTop)
                lineTo(arrowX + headSize, arrowTop + headSize)
            }
            drawPath(headPath, color = highIconColor, style = Stroke(width = 0.9.dp.toPx()))

            val highBaseline = row1Y - textMeasurer.fontAscentDescentSum(highTextPaint) / 2f
            drawTextAtBaseline(textMeasurer, highTimeStr, highTextPaint, textStartX, highBaseline)
        }

        if (lowTimeStr.isNotEmpty()) {
            val iconRight = startX + iconW
            val arrowX = startX + iconW * 0.5f

            val wavePath2 = Path().apply {
                moveTo(startX, row2Y - iconH * 0.35f)
                quadraticBezierTo(
                    startX + iconW * 0.5f,
                    row2Y - iconH * 0.12f,
                    iconRight,
                    row2Y - iconH * 0.35f
                )
            }
            drawPath(wavePath2, color = lowIconColor.copy(alpha = 0.65f), style = Stroke(width = 0.75.dp.toPx()))

            val arrowTop = row2Y - iconH * 0.35f
            val arrowBottom = row2Y + iconH * 0.45f
            drawLine(
                color = lowIconColor,
                start = Offset(arrowX, arrowTop),
                end = Offset(arrowX, arrowBottom),
                strokeWidth = 0.9.dp.toPx()
            )
            val headSize = 1.5.dp.toPx()
            val headPath2 = Path().apply {
                moveTo(arrowX - headSize, arrowBottom - headSize)
                lineTo(arrowX, arrowBottom)
                lineTo(arrowX + headSize, arrowBottom - headSize)
            }
            drawPath(headPath2, color = lowIconColor, style = Stroke(width = 0.9.dp.toPx()))

            val lowBaseline = row2Y - textMeasurer.fontAscentDescentSum(lowTextPaint) / 2f
            drawTextAtBaseline(textMeasurer, lowTimeStr, lowTextPaint, textStartX, lowBaseline)
        }

        if (coef != null) {
            val coefStartX = startX + textBlockWidth + gapTimesCoef
            val centerY = h / 2f

            val dotColor = when {
                coef < 55 -> AppColors.WindLow
                coef <= 80 -> AppColors.WindMid
                else -> AppColors.WindHigh
            }

            val coefBaseline = centerY - textMeasurer.fontAscentDescentSum(coefTextPaint) / 2f
            drawTextAtBaseline(textMeasurer, coefStr, coefTextPaint, coefStartX, coefBaseline)

            val dotCenterX = coefStartX + coefTextW + dotGap + dotRadius
            val dotCenterY = centerY - 0.5.dp.toPx()
            drawCircle(color = dotColor, radius = dotRadius, center = Offset(dotCenterX, dotCenterY))
        }
    }
}

@Composable
fun DayWindThreeSlots(
    dailyData: List<HourlyUiModel>,
    windUnit: String,
    density: Int = 3,
    windMode: String = "both",
    modifier: Modifier = Modifier
) {
    val targetHours = hoursForDensity(density)
    val slots = targetHours.mapNotNull { hour -> dailyData.minByOrNull { abs(it.rawTime.hour - hour) } }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        slots.forEach { slot ->
            MiniWindSlot(slot = slot, windUnit = windUnit, windMode = windMode)
        }
    }
}

/** Point 2 : traduit la densite choisie (1/2/3 creneaux par jour) en heures cibles. */
private fun hoursForDensity(density: Int): List<Int> = when (density) {
    1 -> listOf(13)
    2 -> listOf(9, 18)
    else -> listOf(9, 13, 18)
}

@Composable
fun MiniWindSlot(slot: HourlyUiModel, windUnit: String, windMode: String = "both") {
    val dirFr = SurfUnitsHelper.formatCardinalFr(slot.windDirectionStr)
    val degrees = SurfUnitsHelper.cardinalToDegrees(dirFr)
    val rotationAngle = (degrees + 180f) % 360f
    val arrowColor = SurfUnitsHelper.getSurfWindColor(dirFr, slot.windSpeedKmh)
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    val formattedSpeed = SurfUnitsHelper.formatWindValue(slot.windSpeedKmh, windUnit)
    val showArrow = windMode == "arrow" || windMode == "both"
    val showText = windMode == "text" || windMode == "both"

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (showArrow) {
        Canvas(modifier = Modifier.size(12.dp)) {
            val w = size.width
            val h = size.height

            rotate(rotationAngle, pivot = Offset(w / 2f, h / 2f)) {
                val path = Path().apply {
                    moveTo(w * 0.5f, 0.5.dp.toPx())
                    lineTo(w * 0.95f, h * 0.48f)
                    lineTo(w * 0.68f, h * 0.48f)
                    lineTo(w * 0.68f, h * 0.98f)
                    lineTo(w * 0.32f, h * 0.98f)
                    lineTo(w * 0.32f, h * 0.48f)
                    lineTo(w * 0.05f, h * 0.48f)
                    close()
                }
                drawPath(path = path, color = arrowColor)
                drawPath(
                    path = path,
                    color = Color.Gray,
                    style = Stroke(width = 0.9.dp.toPx())
                )
            }
        }
        }

        if (showText) {
        Text(
            text = dirFr,
            fontSize = if (dirFr.length >= 3) 6.sp else 7.sp,
            fontWeight = FontWeight.Bold,
            color = if (windMode == "text") arrowColor else onSurfaceColor,
            maxLines = 1,
            lineHeight = 8.sp
        )
        }

        if (showText) {
        Text(
            text = formattedSpeed,
            fontSize = 6.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (windMode == "text") arrowColor else onSurfaceColor.copy(alpha = 0.7f),
            maxLines = 1,
            lineHeight = 8.sp
        )
        }
    }
}
