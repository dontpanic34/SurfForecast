@file:Suppress("SpellCheckingInspection")
package com.surfcast.surfforecast

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.input.pointer.positionChange
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.surfcast.surfforecast.ui.theme.AppColors
import kotlinx.datetime.LocalDate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin


// Dimensions (dp) du déroulé : mêmes proportions que la maquette validée.
private const val SELECTED_WIND_DROP = 9 // la colonne de vent de l'heure choisie descend de ce nombre de dp pour la repérer
private const val WIND_ROW_H = 46 + SELECTED_WIND_DROP
private const val PLOT_TOP = 18      // haut de la zone de courbe (dp)
private const val PLOT_BOTTOM_MARGIN = 8 // marge sous la courbe (dp) ; la hauteur du graphique vient des réglages (SkyGraphSettings)
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
    onOpenModelSettings: () -> Unit = {},
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
    modifier: Modifier = Modifier,
    // Motivation du jour (« Prudent » / « Normal » / « Motivé »), un tap la change ; null = pas de réglage affiché.
    motivationLabel: String? = null,
    onMotivationSelect: (Int) -> Unit = {},
    // La planche conseillée pour une heure (d'après les réponses du matériel) ; null = pas d'affichage.
    boardOf: ((HourlyUiModel) -> String?)? = null
) {
    if (groupedByDate.isEmpty()) return

    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val isDark = isDarkSurfaceTheme()
    val density = LocalDensity.current

    val sun = dailySunInfo[selectedDate]
    val sunrise = sun?.let { it.sunrise.hour + it.sunrise.minute / 60f } ?: 7f
    val sunset = sun?.let { it.sunset.hour + it.sunset.minute / 60f } ?: 20f
    // Le graphique couvre toute la journée, avec la même ligne de temps que la barre d'Actuellement (heures du jour, nuit en blocs) : le curseur est au même endroit partout.
    val ax0 = 0f
    val ax1 = 24f
    val dayTides = dailyTides[selectedDate]?.let { tidesForDay(it, 0f, 24f) } ?: emptyList()
    val tideCurve = dailyTides[selectedDate]?.let { tideCurveMarks(it) } ?: emptyList()
    val allDay = (groupedByDate[selectedDate] ?: emptyList()).sortedBy { it.rawTime }
    val curveHours = allDay

    // Notation avec la mémoire du vent : la mer ne devient pas « glacée » une heure après 30 km/h de vent de mer.
    val scoring = hoursWithWindMemory(selectedDate, groupedByDate).associateBy { it.rawTime }
    val ratings = curveHours.map { shown ->
        calculateSlotRating(scoring[shown.rawTime] ?: shown, idealSwellDirection, surferLevel, displayedWind = shown)
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
        Box {
        Row(modifier = Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 4.dp)) { CardInfoButton("day"); CollapseChevron(isCollapsed, onToggleCollapse) }
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
            if (selectedIndex in ratings.indices) {
                var showVoices by remember { mutableStateOf(false) }
                if (showVoices) ModelVoicesDialog(curveHours[selectedIndex], windUnit, onOpenModelSettings) { showVoices = false }
                val shownRating = ratings[selectedIndex]
                val shownBand = conditionBand(shownRating)
                Column(modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 60.dp, top = 2.dp, bottom = 2.dp)) {
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
                            bandLabelWithTrend(shownRating, ratings.getOrNull(selectedIndex - 1)), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
                            color = shownBand.textColor(), maxLines = 1
                        )
                        // Quand les trois modèles de vagues se contredisent nettement sur la journée, on le dit sobrement.
                        val daySpread = curveHours.filter { it.modelSpread > 0.0 }.map { it.modelSpread }.average().takeIf { !it.isNaN() } ?: 0.0
                        if (daySpread >= MODELS_SPLIT_SPREAD) {
                            Text(
                                "Modèles partagés ›", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF9A1F), maxLines = 1,
                                modifier = Modifier.clickable { showVoices = true }
                            )
                        }
                        val board = curveHours.getOrNull(selectedIndex)?.let { boardOf?.invoke(it) }
                        if (board != null) {
                            Text("Planche : $board", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                        }
                    }
                    // La phrase, sous l'heure et le libellé.
                    if (shownRating.why.isNotBlank()) {
                        Text(
                            shownRating.why, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            color = onSurfaceColor.copy(alpha = 0.7f), maxLines = 2, modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    // Coefficient puis les deux marées de la journée, sur une ligne sous la jauge.
                    dailyTides[selectedDate]?.let { info ->
                        val others = if (SkyGraphSettings.tideAll) info.extrema.filter { e -> dayTides.none { it.time == e.time } } else emptyList()
                        TideLine(dayTides, info.coefficient, onSurfaceColor, others)
                    }
                    if (motivationLabel != null) {
                        Row(modifier = Modifier.padding(top = 6.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Motivation", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = onSurfaceColor.copy(alpha = 0.55f))
                            Spacer(modifier = Modifier.width(8.dp))
                            // Trois états visibles d'un coup : on touche celui qu'on veut (Flemme, Normal, Motivé).
                            Row(modifier = Modifier.clip(RoundedCornerShape(50)).background(onSurfaceColor.copy(alpha = 0.08f)).padding(2.dp)) {
                                listOf(-1, 0, 1).forEach { m ->
                                    val on = ToleranceSettings.label(m) == motivationLabel
                                    Text(
                                        ToleranceSettings.label(m), fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                        color = if (on) MaterialTheme.colorScheme.onPrimaryContainer else onSurfaceColor.copy(alpha = 0.7f),
                                        modifier = Modifier.clip(RoundedCornerShape(50))
                                            .background(if (on) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                            .clickable { onMotivationSelect(m) }.padding(horizontal = 10.dp, vertical = 3.dp)
                                    )
                                }
                            }
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
                        sunrise = sunrise, sunset = sunset, sunInfo = sun, ax0 = ax0, ax1 = ax1, windUnit = windUnit,
                        isDark = isDark, onSurfaceColor = onSurfaceColor, tideMarks = tideMarks(dayTides, ax0, ax1), tideCurve = tideCurve, coefValue = dailyTides[selectedDate]?.coefficient, tidesEstimated = dailyTides[selectedDate]?.estimated == true, tidesAbroad = dailyTides[selectedDate]?.abroad == true,
                        beachFacing = idealSwellDirection, surferLevel = surferLevel
                    )
                }
            }
            if (!isCollapsed) CardSettingsPanel("day")
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
    sunInfo: DailySunInfo?,
    ax0: Float,
    ax1: Float,
    windUnit: String,
    isDark: Boolean,
    onSurfaceColor: Color,
    tideMarks: List<TideMark>,
    tideCurve: List<TideMark> = emptyList(),
    coefValue: Int? = null,
    tidesEstimated: Boolean = false,
    tidesAbroad: Boolean = false,
    beachFacing: Int?,
    surferLevel: String
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
    val windFont = 10f // texte du vent : lisible, une colonne mesure environ 30 dp

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val wPx = with(density) { maxWidth.toPx() }
        val pad = with(density) { SIDE_PAD.dp.toPx() }
        // Même ligne de temps que la barre d'Actuellement : heures du jour une par une, nuit par blocs de 3 heures.
        val cells = remember(hours, sunInfo) { buildTimeCells(hours, sunInfo) }
        fun xOf(h: Float) = pad + (wPx - 2 * pad) * timeFraction(cells, hours, h.toDouble())
        // Le milieu de l'heure [h, h+1) : là où se pose son point, comme son curseur dans la barre d'Actuellement.
        fun xHr(h: Float) = xOf(h + 0.5f)

        fun pickHour(x: Float) {
            if (cells.isEmpty()) return
            val frac = ((x - pad) / (wPx - 2 * pad)).coerceIn(0f, 0.9999f)
            val cell = cells[(frac * cells.size).toInt().coerceIn(0, cells.lastIndex)]
            hours.getOrNull(cell.representativeIndex())?.let(onHourSelected)
        }

        Box(
            modifier = Modifier.fillMaxWidth()
                // Le doigt se promène sur le graphique et l'heure le suit : dès que le geste est plutôt horizontal on le garde (la page ne
                // défile plus) ; un geste plutôt vertical est laissé à la liste sans changer l'heure ; un simple toucher choisit l'heure.
                .pointerInput(hours, cells) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var locked = false
                        var vertical = false
                        var total = androidx.compose.ui.geometry.Offset.Zero
                        val slop = viewConfiguration.touchSlop
                        while (true) {
                            val event = awaitPointerEvent()
                            val ch = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) break
                            total += ch.positionChange()
                            if (!locked && total.getDistance() > slop) {
                                if (kotlin.math.abs(total.x) > kotlin.math.abs(total.y)) locked = true else { vertical = true; break }
                            }
                            if (locked) { pickHour(ch.position.x); ch.consume() }
                        }
                        if (!locked && !vertical) pickHour(down.position.x)
                    }
                }
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // --- Vent : une flèche par case de la ligne de temps (heure du jour ou bloc de nuit), comme la barre d'Actuellement ---
                Box(modifier = Modifier.fillMaxWidth().height(WIND_ROW_H.dp)) {
                    val inner = wPx - 2 * pad
                    val cellW = if (cells.isEmpty()) 0f else inner / cells.size
                    for ((k, cell) in cells.withIndex()) {
                        val idx = cell.representativeIndex()
                        val hourly = hours.getOrNull(idx) ?: continue
                        val isSel = selectedIndex in cell.first..cell.last
                        key(k) {
                            val dirFr = SurfUnitsHelper.formatCardinalFr(hourly.windDirectionStr)
                            val color = windFeelColor(hourly, beachFacing, surferLevel)
                            val textColor = SurfUnitsHelper.windTextColor(color, isDark)
                            val rotation = (SurfUnitsHelper.cardinalToDegrees(dirFr) + 180f) % 360f
                            // L'heure choisie : colonne plus large (direction et vitesse), un peu plus bas, soulignée ; les autres : flèche et vitesse.
                            val itemW = if (isSel) 36.dp else with(density) { cellW.toDp() }
                            val fs = if (isSel) windFont else 8.5f
                            val drop by animateDpAsState(if (isSel) SELECTED_WIND_DROP.dp else 0.dp, label = "windDrop")
                            val cx = pad + cellW * (k + 0.5f)
                            Column(
                                modifier = Modifier.offset { IntOffset((cx - itemW.toPx() / 2f).roundToInt(), drop.roundToPx()) }.width(itemW),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                NavArrow(color, rotation, (fs * 1.5f * windArrowScale(windFeelKmh(hourly))).dp)
                                if (isSel) Text(dirFr, fontSize = fs.sp, fontWeight = FontWeight.ExtraBold, color = textColor, maxLines = 1, lineHeight = (fs + 2f).sp)
                                Text(
                                    SurfUnitsHelper.formatWindValue(windFeelKmh(hourly), windUnit), fontSize = fs.sp,
                                    fontWeight = FontWeight.ExtraBold, color = textColor, maxLines = 1, softWrap = false, lineHeight = (fs + 2f).sp
                                )
                                if (isSel) Box(modifier = Modifier.padding(top = 2.dp).width(16.dp).height(2.dp).background(textColor, RoundedCornerShape(1.dp)))
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
                        val xs = FloatArray(hours.size) { xHr(hours[it].rawTime.hour.toFloat()) }
                        // Lissage sur plusieurs heures : le ciel et la pluie passent d'un état à l'autre sans colonne.
                        val hourPx = if (xs.size > 1) (xs.last() - xs.first()) / (hours.last().rawTime.hour - hours.first().rawTime.hour).coerceAtLeast(1)
                            else with(density) { 30.dp.toPx() }
                        val cover = SkyCurve(xs, FloatArray(hours.size) { hours[it].cloudCover / 100f }, 1.6f * hourPx)
                        val rain = SkyCurve(xs, FloatArray(hours.size) { rainAmountForCode(hours[it].weatherCode) }, 1.0f * hourPx)
                        val snow = SkyCurve(xs, FloatArray(hours.size) { snowAmountForCode(hours[it].weatherCode) }, 1.0f * hourPx)
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
                                suns.add(SunSpot(xHr(selH), sizePx, extPx + cy - sizePx / 2f))
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

                // --- Encart Marée : une ligne (sens, coefficient ou hauteur d'eau, prochaine marée), repliable ; dessous, la courbe sur 24 h
                // régulières (son axe à elle, pas celui du graphique : la nuit n'y est pas comprimée). ---
                if (tideCurve.size >= 2) {
                    val open = SkyGraphSettings.tideOpen
                    val rising = Color(0xFF4A9BF0)
                    val falling = Color(0xFF38C976)
                    val curveColor = if (isDark) Color(0xFF9FD3FF) else Color(0xFF2F6EA8)
                    val at = (selH?.plus(0.5f) ?: nowHour ?: 12f).coerceIn(0f, 24f)
                    fun seg(h: Float): Int = tideCurve.indexOfLast { it.hour <= h }.coerceIn(0, tideCurve.size - 2)
                    fun frac(h: Float): Float {
                        val i = seg(h)
                        return ((h - tideCurve[i].hour) / (tideCurve[i + 1].hour - tideCurve[i].hour).coerceAtLeast(1e-3f)).coerceIn(0f, 1f)
                    }
                    fun ease(f: Float) = ((1.0 - kotlin.math.cos(PI * f)) / 2.0).toFloat()
                    // Niveau entre 0 (plein bas) et 1 (plein haut) : un demi-cosinus entre deux marées.
                    fun level(h: Float): Float {
                        val e = ease(frac(h))
                        return if (tideCurve[seg(h) + 1].high) e else 1f - e
                    }
                    val up = tideCurve[seg(at) + 1].high
                    val next = tideCurve.firstOrNull { it.hour > at }
                    // La hauteur d'eau, seulement quand il n'y a pas de coefficient (Espagne, Portugal).
                    val waterM: Double? = if (coefValue == null || tidesAbroad) {
                        val i = seg(at); val a0 = tideCurve[i].height; val b0 = tideCurve[i + 1].height
                        if (a0 != null && b0 != null) a0 + (b0 - a0) * ease(frac(at)) else null
                    } else null
                    val sentence = buildAnnotatedString {
                        withStyle(SpanStyle(color = if (up) rising else falling)) { append(tr(if (up) "Montant" else "Descendant")) }
                        coefValue?.let { append(" · " + (if (tidesEstimated) "≈" else "") + it) }
                        waterM?.let { append(" · " + (if (tidesEstimated || tidesAbroad) "≈" else "") + formatDecimal(it, 1).replace('.', ',') + " m") }
                        if (next != null) {
                            val d = next.hour - at
                            val dh = d.toInt(); val dm = ((d - dh) * 60).roundToInt().coerceAtMost(59)
                            append(" · " + tr(if (next.high) "Plein haut dans" else "Plein bas dans") + " " + (if (dh == 0) "$dm min" else dh.toString() + " h " + dm.toString().padStart(2, '0')))
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { SkyGraphSettings.chooseTideOpen(!open) }.padding(horizontal = SIDE_PAD.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(tr("Marée") + " ", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = onSurfaceColor.copy(alpha = 0.6f), maxLines = 1)
                        Text(sentence, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = onSurfaceColor, maxLines = 1, modifier = Modifier.weight(1f))
                        Text(if (open) "▴" else "▾", fontSize = 12.sp, color = onSurfaceColor.copy(alpha = 0.5f))
                    }
                    if (open) {
                        val tideStyle = TextStyle(color = onSurfaceColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        val tideHalo = if (isDark) Color(0xCC0B121A) else Color(0xCCFFFFFF)
                        Canvas(modifier = Modifier.fillMaxWidth().height((SkyGraphSettings.curveDp + 24).dp)) {
                            val padPx = SIDE_PAD.dp.toPx()
                            fun lx(h: Float) = padPx + (size.width - 2 * padPx) * (h / 24f)
                            val top = 12.dp.toPx()
                            val curveH = SkyGraphSettings.curveDp.dp.toPx()
                            val y0 = top + curveH
                            fun yAt(h: Float) = y0 - curveH * level(h)
                            // Les heures de jour un peu plus claires que la nuit.
                            drawRect(Color.White.copy(alpha = if (isDark) 0.06f else 0.35f), Offset(lx(sunrise), 0f), androidx.compose.ui.geometry.Size(lx(sunset) - lx(sunrise), y0 + 6.dp.toPx()))
                            val line = Path()
                            for (i in 0..120) {
                                val h = 24f * i / 120
                                if (i == 0) line.moveTo(lx(h), yAt(h)) else line.lineTo(lx(h), yAt(h))
                            }
                            drawPath(line, curveColor, style = androidx.compose.ui.graphics.drawscope.Stroke(2.2.dp.toPx(), cap = StrokeCap.Round))
                            for (m in tideMarks) {
                                val cx = lx(m.hour); val cy = yAt(m.hour)
                                drawCircle(if (m.high) rising else falling, 3.dp.toPx(), Offset(cx, cy))
                                drawCircle(Color.White, 3.dp.toPx(), Offset(cx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(0.8.dp.toPx()))
                                val hh = m.hour.toInt()
                                val mm = ((m.hour - hh) * 60).roundToInt().coerceAtMost(59)
                                val txt = (if (tidesEstimated || tidesAbroad) "≈" else "") + hh.toString() + ":" + mm.toString().padStart(2, '0')
                                val w = textMeasurer.measure(txt, tideStyle).size.width.toFloat()
                                val tx = (cx - w / 2f).coerceIn(2.dp.toPx(), size.width - w - 2.dp.toPx())
                                drawHaloTextAtBaseline(textMeasurer, txt, tideStyle, tideHalo, tx, if (m.high) cy - 6.dp.toPx() else cy + 13.dp.toPx())
                            }
                            // Le point blanc : l'heure choisie (grand, avec un trait) ; l'heure actuelle (petit) quand elle est ailleurs.
                            fun dotAt(h: Float, r: Float, withLine: Boolean) {
                                val c = Offset(lx(h), yAt(h))
                                if (withLine) drawLine(Color.White.copy(alpha = 0.6f), Offset(c.x, 0f), Offset(c.x, y0 + 6.dp.toPx()), strokeWidth = 1.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.dp.toPx())))
                                drawCircle(Color.White, r, c)
                                drawCircle(Color(0xFF0B1519), r, c, style = androidx.compose.ui.graphics.drawscope.Stroke(1.3.dp.toPx()))
                            }
                            val selAt = selH?.plus(0.5f)
                            if (nowHour != null && (selAt == null || kotlin.math.abs(selAt - nowHour) > 0.4f)) dotAt(nowHour, 2.5.dp.toPx(), false)
                            if (selAt != null) dotAt(selAt, 4.dp.toPx(), true)
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
    nowHour: Float? = null,
    tideMarks: List<TideMark> = emptyList()
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
            // Un trait sombre dessous et un trait clair dessus : lisible sur le ciel clair comme sur l'eau.
            drawLine(Color(0xFF0B121A).copy(alpha = 0.28f), Offset(0f, y), Offset(size.width, y), strokeWidth = 2.6.dp.toPx(), pathEffect = dash)
            drawLine(Color.White.copy(alpha = 0.60f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1.2.dp.toPx(), pathEffect = dash)
        }

        val pts = hours.map { Offset(xOf(it.rawTime.hour + 0.5f), yOf(it.waveHeight)) }
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

        // Marées : un triangle dans l'eau, en bas de la courbe, à l'heure de chaque marée (bleu en haut = pleine mer, vert en bas = basse mer).
        for (m in tideMarks) {
            val c = Offset(xOf(m.hour), bottom - 11.dp.toPx())
            drawCircle(Color(0xFF0B121A).copy(alpha = 0.45f), radius = 9.dp.toPx(), center = c)
            drawTideTriangle(c, m.high, 9.dp.toPx())
        }

        // Le petit point nacré, avec la taille au-dessus et la période en dessous (contour de la couleur du fond, pas d'encart).
        if (selectedIndex in pts.indices) {
            val p = pts[selectedIndex]
            val hourly = hours[selectedIndex]
            // Même tracé que la semaine (drawHaloTextAtBaseline) : l'ancien double dessin laissait seul le contour sombre.
            fun haloText(text: String, cx: Float, topY: Float, sp: Float, weight: FontWeight, color: Color, outline: Color) {
                val style = TextStyle(color = color, fontSize = sp.sp, fontWeight = weight)
                val layout = textMeasurer.measure(text, style)
                // Jamais sur les graduations de gauche (1,5 / 1 / 0,5) : le texte se décale à droite, le point reste visible.
                val x = (cx - layout.size.width / 2f).coerceIn(38.dp.toPx(), size.width - layout.size.width - 2.dp.toPx())
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
internal fun swellAxisFraction(v: Double, fraction: Float = SkyGraphSettings.waterFraction): Float {
    val a = fraction / 2.0
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

private val TIDE_HIGH_COLOR = Color(0xFF4E9FEA)
private val TIDE_LOW_COLOR = Color(0xFF2BBF9E)

/** Petit triangle de marée : vers le haut et bleu pour la pleine mer, vers le bas et vert pour la basse mer. */
private fun DrawScope.drawTideTriangle(center: Offset, high: Boolean, size: Float) {
    val r = size / 2f
    val path = Path().apply {
        if (high) {
            moveTo(center.x, center.y - r)
            lineTo(center.x + r, center.y + r * 0.8f)
            lineTo(center.x - r, center.y + r * 0.8f)
        } else {
            moveTo(center.x, center.y + r)
            lineTo(center.x + r, center.y - r * 0.8f)
            lineTo(center.x - r, center.y - r * 0.8f)
        }
        close()
    }
    drawPath(path, color = if (high) TIDE_HIGH_COLOR else TIDE_LOW_COLOR)
    drawPath(path, color = Color(0xB30B121A), style = Stroke(width = 0.8.dp.toPx(), join = StrokeJoin.Round))
}

/**
 * Sous la jauge : le coefficient (pastille aux couleurs du vent : jaune doré sous 55, orange jusqu'à 80, rouge au-delà),
 * puis les deux marées de la journée, chacune avec son triangle et son heure.
 */
@Composable
private fun TideLine(tides: List<MareeExtremum>, coef: Int?, onSurfaceColor: Color, others: List<MareeExtremum> = emptyList()) {
    if (others.isEmpty()) return
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (others.isNotEmpty()) {
            Text("hors soleil", fontSize = 10.sp, color = onSurfaceColor.copy(alpha = 0.55f), maxLines = 1)
            for (t in others) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(modifier = Modifier.size(9.dp)) { drawTideTriangle(Offset(size.width / 2f, size.height / 2f), t.type == "PM", size.minDimension * 0.85f) }
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(t.time, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = onSurfaceColor.copy(alpha = 0.85f), maxLines = 1)
                }
            }
        }
    }
}

/** Écart moyen (en part de la hauteur) entre les modèles de vagues au-delà duquel on écrit « Modèles partagés ». */
private const val MODELS_SPLIT_SPREAD = 0.4
