package com.surfcast.surfforecast

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.launch

/** Les réglages qu'on peut changer depuis le ⚙ d'un encart (le contrôleur les branche). */
class CardSettingsControl(
    val windUnit: String,
    val onWindUnit: (String) -> Unit,
    val weeklyWindMode: String,
    val onWeeklyWindMode: (String) -> Unit,
    val weeklyDensity: Int,
    val onWeeklyDensity: (Int) -> Unit
)

val LocalCardSettings = compositionLocalOf<CardSettingsControl?> { null }

/** L'engrenage ouvert (un seul à la fois) : son panneau s'affiche sous le titre de l'encart. */
object CardGear {
    var open by mutableStateOf<String?>(null)
    // La liste principale et la position de son haut à l'écran : pour amener l'encart en haut quand ses réglages s'ouvrent.
    var listState: androidx.compose.foundation.lazy.LazyListState? = null
    var listTopY: Float = 0f
    // Hauteur du tiroir de réglages (dp) : la liste s'allonge d'autant pour que l'encart puisse monter au-dessus.
    var sheetHeightDp by mutableStateOf(0f)
}

/**
 * L'engrenage d'un encart : il ouvre ou ferme ses réglages dans un tiroir en bas de l'écran ([CardSettingsSheet]) ;
 * l'encart monte en haut, entier et visible, et bouge en direct quand on règle.
 */
@Composable
fun CardInfoButton(guideKey: String) {
    val on = CardGear.open == guideKey
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val density = androidx.compose.ui.platform.LocalDensity.current
    var y by remember { mutableStateOf(0f) }
    Box(
        modifier = Modifier.clip(CircleShape).background(if (on) colors.primaryContainer else Color.Transparent)
            .onGloballyPositioned { y = it.positionInRoot().y }
            .clickable {
                CardGear.open = if (on) null else guideKey
                if (!on) scope.launch {
                    // Deux images plus tard, la liste a pris sa marge du bas : on amène le titre de l'encart juste sous l'en-tête.
                    withFrameNanos { }
                    withFrameNanos { }
                    CardGear.listState?.animateScrollBy(y - CardGear.listTopY - with(density) { 70.dp.toPx() })
                }
            }.padding(horizontal = 7.dp, vertical = 4.dp)
    ) {
        SettingsIcon(SettingsIcons.Gear, if (on) colors.onPrimaryContainer else colors.onSurface.copy(alpha = 0.55f), 20.dp)
    }
}

private val CARD_TITLES = mapOf(
    "week" to "de la semaine", "day" to "du déroulé de la journée", "swell" to "de la houle", "wind" to "du vent",
    "weather" to "de la météo", "hourly" to "de l'heure par heure"
)

/** Gardé pour les encarts qui l'appellent : les réglages s'affichent maintenant dans le tiroir du bas ([CardSettingsSheet]). */
@Composable
fun CardSettingsPanel(key: String) {}

/** Le tiroir de réglages de l'encart dont l'engrenage est ouvert, collé en bas de la liste ; rien si aucun n'est ouvert. */
@Composable
fun CardSettingsSheet(modifier: Modifier = Modifier) {
    val key = CardGear.open
    val density = androidx.compose.ui.platform.LocalDensity.current
    if (key == null) {
        CardGear.sheetHeightDp = 0f
        return
    }
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth().heightIn(max = 340.dp).onSizeChanged { CardGear.sheetHeightDp = with(density) { it.height.toDp().value } },
        color = colors.surfaceVariant,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
        shadowElevation = 12.dp
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Réglages ") + tr(CARD_TITLES[key] ?: "de l'encart"), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                Box(modifier = Modifier.size(36.dp).clickable { CardGear.open = null }, contentAlignment = Alignment.Center) {
                    SettingsIcon(SettingsIcons.Close, colors.onSurfaceVariant, 20.dp)
                }
            }
            CardGuideSettings(key)
        }
    }
}

