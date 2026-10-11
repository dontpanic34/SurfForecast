# Décisions et règles de travail

## Comment travailler avec le propriétaire
- **Maquette d'abord** (HTML), puis validation du propriétaire, puis code. On ne code pas avant le « go » sur la maquette.
- **Rien n'est mis en ligne sans « go » explicite.** « En ligne » veut dire le site ET l'appli (APK).
- Pas de compilation en local possible : on compile et teste par l'intégration continue (`workflow_dispatch` de `build-kmp.yml` sur une branche de travail ; le déploiement ne se fait que pour les branches `web` et `unified`).
- Une publication = `unified` (fusion de la branche de travail) puis `web` (avance rapide). Les pushs sur ces branches compilent et testent avant de déployer.
- Parler simplement, en français, sans jargon.

## Règles de produit déjà tranchées
- **Pluie** : on garde le dessin actuel, seulement plus visible, avec des fondus d'apparition et de disparition. Pas de gouttes ni de traînées.
- **Vent** : toujours heure par heure, aucune option de pas. Même flèche (pointe fine du déroulé, agrandie) dans le déroulé et la semaine. L'heure choisie descend un peu et est soulignée.
- **Échelle de houle** : fixe (2 m à une hauteur fixe, resserrée au-delà), jamais ajustée à la houle du jour ; les graduations s'arrêtent à la houle atteinte.
- **Marée** : une ligne sous la jauge avec le coefficient et les deux marées qui correspondent le mieux aux heures d'ensoleillement ; repères sur la ligne des heures.
- **Thème** : un seul bouton « Thème » dans la barre du bas, l'icône montre le thème vers lequel on bascule.
- **Phrases** : jamais décourageantes, jamais de « trop gros / trop petit / trop creux pour toi ». Les phrases ne disent pas une taille absolue (les seuils dépendent du profil). Nouvelles phrases : « Taille limite », « Taille un peu juste », « Gros jour, à jauger sur place », « Creux et puissant ».
- **Profil** : plus de questions sur la taille, la période, le clapot ou le vent. À la place, des exemples concrets (4 tailles de vague x 3 vents : de terre, de côté, de mer) avec 5 réponses : Je reste à terre, J'y vais quand même, Parfait, Je me challenge, Trop gros pour moi. Peu de couleurs.
- **Nuages** : rendu réaliste, couverture continue le long de l'axe, soleil derrière, couvrent toute la hauteur et passent derrière l'eau (opaque). Ne pas re-passer 3 jours dessus : si l'approche actuelle échoue, passer au plan B (images de ciel générées, fondues).

## Historique des nuages (ce qui a échoué et pourquoi)
1. Quatre photos étirées : échelle différente en largeur et hauteur, fondus brutaux.
2. Texture unique en bandes avec filtre de couleur par bande : étirée, en marches.
3. Colonnes d'un pixel de texture : pixelisé (texture trop petite agrandie), stries (bords de texture), lent (500 dessins).
4. 12 calques plein écran fondus par masque : bandes plus denses aux transitions (l'opacité de deux calques superposés se cumule), lent.
5. Actuel : 14 images prêtes (5 paliers de couverture x 2 thèmes, plus 2 variantes pluie), fondues en **additionnant** (`BlendMode.Plus`, poids de somme 1). Brief pour demander conseil à une autre IA : voir `JOURNAL.md`.
