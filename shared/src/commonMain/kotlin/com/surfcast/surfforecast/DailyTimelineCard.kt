@file:Suppress("SpellCheckingInspection")
package com.surfcast.surfforecast

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.surfcast.surfforecast.ui.theme.AppColors
import kotlinx.datetime.LocalDate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import kotlin.math.roundToLong
import kotlin.math.roundToInt

/**
 * Tache 3 / Point 3 : "Deroule de la journee". Fait partie du cardsOrder reordonnable
 * (glisser-depose par appui long sur la poignee/le titre + chevron reduire).
 *
 * Affiche toujours la journee selectionnee dans "Previsions de la semaine" ([selectedDate]).
 * Limite aux heures d'ensoleillement (sunrise -> sunset) via dailySunInfo. Les libelles
 * de premiere/derniere heure (bas du graphique) sont remplaces par l'heure exacte du
 * lever/coucher du soleil, avec une icone dediee.
 *
 * Point 3 : la courbe de houle est coloree segment par segment selon le score
 * "Meilleur Creneau" (rouge/orange/vert), calcule via SurfScoring.kt a partir de
 * [idealSwellDirection] et [surferLevel].
 *
 * En-tete : poignee + titre + marees (haute/basse + UN SEUL coefficient global, meme
 * code couleur que "Previsions de la semaine"), puis le controle de reduction.
 * De haut en bas ensuite : vent (vitesse + direction abregee) + meteo, courbe de houle
 * coloree par score (point rouge = heure actuelle), puis heures (lever/coucher aux
 * extremites).
 *
 * Scrub tactile : glisser (ou taper) n'importe ou dans le corps de la carte deplace
 * l'heure selectionnee ([selectedHour] / [onHourSelected]), qui pilote aussi les encarts
 * Houle/Vent/Meteo affiches plus bas dans "Previsions de la semaine" — meme etat partage
 * que ces trois cartes, donc la selection reste synchronisee dans les deux sens.
 */
