package com.surfcast.surfforecast

data class SurfWebcamOption(
    val camName: String,
    val pageUrl: String
)

data class SpotWebcams(
    val spotDisplayName: String,
    val cameras: List<SurfWebcamOption>
)

/**
 * Catalogue des webcams, indexe par le nom EXACT du spot tel qu'il apparait
 * dans SurfDatabase (SurfSpotsData.kt). Correspondance stricte, sans "contient"
 * approximatif : chaque spot pointe vers exactement ses propres cameras, sans
 * risque qu'un nom en "contienne" un autre par accident.
 */
object SurfWebcamHelper {

    private val catalog: Map<String, SpotWebcams> = mapOf(
        "Montalivet" to SpotWebcams(
            "Montalivet",
            listOf(
                SurfWebcamOption("Webcam Médoc Atlantique", "https://medoc-atlantique.com/webcams/webcam-montalivet/")
            )
        ),
        "Hourtin" to SpotWebcams(
            "Hourtin",
            emptyList()
        ),
        "Carcans" to SpotWebcams(
            "Carcans",
            emptyList()
        ),
        "Lacanau" to SpotWebcams(
            "Lacanau",
            listOf(
                SurfWebcamOption("Plage Centrale (GoSurf)", "https://gosurf.fr/webcam/fr/76/Lacanau-Ocean-Plage-Centrale")
            )
        ),
        "Le Porge" to SpotWebcams(
            "Le Porge",
            emptyList()
        ),
        "Le Grand Crohot" to SpotWebcams(
            "Le Grand Crohot",
            emptyList()
        ),
        "Le Cap Ferret" to SpotWebcams(
            "Le Cap Ferret",
            emptyList()
        ),
        "La Salie" to SpotWebcams(
            "La Salie",
            emptyList()
        ),
        "Biscarrosse" to SpotWebcams(
            "Biscarrosse",
            listOf(
                SurfWebcamOption("Plage de la Centrale (GoSurf)", "https://gosurf.fr/webcam/fr/56/Biscarrosse-Plage-de-la-Centrale")
            )
        ),
        "Mimizan" to SpotWebcams(
            "Mimizan",
            listOf(
                SurfWebcamOption("Plage Centrale", "https://gosurf.fr/webcam/fr/184/Mimizan-Plage-Centrale"),
                SurfWebcamOption("Plage Nord", "https://gosurf.fr/webcam/fr/183/Mimizan-Plage-Nord"),
                SurfWebcamOption("Plage Sud", "https://gosurf.fr/webcam/fr/185/Mimizan-Plage-Sud")
            )
        ),
        "Contis" to SpotWebcams(
            "Contis",
            listOf(SurfWebcamOption("Plage de Contis", "https://gosurf.fr/webcam/fr/163/Contis-Plage-de-Contis"))
        ),
        "Moliets" to SpotWebcams(
            "Moliets",
            listOf(
                SurfWebcamOption("Plage Centrale", "https://gosurf.fr/webcam/fr/59/Moliets-Plage-Centrale"),
                SurfWebcamOption("Plage Nord", "https://gosurf.fr/webcam/fr/58/Moliets-Plage-Nord")
            )
        ),
        "Messanges" to SpotWebcams(
            "Messanges",
            listOf(SurfWebcamOption("Plage", "https://gosurf.fr/webcam/fr/60/Messanges-Plage"))
        ),
        "Hossegor" to SpotWebcams(
            "Hossegor",
            listOf(
                SurfWebcamOption("La Centrale", "https://gosurf.fr/webcam/fr/21/Hossegor-La-Centrale"),
                SurfWebcamOption("Plage de la Nord", "https://gosurf.fr/webcam/fr/170/Hossegor-Plage-de-la-Nord")
            )
        ),
        "Capbreton" to SpotWebcams(
            "Capbreton",
            listOf(
                SurfWebcamOption("Plage Notre-Dame", "https://gosurf.fr/webcam/fr/64/Capbreton-Plage-Notre-Dame"),
                SurfWebcamOption("Plage de la Piste", "https://gosurf.fr/webcam/fr/63/Capbreton-Plage-de-la-Piste")
            )
        ),
        "Seignosse" to SpotWebcams(
            "Seignosse",
            emptyList()
        ),
        "Tarnos" to SpotWebcams(
            "Tarnos",
            listOf(SurfWebcamOption("Plage de la Digue", "https://gosurf.fr/webcam/fr/61/Tarnos-Plage-de-la-Digue"))
        ),
        "Anglet" to SpotWebcams(
            "Anglet",
            listOf(
                SurfWebcamOption("Les Cavaliers", "https://gosurf.fr/webcam/fr/41/Anglet-Les-Cavaliers")
            )
        ),
        "Biarritz" to SpotWebcams(
            "Biarritz",
            listOf(
                SurfWebcamOption("Grande Plage", "https://gosurf.fr/webcam/fr/42/Biarritz-Grande-Plage")
            )
        ),
        "Bidart" to SpotWebcams(
            "Bidart",
            listOf(SurfWebcamOption("Plage du Centre", "https://gosurf.fr/webcam/fr/43/Bidart-Plage-du-Centre"))
        ),
        "Guéthary" to SpotWebcams(
            "Guéthary",
            emptyList()
        ),
        "Saint-Jean-de-Luz" to SpotWebcams(
            "Saint-Jean-de-Luz",
            emptyList()
        ),
        "Hendaye" to SpotWebcams(
            "Hendaye",
            listOf(
                SurfWebcamOption("Plage des Deux Jumeaux", "https://gosurf.fr/webcam/fr/46/Hendaye-Plage-des-Deux-Jumeaux")
            )
        ),
        "La Tranche-sur-Mer" to SpotWebcams(
            "La Tranche-sur-Mer",
            emptyList()
        ),
        "Longeville" to SpotWebcams(
            "Longeville",
            emptyList()
        ),
        "Les Sables-d'Olonne" to SpotWebcams(
            "Les Sables-d'Olonne",
            emptyList()
        ),
        "Saint-Gilles-Croix-de-Vie" to SpotWebcams(
            "Saint-Gilles-Croix-de-Vie",
            listOf(SurfWebcamOption("Grande Plage", "https://gosurf.fr/webcam/fr/112/Saint-Gilles-Croix-de-Vie-Grande-Plage"))
        ),
        "La Torche" to SpotWebcams(
            "La Torche",
            listOf(SurfWebcamOption("Pointe de la Torche", "https://gosurf.fr/webcam/fr/11/Plomeur-Pointe-de-la-Torche"))
        ),
        "Quiberon" to SpotWebcams(
            "Quiberon",
            listOf(SurfWebcamOption("Plage de Penthièvre", "https://gosurf.fr/webcam/fr/17/Saint-Pierre-Quiberon-Plage-de-Penthievre"))
        ),
        "Morgat" to SpotWebcams(
            "Morgat",
            listOf(SurfWebcamOption("Morgat", "https://gosurf.fr/webcam/fr/9/Crozon-Morgat"))
        )
    )

