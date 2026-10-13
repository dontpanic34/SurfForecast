package com.surfcast.surfforecast

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.roundToInt
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Réglage des sources de prévision (maquette `modeles-prevision` et `reglages-pro`) : Auto, ou un modèle par donnée
 * et par échéance, avec sous chaque choix une fiche du modèle (finesse, portée, ce qu'il fait de mieux).
 * En manuel, « Vent et météo » règle le même modèle pour le vent et pour le ciel, la température et la pluie.
 */
@Composable
fun ForecastSourcesPage(
    autoModels: Boolean,
    onAutoChanged: (Boolean) -> Unit,
    shortWave: String, onShortWave: (String) -> Unit,
    longWave: String, onLongWave: (String) -> Unit,
    shortWind: String, onShortWind: (String) -> Unit,
    longWind: String, onLongWind: (String) -> Unit,
    preview: ModelPreview? = null,
    onNeedPreview: () -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    // Les annonces de chaque modèle se chargent à l'ouverture de la page, pour les montrer sous chaque choix.
    LaunchedEffect(autoModels) { onNeedPreview() }
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SettingsSegments(listOf("auto" to "Auto (conseillé)", "manual" to "Choisir moi-même"), if (autoModels) "auto" else "manual") { onAutoChanged(it == "auto") }
        if (autoModels) {
            SettingsNote("En Auto, l'appli compare plusieurs modèles pour toi et affiche une seule valeur. Rien à régler.")
            SettingsSection("Ce que fait Auto") {
                SettingsRow("Houle", "Consensus de MF-WAM, ECMWF Wave et GFS Wave : la hauteur et la période viennent du même calcul. Les modèles fins pèsent plus aujourd'hui et demain, les trois à égalité ensuite.", SettingsIcons.Wave, top = false)
                SettingsRow("Vent", "Moyenne de ECMWF et de GFS, ressenti (rafales comprises), jusqu'à J+7.", SettingsIcons.Wind)
                SettingsRow("Ciel, température, pluie", "Météo-France : AROME aujourd'hui et demain, puis ARPEGE jusqu'à environ 4 jours, puis ECMWF.", SettingsIcons.Cloud)
                SettingsRow("Houle secondaire", "Affichée dès 0,2 m et 7 s, et ajoutée à l'énergie de la note.", SettingsIcons.Layers)
                SettingsRow("AROME HD", "Calculé en coulisses pour comparer, pas affiché.", SettingsIcons.Tune)
            }
        } else {
            SettingsNote("Un modèle par donnée et par échéance. Touche un modèle : sa fiche s'affiche dessous.")
            TermSection("Aujourd'hui et demain", "J+0 et J+1", shortWave, onShortWave, shortWind, onShortWind, preview, day = 1)
            TermSection("Après-demain et la suite", "J+2 à J+7", longWave, onLongWave, longWind, onLongWind, preview, day = 3)
        }
        SettingsHelpLink("Comprendre les modèles") { ComprendreState.open("modeles") }
        Text(
            "Les modèles viennent d'Open-Meteo. Un modèle qui ne couvre pas une heure est remplacé par le suivant : AROME s'arrête vers 2 jours, ARPEGE vers 4.",
            fontSize = 12.sp, color = colors.onSurfaceVariant
        )
    }
}

@Composable
private fun TermSection(title: String, range: String, wave: String, onWave: (String) -> Unit, wind: String, onWind: (String) -> Unit, preview: ModelPreview?, day: Int) {
    SettingsSection("$title · $range") {
        SettingsField("Houle", wave, top = false) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsSegments(listOf("MF-WAM" to "MF-WAM", "ECMWF Wave" to "ECMWF", "GFS Wave" to "GFS"), wave, onWave)
                ModelCard(wave, preview, day, isWave = true)
            }
        }
        SettingsField("Vent et météo", wind) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsSegments(listOf("AROME" to "AROME", "ARPEGE" to "ARPEGE", "ECMWF" to "ECMWF", "GFS" to "GFS"), wind, onWind)
                ModelCard(wind, preview, day, isWave = false)
            }
        }
    }
}

@Composable
private fun ModelCard(model: String, preview: ModelPreview?, day: Int, isWave: Boolean) {
    val info = forecastModelInfo(model)
    val colors = MaterialTheme.colorScheme
    SettingsPreviewBox(info.first) {
        Text(info.second, fontSize = 12.5.sp, lineHeight = 17.sp, color = colors.onSurface)
        val when_ = if (day == 1) "Demain, 14 h" else "Dans 3 jours, 14 h"
        val line: String? = when {
            preview == null -> "Chargement des valeurs de chaque modèle…"
            isWave -> preview.waves[model]?.get(day)?.let { w ->
                val own = comma(w.height) + " m" + (if (w.period > 0) " · ${w.period.roundToInt()} s" else "")
                val others = preview.waves.filterKeys { it != model }.mapNotNull { it.value[day]?.height }
                "$when_ : $own" + rangeOf(others, { comma(it) }, " m")
            }
            else -> preview.winds[model]?.get(day)?.let { w ->
                val dir = w.directionDeg?.let { SurfUnitsHelper.formatCardinalFr(getCardinalDirection(it.toDouble())) + " " }.orEmpty()
                val extra = listOfNotNull(w.temperature?.let { "$it°" }, w.cloudCover?.let { "nuages $it %" }, w.rainMm?.takeIf { it > 0.0 }?.let { comma(it) + " mm" }).joinToString(" · ")
                val others = preview.winds.filterKeys { it != model }.mapNotNull { it.value[day]?.speedKmh?.toDouble() }
                "$when_ : $dir${w.speedKmh} km/h" + (if (extra.isNotEmpty()) " · $extra" else "") + rangeOf(others, { it.roundToInt().toString() }, " km/h")
            }
        }
        if (line != null) {
            Text(line, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.primary)
        } else if (preview != null) {
            Text("Ce modèle ne couvre pas ce moment : l'appli prend le suivant.", fontSize = 12.5.sp, color = colors.onSurfaceVariant)
        }
    }
}

private fun comma(v: Double) = formatDecimal(v, 1).replace('.', ',')

private fun rangeOf(values: List<Double>, fmt: (Double) -> String, unit: String): String {
    if (values.isEmpty()) return ""
    val lo = values.min(); val hi = values.max()
    return "\nLes autres modèles : " + if (fmt(lo) == fmt(hi)) fmt(lo) + unit else fmt(lo) + " à " + fmt(hi) + unit
}
