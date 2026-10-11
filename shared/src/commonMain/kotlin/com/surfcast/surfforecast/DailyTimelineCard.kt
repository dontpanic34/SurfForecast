@file:Suppress("SpellCheckingInspection")
package com.surfcast.surfforecast

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.surfcast.surfforecast.ui.theme.AppColors
import kotlinx.datetime.LocalDate
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin


// Dimensions (dp) du déroulé : mêmes proportions que la maquette validée.
private const val SELECTED_WIND_DROP = 9 // la colonne de vent de l'heure choisie descend de ce nombre de dp pour la repérer
private const val WIND_ROW_H = 40 + SELECTED_WIND_DROP
private const val PLOT_TOP = 18      // haut de la zone de courbe (dp)
private const val PLOT_BOTTOM_MARGIN = 8 // marge sous la courbe (dp) ; la hauteur du graphique vient des réglages (SkyGraphSettings)
private const val BAND_H = 28
private const val HOURS_H = 22
private const val SIDE_PAD = 4

/**
 * « Déroulé de la journée » : l'heure touchée, son libellé, sa phrase et sa jauge ; le vent en couleur toutes les 2 h (plus
 * l'heure choisie) ; la courbe de houle sur un fond météo vivant (taille au-dessus du point, période en dessous) ; une bande
 * jour et nuit découpée heure par heure avec le soleil levant et couchant à leur heure exacte ; une seule ligne d'heures.
 *
 * La fenêtre va d'une demi-heure avant le lever à une demi-heure après le coucher. Glisser ou taper sur la courbe OU sur la
 * bande déplace l'heure choisie ([selectedHour] / [onHourSelected]), partagée avec les encarts Houle/Vent/Météo plus bas.
 */
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
    // Gardé pour compatibilité : le déroulé n'a plus de vue simple.
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
    val isDark = isDarkSurfaceTheme()
    val density = LocalDensity.current

    val sun = dailySunInfo[selectedDate]
    val sunrise = sun?.let { it.sunrise.hour + it.sunrise.minute / 60f } ?: 7f
    val sunset = sun?.let { it.sunset.hour + it.sunset.minute / 60f } ?: 20f
    val ax0 = sunrise - 0.5f
    val ax1 = sunset + 0.5f
    val allDay = (groupedByDate[selectedDate] ?: emptyList()).sortedBy { it.rawTime }
    val firstH = ceil(ax0).toInt().coerceAtLeast(0)
    val lastH = floor(ax1).toInt().coerceAtMost(23)
    val curveHours = allDay.filter { it.rawTime.hour in firstH..lastH }.let { if (it.size >= 2) it else allDay }

    // Notation avec la mémoire du vent : la mer ne devient pas « glacée » une heure après 30 km/h de vent de mer.
    val scoring = hoursWithWindMemory(selectedDate, groupedByDate).associateBy { it.rawTime }
    val ratings = curveHours.map { shown ->
        calculateSlotRating(scoring[shown.rawTime] ?: shown, idealSwellDirection, surferLevel)
    }
    // Une heure choisie hors de la fenêtre (la nuit) se ramène à l'heure visible la plus proche.
    val selectedIndex = selectedHour?.let { sel ->
        val exact = curveHours.indexOfFirst { it.rawTime == sel.rawTime }
        if (exact >= 0) exact else curveHours.indices.minByOrNull { abs(curveHours[it].rawTime.hour - sel.rawTime.hour) } ?: -1
    } ?: -1

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = surfaceColor),
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
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
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(modifier = Modifier.width(110.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            for (i in 1..5) {
                                Box(
                                    Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp))
                                        .background(if (i <= level) shownBand.color() else onSurfaceColor.copy(alpha = 0.15f))
                                )
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        // Marées du jour (haute, basse, coefficient), à droite de la jauge.
                        dailyTides[selectedDate]?.let { TideMiniInfo(it, onSurfaceColor) }
                    }
                }
            } else {
                Text(
                    text = "Déroulé de la journée", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    color = onSurfaceColor.copy(alpha = 0.55f), maxLines = 1,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                )
            }

            if (!isCollapsed) {
                Spacer(modifier = Modifier.height(4.dp))
                if (curveHours.size < 2) {
                    Text(
                        text = "Pas de données diurnes disponibles pour cette sélection.", fontSize = 12.sp,
                        color = onSurfaceColor.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    DailyTimelineBody(
                        hours = curveHours, ratings = ratings, selectedIndex = selectedIndex, onHourSelected = onHourSelected,
                        sunrise = sunrise, sunset = sunset, ax0 = ax0, ax1 = ax1, windUnit = windUnit,
                        isDark = isDark, onSurfaceColor = onSurfaceColor
                    )
                }
            }
        }
    }
}

