package com.surfcast.surfforecast

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

/**
 * Base du journal de bord (sessions, quiver, sous-spots), portage multiplateforme de
 * SessionLogDatabase d'app/ : mêmes tables, même nom de fichier "session_log.db".
 * Room KMP : SQLite embarqué (BundledSQLiteDriver), identique sur Android et iOS.
 */
@Database(
    entities = [QuiverBoard::class, MicroSpot::class, ConditionSnapshot::class, SurfSession::class],
    version = 2,
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

const val SESSION_LOG_DB_NAME = "session_log.db"

/** Fin de configuration commune : le builder vient de la plateforme (chemin du fichier). */
fun RoomDatabase.Builder<SessionLogDatabase>.buildSessionLogDatabase(): SessionLogDatabase =
    setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.Default)
        // Fonctionnalité en cours de développement, pas de migrations écrites :
        // un changement de schéma recrée la base plutôt que de planter.
        .fallbackToDestructiveMigration(true)
        .build()
