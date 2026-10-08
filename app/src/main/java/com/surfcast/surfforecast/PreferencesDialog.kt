@file:Suppress("SpellCheckingInspection")
package com.surfcast.surfforecast

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.surfcast.surfforecast.ui.theme.AppColors

@Composable
fun SurfPreferencesDialog(
    windUnit: String,
    onWindUnitSelected: (String) -> Unit,
    showLiveOverlay: Boolean,
    onToggleLiveOverlay: (Boolean) -> Unit,
    showWeeklyCard: Boolean,
    onToggleWeeklyCard: (Boolean) -> Unit,
    weeklyDensity: Int,
    onWeeklyDensityChanged: (Int) -> Unit,
    weeklyWindMode: String,
    onWeeklyWindModeChanged: (String) -> Unit,
    showDailyTimelineCard: Boolean,
    onToggleDailyTimelineCard: (Boolean) -> Unit,
    showSurfCard: Boolean,
    onToggleSurfCard: (Boolean) -> Unit,
    showWindCard: Boolean,
    onToggleWindCard: (Boolean) -> Unit,
    showWindSeaCard: Boolean,
    onToggleWindSeaCard: (Boolean) -> Unit,
    showWeatherCard: Boolean,
    onToggleWeatherCard: (Boolean) -> Unit,
    showHourlyCard: Boolean,
    onToggleHourlyCard: (Boolean) -> Unit,
    surferLevel: String,
    onSurferLevelChanged: (String) -> Unit,
    engineConfig: ForecastEngineConfig,
    onEngineConfigChanged: (ForecastEngineConfig) -> Unit,
    onViewLogs: () -> Unit,
    onDismiss: () -> Unit,
    // Rouvre l'écran de bienvenue (unités, niveau, origine des prévisions).
    onShowIntro: (() -> Unit)? = null,
    // Rejoue la visite guidée de l'accueil.
    onShowTour: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val pillShape = RoundedCornerShape(50)

    // Brouillon des combos de prevision, initialise depuis la config reellement active
    // (et non plus des valeurs en dur) et applique seulement sur "Enregistrer et fermer".
    var shortTermWind by remember { mutableStateOf(weatherModelLabel(engineConfig.shortTermWeather)) }
    var shortTermWave by remember { mutableStateOf(waveModelLabel(engineConfig.shortTermWave)) }
    var longTermWind by remember { mutableStateOf(weatherModelLabel(engineConfig.longTermWeather)) }
    var longTermWave by remember { mutableStateOf(waveModelLabel(engineConfig.longTermWave)) }

    var showModelsInfo by remember { mutableStateOf(false) }
    var infoTab by remember { mutableStateOf("France") }
    var showScoreInfo by remember { mutableStateOf(false) }

    if (showScoreInfo) {
        AlertDialog(
            onDismissRequest = { showScoreInfo = false },
            containerColor = colors.background,
            title = {
                Text(text = "Comment le score est calcule", color = colors.onBackground, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Chaque heure recoit une note de 0 a 100, calculee a partir de l'energie de la houle (hauteur x periode), du vent et de la maree.",
                        fontSize = 12.sp,
                        color = colors.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                    ModelDescItem(
                        name = "1. Energie de la houle",
                        desc = "Plus la houle est grosse et longue (periode), plus l'energie est elevee. Une houle plate (< 0,4 m) ou une energie trop forte (> 1000 kJ, ca casse en vrac) donne directement 0."
                    )
                    ModelDescItem(
                        name = "2. Vent",
                        desc = "Un vent de terre (offshore) est presque toujours bon. Un vent de mer (onshore) penalise, de plus en plus fort au-dela de 25 km/h ou il elimine le creneau."
                    )
                    ModelDescItem(
                        name = "3. Ton niveau",
                        desc = "Debutant : vise une houle douce (0,4-0,8 m environ), penalisee si trop grosse, trop longue en periode, ou a maree haute. Tolere un peu de vent de mer.\nIntermediaire : vise une houle moyenne, penalise si le vent de mer depasse 15 km/h.\nConfirme : vise une houle plus costaude, veut du vent de terre ou pas de vent, penalise sur les micro-houles."
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showScoreInfo = false }) {
                    Text("J'ai compris", color = colors.primary, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showModelsInfo) {
        AlertDialog(
            onDismissRequest = { showModelsInfo = false },
            containerColor = colors.background,
            title = {
                Text(text = "Comprendre les Combos", color = colors.onBackground, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Associer la haute résolution côtière pour l'immédiat (court terme) avec la robustesse globale d'un modèle de référence pour anticiper la suite (long terme).",
                        fontSize = 12.sp,
                        color = colors.onSurfaceVariant,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(2.dp))
                    Text(text = "🌬️ Météo & Vent", color = colors.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)

                    ModelDescItem(
                        name = "AROME (1.3 km)",
                        desc = "Modèle haute définition Météo-France. Imbattable pour les brises thermiques, effets de site et bascules locales (J+0 / J+1)."
                    )
                    ModelDescItem(
                        name = "ARPEGE (5 km)",
                        desc = "Modèle global à maille variable. Bon compromis intermédiaire sur l'Atlantique."
                    )
                    ModelDescItem(
                        name = "ECMWF (9 km)",
                        desc = "Référence européenne mondiale. Extrêmement stable pour les flux généraux et la synoptique à long terme (J+2 à J+7)."
                    )

                    Spacer(modifier = Modifier.height(2.dp))
                    Text(text = "🌊 Vagues (Houle)", color = colors.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)

                    ModelDescItem(
                        name = "MF-WAM",
                        desc = "Modèle de vagues haute résolution Météo-France. Redoutable pour l'interaction vent/vagues au plus près de la côte."
                    )
                    ModelDescItem(
                        name = "ECMWF Wave",
                        desc = "Modèle global robuste. Idéal pour tracker l'énergie pure des swells formés au large plusieurs jours à l'avance."
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = "🌍 Le Combo Idéal par Région", color = colors.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("France", "Espagne", "Portugal").forEach { tab ->
                            val isSelected = infoTab == tab
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(pillShape)
                                    .clickable { infoTab = tab },
                                color = if (isSelected) colors.primary else colors.surfaceVariant
                            ) {
                                Box(modifier = Modifier.padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = tab,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) colors.onPrimary else colors.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    when (infoTab) {
                        "France" -> {
                            ModelDescItem(
                                name = "Côte Atlantique (Gironde, Landes, Pays Basque)",
                                desc = "• Court terme : AROME + MF-WAM (Gère la micro-météo, les thermiques estivaux et les baïnes).\n• Long terme : ECMWF + ECMWF Wave (Anticipe parfaitement les défilés de perturbations sur le Gascogne)."
                            )
                        }
                        "Espagne" -> {
                            ModelDescItem(
                                name = "Côte Nord (Cantabrie, Asturies, Pays Basque espagnol)",
                                desc = "• Court terme : AROME/ARPEGE + MF-WAM (Idéal face au relief abrupt et aux effets de talus côtiers).\n• Long terme : ECMWF + ECMWF Wave (Capte les houles d'ouest/nord-ouest venant buter sur la corniche)."
                            )
                        }
                        "Portugal" -> {
                            ModelDescItem(
                                name = "Côte Lusitanienne (Ericeira, Peniche, Algarve)",
                                desc = "• Court terme : ARPEGE/ECMWF + MF-WAM (AROME s'arrêtant au nord, Arpege ou ECMWF prennent le relais pour le vent).\n• Long terme : ECMWF + ECMWF Wave (Arme absolue face aux swells XXL du grand large)."
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showModelsInfo = false }) {
                    Text("J'ai compris", color = colors.primary, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .clip(RoundedCornerShape(14.dp)),
            color = colors.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Paramètres",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.onBackground
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(30.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Fermer",
                            tint = colors.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "1. Personnalisation",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.primary
                            )
                            Text(
                                text = "Choisissez les encarts affichés sur l'écran principal.",
                                fontSize = 11.sp,
                                color = colors.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.surfaceVariant)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Niveau de surf",
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.onBackground,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { showScoreInfo = true },
                                        modifier = Modifier.size(18.dp)
                                    ) {
                                        Icon(Icons.Default.Info, contentDescription = "Comment le score est calcule", tint = colors.primary, modifier = Modifier.size(14.dp))
                                    }
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf("beginner" to "Débutant", "intermediate" to "Intermédiaire", "confirmed" to "Confirmé").forEach { (key, label) ->
                                        val isSelected = surferLevel == key
                                        Surface(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(pillShape)
                                                .clickable { onSurferLevelChanged(key) },
                                            color = if (isSelected) colors.primary else colors.background
                                        ) {
                                            Box(modifier = Modifier.padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = label,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSelected) colors.onPrimary else colors.onSurfaceVariant,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.surfaceVariant)
                            ) {
                                CardVisibilityRow("Prévision semaine", showWeeklyCard, onToggleWeeklyCard, colors)

                                if (showWeeklyCard) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "Icônes par jour",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colors.onSurfaceVariant
                                        )
                                        Text(
                                            text = "Combien d'icônes météo et vent par jour (1 = léger, 3 = détaillé)",
                                            fontSize = 10.sp,
                                            color = colors.onSurfaceVariant.copy(alpha = 0.8f)
                                        )
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            listOf(1, 2, 3).forEach { d ->
                                                val isSelected = weeklyDensity == d
                                                Surface(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clip(pillShape)
                                                        .clickable { onWeeklyDensityChanged(d) },
                                                    color = if (isSelected) colors.primary else colors.background
                                                ) {
                                                    Box(modifier = Modifier.padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                                                        Text(
                                                            text = d.toString(),
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (isSelected) colors.onPrimary else colors.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(2.dp))

                                        Text(
                                            text = "Indicateurs vent",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colors.onSurfaceVariant
                                        )
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            listOf("arrow" to "Flèche", "text" to "Texte", "both" to "Les deux", "none" to "Aucun").forEach { (key, label) ->
                                                val isSelected = weeklyWindMode == key
                                                Surface(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clip(pillShape)
                                                        .clickable { onWeeklyWindModeChanged(key) },
                                                    color = if (isSelected) colors.primary else colors.background
                                                ) {
                                                    Box(modifier = Modifier.padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                                                        Text(
                                                            text = label,
                                                            fontSize = 9.5.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (isSelected) colors.onPrimary else colors.onSurfaceVariant,
                                                            maxLines = 1
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                CardVisibilityRow("Déroulé de la journée", showDailyTimelineCard, onToggleDailyTimelineCard, colors)
                                CardVisibilityRow("Vagues & Houle", showSurfCard, onToggleSurfCard, colors)
                                CardVisibilityRow("Vent", showWindCard, onToggleWindCard, colors)
                                CardVisibilityRow("Mer de vent", showWindSeaCard, onToggleWindSeaCard, colors)
                                CardVisibilityRow("Météo", showWeatherCard, onToggleWeatherCard, colors)
                                CardVisibilityRow("Prévision heure par heure", showHourlyCard, onToggleHourlyCard, colors)
                            }
                        }
                    }

                    item {                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "2. Unité du vent",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.primary
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("kmh" to "km/h", "knots" to "Nœuds", "bft" to "Beaufort").forEach { (key, label) ->
                                    val isSelected = windUnit == key
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(pillShape)
                                            .clickable { onWindUnitSelected(key) },
                                        color = if (isSelected) colors.primary else colors.surfaceVariant
                                    ) {
                                        Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                            Text(
                                                text = label,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) colors.onPrimary else colors.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Bouton d'épinglage du widget d'accueil.
                    item {
                        OutlinedButton(
                            onClick = { SurfOverlayWidgetProvider.pinWidget(context) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = pillShape,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.primary)
                        ) {
                            Text(text = "Épingler le Widget d'accueil", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    item {                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "3. Combos de prévision",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.primary
                                )
                                IconButton(
                                    onClick = { showModelsInfo = true },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Info, contentDescription = "Informations", tint = colors.primary)
                                }
                            }

                            Text(text = "⚡ Court terme (J+0 / J+1)", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)

                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(text = "Vent :", fontSize = 10.5.sp, color = colors.onSurfaceVariant)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf("AROME", "ARPEGE", "ECMWF").forEach { model ->
                                        val isSelected = shortTermWind == model
                                        Surface(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(pillShape)
                                                .clickable { shortTermWind = model },
                                            color = if (isSelected) colors.primary else colors.surfaceVariant
                                        ) {
                                            Box(modifier = Modifier.padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = model,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSelected) colors.onPrimary else colors.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(text = "Vagues :", fontSize = 10.5.sp, color = colors.onSurfaceVariant)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf("MF-WAM", "ECMWF Wave").forEach { model ->
                                        val isSelected = shortTermWave == model
                                        Surface(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(pillShape)
                                                .clickable { shortTermWave = model },
                                            color = if (isSelected) colors.primary else colors.surfaceVariant
                                        ) {
                                            Box(modifier = Modifier.padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = model,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSelected) colors.onPrimary else colors.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(text = "📅 Long terme (J+2 / J+7)", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)

                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(text = "Vent :", fontSize = 10.5.sp, color = colors.onSurfaceVariant)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf("ECMWF", "ARPEGE").forEach { model ->
                                        val isSelected = longTermWind == model
                                        Surface(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(pillShape)
                                                .clickable { longTermWind = model },
                                            color = if (isSelected) colors.primary else colors.surfaceVariant
                                        ) {
                                            Box(modifier = Modifier.padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = model,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSelected) colors.onPrimary else colors.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(text = "Vagues :", fontSize = 10.5.sp, color = colors.onSurfaceVariant)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf("ECMWF Wave", "MF-WAM").forEach { model ->
                                        val isSelected = longTermWave == model
                                        Surface(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(pillShape)
                                                .clickable { longTermWave = model },
                                            color = if (isSelected) colors.primary else colors.surfaceVariant
                                        ) {
                                            Box(modifier = Modifier.padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = model,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSelected) colors.onPrimary else colors.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Button(
                                onClick = { onViewLogs() },
                                modifier = Modifier.fillMaxWidth(),
                                shape = pillShape,
                                colors = ButtonDefaults.buttonColors(containerColor = colors.surfaceVariant)
                            ) {
                                Text("Voir les logs d'actualisation", fontSize = 11.5.sp, color = colors.onBackground)
                            }
                        }
                    }
                }

                if (onShowTour != null) {
                    TextButton(onClick = onShowTour, modifier = Modifier.fillMaxWidth()) {
                        Text("🧭 Revoir la visite guidée de l'écran", fontSize = 11.5.sp)
                    }
                }

                if (onShowIntro != null) {
                    TextButton(onClick = onShowIntro, modifier = Modifier.fillMaxWidth()) {
                        Text("ℹ️ Revoir l'introduction (unités, niveau, prévisions)", fontSize = 11.5.sp)
                    }
                }

                DonationSection(weroQr = { androidx.compose.foundation.Image(painter = androidx.compose.ui.res.painterResource(R.drawable.wero_qr), contentDescription = "QR code Wero", modifier = Modifier.fillMaxWidth(), contentScale = androidx.compose.ui.layout.ContentScale.FillWidth) })

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        val newConfig = ForecastEngineConfig(
                            shortTermWeather = weatherModelFromLabel(shortTermWind),
                            shortTermWave = waveModelFromLabel(shortTermWave),
                            longTermWeather = weatherModelFromLabel(longTermWind),
                            longTermWave = waveModelFromLabel(longTermWave)
                        )
                        // updateEngineConfig recharge les previsions : seulement si ca a change.
                        if (newConfig != engineConfig) onEngineConfigChanged(newConfig)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = pillShape,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                ) {
                    Text("Enregistrer et fermer", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun CardVisibilityRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    colors: ColorScheme
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = colors.onPrimary, checkedTrackColor = colors.primary)
        )
    }
}

@Composable
fun ModelDescItem(name: String, desc: String) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = Modifier.padding(bottom = 6.dp)) {
        Text(text = "• $name", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
        Text(text = desc, fontSize = 11.5.sp, color = colors.onSurfaceVariant, lineHeight = 15.sp, modifier = Modifier.padding(start = 10.dp))
    }
}

// Libelles des pilules "Combos de prevision" <-> modeles reellement appeles.
private fun weatherModelLabel(model: WeatherModel): String = when (model) {
    WeatherModel.AROME -> "AROME"
    WeatherModel.ARPEGE -> "ARPEGE"
    WeatherModel.ECMWF_IFS -> "ECMWF"
}

private fun weatherModelFromLabel(label: String): WeatherModel = when (label) {
    "ARPEGE" -> WeatherModel.ARPEGE
    "ECMWF" -> WeatherModel.ECMWF_IFS
    else -> WeatherModel.AROME
}

private fun waveModelLabel(model: WaveModel): String = when (model) {
    WaveModel.MFWAM -> "MF-WAM"
    WaveModel.ECMWF_WAM -> "ECMWF Wave"
}

private fun waveModelFromLabel(label: String): WaveModel =
    if (label == "ECMWF Wave") WaveModel.ECMWF_WAM else WaveModel.MFWAM
