#!/usr/bin/env python3
"""Exporte les traductions de l'appli en fichiers Excel (un par langue) pour une relecture humaine.

Colonnes : N°, Écran, Français, Traduction actuelle, Correction (à remplir), Commentaire.
Usage : export_xlsx.py [dossier de sortie]   (langues : en, de, nl, es)
"""
import glob, os, re, sys
from openpyxl import Workbook
from openpyxl.styles import Alignment, Font, PatternFill
from openpyxl.utils import get_column_letter

SRC = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "shared", "src", "commonMain", "kotlin", "com", "surfcast", "surfforecast")
LANGS = [("en", "Anglais"), ("de", "Allemand"), ("nl", "Néerlandais"), ("es", "Espagnol")]
SCREENS = [  # (début du nom de fichier, écran)
    ("NowTide", "Actuellement"), ("ObservedNow", "Actuellement"), ("DailyTimeline", "Déroulé de la journée"), ("ModelVoices", "Modèles partagés"),
    ("WindCard", "Vent"), ("WeatherCard", "Météo"), ("SurfCard", "Houle"), ("HourlyForecast", "Prévisions heure par heure"),
    ("Preferences", "Réglages"), ("Settings", "Réglages"), ("Quiver", "Matériel"), ("Gear", "Matériel"), ("BoardVolume", "Matériel"),
    ("SessionLog", "Journal de session"), ("SessionFeel", "Journal de session"), ("SessionSummary", "Journal de session"),
    ("Comprendre", "Comprendre"), ("LevelQuestionnaire", "Questionnaire de niveau"), ("SurferProfile", "Profil"),
    ("CoachMarks", "Visite guidée"), ("SpotSelection", "Choix du spot"), ("SpotHeader", "Choix du spot"), ("CardInfo", "Réglages d'un encart"),
    ("MainScreen", "Écran principal"), ("SurfController", "Messages de l'appli"), ("SurfRepository", "Messages de l'appli"),
    ("SurfSpotsData", "Noms de spots et régions"), ("DayPhrase", "Phrases du jour"), ("SurfScoring", "Notes et explications"),
    ("ProfileWizard", "Profil"), ("ForecastModels", "Modèles de prévision"), ("ForecastSources", "Sources et modèles"), ("TextDrawing", "Graphiques"),
    ("ForecastHistory", "Historique des relevés"), ("TimeRuler", "Barre de temps"), ("CellBars", "Barre de temps"),
]


def unescape(s):
    return (s.replace('\\n', '\n').replace('\\t', '\t').replace('\\"', '"').replace('\\$', '$').replace('\\\\', '\\'))


STR = r'"((?:[^"\\]|\\.)*)"'
ENTRY = re.compile(r'^\s*' + STR + r'\s+to\s+(?:t\(|listOf\()\s*' + STR + r'\s*,\s*' + STR + r'\s*,\s*' + STR + r'\s*,\s*' + STR, re.M | re.S)


def load_entries():
    out = []
    for f in sorted(glob.glob(os.path.join(SRC, "Translations*.kt"))) + [os.path.join(SRC, "I18n.kt")]:
        for m in ENTRY.finditer(open(f, encoding="utf-8").read()):
            out.append([unescape(g) for g in m.groups()])
    seen, res = set(), []
    for e in out:  # le dernier gagne (CORE de I18n.kt passe après les fichiers générés)
        pass
    d = {}
    for e in out:
        d[e[0]] = e[1:]
    return d


def screen_of(fr, sources):
    for name, text in sources:
        if fr in text:
            for prefix, screen in SCREENS:
                if name.startswith(prefix):
                    return screen
            return name.replace(".kt", "")
    return "Texte construit ou divers"