/** Position horizontale d'une heure (décimale) sur la largeur [w] (px), avec une petite marge de chaque côté. */
private fun hourX(h: Float, ax0: Float, ax1: Float, w: Float, pad: Float): Float = pad + (w - 2 * pad) * (h - ax0) / (ax1 - ax0)

/** Hauteur du soleil de 0 (horizon) à 1 (zénith, à la mi-journée entre lever et coucher), ou -1 hors du jour. */
private fun sunAltitude(h: Float, sunrise: Float, sunset: Float): Float =
    if (h < sunrise || h > sunset) -1f else sin(PI.toFloat() * (h - sunrise) / (sunset - sunrise)).toDouble().pow(0.85).toFloat()

private fun formatHm(h: Float): String {
    var m = (h * 60f).roundToInt()
    val hh = m / 60
    m %= 60
    return hh.toString().padStart(2, '0') + ":" + m.toString().padStart(2, '0')
}

@Composable
private fun DailyTimelineBody(
    hours: List<HourlyUiModel>,
    ratings: List<SlotRating>,
    selectedIndex: Int,
    onHourSelected: (HourlyUiModel) -> Unit,
    sunrise: Float,
    sunset: Float,
    ax0: Float,
    ax1: Float,
    windUnit: String,
    isDark: Boolean,
    onSurfaceColor: Color
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val nacre = if (isDark) Color(0xFFF1F4F7) else Color(0xFF2A3A4A)
    val sunGold = if (isDark) Color(0xFFFFD9A0) else Color(0xFFB9740A)
    val selHour = hours.getOrNull(selectedIndex)?.rawTime?.hour
    val selH = selHour?.toFloat()
    // Heure actuelle (avec les minutes), mise à jour chaque minute ; seulement pour le jour en cours.
    val nowTick by produceState(nowLocalDateTime()) { while (true) { delay(60_000); value = nowLocalDateTime() } }
    val nowHour: Float? = hours.firstOrNull()?.rawTime?.date?.takeIf { it == nowTick.date }?.let { nowTick.hour + nowTick.minute / 60f }

    // Une indication de vent par heure d'ensoleillement ; on masque seulement ce qui toucherait l'heure choisie (mesuré en pixels).
    val sunnyHours = hours.map { it.rawTime.hour }.filter { it >= ceil(sunrise).toInt() && it <= floor(sunset).toInt() }
    val windFont = 6.6f

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val wPx = with(density) { maxWidth.toPx() }
        val pad = with(density) { SIDE_PAD.dp.toPx() }
        fun xOf(h: Float) = hourX(h, ax0, ax1, wPx, pad)

        fun pickHour(x: Float) {
            val h = ax0 + ((x - pad) / (wPx - 2 * pad)) * (ax1 - ax0)
            val best = hours.minByOrNull { abs(it.rawTime.hour - h) } ?: return
            onHourSelected(best)
        }

        Box(
            modifier = Modifier.fillMaxWidth()
                .pointerInput(hours, ax0, ax1) { detectTapGestures { pickHour(it.x) } }
                .pointerInput(hours, ax0, ax1) { detectHorizontalDragGestures { change, _ -> pickHour(change.position.x) } }
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // --- Vent ---
                val windMinGap = windFont * 2.1f
                val windHours: List<Int> = run {
                    val cand = ArrayList<Pair<Int, Int>>() // heure, priorité
                    sunnyHours.forEach { cand.add(it to 1) }
                    if (selHour != null && selHour in sunnyHours) cand.add(selHour to 0)
                    val kept = ArrayList<Int>()
                    cand.sortedBy { it.second }.forEach { (h, _) ->
                        if (kept.none { k -> k == h || abs(xOf(k.toFloat()) - xOf(h.toFloat())) < with(density) { windMinGap.dp.toPx() } }) kept.add(h)
                    }
                    kept.sorted()
                }
                Box(modifier = Modifier.fillMaxWidth().height(WIND_ROW_H.dp)) {
                    for (h in windHours) {
                        val hourly = hours.firstOrNull { it.rawTime.hour == h } ?: continue
                        key(h) {
                        val dirFr = SurfUnitsHelper.formatCardinalFr(hourly.windDirectionStr)
                        val color = SurfUnitsHelper.getSurfWindColor(dirFr, effectiveWindKmh(hourly).roundToInt())
                        val textColor = SurfUnitsHelper.windTextColor(color, isDark)
                        val rotation = (SurfUnitsHelper.cardinalToDegrees(dirFr) + 180f) % 360f
                        val itemW = 34.dp
                        // La colonne de l'heure choisie descend un peu, avec une petite animation : c'est celle à lire.
                        val drop by animateDpAsState(if (h == selHour) SELECTED_WIND_DROP.dp else 0.dp, label = "windDrop")
                        Column(
                            modifier = Modifier.offset { IntOffset((xOf(h.toFloat()) - itemW.toPx() / 2f).roundToInt(), drop.roundToPx()) }.width(itemW),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Canvas(modifier = Modifier.size((windFont + 3f).dp)) {
                                val w = size.width
                                val hh = size.height
                                rotate(rotation, pivot = Offset(w / 2f, hh / 2f)) {
                                    val p = Path().apply {
                                        moveTo(w * 0.5f, 0f)
                                        lineTo(w * 0.9f, hh * 0.55f)
                                        lineTo(w * 0.5f, hh * 0.38f)
                                        lineTo(w * 0.1f, hh * 0.55f)
                                        close()
                                    }
                                    drawPath(p, color = color)
                                }
                            }
                            Text(dirFr, fontSize = windFont.sp, fontWeight = FontWeight.ExtraBold, color = textColor, maxLines = 1, lineHeight = (windFont + 2f).sp)
                            Text(
                                SurfUnitsHelper.formatWindValue(hourly.windSpeedKmh, windUnit), fontSize = windFont.sp,
                                fontWeight = FontWeight.ExtraBold, color = textColor, maxLines = 1, lineHeight = (windFont + 2f).sp
                            )
                            // Soulignement de la colonne de l'heure choisie, de la couleur de son vent.
                            if (h == selHour) {
                                Box(modifier = Modifier.padding(top = 2.dp).width(16.dp).height(2.dp).background(textColor, RoundedCornerShape(1.dp)))
                            }
                        }
                        }
                    }
                }

                // --- Courbe sur son fond météo ---
                // Échelle fixe : seules les graduations changent (elles s'arrêtent à la houle la plus haute de la journée).
                val rawMax = (hours.maxOfOrNull { it.waveHeight } ?: 1.0).coerceAtLeast(0.3)
                val gridValues = swellAxisGrid(rawMax)
                // Part du graphique prise par l'eau, à la houle la plus haute de la journée ; au-dessus, c'est du ciel.
                val waterF = swellAxisFraction(rawMax)
                val plotHdp = SkyGraphSettings.dayPlotDp()
                val plotBottomDp = plotHdp - PLOT_BOTTOM_MARGIN
                Box(modifier = Modifier.fillMaxWidth().height(plotHdp.dp)) {
                    val extPx = 0f
                    val topPx = with(density) { PLOT_TOP.dp.toPx() }
                    val bottomPx = with(density) { plotBottomDp.dp.toPx() }
                    SkyCanvas(
                        modifier = Modifier.fillMaxWidth().height(plotHdp.dp),
                        isDark = isDark
                    ) { size ->
                        val xs = FloatArray(hours.size) { xOf(hours[it].rawTime.hour.toFloat()) }
                        // Lissage sur plusieurs heures : le ciel et la pluie passent d'un état à l'autre sans colonne.
                        val hourPx = if (xs.size > 1) (xs.last() - xs.first()) / (hours.last().rawTime.hour - hours.first().rawTime.hour).coerceAtLeast(1)
                            else with(density) { 30.dp.toPx() }
                        val cover = SkyCurve(xs, FloatArray(hours.size) { hours[it].cloudCover / 100f }, 1.25f * hourPx)
                        val rain = SkyCurve(xs, FloatArray(hours.size) { rainAmountForCode(hours[it].weatherCode) }, 0.75f * hourPx)
                        val snow = SkyCurve(xs, FloatArray(hours.size) { snowAmountForCode(hours[it].weatherCode) }, 0.75f * hourPx)
                        // Le soleil suit l'heure choisie : il se lève en bas à l'heure du lever, monte au zénith à la mi-journée
                        // près du vent, puis redescend et se couche en bas à l'heure du coucher.
                        val suns = ArrayList<SunSpot>()
                        if (selH != null) {
                            val alt = sunAltitude(selH, sunrise, sunset)
                            if (alt >= 0f) {
                                val sizePx = with(density) { 30.dp.toPx() }
                                // Le soleil reste dans le ciel : au plus bas, juste au-dessus de la ligne d'eau.
                                val waterline = bottomPx - (bottomPx - topPx) * waterF
                                val horizon = waterline - with(density) { 18.dp.toPx() }
                                val zenith = topPx + with(density) { 6.dp.toPx() }
                                val cy = horizon - (horizon - zenith) * alt
                                suns.add(SunSpot(xOf(selH), sizePx, extPx + cy - sizePx / 2f))
                            }
                        }
                        SkyScene(
                            left = pad, right = size.width - pad, top = extPx + topPx, bottom = extPx + bottomPx,
                            cover = cover, rain = rain, snow = snow, suns = suns,
                            animate = hours.any { rainAmountForCode(it.weatherCode) > 0f || snowAmountForCode(it.weatherCode) > 0f },
                            skyHeight = (bottomPx - topPx) * (1f - waterF) + with(density) { 8.dp.toPx() }
                        )
                    }
                    DailyCurveCanvas(
                        modifier = Modifier.fillMaxSize(), hours = hours, ratings = ratings, selectedIndex = selectedIndex,
                        gridValues = gridValues, plotBottomDp = plotBottomDp, onSurfaceColor = onSurfaceColor, isDark = isDark,
                        nacre = nacre, textMeasurer = textMeasurer, xOf = ::xOf, edgeLeft = xOf(ax0), edgeRight = xOf(ax1), nowHour = nowHour
                    )
                }

                // --- Bande jour et nuit, heure par heure ---
                Canvas(modifier = Modifier.fillMaxWidth().height(BAND_H.dp)) {
                    val cellDark = if (isDark) Color(0xFF0F1822) else Color(0xFFD3DBE4)
                    val goldLo = Color(0xFF0B121A)
                    val ico = 9.dp.toPx()
                    val xr = xOf(sunrise)
                    val xs = xOf(sunset)
                    val cellH = 6.dp.toPx()
                    val top = 7.dp.toPx()
                    for (h in hours.map { it.rawTime.hour }.let { (it.firstOrNull() ?: 0)..(it.lastOrNull() ?: 0) }) {
                        val x0 = maxOf(pad, xOf(h - 0.5f))
                        val x1 = minOf(size.width - pad, xOf(h + 0.5f))
                        if (x1 - x0 < 1f) continue
                        drawRoundRect(cellDark, Offset(x0 + 0.75.dp.toPx(), top), Size(x1 - x0 - 1.5.dp.toPx(), cellH), CornerRadius(3.dp.toPx()))
                        val a0 = maxOf(x0, xr + ico)
                        val a1 = minOf(x1, xs - ico)
                        if (a1 - a0 > 1.2.dp.toPx()) {
                            val mid = sin(PI * ((h - sunrise) / (sunset - sunrise)).coerceIn(0f, 1f).toDouble()).toFloat()
                            val k = 0.55f + 0.45f * mid
                            val gold = androidx.compose.ui.graphics.lerp(goldLo, Color(0xFFF6C453), k)
                            val on = h == selHour
                            val hh = if (on) 9.dp.toPx() else cellH
                            drawRoundRect(
                                gold, Offset(a0 + 0.75.dp.toPx(), if (on) top - 1.5.dp.toPx() else top),
                                Size(a1 - a0 - 1.5.dp.toPx(), hh), CornerRadius(3.dp.toPx())
                            )
                            if (on) {
                                drawRoundRect(
                                    nacre, Offset(a0 + 0.75.dp.toPx(), top - 1.5.dp.toPx()), Size(a1 - a0 - 1.5.dp.toPx(), hh),
                                    CornerRadius(3.dp.toPx()), style = Stroke(1.4.dp.toPx())
                                )
                            }
                        }
                    }
                    drawSunEvent(Offset(xr, 10.dp.toPx()), true, sunGold, 1.15.dp.toPx())
                    drawSunEvent(Offset(xs, 10.dp.toPx()), false, sunGold, 1.15.dp.toPx())
                }

                // --- Une seule ligne d'heures, dynamique ---
                Box(modifier = Modifier.fillMaxWidth().height(HOURS_H.dp)) {
                    class Lbl(val h: Float, val text: String, val kind: Int, val prio: Int)
                    val items = ArrayList<Lbl>()
                    if (selH != null) items.add(Lbl(selH, "${selHour}:00", 0, 0))
                    items.add(Lbl(sunrise, formatHm(sunrise), 1, 1))
                    items.add(Lbl(sunset, formatHm(sunset), 1, 1))
                    for (h in hours.map { it.rawTime.hour }) {
                        if (h % 2 == 0 && abs(h - sunrise) >= 1.25f && abs(h - sunset) >= 1.25f) items.add(Lbl(h.toFloat(), "$h h", 2, 2))
                    }
                    val edge = with(density) { 20.dp.toPx() }
                    fun cx(h: Float) = xOf(h).coerceIn(edge, wPx - edge)
                    val kept = ArrayList<Lbl>()
                    for (lb in items.sortedBy { x -> x.prio }) {
                        val w = with(density) { (if (lb.kind == 2) 30 else 34).dp.toPx() }
                        if (kept.all { k ->
                                val kw = with(density) { (if (k.kind == 2) 30 else 34).dp.toPx() }
                                abs(cx(k.h) - cx(lb.h)) >= kw / 2f + w / 2f + with(density) { 2.dp.toPx() }
                            }
                        ) kept.add(lb)
                    }
                    for (l in kept) {
                        val color = when (l.kind) {
                            0 -> nacre
                            1 -> sunGold
                            else -> onSurfaceColor.copy(alpha = 0.6f)
                        }
                        val fs = when (l.kind) { 0 -> 12.5f; 1 -> 9.5f; else -> 10f }
                        val weight = if (l.kind == 2) FontWeight.SemiBold else FontWeight.ExtraBold
                        Box(
                            modifier = Modifier.offset { IntOffset((cx(l.h) - 24.dp.toPx()).roundToInt(), 2.dp.roundToPx()) }.width(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(l.text, fontSize = fs.sp, fontWeight = weight, color = color, maxLines = 1)
                        }
                    }
                }
            }

        }
    }
}

