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
}

/** L'engrenage d'un encart : il ouvre ou ferme ses réglages, juste sous le titre, l'encart reste visible et bouge en direct. */
@Composable
fun CardInfoButton(guideKey: String) {
    val on = CardGear.open == guideKey
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier.clip(CircleShape).background(if (on) colors.primaryContainer else Color.Transparent)
            .clickable { CardGear.open = if (on) null else guideKey }.padding(horizontal = 7.dp, vertical = 4.dp)
    ) {
        SettingsIcon(SettingsIcons.Gear, if (on) colors.onPrimaryContainer else colors.onSurface.copy(alpha = 0.55f), 20.dp)
    }
}

private val CARD_TITLES = mapOf(
    "week" to "de la semaine", "day" to "du déroulé de la journée", "swell" to "de la houle", "wind" to "du vent",
    "weather" to "de la météo", "hourly" to "de l'heure par heure"
)

/** Le panneau de réglages de l'encart, à placer sous son titre. N'affiche rien si l'engrenage n'est pas ouvert. */
@Composable
fun CardSettingsPanel(key: String) {
    if (CardGear.open != key) return
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Réglages " + (CARD_TITLES[key] ?: "de l'encart"), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
            Box(modifier = Modifier.size(36.dp).clickable { CardGear.open = null }, contentAlignment = Alignment.Center) {
                SettingsIcon(SettingsIcons.Close, colors.onSurfaceVariant, 20.dp)
            }
        }
        CardGuideSettings(key)
    }
}

/** Les réglages liés à l'encart. */
@Composable
private fun CardGuideSettings(key: String) {
    val c = LocalCardSettings.current
    when (key) {
        "swell" -> {
            SettingsSection("Affichage") {
                SettingsField("Clapot", top = false) {
                    SettingsSegments(listOf("on" to "Afficher", "off" to "Masquer"), if (SkyGraphSettings.showChop) "on" else "off") { SkyGraphSettings.chooseShowChop(it == "on") }
                }
            }
        }
        "wind" -> if (c != null) {
            SettingsSection("Affichage") {
                SettingsField("Unité du vent", top = false) {
                    SettingsSegments(listOf("kmh" to "km/h", "knots" to "nœuds", "bft" to "Beaufort"), c.windUnit, c.onWindUnit)
                }
            }
        }
        "week" -> {
            SettingsSection("Affichage") {
                var first = true
                if (c != null) {
                    SettingsField("Indicateurs du vent", top = false) {
                        SettingsSegments(listOf("arrow" to "Flèche", "text" to "Texte", "both" to "Les deux", "none" to "Aucun"), c.weeklyWindMode, c.onWeeklyWindMode)
                    }
                    SettingsField("Créneaux par jour") {
                        SettingsSegments(listOf("1" to "1", "2" to "2", "3" to "3"), c.weeklyDensity.toString()) { c.onWeeklyDensity(it.toInt()) }
                    }
                    first = false
                }
                SettingsSliderField("Hauteur de l'encart", "${SkyGraphSettings.weekPct} %", SkyGraphSettings.weekPct.toFloat(), 25f..70f, top = !first) { SkyGraphSettings.setWeek(it.toInt()) }
                SettingsSliderField("Échelle de la mer", "${SkyGraphSettings.waterWeekPct} %", SkyGraphSettings.waterWeekPct.toFloat(), 20f..60f) { SkyGraphSettings.setWaterWeek(it.toInt()) }
                SettingsRow("Valeurs par défaut", icon = SettingsIcons.Tune, chevron = false) { SkyGraphSettings.resetWeek() }
            }
        }
        "day" -> {
            SettingsSection("Affichage") {
                SettingsSliderField("Hauteur de l'encart", "${SkyGraphSettings.dayPct} %", SkyGraphSettings.dayPct.toFloat(), 25f..70f, top = false) { SkyGraphSettings.setDay(it.toInt()) }
                SettingsSliderField("Échelle de la mer", "${SkyGraphSettings.waterPct} %", SkyGraphSettings.waterPct.toFloat(), 20f..60f) { SkyGraphSettings.setWater(it.toInt()) }
                SettingsSliderField("Hauteur de la marée", "${SkyGraphSettings.tideDp}", SkyGraphSettings.tideDp.toFloat(), 24f..60f) { SkyGraphSettings.setTide(it.toInt()) }
                SettingsField("Marées affichées") {
                    SettingsSegments(listOf("sun" to "Journée de soleil", "all" to "Toutes"), if (SkyGraphSettings.tideAll) "all" else "sun") { SkyGraphSettings.chooseTideAll(it == "all") }
                }
                SettingsRow("Valeurs par défaut", icon = SettingsIcons.Tune, chevron = false) { SkyGraphSettings.resetDay() }
            }
        }
    }
}
