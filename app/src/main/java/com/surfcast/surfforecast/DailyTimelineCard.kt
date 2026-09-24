@file:Suppress("SpellCheckingInspection")
package com.surfcast.surfforecast

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.surfcast.surfforecast.ui.theme.AppColors
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
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
@Composable
fun DailyTimelineCard(
    selectedDate: LocalDate,
    groupedByDate: Map<LocalDate, List<HourlyUiModel>>,
    dailySunInfo: Map<LocalDate, DailySunInfo>,
    dailyTides: Map<LocalDate, DailyTideInfo>,
    windUnit: String,
    idealSwellDirection: Int?,
    surferLevel: String,
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
                val t = it.rawTime.toLocalTime()
                !t.isBefore(sun.sunrise) && !t.isAfter(sun.sunset)
            }
        } else {
            // Repli si le lever/coucher n'est pas dispo pour ce jour (ex: J+6/7) : plage diurne large.
            dayHours.filter { it.rawTime.hour in 7..21 }
        }
    }

    val now = LocalDateTime.now()

    val curveHours = daylightHoursFor(selectedDate)
    val timelineItems: List<HourlyUiModel> = curveHours

    val nowIndex = curveHours.indexOfFirst {
        it.rawTime.toLocalDate() == now.toLocalDate() && it.rawTime.hour == now.hour
    }

    val selectedIndex = selectedHour?.let { sel -> curveHours.indexOfFirst { it.rawTime == sel.rawTime } } ?: -1

    val tideInfo = dailyTides[selectedDate]
    val sun = dailySunInfo[selectedDate]
    val hourFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }

    // Point 3 : score par heure (0-100), sert a colorer la courbe segment par segment.
    val scores = curveHours.map { hourly ->
        calculateSlotScore(hourly, idealSwellDirection, surferLevel, isNearHighTide(hourly, tideInfo))
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = surfaceColor),
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
            // --- En-tete : poignee + titre + marees + controle de reduction, sur la meme ligne ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f).then(dragHandleModifier),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DragHandleIcon(color = onSurfaceColor.copy(alpha = 0.35f))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Déroulé de la journée",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = onSurfaceColor.copy(alpha = 0.55f),
                        maxLines = 1
                    )

                    if (tideInfo != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        TideMiniInfo(tideInfo = tideInfo, onSurfaceColor = onSurfaceColor)
                    }
                }

                IconButton(
                    onClick = onToggleCollapse,
                    modifier = Modifier.size(22.dp)
                ) {
                    Canvas(modifier = Modifier.size(12.dp)) {
                        val w = size.width
                        val h = size.height
                        val path = Path().apply {
                            if (isCollapsed) {
                                moveTo(w * 0.2f, h * 0.35f)
                                lineTo(w * 0.8f, h * 0.35f)
                                lineTo(w * 0.5f, h * 0.75f)
                                close()
                            } else {
                                moveTo(w * 0.2f, h * 0.65f)
                                lineTo(w * 0.8f, h * 0.65f)
                                lineTo(w * 0.5f, h * 0.25f)
                                close()
                            }
                        }
                        drawPath(path = path, color = onSurfaceColor.copy(alpha = 0.6f))
                    }
                }
            }

            if (!isCollapsed) {
            Spacer(modifier = Modifier.height(4.dp))

            if (timelineItems.isEmpty()) {
                Text(
                    text = "Pas de données diurnes disponibles pour cette sélection.",
                    fontSize = 11.sp,
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
                                val widthPerItem = size.width / timelineItems.size.toFloat()
                                val index = (x / widthPerItem).toInt().coerceIn(0, timelineItems.size - 1)
                                onHourSelected(timelineItems[index])
                            }
                        }
                        .pointerInput(timelineItems) {
                            detectTapGestures { offset ->
                                val widthPerItem = size.width / timelineItems.size.toFloat()
                                val index = (offset.x / widthPerItem).toInt().coerceIn(0, timelineItems.size - 1)
                                onHourSelected(timelineItems[index])
                            }
                        }
                ) {
                // --- Haut : vent (vitesse + direction abrégée) + météo ---
                Row(modifier = Modifier.fillMaxWidth()) {
                    timelineItems.forEachIndexed { index, hourly ->
                        val dirFr = SurfUnitsHelper.formatCardinalFr(hourly.windDirectionStr)
                        val degrees = SurfUnitsHelper.cardinalToDegrees(dirFr)
                        val rotationAngle = (degrees + 180f) % 360f
                        val arrowColor = SurfUnitsHelper.getSurfWindColor(dirFr, hourly.windSpeedKmh)
                        val isSelected = index == selectedIndex

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    color = if (isSelected) primaryColor.copy(alpha = 0.14f) else Color.Transparent,
                                    shape = RoundedCornerShape(4.dp)
                                ),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(text = SurfUnitsHelper.resolveRealWeatherEmoji(hourly), fontSize = 11.sp)
                            Canvas(modifier = Modifier.size(9.dp)) {
                                val w = size.width
                                val h = size.height
                                rotate(rotationAngle, pivot = Offset(w / 2f, h / 2f)) {
                                    val path = Path().apply {
                                        moveTo(w * 0.5f, 0f)
                                        lineTo(w * 0.9f, h * 0.55f)
                                        lineTo(w * 0.5f, h * 0.38f)
                                        lineTo(w * 0.1f, h * 0.55f)
                                        close()
                                    }
                                    drawPath(path = path, color = arrowColor)
                                }
                            }
                            Text(
                                text = dirFr,
                                fontSize = 6.sp,
                                fontWeight = FontWeight.Bold,
                                color = arrowColor,
                                maxLines = 1
                            )
                            Text(
                                text = SurfUnitsHelper.formatWindValue(hourly.windSpeedKmh, windUnit),
                                fontSize = 6.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = onSurfaceColor.copy(alpha = 0.7f),
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // --- Milieu : courbe de houle coloree par score, avec repere sur l'heure actuelle ---
                DailyTimelineSwellCanvas(
                    hours = curveHours,
                    scores = scores,
                    nowIndex = nowIndex,
                    selectedIndex = selectedIndex,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                )

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    listOf(
                        AppColors.WindHigh to "Faible",
                        AppColors.WindMid to "Moyen",
                        AppColors.TideLow to "Bon"
                    ).forEach { (dotColor, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        ) {
                            Canvas(modifier = Modifier.size(6.dp)) {
                                drawCircle(color = dotColor, radius = size.minDimension / 2f)
                            }
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = label,
                                fontSize = 7.sp,
                                color = onSurfaceColor.copy(alpha = 0.55f),
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // --- Bas : heures, lever/coucher exacts aux deux extremites ---
                Row(modifier = Modifier.fillMaxWidth()) {
                    timelineItems.forEachIndexed { index, hourly ->
                        val isFirst = index == 0
                        val isLast = index == timelineItems.lastIndex
                        val isSelected = index == selectedIndex

                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            when {
                                isFirst && sun != null -> {
                                    SunEventIcon(
                                        isSunrise = true,
                                        color = onSurfaceColor.copy(alpha = 0.6f),
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Text(
                                        text = sun.sunrise.format(hourFormatter),
                                        fontSize = 7.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = onSurfaceColor.copy(alpha = 0.6f),
                                        maxLines = 1
                                    )
                                }
                                isLast && sun != null -> {
                                    SunEventIcon(
                                        isSunrise = false,
                                        color = onSurfaceColor.copy(alpha = 0.6f),
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Text(
                                        text = sun.sunset.format(hourFormatter),
                                        fontSize = 7.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = onSurfaceColor.copy(alpha = 0.6f),
                                        maxLines = 1
                                    )
                                }
                                else -> {
                                    Text(
                                        text = String.format(Locale.FRANCE, "%02dh", hourly.rawTime.hour),
                                        fontSize = 7.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                        color = if (isSelected) primaryColor else onSurfaceColor.copy(alpha = 0.5f),
                                        maxLines = 1,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
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
                fontSize = 8.5.sp,
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
                fontSize = 8.5.sp,
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
                fontSize = 8.5.sp,
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

@Composable
private fun DailyTimelineSwellCanvas(
    hours: List<HourlyUiModel>,
    scores: List<Int>,
    nowIndex: Int,
    selectedIndex: Int,
    modifier: Modifier = Modifier
) {
    if (hours.size < 2) {
        Box(modifier = modifier)
        return
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val density = LocalDensity.current
    val labelTextPaint = remember(density) {
        Paint().apply {
            color = AppColors.TideHighDark.toArgb()
            textSize = with(density) { 7.5.sp.toPx() }
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val padY = 6.dp.toPx()
        val usableH = h - (2 * padY)
        val baseY = padY + usableH

        val maxHeight = (hours.maxOfOrNull { it.waveHeight } ?: 1.0).coerceAtLeast(0.5).toFloat()
        val stepX = w / (hours.size - 1).toFloat()

        // On ne remplit que 80% de la hauteur utile avec les donnees : les 20% du haut
        // restent toujours libres pour que les etiquettes hauteur/periode ne soient
        // jamais ecrasees par la courbe, meme quand un point touche le maximum.
        val points = hours.mapIndexed { index, item ->
            val ratio = (item.waveHeight.toFloat() / maxHeight).coerceIn(0f, 1f) * 0.8f
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
            val score = scores.getOrElse(i - 1) { scores.getOrElse(i) { 50 } }
            val segColor = when (scoreToColorCategory(score)) {
                "red" -> AppColors.WindHigh
                "orange" -> AppColors.WindMid
                else -> AppColors.TideLow
            }
            drawPath(path = segPath, color = segColor, style = Stroke(width = 2.2.dp.toPx()))
        }

        // Étiquettes hauteur/période à quelques points clés pour éviter la surcharge visuelle
        val labelStep = (hours.size / 6).coerceAtLeast(1)
        hours.forEachIndexed { index, item ->
            if (index % labelStep == 0) {
                val label = String.format(Locale.US, "%.1fm/%ds", item.waveHeight, item.wavePeriod.roundToInt())
                val textW = labelTextPaint.measureText(label)
                val x = (points[index].x - textW / 2f).coerceIn(0f, (w - textW).coerceAtLeast(0f))
                val y = (points[index].y - 4.dp.toPx()).coerceAtLeast(2.dp.toPx())
                drawContext.canvas.nativeCanvas.drawText(label, x, y, labelTextPaint)
            }
        }

        // Ligne verticale : heure selectionnee via le scrub tactile (synchro avec les
        // encarts Houle/Vent/Meteo), tracee avant le point "heure actuelle" pour que
        // ce dernier reste visible par-dessus si les deux coincident.
        if (selectedIndex in points.indices) {
            val selPoint = points[selectedIndex]
            drawLine(
                color = primaryColor.copy(alpha = 0.6f),
                start = Offset(selPoint.x, padY),
                end = Offset(selPoint.x, baseY),
                strokeWidth = 1.6.dp.toPx()
            )
            drawCircle(color = Color.White, radius = 3.5.dp.toPx(), center = selPoint)
            drawCircle(color = primaryColor, radius = 2.4.dp.toPx(), center = selPoint)
        }

        // Point rouge : heure actuelle, si elle fait partie des données affichées
        if (nowIndex in points.indices && nowIndex != selectedIndex) {
            val nowPoint = points[nowIndex]
            drawCircle(color = Color.White, radius = 3.5.dp.toPx(), center = nowPoint)
            drawCircle(color = Color(0xFFE53935), radius = 2.4.dp.toPx(), center = nowPoint)
        }
    }
}
