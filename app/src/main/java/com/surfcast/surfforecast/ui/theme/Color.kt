package com.surfcast.surfforecast.ui.theme

import androidx.compose.ui.graphics.Color

object AppColors {
    // --- Couleurs sémantiques (identiques clair/sombre) ---
    val TideHigh = Color(0xFF4FC3F7)      // bleu - marée haute
    val TideHighDark = Color(0xFF1565C0)  // bleu foncé pour texte/icônes sur fond clair
    val TideLow = Color(0xFF66BB6A)       // vert - marée basse
    val TideLowDark = Color(0xFF2E7D32)   // vert foncé pour texte/icônes sur fond clair

    val WindLow = Color(0xFFE8A400)       // jaune doré - vent faible (contraste renforcé)
    val WindMid = Color(0xFFEF6C00)       // orange profond - vent modéré (contraste renforcé)
    val WindHigh = Color(0xFFD32F2F)      // rouge franc - vent fort (contraste renforcé)
    val WindAccent = Color(0xFFE67E22)    // orange utilisé pour le texte/valeur vent

    // --- Thème sombre (existant) ---
    val DarkBackground = Color(0xFF0D1B22)
    val DarkSurface = Color(0xFF14262F)
    val DarkSurfaceVariant = Color(0xFF1B333D)
    val DarkOnBackground = Color(0xFFECEFF1)
    val DarkOnSurfaceVariant = Color(0xFF90A4AE)
    val DarkPillActive = Color(0xFF1565C0)
    val DarkPillInactive = Color(0xFF23404C)

    // --- Thème clair "papier" (option A) ---
    val LightBackground = Color(0xFFF5F3EE)
    val LightSurface = Color(0xFFFFFFFF)
    val LightSurfaceVariant = Color(0xFFEAE7DF)
    val LightOnBackground = Color(0xFF1A1A1A)
    val LightOnSurfaceVariant = Color(0xFF8A8578)
    val LightPillActive = Color(0xFF1A1A1A)
    val LightPillInactive = Color(0xFFEAE7DF)
}
