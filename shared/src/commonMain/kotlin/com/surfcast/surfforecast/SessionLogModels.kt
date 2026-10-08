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
    val finSetup: String,
    // Volume en litres (null = inconnu) ; [volumeEstimated] = calculé depuis les cotes, pas lu sur la planche.
    val volumeL: Double? = null,
    val volumeEstimated: Boolean = false
)

@Serializable
data class MicroSpot(
    val id: Long = 0,
    val parentSpotName: String,
    val name: String,
    // Fiche du banc : conditions où il marche (tout est facultatif).
    // tidePhase : "any" / "rising" (montant) / "falling" (descendant) / "high" (pleine mer) / "low" (basse mer)
    val tidePhase: String = "any",
    val minHeight: Double? = null,
    val maxHeight: Double? = null,
    val notes: String = ""
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
    val isNearHighTide: Boolean,
    // Phase de marée pendant la session : "rising" / "falling" / "high" / "low" (null = inconnue).
    val tidePhase: String? = null
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
    /** Met à jour la fiche d'un banc (marée, hauteur, notes). */
    suspend fun updateMicroSpot(spot: MicroSpot)
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
