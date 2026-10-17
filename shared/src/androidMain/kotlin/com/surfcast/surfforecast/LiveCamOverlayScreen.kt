package com.surfcast.surfforecast

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.surfcast.surfforecast.ui.theme.AppColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun Context.findActivity(): Activity? {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LiveCamOverlayScreen(
    currentSpotName: String,
    onClose: () -> Unit,
    onSwitchSpot: (String) -> Unit = {},
    // Bandeau "conditions actuelles" par-dessus la webcam (même bandeau que l'écran principal),
    // pour comparer ce qu'on voit avec la prévision.
    showLiveOverlay: Boolean = true,
    windUnit: String = "kmh",
    loadLiveConditions: suspend (String) -> LiveConditions? = { null }
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val scope = rememberCoroutineScope()

    // Restaure l'orientation d'origine seulement a la fermeture complete de l'ecran.
    DisposableEffect(Unit) {
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            activity?.requestedOrientation = originalOrientation
        }
    }

    BackHandler { onClose() }

    var refreshTrigger by remember(currentSpotName) { mutableIntStateOf(0) }

    val spotWebcams = remember(currentSpotName, refreshTrigger) {
        SurfWebcamHelper.getCamerasForSpot(currentSpotName, SharedPreferencesStore(context))
    }

    // Correctif : une closure creee une seule fois (dans factory) ne voit jamais
    // les changements ulterieurs de currentSpotName. rememberUpdatedState garantit
    // que le code a l'interieur de la WebView lit toujours le spot reellement affiche.
    val currentSpotNameState = rememberUpdatedState(currentSpotName)

    if (spotWebcams.cameras.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.width(280.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Aucune webcam disponible pour ce spot.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(onClick = onClose) { Text("Fermer") }
                }
            }
        }
        return
    }

    var selectedCamIndex by remember(currentSpotName) { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var realPageTitle by remember { mutableStateOf<String?>(null) }
    var manualNavigation by remember(currentSpotName) { mutableStateOf(false) }

    // Paysage pour regarder une vraie webcam (plein ecran, immersif) ; portrait pendant
    // la recherche sur gosurf.fr, pour voir leur vraie barre de recherche native, sinon
    // elle est coupee en haut de l'ecran en mode paysage.
    LaunchedEffect(manualNavigation) {
        activity?.requestedOrientation = if (manualNavigation) {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }
    var searchTargetCamName by remember(currentSpotName) { mutableStateOf("") }
    val searchTargetCamNameState = rememberUpdatedState(searchTargetCamName)
    var showLinkSuccess by remember(currentSpotName) { mutableStateOf(false) }

    val activeCamera = spotWebcams.cameras.getOrElse(selectedCamIndex) { spotWebcams.cameras.first() }
    // Meme raison que currentSpotNameState/searchTargetCamNameState : le WebViewClient
    // est cree une seule fois dans factory, donc un simple val ne refleterait jamais
    // les changements de camera (ex. selection d'une autre webcam dans la liste).
    val activeCameraState = rememberUpdatedState(activeCamera)

    val mismatchTitle = realPageTitle
    // Decoupe sur espaces ET tirets (utile pour les noms composes, ex "Le Grand Crohot")
    // et ne garde que les mots assez distinctifs (>= 4 lettres) pour eviter les faux positifs.
    val looksWrong = mismatchTitle != null && run {
        val tokens = currentSpotName.split(" ", "-").filter { it.length >= 4 }
        val titleLower = mismatchTitle.lowercase()
        tokens.isNotEmpty() && tokens.none { titleLower.contains(it.lowercase()) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // En recherche GoSurf (portrait), on respecte la barre de statut du telephone
            // pour ne pas que leur barre de recherche soit cachee dessous. En mode camera
            // plein ecran paysage, pas besoin (la barre de statut est generalement masquee).
            .then(if (manualNavigation) Modifier.padding(top = 56.dp) else Modifier)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(android.graphics.Color.BLACK)

                    // ViewSurf charge son lecteur via une iframe tierce (joada.net) :
                    // sans ceci, WebView bloque par defaut les cookies tiers necessaires
                    // a cette iframe, ce qui laisse un ecran gris / lecteur casse.
                    val cookieManager = android.webkit.CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(this, true)

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        cacheMode = WebSettings.LOAD_DEFAULT
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        displayZoomControls = false
                        builtInZoomControls = false
                        userAgentString = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                    }

                    // Empeche Android d'appliquer un assombrissement automatique sur les
                    // pages tierces (GoSurf) : elles doivent toujours s'afficher dans leur
                    // vrai theme clair, sinon le texte devient illisible.
                    @Suppress("DEPRECATION")
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        settings.forceDark = WebSettings.FORCE_DARK_OFF
                    }

                    webChromeClient = WebChromeClient()
                    webViewClient = object : WebViewClient() {
                        private val mainHandler = Handler(Looper.getMainLooper())

                        private fun injectFullscreenJS(view: WebView?) {
                            view?.evaluateJavascript(SurfWebcamHelper.CLEAN_FULLSCREEN_JS, null)
                        }

                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            loadError = false
                            realPageTitle = null

                            if (manualNavigation && url != null && !url.endsWith("gosurf.fr/list") && url.contains("/webcam/")) {
                                SurfWebcamHelper.saveOverride(SharedPreferencesStore(context), currentSpotNameState.value, searchTargetCamNameState.value, url)
                                manualNavigation = false
                                refreshTrigger++
                            }

                            if (!manualNavigation) {
                                injectFullscreenJS(view)
                            }
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            if (manualNavigation) {
                                view?.scrollTo(0, 0)
                                view?.evaluateJavascript(SurfWebcamHelper.COOKIE_REJECT_ONLY_JS, null)
                                view?.evaluateJavascript(SurfWebcamHelper.FORCE_LIGHT_THEME_JS, null)
                                view?.evaluateJavascript("window.scrollTo(0,0);", null)
                                mainHandler.postDelayed({
                                    view?.scrollTo(0, 0)
                                    view?.evaluateJavascript("window.scrollTo(0,0);", null)
                                }, 300)
                                mainHandler.postDelayed({
                                    view?.evaluateJavascript(SurfWebcamHelper.buildGoSurfHighlightJs(currentSpotNameState.value), null)
                                }, 500)
                                mainHandler.postDelayed({ isLoading = false }, 600)
                            } else {
                                // Correctif : les URLs directes du catalogue (ex. Capbreton) pointent
                                // vers un identifiant GoSurf precis, qui devient parfois perime (GoSurf
                                // renumerote ses pages). GoSurf redirige alors en silence vers son annuaire
                                // general (reponse 200, pas d'erreur HTTP) : sans ce controle, l'appli
                                // affichait cet annuaire generique sans jamais lancer la recherche assistee.
                                // On detecte ce cas via l'URL finale (une vraie page camera contient toujours
                                // "/webcam/") plutot que via le seul titre, plus fiable et sans attendre un clic.
                                val finalUrl = url ?: ""
                                val isGoSurfCamera = activeCameraState.value.pageUrl.contains("gosurf.fr")
                                val landedOnDirectory = isGoSurfCamera &&
                                    finalUrl.contains("gosurf.fr") &&
                                    !finalUrl.contains("/webcam/")
                                if (isGoSurfCamera && (loadError || landedOnDirectory)) {
                                    searchTargetCamName = activeCameraState.value.camName
                                    isLoading = true
                                    loadError = false
                                    manualNavigation = true
                                    view?.settings?.useWideViewPort = false
                                    view?.settings?.loadWithOverviewMode = false
                                    view?.loadUrl("https://gosurf.fr/list")
                                    return
                                }

                                injectFullscreenJS(view)
                                val retryDelays = listOf(300L, 800L, 1600L, 3000L)
                                retryDelays.forEach { delay ->
                                    mainHandler.postDelayed({ injectFullscreenJS(view) }, delay)
                                }
                                mainHandler.postDelayed({ isLoading = false }, 600)
                            }
                            view?.evaluateJavascript("document.title") { result ->
                                realPageTitle = result?.trim('"')?.takeIf { it.isNotBlank() && it != "null" }
                            }
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: android.webkit.WebResourceError?
                        ) {
                            super.onReceivedError(view, request, error)
                            if (request == null || request.isForMainFrame) {
                                isLoading = false
                                loadError = true
                            }
                        }

                        override fun onReceivedHttpError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            errorResponse: WebResourceResponse?
                        ) {
                            super.onReceivedHttpError(view, request, errorResponse)
                            if (request != null && request.isForMainFrame) {
                                isLoading = false
                                loadError = true
                            }
                        }

                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): WebResourceResponse? {
                            val url = request?.url?.toString() ?: ""
                            val adPatterns = listOf("googleads", "doubleclick", "adnxs", "criteo", "outbrain", "taboola", "smartadserver")
                            return if (adPatterns.any { url.contains(it) }) {
                                WebResourceResponse("text/plain", "UTF-8", null)
                            } else {
                                super.shouldInterceptRequest(view, request)
                            }
                        }
                    }

                    loadUrl(activeCamera.pageUrl)
                    webViewRef = this
                }
            },
            update = { webView ->
                // En recherche GoSurf, on laisse leur page s'afficher dans sa vraie mise en
                // page mobile (claire) plutot que de forcer une largeur "bureau" qui peut
                // declencher une autre feuille de style chez eux.
                webView.settings.useWideViewPort = !manualNavigation
                webView.settings.loadWithOverviewMode = !manualNavigation

                if (!manualNavigation && webView.url != activeCamera.pageUrl) {
                    isLoading = true
                    loadError = false
                    webView.loadUrl(activeCamera.pageUrl)
                }
            }
        )

        if (isLoading && !loadError) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }

        var mismatchDismissed by remember(currentSpotName, selectedCamIndex) { mutableStateOf(false) }

        if (looksWrong && !isLoading && !loadError && !manualNavigation && !mismatchDismissed) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.width(310.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xF01A1E24),
                    border = BorderStroke(1.dp, AppColors.WindMid.copy(alpha = 0.5f))
                ) {
                    Box {
                        Column(
                            modifier = Modifier.padding(22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = SurfIcons.Warning,
                                contentDescription = null,
                                tint = AppColors.WindMid,
                                modifier = Modifier.size(30.dp)
                            )
                            Text(
                                text = "Ce n'est pas la bonne webcam",
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 10.dp)
                            )
                            Text(
                                text = "La page affiche « ${mismatchTitle ?: ""} » au lieu de ${currentSpotNameState.value}.",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 6.dp, bottom = 16.dp)
                            )
                            Button(
                                onClick = {
                                    searchTargetCamName = activeCamera.camName
                                    webViewRef?.settings?.useWideViewPort = false
                                    webViewRef?.settings?.loadWithOverviewMode = false
                                    try {
                                        val cm = android.webkit.CookieManager.getInstance()
                                        cm.setCookie("https://gosurf.fr", "expired=1; Max-Age=0")
                                        cm.flush()
                                    } catch (e: Exception) { }
                                    isLoading = true
                                    loadError = false
                                    manualNavigation = true
                                    webViewRef?.loadUrl("https://gosurf.fr/list")
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = AppColors.WindMid)
                            ) {
                                Text("Chercher sur GoSurf.fr", fontWeight = FontWeight.Bold, color = Color.Black)
                            }
                            TextButton(onClick = onClose, modifier = Modifier.padding(top = 2.dp)) {
                                Text("Fermer la webcam", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                            }
                        }

                        IconButton(
                            onClick = { mismatchDismissed = true },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .size(28.dp)
                        ) {
                            Icon(
                                imageVector = SurfIcons.Close,
                                contentDescription = "Ignorer",
                                tint = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        if (loadError) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.75f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.width(300.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = SurfIcons.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "Webcam indisponible",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        Text(
                            text = "Impossible de charger le flux. Vérifiez votre connexion ou réessayez plus tard.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp, bottom = 16.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TextButton(onClick = onClose) {
                                Text("Fermer")
                            }
                            Button(
                                onClick = {
                                    loadError = false
                                    isLoading = true
                                    webViewRef?.reload()
                                }
                            ) {
                                Text("Réessayer")
                            }
                        }
                    }
                }
            }
        }

        // Barre du haut, une seule ligne : le spot, les favoris (un toucher change de spot, appui long = placer le spot ici),
        // « Spots ▾ » pour les spots proches, « ⋯ » pour lier la page. Les caméras du spot sont dessous si le spot en a plusieurs.
        val nearbySpots = remember(currentSpotName) { SurfWebcamHelper.nearbyCameraSpots(currentSpotName) }
        var favSlotsVersion by remember { mutableIntStateOf(0) }
        val favorites = remember(currentSpotName, favSlotsVersion) {
            SurfWebcamHelper.getWebcamFavorites(SharedPreferencesStore(context))
        }
        var spotsMenuOpen by remember { mutableStateOf(false) }
        var moreMenuOpen by remember { mutableStateOf(false) }

        if (!manualNavigation) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 28.dp, top = 12.dp, end = 64.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(modifier = Modifier.clip(RoundedCornerShape(20.dp)), color = Color.Black.copy(alpha = 0.75f)) {
                        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(Color(0xFFE53935)))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = spotWebcams.spotDisplayName, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                        }
                    }
                    Row(
                        modifier = Modifier.weight(1f, fill = false).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (slot in 0..3) {
                            val favName = favorites.getOrNull(slot)
                            CamChip(
                                text = favName ?: "＋",
                                active = favName?.equals(currentSpotName, ignoreCase = true) == true,
                                dim = favName == null,
                                onClick = { if (favName != null) onSwitchSpot(favName) },
                                onLongClick = {
                                    SurfWebcamHelper.setWebcamFavorite(SharedPreferencesStore(context), slot, currentSpotNameState.value)
                                    favSlotsVersion++
                                }
                            )
                        }
                        if (nearbySpots.size > 1) {
                            Box {
                                CamChip(text = "Spots ▾", active = false, onClick = { spotsMenuOpen = true })
                                DropdownMenu(expanded = spotsMenuOpen, onDismissRequest = { spotsMenuOpen = false }) {
                                    nearbySpots.forEach { name ->
                                        DropdownMenuItem(
                                            text = { Text(name, fontWeight = if (name.equals(currentSpotName, ignoreCase = true)) FontWeight.Bold else FontWeight.Normal) },
                                            onClick = {
                                                spotsMenuOpen = false
                                                if (!name.equals(currentSpotName, ignoreCase = true)) onSwitchSpot(name)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        Box {
                            CamChip(text = "⋯", active = showLinkSuccess, onClick = { moreMenuOpen = true })
                            DropdownMenu(expanded = moreMenuOpen, onDismissRequest = { moreMenuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text(if (showLinkSuccess) "Lien enregistré !" else "Lier cette page à ${activeCamera.camName}") },
                                    onClick = {
                                        moreMenuOpen = false
                                        val activeUrl = webViewRef?.url
                                        if (!activeUrl.isNullOrBlank() && !activeUrl.startsWith("data:") && !activeUrl.contains("about:blank")) {
                                            SurfWebcamHelper.saveOverride(SharedPreferencesStore(context), currentSpotNameState.value, activeCamera.camName, activeUrl)
                                            showLinkSuccess = true
                                            refreshTrigger++
                                            scope.launch { delay(3000); showLinkSuccess = false }
                                        }
                                    }
                                )
                                realPageTitle?.let { title -> DropdownMenuItem(text = { Text("Source : $title", fontSize = 11.sp) }, enabled = false, onClick = {}) }
                            }
                        }
                    }
                }
                if (spotWebcams.cameras.size > 1) {
                    Row(
                        modifier = Modifier.padding(top = 6.dp).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        spotWebcams.cameras.forEachIndexed { index, cam ->
                            CamChip(
                                text = cam.camName, active = index == selectedCamIndex,
                                onClick = {
                                    selectedCamIndex = index
                                    isLoading = true
                                    loadError = false
                                    manualNavigation = false
                                }
                            )
                        }
                    }
                }
            }
        }

        // Conditions actuelles du spot de la webcam (houle, marée, vent), au-dessus des spots proches.
        // Se recharge à chaque changement de spot ; rien tant que les données ne sont pas là.
        val liveConditions by produceState<LiveConditions?>(initialValue = null, currentSpotName) {
            value = null
            value = loadLiveConditions(currentSpotName)
        }
        if (showLiveOverlay && !manualNavigation) {
            liveConditions?.let { c ->
                SurfLiveStripOverlay(
                    hourlyModel = c.hour,
                    tideInfo = c.tide,
                    windUnit = windUnit,
                    onOpenCam = {},
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                        .fillMaxWidth(0.7f)
                        .widthIn(max = 460.dp)
                )
            }
        }

        // Bouton fermer en haut a droite
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .size(38.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.7f))
        ) {
            Icon(
                imageVector = SurfIcons.Close,
                contentDescription = "Fermer",
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }

    }
}
/** Une pastille de la barre du haut : fond sombre, bordure claire quand elle est active ; l'appui long est facultatif. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CamChip(text: String, active: Boolean, onClick: () -> Unit, onLongClick: (() -> Unit)? = null, dim: Boolean = false) {
    Surface(
        modifier = Modifier.clip(RoundedCornerShape(16.dp)).combinedClickable(onClick = onClick, onLongClick = onLongClick),
        color = if (active) AppColors.WindMid.copy(alpha = 0.3f) else Color.Black.copy(alpha = if (dim) 0.4f else 0.7f),
        border = if (active) BorderStroke(1.5.dp, AppColors.WindMid) else null
    ) {
        Text(
            text = text, fontSize = 12.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1,
            color = if (active) AppColors.WindMid else Color.White.copy(alpha = if (dim) 0.5f else 1f),
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp)
        )
    }
}
