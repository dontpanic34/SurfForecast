# Audit des réglages actuels (12 octobre 2026)

Fait sur la version en ligne (captures avec données simulées) et sur le code (`PreferencesDialog.kt`, `SurferProfileUi.kt`, `ProfileWizard.kt`, `QuiverUi.kt`). Gravité : **A** = gêne vraiment, **B** = à corriger, **C** = détail.

## Le profil et le niveau (le plus important)
1. **A · Le niveau se règle à trois endroits** : à l'ouverture de l'appli (Débutant / Intermédiaire / Confirmé / Expert), dans « Mon profil », et dans « Mon matériel » (« Niveau (pour le volume) », six crans : Débutant, Débutant / Interm., Intermédiaire, Interm. / Confirmé, Confirmé, Expert). Ce dernier est stocké à part : les deux peuvent se contredire.
2. **A · Quatre cases pour résumer un surfeur** : un « Intermédiaire » surfe de 0,8 à 1,7 m dans le résumé, mais rien ne dit ce qu'il y a derrière. On choisit un mot, on ne voit pas ce que ça change (la liste de huit journées est tout en bas, après un long texte).
3. **A · Treize réglages derrière « Personnaliser »** : période minimum, onshore toléré jusqu'à, offshore, rafales gênantes à partir de, compter les rafales, clapot gênant à partir de, etc. Jargon, chiffres sans repère. Décision déjà prise : rafales et clapot automatiques, trois réglages lisibles.
4. **B · Les exemples sont des journées de Montalivet avec une étiquette** (« Trop petit », « Parfait », « Trop gros ») : on ne répond pas, on lit. Et les étiquettes « Trop petit » / « Trop gros » contredisent la règle « jamais décourageant ».
5. **B · La page mélange deux sujets** : Profil, puis « Mon matériel » (âge, taille, poids, forme physique, volume, planches) sur la même page défilante.
6. **B · Matériel** : trois cases vides (Âge, Taille, Poids) sans unité visible, « Forme physique » (Excellente, Bonne, Moyenne, Faible) sans explication, et un tableau « Correspondances niveau x poids » brut, trop dense pour un téléphone.
7. **B · Des « pour toi » partout** : « Des prévisions calculées pour toi », « Les scores pour toi ». Règle : pas de « pour toi ».
8. **C · Texte d'aide gris clair sur fond bleu-gris** (« Dis-nous quelle vague tu cherches… ») : peu contrasté.

## La structure générale
9. **A · Trois noms pour la même chose** : « Réglages » (barre du bas), « Paramètres » (titre de la fenêtre), puis « Prévisions » désigne à la fois l'onglet principal, une page de réglages (modèles) et « Comprendre les prévisions ».
10. **A · Sept lignes dans le menu** de natures différentes : Mon profil, Affichage, Prévisions (modèles), Comprendre les prévisions (aide), Journal de bord (c'est une sauvegarde), Aide, Appli. Aide et Comprendre sont deux entrées d'aide ; « Journal de bord » n'est pas un réglage.
11. **B · Tout est dans une fenêtre qui défile** (on voit environ 60 % de l'écran), avec des drapeaux de langue dans l'en-tête (pris sur la place du titre) et une croix. La langue n'est pas dans « Affichage », alors que le menu dit « Langue, taille, thème… ».
12. **B · Deux façons de valider** : tout s'enregistre tout seul, sauf la page « Prévisions » qui finit par un gros bouton « Appliquer et fermer ».
13. **C · Titres numérotés** (« 1. Unité du vent », « 2. Encarts… », « 3. Combos de prévision ») : la numérotation traverse deux pages et ne veut rien dire.
14. **C · Icône 🎛️ pour « Affichage »** : illisible (on dirait une plaque de cuisson).

## Affichage
15. **A · La page est longue (cinq écrans) et fourre-tout** : taille, thème, trois curseurs de graphiques en %, unité du vent, encarts, icônes par jour, étoiles des jours, indicateurs de vent.
16. **A · Les encarts sont listés deux fois** : une fois avec interrupteurs, une seconde fois dans « Ordre des encarts » avec des boutons ▲ ▼ (il faut remonter chaque encart cran par cran).
17. **B · « Icônes par jour (météo et vent) »** : les icônes météo n'existent plus dans la semaine ; le réglage ne règle plus que les créneaux de vent. Libellé faux.
18. **B · Les curseurs de graphiques** (« Place du graphique, semaine 43 % », « Hauteur de l'échelle (où arrive 2 m) 40 % ») sont des réglages de développeur.
19. **B · Doublons avec l'écran principal** : « Thème » existe aussi dans la barre du bas, « Taille de l'affichage » dans le bouton « Aa ».
20. **B · « Étoiles des jours : Exigeant / Normal / Indulgent »** : un second réglage d'exigence, à côté du niveau du profil.

## Prévisions (modèles) et orientation
21. **A · Quatre menus de modèles** (vent et vagues, court et long terme : AROME, ARPEGE, ECMWF, MF-WAM, ECMWF Wave), chacun avec un « i » : du jargon d'expert ; c'est automatique par défaut dans la maquette.
22. **A · L'orientation de la plage** (Google, Google Maps, boussole N à NO, ± 5°) est le plus long bloc de la page. Décision déjà prise : ce n'est plus un réglage, chaque spot a la sienne.
23. **C · « Voir les logs d'actualisation »** : outil de développeur, pas un réglage.

## Premier lancement
24. **B · L'ouverture demande trop** : taille de l'affichage, niveau et liste de journées sur le même écran, puis une visite guidée de quatre pages de texte (« Une minute suffit… »).