    /** Vrai uniquement si ce nom de spot EXACT (celui de SurfDatabase) a au moins une webcam. */
    fun hasCamera(spotName: String): Boolean {
        return catalog[spotName]?.cameras?.isNotEmpty() == true
    }

    /** Liste de tous les spots repertories avec au moins une webcam (pour l'annuaire). */
    val allSpotsWithCameras: List<SpotWebcams>
        get() = catalog.values.filter { it.cameras.isNotEmpty() }

    /**
     * Trouve les webcams du spot dont le nom correspond EXACTEMENT (aucune
     * correspondance approximative). Chaque camera du catalogue peut avoir ete
     * corrigee individuellement par l'utilisateur (via la recherche GoSurf) :
     * si une correction existe pour cette camera precise, son URL est remplacee
     * silencieusement, en gardant le meme nom/la meme position dans la liste.
     * Si le spot n'est pas dans le catalogue ou n'a pas de camera, renvoie une
     * liste vide (jamais de webcam d'un autre spot par erreur).
     */
    fun getCamerasForSpot(spotName: String, prefs: KeyValueStore? = null): SpotWebcams {
        val base = catalog[spotName] ?: SpotWebcams(spotName, emptyList())
        if (prefs == null || base.cameras.isEmpty()) return base

        val fixedCameras = base.cameras.map { cam ->
            val saved = prefs.getString(overrideKey(spotName, cam.camName), null)
            if (saved != null) cam.copy(pageUrl = saved) else cam
        }
        return base.copy(cameras = fixedCameras)
    }

    private fun overrideKey(spotName: String, camName: String): String = "webcam_override_${spotName}__${camName}"