INSTR = {
    "fr": ("Consignes de relecture", [
        "Surf Log est une appli de prévisions de surf (houle, vent, marée) et un journal de sessions. Les textes sont écrits en français ; on te demande de relire leur traduction.",
        "1. Lis la colonne C (français) et la colonne D (traduction actuelle).",
        "2. Si la traduction est bonne : ne fais rien.",
        "3. Si elle est fausse, bancale, trop littérale ou si le terme de surf n'est pas celui qu'on utilise vraiment : écris la bonne version dans la colonne E (jaune).",
        "4. Un commentaire est possible dans la colonne F (facultatif), par exemple si le sens change selon l'écran.",
        "Règles : tutoiement (l'appli dit « tu »). Garde tels quels les chiffres, unités, symboles (› · + ≈ %) et les mots entre accolades ou dollars. Ne traduis pas « Surf Log ». Textes courts pour les boutons et titres (peu de place sur un téléphone). Utilise le vocabulaire que les surfeurs utilisent vraiment, pas une traduction mot à mot.",
        "Une même phrase peut apparaître à plusieurs endroits : la colonne B indique le premier. Merci !"]),
    "en": ("Review instructions", [
        "Surf Log is a surf forecast app (swell, wind, tide) with a session log. The texts are written in French; please review their translation.",
        "1. Read column C (French) and column D (current translation).",
        "2. If the translation is good: do nothing.",
        "3. If it is wrong, awkward, too literal, or not the surf term people really use: write the better version in column E (yellow).",
        "4. You can add a comment in column F (optional), e.g. if the meaning depends on the screen.",
        "Rules: informal address (the app says « tu »). Keep numbers, units, symbols (› · + ≈ %) and any placeholders unchanged. Do not translate « Surf Log ». Keep buttons and titles short (little room on a phone). Use the vocabulary surfers actually use, not word-for-word translation.",
        "The same sentence can appear in several places: column B shows the first one. Thank you!"]),
    "de": ("Hinweise zur Überprüfung", [
        "Surf Log ist eine App für Surf-Vorhersagen (Dünung, Wind, Gezeiten) mit Session-Logbuch. Die Texte sind auf Französisch geschrieben; bitte prüfe die deutsche Übersetzung.",
        "1. Lies Spalte C (Französisch) und Spalte D (aktuelle Übersetzung).",
        "2. Ist die Übersetzung gut: nichts tun.",
        "3. Ist sie falsch, unnatürlich, zu wörtlich oder nicht der unter Surfern übliche Begriff: schreibe die bessere Version in Spalte E (gelb).",
        "4. Ein Kommentar in Spalte F ist möglich (optional), z. B. wenn die Bedeutung vom Bildschirm abhängt.",
        "Regeln: informelle Anrede (die App sagt « tu » = du). Zahlen, Einheiten, Symbole (› · + ≈ %) und Platzhalter unverändert lassen. « Surf Log » nicht übersetzen. Buttons und Titel kurz halten (wenig Platz auf dem Handy). Verwende die Begriffe, die Surfer wirklich benutzen, keine Wort-für-Wort-Übersetzung.",
        "Derselbe Satz kann an mehreren Stellen vorkommen: Spalte B zeigt die erste. Danke!"]),
    "nl": ("Instructies voor de controle", [
        "Surf Log is een app met surfvoorspellingen (deining, wind, getij) en een sessielogboek. De teksten zijn in het Frans geschreven; controleer alsjeblieft de Nederlandse vertaling.",
        "1. Lees kolom C (Frans) en kolom D (huidige vertaling).",
        "2. Is de vertaling goed: doe niets.",
        "3. Is ze fout, onnatuurlijk, te letterlijk of niet de term die surfers echt gebruiken: schrijf de betere versie in kolom E (geel).",
        "4. Een opmerking in kolom F kan (optioneel), bijvoorbeeld als de betekenis van het scherm afhangt.",
        "Regels: informele aanspreekvorm (de app zegt « tu » = je). Laat cijfers, eenheden, symbolen (› · + ≈ %) en plaatshouders ongewijzigd. Vertaal « Surf Log » niet. Houd knoppen en titels kort (weinig ruimte op een telefoon). Gebruik de woorden die surfers echt gebruiken, geen woord-voor-woordvertaling.",
        "Dezelfde zin kan op meerdere plekken staan: kolom B toont de eerste. Bedankt!"]),
    "es": ("Instrucciones de revisión", [
        "Surf Log es una app de previsiones de surf (oleaje, viento, marea) con un diario de sesiones. Los textos están escritos en francés; por favor revisa su traducción al español.",
        "1. Lee la columna C (francés) y la columna D (traducción actual).",
        "2. Si la traducción es buena: no hagas nada.",
        "3. Si es incorrecta, poco natural, demasiado literal o no es el término que los surfistas usan de verdad: escribe la versión mejor en la columna E (amarillo).",
        "4. Puedes añadir un comentario en la columna F (opcional), por ejemplo si el sentido cambia según la pantalla.",
        "Reglas: trato informal (la app dice « tu » = tú). Deja igual los números, unidades, símbolos (› · + ≈ %) y marcadores. No traduzcas « Surf Log ». Textos cortos en botones y títulos (poco espacio en el móvil). Usa el vocabulario que los surfistas usan de verdad, no una traducción palabra por palabra.",
        "Una misma frase puede aparecer en varios sitios: la columna B indica el primero. ¡Gracias!"]),
}
GLOSS_TERMS = ["Montant / Descendant", "Houle", "Clapot / Mer de vent", "Période", "Série", "Take-off", "Passer la barre", "Mousse / Reforme / Large",
               "Pic", "Offshore / Onshore / Travers", "Coefficient", "Volume / Planche du jour", "Shortboard, Fish, Mid-length, Longboard",
               "Prévu / Vécu", "Modèles partagés", "Mesures en direct"]
