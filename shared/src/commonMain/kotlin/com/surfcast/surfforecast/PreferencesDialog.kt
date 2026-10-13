@file:Suppress("SpellCheckingInspection")
package com.surfcast.surfforecast

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextAlign
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
    cardsOrder: List<String> = emptyList(),
    onMoveCardUp: (String) -> Unit = {},
    onMoveCardDown: (String) -> Unit = {},
    displaySize: String = "normal",
    onDisplaySizeChanged: (String) -> Unit = {},
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
    showWeatherCard: Boolean,
    onToggleWeatherCard: (Boolean) -> Unit,
    showHourlyCard: Boolean,
    onToggleHourlyCard: (Boolean) -> Unit,
    surferLevel: String,
    onSurferLevelChanged: (String) -> Unit,
    // Thème clair / sombre / auto (déplacé de l'écran principal vers Affichage).
    themeMode: String = "system",
    language: String = "fr",
    appVersion: String? = null,
    onLanguageChanged: (String) -> Unit = {},
    onThemeModeChanged: (String) -> Unit = {},
    customProfile: String = "",
    onCustomProfileSaved: (String) -> Unit = {},
    exampleAnswers: String = "",
    onExampleAnswersChanged: (String) -> Unit = {},
    levelQuiz: LevelQuizHost? = null,
    engineConfig: ForecastEngineConfig,
    onEngineConfigChanged: (ForecastEngineConfig) -> Unit,
    onViewLogs: () -> Unit,
    onDismiss: () -> Unit,
    // Épinglage du widget d'accueil : Android seulement (null = bouton masqué).
    onPinWidget: (() -> Unit)? = null,
    // Rouvre l'écran de bienvenue (unités, niveau, origine des prévisions).
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
    body: BodyState = BodyState(),
    onBodyChanged: (BodyState) -> Unit = {},
    onAddBoard: (model: String, family: String, lengthLitrage: String, finSetup: String, volumeL: Double?, volumeEstimated: Boolean) -> Unit = { _, _, _, _, _, _ -> },
    onDeleteBoard: (QuiverBoard) -> Unit = {},
    // Page à ouvrir directement (« profile » depuis la visite guidée) et état du badge « Recommandé ».
    startPage: String? = null,
    profileReviewed: Boolean = true,
    // Les annonces de chaque modèle (page des sources) et la demande de les charger.
    modelPreview: ModelPreview? = null,
    onNeedModelPreview: () -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    val pillShape = RoundedCornerShape(50)

    // Brouillon des combos de prévision, initialisé depuis la config réellement active
    // et appliqué seulement sur "Enregistrer et fermer".
    var shortTermWind by remember { mutableStateOf(weatherModelLabel(engineConfig.shortTermWeather)) }
    var shortTermWave by remember { mutableStateOf(waveModelLabel(engineConfig.shortTermWave)) }
    var longTermWind by remember { mutableStateOf(weatherModelLabel(engineConfig.longTermWeather)) }
    var longTermWave by remember { mutableStateOf(waveModelLabel(engineConfig.longTermWave)) }

    // Sources des prévisions : Auto (les modèles conseillés) ou choix à la main (modèles et orientation de la plage).
    var autoModels by remember { mutableStateOf(engineConfig == ForecastEngineConfig()) }
    var showModelsInfo by remember { mutableStateOf(false) }
    var infoTab by remember { mutableStateOf("France") }
    var showScoreInfo by remember { mutableStateOf(false) }
    // Modèle de prévision dont on affiche la fiche (petit « i » à côté de chaque modèle).
    var modelInfo by remember { mutableStateOf<String?>(null) }
    // Page ouverte : null = menu des Paramètres, sinon "display", "forecast", "journal", "app" ou "help".
    var page by remember { mutableStateOf(if (startPage == "profileQuiz") "profile" else startPage) }
    // Vrai quand le questionnaire occupe la page de profil : le bloc Matériel est alors masqué.
    var profileFocus by remember { mutableStateOf(false) }

    modelInfo?.let { name ->
        val info = forecastModelInfo(name)
        AlertDialog(
            onDismissRequest = { modelInfo = null },
            containerColor = colors.background,
            title = { Text(text = info.first, color = colors.onBackground, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = { Text(text = info.second, color = colors.onSurfaceVariant, fontSize = 13.sp, lineHeight = 18.sp) },
            confirmButton = { TextButton(onClick = { modelInfo = null }) { Text("Compris", color = colors.primary, fontWeight = FontWeight.Bold) } }
        )
    }

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
                        text = "Chaque heure recoit une note de 0 a 100, calculee a partir de l'energie de la houle (hauteur x periode), de la direction, du vent et du clapot. La maree n'entre pas dans la note.",
                        fontSize = 12.sp,
                        color = colors.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                    ModelDescItem(
                        name = "1. Énergie de la houle",
                        desc = "Plus la houle est grosse et longue (période), plus l'énergie est élevée. Une houle plate (< 0,4 m) donne 0. Un petit jour propre (0,8 m à 9 s) ouvre pour tout le monde, et la note monte avec la taille. Au-dessus de ton confort, c'est « challengeant » (orange) ; bien au-delà de ton maximum, « trop gros » (violet), pas « mauvais »."
                    )
                    ModelDescItem(
                        name = "2. Vent",
                        desc = "Offshore (de la terre) : le meilleur. Onshore (de la mer) : presque aussi bon quand il y a très peu de vent, puis de plus en plus mauvais. Side-shore : entre les deux. Plus la vague est grosse, moins le vent la gêne. Les rafales comptent à moitié de leur écart avec le vent moyen, et de fortes rafales gâchent la session."
                    )
                    ModelDescItem(
                        name = "3. Direction et clapot",
                        desc = "Une houle de face est mieux notée qu'une houle de côté. Un clapot (mer de vent) important par rapport à la houle baisse la note."
                    )
                    ModelDescItem(
                        name = "4. Ton niveau",
                        desc = "Le profil règle ta plus petite et ta plus grosse vague (le plafond « trop gros »), mais aussi la tolérance au vent, aux rafales, au clapot et à la période. La direction du vent compte aussi selon le niveau : l'offshore creuse la vague et fait les tubes, ce qu'un débutant ignore, qu'un intermédiaire apprécie avec modération et qu'un confirmé ou un expert recherche. Tout ce qui dégrade la vague ne pèse pas pareil : un expert, qui cherche le plein potentiel de la vague, y est plus sensible qu'un débutant qui prend de la mousse.\nDébutant : les mousses, petites vagues douces (jusqu'à 1,3 m à 10 s).\nIntermédiaire : commence à aller au large et à suivre les vagues (jusqu'à 1,7 m à 10 s).\nConfirmé : autonome, surfe seul, préfère un peu de puissance (jusqu'à 2 m à 11 s).\nExpert : plein potentiel de la vague, aucune limite.\nPersonnalisé : tu règles tout toi-même."
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
                                color = if (isSelected) colors.primaryContainer else colors.surfaceVariant
                            ) {
                                Box(modifier = Modifier.padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = tab,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) colors.onPrimaryContainer else colors.onSurfaceVariant
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
      // La fenêtre épouse son contenu (jusqu'à 90 % de l'écran), au lieu d'être toujours haute.
      BoxWithConstraints {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight * 0.9f)
                .clip(RoundedCornerShape(14.dp)),
            color = colors.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                SettingsTopBar(
                    title = when (page) {
                        null -> "Réglages"
                        "profile" -> "Profil"
                        "gear" -> "Matériel"
                        "forecast" -> "Sources et modèles"
                        "app" -> "Application"
                        "guide" -> "Comprendre les prévisions"
                        else -> "Aide"
                    },
                    onBack = if (page == null) null else ({ page = if (page == "gear") "profile" else null }),
                    onClose = onDismiss
                )
                if (language != "fr") {
                    Text(
                        "La traduction arrive bientôt : pour l'instant l'appli reste en français.",
                        fontSize = 11.5.sp,
                        color = colors.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))

                // Colonne défilante simple (et non LazyColumn) : le contenu est court, et la hauteur de la
                // carte APK (image chargée après coup) ne doit pas décaler le défilement.
                val scrollState = rememberScrollState()
                LaunchedEffect(page) { scrollState.scrollTo(0) }
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (page == null) {
                        SettingsSection("Mon surf") {
                            SettingsRow("Profil", "Ton niveau et ce que tu surfes", SettingsIcons.User, badge = if (profileReviewed) null else "Recommandé", top = false) { page = "profile" }
                            SettingsRow("Matériel", "Planches et planche du jour", SettingsIcons.Board) { page = "gear" }
                        }
                        SettingsSection("Prévisions") {
                            SettingsRow("Sources et modèles", "D'où viennent la houle, le vent et la météo", SettingsIcons.Layers, value = if (autoModels) "Auto" else "Manuel", top = false) { page = "forecast" }
                            SettingsField("Taille du texte", DISPLAY_SIZE_CHOICES.firstOrNull { it.first == displaySize }?.second) {
                                SettingsSegments(DISPLAY_SIZE_CHOICES, displaySize, onDisplaySizeChanged)
                            }
                        }
                        if (backup != null || (install != null && (!install.isInstalled() || install.platform == "android")) || onPinWidget != null) {
                            SettingsSection("Application") {
                                SettingsRow("Widget, installation et journal", "Installer l'appli, épingler le widget, sauvegarder le journal", SettingsIcons.Widget, top = false) { page = "app" }
                            }
                        }
                        SettingsSection("Aide") {
                            SettingsRow("Comprendre la houle", "11 leçons courtes : période, séries, modèles, bouées", SettingsIcons.Book, top = false) { ComprendreState.open() }
                            SettingsRow("Aide et langue", "Visite guidée, explications des prévisions, langue", SettingsIcons.Globe) { page = "help" }
                        }
                        if (!appVersion.isNullOrEmpty()) {
                            Text(
                                text = "Surf Log v$appVersion",
                                fontSize = 11.sp,
                                color = colors.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    if (page == "profile") Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        ProfilePage(
                            surferLevel = surferLevel,
                            onInfo = { showScoreInfo = true },
                            levelQuiz = levelQuiz,
                            startQuiz = startPage == "profileQuiz",
                            onFocusMode = { profileFocus = it }
                        )
                    }

                    if (page == "profile" && !profileFocus) SettingsSection("Matériel") {
                        SettingsRow("Mon matériel", "Planches, planche du jour, volume conseillé", SettingsIcons.Board, top = false) { page = "gear" }
                    }

                    if (page == "gear") Column(modifier = Modifier.fillMaxWidth()) {
                        GearContent(
                            quiverBoards = quiverBoards,
                            surferLevel = surferLevel,
                            body = body,
                            onBodyChanged = onBodyChanged,
                            onAddBoard = onAddBoard,
                            onDeleteBoard = onDeleteBoard
                        )
                    }

                    // Épinglage du widget : Android seulement (rien à afficher sur le site).
                    if (page == "app" && onPinWidget != null) {
                        SettingsSection("Widget") {
                            SettingsRow("Épingler le widget d'accueil", "Marée, houle et vent sur l'écran d'accueil du téléphone", SettingsIcons.Widget, top = false, onClick = onPinWidget)
                        }
                    }

                    if (page == "forecast") ForecastSourcesPage(
                        autoModels = autoModels, onAutoChanged = { autoModels = it },
                        shortWave = shortTermWave, onShortWave = { shortTermWave = it },
                        longWave = longTermWave, onLongWave = { longTermWave = it },
                        shortWind = shortTermWind, onShortWind = { shortTermWind = it },
                        longWind = longTermWind, onLongWind = { longTermWind = it },
                        preview = modelPreview, onNeedPreview = onNeedModelPreview
                    )
                    if (page == "help") {
                        SettingsSection("Langue") {
                            SettingsField("Langue", top = false) { LanguageFlags(language = language, onChange = onLanguageChanged) }
                        }
                        SettingsSection("Explications") {
                            SettingsRow("Comprendre la houle", "11 leçons courtes : période, séries, modèles, bouées", SettingsIcons.Book, top = false) { ComprendreState.open() }
                            SettingsRow("Comprendre les prévisions", "Houle, période, vent, marée, note", SettingsIcons.Chart) { page = "guide" }
                            if (onShowTour != null) SettingsRow("Revoir la visite guidée", "Les bulles qui expliquent l'écran principal", SettingsIcons.Compass, onClick = onShowTour)
                        }
                    }
                    if (page == "guide") ForecastGuideContent()

                    if (page == "app" && backup != null) {
                        var importMessage by remember { mutableStateOf<String?>(null) }
                        SettingsSection("Journal de bord") {
                            SettingsRow("Sauvegarder le journal", "Enregistre un fichier avec tes sessions", SettingsIcons.Save, top = false) { backup.export() }
                            SettingsRow("Restaurer un journal", "Reprend un fichier sauvegardé", SettingsIcons.Save, chevron = true) {
                                backup.import { ok -> importMessage = if (ok) "Journal restauré." else "Fichier non reconnu." }
                            }
                        }
                        importMessage?.let { SettingsNote(it) }
                    }

                    if (page == "app" && install != null && (!install.isInstalled() || install.platform == "android")) {
                    var showIosGuide by remember { mutableStateOf(false) }
                    var showAndroidInstall by remember { mutableStateOf(false) }
                    SettingsSection("Appli") {
                        when (install.platform) {
                            "android" -> SettingsRow("Installer l'appli", "Plus complète que le site : photos, vidéos et widget", SettingsIcons.Widget, top = false) { showAndroidInstall = true }
                            "ios" -> SettingsRow("Ajouter à l'écran d'accueil", "Le site s'ouvre comme une appli", SettingsIcons.Widget, top = false) { showIosGuide = true }
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
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.primaryContainer)
                                ) { Text("Télécharger l'APK", fontSize = 12.sp, color = colors.onPrimaryContainer) }
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
                            val newConfig = if (autoModels) ForecastEngineConfig() else ForecastEngineConfig(
                            shortTermWeather = weatherModelFromLabel(shortTermWind),
                            shortTermWave = waveModelFromLabel(shortTermWave),
                            longTermWeather = weatherModelFromLabel(longTermWind),
                            longTermWave = waveModelFromLabel(longTermWave),
                            consensus = false
                        )
                        // updateEngineConfig recharge les prévisions : seulement si ça a changé.
                        if (newConfig != engineConfig) onEngineConfigChanged(newConfig)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = pillShape,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primaryContainer)
                ) {
                    Text("Appliquer et fermer", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
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
            colors = SwitchDefaults.colors(checkedThumbColor = colors.onPrimaryContainer, checkedTrackColor = colors.primaryContainer)
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
    WeatherModel.GFS -> "GFS"
}

private fun weatherModelFromLabel(label: String): WeatherModel = when (label) {
    "ARPEGE" -> WeatherModel.ARPEGE
    "ECMWF" -> WeatherModel.ECMWF_IFS
    "GFS" -> WeatherModel.GFS
    else -> WeatherModel.AROME
}

private fun waveModelLabel(model: WaveModel): String = when (model) {
    WaveModel.MFWAM -> "MF-WAM"
    WaveModel.ECMWF_WAM -> "ECMWF Wave"
    WaveModel.GFS_WAVE -> "GFS Wave"
}

private fun waveModelFromLabel(label: String): WaveModel =
    when (label) { "ECMWF Wave" -> WaveModel.ECMWF_WAM; "GFS Wave" -> WaveModel.GFS_WAVE; else -> WaveModel.MFWAM }


@Composable
private fun PrefMenuRow(icon: String, title: String, subtitle: String, badge: String? = null, onClick: () -> Unit) {
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground)
                if (badge != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        badge, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = colors.onPrimaryContainer,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(colors.primaryContainer).padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            Text(subtitle, fontSize = 12.sp, color = colors.onSurfaceVariant)
        }
        Text("›", fontSize = 22.sp, color = colors.onSurfaceVariant)
    }
}


/** Fiche d'un modèle de prévision : (titre, explication) pour les curieux. */
internal fun forecastModelInfo(model: String): Pair<String, String> = when (model) {
    "AROME" -> "AROME (Météo-France)" to
        "Modèle de vent à maille très fine (1,3 km sur la France) : il voit les brises côtières, les thermiques et les effets du relief. " +
        "C'est le plus précis pour le vent d'aujourd'hui et de demain. Il ne regarde que 2 à 3 jours devant."
    "ARPEGE" -> "ARPEGE (Météo-France)" to
        "Modèle global de Météo-France, plus grossier (11 km sur l'Europe) mais qui voit plus loin (environ 4 jours). " +
        "Un bon repli quand AROME ne couvre pas la date ou le spot."
    "ECMWF" -> "ECMWF IFS (centre européen)" to
        "Le modèle du Centre européen de prévision à moyen terme (9 km), l'un des meilleurs au monde. Très stable, il prévoit jusqu'à 10 jours. " +
        "Moins fin que AROME sur les brises locales."
    "GFS" -> "GFS (NOAA)" to
        "Le modèle américain (13 km), qui voit loin (plus d'une semaine). En mode Auto, le vent est la moyenne de ECMWF et de GFS : " +
        "deux modèles indépendants qui se corrigent l'un l'autre."
    "MF-WAM" -> "MF-WAM (Météo-France)" to
        "Modèle de vagues de Météo-France (8 km). Bon sur les côtes françaises : il tient compte des fonds et des effets côtiers, " +
        "donc de la houle telle qu'elle arrive près du rivage."
    "ECMWF Wave" -> "ECMWF Wave (WAM, centre européen)" to
        "Modèle de vagues du Centre européen (14 km). La référence pour la houle du grand large, les longues périodes et l'énergie océanique. " +
        "Moins précis tout près de la côte."
    "GFS Wave" -> "GFS Wave (NOAA, WaveWatch III)" to
        "Le modèle de vagues américain (16 km), celui de Windguru. Il voit loin, mais sur notre côte il surestime la période d'environ 2 s : " +
        "c'est pourquoi l'Auto le met de côté à court terme."
    else -> model to "Modèle de prévision."
}

/** Les drapeaux de langue, juste à côté de « Paramètres » : un geste, sans passer par l'onglet Affichage. */
@Composable
private fun LanguageFlags(language: String, onChange: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        APP_LANGUAGES.forEach { lang ->
            val selected = language == lang.code
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) colors.primary.copy(alpha = 0.25f) else Color.Transparent)
                    .clickable { onChange(lang.code) }
                    .padding(horizontal = 5.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                FlagIcon(lang.code, if (selected) 20.dp else 17.dp)
            }
        }
    }
}