    /**
     * Enregistre l'URL que l'utilisateur a trouvee manuellement sur gosurf.fr comme
     * etant la bonne webcam pour CETTE camera precise (ex: "La Centrale" de Hossegor),
     * sans affecter les autres cameras du meme spot. Ne necessite plus jamais de mise
     * a jour du code si GoSurf change encore ses identifiants.
     */
    fun saveOverride(prefs: KeyValueStore, spotName: String, camName: String, url: String) {
        prefs.putString(overrideKey(spotName, camName), url)
    }

    /** Efface la correction manuelle enregistree pour cette camera precise. */
    fun clearOverride(prefs: KeyValueStore, spotName: String, camName: String) {
        prefs.remove(overrideKey(spotName, camName))
    }

    fun hasSavedOverride(prefs: KeyValueStore, spotName: String, camName: String): Boolean {
        return prefs.contains(overrideKey(spotName, camName))
    }

    /** Les 4 favoris webcam (independants des favoris meteo), pour un acces rapide. */
    fun getWebcamFavorites(prefs: KeyValueStore): List<String?> {
        return (0..3).map { index -> prefs.getString("webcam_fav_$index", null) }
    }

    fun setWebcamFavorite(prefs: KeyValueStore, slotIndex: Int, spotName: String) {
        prefs.putString("webcam_fav_$slotIndex", spotName)
    }

    /**
     * Trouve le spot equipe d'une webcam le plus proche au sud (previous) et au nord (next)
     * du spot donne, en se basant sur la latitude. Retourne (null, null) si le spot est inconnu.
     */
    fun findAdjacentSpots(currentSpotName: String): Pair<String?, String?> {
        val current = SurfDatabase.findSpotByName(currentSpotName) ?: return null to null
        val candidates = SurfDatabase.getAllSpots().filter {
            it.name != current.name && hasCamera(it.name)
        }
        val previous = candidates.filter { it.latitude < current.latitude }.maxByOrNull { it.latitude }
        val next = candidates.filter { it.latitude > current.latitude }.minByOrNull { it.latitude }
        return previous?.name to next?.name
    }

    /**
     * Liste, triee par latitude, des spots proches du spot donne qui ont reellement
     * une webcam enregistree (jamais de "pas de webcam" dans cette liste). Le spot
     * courant est inclus si lui-meme a une webcam.
     */
    /**
     * Liste, triee par latitude (nord -> sud), de TOUS les spots ayant reellement une
     * webcam enregistree, sans limite de distance : permet de naviguer d'un bout a
     * l'autre de la liste (ex: des Sables-d'Olonne jusqu'a Capbreton) depuis la barre
     * du bas de l'ecran webcam.
     */
    fun nearbyCameraSpots(currentSpotName: String, radius: Int = 8): List<String> {
        return SurfDatabase.getAllSpots()
            .filter { hasCamera(it.name) }
            .sortedBy { it.latitude }
            .map { it.name }
    }

    /**
     * JS injecte uniquement quand on navigue vers gosurf.fr lui-meme (pas une page camera) :
     * cherche un element dont le texte contient le nom du spot, le fait defiler jusqu'a l'ecran
     * et le surligne, pour eviter d'avoir a scroller manuellement toute la liste.
     */
    private fun findMatchJsSnippet(): String = """
        function findMatch(target) {
            var all = document.querySelectorAll('a, div, h1, h2, h3, span, p');
            var best = null;
            var bestLen = 999;
            for (var i = 0; i < all.length; i++) {
                var el = all[i];
                var txt = (el.innerText || el.textContent || '').trim().toLowerCase();
                if (txt.length > 0 && txt.length < 80 && txt.indexOf(target) !== -1) {
                    // Prend l'element au texte le plus court contenant le mot-cle
                    // (donc le plus precis), peu importe son nombre d'enfants.
                    if (txt.length < bestLen) {
                        best = el;
                        bestLen = txt.length;
                    }
                }
            }
            return best;
        }
    """

