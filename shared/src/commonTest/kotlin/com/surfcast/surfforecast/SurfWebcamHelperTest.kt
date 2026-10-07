package com.surfcast.surfforecast

import kotlin.test.Test
import kotlin.test.assertEquals

class SurfWebcamHelperTest {

    @Test
    fun liveCamUrlIsASearchThatNeverGoesStale() {
        assertEquals(
            "https://duckduckgo.com/?q=%21ducky%20webcam%20gosurf%20Montalivet",
            SurfWebcamHelper.liveCamUrl("Montalivet")
        )
    }

    @Test
    fun cameraNameIsOnlyAddedWhenTheSpotHasSeveral() {
        val mimizan = SurfWebcamHelper.getCamerasForSpot("Mimizan").cameras
        assertEquals(
            "https://duckduckgo.com/?q=%21ducky%20webcam%20gosurf%20Mimizan%20Plage%20Nord",
            SurfWebcamHelper.liveCamUrl("Mimizan", mimizan.first { it.camName == "Plage Nord" })
        )
        val lacanau = SurfWebcamHelper.getCamerasForSpot("Lacanau").cameras.single()
        assertEquals(
            "https://duckduckgo.com/?q=%21ducky%20webcam%20gosurf%20Lacanau",
            SurfWebcamHelper.liveCamUrl("Lacanau", lacanau)
        )
    }

    @Test
    fun accentsAreEncodedAsUtf8() {
        assertEquals(
            "https://duckduckgo.com/?q=%21ducky%20webcam%20gosurf%20Gu%C3%A9thary",
            SurfWebcamHelper.liveCamUrl("Guéthary")
        )
    }
}
