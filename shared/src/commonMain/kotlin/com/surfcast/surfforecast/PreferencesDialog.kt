@file:Suppress("SpellCheckingInspection")
package com.surfcast.surfforecast

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import com.surfcast.surfforecast.resources.Res
import com.surfcast.surfforecast.resources.widget_preview
import org.jetbrains.compose.resources.painterResource
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
    // Épinglage du widget d'accueil : Android seulement (null = bouton masqué).
    onPinWidget: (() -> Unit)? = null,
    // Rouvre l'écran de bienvenue (unités, niveau, origine des prévisions).
    onShowIntro: (() -> Unit)? = null,
    onShowTour: (() -> Unit)? = null,
    // Orientation du spot affiché (null = pas de spot chargé : la carte est masquée).
    spotName: String = "",
    beachFacing: Int? = null,
    defaultBeachFacing: Int? = null,
    onBeachFacingChanged: ((Int?) -> Unit)? = null,
    // Sauvegarde / restauration du journal (version web seulement).
    backup: SessionLogBackup? = null,
    // Installation du site comme appli (version web seulement).
    install: AppInstall? = null,
    // Mon matériel : quiver partagé avec le journal de bord, et âge / taille / poids (0 = non renseigné).
    quiverBoards: List<QuiverBoard> = emptyList(),
    bodyAge: Int = 0,
    bodyHeightCm: Int = 0,
    bodyWeightKg: Int = 0,
    onBodyChanged: (age: Int, heightCm: Int, weightKg: Int) -> Unit = { _, _, _ -> },
    onAddBoard: (model: String, family: String, lengthLitrage: String, finSetup: String, volumeL: Double?, volumeEstimated: Boolean) -> Unit = { _, _, _, _, _, _ -> },
    onDeleteBoard: (QuiverBoard) -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    val pillShape = RoundedCornerShape(50)

    // Brouillon des combos de prévision, initialisé depuis la config réellement active
    // et appliqué seulement sur "Enregistrer et fermer".
    var shortTermWind by remember { mutableStateOf(weatherModelLabel(engineConfig.shortTermWeather)) }
    var shortTermWave by remember { mutableStateOf(waveModelLabel(engineConfig.shortTermWave)) }
    var longTermWind by remember { mutableStateOf(weatherModelLabel(engineConfig.longTermWeather)) }
    var longTermWave by remember { mutableStateOf(waveModelLabel(engineConfig.longTermWave)) }

    var showModelsInfo by remember { mutableStateOf(false) }
    var infoTab by remember { mutableStateOf("France") }
    var showScoreInfo by remember { mutableStateOf(false) }
    // Page ouverte : null = menu des Paramètres, sinon "display", "forecast", "journal", "app" ou "help".
    var page by remember { mutableStateOf<String?>(null) }

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
                        name = "1. Énergie de la houle",
                        desc = "Plus la houle est grosse et longue (période), plus l'énergie est élevée. Une houle plate (< 0,4 m) donne 0. Un petit jour propre (0,8 m à 9 s) ouvre pour tout le monde. Au-delà du plafond de ton niveau, c'est « trop gros » (violet), pas « mauvais »."
                    )
                    ModelDescItem(
                        name = "2. Vent",
                        desc = "Offshore (de la terre) : le meilleur. Onshore (de la mer) : presque aussi bon quand il y a très peu de vent, puis de plus en plus mauvais. Les rafales comptent : de fortes rafales gâchent la session quelle que soit la direction."
                    )
                    ModelDescItem(
                        name = "3. Direction et clapot",
                        desc = "Une houle de face est mieux notée qu'une houle de travers. Un clapot (mer de vent) important par rapport à la houle baisse la note."
                    )
                    ModelDescItem(
                        name = "4. Ton niveau",
                        desc = "Le profil règle l'énergie idéale, le plafond « trop gros », mais aussi la tolérance au vent, aux rafales, au clapot et à la période.\nDébutant : les mousses, petites vagues douces (trop gros dès 350 kJ).\nIntermédiaire : commence à aller au large et à suivre les vagues (trop gros dès 700).\nConfirmé : autonome, surfe seul, préfère un peu de puissance (trop gros dès 3500).\nExpert : plein potentiel de la vague, aucune limite.\nPersonnalisé : tu règles tout toi-même."
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
                                        fontSize = 12.sp,
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
                    if (page == null) {
                        Text(
                            text = "Paramètres",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.onBackground
                        )
                    } else {
                        TextButton(onClick = { page = if (page == "guide") "help" else null }) {
                            Text(if (page == "guide") "‹ Aide" else "‹ Paramètres", fontSize = 13.sp, color = colors.primary)
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(30.dp)) {
                        Icon(
                            imageVector = SurfIcons.Close,
                            contentDescription = "Fermer",
                            tint = colors.onSurfaceVariant
                        )
                    }
                }
                page?.let { current ->
                    Text(
                        text = when (current) {
                            "profile" -> "Mon profil"
                            "display" -> "Affichage"
                            "forecast" -> "Prévisions"
                            "journal" -> "Journal de bord"
                            "app" -> "Appli"
                            "guide" -> "Comprendre les prévisions"
                            else -> "Aide"
                        },
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.onBackground
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Colonne défilante simple (et non LazyColumn) : le contenu est court, et la hauteur de la
                // carte APK (image chargée après coup) ne doit pas décaler le défilement.
                val scrollState = rememberScrollState()
                LaunchedEffect(page) { scrollState.scrollTo(0) }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (page == null) {
                        PrefMenuRow("🏄", "Mon profil", "Niveau, style de surf, mon matériel") { page = "profile" }
                        PrefMenuRow("🎛️", "Affichage", "Unité du vent, encarts") { page = "display" }
                        PrefMenuRow("🌊", "Prévisions", "Modèles utilisés, logs d'actualisation") { page = "forecast" }
                        if (backup != null) PrefMenuRow("📓", "Journal de bord", "Sauvegarder, restaurer") { page = "journal" }
                        if ((install != null && (!install.isInstalled() || install.platform == "android")) || onPinWidget != null) PrefMenuRow("📲", "Appli", "Installer, widget") { page = "app" }
                        PrefMenuRow("💡", "Aide", "Comprendre les prévisions, visite guidée") { page = "help" }
                    }

                    if (page == "profile") Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        SurferProfileSection(
                            surferLevel = surferLevel,
                            onLevelChanged = onSurferLevelChanged,
                            onInfo = { showScoreInfo = true }
                        )
                        HorizontalDivider(color = colors.onBackground.copy(alpha = 0.1f))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            QuiverIcon(color = colors.onBackground, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Mon matériel", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
                        }
                        GearContent(
                            quiverBoards = quiverBoards,
                            age = bodyAge,
                            heightCm = bodyHeightCm,
                            weightKg = bodyWeightKg,
                            onBodyChanged = onBodyChanged,
                            onAddBoard = onAddBoard,
                            onDeleteBoard = onDeleteBoard
                        )
                    }

                    if (page == "display") Column(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "1. Personnalisation",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.primary
                            )
                            Text(
                                text = "Choisissez les encarts affichés sur l'écran principal.",
                                fontSize = 12.sp,
                                color = colors.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(4.dp))

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
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colors.onSurfaceVariant
                                        )
                                        Text(
                                            text = "Combien d'icônes météo et vent par jour (1 = léger, 3 = détaillé)",
                                            fontSize = 11.5.sp,
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
                                                            fontSize = 12.sp,
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
                                            fontSize = 12.sp,
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
                                                            fontSize = 11.sp,
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

                    if (page == "display") Column(modifier = Modifier.fillMaxWidth()) {                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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

                    // Bouton d'épinglage du widget : Android seulement (rien à afficher sur le site).
                    if (page == "app" && onPinWidget != null) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = onPinWidget,
                                modifier = Modifier.fillMaxWidth(),
                                shape = pillShape,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.primary)
                            ) {
                                Text(text = "Épingler le Widget d'accueil", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (page == "forecast" && onBeachFacingChanged != null && spotName.isNotEmpty()) {
                        BeachFacingCard(
                            spotName = spotName,
                            facing = beachFacing,
                            defaultFacing = defaultBeachFacing,
                            onChange = onBeachFacingChanged
                        )
                    }

                    if (page == "forecast") Column(modifier = Modifier.fillMaxWidth()) {                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                    Icon(SurfIcons.Info, contentDescription = "Informations", tint = colors.primary)
                                }
                            }

                            Text(text = "⚡ Court terme (J+0 / J+1)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)

                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(text = "Vent :", fontSize = 12.sp, color = colors.onSurfaceVariant)
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
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSelected) colors.onPrimary else colors.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(text = "Vagues :", fontSize = 12.sp, color = colors.onSurfaceVariant)
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
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSelected) colors.onPrimary else colors.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(text = "📅 Long terme (J+2 / J+7)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)

                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(text = "Vent :", fontSize = 12.sp, color = colors.onSurfaceVariant)
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
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSelected) colors.onPrimary else colors.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(text = "Vagues :", fontSize = 12.sp, color = colors.onSurfaceVariant)
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
                                                    fontSize = 12.sp,
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
                                Text("Voir les logs d'actualisation", fontSize = 12.sp, color = colors.onBackground)
                            }
                        }
                    }
                    if (page == "help") {
                        PrefMenuRow("📘", "Comprendre les prévisions", "Houle, période, vent, marée, score") { page = "guide" }
                    }
                    if (page == "guide") ForecastGuideContent()
                    if (page == "help" && onShowTour != null) {
                    TextButton(onClick = onShowTour, modifier = Modifier.fillMaxWidth()) {
                        Text("🧭 Revoir la visite guidée de l'écran", fontSize = 12.sp)
                    }
                }

                    if (page == "help" && onShowIntro != null) {
                    TextButton(onClick = onShowIntro, modifier = Modifier.fillMaxWidth()) {
                        Text("ℹ️ Revoir l'introduction (unités, niveau, prévisions)", fontSize = 12.sp)
                    }
                }

                    if (page == "journal" && backup != null) {
                    var importMessage by remember { mutableStateOf<String?>(null) }
                    Text("Journal de bord", fontSize = 12.sp, color = colors.onBackground.copy(alpha = 0.7f))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { backup.export() },
                            modifier = Modifier.weight(1f),
                            shape = pillShape,
                            colors = ButtonDefaults.buttonColors(containerColor = colors.surfaceVariant)
                        ) { Text("⬇ Sauvegarder", fontSize = 12.sp, color = colors.onBackground) }
                        Button(
                            onClick = {
                                backup.import { ok ->
                                    importMessage = if (ok) "Journal restauré." else "Fichier non reconnu."
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = pillShape,
                            colors = ButtonDefaults.buttonColors(containerColor = colors.surfaceVariant)
                        ) { Text("⬆ Restaurer", fontSize = 12.sp, color = colors.onBackground) }
                    }
                    importMessage?.let { Text(it, fontSize = 12.sp, color = colors.onBackground) }
                }

                    if (page == "app" && install != null && (!install.isInstalled() || install.platform == "android")) {
                    var showIosGuide by remember { mutableStateOf(false) }
                    var showAndroidInstall by remember { mutableStateOf(false) }
                    Text("Appli", fontSize = 12.sp, color = colors.onBackground.copy(alpha = 0.7f))
                    when (install.platform) {
                        "android" -> {
                            Button(
                                onClick = { showAndroidInstall = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = pillShape,
                                colors = ButtonDefaults.buttonColors(containerColor = colors.surfaceVariant)
                            ) { Text("📲 Installer l'appli", fontSize = 12.sp, color = colors.onBackground) }
                        }
                        "ios" -> {
                            Button(
                                onClick = { showIosGuide = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = pillShape,
                                colors = ButtonDefaults.buttonColors(containerColor = colors.surfaceVariant)
                            ) { Text("📲 Ajouter à l'écran d'accueil", fontSize = 12.sp, color = colors.onBackground) }
                        }
                    }
                    if (showIosGuide) IosInstallGuideDialog(onDismiss = { showIosGuide = false })
                    if (showAndroidInstall) {
                        val apkUri = LocalUriHandler.current
                        AlertDialog(
                            onDismissRequest = { showAndroidInstall = false },
                            confirmButton = { TextButton(onClick = { showAndroidInstall = false }) { Text("Fermer") } },
                            title = { Text("Installer Surf Log", fontSize = 17.sp) },
                            text = {
                                Column(
                                    modifier = Modifier.verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(colors.primary.copy(alpha = 0.10f))
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("Recommandé sur Android", fontSize = 12.sp, color = colors.primary)
                                Text("Télécharger l'appli (APK)", fontSize = 13.sp, color = colors.onBackground)
                                Text(
                                    "Plus complète que le site : photos et vidéos dans le journal de bord, et un widget pour l'écran d'accueil.",
                                    fontSize = 12.sp,
                                    color = colors.onBackground.copy(alpha = 0.75f)
                                )
                                Image(
                                    painter = painterResource(Res.drawable.widget_preview),
                                    contentDescription = "Le widget : marée, houle et vent, avec les changements prévus",
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)),
                                    contentScale = ContentScale.FillWidth
                                )
                                Text(
                                    "Le widget affiche la marée, la houle et le vent, et prévient quand le vent forcit ou tourne.",
                                    fontSize = 12.sp,
                                    color = colors.onBackground.copy(alpha = 0.75f)
                                )
                                Button(
                                    onClick = { apkUri.openUri("https://surflog.fr/surflog.apk") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = pillShape,
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                                ) { Text("⬇ Télécharger l'APK", fontSize = 12.sp, color = colors.onPrimary) }
                                Text(
                                    "Android demandera d'autoriser l'installation depuis le navigateur. Le journal du site et celui de l'appli sont séparés : sauvegarde puis restaure pour transférer.",
                                    fontSize = 11.5.sp,
                                    color = colors.onBackground.copy(alpha = 0.6f)
                                )
                            }

                                    if (!install.isInstalled()) Text("Ou garder uniquement le site, sans télécharger :", fontSize = 12.sp, color = colors.onBackground.copy(alpha = 0.6f))
                                    if (!install.isInstalled()) Button(
                                onClick = { install.prompt() },
                                enabled = install.canPrompt(),
                                modifier = Modifier.fillMaxWidth(),
                                shape = pillShape,
                                colors = ButtonDefaults.buttonColors(containerColor = colors.surfaceVariant)
                                    ) { Text("Ajouter le raccourci du site", fontSize = 12.sp, color = colors.onBackground) }
                                    if (!install.isInstalled() && !install.canPrompt()) {
                                Text(
                                    "Si le bouton est grisé : menu ⋮ de Chrome → « Installer l'application » (ou « Ajouter à l'écran d'accueil »).",
                                    fontSize = 12.sp,
                                    color = colors.onBackground.copy(alpha = 0.6f)
                                )
                            }
                                }
                            }
                        )
                    }
                }

                }

                if (page == null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    DonationButton(modifier = Modifier.fillMaxWidth())
                }
                if (page == "forecast") {
                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            val newConfig = ForecastEngineConfig(
                            shortTermWeather = weatherModelFromLabel(shortTermWind),
                            shortTermWave = waveModelFromLabel(shortTermWave),
                            longTermWeather = weatherModelFromLabel(longTermWind),
                            longTermWave = waveModelFromLabel(longTermWave)
                        )
                        // updateEngineConfig recharge les prévisions : seulement si ça a changé.
                        if (newConfig != engineConfig) onEngineConfigChanged(newConfig)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = pillShape,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                ) {
                    Text("Appliquer et fermer", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
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
        Text(text = desc, fontSize = 12.sp, color = colors.onSurfaceVariant, lineHeight = 15.sp, modifier = Modifier.padding(start = 10.dp))
    }
}

// Libellés des pilules "Combos de prévision" <-> modèles réellement appelés.
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


@Composable
private fun PrefMenuRow(icon: String, title: String, subtitle: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) { Text(icon, fontSize = 18.sp) }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground)
            Text(subtitle, fontSize = 12.sp, color = colors.onSurfaceVariant)
        }
        Text("›", fontSize = 22.sp, color = colors.onSurfaceVariant)
    }
}
