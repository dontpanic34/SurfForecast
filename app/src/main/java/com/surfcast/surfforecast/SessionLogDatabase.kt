package com.surfcast.surfforecast

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [QuiverBoard::class, MicroSpot::class, ConditionSnapshot::class, SurfSession::class],
    version = 3,
    exportSchema = false
)
abstract class SessionLogDatabase : RoomDatabase() {

    abstract fun sessionLogDao(): SessionLogDao

    companion object {
        // 2 -> 3 : fiche des bancs (marée, hauteur, notes) + phase de marée des sessions.
        // Vraie migration : les sessions déjà enregistrées sont conservées.
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE micro_spots ADD COLUMN tidePhase TEXT NOT NULL DEFAULT 'any'")
                db.execSQL("ALTER TABLE micro_spots ADD COLUMN minHeight REAL")
                db.execSQL("ALTER TABLE micro_spots ADD COLUMN maxHeight REAL")
                db.execSQL("ALTER TABLE micro_spots ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE condition_snapshots ADD COLUMN tidePhase TEXT")
            }
        }

        @Volatile
        private var instance: SessionLogDatabase? = null

        fun getInstance(context: Context): SessionLogDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SessionLogDatabase::class.java,
                    "session_log.db"
                )
                    .addMigrations(MIGRATION_2_3)
                    // Fonctionnalite en cours de developpement, pas de migrations ecrites :
                    // un changement de schema recree la base plutot que de planter.
                    .fallbackToDestructiveMigration(true)
                    .build().also { instance = it }
            }
        }
    }
}
