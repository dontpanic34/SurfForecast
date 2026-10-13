package com.surfcast.surfforecast

/** Langue proposée dans l'appli : code, drapeau, nom dans sa propre langue. */
data class AppLanguage(val code: String, val flag: String, val name: String)

/** Langues proposées (l'interface est traduite au fil de l'eau : voir I18n.kt ; un texte sans traduction reste en français). */
val APP_LANGUAGES = listOf(
    AppLanguage("fr", "🇫🇷", "Français"),
    AppLanguage("en", "🇬🇧", "English"),
    AppLanguage("de", "🇩🇪", "Deutsch"),
    AppLanguage("nl", "🇳🇱", "Nederlands"),
    AppLanguage("es", "🇪🇸", "Español")
)

fun appLanguage(code: String): AppLanguage = APP_LANGUAGES.firstOrNull { it.code == code } ?: APP_LANGUAGES.first()