GLOSS = {
    "en": ["Rising tide / falling tide. Always capitalised in the app.", "Swell: waves formed far out at sea that reach the coast.",
           "Wind chop / wind swell: small messy waves made by the local wind.", "Swell period: seconds between two waves. The longer it is, the more powerful the swell.",
           "Set: a group of bigger waves arriving together.", "The moment you stand up on the board.", "Getting out through the breaking waves to the line-up.",
           "Whitewater / a wave that has broken and reforms / the outside peak.", "Peak: the spot where the wave starts to break.",
           "Wind blowing from land (smooths the wave) / from the sea (spoils it) / from the side (cross-shore).",
           "French tide index (20 to 120): the higher it is, the bigger the tidal range.", "Litres of the board / the board recommended for the conditions.",
           "Board types; usually kept as they are.", "What the app forecast / what the surfer felt in the water (session log).",
           "The forecast models disagree with each other.", "Real measurements from a buoy (swell) and a weather station (wind)."],
    "de": ["Steigende / fallende Flut (Gezeiten). In der App immer mit Großbuchstaben.", "Dünung: Wellen, die weit draußen auf dem Meer entstehen und die Küste erreichen.",
           "Windwelle / Windsee: kleine, unruhige Wellen, die der lokale Wind erzeugt.", "Wellenperiode: Sekunden zwischen zwei Wellen. Je länger, desto kraftvoller die Dünung.",
           "Set: eine Gruppe größerer Wellen, die zusammen ankommen.", "Der Moment, in dem man auf dem Brett aufsteht.", "Durch die brechenden Wellen ins Line-up hinauspaddeln.",
           "Weißwasser / eine gebrochene Welle, die sich neu formt / der Peak weiter draußen.", "Peak: die Stelle, an der die Welle zu brechen beginnt.",
           "Wind vom Land (glättet die Welle) / vom Meer (verschlechtert sie) / seitlich (Cross-Shore).",
           "Französischer Gezeitenindex (20 bis 120): je höher, desto größer der Tidenhub.", "Liter des Bretts / das für die Bedingungen empfohlene Brett.",
           "Brettarten; werden meist unverändert übernommen.", "Was die App vorhergesagt hat / was der Surfer im Wasser erlebt hat (Session-Logbuch).",
           "Die Vorhersagemodelle sind sich nicht einig.", "Echte Messungen einer Boje (Dünung) und einer Wetterstation (Wind)."],
    "nl": ["Opkomend / afgaand tij. In de app altijd met hoofdletter.", "Deining: golven die ver op zee ontstaan en de kust bereiken.",
           "Windgolven / korte wind-chop: kleine, rommelige golven door de lokale wind.", "Golfperiode: seconden tussen twee golven. Hoe langer, hoe krachtiger de deining.",
           "Set: een groep grotere golven die samen aankomen.", "Het moment waarop je op de plank gaat staan.", "Door de brekende golven naar buiten peddelen naar de line-up.",
           "Schuim / een gebroken golf die zich opnieuw vormt / de piek verder buiten.", "Piek: de plek waar de golf begint te breken.",
           "Wind van het land (maakt de golf glad) / van zee (verpest hem) / zijwaarts (cross-shore).",
           "Frans getij-index (20 tot 120): hoe hoger, hoe groter het verschil tussen hoog en laag water.", "Liters van de plank / de plank die bij de condities past.",
           "Plankentypes; meestal ongewijzigd overgenomen.", "Wat de app voorspelde / wat de surfer in het water ervoer (sessielogboek).",
           "De voorspellingsmodellen zijn het niet eens met elkaar.", "Echte metingen van een boei (deining) en een weerstation (wind)."],
    "es": ["Marea que sube / que baja. En la app siempre con mayúscula.", "Oleaje: olas formadas lejos en el mar que llegan a la costa.",
           "Mar de viento / chop: olas pequeñas y desordenadas creadas por el viento local.", "Periodo del oleaje: segundos entre dos olas. Cuanto más largo, más potente el oleaje.",
           "Serie (set): grupo de olas más grandes que llegan juntas.", "El momento de ponerse de pie en la tabla.", "Salir al pico atravesando las olas que rompen.",
           "Espuma / ola que ya rompió y se reforma / el pico de fuera.", "Pico: el punto donde la ola empieza a romper.",
           "Viento que viene de tierra (alisa la ola) / del mar (la estropea) / de lado (cross-shore).",
           "Índice de marea francés (de 20 a 120): cuanto más alto, mayor la diferencia entre pleamar y bajamar.", "Litros de la tabla / la tabla recomendada para las condiciones.",
           "Tipos de tabla; normalmente se dejan tal cual.", "Lo que la app pronosticó / lo que el surfista sintió en el agua (diario de sesiones).",
           "Los modelos de previsión no coinciden entre sí.", "Mediciones reales de una boya (oleaje) y de una estación meteorológica (viento)."],
}
GLOSS_HEAD = {"en": ("Term (French)", "Meaning"), "de": ("Begriff (Französisch)", "Bedeutung"), "nl": ("Term (Frans)", "Betekenis"), "es": ("Término (francés)", "Significado")}
GLOSS_TITLE = {"en": "Glossary", "de": "Glossar", "nl": "Woordenlijst", "es": "Glosario"}


