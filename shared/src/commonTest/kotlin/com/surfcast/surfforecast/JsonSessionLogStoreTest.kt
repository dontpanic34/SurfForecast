package com.surfcast.surfforecast

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JsonSessionLogStoreTest {

    private val condition = ConditionSnapshot(
        energyKj = 300, waveHeight = 1.4, wavePeriod = 12.0, waveDirection = 280,
        windSpeedKmh = 5, windDirection = 90, tideCoeff = 93, isNearHighTide = true
    )

    @Test
    fun sessionsArePersistedWithTheirRelations() = runTest {
        val prefs = InMemoryKeyValueStore()
        val store = JsonSessionLogStore(prefs)
        val board = store.insertQuiverBoard(QuiverBoard(model = "Fish 5'8", family = "twin", lengthLitrage = "32L", finSetup = "twin"))
        val spot = store.insertMicroSpot(MicroSpot(parentSpotName = "Soulac", name = "Plage centrale"))
        store.logSession(1_000, 2_000, spot, board, condition, 5, "Glassy", null)
        store.logSession(3_000, 4_000, spot, board, condition, 2, null, null)

        // Relu depuis le stockage, comme après un rechargement de la page.
        val reloaded = JsonSessionLogStore(prefs)
        val all = reloaded.getAllSessions().first()
        assertEquals(listOf(3_000L, 1_000L), all.map { it.session.startTime })
        assertEquals("Plage centrale", all.last().microSpot.name)
        assertEquals(1, reloaded.getReferenceSessions(4).first().size)
        assertEquals(listOf("Plage centrale"), reloaded.getMicroSpotsForSpot("Soulac").first().map { it.name })
    }

    @Test
    fun boardUsedBySessionCannotBeDeleted() = runTest {
        val store = JsonSessionLogStore(InMemoryKeyValueStore())
        val boardId = store.insertQuiverBoard(QuiverBoard(model = "Longboard", family = "longboard", lengthLitrage = "9'2", finSetup = "single"))
        val spotId = store.insertMicroSpot(MicroSpot(parentSpotName = "Soulac", name = "Nord"))
        store.logSession(1_000, 2_000, spotId, boardId, condition, 4, null, null)
        val board = store.getAllQuiverBoards().first().single()
        assertFailsWith<IllegalStateException> { store.deleteQuiverBoard(board) }
    }
}
