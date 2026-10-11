# Journal des modifications

Du plus récent au plus ancien. État : **en ligne** = site et APK ; **branche** = poussé sur la branche de travail `claude/new-session-pume8w` seulement ; **maquette** = maquette validée ou en attente, pas de code.

## 11 octobre 2026
- **Nuages : vrais sprites détourés posés par `CloudPlan`** (atlas net 4096 px, atlas flou pour la brume, voile de couvert, couleurs sous la pluie), remplace les images par paliers. Photos agrandies par Real-ESRGAN (réseau réimplémenté en numpy), voir `scripts/nuages/`. État : **en ligne** (publié le 11 octobre sur `unified` et `web`, builds verts, déploiement du site et APK). Rendu réel à juger sur téléphone.
- Les nuages en images par paliers (fondu additif) sont **en ligne** (builds verts) mais jugés insuffisants : remplacés ci-dessus.
- **Nuages en images prêtes, fondues en additionnant** : 14 images (5 paliers x 2 thèmes + variantes pluie), `BlendMode.Plus`, fond du ciel en deux calques. Remplace la grosse texture. Un oubli (`LocalSkyImages`) avait fait échouer la première publication, corrigé. État : **publié sur `unified` et `web`, en attente du résultat de la compilation et du déploiement.**
- **Phrases sans « pour toi »** : « Taille limite », « Taille un peu juste », « Gros jour, à jauger sur place », « Creux et puissant ». État : **branche**. Les badges « Trop petit » et « Trop gros » ne sont pas encore renommés (à décider).
- **« Nuages XX % » dans la ligne « Maintenant »** : état **en ligne** (si le déploiement du jour est passé).
- **Pluie plus visible** (opacité qui monte plus vite avec l'intensité) et **bouton « Thème »** dans la barre du bas : état **en ligne**.
- **Nuages couvrent toute la hauteur et passent derrière l'eau** (plus de trait net avant la courbe) : **en ligne**, puis remplacé par les images prêtes ci-dessus.
- **Marée sous la jauge**, **colonne de vent de l'heure choisie plus basse et soulignée**, **même flèche de vent agrandie partout** : **en ligne**.
- **Échelle de houle fixe** (2 m resserrée au-delà), **vent heure par heure**, **point « Now »**, **texte de houle lisible sur Android** : **en ligne**.
- **Maquettes faites, non codées** : choix du niveau par exemples (12 situations, 5 réponses), banc d'essai du vent (cohérence couleur / note / étoiles / phrase), compte rendu de toutes les phrases de l'appli, brief pour une autre IA sur les nuages.

## Avant le 11 octobre
Voir l'historique Git. Résumé : déroulé du jour refait (fenêtre lever-coucher), fond météo en images, semaine sans icônes météo, code couleur du vent à seuils fixes, océan opaque sous la courbe, graphiques à 43 %, eau à 35 % réglables.