/** Une rangée dense des réglages : icône, titre, flèche (badge facultatif). */
@Composable
private fun DenseRow(icon: String, title: String, badge: String? = null, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.surfaceVariant)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 16.sp)
        Spacer(modifier = Modifier.width(10.dp))
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground, modifier = Modifier.weight(1f))
        if (badge != null) {
            Text(
                badge, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = colors.onPrimaryContainer,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(colors.primaryContainer).padding(horizontal = 8.dp, vertical = 2.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text("›", fontSize = 20.sp, color = colors.onSurfaceVariant)
    }
}

/** Une rangée dense avec le choix directement dessus (taille, unité du vent). */
@Composable
private fun DenseChoiceRow(icon: String, title: String, choices: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.surfaceVariant).padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon == "Aa") Text("Aa", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = colors.primary) else Text(icon, fontSize = 16.sp)
        Spacer(modifier = Modifier.width(10.dp))
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground, modifier = Modifier.weight(1f))
        ChipChoices(choices, selected, onSelect, small = true, modifier = Modifier.weight(2f))
    }
}

@Composable
private fun ChipChoices(
    choices: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit,
    small: Boolean = false, modifier: Modifier = Modifier.fillMaxWidth()
) {
    val colors = MaterialTheme.colorScheme
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        choices.forEach { (key, label) ->
            val isSelected = selected == key
            Box(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(50))
                    .background(if (isSelected) colors.primaryContainer else if (small) colors.background else colors.surfaceVariant)
                    .clickable { onSelect(key) }.padding(vertical = if (small) 6.dp else 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, fontSize = if (small) 11.sp else 12.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                    color = if (isSelected) colors.onPrimaryContainer else colors.onSurfaceVariant)
            }
        }
    }
}

