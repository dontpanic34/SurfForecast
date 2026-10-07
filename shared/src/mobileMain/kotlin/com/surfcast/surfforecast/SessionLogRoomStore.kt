package com.surfcast.surfforecast

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Journal de bord sur la base Room (Android/iOS). */
class SessionLogRoomStore(private val dao: SessionLogDao) : SessionLogStore {
    override fun getAllQuiverBoards(): Flow<List<QuiverBoard>> =
        dao.getAllQuiverBoards().map { list -> list.map { it.toModel() } }

    override fun getMicroSpotsForSpot(parentSpotName: String): Flow<List<MicroSpot>> =
        dao.getMicroSpotsForSpot(parentSpotName).map { list -> list.map { it.toModel() } }

    override fun getReferenceSessions(minRating: Int): Flow<List<SurfSessionWithRelations>> =
        dao.getReferenceSessions(minRating).map { list -> list.map { it.toModel() } }

    override fun getAllSessions(): Flow<List<SurfSessionWithRelations>> =
        dao.getAllSessions().map { list -> list.map { it.toModel() } }

    override suspend fun insertQuiverBoard(board: QuiverBoard): Long = dao.insertQuiverBoard(board.toEntity())

    override suspend fun deleteQuiverBoard(board: QuiverBoard) = dao.deleteQuiverBoard(board.toEntity())

    override suspend fun insertMicroSpot(spot: MicroSpot): Long = dao.insertMicroSpot(spot.toEntity())

    override suspend fun logSession(
        startTime: Long,
        endTime: Long,
        microSpotId: Long,
        quiverId: Long,
        condition: ConditionSnapshot,
        rating: Int,
        comment: String?,
        mediaUri: String?
    ): Long = dao.logSession(startTime, endTime, microSpotId, quiverId, condition.toEntity(), rating, comment, mediaUri)
}