/** Courbe de houle : graduations, remplissage, segments colorés par note, puis le petit point nacré avec sa taille et sa période. */
@Composable
private fun DailyCurveCanvas(
    modifier: Modifier,
    hours: List<HourlyUiModel>,
    ratings: List<SlotRating>,
    selectedIndex: Int,
    gridValues: List<Double>,
    plotBottomDp: Float,
    onSurfaceColor: Color,
    isDark: Boolean,
    nacre: Color,
    textMeasurer: TextMeasurer,
    xOf: (Float) -> Float,
    edgeLeft: Float,
    edgeRight: Float,
    nowHour: Float? = null
) {
    val skyImages = LocalSkyImages.current
    val rainActive = hours.any { rainAmountForCode(it.weatherCode) > 0f }
    val clock = rememberRainClock(rainActive)
    val halo = if (isDark) Color(0xFF0B121A) else Color.White
    val weekTextHalo = Color(0xFF0B121A)
    val labelColor = if (isDark) Color.White else Color(0xFF14202B)
    Canvas(modifier = modifier) {
        val top = PLOT_TOP.dp.toPx()
        val bottom = plotBottomDp.dp.toPx()
        fun yOf(v: Double): Float = bottom - (bottom - top) * swellAxisFraction(v)

        gridValues.forEach { v ->
            val y = yOf(v)
            // Pointillés au-dessus de 2 m, là où l'échelle est resserrée.
            val dash = if (v > SWELL_AXIS_BREAK) PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())) else null
            drawLine(onSurfaceColor.copy(alpha = 0.10f), Offset(0f, y), Offset(size.width, y), strokeWidth = 0.8.dp.toPx(), pathEffect = dash)
        }

        val pts = hours.map { Offset(xOf(it.rawTime.hour.toFloat()), yOf(it.waveHeight)) }
        if (pts.size >= 2) {
            // La courbe va d'un bord à l'autre de la fenêtre (lever − 0,5 h → coucher + 0,5 h) : on prolonge à plat le premier
            // et le dernier point jusqu'aux bords.
            val line = ArrayList<Offset>(pts.size + 2)
            line.add(Offset(minOf(edgeLeft, pts.first().x), pts.first().y))
            line.addAll(pts)
            line.add(Offset(maxOf(edgeRight, pts.last().x), pts.last().y))
            val fill = Path().apply {
                moveTo(line.first().x, bottom)
                lineTo(line.first().x, line.first().y)
                for (i in 1 until line.size) {
                    val p = line[i - 1]; val c = line[i]; val mx = (p.x + c.x) / 2f
                    cubicTo(mx, p.y, mx, c.y, c.x, c.y)
                }
                lineTo(line.last().x, bottom)
                close()
            }
            if (skyImages != null) {
                val rainCurve = SkyCurve(
                    FloatArray(hours.size) { pts[it].x }, FloatArray(hours.size) { rainAmountForCode(hours[it].weatherCode) },
                    0.75f * (if (pts.size > 1) (pts.last().x - pts.first().x) / (pts.size - 1) else 30.dp.toPx())
                )
                drawWater(skyImages, rainCurve.takeIf { rainActive }, fill, line.first().x, line.last().x, pts.minOf { it.y } - 2.dp.toPx(), bottom)
                if (rainActive) {
                    fun surfaceY(x: Float): Float {
                        for (i in 1 until line.size) if (x <= line[i].x) {
                            val a = line[i - 1]; val b = line[i]
                            return a.y + (b.y - a.y) * ((x - a.x) / (b.x - a.x).coerceAtLeast(1e-3f)).coerceIn(0f, 1f)
                        }
                        return line.last().y
                    }
                    drawRainRipples(fill, rainCurve, ::surfaceY, line.first().x, line.last().x, bottom, clock.value)
                }
            }
            for (i in 1 until line.size) {
                val p = line[i - 1]; val c = line[i]; val mx = (p.x + c.x) / 2f
                val seg = Path().apply { moveTo(p.x, p.y); cubicTo(mx, p.y, mx, c.y, c.x, c.y) }
                val ri = (i - 2).coerceIn(0, (ratings.size - 1).coerceAtLeast(0))
                val rating = ratings.getOrElse(ri) { SlotRating(50, false) }
                drawPath(seg, color = conditionBand(rating).color(), style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round))
            }
            // L'heure qu'il est : un petit point discret, sans texte, qui glisse sur la courbe. Le toucher choisit l'heure comme ailleurs.
            if (nowHour != null) {
                val nx = xOf(nowHour)
                if (nx >= line.first().x && nx <= line.last().x) {
                    val c = Offset(nx, curveYAt(line, nx))
                    drawCircle(halo.copy(alpha = 0.85f), radius = 4.dp.toPx(), center = c)
                    drawCircle(nacre.copy(alpha = 0.75f), radius = 2.6.dp.toPx(), center = c)
                    // « Now » au-dessus du point, tant que l'heure en cours n'est pas l'heure choisie : une fois choisie, ce sont la
                    // taille et la période qui s'affichent à sa place. On l'omet s'il toucherait la taille de l'heure choisie.
                    val nowIdx = hours.indices.minByOrNull { kotlin.math.abs(hours[it].rawTime.hour - nowHour) }
                    val selX = pts.getOrNull(selectedIndex)?.x
                    if (selectedIndex != nowIdx && (selX == null || kotlin.math.abs(selX - nx) > 26.dp.toPx())) {
                        val style = TextStyle(color = Color.White, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                        val w = textMeasurer.measure("Now", style).size.width
                        val tx = (nx - w / 2f).coerceIn(2.dp.toPx(), size.width - w - 2.dp.toPx())
                        drawHaloTextAtBaseline(textMeasurer, "Now", style, weekTextHalo, tx, c.y - 8.dp.toPx())
                    }
                }
            }
        }

        // Graduations par-dessus la courbe, sur un fond léger (comme la vue semaine).
        // Même style que la taille et la période : blanc, gras, contour sombre, sans encart derrière.
        val axisLabelStyle = TextStyle(color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        gridValues.forEach { v ->
            drawHaloTextAtBaseline(textMeasurer, formatAxisHeight(v), axisLabelStyle, weekTextHalo, 4.dp.toPx(), yOf(v) - 3.dp.toPx())
        }

        // Le petit point nacré, avec la taille au-dessus et la période en dessous (contour de la couleur du fond, pas d'encart).
        if (selectedIndex in pts.indices) {
            val p = pts[selectedIndex]
            val hourly = hours[selectedIndex]
            // Même tracé que la semaine (drawHaloTextAtBaseline) : l'ancien double dessin laissait seul le contour sombre.
            fun haloText(text: String, cx: Float, topY: Float, sp: Float, weight: FontWeight, color: Color, outline: Color) {
                val style = TextStyle(color = color, fontSize = sp.sp, fontWeight = weight)
                val layout = textMeasurer.measure(text, style)
                val x = (cx - layout.size.width / 2f).coerceIn(2.dp.toPx(), size.width - layout.size.width - 2.dp.toPx())
                drawHaloTextAtBaseline(textMeasurer, text, style, outline, x, topY + layout.firstBaseline)
            }
            // Même style que la vue semaine (heightTextPaint / periodTextPaint) : blanc avec contour sombre, dans les deux thèmes.
            // La taille est au-dessus de la courbe (sur le ciel), la période dessous (sur l'eau).
            haloText(formatDecimal(hourly.waveHeight, 1, ',') + " m", p.x, p.y - 26.dp.toPx(), 12f, FontWeight.Bold, Color.White, weekTextHalo)
            haloText("${hourly.wavePeriod.roundToInt()} s", p.x, p.y + 8.dp.toPx(), 11f, FontWeight.Bold, Color.White.copy(alpha = 0.92f), weekTextHalo)
            drawCircle(nacre.copy(alpha = 0.9f), radius = 3.6.dp.toPx(), center = p)
            drawCircle(nacre.copy(alpha = 0.35f), radius = 6.dp.toPx(), center = p, style = Stroke(1.2.dp.toPx()))
        }
    }
}