    fun buildGoSurfHighlightJs(spotName: String): String {
        val safe = spotName.replace("\\", "\\\\").replace("\"", "\\\"")
        return """
            (function() {
                try {
                    var target = "$safe".toLowerCase().trim();
                    var attempts = 0;
                    var maxAttempts = 60;

                    var oldBanner = document.getElementById('surf-search-banner');
                    if (oldBanner) oldBanner.remove();

                    function showBanner(text, color) {
                        var b = document.createElement('div');
                        b.id = 'surf-search-banner';
                        b.textContent = text;
                        b.style.cssText = 'position:fixed;top:8px;left:50%;transform:translateX(-50%);' +
                            'background:' + color + ';color:#fff;padding:8px 16px;border-radius:10px;' +
                            'font-size:13px;font-weight:bold;z-index:999999;box-shadow:0 2px 8px rgba(0,0,0,0.3);' +
                            'font-family:sans-serif;';
                        document.body.appendChild(b);
                        setTimeout(function() { b.remove(); }, 4000);
                    }

                    ${findMatchJsSnippet()}

                    function tick() {
                        var found = findMatch(target);
                        if (found) {
                            found.scrollIntoView({behavior: 'smooth', block: 'center'});
                            found.style.backgroundColor = '#FFFFFF';
                            found.style.outline = '3px solid #1565C0';
                            found.style.outlineOffset = '2px';
                            found.style.borderRadius = '8px';
                            found.style.position = 'relative';
                            found.style.zIndex = '9999';
                            showBanner('✓ Trouvé : ' + target, '#2E7D32');
                            return;
                        }
                        attempts++;
                        if (attempts >= maxAttempts) {
                            showBanner('✗ Aucun résultat pour "' + target + '"', '#C62828');
                            return;
                        }
                        window.scrollBy(0, 700);
                        setTimeout(tick, 350);
                    }

                    showBanner('Recherche de "' + target + '"…', '#455A64');
                    tick();
                } catch(e) {}
            })();
        """
    }

    /**
     * Comme buildGoSurfHighlightJs, mais clique automatiquement sur le lien trouve
     * au lieu de simplement le surligner (recherche du <a href> le plus proche).
     */
    fun buildGoSurfAutoClickJs(spotName: String): String {
        val safe = spotName.replace("\\", "\\\\").replace("\"", "\\\"")
        return """
            (function() {
                try {
                    var target = "$safe".toLowerCase();
                    var attempts = 0;
                    var maxAttempts = 60;

                    ${findMatchJsSnippet()}

                    function findLink(el) {
                        if (el.tagName === 'A' && el.href) return el.href;
                        var childLink = el.querySelector('a[href]');
                        if (childLink) return childLink.href;
                        var node = el;
                        for (var depth = 0; depth < 5 && node; depth++) {
                            node = node.parentElement;
                            if (node && node.tagName === 'A' && node.href) return node.href;
                        }
                        return null;
                    }

                    function tick() {
                        var found = findMatch(target);
                        if (found) {
                            var href = findLink(found);
                            if (href) {
                                window.location.href = href;
                            } else {
                                found.scrollIntoView({behavior: 'smooth', block: 'center'});
                                found.style.backgroundColor = '#FFFFFF';
                                found.style.outline = '3px solid #1565C0';
                                found.style.outlineOffset = '2px';
                                found.style.borderRadius = '8px';
                            }
                            return;
                        }
                        attempts++;
                        if (attempts >= maxAttempts) return;
                        window.scrollBy(0, 700);
                        setTimeout(tick, 350);
                    }

                    tick();
                } catch(e) {}
            })();
        """
    }

    /**
     * Force le thème clair au niveau CSS, independamment du reglage Android (qui peut
     * etre ignore par les WebView recentes) : garantie que gosurf.fr s'affiche toujours
     * dans son apparence claire d'origine, quel que soit le theme sombre du telephone.
     */
    const val FORCE_LIGHT_THEME_JS = """
        (function() {
            try {
                var style = document.getElementById('surf-force-light');
                if (!style) {
                    style = document.createElement('style');
                    style.id = 'surf-force-light';
                    document.head.appendChild(style);
                }
                style.textContent = `
                    :root { color-scheme: light only !important; }
                    html, body { background: #ffffff !important; color: #000000 !important; }
                `;
            } catch(e) {}
        })();
    """

    const val COOKIE_REJECT_ONLY_JS = """
        (function() {
            try {
                var rejectSelectors = [
                    '#onetrust-reject-all-handler',
                    '.onetrust-close-btn-handler',
                    '#didomi-notice-disagree-button',
                    '.didomi-continue-without-agreeing',
                    '.qc-cmp2-summary-buttons button:first-child',
                    '#axeptio_btn_dismiss',
                    '.sp_choice_type_13',
                    'button[aria-label*="refuser" i]',
                    'button[aria-label*="reject" i]'
                ];
                rejectSelectors.forEach(function(sel) {
                    var el = document.querySelector(sel);
                    if (el) el.click();
                });

                var keywords = ['tout refuser', 'refuser', 'continuer sans accepter', 'reject all', 'decline', 'refuse'];
                var clickable = document.querySelectorAll('button, a, span[role="button"], div[role="button"]');
                for (var i = 0; i < clickable.length; i++) {
                    var txt = (clickable[i].innerText || clickable[i].textContent || '').trim().toLowerCase();
                    if (txt.length > 0 && txt.length < 40 && keywords.some(function(k) { return txt === k || txt.indexOf(k) !== -1; })) {
                        clickable[i].click();
                        break;
                    }
                }
            } catch(e) {}
        })();
    """

