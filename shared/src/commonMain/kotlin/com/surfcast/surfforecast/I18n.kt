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
    // Traduction absente pour cette langue : l'anglais à la place du français.
    TRANSLATIONS[fr]?.let { v -> (v.getOrNull(i)?.takeIf { it.isNotBlank() } ?: v.getOrNull(0)?.takeIf { it.isNotBlank() })?.let { return it } }
    return translateByFragments(fr, I18n.language, i)
}

private val cache = HashMap<String, String>()

/**
 * Texte composé (« houle qui monte de 1,2 m à 1,8 m ») : on remplace les morceaux français connus (les plus longs d'abord)
 * et, en anglais, la virgule décimale devient un point.
 */
private fun translateByFragments(text: String, lang: String, i: Int): String {
    if (text.length < 3 || text.none { it.isLetter() }) return text
    val key = lang + "|" + text
    cache[key]?.let { return it }
    var out = text
    for (f in FRAGMENTS) {
        var from = 0
        while (true) {
            val at = out.indexOf(f.fr, from)
            if (at < 0) break
            val end = at + f.fr.length
            val okBefore = !f.fr.first().isLetter() || at == 0 || !out[at - 1].isLetter()
            val okAfter = !f.fr.last().isLetter() || end >= out.length || !out[end].isLetter()
            val rep = f.tr.getOrNull(i)?.takeIf { it.isNotBlank() } ?: f.tr.getOrNull(0)
            if (okBefore && okAfter && !rep.isNullOrEmpty()) {
                out = out.substring(0, at) + rep + out.substring(end)
                from = at + rep.length
            } else from = at + 1
        }
    }
    // « 1,2 m à 1,8 m » : le « à » entre deux valeurs.
    val to = listOf("to", "bis", "tot", "a")[i]
    out = RANGE_A.replace(out) { it.groupValues[1] + " " + to + " " + it.groupValues[2] }
    if (lang == "en") out = DECIMAL_COMMA.replace(out) { it.groupValues[1] + "." + it.groupValues[2] }
    if (cache.size > 4000) cache.clear()
    cache[key] = out
    return out
}

private val RANGE_A = Regex("(\\d[^\\s]*(?: m| s| km/h| kJ| h)?) à (\\d)")

private val DECIMAL_COMMA = Regex("(\\d),(\\d)")

private class Fragment(val fr: String, val tr: List<String>)

/** Morceaux traduisibles à l'intérieur d'un texte composé : les textes de plus d'un mot (ou en minuscules), plus les morceaux de FRAGMENT_TRANSLATIONS. */
private val FRAGMENTS: List<Fragment> by lazy {
    val all = HashMap<String, List<String>>()
    for ((k, v) in TRANSLATIONS) {
        val t = k.trim()
        if (t.length >= 4 && (t.contains(' ') || t.first().isLowerCase()) && v.size == 4) if (t !in all) all[t] = v
    }
    for ((k, v) in FRAGMENT_TRANSLATIONS) all[k] = v
    all.map { Fragment(it.key, it.value) }.sortedByDescending { it.fr.length }
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

private val CORE: Map<String, List<String>> = mapOf(
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
    "Montante" to t("Rising", "Steigend", "Opkomend", "Subiendo"),
    "Descendante" to t("Falling", "Fallend", "Afgaand", "Bajando"),
    "montante" to t("rising", "steigend", "opkomend", "subiendo"),
    "descendante" to t("falling", "fallend", "afgaand", "bajando"),
    "Touche une ligne pour l'afficher en haut." to t("Tap a line to show it at the top.", "Tippe auf eine Zeile, um sie oben anzuzeigen.", "Tik op een regel om hem bovenaan te tonen.", "Toca una línea para mostrarla arriba."),
    "Principale" to t("Main", "Hauptbrett", "Hoofdboard", "Principal"),
    "★ Principale" to t("★ Main", "★ Hauptbrett", "★ Hoofdboard", "★ Principal"),
    "Mettre en favori" to t("Add to favorites", "Zu Favoriten hinzufügen", "Toevoegen aan favorieten", "Añadir a favoritos"),
    "Favori" to t("Favorite", "Favorit", "Favoriet", "Favorito"),
    "place libre" to t("free slot", "freier Platz", "vrije plek", "lugar libre"),
    "Choisir un spot" to t("Choose a spot", "Spot wählen", "Kies een spot", "Elegir un spot"),
    "Les leçons pour lire les prévisions" to t("Lessons to read the forecast", "Lektionen zum Lesen der Vorhersage", "Lessen om de voorspelling te lezen", "Lecciones para leer la previsión"),
    "Mixte" to t("Both", "Beides", "Beide", "Ambos"),
    "De jour" to t("Daytime", "Tagsüber", "Overdag", "De día"),
    // Divers
    "Aujourd'hui" to t("Today", "Heute", "Vandaag", "Hoy"),
    "Demain" to t("Tomorrow", "Morgen", "Morgen", "Mañana"),
    "Fermer" to t("Close", "Schließen", "Sluiten", "Cerrar"),
    "Annuler" to t("Cancel", "Abbrechen", "Annuleren", "Cancelar"),
    "Enregistrer" to t("Save", "Speichern", "Opslaan", "Guardar"),
    "Supprimer" to t("Delete", "Löschen", "Verwijderen", "Eliminar"),
    "Modifier" to t("Edit", "Bearbeiten", "Bewerken", "Editar")
)

/** Le dictionnaire entier : les textes du cœur ci-dessus, puis les fichiers Translations*.kt (générés). */
private val TRANSLATIONS: Map<String, List<String>> by lazy {
    HashMap<String, List<String>>().apply {
        for (part in GENERATED_TRANSLATIONS) for ((k, v) in part) if (k !in this) put(k, v)
        putAll(CORE)
    }
}
