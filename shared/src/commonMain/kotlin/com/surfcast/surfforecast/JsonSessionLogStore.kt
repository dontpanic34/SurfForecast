package com.surfcast.surfforecast

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Journal de bord stocké en JSON dans un KeyValueStore (localStorage dans le navigateur,
 * où Room n'existe pas). Mêmes règles que la base Room : ids auto-incrémentés, planche
 * non supprimable si une session l'utilise.
 */
class JsonSessionLogStore(private val prefs: KeyValueStore) : SessionLogStore {

    @Serializable
    private data class Data(
        val boards: List<QuiverBoard> = emptyList(),
        val microSpots: List<MicroSpot> = emptyList(),
        val conditions: List<ConditionSnapshot> = emptyList(),
        val sessions: List<SurfSession> = emptyList(),
        val nextId: Long = 1
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val state = MutableStateFlow(load())

    private fun load(): Data = prefs.getString(KEY, null)
        ?.let { runCatching { json.decodeFromString(Data.serializer(), it) }.getOrNull() }
        ?: Data()

    private fun save(data: Data) {
        prefs.putString(KEY, json.encodeToString(Data.serializer(), data))
        state.value = data
    }

    private fun Data.withRelations(): List<SurfSessionWithRelations> = sessions.mapNotNull { s ->
        SurfSessionWithRelations(
            session = s,
            microSpot = microSpots.firstOrNull { it.id == s.microSpotId } ?: return@mapNotNull null,
            quiverBoard = boards.firstOrNull { it.id == s.quiverId } ?: return@mapNotNull null,
            condition = conditions.firstOrNull { it.id == s.conditionId } ?: return@mapNotNull null
        )
    }.sortedByDescending { it.session.startTime }

    override fun getAllQuiverBoards(): Flow<List<QuiverBoard>> = state.map { d -> d.boards.sortedBy { it.model } }

    override fun getMicroSpotsForSpot(parentSpotName: String): Flow<List<MicroSpot>> =
        state.map { d -> d.microSpots.filter { it.parentSpotName == parentSpotName }.sortedBy { it.name } }

    override fun getReferenceSessions(minRating: Int): Flow<List<SurfSessionWithRelations>> =
        state.map { d -> d.withRelations().filter { it.session.rating >= minRating } }

    override fun getAllSessions(): Flow<List<SurfSessionWithRelations>> = state.map { it.withRelations() }

    override suspend fun insertQuiverBoard(board: QuiverBoard): Long {
        val d = state.value
        save(d.copy(boards = d.boards + board.copy(id = d.nextId), nextId = d.nextId + 1))
        return d.nextId
    }

    override suspend fun deleteQuiverBoard(board: QuiverBoard) {
        val d = state.value
        check(d.sessions.none { it.quiverId == board.id }) { "Planche utilisée par une session" }
        save(d.copy(boards = d.boards.filterNot { it.id == board.id }))
    }

    override suspend fun insertMicroSpot(spot: MicroSpot): Long {
        val d = state.value
        save(d.copy(microSpots = d.microSpots + spot.copy(id = d.nextId), nextId = d.nextId + 1))
        return d.nextId
    }

    override suspend fun updateMicroSpot(spot: MicroSpot) {
        val d = state.value
        save(d.copy(microSpots = d.microSpots.map { if (it.id == spot.id) spot else it }))
    }

    override suspend fun logSession(
        startTime: Long,
        endTime: Long,
        microSpotId: Long,
        quiverId: Long,
        condition: ConditionSnapshot,
        rating: Int,
        comment: String?,
        mediaUri: String?
    ): Long {
        val d = state.value
        val conditionId = d.nextId
        val sessionId = d.nextId + 1
        val session = SurfSession(
            id = sessionId,
            startTime = startTime,
            endTime = endTime,
            microSpotId = microSpotId,
            quiverId = quiverId,
            conditionId = conditionId,
            rating = rating,
            comment = comment,
            mediaUri = mediaUri
        )
        save(
            d.copy(
                conditions = d.conditions + condition.copy(id = conditionId),
                sessions = d.sessions + session,
                nextId = d.nextId + 2
            )
        )
        return sessionId
    }

    /** Copie complète du journal (fichier de sauvegarde). */
    fun exportJson(): String = json.encodeToString(Data.serializer(), state.value)

    /** Remplace tout le journal par une sauvegarde ; false (rien modifié) si le fichier est illisible. */
    fun importJson(text: String): Boolean {
        val data = runCatching { json.decodeFromString(Data.serializer(), text) }.getOrNull() ?: return false
        // Un fichier quelconque (JSON valide mais autre chose) donnerait un journal vide : refusé.
        if (data.boards.isEmpty() && data.sessions.isEmpty() && data.microSpots.isEmpty()) return false
        save(data)
        return true
    }

    private companion object {
        const val KEY = "session_log_json"
    }
}
