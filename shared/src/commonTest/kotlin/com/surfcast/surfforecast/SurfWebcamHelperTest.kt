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
    fun cameraOfTheCatalogOpensItsDirectPage() {
        val mimizan = SurfWebcamHelper.getCamerasForSpot("Mimizan").cameras
        val nord = mimizan.first { it.camName == "Plage Nord" }
        assertEquals(nord.pageUrl, SurfWebcamHelper.liveCamUrl("Mimizan", nord))
        val lacanau = SurfWebcamHelper.getCamerasForSpot("Lacanau").cameras.single()
        assertEquals("https://gosurf.fr/webcam/fr/76/Lacanau-Ocean-Plage-Centrale", SurfWebcamHelper.liveCamUrl("Lacanau", lacanau))
    }

    @Test
    fun accentsAreEncodedAsUtf8() {
        assertEquals(
            "https://duckduckgo.com/?q=%21ducky%20webcam%20gosurf%20Gu%C3%A9thary",
            SurfWebcamHelper.liveCamUrl("Guéthary")
        )
    }
}
