package com.surfcast.surfforecast

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

/**
 * Base du journal de bord (sessions, quiver, sous-spots), portage multiplateforme de
 * SessionLogDatabase d'app/ : mêmes tables, même nom de fichier "session_log.db".
 * Room KMP : SQLite embarqué (BundledSQLiteDriver), identique sur Android et iOS.
 */
@Database(
    entities = [QuiverBoardEntity::class, MicroSpotEntity::class, ConditionSnapshotEntity::class, SurfSessionEntity::class],
    version = 5,
    exportSchema = false
)
@ConstructedBy(SessionLogDatabaseConstructor::class)
abstract class SessionLogDatabase : RoomDatabase() {
    abstract fun sessionLogDao(): SessionLogDao
}

// Implémenté par le compilateur Room (KSP) pour chaque plateforme.
@Suppress("KotlinNoActualForExpect", "NO_ACTUAL_FOR_EXPECT")
expect object SessionLogDatabaseConstructor : RoomDatabaseConstructor<SessionLogDatabase> {
    override fun initialize(): SessionLogDatabase
}

/**
 * 2 -> 3 : fiche des bancs (marée, hauteur, notes) + phase de marée des sessions. Vraie migration,
 * identique à celle de l'ancienne base Android : les sessions déjà enregistrées sont conservées.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE micro_spots ADD COLUMN tidePhase TEXT NOT NULL DEFAULT 'any'")
        connection.execSQL("ALTER TABLE micro_spots ADD COLUMN minHeight REAL")
        connection.execSQL("ALTER TABLE micro_spots ADD COLUMN maxHeight REAL")
        connection.execSQL("ALTER TABLE micro_spots ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
        connection.execSQL("ALTER TABLE condition_snapshots ADD COLUMN tidePhase TEXT")
    }
}

/** v3 → v4 : volume des planches (quiver structuré). Les planches existantes sont conservées. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE quiver ADD COLUMN volumeL REAL")
        connection.execSQL("ALTER TABLE quiver ADD COLUMN volumeEstimated INTEGER NOT NULL DEFAULT 0")
    }
}

/** v4 → v5 : note prévue par l'appli, gardée avec chaque session pour la comparer au ressenti. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE condition_snapshots ADD COLUMN forecastScore INTEGER")
    }
}

const val SESSION_LOG_DB_NAME = "session_log.db"

/** Fin de configuration commune : le builder vient de la plateforme (chemin du fichier). */
fun RoomDatabase.Builder<SessionLogDatabase>.buildSessionLogDatabase(): SessionLogDatabase =
    setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.Default)
        // Fonctionnalité en cours de développement, pas de migrations écrites :
        // un changement de schéma recrée la base plutôt que de planter.
        .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
        .fallbackToDestructiveMigration(true)
        .build()
