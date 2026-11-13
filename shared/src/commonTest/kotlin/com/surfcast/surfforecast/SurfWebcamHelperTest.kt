package com.surfcast.surfforecast

import kotlin.test.Test
import kotlin.test.assertEquals

class SurfWebcamHelperTest {

    @Test
    fun liveCamUrlWithoutKnownCameraIsASearch() {
        assertEquals(
            "https://duckduckgo.com/?q=%21ducky%20webcam%20gosurf%20Montalivet",
            SurfWebcamHelper.liveCamUrl("Montalivet")
        )
    }

    @Test
    fun cameraSearchAddsNameOnlyWhenSeveralCameras() {
        val nord = SurfWebcamHelper.getCamerasForSpot("Mimizan").cameras.first { it.camName == "Plage Nord" }
        assertEquals(
            "https://surflog.fr/api/cam?u=https%3A%2F%2Fgosurf.fr%2Fwebcam%2Ffr%2F183%2FMimizan-Plage-Nord&q=webcam%20gosurf%20Mimizan%20Plage%20Nord",
            SurfWebcamHelper.liveCamUrl("Mimizan", nord)
        )
        // Lacanau a maintenant deux caméras (lecteur Viewsurf ajouté le 13 novembre) : le cas « une seule caméra » passe à Contis.
        val contis = SurfWebcamHelper.getCamerasForSpot("Contis").cameras.single()
        assertEquals(
            "https://surflog.fr/api/cam?u=https%3A%2F%2Fgosurf.fr%2Fwebcam%2Ffr%2F163%2FContis-Plage-de-Contis&q=webcam%20gosurf%20Contis",
            SurfWebcamHelper.liveCamUrl("Contis", contis)
        )
    }

    @Test
    fun braveOptionBuildsAnIntentWithTheRelayAsFallback() {
        // Caméra GoSurf de Lacanau (le spot a aussi un lecteur Viewsurf depuis le 13 novembre, ouvert sans relais).
        val cam = SurfWebcamHelper.getCamerasForSpot("Lacanau").cameras.first { "gosurf.fr" in it.pageUrl }
        SkyGraphSettings.camBrave = true
        try {
            val url = SurfWebcamHelper.liveCamUrl("Lacanau", cam)
            kotlin.test.assertTrue(url.startsWith("intent://surflog.fr/api/cam?"))
            kotlin.test.assertTrue(url.contains("package=com.brave.browser"))
            kotlin.test.assertTrue(url.contains("S.browser_fallback_url=https%3A%2F%2Fsurflog.fr%2Fapi%2Fcam"))
        } finally { SkyGraphSettings.camBrave = false }
    }

    @Test
    fun accentsAreEncodedAsUtf8() {
        assertEquals(
            "https://duckduckgo.com/?q=%21ducky%20webcam%20gosurf%20Gu%C3%A9thary",
            SurfWebcamHelper.liveCamUrl("Guéthary")
        )
    }

    @Test
    fun viewsurfPlayerIsOpenedDirectly() {
        val cam = SurfWebcamHelper.getCamerasForSpot("Montalivet").cameras.first()
        kotlin.test.assertTrue(cam.isViewsurfPlayer)
        assertEquals("Viewsurf", cam.sourceName)
        assertEquals("https://pv.viewsurf.com/1704/Vendays-Montalivet-Plage", SurfWebcamHelper.liveCamUrl("Montalivet", cam))
    }

    @Test
    fun spotsAroundAreSortedByDistanceWithoutTheSpotItself() {
        val around = SurfWebcamHelper.camSpotsAround("Montalivet")
        kotlin.test.assertTrue(around.isNotEmpty())
        kotlin.test.assertTrue(around.none { it.name == "Montalivet" })
        kotlin.test.assertTrue(around.all { SurfWebcamHelper.hasCamera(it.name) })
        assertEquals(around.sortedBy { it.distanceKm }, around)
        assertEquals("Lacanau", around.first().name)
    }

    @Test
    fun favoriteToggleUsesTheFirstFreeSlotAndRemovesOnSecondTap() {
        val prefs = InMemoryKeyValueStore()
        SurfWebcamHelper.setWebcamFavorite(prefs, 0, "Lacanau")
        kotlin.test.assertTrue(SurfWebcamHelper.toggleWebcamFavorite(prefs, "Montalivet"))
        assertEquals(listOf("Lacanau", "Montalivet", null, null), SurfWebcamHelper.getWebcamFavorites(prefs))
        kotlin.test.assertTrue(SurfWebcamHelper.toggleWebcamFavorite(prefs, "Montalivet"))
        kotlin.test.assertFalse(SurfWebcamHelper.isWebcamFavorite(prefs, "Montalivet"))
    }

    @Test
    fun viewsurfImageFollowsTheViewOfTheAddress() {
        val html = """
            <meta property="og:image" content="https://filmspv.viewsurf.com/montalivet01_live/media.jpg" />
            "collection": [{ "id": "6592", "default": false, "media": { "thumbnail": "//filmspv.viewsurf.com/m/panoramique/media_1_tn.jpg" } },
            { "id": "6594", "default": true, "media": { "thumbnail": "//filmspv.viewsurf.com/m/plage/media_2_tn.jpg", "poster": "//filmspv.viewsurf.com/m/plage/media_2.jpg" } },
            { "id": "6596", "default": false, "media": { "thumbnail": "//filmspv.viewsurf.com/m/plage_nord/media_3_tn.jpg", "poster": "//filmspv.viewsurf.com/m/plage_nord/media_3.jpg" } }]
        """
        assertEquals("6596", SurfWebcamHelper.decodeBase64Id("NjU5Njp1bmRlZmluZWQ"))
        assertEquals(
            "https://filmspv.viewsurf.com/m/plage_nord/media_3.jpg",
            SurfWebcamHelper.viewsurfImageFromPlayerHtml(html, "https://pv.viewsurf.com/1704/Vendays-Montalivet-Plage?i=NjU5Njp1bmRlZmluZWQ")
        )
        assertEquals(
            "https://filmspv.viewsurf.com/m/plage/media_2.jpg",
            SurfWebcamHelper.viewsurfImageFromPlayerHtml(html, "https://pv.viewsurf.com/1704/Vendays-Montalivet-Plage")
        )
        assertEquals(
            "https://filmspv.viewsurf.com/montalivet01_live/media.jpg",
            SurfWebcamHelper.viewsurfImageFromPlayerHtml("""<meta property="og:image" content="https://filmspv.viewsurf.com/montalivet01_live/media.jpg" />""", "https://pv.viewsurf.com/1704/x")
        )
    }

    @Test
    fun medocAtlantiqueIsGone() {
        kotlin.test.assertTrue(SurfWebcamHelper.allSpotsWithCameras.flatMap { it.cameras }.none { "medoc" in it.pageUrl })
    }
}