/**
 * Hauteur de la courbe de houle à la position [x] : même tracé que le chemin dessiné (une cubique à tangentes horizontales
 * entre deux points, dont l'abscisse suit 1,5t − 1,5t² + t³ et l'ordonnée 3t² − 2t³), pour que le point colle à la courbe.
 */
internal fun curveYAt(line: List<Offset>, x: Float): Float {
    for (i in 1 until line.size) {
        val p = line[i - 1]; val c = line[i]
        if (x > c.x) continue
        val dx = c.x - p.x
        if (dx < 1e-3f) return c.y
        val u = ((x - p.x) / dx).coerceIn(0f, 1f)
        var lo = 0f; var hi = 1f
        repeat(24) {
            val t = (lo + hi) / 2f
            if (1.5f * t - 1.5f * t * t + t * t * t < u) lo = t else hi = t
        }
        val t = (lo + hi) / 2f
        return p.y + (c.y - p.y) * (3f * t * t - 2f * t * t * t)
    }
    return line.last().y
}

/** Heure la plus proche d'une position horizontale : les points de la courbe vont de 0 à la largeur (n - 1 intervalles). */
internal fun hourIndexAt(x: Float, width: Float, count: Int): Int {
    if (count <= 1 || width <= 0f) return 0
    return (x / (width / (count - 1))).roundToInt().coerceIn(0, count - 1)
}

