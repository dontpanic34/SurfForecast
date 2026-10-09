package com.surfcast.surfforecast

/** Langue proposée dans l'appli : code, drapeau, nom dans sa propre langue. */
data class AppLanguage(val code: String, val flag: String, val name: String)

/** Langues proposées. Seul le français est traduit pour l'instant : la traduction viendra une fois l'appli stabilisée. */
val APP_LANGUAGES = listOf(
    AppLanguage("fr", "🇫🇷", "Français"),
    AppLanguage("en", "🇬🇧", "English"),
    AppLanguage("de", "🇩🇪", "Deutsch"),
    AppLanguage("es", "🇪🇸", "Español")
)

fun appLanguage(code: String): AppLanguage = APP_LANGUAGES.firstOrNull { it.code == code } ?: APP_LANGUAGES.first()
