package com.surfcast.surfforecast

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import com.surfcast.surfforecast.ui.theme.AppColors
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlin.math.roundToInt
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * Les conditions actuelles du spot, écrites comme un titre en haut à gauche : « Maintenant · Montalivet · 14 h », puis
 * houle, période, vent et l'état de la marée (montante, descendante, pleine haute, pleine basse). À droite, le libellé de la note de l'heure.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NowTideCard(
    hourlyModel: HourlyUiModel,
    tideInfo: DailyTideInfo?,
    windUnit: String,
    seaTemperature: Int? = null,
    // La note de l'heure, avec son libellé et sa tendance (null = pas de libellé).
    rating: SlotRating? = null,
    // La note de l'heure d'avant : elle donne le sens du libellé (« Bon à Correct » si ça baisse).
    previousRating: SlotRating? = null,
    spotName: String = "",
    // « 17:20 ⟳ » à droite du titre ; toucher relance les prévisions.
    updatedText: String = "",
    // La dernière mise à jour date de plus de 30 min : l'heure passe en orange.
    updateStale: Boolean = false,
    // Il fait nuit : pas de libellé de qualité (« Nuit »).
    isNight: Boolean = false,
    // La nuit en une phrase (houle, vent, ce qu'on trouvera au lever) ; null = on garde les tuiles.
    nightText: String? = null,
    // Le jour s'est levé depuis moins de 2 h : la nuit en une phrase reste affichée au-dessus des tuiles.
    dawnText: String? = null,
    // Dernière mesure de la bouée de référence : une ligne sous les tuiles, pour comparer la prévision à ce qu'on mesure en ce moment.
    buoy: BuoyReading? = null,
    // Dernière mesure d'une station Météo-France proche : le vent et la pluie réels, sous la ligne de la bouée.
    observed: ObservedNow? = null,
    // La ligne de temps du jour affiché : elle choisit l'heure des cartes Houle, Vent et Météo (vide = pas de ligne).
    timeHours: List<HourlyUiModel> = emptyList(),
    timeSun: DailySunInfo? = null,
    pickedHour: HourlyUiModel? = null,
    onPickHour: ((HourlyUiModel) -> Unit)? = null,
    onRefresh: (() -> Unit)? = null,
    boardOfDay: String? = null,
    // « + Session » : ouvre la saisie d'une session du journal (null = pas de bouton).
    onAddSession: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val dirFr = SurfUnitsHelper.formatCardinalFr(hourlyModel.windDirectionStr)
    val speed = SurfUnitsHelper.formatWindValue(effectiveWindKmh(hourlyModel), windUnit)
    val unit = SurfUnitsHelper.getWindUnitSymbol(windUnit)
    val title = buildString {
        append(tr("Actuellement")).append(" ").append(hourlyModel.rawTime.hour).append(" h")
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (timeHours.isNotEmpty() && pickedHour != null && onPickHour != null) {
            TimeRuler(hours = timeHours, sun = timeSun, selected = pickedHour, onHourSelected = onPickHour)
        }
        val open = SkyGraphSettings.nowOpen
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).clickable { SkyGraphSettings.chooseNowOpen(!open) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.onSurface.copy(alpha = 0.6f), maxLines = 1)
                // L'état de l'heure juste après le titre, sans fond : le mot, dans la couleur de la note.
                if (isNight) {
                    Text(" · " + tr("Nuit"), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = colors.onSurface.copy(alpha = 0.55f), maxLines = 1)
                } else if (rating != null) {
                    Text(" · " + bandLabelWithTrend(rating, previousRating), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = conditionBand(rating).textColor(), maxLines = 1)
                }
                Text(if (open) " ▴" else " ▾", fontSize = 12.sp, color = colors.onSurface.copy(alpha = 0.5f))
            }
            if (onAddSession != null) {
                Text(
                    tr("+ Session"), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.primary,
                    modifier = Modifier.padding(horizontal = 6.dp).clip(RoundedCornerShape(50)).background(colors.primary.copy(alpha = 0.15f))
                        .clickable(onClick = onAddSession).padding(horizontal = 9.dp, vertical = 2.dp)
                )
            }
            if (updatedText.isNotEmpty()) {
                Text(
                    "$updatedText ⟳", fontSize = 10.sp, maxLines = 1,
                    color = if (updateStale) Color(0xFFFF9800) else colors.onSurface.copy(alpha = 0.45f),
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).let { if (onRefresh != null) it.clickable(onClick = onRefresh) else it }.padding(horizontal = 2.dp, vertical = 2.dp)
                )
            }
        }
        if (isNight && nightText != null) {
            Text(nightText, fontSize = 13.sp, color = colors.onSurface, modifier = Modifier.padding(top = 2.dp, bottom = 2.dp))
            return@Column
        }
        if (!open) return@Column
        if (dawnText != null) Text(dawnText, fontSize = 13.sp, color = colors.onSurface.copy(alpha = 0.85f), modifier = Modifier.padding(top = 2.dp, bottom = 6.dp))
        val next = nextTide(hourlyModel.rawTime.time, tideInfo)
        val coef = tideInfo?.coefficient
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NowTile(tr("Houle"), "${formatDecimal(hourlyModel.waveHeight, 1).replace('.', ',')} m · ${hourlyModel.wavePeriod.toInt()} s", Modifier.weight(1f))
            NowTile(tr("Vent"), "$speed $unit $dirFr", Modifier.weight(1f))
            if (coef != null || next != null) {
                NowTile(tr("Marée"), (if (next != null) (if (next.first) "▲ " else "▼ ") + next.second.replace(" h ", ":") else ""), Modifier.weight(1f), coef = coef)
            }
        }
        if (buoy != null || observed != null) MeasuresRow(buoy, observed, windUnit)
    }
}

