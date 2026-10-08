package com.surfcast.surfforecast

// Tables Room du journal de bord (Android/iOS), identiques à celles de l'app Android :
// même noms de tables et de colonnes, le fichier session_log.db existant reste lisible.

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// family : "longboard" / "mousse" / "mid-length" / "twin" / "groveler" / "shortboard"
@Entity(tableName = "quiver")
data class QuiverBoardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val model: String,
    val family: String,
    val lengthLitrage: String,
    val finSetup: String,
    val volumeL: Double? = null,
    @ColumnInfo(defaultValue = "0") val volumeEstimated: Boolean = false
)

@Entity(tableName = "micro_spots")
data class MicroSpotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val parentSpotName: String,
    val name: String,
    val tidePhase: String = "any",
    val minHeight: Double? = null,
    val maxHeight: Double? = null,
    val notes: String = ""
)

@Entity(tableName = "condition_snapshots")
data class ConditionSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val energyKj: Int,
    val waveHeight: Double,
    val wavePeriod: Double,
    val waveDirection: Int,
    val windSpeedKmh: Int,
    val windDirection: Int,
    val tideCoeff: Int?,
    val isNearHighTide: Boolean,
    val tidePhase: String? = null
)

@Entity(
    tableName = "sessions",
    foreignKeys = [
        ForeignKey(entity = MicroSpotEntity::class, parentColumns = ["id"], childColumns = ["microSpotId"]),
        ForeignKey(entity = QuiverBoardEntity::class, parentColumns = ["id"], childColumns = ["quiverId"]),
        ForeignKey(entity = ConditionSnapshotEntity::class, parentColumns = ["id"], childColumns = ["conditionId"])
    ],
    indices = [Index("microSpotId"), Index("quiverId"), Index("conditionId")]
)
data class SurfSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
    val endTime: Long,
    val microSpotId: Long,
    val quiverId: Long,
    val conditionId: Long,
    val rating: Int,
    val comment: String? = null,
    val mediaUri: String? = null
)

internal fun QuiverBoardEntity.toModel() = QuiverBoard(id, model, family, lengthLitrage, finSetup, volumeL, volumeEstimated)
internal fun QuiverBoard.toEntity() = QuiverBoardEntity(id, model, family, lengthLitrage, finSetup, volumeL, volumeEstimated)
internal fun MicroSpotEntity.toModel() = MicroSpot(id, parentSpotName, name, tidePhase, minHeight, maxHeight, notes)
internal fun MicroSpot.toEntity() = MicroSpotEntity(id, parentSpotName, name, tidePhase, minHeight, maxHeight, notes)
internal fun ConditionSnapshotEntity.toModel() = ConditionSnapshot(
    id, energyKj, waveHeight, wavePeriod, waveDirection, windSpeedKmh, windDirection, tideCoeff, isNearHighTide, tidePhase
)
internal fun ConditionSnapshot.toEntity() = ConditionSnapshotEntity(
    id, energyKj, waveHeight, wavePeriod, waveDirection, windSpeedKmh, windDirection, tideCoeff, isNearHighTide, tidePhase
)
internal fun SurfSessionEntity.toModel() = SurfSession(
    id, startTime, endTime, microSpotId, quiverId, conditionId, rating, comment, mediaUri
)
