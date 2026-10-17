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
        val lacanau = SurfWebcamHelper.getCamerasForSpot("Lacanau").cameras.single()
        assertEquals(
            "https://surflog.fr/api/cam?u=https%3A%2F%2Fgosurf.fr%2Fwebcam%2Ffr%2F76%2FLacanau-Ocean-Plage-Centrale&q=webcam%20gosurf%20Lacanau",
            SurfWebcamHelper.liveCamUrl("Lacanau", lacanau)
        )
    }

    @Test
    fun braveOptionBuildsAnIntentWithTheRelayAsFallback() {
        val cam = SurfWebcamHelper.getCamerasForSpot("Lacanau").cameras.single()
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
}