/** La prochaine marée après [now] : (true = pleine mer, « 14 h 05 »), ou null sans horaires. */
internal fun nextTide(now: kotlinx.datetime.LocalTime, info: DailyTideInfo?): Pair<Boolean, String>? {
    if (info == null) return null
    val all = info.extrema.ifEmpty {
        listOfNotNull(info.highTideTime?.let { MareeExtremum("PM", it) }, info.lowTideTime?.let { MareeExtremum("BM", it) })
    }
    val cands = all.mapNotNull { e -> parseTimeStringSafe(e.time)?.let { e to it } }
    val n = cands.filter { it.second.toSecondOfDay() > now.toSecondOfDay() }.minByOrNull { it.second.toSecondOfDay() } ?: return null
    val t = n.second
    return (n.first.type == "PM") to "${t.hour} h ${t.minute.toString().padStart(2, '0')}"
}

/** Le point cardinal en toutes lettres : « S » devient « sud », « OSO » devient « ouest-sud-ouest ». */
internal fun cardinalWordFr(c: String): String {
    val names = mapOf("N" to "nord", "S" to "sud", "E" to "est", "O" to "ouest")
    fun one(ch: Char) = names[ch.toString()] ?: ch.toString()
    return when (c.length) {
        1 -> one(c[0])
        2 -> one(c[0]) + "-" + one(c[1])
        3 -> one(c[0]) + "-" + one(c[1]) + "-" + one(c[2])
        else -> c
    }.let { if (c.length == 2) it.replace("nord-", "nord-").replace("sud-", "sud-") else it }
}

/** Une tuile de « Maintenant » : un titre discret et la donnée sur une seule ligne (pour la marée : point de couleur et coefficient, puis la prochaine marée). */
@Composable
private fun NowTile(title: String, value: String, modifier: Modifier = Modifier, coef: Int? = null) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier.clip(RoundedCornerShape(10.dp)).background(colors.surfaceVariant.copy(alpha = 0.6f)).padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(title, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = colors.onSurface.copy(alpha = 0.55f), maxLines = 1)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (coef != null) {
                val dot = when { coef < 55 -> AppColors.WindLow; coef <= 80 -> AppColors.WindMid; else -> AppColors.WindHigh }
                Box(modifier = Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(dot))
                Text(" $coef ", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = colors.onSurface, maxLines = 1)
            }
            Text(value, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = colors.onSurface, maxLines = 1)
        }
    }
}


private const val WAVE_PATH = "M2 20.5c2.5 0 2.5-1.5 5-1.5s2.5 1.5 5 1.5 2.5-1.5 5-1.5 2.5 1.5 5 1.5"

private fun strokeIcon(name: String, vararg paths: String): androidx.compose.ui.graphics.vector.ImageVector {
    val b = androidx.compose.ui.graphics.vector.ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
    for (d in paths) b.addPath(
        pathData = androidx.compose.ui.graphics.vector.addPathNodes(d),
        stroke = androidx.compose.ui.graphics.SolidColor(Color.Black), strokeLineWidth = 1.7f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round, strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    )
    return b.build()
}

