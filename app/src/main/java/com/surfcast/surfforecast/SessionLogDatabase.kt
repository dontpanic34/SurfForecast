package com.surfcast.surfforecast

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [QuiverBoard::class, MicroSpot::class, ConditionSnapshot::class, SurfSession::class],
    version = 2,
    exportSchema = false
)
abstract class SessionLogDatabase : RoomDatabase() {

    abstract fun sessionLogDao(): SessionLogDao

    companion object {
        @Volatile
        private var instance: SessionLogDatabase? = null

        fun getInstance(context: Context): SessionLogDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SessionLogDatabase::class.java,
                    "session_log.db"
                )
                    // Fonctionnalite en cours de developpement, pas de migrations ecrites :
                    // un changement de schema recree la base plutot que de planter.
                    .fallbackToDestructiveMigration(true)
                    .build().also { instance = it }
            }
        }
    }
}
