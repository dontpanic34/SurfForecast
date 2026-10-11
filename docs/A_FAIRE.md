# À faire

## Bug à traiter en priorité (signalé le 11 octobre, 3 captures de Montalivet)
La phrase grise en haut du déroulé ne correspond pas au vent affiché :
- 17 h, vent O (ouest, donc de mer) à 3 km/h : « Vent de côté qui gêne ».
- 13 h, SSE 8 km/h : « Vent de côté qui gêne ».
- 12 h, SSE 8 km/h aussi : « Bonne taille, vent léger ».
Cause très probable trouvée (à confirmer par test) : la note et la phrase sont calculées avec la « mémoire du vent » (`hoursWithWindMemory` / `windMemoryAdjusted`, DailyTimelineCard ligne 110) : le vent des heures précédentes (et de la veille) peut remplacer le vent réel de l'heure, alors que l'écran affiche le vent réel. Analyse faite le 11 octobre (calcul à la main sur les vents des captures, les rafales ne sont pas affichées donc non vérifiées) :
- 17 h, O 3 km/h : la mémoire (`keep` 0,75) garde le vent des heures d'avant (SO 8 km/h à 16 h), donc la note voit environ SO 6 km/h, soit du vent de côté, alors que le vent réel est de mer.
- 12 h et 13 h, SSE 8 km/h : à 12 h la mémoire garde SE ~10 km/h (SE = 135°, classé de terre) ; à 13 h le vent redevient SSE (157°, classé de côté). D'où « Bonne taille, vent léger » puis « Vent de côté qui gêne ». Les rafales (comptées à moitié, gardées en mémoire) pèsent aussi sans être affichées.
- La courbe du vent de côté est sévère : 12 km/h « effectifs » donnent 0,8, donc « qui gêne » dès environ 12 km/h avec rafales.
Correction à décider : afficher le vent pris en compte quand il diffère du vent réel (et les rafales), phrase qui explique la mémoire du vent, et revoir la courbe du vent de côté (étape 2 : règles du vent).
Pistes à vérifier : (1) les rafales comptent à moitié dans le vent de la note mais ne sont pas affichées, donc un vent moyen faible peut donner une phrase de vent ; (2) la phrase de vent n'apparaît que si le vent est le pire facteur sous 0,85, ce qui ne devrait pas arriver à 3 km/h : vérifier que la phrase correspond bien à l'heure sélectionnée (décalage possible entre la colonne de vent, l'heure sélectionnée et l'heure notée) ; (3) la catégorie de vent (de mer, de côté, de terre) doit venir de l'orientation réelle de la plage partout. À faire avec la maquette du banc d'essai du vent : afficher les rafales et la raison exacte, et ajouter un test sur ces trois cas.

## Vent : fait sur la branche (11 octobre), reste à publier et à brancher le « i »
- Vent ressenti codé (voir `JOURNAL.md`). Reste : afficher le ressenti aussi dans « Maintenant », la carte vent détaillée et la météo détaillée (elles montrent encore le vent moyen), et brancher le texte du « i ».
- **Texte du « i » du vent (à intégrer dans l'appli)** : « Le vent affiché est le vent ressenti : le vent moyen plus 35 % de l'écart avec les rafales (8 km/h de moyenne et rafales à 22 km/h donnent 13 km/h). Les rafales froissent la mer par à-coups mais durent peu, on n'en garde donc qu'une part. Jaune : le vent ne gêne pas ; orange : il gêne ; rouge : il est fort. Le vent de terre (de derrière la plage) lisse et creuse la vague, le vent de mer la hache, le vent de côté est entre les deux. La note tient aussi compte du vent des heures précédentes, car la mer met un moment à se calmer. »

## En attente de validation du propriétaire
- (Fait) Phrase de la mémoire du vent : A « Mer encore marquée par le vent d'avant » choisie.
- **Choix du niveau par exemples** (maquette faite, 12 situations x 5 réponses). Après accord : coder l'assistant « Mon profil » (remplacer les questions taille / période / clapot / vent par ces exemples) et brancher les réponses sur le calcul du profil (`ProfileWizard.kt`, `applyExampleAnswers`).
- **Badges « Trop petit » / « Trop gros »** : proposition « Taille limite » et « Gros jour » (orange). À décider.
- **Phrases restantes** (guide, réglages, accueil, explications du profil) contenant « pour toi » ou « trop gros » : repasser avec des tournures du même ton.
- **« Creux et puissant »** sort de deux conditions différentes : faut-il deux phrases ?

## Prochaines tâches
1. **Cohérence vent / note / étoiles / phrase** : une seule règle pour la couleur du vent (jaune au moins 0,9, orange de 0,6 à 0,9, rouge sous 0,6), la même pour les phrases, avec l'orientation réelle de la plage partout. Ajouter `windFactor` au résultat de note pour colorer les flèches du déroulé et de la semaine. Expliquer étoiles (qualité du jour) et libellé de l'heure. Maquette du banc d'essai : `banc_vent` (non publiée en lien).
2. **Nuages** : vérifier le rendu des images prêtes sur l'appareil (pixels, stries, chargement). Sinon plan B : images de ciel générées (par Gemini ou par code), mêmes paliers, même fondu additif. Voir le brief nuages.
3. **Réglages** : refaire en 5 pages courtes (la maquette précédente était jugée faible).
4. **Explications « i »** : pastilles d'explication sur les éléments clés.
5. **Planches, notes et volume** : calcul du volume, notes personnelles.
6. **Hauteur du soleil selon la saison**, vérifier le libellé « 1,0 m 10 s » de la semaine.

## Idées à garder
- Contour de la flèche de vent de la semaine si elle est peu visible sur ciel clair.

## Le ressenti pour tout (règle du 11 octobre 2026, à cadrer par maquette)
- Vent : fait dans le déroulé et la semaine ; reste « Maintenant », cartes vent, détail, bandeau.
- Nuages : ressenti = ce qu'on voit du ciel (éclaircies, soleil voilé) plutôt que le % brut.
- Maquette `ressenti_temp_nuages.html` envoyée au propriétaire (température ressentie : gros chiffre = ressenti, air en petit ; nuages : un mot pour le ciel) : validée, codée sur la branche.
- Température : température ressentie (vent, humidité, soleil) plutôt que la température de l'air ; eau et combinaison à voir.
- Houle : **pas de ressenti**, on garde la donnée brute (décision du propriétaire).
- Semaine : phrase du jour choisi à la place du titre + température ressentie par jour : maquette `semaine_phrase.html` envoyée, en attente de validation.