/** Balise océanique (cône flottant) et parabole inclinée. */
private val BuoyIcon by lazy {
    strokeIcon("Buoy", "M10.8 3.5a1.2 1.2 0 1 0 2.4 0a1.2 1.2 0 1 0 -2.4 0", "M12 5l4.5 11h-9z", "M9.8 11h4.4", WAVE_PATH)
}
private val DishIcon by lazy {
    strokeIcon("Dish", "M5 8a8 8 0 0 0 11 11z", "M5 8l11 11", "M10 14l5-5", "M15 9l1.5-1.5", "M16 7a1 1 0 1 0 2 0a1 1 0 1 0 -2 0", "M9 21h8", "M13 19v2")
}

/** « LIVE » : deux pastilles (balise, station) ; un appui montre le détail sur une ligne. */
@Composable
private fun MeasuresRow(buoy: BuoyReading?, observed: ObservedNow?, windUnit: String) {
    val colors = MaterialTheme.colorScheme
    var open by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0) }
    val unit = SurfUnitsHelper.getWindUnitSymbol(windUnit)
    fun hm(t: kotlinx.datetime.LocalDateTime) = "${t.hour} h ${t.minute.toString().padStart(2, '0')}"
    @Composable
    fun Pill(id: Int, icon: androidx.compose.ui.graphics.vector.ImageVector, value: String) {
        val on = open == id
        Row(
            modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) colors.primary.copy(alpha = 0.18f) else colors.surfaceVariant)
                .clickable { open = if (on) 0 else id }.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            androidx.compose.material3.Icon(icon, null, Modifier.size(15.dp), tint = if (on) colors.primary else colors.onSurface)
            Text(value, fontSize = 12.5.sp, color = if (on) colors.primary else colors.onSurface, maxLines = 1)
        }
    }
    Column(Modifier.padding(top = 8.dp)) {
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("LIVE", fontSize = 11.sp, color = colors.onSurfaceVariant, maxLines = 1)
                buoy?.let { b ->
                    val period = b.period?.let { " · ${it.roundToInt()} s" }.orEmpty()
                    Pill(1, BuoyIcon, "${formatDecimal(b.significantHeight, 1).replace('.', ',')} m$period")
                }
                observed?.let { o ->
                    val dir = o.directionDeg?.let { " " + SurfUnitsHelper.formatCardinalFr(getCardinalDirection(it.toDouble())) }.orEmpty()
                    Pill(2, DishIcon, "${SurfUnitsHelper.formatWindValue(o.windKmh, windUnit)} $unit$dir")
                }
            }
        }
        val line = when {
            open == 1 && buoy != null -> {
                val t = buoy.timeUtc.toInstant(kotlinx.datetime.TimeZone.UTC).toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
                val max = buoy.maxHeight?.let { " · max ${formatDecimal(it, 1).replace('.', ',')} m" }.orEmpty()
                "${buoy.buoy.name}, à ${buoy.distanceKm} km$max · mesuré à ${hm(t)}"
            }
            open == 2 && observed != null -> {
                val t = observed.timeUtc.toInstant(kotlinx.datetime.TimeZone.UTC).toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
                val gust = observed.gustKmh?.takeIf { it > observed.windKmh }?.let { " · rafales ${SurfUnitsHelper.formatWindValue(it, windUnit)} $unit" }.orEmpty()
                "${niceStationName(observed.stationName)}, à ${observed.distanceKm.roundToInt()} km$gust · mesuré à ${hm(t)}"
            }
            else -> null
        }
        if (line != null) Text(line, fontSize = 11.5.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
    }
}

/** Vrai si la dernière mise à jour (« HH:mm ») date de plus de 30 min par rapport à [now]. */
internal fun isUpdateStale(updated: String, now: kotlinx.datetime.LocalDateTime): Boolean {
    val parts = updated.split(":")
    val h = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: return false
    val m = parts.getOrNull(1)?.trim()?.take(2)?.toIntOrNull() ?: return false
    var diff = now.hour * 60 + now.minute - (h * 60 + m)
    if (diff < 0) diff += 24 * 60
    return diff > 30
}
