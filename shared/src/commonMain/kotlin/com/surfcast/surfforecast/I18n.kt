package com.surfcast.surfforecast

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Traduction de l'interface. Le texte français sert de clé : `tr("Vent")` rend « Wind », « Wind », « Wind »…
 * selon la langue choisie ; un texte sans traduction reste en français. La langue est un état Compose :
 * les écrans qui appellent `tr()` se redessinent quand elle change.
 */
object I18n {
    var language by mutableStateOf("fr")
}

/** Ordre des traductions dans [TRANSLATIONS] : anglais, allemand, néerlandais, espagnol. */
private val LANG_INDEX = mapOf("en" to 0, "de" to 1, "nl" to 2, "es" to 3)

fun tr(fr: String): String {
    val i = LANG_INDEX[I18n.language] ?: return fr
    return TRANSLATIONS[fr]?.getOrNull(i)?.takeIf { it.isNotEmpty() } ?: fr
}

/** Comme [tr], avec des valeurs à la place de {0}, {1}… (l'ordre des mots change d'une langue à l'autre). */
fun tr(fr: String, vararg args: Any): String {
    var s = tr(fr)
    args.forEachIndexed { i, a -> s = s.replace("{$i}", a.toString()) }
    return s
}

/** Textes sans traduction, pour les tests : un français devenu introuvable se voit tout de suite. */
internal fun missingTranslations(): List<String> =
    TRANSLATIONS.filterValues { it.size != 4 || it.any { t -> t.isBlank() } }.keys.toList()

private fun t(en: String, de: String, nl: String, es: String) = listOf(en, de, nl, es)