/** Heure la plus proche d'une position horizontale : les points de la courbe vont de 0 à la largeur (n - 1 intervalles). */
internal fun hourIndexAt(x: Float, width: Float, count: Int): Int {
    if (count <= 1 || width <= 0f) return 0
    return (x / (width / (count - 1))).roundToInt().coerceIn(0, count - 1)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DailyTimelineCard(
    selectedDate: LocalDate,
    groupedByDate: Map<LocalDate, List<HourlyUiModel>>,
    dailySunInfo: Map<LocalDate, DailySunInfo>,
    dailyTides: Map<LocalDate, DailyTideInfo>,
    windUnit: String,
    idealSwellDirection: Int?,
    surferLevel: String,
    // Plus grosse houle de la semaine (échelle partagée avec la vue semaine).
    scaleRawMax: Double? = null,
    // Vue simple : une heure sur deux (la légende des couleurs reste toujours visible).
    compact: Boolean = false,
    selectedHour: HourlyUiModel?,
    onHourSelected: (HourlyUiModel) -> Unit,
    isCollapsed: Boolean,
    onToggleCollapse: () -> Unit,
    dragHandleModifier: Modifier = Modifier,
    modifier: Modifier = Modifier
) {
    if (groupedByDate.isEmpty()) return

    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    fun daylightHoursFor(date: LocalDate): List<HourlyUiModel> {
        val dayHours = groupedByDate[date] ?: return emptyList()
        val sun = dailySunInfo[date]
        return if (sun != null) {
            dayHours.filter {
                val t = it.rawTime.time
                t >= sun.sunrise && t <= sun.sunset
            }
        } else {
            // Repli si le lever/coucher n'est pas dispo pour ce jour (ex: J+6/7) : plage diurne large.
            dayHours.filter { it.rawTime.hour in 7..21 }
        }
    }

    val now = nowLocalDateTime()

    // Les 24 heures du jour (la nuit est assombrie sur la courbe) ; les créneaux de 3 h donnent météo et vent.
    val curveHours = (groupedByDate[selectedDate] ?: emptyList()).sortedBy { it.rawTime }
    val timelineItems: List<HourlyUiModel> = curveHours

    val nowIndex = curveHours.indexOfFirst {
        it.rawTime.date == now.date && it.rawTime.hour == now.hour
    }

    val selectedIndex = selectedHour?.let { sel -> curveHours.indexOfFirst { it.rawTime == sel.rawTime } } ?: -1

    val tideInfo = dailyTides[selectedDate]
    val sun = dailySunInfo[selectedDate]

    // Température de la mer (vraie prévision) : celle de l'heure touchée, sinon la moyenne du jour.
    val seaTemp = (
        selectedHour?.takeIf { it.rawTime.date == selectedDate }?.seaTemperature
            ?: curveHours.mapNotNull { it.seaTemperature }.takeIf { it.isNotEmpty() }?.average()
        )?.let { kotlin.math.round(it).toInt() }

    // Point 3 : score par heure (0-100), sert a colorer la courbe segment par segment.
    // Score négatif = trop gros pour le niveau (orange).
    // Notation avec la mémoire du vent : la mer ne devient pas « glacée » une heure après 30 km/h de vent de mer.
    val scoring = hoursWithWindMemory(selectedDate, groupedByDate).associateBy { it.rawTime }
    val ratings = curveHours.map { shown ->
        val hourly = scoring[shown.rawTime] ?: shown
        calculateSlotRating(hourly, idealSwellDirection, surferLevel)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = surfaceColor),
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
            // Pas de titre : l'heure touchée, son libellé et sa phrase. Si la phrase ne tient pas à côté du libellé, elle passe
            // entière à la ligne (jamais coupée), entre le libellé et la jauge à 5 segments.
            if (selectedIndex in ratings.indices) {
                val shownRating = ratings[selectedIndex]
                val shownBand = conditionBand(shownRating)
                val level = gaugeLevel(shownRating)
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp),
                        itemVerticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            curveHours[selectedIndex].rawTime.hour.toString() + " h", fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold, color = onSurfaceColor, maxLines = 1
                        )
                        Text(
                            bandLabelWithTrend(shownRating), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
                            color = shownBand.textColor(), maxLines = 1
                        )
                        if (shownRating.why.isNotBlank()) {
                            Text(
                                shownRating.why, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                color = onSurfaceColor.copy(alpha = 0.6f), maxLines = 1, modifier = Modifier.padding(bottom = 1.dp)
                            )
                        }
                    }
                    Row(modifier = Modifier.padding(top = 4.dp).width(110.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        for (i in 1..5) {
                            Box(
                                Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp))
                                    .background(if (i <= level) shownBand.color() else onSurfaceColor.copy(alpha = 0.15f))
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = "Déroulé de la journée",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = onSurfaceColor.copy(alpha = 0.55f),
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                )
            }

            if (!isCollapsed) {
            Spacer(modifier = Modifier.height(4.dp))

            if (timelineItems.isEmpty()) {
                Text(
                    text = "Pas de données diurnes disponibles pour cette sélection.",
                    fontSize = 12.sp,
                    color = onSurfaceColor.copy(alpha = 0.5f),
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                val primaryColor = MaterialTheme.colorScheme.primary

                // --- Scrub tactile : glisser ou taper deplace l'heure selectionnee,
                // partagee avec les encarts Houle/Vent/Meteo (synchro bidirectionnelle). ---
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(timelineItems) {
                            detectHorizontalDragGestures { change, _ ->
                                val x = change.position.x.coerceIn(0f, size.width.toFloat())
                                val index = hourIndexAt(x, size.width.toFloat(), timelineItems.size)
                                onHourSelected(timelineItems[index])
                            }
                        }
                        .pointerInput(timelineItems) {
                            detectTapGestures { offset ->
                                val index = hourIndexAt(offset.x, size.width.toFloat(), timelineItems.size)
                                onHourSelected(timelineItems[index])
                            }
                        }
                ) {
                // --- Haut : météo et vent par créneau de 3 h, posés à l'aplomb de leur heure sur la courbe ---
                BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(62.dp)) {
                    val slotW = maxWidth * 3f / (timelineItems.size - 1).coerceAtLeast(1)
                    val firstHour = timelineItems.firstOrNull()?.rawTime?.hour ?: 0
                    for (h in 6..21 step 3) {
                        val idx = h - firstHour
                        val hourly = timelineItems.getOrNull(idx) ?: continue
                        val centerX = maxWidth * idx / (timelineItems.size - 1).coerceAtLeast(1)
                        val left = (centerX - slotW / 2).coerceIn(0.dp, maxWidth - slotW)
                        val selectedHourOfDay = timelineItems.getOrNull(selectedIndex)?.rawTime?.hour ?: -1
                        val isSelected = selectedHourOfDay in (h - 1)..(h + 1)
                        Column(
                            modifier = Modifier.offset(x = left).width(slotW)
                                .background(
                                    color = if (isSelected) primaryColor.copy(alpha = 0.14f) else Color.Transparent,
                                    shape = RoundedCornerShape(4.dp)
                                ),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val dirFr = SurfUnitsHelper.formatCardinalFr(hourly.windDirectionStr)
                            val degrees = SurfUnitsHelper.cardinalToDegrees(dirFr)
                            val rotationAngle = (degrees + 180f) % 360f
                            // Le vent est une donnée, pas une note : gris et blanc, la couleur reste aux libellés.
                            val arrowColor = onSurfaceColor.copy(alpha = 0.6f)
                            WeatherIcon(SurfUnitsHelper.resolveRealWeatherEmoji(hourly), 16.dp)
                            Canvas(modifier = Modifier.size(12.dp)) {
                                val w = size.width
                                val hh = size.height
                                rotate(rotationAngle, pivot = Offset(w / 2f, hh / 2f)) {
                                    val path = Path().apply {
                                        moveTo(w * 0.5f, 0f)
                                        lineTo(w * 0.9f, hh * 0.55f)
                                        lineTo(w * 0.5f, hh * 0.38f)
                                        lineTo(w * 0.1f, hh * 0.55f)
                                        close()
                                    }
                                    drawPath(path = path, color = arrowColor)
                                }
                            }
                            Text(text = dirFr, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = onSurfaceColor, maxLines = 1)
                            Text(
                                text = SurfUnitsHelper.formatWindValue(hourly.windSpeedKmh, windUnit),
                                fontSize = 10.sp, fontWeight = FontWeight.Bold, color = onSurfaceColor.copy(alpha = 0.75f), maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // --- Milieu : courbe de houle coloree par score, avec repere sur l'heure actuelle ---
                DailyTimelineSwellCanvas(
                    hours = curveHours,
                    ratings = ratings,
                    nowIndex = nowIndex,
                    selectedIndex = selectedIndex,
                    onSurfaceColor = onSurfaceColor,
                    scaleRawMax = scaleRawMax,
                    sunriseHour = sun?.let { it.sunrise.hour + it.sunrise.minute / 60f },
                    sunsetHour = sun?.let { it.sunset.hour + it.sunset.minute / 60f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(WEEK_CANVAS_H)
                )

                Spacer(modifier = Modifier.height(2.dp))

                // --- Bas : une graduation toutes les 3 h, posée à l'aplomb de son heure, sur fond léger ---
                BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(20.dp)) {
                    val slotW = maxWidth * 3f / (timelineItems.size - 1).coerceAtLeast(1)
                    val firstHour = timelineItems.firstOrNull()?.rawTime?.hour ?: 0
                    for (h in 6..21 step 3) {
                        val idx = h - firstHour
                        if (idx !in timelineItems.indices) continue
                        val centerX = maxWidth * idx / (timelineItems.size - 1).coerceAtLeast(1)
                        val left = (centerX - slotW / 2).coerceIn(0.dp, maxWidth - slotW)
                        Box(modifier = Modifier.offset(x = left).width(slotW), contentAlignment = Alignment.Center) {
                            Text(
                                text = "$h h", fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                                color = onSurfaceColor.copy(alpha = 0.75f), maxLines = 1,
                                modifier = Modifier.background(onSurfaceColor.copy(alpha = 0.10f), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                // Lever et coucher du soleil, à part : les colonnes ci-dessus restent de vraies heures.
                if (sun != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SunEventIcon(isSunrise = true, color = onSurfaceColor.copy(alpha = 0.6f), modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(sun.sunrise.formatHHmm(), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = onSurfaceColor.copy(alpha = 0.6f), maxLines = 1)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(sun.sunset.formatHHmm(), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = onSurfaceColor.copy(alpha = 0.6f), maxLines = 1)
                            Spacer(modifier = Modifier.width(3.dp))
                            SunEventIcon(isSunrise = false, color = onSurfaceColor.copy(alpha = 0.6f), modifier = Modifier.size(12.dp))
                        }
                    }
                }
                }
            }
            }
        }
    }
}

/**
 * Icone lever/coucher de soleil dessinee a la main : horizon + demi-soleil + fleche
 * (vers le haut = lever, vers le bas = coucher).
 */
@Composable
private fun SunEventIcon(isSunrise: Boolean, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val horizonY = h * 0.62f

        drawLine(color = color, start = Offset(0f, horizonY), end = Offset(w, horizonY), strokeWidth = 0.8.dp.toPx())

        drawArc(
            color = color,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(w * 0.2f, horizonY - w * 0.3f),
            size = Size(w * 0.6f, w * 0.6f),
            style = Stroke(width = 0.8.dp.toPx())
        )

        val arrowX = w / 2f
        val lowY = horizonY - w * 0.15f
        val highY = horizonY - w * 0.55f
        val startY = if (isSunrise) lowY else highY
        val endY = if (isSunrise) highY else lowY

        drawLine(
            color = color,
            start = Offset(arrowX, startY),
            end = Offset(arrowX, endY),
            strokeWidth = 0.8.dp.toPx(),
            cap = StrokeCap.Round
        )

        val headSize = 1.1.dp.toPx()
        val dir = if (isSunrise) 1f else -1f
        val headPath = Path().apply {
            moveTo(arrowX - headSize, endY + dir * headSize)
            lineTo(arrowX, endY)
            lineTo(arrowX + headSize, endY + dir * headSize)
        }
        drawPath(headPath, color = color, style = Stroke(width = 0.8.dp.toPx()))
    }
}

/**
 * Petit résumé marée (haute/basse), même esprit visuel que SurfLiveStripOverlay, mais
 * compact pour tenir sur la ligne du titre. Le coefficient n'est plus repete entre
 * parentheses a cote de chaque heure : un seul coefficient est affiche a la fin, avec le
 * meme code couleur que "Previsions de la semaine" (pastille verte/orange/rouge selon
 * le seuil, cf. DailyTideCanvas dans MainScreen.kt).
 */
@Composable
private fun TideMiniInfo(tideInfo: DailyTideInfo, onSurfaceColor: Color) {
    val highTime = tideInfo.highTideTime
    val lowTime = tideInfo.lowTideTime
    val coef = tideInfo.coefficient

    Row(verticalAlignment = Alignment.CenterVertically) {
        if (highTime != null) {
            Canvas(modifier = Modifier.size(9.dp)) {
                val w = size.width
                val h = size.height
                val wavePath = Path().apply {
                    moveTo(0f, h * 0.70f)
                    quadraticBezierTo(w * 0.5f, h * 0.25f, w, h * 0.70f)
                }
                drawPath(wavePath, color = Color(0xFF64B5F6), style = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round))
                val arrowX = w * 0.5f
                drawLine(
                    color = Color(0xFF64B5F6),
                    start = Offset(arrowX, h * 0.70f),
                    end = Offset(arrowX, h * 0.10f),
                    strokeWidth = 1.dp.toPx(),
                    cap = StrokeCap.Round
                )
                val headSize = 1.4.dp.toPx()
                val headPath = Path().apply {
                    moveTo(arrowX - headSize, h * 0.10f + headSize)
                    lineTo(arrowX, h * 0.10f)
                    lineTo(arrowX + headSize, h * 0.10f + headSize)
                }
                drawPath(headPath, color = Color(0xFF64B5F6), style = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round))
            }
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = highTime,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = onSurfaceColor,
                maxLines = 1
            )
        }

        if (lowTime != null) {
            Spacer(modifier = Modifier.width(6.dp))
            Canvas(modifier = Modifier.size(9.dp)) {
                val w = size.width
                val h = size.height
                val wavePath = Path().apply {
                    moveTo(0f, h * 0.30f)
                    quadraticBezierTo(w * 0.5f, h * 0.75f, w, h * 0.30f)
                }
                drawPath(wavePath, color = Color(0xFF26A69A), style = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round))
                val arrowX = w * 0.5f
                drawLine(
                    color = Color(0xFF26A69A),
                    start = Offset(arrowX, h * 0.30f),
                    end = Offset(arrowX, h * 0.90f),
                    strokeWidth = 1.dp.toPx(),
                    cap = StrokeCap.Round
                )
                val headSize = 1.4.dp.toPx()
                val headPath2 = Path().apply {
                    moveTo(arrowX - headSize, h * 0.90f - headSize)
                    lineTo(arrowX, h * 0.90f)
                    lineTo(arrowX + headSize, h * 0.90f - headSize)
                }
                drawPath(headPath2, color = Color(0xFF26A69A), style = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round))
            }
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = lowTime,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = onSurfaceColor,
                maxLines = 1
            )
        }

        if (coef != null) {
            Spacer(modifier = Modifier.width(6.dp))

            val dotColor = when {
                coef < 55 -> AppColors.WindLow
                coef <= 80 -> AppColors.WindMid
                else -> AppColors.WindHigh
            }

            Text(
                text = "$coef",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = onSurfaceColor.copy(alpha = 0.75f),
                maxLines = 1
            )
            Spacer(modifier = Modifier.width(3.dp))
            Canvas(modifier = Modifier.size(6.dp)) {
                drawCircle(color = dotColor, radius = size.minDimension / 2f)
            }
        }
    }
}

