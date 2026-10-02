package com.surfcast.surfforecast

import android.content.Context
import androidx.room.Room

/** Même fichier que l'app Android actuelle : les sessions déjà notées seront retrouvées. */
fun sessionLogDatabase(context: Context): SessionLogDatabase {
    val appContext = context.applicationContext
    return Room.databaseBuilder<SessionLogDatabase>(
        context = appContext,
        name = appContext.getDatabasePath(SESSION_LOG_DB_NAME).absolutePath
    ).buildSessionLogDatabase()
}
