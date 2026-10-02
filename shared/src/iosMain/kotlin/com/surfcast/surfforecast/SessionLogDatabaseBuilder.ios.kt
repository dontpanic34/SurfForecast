package com.surfcast.surfforecast

import androidx.room.Room
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

/** Base dans le dossier Documents de l'app (sauvegardé par iCloud/iTunes). */
@OptIn(ExperimentalForeignApi::class)
fun sessionLogDatabase(): SessionLogDatabase {
    val documents = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null
    )
    val path = requireNotNull(documents?.path) { "Dossier Documents introuvable" } + "/" + SESSION_LOG_DB_NAME
    return Room.databaseBuilder<SessionLogDatabase>(name = path).buildSessionLogDatabase()
}
