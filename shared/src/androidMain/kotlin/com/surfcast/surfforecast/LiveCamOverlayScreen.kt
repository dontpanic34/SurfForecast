package com.surfcast.surfforecast

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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

private val LiveRed = Color(0xFFE53935)
private val StarGold = Color(0xFFF5B50A)

/**
 * Écran webcam (refonte du 13 novembre, maquette `docs/maquettes/webcam-refonte.html`, façon Rider Report).
 *
 * - Page en portrait : le spot regardé en premier (celui depuis lequel on ouvre la webcam), grand lecteur,
 *   conditions du moment, caméras du spot, spots autour avec leur distance, « Toutes les webcams » avec recherche.
 *   Les favoris sont des pastilles sous le titre ; l'étoile ajoute ou retire le spot.
 * - Plein écran (bouton ⤢ ou téléphone tourné) : la vidéo seule, nom de la caméra, croix, flèches entre caméras et
 *   la barre houle / marée / vent ; les commandes se masquent seules et reviennent au toucher.
 * - Le lecteur officiel Viewsurf (pv.viewsurf.com) ne contient que la vidéo : rien à nettoyer. Les autres pages
 *   (GoSurf, Médoc Atlantique) gardent le nettoyage par script, la recherche GoSurf et « Lier cette page ».
 *
 * Un seul WebView pour tout l'écran : il passe de la page au plein écran sans recharger le flux.
 */
