# Décisions et règles de travail

## Comment travailler avec le propriétaire
- **Maquette d'abord** (HTML), puis validation du propriétaire, puis code. On ne code pas avant le « go » sur la maquette.
- **Rien n'est mis en ligne sans « go » explicite.** « En ligne » veut dire le site ET l'appli (APK).
- Pas de compilation en local possible : on compile et teste par l'intégration continue (`workflow_dispatch` de `build-kmp.yml` sur une branche de travail ; le déploiement ne se fait que pour les branches `web` et `unified`).
- Une publication = `unified` (fusion de la branche de travail) puis `web` (avance rapide). Les pushs sur ces branches compilent et testent avant de déployer.
- Parler simplement, en français, sans jargon.
- **En cas de doute, poser la question** au propriétaire avant de publier ou de trancher.
- **Le ressenti avant le prévu** (règle générale, 11 octobre 2026) : l'appli montre et note ce qu'on sent sur place, pas la valeur brute de la prévision. Vaut pour le vent (déjà fait : vent ressenti), les nuages, la température, la houle, etc. À chaque chiffre affiché, se demander « qu'est-ce qu'on ressent vraiment ? » et, si c'est différent du prévu, afficher le ressenti (le prévu au plus en détail). Les cas restants sont dans `A_FAIRE.md`.

## Règles de produit déjà tranchées
- **Pluie** : on garde le dessin actuel, seulement plus visible, avec des fondus d'apparition et de disparition. Pas de gouttes ni de traînées.
- **Vent** : toujours heure par heure, aucune option de pas. Même flèche (pointe fine du déroulé, agrandie) dans le déroulé et la semaine. L'heure choisie descend un peu et est soulignée.
- **Échelle de houle** : fixe (2 m à une hauteur fixe, resserrée au-delà), jamais ajustée à la houle du jour ; les graduations s'arrêtent à la houle atteinte.
- **Marée** : une ligne sous la jauge avec le coefficient et les deux marées qui correspondent le mieux aux heures d'ensoleillement ; repères sur la ligne des heures.
- **Thème** : un seul bouton « Thème » dans la barre du bas, l'icône montre le thème vers lequel on bascule.
- **Phrases** : jamais décourageantes, jamais de « trop gros / trop petit / trop creux pour toi ». Les phrases ne disent pas une taille absolue (les seuils dépendent du profil). Nouvelles phrases : « Taille limite », « Taille un peu juste », « Gros jour, à jauger sur place », « Creux et puissant ».
- **Profil** : plus de questions sur la taille, la période, le clapot ou le vent. À la place, des exemples concrets (4 tailles de vague x 3 vents : de terre, de côté, de mer) avec 5 réponses : Je reste à terre, J'y vais quand même, Parfait, Je me challenge, Trop gros pour moi. Peu de couleurs.
- **Nuages** : rendu réaliste, couverture continue le long de l'axe, soleil derrière, couvrent toute la hauteur et passent derrière l'eau (opaque). Ne pas re-passer 3 jours dessus : si l'approche actuelle échoue, passer au plan B (images de ciel générées, fondues).

## Règle du vent (décidée le 11 octobre 2026)
- **Un seul chiffre « ressenti »** pour le vent, comme une température ressentie : vent moyen + **35 %** de l'écart avec les rafales. Exemple : moyen 8 km/h, rafales 22 km/h donnent 13 km/h. Source du choix : pas de règle publiée pour le surf ; les rafales dépassent le vent moyen de 15 à 50 % sur l'eau (rapport 1,25 en moyenne), et la méthode du « vent moyen équivalent » (gust / 1,85) correspond à environ 28 % sur un cas très rafaleux. 35 % est une estimation à ajuster sur des jours connus. L'appli est faite pour le surf, pas pour le kitesurf.
- Ce chiffre sert à la couleur de la flèche, à la phrase et à la note. Il est affiché sous la direction dans la colonne des heures ; le détail (moyen et rafales) apparaît seulement pour l'heure choisie, dans l'encart du bas.
- **Cette règle et son calcul doivent figurer dans le « i » du vent** (voir `A_FAIRE.md`).
- Une règle unique pour la couleur et la phrase : facteur de vent 0,9 et plus = propre (jaune), de 0,6 à 0,9 = gêne (orange), sous 0,6 = fort (rouge), avec l'orientation réelle de la plage. Nouvelles courbes proposées (à valider) : vent de côté propre jusqu'à environ 12 km/h ressentis, vent de mer légèrement adouci.
- La mémoire du vent (le vent des heures d'avant pèse à 75 %) reste pour la note, mais quand elle change la phrase, la phrase le dit (formulation à choisir parmi A, B, C, voir `A_FAIRE.md`).

## Historique des nuages (ce qui a échoué et pourquoi)
1. Quatre photos étirées : échelle différente en largeur et hauteur, fondus brutaux.
2. Texture unique en bandes avec filtre de couleur par bande : étirée, en marches.
3. Colonnes d'un pixel de texture : pixelisé (texture trop petite agrandie), stries (bords de texture), lent (500 dessins).
4. 12 calques plein écran fondus par masque : bandes plus denses aux transitions (l'opacité de deux calques superposés se cumule), lent.
5. Images prêtes par paliers (publiée, jugée insuffisante par l'autre IA et par le propriétaire) : 14 images prêtes (5 paliers de couverture x 2 thèmes, plus 2 variantes pluie), fondues en **additionnant** (`BlendMode.Plus`, poids de somme 1). Brief pour demander conseil à une autre IA : voir `JOURNAL.md`.
6. **Actuel (à vérifier sur appareil)** : vrais nuages détourés (atlas de 12 sprites issus de 2 photos Gemini agrandies x4 par Real-ESRGAN puis ramenées à x2), posés un à un par `CloudPlan` (couverture ressentie en 4 états, candidats à graine fixe, visibilité progressive, brume floue, voile de couvert, couleurs sous la pluie), dessinés une seule fois dans une image en cache à la taille réelle. Interdits : seuils par colonne, calques plein écran, texture étirée, ColorFilter par bande, fondus de deux images, `BlendMode.Plus`. Scripts de fabrication de l'atlas : `scripts/nuages/`.