private val TRANSLATIONS: Map<String, List<String>> = mapOf(
    // Barre du bas
    "Prévisions" to t("Forecast", "Vorhersage", "Voorspelling", "Previsión"),
    "Webcam" to t("Webcam", "Webcam", "Webcam", "Webcam"),
    "Journal" to t("Log", "Logbuch", "Logboek", "Diario"),
    "Comprendre" to t("Learn", "Verstehen", "Begrijpen", "Entender"),
    "Réglages" to t("Settings", "Einstellungen", "Instellingen", "Ajustes"),
    // Encarts
    "Houle" to t("Swell", "Dünung", "Deining", "Oleaje"),
    "Vent" to t("Wind", "Wind", "Wind", "Viento"),
    "Météo" to t("Weather", "Wetter", "Weer", "Tiempo"),
    "Marée" to t("Tide", "Gezeiten", "Getij", "Marea"),
    "Prévisions de la semaine" to t("Week forecast", "Wochenvorhersage", "Weekvoorspelling", "Previsión semanal"),
    "Prévision heure par heure" to t("Hour by hour", "Stunde für Stunde", "Uur per uur", "Hora por hora"),
    "Actuellement" to t("Now", "Aktuell", "Nu", "Ahora"),
    "mis à jour" to t("updated", "aktualisiert", "bijgewerkt", "actualizado"),
    "+ Session" to t("+ Session", "+ Session", "+ Sessie", "+ Sesión"),
    "Nuit" to t("Night", "Nacht", "Nacht", "Noche"),
    // Notes
    "Gros jour" to t("Big day", "Großer Tag", "Grote dag", "Día grande"),
    "Mauvais" to t("Poor", "Schlecht", "Slecht", "Malo"),
    "Médiocre" to t("Mediocre", "Mäßig", "Matig", "Mediocre"),
    "Correct" to t("Fair", "Okay", "Redelijk", "Aceptable"),
    "Bon" to t("Good", "Gut", "Goed", "Bueno"),
    "Très bon" to t("Very good", "Sehr gut", "Zeer goed", "Muy bueno"),
    "Parfait" to t("Perfect", "Perfekt", "Perfect", "Perfecto"),
    "Challengeant" to t("Challenging", "Anspruchsvoll", "Uitdagend", "Exigente"),
    "Petit jour" to t("Small day", "Kleiner Tag", "Kleine dag", "Día pequeño"),
    "Trop de vent" to t("Too windy", "Zu windig", "Te veel wind", "Demasiado viento"),
    " à " to t(" to ", " bis ", " tot ", " a "),
    // Réglages
    "Profil" to t("Profile", "Profil", "Profiel", "Perfil"),
    "Matériel" to t("Gear", "Ausrüstung", "Materiaal", "Material"),
    "Sources et modèles" to t("Sources and models", "Quellen und Modelle", "Bronnen en modellen", "Fuentes y modelos"),
    "Application" to t("App", "App", "App", "Aplicación"),
    "Aide" to t("Help", "Hilfe", "Help", "Ayuda"),
    "Aide et langue" to t("Help and language", "Hilfe und Sprache", "Help en taal", "Ayuda e idioma"),
    "Langue" to t("Language", "Sprache", "Taal", "Idioma"),
    "Mon surf" to t("My surfing", "Mein Surfen", "Mijn surfen", "Mi surf"),
    "Taille du texte" to t("Text size", "Textgröße", "Tekstgrootte", "Tamaño del texto"),
    "Visite guidée" to t("Guided tour", "Geführte Tour", "Rondleiding", "Visita guiada"),
    "Revoir la visite guidée" to t("Replay the guided tour", "Tour erneut ansehen", "Rondleiding opnieuw bekijken", "Repetir la visita guiada"),
    "Journal de bord" to t("Surf log", "Surf-Logbuch", "Surflogboek", "Diario de surf"),
    "Sauvegarder le journal" to t("Back up the log", "Logbuch sichern", "Logboek back-uppen", "Guardar el diario"),
    "Restaurer un journal" to t("Restore a log", "Logbuch wiederherstellen", "Logboek herstellen", "Restaurar un diario"),
    "Installer l'appli" to t("Install the app", "App installieren", "App installeren", "Instalar la aplicación"),
    "Ajouter à l'écran d'accueil" to t("Add to home screen", "Zum Startbildschirm hinzufügen", "Toevoegen aan beginscherm", "Añadir a la pantalla de inicio"),
    "Ton niveau et ce que tu surfes" to t("Your level and what you surf", "Dein Level und was du surfst", "Je niveau en wat je surft", "Tu nivel y lo que surfeas"),
    "Planches et planche du jour" to t("Boards and board of the day", "Bretter und Brett des Tages", "Boards en board van de dag", "Tablas y tabla del día"),
    "D'où viennent la houle, le vent et la météo" to t("Where swell, wind and weather come from", "Woher Dünung, Wind und Wetter kommen", "Waar deining, wind en weer vandaan komen", "De dónde vienen el oleaje, el viento y el tiempo"),
    "Visite guidée et langue" to t("Guided tour and language", "Geführte Tour und Sprache", "Rondleiding en taal", "Visita guiada e idioma"),
    "Recommandé" to t("Recommended", "Empfohlen", "Aanbevolen", "Recomendado"),
    "Auto" to t("Auto", "Auto", "Auto", "Auto"),
    "Manuel" to t("Manual", "Manuell", "Handmatig", "Manual"),
    // Divers
    "Aujourd'hui" to t("Today", "Heute", "Vandaag", "Hoy"),
    "Demain" to t("Tomorrow", "Morgen", "Morgen", "Mañana"),
    "Fermer" to t("Close", "Schließen", "Sluiten", "Cerrar"),
    "Annuler" to t("Cancel", "Abbrechen", "Annuleren", "Cancelar"),
    "Enregistrer" to t("Save", "Speichern", "Opslaan", "Guardar"),
    "Supprimer" to t("Delete", "Löschen", "Verwijderen", "Eliminar"),
    "Modifier" to t("Edit", "Bearbeiten", "Bewerken", "Editar")
)