@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun LiveCamOverlayScreen(
    currentSpotName: String,
    onClose: () -> Unit,
    onSwitchSpot: (String) -> Unit = {},
    // Barre « conditions actuelles » (houle, marée, vent), la même que sur l'écran principal.
    showLiveOverlay: Boolean = true,
    windUnit: String = "kmh",
    loadLiveConditions: suspend (String) -> LiveConditions? = { null }
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val prefs = remember(context) { SharedPreferencesStore(context) }
    val scope = rememberCoroutineScope()
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    var refreshTrigger by remember(currentSpotName) { mutableIntStateOf(0) }
    val spotWebcams = remember(currentSpotName, refreshTrigger) {
        SurfWebcamHelper.getCamerasForSpot(currentSpotName, prefs)
    }
    var selectedCamIndex by remember(currentSpotName) { mutableIntStateOf(0) }
    val activeCamera = spotWebcams.cameras.getOrNull(selectedCamIndex) ?: spotWebcams.cameras.firstOrNull()

    var fullscreenRequested by remember { mutableStateOf(false) }
    // Après la croix du plein écran, on garde le portrait (sinon un téléphone encore tenu en paysage y retournerait aussitôt).
    var keepPortrait by remember { mutableStateOf(false) }
    var manualNavigation by remember(currentSpotName) { mutableStateOf(false) }
    val fullscreen = activeCamera != null && !manualNavigation && (fullscreenRequested || (isLandscape && !keepPortrait))

    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf(false) }
    var realPageTitle by remember { mutableStateOf<String?>(null) }
    var searchTargetCamName by remember(currentSpotName) { mutableStateOf("") }
    var showLinkSuccess by remember(currentSpotName) { mutableStateOf(false) }
    var showAllCams by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var controlsTouch by remember { mutableIntStateOf(0) }
    var favVersion by remember { mutableIntStateOf(0) }
    var favMessage by remember { mutableStateOf<String?>(null) }

    val currentSpotNameState = rememberUpdatedState(currentSpotName)
    val searchTargetCamNameState = rememberUpdatedState(searchTargetCamName)
    val activeCameraState = rememberUpdatedState(activeCamera)
    val manualNavigationState = rememberUpdatedState(manualNavigation)

    // Orientation : libre sur la page (tourner le téléphone ouvre le plein écran), paysage forcé en plein écran.
    DisposableEffect(Unit) {
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        onDispose {
            activity?.requestedOrientation = originalOrientation
            activity?.window?.let { w ->
                WindowCompat.getInsetsController(w, w.decorView).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
    LaunchedEffect(fullscreenRequested, keepPortrait, manualNavigation) {
        activity?.requestedOrientation = when {
            fullscreenRequested && !manualNavigation -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            keepPortrait || manualNavigation -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    // Plein écran immersif : barres du système masquées (elles reviennent d'un glissement du bord).
    LaunchedEffect(fullscreen) {
        val w = activity?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(w, w.decorView)
        if (fullscreen) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    // Les commandes du plein écran se masquent après 4 s sans toucher.
    LaunchedEffect(fullscreen, controlsTouch, selectedCamIndex) {
        controlsVisible = true
        if (fullscreen) {
            delay(4000)
            controlsVisible = false
        }
    }

    fun exitFullscreen() {
        fullscreenRequested = false
        keepPortrait = true
    }
    fun enterFullscreen() {
        keepPortrait = false
        fullscreenRequested = true
    }
    fun selectCamera(index: Int) {
        if (index == selectedCamIndex) return
        selectedCamIndex = index
        isLoading = true
        loadError = false
        manualNavigation = false
    }

    BackHandler {
        when {
            showAllCams -> showAllCams = false
            manualNavigation -> { manualNavigation = false; refreshTrigger++ }
            fullscreen -> exitFullscreen()
            else -> onClose()
        }
    }

    // Le WebView unique de l'écran.
    val webView = remember {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(android.graphics.Color.BLACK)
            // Viewsurf charge son lecteur via des services tiers : sans les cookies tiers, le lecteur reste gris.
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
            // Pas d'assombrissement automatique des pages tierces (GoSurf doit rester lisible dans son thème clair).
            @Suppress("DEPRECATION")
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                settings.forceDark = WebSettings.FORCE_DARK_OFF
            }
            // Un toucher sur la vidéo fait revenir les commandes du plein écran (le WebView garde le toucher).
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_UP) controlsTouch++
                false
            }
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                private val mainHandler = Handler(Looper.getMainLooper())

                private fun prepare(view: WebView?) {
                    val cam = activeCameraState.value ?: return
                    view?.evaluateJavascript(
                        if (cam.isViewsurfPlayer) AUTOPLAY_JS else SurfWebcamHelper.CLEAN_FULLSCREEN_JS,
                        null
                    )
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    loadError = false
                    realPageTitle = null
                    if (manualNavigationState.value && url != null && !url.endsWith("gosurf.fr/list") && url.contains("/webcam/")) {
                        // Recherche GoSurf : la page choisie devient l'adresse de cette caméra.
                        SurfWebcamHelper.saveOverride(prefs, currentSpotNameState.value, searchTargetCamNameState.value, url)
                        manualNavigation = false
                        refreshTrigger++
                    }
                    if (!manualNavigationState.value) prepare(view)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    if (manualNavigationState.value) {
                        view?.evaluateJavascript(SurfWebcamHelper.COOKIE_REJECT_ONLY_JS, null)
                        view?.evaluateJavascript(SurfWebcamHelper.FORCE_LIGHT_THEME_JS, null)
                        view?.evaluateJavascript("window.scrollTo(0,0);", null)
                        mainHandler.postDelayed({
                            view?.evaluateJavascript(SurfWebcamHelper.buildGoSurfHighlightJs(currentSpotNameState.value), null)
                        }, 500)
                        mainHandler.postDelayed({ isLoading = false }, 600)
                    } else {
                        // Une adresse GoSurf périmée redirige en silence vers l'annuaire (réponse 200) : on lance alors
                        // la recherche assistée plutôt que d'afficher l'annuaire générique.
                        val cam = activeCameraState.value
                        val finalUrl = url ?: ""
                        val isGoSurfCamera = cam?.pageUrl?.contains("gosurf.fr") == true
                        val landedOnDirectory = isGoSurfCamera && finalUrl.contains("gosurf.fr") && !finalUrl.contains("/webcam/")
                        if (isGoSurfCamera && (loadError || landedOnDirectory)) {
                            searchTargetCamName = cam?.camName ?: ""
                            isLoading = true
                            loadError = false
                            manualNavigation = true
                            view?.settings?.useWideViewPort = false
                            view?.settings?.loadWithOverviewMode = false
                            view?.loadUrl("https://gosurf.fr/list")
                            return
                        }
                        prepare(view)
                        listOf(300L, 800L, 1600L, 3000L).forEach { d -> mainHandler.postDelayed({ prepare(view) }, d) }
                        mainHandler.postDelayed({ isLoading = false }, 600)
                    }
                    view?.evaluateJavascript("document.title") { result ->
                        realPageTitle = result?.trim('"')?.takeIf { it.isNotBlank() && it != "null" }
                    }
                }

                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: android.webkit.WebResourceError?) {
                    super.onReceivedError(view, request, error)
                    if (request == null || request.isForMainFrame) {
                        isLoading = false
                        loadError = true
                    }
                }

                override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
                    super.onReceivedHttpError(view, request, errorResponse)
                    if (request != null && request.isForMainFrame) {
                        isLoading = false
                        loadError = true
                    }
                }

                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                    val url = request?.url?.toString() ?: ""
                    val adPatterns = listOf("googleads", "doubleclick", "adnxs", "criteo", "outbrain", "taboola", "smartadserver")
                    return if (adPatterns.any { url.contains(it) }) WebResourceResponse("text/plain", "UTF-8", null)
                    else super.shouldInterceptRequest(view, request)
                }
            }
        }
    }
    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        }
    }

    // Charge la caméra choisie (sauf pendant la recherche GoSurf, qui pilote elle-même le WebView).
    LaunchedEffect(activeCamera?.pageUrl, manualNavigation) {
        val url = activeCamera?.pageUrl ?: return@LaunchedEffect
        webView.settings.useWideViewPort = !manualNavigation
        webView.settings.loadWithOverviewMode = !manualNavigation
        if (!manualNavigation && webView.url != url) {
            isLoading = true
            loadError = false
            webView.loadUrl(url)
        }
    }

    val liveConditions by produceState<LiveConditions?>(initialValue = null, currentSpotName) {
        value = null
        value = loadLiveConditions(currentSpotName)
    }

    // Une page qui affiche un autre spot que celui demandé (GoSurf renumérote parfois ses pages).
    val pageTitle = realPageTitle
    val looksWrong = activeCamera?.isViewsurfPlayer == false && pageTitle != null && run {
        val tokens = currentSpotName.split(" ", "-").filter { it.length >= 4 }
        val titleLower = pageTitle.lowercase()
        tokens.isNotEmpty() && tokens.none { titleLower.contains(it.lowercase()) }
    }

    fun startGoSurfSearch() {
        val cam = activeCamera ?: return
        searchTargetCamName = cam.camName
        try {
            val cm = android.webkit.CookieManager.getInstance()
            cm.setCookie("https://gosurf.fr", "expired=1; Max-Age=0")
            cm.flush()
        } catch (_: Exception) { }
        isLoading = true
        loadError = false
        manualNavigation = true
        webView.settings.useWideViewPort = false
        webView.settings.loadWithOverviewMode = false
        webView.loadUrl("https://gosurf.fr/list")
    }

    fun linkCurrentPage() {
        val cam = activeCamera ?: return
        val activeUrl = webView.url
        if (!activeUrl.isNullOrBlank() && !activeUrl.startsWith("data:") && !activeUrl.contains("about:blank")) {
            SurfWebcamHelper.saveOverride(prefs, currentSpotName, cam.camName, activeUrl)
            showLinkSuccess = true
            refreshTrigger++
            scope.launch { delay(3000); showLinkSuccess = false }
        }
    }

    when {
        activeCamera == null -> NoCameraPage(currentSpotName, onClose, onSwitchSpot)
        manualNavigation -> GoSurfSearchPage(
            webView = webView,
            camName = searchTargetCamName.ifBlank { currentSpotName },
            isLoading = isLoading,
            onCancel = { manualNavigation = false; refreshTrigger++ }
        )
        fullscreen -> FullscreenCam(
            webView = webView,
            spotName = spotWebcams.spotDisplayName,
            camera = activeCamera,
            cameraCount = spotWebcams.cameras.size,
            selectedIndex = selectedCamIndex,
            controlsVisible = controlsVisible,
            isLoading = isLoading,
            loadError = loadError,
            liveConditions = if (showLiveOverlay) liveConditions else null,
            windUnit = windUnit,
            onPrev = { selectCamera((selectedCamIndex - 1 + spotWebcams.cameras.size) % spotWebcams.cameras.size) },
            onNext = { selectCamera((selectedCamIndex + 1) % spotWebcams.cameras.size) },
            onRetry = { loadError = false; isLoading = true; webView.reload() },
            onClose = { exitFullscreen() }
        )
        else -> {
            val favorites = remember(favVersion, currentSpotName) {
                SurfWebcamHelper.getWebcamFavorites(prefs).filterNotNull().distinct()
            }
            val isFavorite = favorites.any { it.equals(currentSpotName, ignoreCase = true) }
            Box(Modifier.fillMaxSize()) {
                CamPage(
                    webView = webView,
                    spotWebcams = spotWebcams,
                    selectedIndex = selectedCamIndex,
                    activeCamera = activeCamera,
                    favorites = favorites,
                    isFavorite = isFavorite,
                    favMessage = favMessage,
                    isLoading = isLoading,
                    loadError = loadError,
                    looksWrong = looksWrong,
                    realPageTitle = pageTitle,
                    showLinkSuccess = showLinkSuccess,
                    liveConditions = if (showLiveOverlay) liveConditions else null,
                    windUnit = windUnit,
                    onBack = onClose,
                    onToggleFavorite = {
                        val ok = SurfWebcamHelper.toggleWebcamFavorite(prefs, currentSpotName)
                        favVersion++
                        if (!ok) scope.launch {
                            favMessage = "4 favoris au plus : retire-en un d'abord"
                            delay(2500)
                            favMessage = null
                        }
                    },
                    onSwitchSpot = { name -> if (!name.equals(currentSpotName, ignoreCase = true)) onSwitchSpot(name) },
                    onSelectCamera = { selectCamera(it) },
                    onFullscreen = { enterFullscreen() },
                    onRetry = { loadError = false; isLoading = true; webView.reload() },
                    onGoSurfSearch = { startGoSurfSearch() },
                    onLinkPage = { linkCurrentPage() },
                    onShowAll = { showAllCams = true }
                )
                if (showAllCams) {
                    AllCamsSheet(
                        currentSpotName = currentSpotName,
                        onPick = { name ->
                            showAllCams = false
                            if (!name.equals(currentSpotName, ignoreCase = true)) onSwitchSpot(name)
                        },
                        onDismiss = { showAllCams = false }
                    )
                }
            }
        }
    }
}