/** Aperçu en temps réel de la place du graphique : le ciel et la mer d'une carte de la semaine, aux proportions réglées. */
@Composable
private fun GraphPreview(weekPct: Int, dayPct: Int, waterPct: Int) {
    val colors = MaterialTheme.colorScheme
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf("Semaine" to weekPct, "Jour" to dayPct).forEach { (name, pct) ->
            Column(modifier = Modifier.weight(1f)) {
                Text(name, fontSize = 11.sp, color = colors.onSurfaceVariant)
                Column(modifier = Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(10.dp)).background(colors.surfaceVariant)) {
                    Box(modifier = Modifier.fillMaxWidth().weight(100f - pct).background(Color(0xFF9CC9F0)))
                    Box(modifier = Modifier.fillMaxWidth().weight(pct.toFloat()).background(Color(0xFF29B6F6).copy(alpha = 0.55f)), contentAlignment = Alignment.TopCenter) {
                        Box(modifier = Modifier.fillMaxWidth().padding(top = (waterPct / 5).dp).height(2.dp).background(colors.onBackground.copy(alpha = 0.5f)))
                    }
                }
            }
        }
    }
}

/** Aperçu en temps réel de trois jours de la semaine : indicateurs de vent et icônes par jour. */
@Composable
private fun WeekPreview(windMode: String, density: Int) {
    val colors = MaterialTheme.colorScheme
    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.surfaceVariant).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("Auj. 12" to "SO 14", "Dem. 13" to "O 9", "Mer. 14" to "NO 18").forEach { (day, wind) ->
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(day, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
                Text(listOf("☀️", "⛅", "☁️").take(density).joinToString(" "), fontSize = 13.sp)
                if (windMode != "none") {
                    val txt = when (windMode) { "arrow" -> "↗"; "text" -> wind; else -> "↗ $wind" }
                    Text(txt, fontSize = 12.sp, color = colors.onBackground)
                }
            }
        }
    }
}