def add_instructions(wb, code):
    title, lines = INSTR[code]
    ws = wb.create_sheet(title[:30], 0)
    ws.column_dimensions["A"].width = 120
    ws.append([title])
    ws["A1"].font = Font(bold=True, size=14)
    for ln in lines:
        ws.append([ln])
    for row in ws.iter_rows(min_row=2):
        row[0].alignment = Alignment(wrap_text=True, vertical="top")
    g = wb.create_sheet(GLOSS_TITLE[code], 1)
    g.append(list(GLOSS_HEAD[code]))
    for c in g[1]:
        c.font = Font(bold=True, color="FFFFFF"); c.fill = PatternFill("solid", fgColor="1F4E79")
    for term, meaning in zip(GLOSS_TERMS, GLOSS[code]):
        g.append([term, meaning])
    g.column_dimensions["A"].width = 38; g.column_dimensions["B"].width = 100
    for row in g.iter_rows(min_row=2):
        for c in row:
            c.alignment = Alignment(wrap_text=True, vertical="top")


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else "."
    os.makedirs(out, exist_ok=True)
    entries = load_entries()
    sources = []
    for f in sorted(glob.glob(os.path.join(SRC, "*.kt"))):
        n = os.path.basename(f)
        if n.startswith("Translations") or n == "I18n.kt":
            continue
        sources.append((n, open(f, encoding="utf-8").read()))
    rows = []
    for fr, tr in entries.items():
        esc = fr.replace('\\', '\\\\').replace('"', '\\"').replace('\n', '\\n')
        rows.append((screen_of(esc, sources), fr, tr))
    rows.sort(key=lambda r: (r[0], len(r[1]) > 160, r[1].lower()))
    for i, (code, label) in enumerate(LANGS):
        wb = Workbook()
        ws = wb.active
        ws.title = label
        head = ["N°", "Écran", "Français", f"Traduction actuelle ({label})", "Correction (à remplir)", "Commentaire"]
        ws.append(head)
        for c in ws[1]:
            c.font = Font(bold=True, color="FFFFFF")
            c.fill = PatternFill("solid", fgColor="1F4E79")
            c.alignment = Alignment(vertical="center", wrap_text=True)
        for n, (screen, fr, tr) in enumerate(rows, 1):
            ws.append([n, screen, fr, tr[i], "", ""])
        for col, w in zip("ABCDEF", (6, 24, 60, 60, 50, 30)):
            ws.column_dimensions[col].width = w
        for row in ws.iter_rows(min_row=2):
            for c in row:
                c.alignment = Alignment(vertical="top", wrap_text=True)
            row[4].fill = PatternFill("solid", fgColor="FFF2CC")
        ws.freeze_panes = "A2"
        ws.auto_filter.ref = f"A1:F{len(rows) + 1}"
        add_instructions(wb, code)
        wb.active = 0
        path = os.path.join(out, f"traduction-{code}.xlsx")
        wb.save(path)
        print(path, len(rows), "lignes")


main()
