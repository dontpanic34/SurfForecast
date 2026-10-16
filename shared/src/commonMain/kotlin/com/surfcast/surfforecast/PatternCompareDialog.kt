package com.surfcast.surfforecast

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.abs
import kotlin.math.roundToInt

/** La proximité d'une ligne : vert = proche, jaune = un peu différent, rouge = différent. */
private enum class Gap(val color: Color) { Close(Color(0xFF38C976)), Near(Color(0xFFE8B43A)), Far(Color(0xFFE5533D)) }

private fun hmFr(ms: Long): String {
    val t = epochMillisToLocalDateTime(ms)
    return t.hour.toString() + " h" + (if (t.minute != 0) " " + t.minute.toString().padStart(2, '0') else "")
}

/** « 14 h à 15 h 20 » : l'heure de début et de fin d'une session. */
internal fun sessionHoursLabel(s: SurfSession): String = hmFr(s.startTime) + tr(" à ") + hmFr(s.endTime)

private fun phaseWord(p: String?): String? = when (p) {
    "rising" -> "montante"; "falling" -> "descendante"; "high" -> "pleine mer"; "low" -> "basse mer"; else -> null
}

private fun categoryWord(c: String): String = when (c) {
    "offshore" -> "offshore"; "onshore" -> "onshore"; "side-off" -> "side-off"; "side-on" -> "side-on"; else -> "side"
}

/**
 * La comparaison côte à côte d'une prévision et de la session qui lui « ressemble » : houle, direction, vent, marée,
 * chaque ligne avec une barre verte (proche), jaune ou rouge (différent), puis ce qui pèse le plus dans l'écart.
 */
