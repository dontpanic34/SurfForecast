package com.surfcast.surfforecast

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.first
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
        // backgroundScope : les flux du journal de bord (stateIn) tournent en continu, comme dans l'app.
        return SurfController(scope = backgroundScope, prefs = prefs, repository = SurfRepository(offline))
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
        // Aucun encart replié au départ (par défaut, certains le sont : voir le test suivant).
        val prefs = InMemoryKeyValueStore().apply { putString("collapsed_cards", "") }
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
    fun detailCardsAreHiddenByDefaultOnFirstUse() = runTest {
        // Plus de bouton « réduire » : les encarts de détail, réduits par défaut avant, sont masqués (réglables
        // dans Paramètres › Affichage) ; la semaine et le déroulé de la journée restent visibles.
        val c = controller(InMemoryKeyValueStore())
        assertFalse(c.showSurfCard)
        assertFalse(c.showWindCard)
        assertFalse(c.showWindSeaCard)
        assertFalse(c.showWeatherCard)
        assertFalse(c.showHourlyCard)
        assertTrue(c.showWeeklyCard)
        assertTrue(c.showDailyTimelineCard)
        listOf("weekly", "dailyTimeline", "surf", "wind").forEach { assertFalse(c.isCardCollapsed(it), it) }
        advanceUntilIdle()
    }

    @Test
    fun networkFailureOnFirstLoadShowsError() = runTest {
        val c = controller(InMemoryKeyValueStore())
        // Le moteur HTTP simulé répond sur son propre dispatcher (pas le temps virtuel du
        // test) : on attend donc la sortie de l'état Loading plutôt que advanceUntilIdle().
        val state = c.uiState.first { it !is SurfUiState.Loading }
        assertIs<SurfUiState.Error>(state)
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