/** Relance la vidéo du lecteur Viewsurf (coupée du son : l'autoplay muet est toujours permis). */
private const val AUTOPLAY_JS =
    "(function(){try{var v=document.querySelector('video');if(v){v.muted=true;v.setAttribute('playsinline','true');" +
        "var p=v.play();if(p&&p.catch)p.catch(function(){});}}catch(e){}})();"

/** Pose le WebView unique dans la mise en page courante (il est d'abord retiré de l'ancienne). */
@Composable
private fun CamWebView(webView: WebView, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = {
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView
        }
    )
}

// ---------------------------------------------------------------------------------------------
// Page portrait
// ---------------------------------------------------------------------------------------------

@Composable
private fun CamPage(
    webView: WebView,
    spotWebcams: SpotWebcams,
    selectedIndex: Int,
    activeCamera: SurfWebcamOption,
    favorites: List<String>,
    isFavorite: Boolean,
    favMessage: String?,
    isLoading: Boolean,
    loadError: Boolean,
    looksWrong: Boolean,
    realPageTitle: String?,
    showLinkSuccess: Boolean,
    liveConditions: LiveConditions?,
    windUnit: String,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSwitchSpot: (String) -> Unit,
    onSelectCamera: (Int) -> Unit,
    onFullscreen: () -> Unit,
    onRetry: () -> Unit,
    onGoSurfSearch: () -> Unit,
    onLinkPage: () -> Unit,
    onShowAll: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val spotName = spotWebcams.spotDisplayName
    val around = remember(spotName) { SurfWebcamHelper.camSpotsAround(spotName) }
    val camCount = spotWebcams.cameras.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        // Barre du titre : retour, nom du spot, étoile des favoris.
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { BackChevron(colors.onBackground) }
            Column(modifier = Modifier.weight(1f)) {
                Text(spotName, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = (if (camCount > 1) "$camCount caméras" else "1 caméra") + " · en direct",
                    fontSize = 12.sp, color = colors.onSurfaceVariant
                )
            }
            IconButton(onClick = onToggleFavorite) {
                Text(
                    text = if (isFavorite) "★" else "☆",
                    fontSize = 24.sp,
                    color = if (isFavorite) StarGold else colors.onSurfaceVariant
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Favoris : des pastilles dans la page (plus rien par-dessus la barre d'état).
            if (favorites.isNotEmpty() || favMessage != null) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (favorites.isNotEmpty()) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(favorites) { fav ->
                                    SpotChip(text = fav, active = fav.equals(spotName, ignoreCase = true), onClick = { onSwitchSpot(fav) })
                                }
                            }
                        }
                        favMessage?.let { Text(it, fontSize = 12.sp, color = colors.onSurfaceVariant) }
                    }
                }
            }

            // Lecteur.
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black)
                ) {
                    CamWebView(webView, Modifier.fillMaxSize())
                    PlayerStateOverlay(isLoading = isLoading, loadError = loadError, onRetry = onRetry, compact = true)
                    LivePill(Modifier.align(Alignment.TopStart).padding(8.dp))
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))))
                            .padding(start = 10.dp, end = 4.dp, top = 14.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(activeCamera.shortName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text("Images : ${activeCamera.sourceName}", color = Color.White.copy(alpha = 0.75f), fontSize = 10.5.sp, maxLines = 1)
                        IconButton(onClick = onFullscreen, modifier = Modifier.size(38.dp)) { FullscreenGlyph(Color.White) }
                    }
                }
            }

            // Conditions du moment (houle, marée, vent) : la même barre qu'en plein écran.
            liveConditions?.let { c ->
                item {
                    SurfLiveStripOverlay(hourlyModel = c.hour, tideInfo = c.tide, windUnit = windUnit, onOpenCam = {}, modifier = Modifier.fillMaxWidth())
                }
            }

            // Page GoSurf qui montre un autre spot : on le dit dans la page, sans cacher l'image.
            if (looksWrong && !isLoading && !loadError) {
                item {
                    Surface(shape = RoundedCornerShape(14.dp), color = colors.surface) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Ce n'est peut-être pas la bonne webcam", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = colors.onSurface)
                            Text("La page affiche « ${realPageTitle ?: ""} » au lieu de $spotName.", fontSize = 12.sp, color = colors.onSurfaceVariant)
                            Button(onClick = onGoSurfSearch) { Text("Chercher sur GoSurf.fr") }
                        }
                    }
                }
            }

            // Caméras du spot.
            item { SectionTitle("Caméras de $spotName") }
            items(spotWebcams.cameras.size) { index ->
                val cam = spotWebcams.cameras[index]
                CameraRow(camera = cam, active = index == selectedIndex, onClick = { onSelectCamera(index) })
            }
            // Pages tierces (pas le lecteur Viewsurf) : corriger l'adresse de la caméra.
            if (!activeCamera.isViewsurfPlayer) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Mauvaise image ?", fontSize = 12.sp, color = colors.onSurfaceVariant)
                        TextButton(onClick = onGoSurfSearch) { Text("Chercher sur GoSurf", fontSize = 12.sp) }
                        TextButton(onClick = onLinkPage) { Text(if (showLinkSuccess) "Lien enregistré" else "Lier cette page", fontSize = 12.sp) }
                    }
                }
            }

            // Spots autour, du plus proche au plus loin.
            if (around.isNotEmpty()) {
                item { SectionTitle("Autour de $spotName") }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(around) { spot -> NearbyCard(spot = spot, onClick = { onSwitchSpot(spot.name) }) }
                    }
                }
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onShowAll),
                    color = colors.surface
                ) {
                    Text(
                        "Toutes les webcams", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = colors.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        fontSize = 11.5.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun SpotChip(text: String, active: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClick),
        color = if (active) colors.primaryContainer else colors.surfaceVariant
    ) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            if (active) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(LiveRed))
                Spacer(Modifier.width(5.dp))
            }
            Text(
                text, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                color = if (active) colors.onPrimaryContainer else colors.onSurface
            )
        }
    }
}

