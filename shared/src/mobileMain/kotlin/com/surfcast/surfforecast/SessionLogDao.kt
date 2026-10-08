package com.surfcast.surfforecast

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

data class SurfSessionWithRelationsEntity(
    @Embedded val session: SurfSessionEntity,
    @Embedded(prefix = "spot_") val microSpot: MicroSpotEntity,
    @Embedded(prefix = "quiver_") val quiverBoard: QuiverBoardEntity,
    @Embedded(prefix = "cond_") val condition: ConditionSnapshotEntity
) {
    fun toModel() = SurfSessionWithRelations(session.toModel(), microSpot.toModel(), quiverBoard.toModel(), condition.toModel())
}

private const val SESSION_WITH_RELATIONS_SELECT = """
    SELECT
        s.*,
        m.id AS spot_id, m.parentSpotName AS spot_parentSpotName, m.name AS spot_name,
        q.id AS quiver_id, q.model AS quiver_model, q.family AS quiver_family, q.lengthLitrage AS quiver_lengthLitrage, q.finSetup AS quiver_finSetup,
        c.id AS cond_id, c.energyKj AS cond_energyKj, c.waveHeight AS cond_waveHeight, c.wavePeriod AS cond_wavePeriod,
        c.waveDirection AS cond_waveDirection, c.windSpeedKmh AS cond_windSpeedKmh, c.windDirection AS cond_windDirection,
        c.tideCoeff AS cond_tideCoeff, c.isNearHighTide AS cond_isNearHighTide
    FROM sessions s
    INNER JOIN micro_spots m ON m.id = s.microSpotId
    INNER JOIN quiver q ON q.id = s.quiverId
    INNER JOIN condition_snapshots c ON c.id = s.conditionId
"""

@Dao
interface SessionLogDao {

    @Insert
    suspend fun insertQuiverBoard(board: QuiverBoardEntity): Long

    @Delete
    suspend fun deleteQuiverBoard(board: QuiverBoardEntity)

    @Insert
    suspend fun insertMicroSpot(spot: MicroSpotEntity): Long

    @androidx.room.Update
    suspend fun updateMicroSpot(spot: MicroSpotEntity)

    @Insert
    suspend fun insertCondition(condition: ConditionSnapshotEntity): Long

    @Insert
    suspend fun insertSession(session: SurfSessionEntity): Long

    @Query("SELECT * FROM quiver ORDER BY model")
    fun getAllQuiverBoards(): Flow<List<QuiverBoardEntity>>

    @Query("SELECT * FROM micro_spots WHERE parentSpotName = :parentSpotName ORDER BY name")
    fun getMicroSpotsForSpot(parentSpotName: String): Flow<List<MicroSpotEntity>>

    @Transaction
    suspend fun logSession(
        startTime: Long,
        endTime: Long,
        microSpotId: Long,
        quiverId: Long,
        condition: ConditionSnapshotEntity,
        rating: Int,
        comment: String?,
        mediaUri: String?
    ): Long {
        val conditionId = insertCondition(condition)
        return insertSession(
            SurfSessionEntity(
                startTime = startTime,
                endTime = endTime,
                microSpotId = microSpotId,
                quiverId = quiverId,
                conditionId = conditionId,
                rating = rating,
                comment = comment,
                mediaUri = mediaUri
            )
        )
    }

    @Query("$SESSION_WITH_RELATIONS_SELECT WHERE s.rating >= :minRating ORDER BY s.startTime DESC")
    fun getReferenceSessions(minRating: Int = 4): Flow<List<SurfSessionWithRelationsEntity>>

    @Query("$SESSION_WITH_RELATIONS_SELECT ORDER BY s.startTime DESC")
    fun getAllSessions(): Flow<List<SurfSessionWithRelationsEntity>>
}
