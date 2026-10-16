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
    "Comparer" to t("Compare", "Vergleichen", "Vergelijken", "Comparar"),
    "Prévision à gauche, ta session à droite" to t("Forecast on the left, your session on the right", "Vorhersage links, deine Session rechts", "Voorspelling links, jouw sessie rechts", "Previsión a la izquierda, tu sesión a la derecha"),
    "Prévision" to t("Forecast", "Vorhersage", "Voorspelling", "Previsión"),
    "Ta session" to t("Your session", "Deine Session", "Jouw sessie", "Tu sesión"),
    "Direction" to t("Direction", "Richtung", "Richting", "Dirección"),
    "de la plage" to t("from the beach", "vom Strand", "van het strand", "de la playa"),
    "la houle" to t("the swell", "die Dünung", "de deining", "el oleaje"),
    "sa direction" to t("its direction", "ihre Richtung", "de richting", "su dirección"),
    "le vent" to t("the wind", "der Wind", "de wind", "el viento"),
    "la marée" to t("the tide", "die Gezeiten", "het tij", "la marea"),
    "Les conditions sont proches sur tous les points." to t("Conditions are close on every point.", "Die Bedingungen sind in allen Punkten ähnlich.", "De omstandigheden lijken op alle punten op elkaar.", "Las condiciones son parecidas en todos los puntos."),
    "L'écart principal :" to t("Main difference:", "Hauptunterschied:", "Belangrijkste verschil:", "Diferencia principal:"),
    "Barre colorée : vert = proche, jaune = un peu différent, rouge = différent." to t("Colored bar: green = close, yellow = slightly different, red = different.", "Farbbalken: grün = ähnlich, gelb = etwas anders, rot = anders.", "Gekleurde balk: groen = dichtbij, geel = een beetje anders, rood = anders.", "Barra de color: verde = parecido, amarillo = algo distinto, rojo = distinto."),
    "Régler, plier, déplacer" to t("Adjust, fold, move", "Einstellen, einklappen, verschieben", "Instellen, inklappen, verplaatsen", "Ajustar, plegar, mover"),
    "Le ⚙ d'un encart ouvre ses réglages et ses explications. La flèche ▾ le plie ou le déplie. Garde le doigt appuyé sur son titre pour le déplacer." to t(
        "A card's ⚙ opens its settings and explanations. The ▾ arrow folds or unfolds it. Keep your finger pressed on its title to move it.",
        "Das ⚙ einer Karte öffnet ihre Einstellungen und Erklärungen. Der Pfeil ▾ klappt sie ein oder aus. Halte den Finger auf ihrem Titel gedrückt, um sie zu verschieben.",
        "De ⚙ van een kaart opent de instellingen en uitleg. De pijl ▾ klapt hem in of uit. Houd je vinger op de titel ingedrukt om hem te verplaatsen.",
        "El ⚙ de una tarjeta abre sus ajustes y explicaciones. La flecha ▾ la pliega o la despliega. Mantén el dedo pulsado sobre su título para moverla."),
    "Agadir & Taghazout" to listOf("Agadir & Taghazout", "Agadir & Taghazout", "Agadir & Taghazout", "Agadir y Taghazout"),
    "Andalousie méditerranéenne" to listOf("Mediterranean Andalusia", "Mediterranes Andalusien", "Mediterraan Andalusië", "Andalucía mediterránea"),
    "Anti-Atlas" to listOf("Anti-Atlas", "Anti-Atlas", "Anti-Atlas", "Anti-Atlas"),
    "Aveiro & Figueira" to listOf("Aveiro & Figueira", "Aveiro & Figueira", "Aveiro & Figueira", "Aveiro y Figueira"),
    "Açores" to listOf("Azores", "Azoren", "Azoren", "Azores"),
    "Baléares" to listOf("Balearic Islands", "Balearen", "Balearen", "Baleares"),
    "Cadix" to listOf("Cádiz", "Cádiz", "Cádiz", "Cádiz"),
    "Canaries" to listOf("Canary Islands", "Kanarische Inseln", "Canarische Eilanden", "Canarias"),
    "Casablanca" to listOf("Casablanca", "Casablanca", "Casablanca", "Casablanca"),
    "Casablanca & Doukkala" to listOf("Casablanca & Doukkala", "Casablanca & Doukkala", "Casablanca & Doukkala", "Casablanca y Doukkala"),
    "Catalogne" to listOf("Catalonia", "Katalonien", "Catalonië", "Cataluña"),
    "Costa da Morte" to listOf("Costa da Morte", "Costa da Morte", "Costa da Morte", "Costa da Morte"),
    "Côte Nord & Rabat" to listOf("North Coast & Rabat", "Nordküste & Rabat", "Noordkust & Rabat", "Costa Norte y Rabat"),
    "Côte Ouest (Galice)" to listOf("West Coast (Galicia)", "Westküste (Galicien)", "Westkust (Galicië)", "Costa Oeste (Galicia)"),
    "Détroit de Gibraltar" to listOf("Strait of Gibraltar", "Straße von Gibraltar", "Straat van Gibraltar", "Estrecho de Gibraltar"),
    "El Jadida & Safi" to listOf("El Jadida & Safi", "El Jadida & Safi", "El Jadida & Safi", "El Jadida y Safi"),
    "Essaouira" to listOf("Essaouira", "Essaouira", "Essaouira", "Essaouira"),
    "Huelva" to listOf("Huelva", "Huelva", "Huelva", "Huelva"),
    "Imsouane" to listOf("Imsouane", "Imsouane", "Imsouane", "Imsouane"),
    "Madère" to listOf("Madeira", "Madeira", "Madeira", "Madeira"),
    "Maroc" to listOf("Morocco", "Marokko", "Marokko", "Marruecos"),
    "Murcie & Alicante" to listOf("Murcia & Alicante", "Murcia & Alicante", "Murcia & Alicante", "Murcia y Alicante"),
    "Méditerranée" to listOf("Mediterranean", "Mittelmeer", "Middellandse Zee", "Mediterráneo"),
    "Rabat & Kénitra" to listOf("Rabat & Kenitra", "Rabat & Kenitra", "Rabat & Kenitra", "Rabat y Kenitra"),
    "Rif & Oriental" to listOf("Rif & Oriental", "Rif & Oriental", "Rif & Oriental", "Rif y Oriental"),
    "Rías Baixas" to listOf("Rías Baixas", "Rías Baixas", "Rías Baixas", "Rías Baixas"),
    "Sahara" to listOf("Sahara", "Sahara", "Sahara", "Sahara"),
    "Sud & Sahara" to listOf("South & Sahara", "Süden & Sahara", "Zuid & Sahara", "Sur y Sáhara"),
    "Sud-Est & Méditerranée" to listOf("South-East & Mediterranean", "Südosten & Mittelmeer", "Zuidoosten & Middellandse Zee", "Sureste y Mediterráneo"),
    "Sud-Ouest (Andalousie atlantique)" to listOf("South-West (Atlantic Andalusia)", "Südwesten (Atlantisches Andalusien)", "Zuidwesten (Atlantisch Andalusië)", "Suroeste (Andalucía atlántica)"),
    "Taghazout & Agadir" to listOf("Taghazout & Agadir", "Taghazout & Agadir", "Taghazout & Agadir", "Taghazout y Agadir"),
    "Tanger & Larache" to listOf("Tangier & Larache", "Tanger & Larache", "Tanger & Larache", "Tánger y Larache"),
    "Valence" to listOf("Valencia", "Valencia", "Valencia", "Valencia"),
    "Îles (Baléares & Canaries)" to listOf("Islands (Balearics & Canaries)", "Inseln (Balearen & Kanaren)", "Eilanden (Balearen & Canarische Eilanden)", "Islas (Baleares y Canarias)"),
    "Îles (Madère & Açores)" to listOf("Islands (Madeira & Azores)", "Inseln (Madeira & Azoren)", "Eilanden (Madeira & Azoren)", "Islas (Madeira y Azores)"),
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
    "Jours plus anciens ▾" to t("Older days ▾", "Ältere Tage ▾", "Oudere dagen ▾", "Días anteriores ▾"),
    "Jours plus anciens ▴" to t("Older days ▴", "Ältere Tage ▴", "Oudere dagen ▴", "Días anteriores ▴"),
    "Hauteur de la courbe de marée" to t("Tide curve height", "Höhe der Gezeitenkurve", "Hoogte van de getijdenkromme", "Altura de la curva de marea"),
    "Plein haut dans" to t("High tide in", "Hochwasser in", "Hoogwater over", "Pleamar en"),
    "Plein haut à" to t("High tide at", "Hochwasser um", "Hoogwater om", "Pleamar a las"),
    "Plein bas à" to t("Low tide at", "Niedrigwasser um", "Laagwater om", "Bajamar a las"),
    " à " to t(" at ", " um ", " om ", " a las "),
    "Plein bas dans" to t("Low tide in", "Niedrigwasser in", "Laagwater over", "Bajamar en"),
    "Modifier la session" to t("Edit session", "Session bearbeiten", "Sessie bewerken", "Editar sesión"),
    "Supprimer cette session ?" to t("Delete this session?", "Diese Session löschen?", "Deze sessie verwijderen?", "¿Eliminar esta sesión?"),
    "Elle sera définitivement retirée de ton journal." to t("It will be permanently removed from your log.", "Sie wird endgültig aus deinem Logbuch entfernt.", "Ze wordt definitief uit je logboek verwijderd.", "Se eliminará definitivamente de tu diario."),
    "Ressenti" to t("Feeling", "Gefühl", "Gevoel", "Sensación"),
    "Durée" to t("Duration", "Dauer", "Duur", "Duración"),
    "Appuie sur le + pour noter une session en dix secondes : l'appli garde les conditions. Retrouve-les ensuite dans l'onglet Journal." to t("Tap + to log a session in ten seconds: the app keeps the conditions. Find them afterwards in the Journal tab.", "Tippe auf +, um in zehn Sekunden eine Session einzutragen: Die App speichert die Bedingungen. Du findest sie danach im Tab Journal.", "Tik op + om in tien seconden een sessie te noteren: de app bewaart de condities. Je vindt ze daarna in het tabblad Journal.", "Toca + para anotar una sesión en diez segundos: la app guarda las condiciones. Encuéntralas luego en la pestaña Diario."),
    "Noter une session" to t("Log a session", "Session eintragen", "Sessie noteren", "Anotar una sesión"),
    "Modèles à" to t("Models at", "Modelle um", "Modellen om", "Modelos a las"),
    "Pour changer de modèle : Réglages." to t("To change models: Settings.", "Modelle ändern: Einstellungen.", "Modellen wijzigen: Instellingen.", "Para cambiar de modelo: Ajustes."),
    "Mesures en direct" to t("Live measurements", "Live-Messungen", "Live metingen", "Mediciones en directo"),
    "En direct" to t("Live", "Live", "Live", "En directo"),
    "Développeur" to t("Developer", "Entwickler", "Ontwikkelaar", "Desarrollador"),
    "Statistiques des utilisateurs (clé demandée)" to t("User statistics (key required)", "Nutzerstatistiken (Schlüssel nötig)", "Gebruikersstatistieken (sleutel nodig)", "Estadísticas de usuarios (se pide clave)"),
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
    "Montant" to t("Rising", "Steigend", "Opkomend", "Subiendo"),
    "Descendant" to t("Falling", "Fallend", "Afgaand", "Bajando"),
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
    "Session" to t("Session", "Session", "Sessie", "Sesión"),
    "DÉBUT" to t("START", "BEGINN", "START", "INICIO"),
    "DURÉE" to t("DURATION", "DAUER", "DUUR", "DURACIÓN"),
    "PLANCHE" to t("BOARD", "BOARD", "BOARD", "TABLA"),
    "Planche" to t("Board", "Board", "Board", "Tabla"),
    "Nom de la planche" to t("Board name", "Name des Boards", "Naam van het board", "Nombre de la tabla"),
    "Prévu" to t("Forecast", "Vorhersage", "Voorspeld", "Previsto"),
    "Vécu" to t("Felt", "Erlebt", "Beleefd", "Vivido"),
    "Enregistrer" to t("Save", "Speichern", "Opslaan", "Guardar"),
    "de" to t("from", "von", "van", "de"),
    "à" to t("to", "bis", "tot", "a"),
    "Pas de vent" to t("No wind", "Kein Wind", "Geen wind", "Sin viento"),
    "Offshore" to t("Offshore", "Offshore", "Offshore", "Offshore"),
    "Onshore" to t("Onshore", "Onshore", "Onshore", "Onshore"),
    "Sideshore" to t("Sideshore", "Sideshore", "Sideshore", "Sideshore"),
    "Side-off" to t("Side-off", "Side-off", "Side-off", "Side-off"),
    "Side-on" to t("Side-on", "Side-on", "Side-on", "Side-on"),
    "léger" to t("light", "leicht", "licht", "ligero"),
    "modéré" to t("moderate", "mäßig", "matig", "moderado"),
    "soutenu" to t("strong", "kräftig", "stevig", "fuerte"),
    "Marée haute" to t("High tide", "Hochwasser", "Hoog water", "Marea alta"),
    "Marée basse" to t("Low tide", "Niedrigwasser", "Laag water", "Marea baja"),
    "Gros jour" to t("Big day", "Großer Tag", "Grote dag", "Día grande"),
    "Une note perso (facultatif)" to t("A personal note (optional)", "Eine persönliche Notiz (optional)", "Een persoonlijke notitie (optioneel)", "Una nota personal (opcional)"),
    "Ajouter photo / video" to t("Add photo / video", "Foto / Video hinzufügen", "Foto / video toevoegen", "Añadir foto / vídeo"),
    "1 media joint" to t("1 media attached", "1 Medium angehängt", "1 media bijgevoegd", "1 archivo adjunto"),
    "Historique" to t("History", "Verlauf", "Geschiedenis", "Historial"),
    "Historique des relevés" to t("Records history", "Verlauf der Aufzeichnungen", "Geschiedenis van metingen", "Historial de registros"),
    "Ce que l'appli a relevé en coulisse : les prévisions heure par heure" to t("What the app recorded behind the scenes: hour-by-hour forecasts", "Was die App im Hintergrund aufgezeichnet hat: Vorhersagen Stunde für Stunde", "Wat de app op de achtergrond heeft vastgelegd: voorspellingen uur per uur", "Lo que la app ha registrado en segundo plano: previsiones hora por hora"),
    "Autre" to t("Other", "Andere", "Overig", "Otra"),
    "Aujourd'hui" to t("Today", "Heute", "Vandaag", "Hoy"),
    "Hier" to t("Yesterday", "Gestern", "Gisteren", "Ayer"),
    "Avant-hier" to t("Day before yesterday", "Vorgestern", "Eergisteren", "Anteayer"),
    "Mauvais" to t("Poor", "Schlecht", "Slecht", "Malo"),
    "1. Touche la barre du jour pour choisir l'heure de début, puis règle la durée." to t("1. Tap the day bar to choose the start time, then set the duration.", "1. Tippe auf die Tagesleiste, um die Startzeit zu wählen, und stelle dann die Dauer ein.", "1. Tik op de dagbalk om de starttijd te kiezen en stel daarna de duur in.", "1. Toca la barra del día para elegir la hora de inicio y ajusta después la duración."),
    "2. Choisis ta planche (« + Planche » pour en ajouter une)." to t("2. Pick your board (“+ Board” to add one).", "2. Wähle dein Board („+ Board“ zum Hinzufügen).", "2. Kies je board (“+ Board” om er een toe te voegen).", "2. Elige tu tabla («+ Tabla» para añadir una)."),
    "3. L'appli te dit ce qui était prévu sur ton créneau : touche la couleur de ce que tu as vécu." to t("3. The app tells you what was forecast for your window: tap the colour of what you felt.", "3. Die App sagt dir, was für dein Zeitfenster vorhergesagt war: tippe auf die Farbe deines Erlebnisses.", "3. De app vertelt wat er voorspeld was voor je tijdvak: tik op de kleur van wat je hebt beleefd.", "3. La app te dice lo que estaba previsto en tu franja: toca el color de lo que viviste."),
    "4. Avec tes sessions Très bon ou Parfait, l'appli te signale les prévisions qui leur ressemblent." to t("4. With your Very good or Perfect sessions, the app flags forecasts that look like them.", "4. Mit deinen Sessions „Sehr gut“ oder „Perfekt“ zeigt dir die App ähnliche Vorhersagen.", "4. Met je sessies Zeer goed of Perfect wijst de app voorspellingen aan die erop lijken.", "4. Con tus sesiones Muy bueno o Perfecto, la app te señala las previsiones parecidas."),
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
