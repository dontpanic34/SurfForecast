# À faire

## Plus tard : la condition physique dans « Mon matériel » (12 octobre)
- « Ta rame pour passer la barre » (sans souci / je fatigue au bout d'un moment / je fatigue vite / je n'avance pas aussi vite que je devrais) sort du questionnaire du niveau : c'est de la condition physique. À reprendre avec le volume de planche dans « Mon matériel » (déjà « Forme physique : excellente, bonne, moyenne, faible » aujourd'hui) : probablement la fusionner avec cette « forme physique ».

## Plus tard : le volume de planche et la fréquence (12 octobre)
- Conserver dans le profil la fréquence (quelques fois par an… plusieurs fois par semaine) et la saison (toute l'année / surtout l'été). Au moment de reprendre le volume recommandé (`BoardVolume.kt`, « Mon matériel »), en tenir compte : plus de volume pour quelqu'un qui surfe peu ou seulement l'été. Sujet compliqué, à traiter à part ; rien dans le niveau en attendant.

## Tâche ajoutée le 12 octobre : toutes les marées de la journée d'ensoleillement dans le déroulé
- **Aujourd'hui** (`DaylightTides.kt`, `daylightTides`) : on n'affiche que **deux** marées (`take(2)`), les plus proches du milieu de la journée dans la fenêtre « une demi-heure avant le lever à une demi-heure après le coucher ».
- **Demandé** : afficher **autant de marées que possible** dans la journée d'ensoleillement. Exemple du propriétaire : pleine mer vers 8 h et lever du soleil vers 8 h, on la garde ; la suivante, 6 h plus tard (vers 14 h) ; puis la troisième si elle tombe dans la fenêtre. Deux pleines mers dans la même journée de soleil, c'est normal et il y a la place.
- **Où** : les triangles dans l'eau sur la courbe (`tideMarks`) et la ligne sous la jauge (`TideLine`, qui montre déjà le triangle et l'heure précise de chaque marée). Le propriétaire veut aussi **l'heure précise** à côté des repères : à maquetter (il y a trois marées au plus dans la fenêtre : coefficient + trois heures tiennent sur la ligne).
- **Changement minimal** : garder toutes les marées de la fenêtre (au lieu de deux), et ne compléter avec les plus proches hors fenêtre que s'il y en a moins de deux (comportement actuel conservé dans ce cas). Des tests existent (`DaylightTidesTest.kt`) et attendent deux marées : à adapter (certains cas passeront à trois), et à compléter : lever 8 h / pleine mer 8 h gardée ; fenêtre longue à trois marées ; jour sans marée dans la fenêtre.

## À coder après validation (12 octobre) : side-off / side-on
- Couper le vent « côté » à 90° dans `windCategoryFromDegrees` (side-off = famille offshore, side-on = famille onshore) : touche les couleurs des flèches, les notes (`windFactorOf`, `windCurves`) et les phrases (`windPhrase`, qui dit encore « sideshore »). Les mots des phrases restent à choisir avec le propriétaire (onglet « Phrases du vent » de `docs/maquettes/niveau.html`). Cas qui l'a révélé : NE 31 km/h à Montalivet (130° de la mer) classé « côté ».

## Retours du propriétaire à traiter (soir du 11 octobre)
- **Nuages : transitions entre deux couvertures trop brutales** (on dirait des segments séparés) et couvertures peu jolies. Causes probables : lissage de la couverture trop court (quelques dizaines de pixels) alors que la prévision saute d'une heure à l'autre, mélange de sprites très différents, aplats. Pistes : fondu long (2 à 3 h dans le déroulé, un jour dans la semaine) avec courbe en S, limiter la vitesse de changement de couverture, fond du ciel stable, voile de couvert plus progressif.
- **Pluie** : le propriétaire la trouve très bien (12 octobre), ne rien changer.
- **Semaine : phrase du jour et température** : maquette avec rendu réel envoyée (https://claude.ai/artifact/Y5cp87SPMmrriFWJhvcYgu). Phrase en 3 lignes (choisi le 12 octobre) : maquette mise à jour dans `docs/maquettes/ecran-semaine.html`, deux versions de lignes à départager. Option A, offshore/onshore, déroulé sans 3e ligne, températures discrètes dans le ciel et l'eau (2e maquette du 12 octobre, à valider). Règle : aucun mot « ressenti » à l'écran, sauf dans le « i » (à retirer de `NowTideCard.kt` et `WeatherDetailScreen.kt`).
- **Refonte du niveau (priorité du propriétaire) et des réglages** : maquette `docs/maquettes/niveau.html` envoyée, à choisir (4 façons de dire son niveau, 3 structures d'accueil). Ensuite coder une page à la fois.
- **Réglages en 5 pages** (ancienne maquette) : maquette validée en principe (https://claude.ai/artifact/6X9Pg1aQLgtDyXbRMw8EtX), à coder une page à la fois ; retirer l'orientation de la plage des réglages.
- Rappel : ne pousser sur `unified` et `web` qu'avec le « go » du propriétaire.

## Semaine : codée sur la branche, à relancer en intégration continue puis à publier avec ton « go »
- Reste à vérifier sur téléphone : lisibilité de la température sur les nuages sombres, phrase du jour sur plusieurs jours, « Marée » dans « Maintenant ». Le texte du « i » du vent n'a pas changé.
- Les phrases du profil et du guide contenant « pour toi » ou « trop gros » restent à repasser.

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
- Semaine : phrase du jour choisi à la place du titre + un seul chiffre par jour (ressenti moyen des heures de jour, ni mini ni maxi) : maquette `semaine_phrase.html` envoyée, en attente de validation.