/** Une ligne de réglage : le nom (et sa valeur) à gauche, le contrôle à droite, sur une seule ligne. */
@Composable
private fun GearLine(label: String, value: String? = null, control: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp).padding(horizontal = 12.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(0.38f)) {
            Text(tr(label), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.onSurface, lineHeight = 15.sp)
            if (value != null) Text(value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.primary)
        }
        Box(modifier = Modifier.weight(0.62f), contentAlignment = Alignment.CenterEnd) {
            CompositionLocalProvider(LocalSegmentFontSize provides 12.sp) { control() }
        }
    }
}

@Composable
private fun GearSlider(label: String, valueText: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    GearLine(label, valueText) {
        val cs = MaterialTheme.colorScheme
        Slider(
            value = value, onValueChange = onChange, valueRange = range,
            colors = SliderDefaults.colors(thumbColor = cs.primary, activeTrackColor = cs.primary, inactiveTrackColor = cs.outlineVariant)
        )
    }
}

/** Les réglages liés à l'encart : une ligne par réglage, pour tout voir sans défiler. */
@Composable
private fun CardGuideSettings(key: String) {
    val c = LocalCardSettings.current
    SettingsSection {
        when (key) {
            "swell" -> {
                GearLine("Clapot") {
                    SettingsSegments(listOf("on" to "Afficher", "off" to "Masquer"), if (SkyGraphSettings.showChop) "on" else "off") { SkyGraphSettings.chooseShowChop(it == "on") }
                }
            }
            "wind" -> if (c != null) {
                GearLine("Unité du vent") {
                    SettingsSegments(listOf("kmh" to "km/h", "knots" to "nœuds", "bft" to "Beaufort"), c.windUnit, c.onWindUnit)
                }
            }
            "week" -> {
                if (c != null) {
                    GearLine("Indicateurs du vent") {
                        SettingsSegments(listOf("arrow" to "Flèche", "text" to "Texte", "both" to "Mixte", "none" to "Aucun"), c.weeklyWindMode, c.onWeeklyWindMode)
                    }
                    SettingsDivider()
                    GearLine("Créneaux par jour") {
                        SettingsSegments(listOf("1" to "1", "2" to "2", "3" to "3"), c.weeklyDensity.toString()) { c.onWeeklyDensity(it.toInt()) }
                    }
                    SettingsDivider()
                }
                GearSlider("Hauteur de l'encart", "${SkyGraphSettings.weekPct} %", SkyGraphSettings.weekPct.toFloat(), 25f..70f) { SkyGraphSettings.setWeek(it.toInt()) }
                SettingsDivider()
                GearSlider("Échelle de la mer", "${SkyGraphSettings.waterWeekPct} %", SkyGraphSettings.waterWeekPct.toFloat(), 20f..60f) { SkyGraphSettings.setWaterWeek(it.toInt()) }
                SettingsRow("Valeurs par défaut", icon = SettingsIcons.Tune, chevron = false) { SkyGraphSettings.resetWeek() }
            }
            "day" -> {
                GearSlider("Hauteur de l'encart", "${SkyGraphSettings.dayPct} %", SkyGraphSettings.dayPct.toFloat(), 25f..70f) { SkyGraphSettings.setDay(it.toInt()) }
                SettingsDivider()
                GearSlider("Échelle de la mer", "${SkyGraphSettings.waterPct} %", SkyGraphSettings.waterPct.toFloat(), 20f..60f) { SkyGraphSettings.setWater(it.toInt()) }
                SettingsDivider()
                GearSlider("Hauteur de la rampe de marée", "${SkyGraphSettings.rampDp} px", SkyGraphSettings.rampDp.toFloat(), 8f..28f) { SkyGraphSettings.setRamp(it.toInt()) }
                SettingsRow("Valeurs par défaut", icon = SettingsIcons.Tune, chevron = false) { SkyGraphSettings.resetDay() }
            }
        }
    }
}