/**
 * Formate une hauteur de houle pour l'axe, a la francaise (virgule) et sans decimale
 * inutile sur les valeurs rondes : 0.5 -> "0,5", 1.0 -> "1", 1.5 -> "1,5".
 * Les paliers en quart de metre (0.25 / 0.75...) gardent 2 decimales pour rester
 * exacts : arrondir "%.1f" en HALF_UP afficherait a tort "0,3" pour 0.25.
 */
/**
 * Échelle fixe des hauteurs de houle (déroulé de la journée et semaine) : un mètre a toujours la même hauteur à l'écran, d'une
 * semaine à l'autre. Une houle de 2 m arrive à la part d'eau réglée ([SkyGraphSettings.waterFraction]). Au-delà de 2 m l'échelle
 * se resserre : de 2 à 6 m, moitié moins de hauteur par mètre ; au-delà de 6 m, un quart. Renvoie la position de [v] mètres
 * entre 0 (bas du graphique) et 1 (haut).
 */
internal fun swellAxisFraction(v: Double): Float {
    val a = SkyGraphSettings.waterFraction / 2.0
    val f = when {
        v <= 2.0 -> a * v
        v <= 6.0 -> a * 2.0 + a * 0.5 * (v - 2.0)
        else -> a * 4.0 + a * 0.25 * (v - 6.0)
    }
    return f.coerceIn(0.0, 1.0).toFloat()
}