    const val CLEAN_FULLSCREEN_JS = """
        (function() {
            try {
                var rejectSelectors = [
                    '#onetrust-reject-all-handler',
                    '.onetrust-close-btn-handler',
                    '#didomi-notice-disagree-button',
                    '.didomi-continue-without-agreeing',
                    '.qc-cmp2-summary-buttons button:first-child',
                    '#axeptio_btn_dismiss',
                    '.sp_choice_type_13',
                    'button[aria-label*="refuser" i]',
                    'button[aria-label*="reject" i]'
                ];
                rejectSelectors.forEach(function(sel) {
                    var el = document.querySelector(sel);
                    if (el) el.click();
                });

                var keywords = ['tout refuser', 'refuser', 'continuer sans accepter', 'reject all', 'decline', 'refuse'];
                var clickable = document.querySelectorAll('button, a, span[role="button"], div[role="button"]');
                for (var i = 0; i < clickable.length; i++) {
                    var txt = (clickable[i].innerText || clickable[i].textContent || '').trim().toLowerCase();
                    if (txt.length > 0 && txt.length < 40 && keywords.some(function(k) { return txt === k || txt.indexOf(k) !== -1; })) {
                        clickable[i].click();
                        break;
                    }
                }
            } catch(e) {}

            try {
                var css = `
                    header, footer, nav, table, tbody, tr, td, th,
                    .header, .footer, .navbar, .top-bar,
                    .ads, .pub, .banner, #onetrust-consent-sdk, .cookie-banner,
                    .meteo-block, .content-sidebar, .other-views, .bloc-tools,
                    .site-header, .site-footer, .breadcrumb, h1, h2, h3, p,
                    .vjs-poster, .banner-pub, .zone-pub, .cam-header, .cam-footer,
                    .cam-infos, .forecast, .previsions, .tableau-previsions,
                    [class*="forecast"], [class*="prevision"], [class*="meteo"],
                    [id*="forecast"], [id*="prevision"], [id*="meteo"] {
                        display: none !important;
                    }

                    html, body {
                        margin: 0 !important;
                        padding: 0 !important;
                        width: 100vw !important;
                        height: 100vh !important;
                        overflow: hidden !important;
                        background: #000 !important;
                    }

                    #media, #viewsurf-player, .player-wrapper, .viewsurf-iframe,
                    #container, .main-container, .wrapper, .media-player,
                    .player-container, #player-container, .webcam-player, .video-container {
                        position: fixed !important;
                        top: 0 !important;
                        left: 0 !important;
                        width: 100vw !important;
                        height: 100vh !important;
                        max-width: 100vw !important;
                        max-height: 100vh !important;
                        margin: 0 !important;
                        padding: 0 !important;
                        z-index: 2147483640 !important;
                    }

                    #player, .video-js, video, iframe {
                        position: fixed !important;
                        top: 0 !important;
                        left: 0 !important;
                        width: 100vw !important;
                        height: 100vh !important;
                        max-width: 100vw !important;
                        max-height: 100vh !important;
                        z-index: 2147483647 !important;
                        object-fit: contain !important;
                        background: #000 !important;
                    }
                `;
                var style = document.getElementById('surf-clean-style');
                if (!style) {
                    style = document.createElement('style');
                    style.id = 'surf-clean-style';
                    style.appendChild(document.createTextNode(css));
                    document.head.appendChild(style);
                } else {
                    style.textContent = css;
                }

                var v = document.querySelector('video');
                if (v) {
                    v.muted = true;
                    v.setAttribute('playsinline', 'true');
                    v.play();
                }
                var playBtn = document.querySelector('.vjs-big-play-button, button.play, .vjs-play-control, #play-button');
                if (playBtn) {
                    playBtn.click();
                }
            } catch(e) {}
        })();
    """
}