@Composable
private fun LivePill(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(LiveRed))
        Spacer(Modifier.width(5.dp))
        Text("EN DIRECT", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp)
    }
}

/** Caméra du spot : vignette, nom, exploitant ; la caméra lancée est surlignée. */
@Composable
private fun CameraRow(camera: SurfWebcamOption, active: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) colors.surface else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CamThumbnail(seed = camera.camName, modifier = Modifier.width(84.dp).height(47.dp))
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(camera.shortName, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Images : ${camera.sourceName}", fontSize = 11.5.sp, color = colors.onSurfaceVariant)
        }
        if (active) {
            val barColor = colors.primary
            Canvas(Modifier.size(16.dp)) {
                val w = size.width / 5f
                listOf(0.55f, 1f, 0.4f).forEachIndexed { i, h ->
                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(i * 2 * w, size.height * (1 - h)),
                        size = Size(w, size.height * h),
                        cornerRadius = CornerRadius(2f, 2f)
                    )
                }
            }
        }
    }
}

@Composable
private fun NearbyCard(spot: NearbyCamSpot, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.width(132.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        CamThumbnail(seed = spot.name, modifier = Modifier.fillMaxWidth().height(74.dp))
        Text(spot.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${spot.distanceKm} km", fontSize = 11.sp, color = colors.onSurfaceVariant)
    }
}

/**
 * Vignette dessinée (ciel, mer, sable) : les exploitants ne fournissent pas d'image fixe stable pour chaque caméra.
 * La teinte varie avec le nom pour qu'on distingue les vignettes entre elles.
 */
@Composable
private fun CamThumbnail(seed: String, modifier: Modifier = Modifier) {
    val h = seed.hashCode() and 0x7fffffff
    val warm = h % 2 == 0
    val horizon = 0.38f + (h % 7) * 0.02f
    val shore = 0.72f + (h % 5) * 0.02f
    Canvas(modifier = modifier.clip(RoundedCornerShape(8.dp))) {
        val w = size.width
        val ht = size.height
        drawRect(
            brush = Brush.verticalGradient(
                listOf(if (warm) Color(0xFF4D6F93) else Color(0xFF5B86B0), if (warm) Color(0xFFE7B98C) else Color(0xFFC9DCEB)),
                startY = 0f, endY = ht * horizon
            ),
            size = Size(w, ht * horizon)
        )
        drawRect(
            brush = Brush.verticalGradient(listOf(Color(0xFF2F5F78), Color(0xFF5F8E9C)), startY = ht * horizon, endY = ht * shore),
            topLeft = Offset(0f, ht * horizon),
            size = Size(w, ht * (shore - horizon))
        )
        for (i in 1..3) {
            val y = ht * (horizon + (shore - horizon) * i / 4f)
            drawLine(Color.White.copy(alpha = 0.15f + i * 0.12f), Offset(0f, y), Offset(w, y), strokeWidth = 1f + i * 0.6f)
        }
        drawRect(
            brush = Brush.verticalGradient(listOf(Color(0xFFC9B38C), Color(0xFF8F7A59)), startY = ht * shore, endY = ht),
            topLeft = Offset(0f, ht * shore),
            size = Size(w, ht * (1 - shore))
        )
    }
}

@Composable
private fun PlayerStateOverlay(isLoading: Boolean, loadError: Boolean, onRetry: () -> Unit, compact: Boolean) {
    if (isLoading && !loadError) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(if (compact) 32.dp else 40.dp))
        }
    }
    if (loadError) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(12.dp)) {
                Text("Webcam indisponible", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Le flux ne répond pas. Vérifie ta connexion ou réessaie.", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, textAlign = TextAlign.Center)
                Button(onClick = onRetry) { Text("Réessayer") }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Plein écran
// ---------------------------------------------------------------------------------------------

@Composable
private fun FullscreenCam(
    webView: WebView,
    spotName: String,
    camera: SurfWebcamOption,
    cameraCount: Int,
    selectedIndex: Int,
    controlsVisible: Boolean,
    isLoading: Boolean,
    loadError: Boolean,
    liveConditions: LiveConditions?,
    windUnit: String,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit
) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        CamWebView(webView, Modifier.fillMaxSize())
        PlayerStateOverlay(isLoading = isLoading, loadError = loadError, onRetry = onRetry, compact = false)

        AnimatedVisibility(visible = controlsVisible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)))
                        .padding(start = 40.dp, end = 16.dp, top = 12.dp, bottom = 22.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(LiveRed))
                    Spacer(Modifier.width(8.dp))
                    Text(camera.shortName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
                    Text(
                        "  ·  $spotName · ${camera.sourceName}", color = Color.White.copy(alpha = 0.7f), fontSize = 11.5.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f))
                    ) {
                        Icon(SurfIcons.Close, contentDescription = "Quitter le plein écran", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }

                if (cameraCount > 1) {
                    RoundArrow(left = true, onClick = onPrev, modifier = Modifier.align(Alignment.CenterStart).padding(start = 40.dp))
                    RoundArrow(left = false, onClick = onNext, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp))
                    Row(
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = if (liveConditions != null) 64.dp else 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        repeat(cameraCount) { i ->
                            Box(Modifier.size(6.dp).clip(CircleShape).background(Color.White.copy(alpha = if (i == selectedIndex) 1f else 0.4f)))
                        }
                    }
                }

                liveConditions?.let { c ->
                    SurfLiveStripOverlay(
                        hourlyModel = c.hour, tideInfo = c.tide, windUnit = windUnit, onOpenCam = {},
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp)
                            .fillMaxWidth(0.72f)
                            .widthIn(max = 520.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RoundArrow(left: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.4f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(16.dp)) {
            val path = Path().apply {
                if (left) {
                    moveTo(size.width * 0.65f, size.height * 0.15f); lineTo(size.width * 0.3f, size.height * 0.5f); lineTo(size.width * 0.65f, size.height * 0.85f)
                } else {
                    moveTo(size.width * 0.35f, size.height * 0.15f); lineTo(size.width * 0.7f, size.height * 0.5f); lineTo(size.width * 0.35f, size.height * 0.85f)
                }
            }
            drawPath(path, Color.White, style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

@Composable
private fun BackChevron(color: Color) {
    Canvas(Modifier.size(20.dp)) {
        val path = Path().apply {
            moveTo(size.width * 0.62f, size.height * 0.18f); lineTo(size.width * 0.3f, size.height * 0.5f); lineTo(size.width * 0.62f, size.height * 0.82f)
        }
        drawPath(path, color, style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun FullscreenGlyph(color: Color) {
    Canvas(Modifier.size(18.dp)) {
        val w = size.width
        val h = size.height
        val k = 0.3f
        listOf(
            listOf(Offset(0f, h * k), Offset(0f, 0f), Offset(w * k, 0f)),
            listOf(Offset(w * (1 - k), 0f), Offset(w, 0f), Offset(w, h * k)),
            listOf(Offset(0f, h * (1 - k)), Offset(0f, h), Offset(w * k, h)),
            listOf(Offset(w * (1 - k), h), Offset(w, h), Offset(w, h * (1 - k)))
        ).forEach { pts ->
            val p = Path().apply { moveTo(pts[0].x, pts[0].y); lineTo(pts[1].x, pts[1].y); lineTo(pts[2].x, pts[2].y) }
            drawPath(p, color, style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Toutes les webcams, recherche GoSurf, spot sans caméra
// ---------------------------------------------------------------------------------------------

/** Toutes les webcams, par région, avec une recherche par nom. */
@Composable
private fun AllCamsSheet(currentSpotName: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    var query by remember { mutableStateOf("") }
    val groups = remember {
        SurfDatabase.countries.flatMap { country ->
            country.regions.mapNotNull { region ->
                val spots = region.subRegions.flatMap { it.spots }.filter { SurfWebcamHelper.hasCamera(it.name) }
                if (spots.isEmpty()) null else region.name to spots.map { it.name }
            }
        }
    }
    val q = query.trim().lowercase()
    val filtered = groups.mapNotNull { (region, names) ->
        val f = names.filter { q.isEmpty() || it.lowercase().contains(q) }
        if (f.isEmpty()) null else region to f
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .clickable(enabled = false) {},
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            color = colors.background
        ) {
            Column(
                Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    Modifier.align(Alignment.CenterHorizontally).width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp))
                        .background(colors.onSurfaceVariant.copy(alpha = 0.4f))
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Toutes les webcams", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = colors.onBackground, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(SurfIcons.Close, contentDescription = "Fermer", tint = colors.onBackground) }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Chercher une plage, une ville") },
                    leadingIcon = { Icon(SurfIcons.Search, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
                )
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (filtered.isEmpty()) {
                        item { Text("Aucune plage ne correspond.", fontSize = 13.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(8.dp)) }
                    }
                    filtered.forEach { (region, names) ->
                        item { Box(Modifier.padding(top = 8.dp)) { SectionTitle(region) } }
                        items(names) { name ->
                            val camCount = SurfWebcamHelper.getCamerasForSpot(name).cameras.size
                            val current = name.equals(currentSpotName, ignoreCase = true)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (current) colors.surface else Color.Transparent)
                                    .clickable { onPick(name) }
                                    .padding(horizontal = 8.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    name, fontSize = 14.5.sp, color = colors.onBackground,
                                    fontWeight = if (current) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f)
                                )
                                Text(if (camCount > 1) "$camCount caméras" else "1 caméra", fontSize = 12.sp, color = colors.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Recherche de la bonne page sur gosurf.fr : la page choisie devient l'adresse de la caméra. */
@Composable
private fun GoSurfSearchPage(webView: WebView, camName: String, isLoading: Boolean, onCancel: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().background(colors.background).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCancel) { BackChevron(colors.onBackground) }
            Column(Modifier.weight(1f)) {
                Text("Chercher sur GoSurf", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
                Text("Touche la webcam « $camName » : elle sera gardée pour la prochaine fois.", fontSize = 12.sp, color = colors.onSurfaceVariant, maxLines = 2)
            }
        }
        Box(Modifier.fillMaxSize()) {
            CamWebView(webView, Modifier.fillMaxSize())
            if (isLoading) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

/** Spot sans caméra : on le dit, et on propose les spots équipés les plus proches. */
@Composable
private fun NoCameraPage(spotName: String, onClose: () -> Unit, onSwitchSpot: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val around = remember(spotName) { SurfWebcamHelper.camSpotsAround(spotName, maxKm = 300, limit = 6) }
    Column(
        Modifier.fillMaxSize().background(colors.background).windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { BackChevron(colors.onBackground) }
            Text(spotName, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
        }
        Text("Aucune webcam pour ce spot.", fontSize = 14.sp, color = colors.onSurface)
        if (around.isNotEmpty()) {
            SectionTitle("Webcams les plus proches")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(around) { spot -> NearbyCard(spot = spot, onClick = { onSwitchSpot(spot.name) }) }
            }
        }
    }
}
