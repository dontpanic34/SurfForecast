package com.surfcast.surfforecast

import kotlin.math.roundToInt

data class SurfWebcamOption(
    val camName: String,
    val pageUrl: String
) {
    /** Lecteur officiel Viewsurf (pv.viewsurf.com) : une page qui ne contient que la vidéo, sans rien à nettoyer. */
    val isViewsurfPlayer: Boolean get() = pageUrl.startsWith("https://pv.viewsurf.com/")

    /** Nom de l'exploitant affiché sous la caméra (« Images : Viewsurf »). */
    val sourceName: String
        get() = when {
            isViewsurfPlayer -> "Viewsurf"
            "gosurf.fr" in pageUrl -> "GoSurf"
            "medoc-atlantique.com" in pageUrl -> "Médoc Atlantique"
            else -> pageUrl.substringAfter("://").substringBefore('/').removePrefix("www.")
        }

    /** Nom sans la précision de source entre parenthèses (« Grande Plage (Viewsurf) » → « Grande Plage »). */
    val shortName: String get() = camName.substringBefore(" (").ifBlank { camName }
}

/** Un spot équipé de webcam proche d'un autre, avec la distance à vol d'oiseau. */
data class NearbyCamSpot(val name: String, val distanceKm: Int)

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
                SurfWebcamOption("Montalivet", "https://pv.viewsurf.com/1704/Vendays-Montalivet-Plage"),
                SurfWebcamOption("Montalivet Nord", "https://pv.viewsurf.com/1704/Vendays-Montalivet-Plage?i=NjU5Njp1bmRlZmluZWQ"),
                SurfWebcamOption("Plage Centrale (GoSurf)", "https://gosurf.fr/webcam/fr/57/Montalivet-Plage-Centrale"),
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
                SurfWebcamOption("Surf club (Plage Nord)", "https://pv.viewsurf.com/410/Lacanau-Plage-Nord-Surf-club?i=MjY5Njp1bmRlZmluZWQ"),
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
                SurfWebcamOption("La Nord", "https://pv.viewsurf.com/2362/Biscarrosse-Plage-Nord?i=ODI3NDp1bmRlZmluZWQ"),
                SurfWebcamOption("La Sud", "https://pv.viewsurf.com/2364/Biscarrosse-Plage-Sud?i=ODI4NDp1bmRlZmluZWQ"),
                SurfWebcamOption("Biscarrosse Plage", "https://pv.viewsurf.com/2484/Biscarrosse-Plage-Live?i=ODUwNDp1bmRlZmluZWQ"),
                SurfWebcamOption("Plage de la Centrale (GoSurf)", "https://gosurf.fr/webcam/fr/56/Biscarrosse-Plage-de-la-Centrale")
            )
        ),
        "Mimizan" to SpotWebcams(
            "Mimizan",
            listOf(
                SurfWebcamOption("Centrale", "https://pv.viewsurf.com/2598/Mimizan-Plage-remember?i=ODczMDp1bmRlZmluZWQ"),
                SurfWebcamOption("Nord", "https://pv.viewsurf.com/2598/Mimizan-Plage-remember?i=ODcyODp1bmRlZmluZWQ"),
                SurfWebcamOption("Sud", "https://pv.viewsurf.com/2598/Mimizan-Plage-remember?i=ODczMjoxMTkxMjg4Mjk0"),
                SurfWebcamOption("Plage Centrale", "https://gosurf.fr/webcam/fr/184/Mimizan-Plage-Centrale"),
                SurfWebcamOption("Plage Nord", "https://gosurf.fr/webcam/fr/183/Mimizan-Plage-Nord"),
                SurfWebcamOption("Plage Sud", "https://gosurf.fr/webcam/fr/185/Mimizan-Plage-Sud")
            )
        ),
        "Contis" to SpotWebcams(
            "Contis",
            listOf(
                SurfWebcamOption("Plage de Contis", "https://gosurf.fr/webcam/fr/163/Contis-Plage-de-Contis")
            )
        ),
        "Moliets" to SpotWebcams(
            "Moliets",
            listOf(
                SurfWebcamOption("Golf de Moliets", "https://pv.viewsurf.com/2344/Golf-de-Moliets-Ocean?i=ODE5Njp1bmRlZmluZWQ"),
                SurfWebcamOption("Plage Centrale", "https://gosurf.fr/webcam/fr/59/Moliets-Plage-Centrale"),
                SurfWebcamOption("Plage Nord", "https://gosurf.fr/webcam/fr/58/Moliets-Plage-Nord")
            )
        ),
        "Messanges" to SpotWebcams(
            "Messanges",
            listOf(
                SurfWebcamOption("Plage", "https://gosurf.fr/webcam/fr/60/Messanges-Plage")
            )
        ),
        "Hossegor" to SpotWebcams(
            "Hossegor",
            listOf(
                SurfWebcamOption("Hossegor", "https://pv.viewsurf.com/2646/Hossegor?i=ODg1MDp1bmRlZmluZWQ"),
                SurfWebcamOption("Plage Centrale", "https://pv.viewsurf.com/1810/hossegor?i=Njg4MDp1bmRlZmluZWQ"),
                SurfWebcamOption("La Centrale", "https://gosurf.fr/webcam/fr/21/Hossegor-La-Centrale"),
                SurfWebcamOption("Plage de la Nord", "https://gosurf.fr/webcam/fr/170/Hossegor-Plage-de-la-Nord")
            )
        ),
        "Capbreton" to SpotWebcams(
            "Capbreton",
            listOf(
                SurfWebcamOption("Santocha", "https://pv.viewsurf.com/2534/Capbreton-Santocha?i=ODU3Mjo"),
                SurfWebcamOption("Plage Notre-Dame", "https://gosurf.fr/webcam/fr/64/Capbreton-Plage-Notre-Dame"),
                SurfWebcamOption("Plage de la Piste", "https://gosurf.fr/webcam/fr/63/Capbreton-Plage-de-la-Piste")
            )
        ),
        "Seignosse" to SpotWebcams(
            "Seignosse",
            listOf(
                SurfWebcamOption("Le Penon", "https://pv.viewsurf.com/2652/Seignosse-Le-Penon?i=ODg1Njp1bmRlZmluZWQ")
            )
        ),
        "Tarnos" to SpotWebcams(
            "Tarnos",
            listOf(
                SurfWebcamOption("Plage de la Barre", "https://pv.viewsurf.com/2134/Anglet-Plage-de-La-Barre-et-embouchure-de-l-Adour?i=NzY4MDp1bmRlZmluZWQ"),
                SurfWebcamOption("Plage de la Digue", "https://gosurf.fr/webcam/fr/61/Tarnos-Plage-de-la-Digue")
            )
        ),
        "Anglet" to SpotWebcams(
            "Anglet",
            listOf(
                SurfWebcamOption("Chambre d'Amour", "https://pv.viewsurf.com/2270/Anglet-La-petite-chambre-d-amour-Quiksilver?i=Nzk4Njo"),
                SurfWebcamOption("Cavaliers", "https://pv.viewsurf.com/1994/Anglet-Plage-des-Cavaliers?i=NzMxMDp1bmRlZmluZWQ"),
                SurfWebcamOption("Plage de l'Océan", "https://pv.viewsurf.com/774/Anglet-Plage-de-l-Ocean?i=NDE1ODp1bmRlZmluZWQ"),
                SurfWebcamOption("Plage du Club", "https://pv.viewsurf.com/2126/Anglet-Plage-du-Club?i=NzY3Mjp1bmRlZmluZWQ"),
                SurfWebcamOption("Sables d'Or", "https://pv.viewsurf.com/2128/Anglet-Plage-des-Sables-d-Or?i=NzY3NDp1bmRlZmluZWQ"),
                SurfWebcamOption("Les Cavaliers", "https://gosurf.fr/webcam/fr/41/Anglet-Les-Cavaliers")
            )
        ),
        "Biarritz" to SpotWebcams(
            "Biarritz",
            listOf(
                SurfWebcamOption("Côte des Basques", "https://pv.viewsurf.com/2052/Biarritz-Cote-des-basques?i=NzUwMjp1bmRlZmluZWQ"),
                SurfWebcamOption("Grande Plage (Viewsurf)", "https://pv.viewsurf.com/1802/Biarritz-Grande-Plage?i=Njg2NDp1bmRlZmluZWQ"),
                SurfWebcamOption("Grande Plage", "https://gosurf.fr/webcam/fr/42/Biarritz-Grande-Plage")
            )
        ),
        "Bidart" to SpotWebcams(
            "Bidart",
            listOf(
                SurfWebcamOption("Plage du Centre", "https://gosurf.fr/webcam/fr/43/Bidart-Plage-du-Centre")
            )
        ),
        "Guéthary" to SpotWebcams(
            "Guéthary",
            emptyList()
        ),
        "Saint-Jean-de-Luz" to SpotWebcams(
            "Saint-Jean-de-Luz",
            listOf(
                SurfWebcamOption("Saint-Jean-de-Luz", "https://pv.viewsurf.com/480/Saint-Jean-de-Luz?i=MzExNDp1bmRlZmluZWQ"),
                SurfWebcamOption("Sainte-Barbe", "https://pv.viewsurf.com/480/Saint-Jean-de-Luz?i=MzEyNjp1bmRlZmluZWQ"),
                SurfWebcamOption("Donibane", "https://pv.viewsurf.com/480/Saint-Jean-de-Luz?i=MzEyMDp1bmRlZmluZWQ")
            )
        ),
        "Hendaye" to SpotWebcams(
            "Hendaye",
            listOf(
                SurfWebcamOption("Plage des Jumeaux", "https://pv.viewsurf.com/850/Hendaye-Plage-des-jumeaux?i=NDQxODp1bmRlZmluZWQ"),
                SurfWebcamOption("Plage des Deux Jumeaux", "https://gosurf.fr/webcam/fr/46/Hendaye-Plage-des-Deux-Jumeaux")
            )
        ),
        "La Tranche-sur-Mer" to SpotWebcams(
            "La Tranche-sur-Mer",
            listOf(
                SurfWebcamOption("L'Embarcadère", "https://pv.viewsurf.com/2094/La-Tranche-sur-mer-Embarcadere")
            )
        ),
        "Longeville" to SpotWebcams(
            "Longeville",
            listOf(
                SurfWebcamOption("Les Conches", "https://pv.viewsurf.com/1998/Longeville-sur-mer-Plage-des-Conches?i=NzMzMjp1bmRlZmluZWQ"),
                SurfWebcamOption("Bud Bud", "https://pv.viewsurf.com/1998/Longeville-sur-mer-Plage-des-Conches?i=NzMzMDp1bmRlZmluZWQ"),
                SurfWebcamOption("Plage du Rocher", "https://pv.viewsurf.com/2000/Longeville-sur-mer-Plage-du-Rocher?i=NzMzNjp1bmRlZmluZWQ")
            )
        ),
        "Les Sables-d'Olonne" to SpotWebcams(
            "Les Sables-d'Olonne",
            listOf(
                SurfWebcamOption("Tanchet", "https://pv.viewsurf.com/id/1070/a/media/c/4912?i=NDkxNDp1bmRlZmluZWQ"),
                SurfWebcamOption("Baie des Sables", "https://pv.viewsurf.com/id/1070/a/media/c/4912?i=NDkwODp1bmRlZmluZWQ"),
                SurfWebcamOption("Remblai", "https://pv.viewsurf.com/id/1070/a/media/c/4912?i=NDkxMDp1bmRlZmluZWQ")
            )
        ),
        "Saint-Gilles-Croix-de-Vie" to SpotWebcams(
            "Saint-Gilles-Croix-de-Vie",
            listOf(
                SurfWebcamOption("La Plage", "https://pv.viewsurf.com/2008/Saint-Gilles-Croix-de-Vie-La-Plage?i=NzM2Mjp1bmRlZmluZWQ"),
                SurfWebcamOption("Grande Plage", "https://gosurf.fr/webcam/fr/112/Saint-Gilles-Croix-de-Vie-Grande-Plage")
            )
        ),
        "La Torche" to SpotWebcams(
            "La Torche",
            listOf(
                SurfWebcamOption("Pointe de la Torche", "https://gosurf.fr/webcam/fr/11/Plomeur-Pointe-de-la-Torche")
            )
        ),
        "Quiberon" to SpotWebcams(
            "Quiberon",
            listOf(
                SurfWebcamOption("Plage de Penthièvre", "https://gosurf.fr/webcam/fr/17/Saint-Pierre-Quiberon-Plage-de-Penthievre")
            )
        ),
        "Morgat" to SpotWebcams(
            "Morgat",
            listOf(
                SurfWebcamOption("Morgat (Viewsurf)", "https://pv.viewsurf.com/id/606/a/media/c/3632?i=MzYzMjp1bmRlZmluZWQ"),
                SurfWebcamOption("Morgat", "https://gosurf.fr/webcam/fr/9/Crozon-Morgat")
            )
        )
    )

    /**
     * Lien de la webcam qui ne périme jamais : GoSurf change régulièrement les identifiants de
     * ses pages (/webcam/fr/76/...), donc au lieu d'une adresse figée on lance une recherche
     * qui ouvre directement le premier résultat (DuckDuckGo « !ducky »), c'est-à-dire la page
     * actuelle de la webcam. [camName] n'est ajouté que si le spot a plusieurs caméras.
     */
    fun liveCamUrl(spotName: String, camName: String? = null): String {
        val query = listOfNotNull("!ducky", "webcam", "gosurf", spotName, camName?.substringBefore(" ("))
            .joinToString(" ")
        return "https://duckduckgo.com/?q=" + percentEncode(query)
    }

    /**
     * Lien webcam d'une caméra du catalogue : le relais surflog.fr/api/cam vérifie l'adresse GoSurf du catalogue et y envoie
     * tout droit (plus de page de résultats à choisir) ; si GoSurf a changé son numéro, il retombe sur la recherche « !ducky ».
     * Avec « Webcams dans Brave » (Android), le lien est une adresse « intent » : Chrome lance Brave s'il est installé
     * (sans pubs ni cookies), sinon ouvre la même page dans le navigateur courant.
     */
    fun liveCamUrl(spotName: String, camera: SurfWebcamOption): String {
        // Le lecteur Viewsurf a une adresse stable : on y va tout droit, sans passer par le relais (réservé à GoSurf).
        if (camera.isViewsurfPlayer) {
            return if (SkyGraphSettings.camBrave) {
                "intent://" + camera.pageUrl.removePrefix("https://") + "#Intent;scheme=https;package=com.brave.browser;S.browser_fallback_url=" + percentEncode(camera.pageUrl) + ";end"
            } else camera.pageUrl
        }
        val multiple = (catalog[spotName]?.cameras?.size ?: 0) > 1
        val search = listOfNotNull("webcam", "gosurf", spotName, if (multiple) camera.camName.substringBefore(" (") else null).joinToString(" ")
        val relay = "https://surflog.fr/api/cam?u=" + percentEncode(camera.pageUrl) + "&q=" + percentEncode(search)
        return if (SkyGraphSettings.camBrave) {
            "intent://" + relay.removePrefix("https://") + "#Intent;scheme=https;package=com.brave.browser;S.browser_fallback_url=" + percentEncode(relay) + ";end"
        } else relay
    }

    internal fun percentEncode(text: String): String = buildString {
        for (b in text.encodeToByteArray()) {
            val c = b.toInt() and 0xFF
            val plain = c in 'a'.code..'z'.code || c in 'A'.code..'Z'.code || c in '0'.code..'9'.code || c == '-'.code || c == '_'.code || c == '.'.code
            if (plain) append(c.toChar()) else append('%').append(c.toString(16).uppercase().padStart(2, '0'))
        }
    }

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

    /** Vrai si le spot est dans les favoris webcam. */
    fun isWebcamFavorite(prefs: KeyValueStore, spotName: String): Boolean =
        getWebcamFavorites(prefs).any { it.equals(spotName, ignoreCase = true) }

    /**
     * Ajoute le spot dans la première place libre des favoris webcam, ou l'en retire s'il y est déjà.
     * Renvoie false si les 4 places sont prises (rien n'est changé).
     */
    fun toggleWebcamFavorite(prefs: KeyValueStore, spotName: String): Boolean {
        val favorites = getWebcamFavorites(prefs)
        val existing = favorites.indexOfFirst { it.equals(spotName, ignoreCase = true) }
        if (existing >= 0) {
            prefs.remove("webcam_fav_$existing")
            return true
        }
        val free = favorites.indexOfFirst { it == null }
        if (free < 0) return false
        setWebcamFavorite(prefs, free, spotName)
        return true
    }

    /**
     * Spots équipés de webcam autour de [currentSpotName], du plus proche au plus loin (le spot lui-même exclu),
     * dans un rayon de [maxKm] km, au plus [limit] spots. Liste vide si le spot est inconnu.
     */
    fun camSpotsAround(currentSpotName: String, maxKm: Int = 150, limit: Int = 8): List<NearbyCamSpot> {
        val current = SurfDatabase.findSpotByName(currentSpotName) ?: return emptyList()
        return SurfDatabase.getAllSpots()
            .filter { it.name != current.name && hasCamera(it.name) }
            .map { NearbyCamSpot(it.name, distanceKm(current.latitude, current.longitude, it.latitude, it.longitude).roundToInt()) }
            .filter { it.distanceKm <= maxKm }
            .sortedBy { it.distanceKm }
            .take(limit)
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