package com.surfcast.surfforecast

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SurfControllerTest {

    private fun TestScope.controller(prefs: KeyValueStore): SurfController {
        val offline = HttpClient(MockEngine { respondError(HttpStatusCode.ServiceUnavailable) })
        return SurfController(scope = this, prefs = prefs, repository = SurfRepository(offline))
    }

    @Test
    fun savedCardsOrderIsCompletedWithNewCards() = runTest {
        val prefs = InMemoryKeyValueStore().apply { putString("cards_order", "hourly,surf") }
        val c = controller(prefs)
        assertEquals(
            listOf("hourly", "surf", "weekly", "dailyTimeline", "wind", "windSea", "weather"),
            c.cardsOrder.toList()
        )
        advanceUntilIdle()
    }

    @Test
    fun cardMovesAndCollapseArePersistedWithAndroidKeys() = runTest {
        val prefs = InMemoryKeyValueStore()
        val c = controller(prefs)
        c.moveCardUp("dailyTimeline")
        c.toggleCardCollapsed("wind")
        assertEquals("dailyTimeline,weekly,surf,wind,windSea,weather,hourly", prefs.getString("cards_order", null))
        assertEquals("wind", prefs.getString("collapsed_cards", null))
        assertTrue(c.isCardCollapsed("wind"))
        assertFalse(c.isCardCollapsed("surf"))
        advanceUntilIdle()
    }

    @Test
    fun networkFailureOnFirstLoadShowsError() = runTest {
        val c = controller(InMemoryKeyValueStore())
        advanceUntilIdle()
        assertIs<SurfUiState.Error>(c.uiState.value)
        assertFalse(c.isRefreshing)
    }

    @Test
    fun corruptedEngineConfigFallsBackToDefaults() {
        val prefs = InMemoryKeyValueStore().apply {
            putString("short_weather", "OBSOLETE")
            putString("long_wave", "ECMWF_WAM")
        }
        val config = loadEngineConfigFromPrefs(prefs)
        assertEquals(WeatherModel.AROME, config.shortTermWeather)
        assertEquals(WaveModel.ECMWF_WAM, config.longTermWave)
    }
}