/**
 * Formate une hauteur de houle pour l'axe, a la francaise (virgule) et sans decimale
 * inutile sur les valeurs rondes : 0.5 -> "0,5", 1.0 -> "1", 1.5 -> "1,5".
 * Les paliers en quart de metre (0.25 / 0.75...) gardent 2 decimales pour rester
 * exacts : arrondir "%.1f" en HALF_UP afficherait a tort "0,3" pour 0.25.
 */
/**
 * Échelle des hauteurs de houle (axe de gauche), commune au déroulé de la journée et à la vue semaine : palier de 0,25 m
 * doublé jusqu'à environ 4 lignes, avec toujours un peu de marge au-dessus du pic. Renvoie (maximum, valeurs des lignes).
 */
internal fun swellAxisScale(rawMax: Double): Pair<Double, List<Double>> {
    var step = 0.25
    while (rawMax / step > 4.0) step *= 2.0
    var niceMax = kotlin.math.ceil(rawMax / step) * step
    if (niceMax - rawMax < step * 0.2) niceMax += step
    val gridValues = generateSequence(step) { it + step }.takeWhile { it <= niceMax + step * 0.01 }.toList()
    return niceMax to gridValues
}

internal fun formatAxisHeight(value: Double): String {
    val hundredths = (value * 100).roundToLong()
    return when {
        hundredths % 100 == 0L -> (hundredths / 100).toString()
        hundredths % 50 == 0L -> formatDecimal(value, 1, decimalSeparator = ',')
        else -> formatDecimal(value, 2, decimalSeparator = ',')
    }
}