@Composable
fun PatternCompareDialog(
    match: PatternMatch,
    idealSwellDirection: Int?,
    forecastTide: DailyTideInfo?,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val f = match.hourlyModel
    val ref = match.referenceSession
    val c = ref.condition
    val refStart = epochMillisToLocalDateTime(ref.session.startTime)
    val fDay = "${frenchShortDayName(f.rawTime.date)} ${f.rawTime.date.day}"
    val rDay = "${refStart.date.day} ${frenchMonthName(refStart.date)}"

    val fCat = windCategoryFor(f.windDirectionStr, idealSwellDirection)
    val rCat = windCategoryFromDegrees(c.windDirection.toFloat(), idealSwellDirection)
    val fDir = idealSwellDirection?.let { angularDifference(f.waveDirection.toDouble(), it.toDouble()) } ?: 0.0
    val rDir = idealSwellDirection?.let { angularDifference(c.waveDirection.toDouble(), it.toDouble()) } ?: 0.0

    val swellGap = abs(f.energyKj - c.energyKj).let { d -> val base = maxOf(f.energyKj, c.energyKj).coerceAtLeast(1); if (d * 4 < base) Gap.Close else if (d * 2 < base) Gap.Near else Gap.Far }
    val dirGap = abs(fDir - rDir).let { if (it < 20) Gap.Close else if (it < 45) Gap.Near else Gap.Far }
    val windDiff = abs(f.windSpeedKmh - c.windSpeedKmh)
    val windGap = when {
        fCat == rCat && windDiff < 5 -> Gap.Close
        windCatClose(fCat, rCat) && windDiff < 8 -> Gap.Near
        else -> Gap.Far
    }
    val fCoef = forecastTide?.coefficient
    val tideGap = if (fCoef != null && c.tideCoeff != null) abs(fCoef - c.tideCoeff).let { if (it < 15) Gap.Close else if (it < 30) Gap.Near else Gap.Far } else null
    val fPhase = phaseWord(tidePhaseAt(f.rawTime.time, forecastTide))
    val rPhase = phaseWord(c.tidePhase)

    val worst = listOfNotNull(
        "la houle" to swellGap, "sa direction" to dirGap, "le vent" to windGap, tideGap?.let { "la marée" to it }
    ).filter { it.second == Gap.Far }.map { it.first }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxWidth(0.94f).clip(RoundedCornerShape(16.dp)), color = colors.surface) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Comparer"), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = colors.onSurface)
                    Text("✕", fontSize = 16.sp, color = colors.onSurfaceVariant, modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onDismiss).padding(horizontal = 10.dp, vertical = 4.dp))
                }
                Text(
                    tr("Prévision à gauche, ta session à droite"), fontSize = 11.5.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(bottom = 10.dp)
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.width(66.dp))
                    HeaderCell(tr("Prévision"), "$fDay · ${f.rawTime.hour} h", Modifier.weight(1f))
                    HeaderCell(tr("Ta session"), "$rDay · ${sessionHoursLabel(ref.session)}", Modifier.weight(1f))
                }
                Spacer6()
                CompareRow(
                    tr("Houle"), swellGap,
                    "${formatDecimal(f.waveHeight, 1).replace('.', ',')} m · ${f.wavePeriod.roundToInt()} s", "${f.energyKj} kJ",
                    "${formatDecimal(c.waveHeight, 1).replace('.', ',')} m · ${c.wavePeriod.roundToInt()} s", "${c.energyKj} kJ"
                )
                Spacer6()
                CompareRow(
                    tr("Direction"), dirGap,
                    "${getCardinalDirection(f.waveDirection.toDouble())} ${f.waveDirection.roundToInt()}°", if (idealSwellDirection != null) "${fDir.roundToInt()}° ${tr("de la plage")}" else "",
                    "${getCardinalDirection(c.waveDirection.toDouble())} ${c.waveDirection}°", if (idealSwellDirection != null) "${rDir.roundToInt()}° ${tr("de la plage")}" else ""
                )
                Spacer6()
                CompareRow(
                    tr("Vent"), windGap,
                    "${f.windSpeedKmh} km/h ${SurfUnitsHelper.formatCardinalFr(f.windDirectionStr)}", categoryWord(fCat),
                    "${c.windSpeedKmh} km/h ${SurfUnitsHelper.formatCardinalFr(getCardinalDirection(c.windDirection.toDouble()))}", categoryWord(rCat)
                )
                if (tideGap != null || fPhase != null || rPhase != null) {
                    Spacer6()
                    CompareRow(
                        tr("Marée"), tideGap ?: Gap.Near,
                        fCoef?.let { "coef $it" } ?: "—", fPhase?.let { tr(it) }.orEmpty(),
                        c.tideCoeff?.let { "coef $it" } ?: "—", rPhase?.let { tr(it) }.orEmpty()
                    )
                }
                Row(
                    modifier = Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(colors.surfaceVariant).padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("${match.score} %", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = if (match.score >= 90) Gap.Close.color else Gap.Near.color)
                    Text(
                        if (worst.isEmpty()) tr("Les conditions sont proches sur tous les points.")
                        else tr("L'écart principal :") + " " + worst.joinToString(", ") { tr(it) } + ".",
                        fontSize = 12.5.sp, color = colors.onSurface
                    )
                }
                Text(
                    tr("Barre colorée : vert = proche, jaune = un peu différent, rouge = différent."),
                    fontSize = 11.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

private fun windCatClose(a: String, b: String): Boolean {
    val order = listOf("offshore", "side-off", "side", "side-on", "onshore")
    return abs(order.indexOf(a) - order.indexOf(b)) <= 1
}

@Composable
private fun Spacer6() { Box(Modifier.height(6.dp)) }

@Composable
private fun HeaderCell(label: String, sub: String, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier) {
        Text(label, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = colors.onSurfaceVariant)
        Text(sub, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = colors.onSurface)
    }
}

@Composable
private fun CompareRow(label: String, gap: Gap, a: String, aSub: String, b: String, bSub: String) {
    val colors = MaterialTheme.colorScheme
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.onSurfaceVariant, modifier = Modifier.width(66.dp))
        Cell(a, aSub, gap, Modifier.weight(1f).fillMaxHeight())
        Cell(b, bSub, gap, Modifier.weight(1f).fillMaxHeight())
    }
}

@Composable
private fun Cell(value: String, sub: String, gap: Gap, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(modifier = modifier.clip(RoundedCornerShape(8.dp)).background(colors.surfaceVariant)) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(gap.color))
        Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Text(value, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
            if (sub.isNotEmpty()) Text(sub, fontSize = 10.5.sp, color = colors.onSurfaceVariant)
        }
    }
}
