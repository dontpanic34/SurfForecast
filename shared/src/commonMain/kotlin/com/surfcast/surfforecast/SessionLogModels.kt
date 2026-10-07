package com.surfcast.surfforecast

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

// Modèles du journal de bord, indépendants du stockage : Room sur Android/iOS
// (SessionLogRoomStore), JSON dans le navigateur (JsonSessionLogStore).

// family : "longboard" / "mousse" / "mid-length" / "twin" / "groveler" / "shortboard"
@Serializable
data class QuiverBoard(
    val id: Long = 0,
    val model: String,
    val family: String,
    val lengthLitrage: String,
    val finSetup: String
)

@Serializable
data class MicroSpot(
    val id: Long = 0,
    val parentSpotName: String,
    val name: String
)

@Serializable
data class ConditionSnapshot(
    val id: Long = 0,
    val energyKj: Int,
    val waveHeight: Double,
    val wavePeriod: Double,
    val waveDirection: Int,
    val windSpeedKmh: Int,
    val windDirection: Int,
    val tideCoeff: Int?,
    val isNearHighTide: Boolean
)

@Serializable
data class SurfSession(
    val id: Long = 0,
    val startTime: Long,
    val endTime: Long,
    val microSpotId: Long,
    val quiverId: Long,
    val conditionId: Long,
    val rating: Int,
    val comment: String? = null,
    val mediaUri: String? = null
)

data class SurfSessionWithRelations(
    val session: SurfSession,
    val microSpot: MicroSpot,
    val quiverBoard: QuiverBoard,
    val condition: ConditionSnapshot
)

/** Accès au journal de bord (mêmes opérations que l'ancien SessionLogDao Room). */
interface SessionLogStore {
    fun getAllQuiverBoards(): Flow<List<QuiverBoard>>
    fun getMicroSpotsForSpot(parentSpotName: String): Flow<List<MicroSpot>>
    fun getReferenceSessions(minRating: Int = 4): Flow<List<SurfSessionWithRelations>>
    fun getAllSessions(): Flow<List<SurfSessionWithRelations>>
    suspend fun insertQuiverBoard(board: QuiverBoard): Long
    /** Échoue si la planche est utilisée par une session enregistrée. */
    suspend fun deleteQuiverBoard(board: QuiverBoard)
    suspend fun insertMicroSpot(spot: MicroSpot): Long
    suspend fun logSession(
        startTime: Long,
        endTime: Long,
        microSpotId: Long,
        quiverId: Long,
        condition: ConditionSnapshot,
        rating: Int,
        comment: String?,
        mediaUri: String?
    ): Long
}