/**
 * Inspire des applis de reference (ex. Surf-Forecast) : un axe vertical a gauche avec
 * des lignes horizontales a hauteur de houle fixe (0,5 / 1 / 1,5 m, etc.), pour lire
 * la courbe d'un coup d'oeil "entre telle et telle ligne" plutot que de devoir dechiffrer
 * de petites etiquettes numeriques collees a chaque point.
 */
@Composable
private fun DailyTimelineSwellCanvas(
    hours: List<HourlyUiModel>,
    ratings: List<SlotRating>,
    nowIndex: Int,
    selectedIndex: Int,
    onSurfaceColor: Color,
    // Plus grosse houle de la semaine : la même échelle que la vue semaine (mêmes hauteurs, mêmes graduations).
    scaleRawMax: Double? = null,
    // Lever et coucher (heures décimales) : la nuit est assombrie.
    sunriseHour: Float? = null,
    sunsetHour: Float? = null,
    modifier: Modifier = Modifier
) {
    if (hours.size < 2) {
        Box(modifier = modifier)
        return
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    // Remplace les Paint Android (non multiplateformes) : mêmes tailles, graisses et alignements.
    val textMeasurer = rememberTextMeasurer()
    val axisTextStyle = TextStyle(color = onSurfaceColor.copy(alpha = 0.45f), fontSize = 10.sp, fontWeight = FontWeight.Normal)
    val periodTextStyle = TextStyle(color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    // Fond léger derrière les graduations (couleur de l'encart : marche en clair comme en sombre).
    val labelBg = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val padY = 16.dp.toPx()

        // Echelle de l'axe : un pas "rond" (0,25 / 0,5 / 1 / 2 m...) choisi pour obtenir
        // environ 4 lignes, et toujours un peu de marge au-dessus du pic du jour.
        val rawMax = maxOf(scaleRawMax ?: 0.0, hours.maxOfOrNull { it.waveHeight } ?: 1.0).coerceAtLeast(0.3)
        val (niceMax, gridValues) = swellAxisScale(rawMax)

        val axisLabels = gridValues.map { formatAxisHeight(it) }
        // Graduations DANS la courbe (pas de colonne vide à gauche) : marges gauche et droite identiques.
        val axisW = 0f

        val usableH = h - (2 * padY)
        val baseY = padY + usableH
        val plotLeft = axisW
        val plotW = (w - plotLeft).coerceAtLeast(1f)
        val stepX = plotW / (hours.size - 1).toFloat()

        // Lignes horizontales de repere + etiquette a gauche, a chaque palier de l'axe.
        gridValues.forEach { value ->
            val y = padY + usableH * (1f - (value / niceMax).toFloat())
            drawLine(
                color = onSurfaceColor.copy(alpha = 0.10f),
                start = Offset(plotLeft, y),
                end = Offset(w, y),
                strokeWidth = 0.8.dp.toPx()
            )
        }

        val points = hours.mapIndexed { index, item ->
            val ratio = (item.waveHeight / niceMax).coerceIn(0.0, 1.0).toFloat()
            val y = padY + usableH * (1f - ratio)
            Offset(plotLeft + index * stepX, y)
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
        drawPath(path = fillPath, color = AppColors.TideHigh.copy(alpha = 0.18f))

        // Point 3 : la courbe est tracee segment par segment, coloree selon le score
        // (rouge/orange/vert) de l'heure de depart du segment.
        for (i in 1 until points.size) {
            val prev = points[i - 1]
            val curr = points[i]
            val midX = (prev.x + curr.x) / 2f
            val segPath = Path().apply {
                moveTo(prev.x, prev.y)
                cubicTo(midX, prev.y, midX, curr.y, curr.x, curr.y)
            }
            val rating = ratings.getOrElse(i - 1) { ratings.getOrElse(i) { SlotRating(50, false) } }
            val segColor = conditionBand(rating).color()
            drawPath(path = segPath, color = segColor, style = Stroke(width = 2.6.dp.toPx()))
        }

        // Trois pastilles "periode" reparties dans la journee (matin / milieu / fin)
        // plutot qu'une seule au pic : donne une idee de comment la periode evolue sur
        // la journee, sans surcharger la courbe d'une etiquette par point.
        val periodIndices = listOf(0.13, 0.38, 0.63, 0.88)
            .map { frac -> (frac * (hours.size - 1)).roundToInt().coerceIn(0, hours.size - 1) }
            .distinct()
        periodIndices.forEach { idx ->
            val point = points[idx]
            val label = "${hours[idx].wavePeriod.roundToInt()}s"
            val periodLayout = textMeasurer.measure(label, periodTextStyle)
            val textW = periodLayout.size.width.toFloat()
            val bubbleLow = plotLeft + textW / 2f + 6.dp.toPx()
            val bubbleHigh = (w - textW / 2f - 6.dp.toPx()).coerceAtLeast(bubbleLow)
            val bubbleCenter = Offset(
                point.x.coerceIn(bubbleLow, bubbleHigh),
                (point.y - 16.dp.toPx()).coerceAtLeast(padY + 8.dp.toPx())
            )
            drawCircle(
                color = Color.Black.copy(alpha = 0.35f),
                radius = 13.dp.toPx(),
                center = bubbleCenter
            )
            // Centré sur la pastille, ligne de base à +3.5dp (comme Paint.Align.CENTER).
            drawText(
                periodLayout,
                topLeft = Offset(bubbleCenter.x - textW / 2f, bubbleCenter.y + 3.5.dp.toPx() - periodLayout.firstBaseline)
            )
        }

        // La nuit (avant le lever, après le coucher) est assombrie.
        val firstHour = hours.first().rawTime.hour.toFloat()
        val nightColor = Color.Black.copy(alpha = 0.30f)
        if (sunriseHour != null) {
            val x = (plotLeft + (sunriseHour - firstHour) * stepX).coerceIn(plotLeft, w)
            if (x > plotLeft) drawRect(nightColor, topLeft = Offset(plotLeft, padY), size = Size(x - plotLeft, baseY - padY))
        }
        if (sunsetHour != null) {
            val x = (plotLeft + (sunsetHour - firstHour) * stepX).coerceIn(plotLeft, w)
            if (x < w) drawRect(nightColor, topLeft = Offset(x, padY), size = Size(w - x, baseY - padY))
        }

        // « Maintenant » : trait en pointillés et son nom en haut (masqué quand il touche l'heure choisie).
        val selX = points.getOrNull(selectedIndex)?.x
        if (nowIndex in points.indices && nowIndex != selectedIndex) {
            val nx = points[nowIndex].x
            drawLine(
                color = onSurfaceColor.copy(alpha = 0.5f), start = Offset(nx, padY - 2.dp.toPx()), end = Offset(nx, baseY),
                strokeWidth = 1.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
            )
            if (selX == null || kotlin.math.abs(selX - nx) > 56.dp.toPx()) {
                val nowLayout = textMeasurer.measure("Maintenant", axisTextStyle.copy(fontWeight = FontWeight.Bold))
                val tx = (nx - nowLayout.size.width / 2f).coerceIn(0f, w - nowLayout.size.width)
                drawText(nowLayout, topLeft = Offset(tx, 0f))
            }
        }

        // Heure choisie : trait blanc et son heure en haut ; le point prend la couleur de la note.
        if (selectedIndex in points.indices) {
            val selPoint = points[selectedIndex]
            drawLine(color = Color.White, start = Offset(selPoint.x, padY - 2.dp.toPx()), end = Offset(selPoint.x, baseY), strokeWidth = 2.dp.toPx())
            val hourText = hours[selectedIndex].rawTime.hour.toString().padStart(2, '0') + ":00"
            val pill = textMeasurer.measure(hourText, TextStyle(color = Color(0xFF0B121A), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold))
            val pw = pill.size.width + 10.dp.toPx()
            val pillLeft = (selPoint.x - pw / 2f).coerceIn(0f, w - pw)
            drawRoundRect(Color.White, topLeft = Offset(pillLeft, 0f), size = Size(pw, pill.size.height.toFloat() + 2.dp.toPx()), cornerRadius = CornerRadius(5.dp.toPx()))
            drawText(pill, topLeft = Offset(pillLeft + 5.dp.toPx(), 1.dp.toPx()))
            val ringColor = ratings.getOrNull(selectedIndex)?.let { conditionBand(it).color() } ?: primaryColor
            drawCircle(color = Color.White, radius = 4.6.dp.toPx(), center = selPoint)
            drawCircle(color = ringColor, radius = 3.dp.toPx(), center = selPoint)
        }

        // Graduations en dernier, par-dessus le remplissage et la courbe : le fond léger derrière les chiffres reste
        // visible (comme dans la vue semaine, où l'échelle est dessinée au-dessus).
        gridValues.forEach { value ->
            val y = padY + usableH * (1f - (value / niceMax).toFloat())
            val axisLayout = textMeasurer.measure(formatAxisHeight(value), axisTextStyle)
            val labelTop = y - axisLayout.size.height - 1.dp.toPx()
            drawRoundRect(
                color = labelBg,
                topLeft = Offset(2.dp.toPx(), labelTop),
                size = androidx.compose.ui.geometry.Size(axisLayout.size.width + 4.dp.toPx(), axisLayout.size.height.toFloat()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
            )
            drawText(axisLayout, topLeft = Offset(4.dp.toPx(), labelTop))
        }
    }
}