/** Début de la partie resserrée de l'échelle : les lignes au-dessus sont en pointillés. */
internal const val SWELL_AXIS_BREAK = 2.0

/**
 * Graduations de l'axe : tous les 0,5 m jusqu'à 2 m, tous les mètres jusqu'à 6 m, tous les 2 m au-delà. On s'arrête à la
 * première qui atteint [rawMax] : pas de « 2 » si la houle ne dépasse pas 1,5 m.
 */
internal fun swellAxisGrid(rawMax: Double): List<Double> {
    val all = ArrayList<Double>()
    var v = 0.5
    while (v <= 2.0 + 1e-9) { all.add(v); v += 0.5 }
    for (m in 3..6) all.add(m.toDouble())
    var w = 8.0
    while (w <= 14.0) { all.add(w); w += 2.0 }
    var i = 0
    while (i < all.size - 1 && all[i] < rawMax - 1e-9) i++
    return all.subList(0, i + 1).toList()
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
 * Lever / coucher de soleil dessiné à la main : horizon, demi-soleil et flèche (vers le haut = lever, vers le bas =
 * coucher), centré sur [center] ; [u] est l'unité de dessin (1,2 dp donne une icône d'environ 17 dp de large).
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSunEvent(center: Offset, isSunrise: Boolean, color: Color, u: Float) {
    val stroke = Stroke(width = 1.1f * u, cap = StrokeCap.Round)
    val horizonY = center.y + 3f * u
    drawLine(color, Offset(center.x - 7f * u, horizonY), Offset(center.x + 7f * u, horizonY), strokeWidth = stroke.width, cap = StrokeCap.Round)
    drawArc(
        color = color, startAngle = 180f, sweepAngle = 180f, useCenter = false,
        topLeft = Offset(center.x - 4.5f * u, horizonY - 4.5f * u), size = Size(9f * u, 9f * u), style = stroke
    )
    val tip = center.y + (if (isSunrise) -8f else -1f) * u
    val tail = center.y + (if (isSunrise) -1f else -8f) * u
    drawLine(color, Offset(center.x, tail), Offset(center.x, tip), strokeWidth = stroke.width, cap = StrokeCap.Round)
    val back = if (isSunrise) 2.5f else -2.5f
    val head = Path().apply {
        moveTo(center.x - 2.5f * u, tip + back * u)
        lineTo(center.x, tip)
        lineTo(center.x + 2.5f * u, tip + back * u)
    }
    drawPath(head, color, style = stroke)
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
