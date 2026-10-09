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
import androidx.compose.ui.text.drawText
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
    // Étoiles du jour (0,5 à 5) d'après le meilleur créneau ; null = trop gros pour le profil.
    dailyStars: List<Float?> = emptyList(),
    dailySunInfo: Map<LocalDate, DailySunInfo>,
    fixedMaxScale: Float,
    selectedIndex: Int,
    surferLevel: String,
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
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
            CardControlsRow(
                title = "Prévisions de la semaine",
                isCollapsed = isCollapsed,
                onToggleCollapse = onToggleCollapse,
                dragHandleModifier = dragHandleModifier,
            )

            if (!isCollapsed) {
            Spacer(modifier = Modifier.height(2.dp))
            val today = nowLocalDateTime().date
            val scrollState = rememberScrollState()
            val allDaylight = remember(availableDates, groupedByDate, dailySunInfo) {
                availableDates.flatMap { daylightHoursFor(it, groupedByDate, dailySunInfo) }
            }

            // 4 jours bien lisibles : les suivants s'atteignent en glissant sur le côté. L'échelle des
            // hauteurs reste fixe à gauche ; on voit « un bout » du jour suivant pour montrer qu'il y a une suite.
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val dayW = (maxWidth - WEEK_AXIS_W) / WEEK_VISIBLE_DAYS
                Box(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(start = WEEK_AXIS_W).horizontalScroll(scrollState)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
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
                                        .width(dayW)
                                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                        .background(if (isSelected) primaryColor.copy(alpha = 0.2f) else Color.Transparent)
                                        .clickable { onSelectDate(date) }
                                        .padding(vertical = 3.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = dayLabel,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) primaryColor else onSurfaceColor,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        if (dailyStars.size == availableDates.size) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                availableDates.forEachIndexed { index, date ->
                                    val isSelected = date == selectedDate
                                    Box(
                                        modifier = Modifier
                                            .width(dayW)
                                            .background(if (isSelected) primaryColor.copy(alpha = 0.2f) else Color.Transparent)
                                            .clickable { onSelectDate(date) }
                                            .padding(bottom = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val stars = dailyStars[index]
                                        if (stars == null) {
                                            Text("Trop gros", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = ScoreBand.TOO_BIG.color(), maxLines = 1)
                                        } else {
                                            StarsRow(value = stars, starSize = 13.dp)
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Row(modifier = Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            availableDates.forEach { date ->
                                val dailyData = groupedByDate[date] ?: emptyList()
                                val isSelected = date == selectedDate
                                Box(
                                    modifier = Modifier
                                        .width(dayW)
                                        .height(52.dp)
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
                            Row(modifier = Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                availableDates.forEach { date ->
                                    val dailyData = groupedByDate[date] ?: emptyList()
                                    val isSelected = date == selectedDate
                                    Box(
                                        modifier = Modifier
                                            .width(dayW)
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

                        Box(modifier = Modifier.width(dayW * availableDates.size).height(WEEK_CANVAS_H)) {
                            ContinuousWaveCanvas(
                                allHourlyData = allDaylight,
                                dailyPeriods = dailyPeriods,
                                dailyHeights = dailyHeights,
                                surferLevel = surferLevel,
                                dailyEnergies = availableDates.map { date ->
                                    daylightHoursFor(date, groupedByDate, dailySunInfo).maxOfOrNull { it.energyKj } ?: 0
                                },
                                daysCount = availableDates.size,
                                selectedIndex = selectedIndex,
                                axisWidth = 0.dp,
                                modifier = Modifier.fillMaxSize()
                            )

                            Row(modifier = Modifier.fillMaxSize()) {
                                availableDates.forEach { date ->
                                    val isSelected = date == selectedDate
                                    Box(
                                        modifier = Modifier
                                            .width(dayW)
                                            .fillMaxHeight()
                                            .background(if (isSelected) primaryColor.copy(alpha = 0.12f) else Color.Transparent)
                                            .clickable { onSelectDate(date) }
                                    )
                                }
                            }
                        }
                    }

                    // Échelle des hauteurs : fixe, ne défile pas avec les jours.
                    WeekAxisCanvas(
                        allHourlyData = allDaylight,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .width(WEEK_AXIS_W)
                            .height(WEEK_CANVAS_H)
                    )
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
                WeatherIcon(emoji, 16.dp)
                Text(
                    text = "${slot.temperature}°",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    lineHeight = 11.sp
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
internal fun isDarkSurfaceTheme(): Boolean =
    MaterialTheme.colorScheme.surface.luminance() < 0.5f

/** Largeur de la colonne de l'échelle des hauteurs (à gauche), commune à toutes les lignes de la vue semaine. */
internal val WEEK_AXIS_W = 30.dp

/** Jours visibles à la fois : 3,7 pour qu'un bout du jour suivant invite à glisser. */
internal const val WEEK_VISIBLE_DAYS = 3.7f

/** Hauteur de la courbe de la semaine (partagée avec son échelle fixe). */
internal val WEEK_CANVAS_H = 132.dp

/** Échelle des hauteurs de la semaine, fixe à gauche pendant que les jours défilent. */
@Composable
internal fun WeekAxisCanvas(allHourlyData: List<HourlyUiModel>, modifier: Modifier = Modifier) {
    if (allHourlyData.isEmpty()) return
    val textMeasurer = rememberTextMeasurer()
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val axisTextStyle = TextStyle(color = onSurfaceColor.copy(alpha = 0.45f), fontSize = 10.sp)
    Canvas(modifier = modifier) {
        val padTop = 16.dp.toPx()
        val usableH = size.height - padTop - 16.dp.toPx()
        val rawMax = (allHourlyData.maxOfOrNull { it.waveHeight } ?: 1.0).coerceAtLeast(0.3)
        val (niceMax, gridValues) = swellAxisScale(rawMax)
        gridValues.forEach { value ->
            val y = padTop + usableH * (1f - (value / niceMax).toFloat())
            val layout = textMeasurer.measure(formatAxisHeight(value), axisTextStyle)
            drawText(layout, topLeft = Offset(size.width - 5.dp.toPx() - layout.size.width, y + 3.dp.toPx() - layout.firstBaseline))
        }
    }
}

@Composable
fun ContinuousWaveCanvas(
    allHourlyData: List<HourlyUiModel>,
    dailyPeriods: List<Int>,
    dailyHeights: List<Double>,
    surferLevel: String,
    // Énergie de la houle au pic de chaque jour (kJ) : sous la courbe, colorée d'après le profil.
    dailyEnergies: List<Int> = emptyList(),
    daysCount: Int,
    selectedIndex: Int,
    // 0 quand l'échelle est dessinée à part (vue semaine à défilement) ; sinon réserve la colonne d'échelle.
    axisWidth: androidx.compose.ui.unit.Dp = WEEK_AXIS_W,
    modifier: Modifier = Modifier
) {
    if (allHourlyData.isEmpty()) return

    val textMeasurer = rememberTextMeasurer()
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val isDarkTheme = isDarkSurfaceTheme()
    val tideLineColor = if (isDarkTheme) AppColors.TideHigh else AppColors.TideHighDark
    val profile = remember(surferLevel) { SurfProfile.fromLevel(surferLevel) }

    val heightTextPaint = TextStyle(color = tideLineColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    // Période juste au-dessus de la courbe : neutre, lisible dans les deux thèmes.
    val periodTextPaint = TextStyle(color = onSurfaceColor.copy(alpha = 0.7f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
    val axisTextStyle = TextStyle(color = onSurfaceColor.copy(alpha = 0.45f), fontSize = 10.sp)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val axisW = axisWidth.toPx()
        val plotLeft = axisW
        val plotW = (w - plotLeft).coerceAtLeast(1f)
        val padTop = 16.dp.toPx()
        val padBottom = 16.dp.toPx()
        val usableH = h - padTop - padBottom
        val baseY = padTop + usableH

        // Échelle des hauteurs à gauche, qui s'adapte aux prévisions de la semaine (comme le déroulé de la journée).
        val rawMax = (allHourlyData.maxOfOrNull { it.waveHeight } ?: 1.0).coerceAtLeast(0.3)
        val (niceMax, gridValues) = swellAxisScale(rawMax)
        gridValues.forEach { value ->
            val y = padTop + usableH * (1f - (value / niceMax).toFloat())
            drawLine(
                color = onSurfaceColor.copy(alpha = 0.10f),
                start = Offset(plotLeft, y),
                end = Offset(w, y),
                strokeWidth = 0.8.dp.toPx()
            )
            if (axisW > 0f) {
                val layout = textMeasurer.measure(formatAxisHeight(value), axisTextStyle)
                drawText(layout, topLeft = Offset(plotLeft - 5.dp.toPx() - layout.size.width, y + 3.dp.toPx() - layout.firstBaseline))
            }
        }

        val dayWidth = plotW / daysCount.toFloat()
        for (i in 1 until daysCount) {
            val x = plotLeft + i * dayWidth
            drawLine(color = Color.Gray.copy(alpha = 0.3f), start = Offset(x, 0f), end = Offset(x, h), strokeWidth = 0.8.dp.toPx())
        }

        val stepX = plotW / (allHourlyData.size - 1).coerceAtLeast(1).toFloat()
        val points = allHourlyData.mapIndexed { index, item ->
            val ratio = (item.waveHeight / niceMax).coerceIn(0.0, 1.0).toFloat()
            Offset(plotLeft + index * stepX, padTop + usableH * (1f - ratio))
        }

        val fillPath = Path().apply {
            moveTo(plotLeft, baseY)
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
            val targetX = plotLeft + (i + 0.5f) * dayWidth
            val closestPoint = points.minByOrNull { abs(it.x - targetX) } ?: continue

            val pText = dailyPeriods.getOrNull(i)?.let { "${it}s" }
            if (i == selectedIndex) {
                // Jour sélectionné : un seul repère, sur la courbe, avec « hauteur période » au-dessus (rien dessous,
                // pour ne pas chevaucher la courbe).
                drawCircle(color = Color.White, radius = 4.4.dp.toPx(), center = closestPoint)
                drawCircle(color = tideLineColor, radius = 2.8.dp.toPx(), center = closestPoint)
                val hText = "${formatDecimal(dailyHeights.getOrNull(i) ?: 0.0, 1)}m".replace('.', ',')
                val hW = textMeasurer.measure(hText, heightTextPaint).size.width.toFloat()
                val pW = pText?.let { textMeasurer.measure(it, periodTextPaint).size.width.toFloat() } ?: 0f
                val gap = 3.dp.toPx()
                val totalW = hW + if (pText != null) gap + pW else 0f
                val baseline = (closestPoint.y - 9.dp.toPx()).coerceAtLeast(10.dp.toPx())
                drawTextAtBaseline(textMeasurer, hText, heightTextPaint, targetX - totalW / 2f, baseline)
                if (pText != null) drawTextAtBaseline(textMeasurer, pText, periodTextPaint, targetX - totalW / 2f + hW + gap, baseline)
            } else if (pText != null) {
                // Autres jours : la période seule, juste au-dessus de la courbe.
                val pW = textMeasurer.measure(pText, periodTextPaint).size.width.toFloat()
                drawTextAtBaseline(textMeasurer, pText, periodTextPaint, targetX - pW / 2f, (closestPoint.y - 5.dp.toPx()).coerceAtLeast(10.dp.toPx()))
            }
        }

        // Énergie de la houle au pic du jour, sous la courbe ; la couleur suit le profil de l'utilisateur :
        // gris (sous le minimum), vert (zone idéale), orange (au-dessus), violet (trop gros).
        for (i in 0 until daysCount) {
            val targetX = plotLeft + (i + 0.5f) * dayWidth
            val energy = dailyEnergies.getOrNull(i)?.takeIf { it > 0 } ?: continue
            val eText = "${energy}kJ"
            val eStyle = TextStyle(color = energyZoneColor(energyZone(energy.toDouble(), profile)), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            val eW = textMeasurer.measure(eText, eStyle).size.width.toFloat()
            drawTextAtBaseline(textMeasurer, eText, eStyle, targetX - eW / 2f, h - 3.dp.toPx())
        }
    }
}

/** Couleur d'une zone d'énergie : gris, vert, orange, violet (« trop gros », comme le score). */
internal fun energyZoneColor(zone: Int): Color = when (zone) {
    0 -> Color(0xFF78909C)
    1 -> Color(0xFF26A69A)
    2 -> Color(0xFFFB8C00)
    else -> Color(0xFF9D4EDD)
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
            fontSize = if (dirFr.length >= 3) 9.sp else 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (windMode == "text") arrowColor else onSurfaceColor,
            maxLines = 1,
            lineHeight = 11.sp
        )
        }

        if (showText) {
        Text(
            text = formattedSpeed,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (windMode == "text") arrowColor else onSurfaceColor.copy(alpha = 0.7f),
            maxLines = 1,
            lineHeight = 11.sp
        )
        }
    }
}
