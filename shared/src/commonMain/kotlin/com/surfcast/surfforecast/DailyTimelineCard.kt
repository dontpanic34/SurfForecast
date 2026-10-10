@file:Suppress("SpellCheckingInspection")
package com.surfcast.surfforecast

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
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
private const val WIND_ROW_H = 40
private const val PLOT_H = 112
private const val PLOT_TOP = 14      // haut de la zone de courbe
private const val PLOT_BOTTOM = 104  // bas de la zone de courbe
private const val SKY_EXT = 36       // le ciel déborde au-dessus du graphique, jusque vers le vent
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
    // Plus grosse houle de la semaine (échelle partagée avec la vue semaine).
    scaleRawMax: Double? = null,
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
                        sunrise = sunrise, sunset = sunset, ax0 = ax0, ax1 = ax1, scaleRawMax = scaleRawMax, windUnit = windUnit,
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
    scaleRawMax: Double?,
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

    // Une indication de vent par heure d'ensoleillement : heures paires, plus l'heure choisie, sans heure imposée ; on masque
    // ce qui toucherait sa voisine (mesuré en pixels).
    val sunnyHours = hours.map { it.rawTime.hour }.filter { it >= ceil(sunrise).toInt() && it <= floor(sunset).toInt() }
    val windFont = if (sunnyHours.size > 12) 7.4f else if (sunnyHours.size > 9) 8.6f else 9.6f

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
                val windMinGap = windFont * 2.5f + 2f
                val windHours: List<Int> = run {
                    val cand = ArrayList<Pair<Int, Int>>() // heure, priorité
                    sunnyHours.filter { it % 2 == 0 }.forEach { cand.add(it to 1) }
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
                        val dirFr = SurfUnitsHelper.formatCardinalFr(hourly.windDirectionStr)
                        val color = SurfUnitsHelper.getSurfWindColor(dirFr, effectiveWindKmh(hourly).roundToInt())
                        val textColor = SurfUnitsHelper.windTextColor(color, isDark)
                        val rotation = (SurfUnitsHelper.cardinalToDegrees(dirFr) + 180f) % 360f
                        val itemW = 34.dp
                        Column(
                            modifier = Modifier.offset { IntOffset((xOf(h.toFloat()) - itemW.toPx() / 2f).roundToInt(), 0) }.width(itemW),
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
                        }
                    }
                }

                // --- Courbe sur son fond météo ---
                val rawMax = maxOf(scaleRawMax ?: 0.0, hours.maxOfOrNull { it.waveHeight } ?: 1.0).coerceAtLeast(0.3)
                val (niceMax, gridValues) = swellAxisScale(rawMax)
                val displayMax = (niceMax * 1.3).toFloat()
                Box(modifier = Modifier.fillMaxWidth().height(PLOT_H.dp)) {
                    val extPx = with(density) { SKY_EXT.dp.toPx() }
                    val topPx = with(density) { PLOT_TOP.dp.toPx() }
                    val bottomPx = with(density) { PLOT_BOTTOM.dp.toPx() }
                    SkyCanvas(
                        modifier = Modifier.fillMaxWidth().height((PLOT_H + SKY_EXT).dp).offset(y = (-SKY_EXT).dp),
                        isDark = isDark
                    ) { size ->
                        val xs = FloatArray(hours.size) { xOf(hours[it].rawTime.hour.toFloat()) }
                        val smooth = with(density) { 6.dp.toPx() }
                        val cover = SkyCurve(xs, FloatArray(hours.size) { hours[it].cloudCover / 100f }, smooth)
                        val rain = SkyCurve(xs, FloatArray(hours.size) { rainAmountForCode(hours[it].weatherCode) }, smooth)
                        val snow = SkyCurve(xs, FloatArray(hours.size) { snowAmountForCode(hours[it].weatherCode) }, smooth)
                        // Le soleil suit l'heure choisie : il se lève en bas à l'heure du lever, monte au zénith à la mi-journée
                        // près du vent, puis redescend et se couche en bas à l'heure du coucher.
                        val suns = ArrayList<SunSpot>()
                        if (selH != null) {
                            val alt = sunAltitude(selH, sunrise, sunset)
                            if (alt >= 0f) {
                                val sizePx = with(density) { 52.dp.toPx() }
                                val horizon = bottomPx - with(density) { 26.dp.toPx() }
                                val zenith = topPx - with(density) { 12.dp.toPx() }
                                val cy = horizon - (horizon - zenith) * alt
                                suns.add(SunSpot(xOf(selH), sizePx, extPx + cy - sizePx / 2f))
                            }
                        }
                        SkyScene(
                            left = pad, right = size.width - pad, top = extPx + topPx, bottom = extPx + bottomPx,
                            cover = cover, rain = rain, snow = snow, suns = suns,
                            animate = hours.any { rainAmountForCode(it.weatherCode) > 0f || snowAmountForCode(it.weatherCode) > 0f }
                        )
                    }
                    DailyCurveCanvas(
                        modifier = Modifier.fillMaxSize(), hours = hours, ratings = ratings, selectedIndex = selectedIndex,
                        gridValues = gridValues, displayMax = displayMax, onSurfaceColor = onSurfaceColor, isDark = isDark,
                        nacre = nacre, textMeasurer = textMeasurer, xOf = ::xOf
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

            // --- Surbrillance de l'heure choisie : un fondu, plus coloré au milieu (vers la courbe), qui s'estompe vers le haut,
            // le bas et les côtés, sans bord visible. ---
            if (selH != null) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val hourW = (size.width - 2 * pad) / (ax1 - ax0)
                    val half = hourW * 0.95f
                    val cxp = xOf(selH)
                    val a = if (isDark) 0.30f else 0.24f
                    val base = nacre
                    val stops = arrayOf(
                        0.00f to base.copy(alpha = 0f), 0.10f to base.copy(alpha = a * 0.04f), 0.24f to base.copy(alpha = a * 0.20f),
                        0.38f to base.copy(alpha = a * 0.55f), 0.52f to base.copy(alpha = a), 0.62f to base.copy(alpha = a * 0.70f),
                        0.74f to base.copy(alpha = a * 0.35f), 0.86f to base.copy(alpha = a * 0.10f), 1.00f to base.copy(alpha = 0f)
                    )
                    val bounds = androidx.compose.ui.geometry.Rect(cxp - half, 0f, cxp + half, size.height)
                    drawIntoCanvas { canvas ->
                        canvas.saveLayer(bounds, Paint())
                        drawRect(brush = Brush.verticalGradient(*stops, startY = 0f, endY = size.height), topLeft = bounds.topLeft, size = bounds.size)
                        drawRect(
                            brush = Brush.horizontalGradient(
                                0f to Color.Transparent, 0.35f to Color.Black, 0.65f to Color.Black, 1f to Color.Transparent,
                                startX = bounds.left, endX = bounds.right
                            ),
                            topLeft = bounds.topLeft, size = bounds.size, blendMode = BlendMode.DstIn
                        )
                        canvas.restore()
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
    displayMax: Float,
    onSurfaceColor: Color,
    isDark: Boolean,
    nacre: Color,
    textMeasurer: TextMeasurer,
    xOf: (Float) -> Float
) {
    val primaryFill = AppColors.TideHigh
    val axisTextStyle = TextStyle(color = onSurfaceColor.copy(alpha = 0.55f), fontSize = 10.sp, fontWeight = FontWeight.Normal)
    val labelBg = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f)
    val halo = if (isDark) Color(0xFF0B121A) else Color.White
    val labelColor = if (isDark) Color.White else Color(0xFF14202B)
    Canvas(modifier = modifier) {
        val top = PLOT_TOP.dp.toPx()
        val bottom = PLOT_BOTTOM.dp.toPx()
        fun yOf(v: Double): Float = bottom - (bottom - top) * (v / displayMax).toFloat().coerceIn(0f, 1.2f)

        gridValues.forEach { v ->
            val y = yOf(v)
            drawLine(onSurfaceColor.copy(alpha = 0.10f), Offset(0f, y), Offset(size.width, y), strokeWidth = 0.8.dp.toPx())
        }

        val pts = hours.map { Offset(xOf(it.rawTime.hour.toFloat()), yOf(it.waveHeight)) }
        if (pts.size >= 2) {
            val fill = Path().apply {
                moveTo(pts.first().x, bottom)
                lineTo(pts.first().x, pts.first().y)
                for (i in 1 until pts.size) {
                    val p = pts[i - 1]; val c = pts[i]; val mx = (p.x + c.x) / 2f
                    cubicTo(mx, p.y, mx, c.y, c.x, c.y)
                }
                lineTo(pts.last().x, bottom)
                close()
            }
            for (i in 1 until pts.size) {
                val p = pts[i - 1]; val c = pts[i]; val mx = (p.x + c.x) / 2f
                val seg = Path().apply { moveTo(p.x, p.y); cubicTo(mx, p.y, mx, c.y, c.x, c.y) }
                val rating = ratings.getOrElse(i - 1) { ratings.getOrElse(i) { SlotRating(50, false) } }
                drawPath(seg, color = conditionBand(rating).color(), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            }
        }

        // Graduations par-dessus la courbe, sur un fond léger (comme la vue semaine).
        gridValues.forEach { v ->
            val y = yOf(v)
            val layout = textMeasurer.measure(formatAxisHeight(v), axisTextStyle)
            val labelTop = y - layout.size.height - 1.dp.toPx()
            drawRoundRect(
                labelBg, Offset(2.dp.toPx(), labelTop), Size(layout.size.width + 4.dp.toPx(), layout.size.height.toFloat()),
                CornerRadius(3.dp.toPx())
            )
            drawText(layout, topLeft = Offset(4.dp.toPx(), labelTop))
        }

        // Le petit point nacré, avec la taille au-dessus et la période en dessous (contour de la couleur du fond, pas d'encart).
        if (selectedIndex in pts.indices) {
            val p = pts[selectedIndex]
            val hourly = hours[selectedIndex]
            fun haloText(text: String, cx: Float, topY: Float, sp: Float, weight: FontWeight) {
                val style = TextStyle(color = nacre, fontSize = sp.sp, fontWeight = weight)
                val outline = TextStyle(
                    color = halo, fontSize = sp.sp, fontWeight = weight,
                    drawStyle = Stroke(width = 3.dp.toPx(), join = StrokeJoin.Round)
                )
                val layout = textMeasurer.measure(text, style)
                val x = (cx - layout.size.width / 2f).coerceIn(2.dp.toPx(), size.width - layout.size.width - 2.dp.toPx())
                drawText(textMeasurer, text, Offset(x, topY), outline)
                drawText(textMeasurer, text, Offset(x, topY), style)
            }
            haloText(formatDecimal(hourly.waveHeight, 1, ',') + " m", p.x, p.y - 26.dp.toPx(), 12f, FontWeight.ExtraBold)
            haloText("${hourly.wavePeriod.roundToInt()} s", p.x, p.y + 8.dp.toPx(), 10.5f, FontWeight.Bold)
            drawCircle(nacre.copy(alpha = 0.9f), radius = 3.6.dp.toPx(), center = p)
            drawCircle(nacre.copy(alpha = 0.35f), radius = 6.dp.toPx(), center = p, style = Stroke(1.2.dp.toPx()))
        }
    }
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
