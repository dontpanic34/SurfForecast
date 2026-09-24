package com.surfcast.surfforecast

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// family : "longboard" / "mousse" / "mid-length" / "twin" / "groveler" / "shortboard"
@Entity(tableName = "quiver")
data class QuiverBoard(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val model: String,
    val family: String,
    val lengthLitrage: String,
    val finSetup: String
)

@Entity(tableName = "micro_spots")
data class MicroSpot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val parentSpotName: String,
    val name: String
)

@Entity(tableName = "condition_snapshots")
data class ConditionSnapshot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val energyKj: Int,
    val waveHeight: Double,
    val wavePeriod: Double,
    val waveDirection: Int,
    val windSpeedKmh: Int,
    val windDirection: Int,
    val tideCoeff: Int?,
    val isNearHighTide: Boolean
)

@Entity(
    tableName = "sessions",
    foreignKeys = [
        ForeignKey(entity = MicroSpot::class, parentColumns = ["id"], childColumns = ["microSpotId"]),
        ForeignKey(entity = QuiverBoard::class, parentColumns = ["id"], childColumns = ["quiverId"]),
        ForeignKey(entity = ConditionSnapshot::class, parentColumns = ["id"], childColumns = ["conditionId"])
    ],
    indices = [Index("microSpotId"), Index("quiverId"), Index("conditionId")]
)
data class SurfSession(
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
