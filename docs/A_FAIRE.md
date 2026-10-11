# À faire

## Bug à traiter en priorité (signalé le 11 octobre, 3 captures de Montalivet)
La phrase grise en haut du déroulé ne correspond pas au vent affiché :
- 17 h, vent O (ouest, donc de mer) à 3 km/h : « Vent de côté qui gêne ».
- 13 h, SSE 8 km/h : « Vent de côté qui gêne ».
- 12 h, SSE 8 km/h aussi : « Bonne taille, vent léger ».
Cause très probable trouvée (à confirmer par test) : la note et la phrase sont calculées avec la « mémoire du vent » (`hoursWithWindMemory` / `windMemoryAdjusted`, DailyTimelineCard ligne 110) : le vent des heures précédentes (et de la veille) peut remplacer le vent réel de l'heure, alors que l'écran affiche le vent réel. Pistes à vérifier : (1) les rafales comptent à moitié dans le vent de la note mais ne sont pas affichées, donc un vent moyen faible peut donner une phrase de vent ; (2) la phrase de vent n'apparaît que si le vent est le pire facteur sous 0,85, ce qui ne devrait pas arriver à 3 km/h : vérifier que la phrase correspond bien à l'heure sélectionnée (décalage possible entre la colonne de vent, l'heure sélectionnée et l'heure notée) ; (3) la catégorie de vent (de mer, de côté, de terre) doit venir de l'orientation réelle de la plage partout. À faire avec la maquette du banc d'essai du vent : afficher les rafales et la raison exacte, et ajouter un test sur ces trois cas.

## En attente de validation du propriétaire
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
