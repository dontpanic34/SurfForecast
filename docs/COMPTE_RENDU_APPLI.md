# Compte-rendu : comment fonctionne l'appli, ce qui ne va pas, ce qu'on devrait faire

Établi le 13 octobre 2026, après les vidéos sur la houle. Aucune publication faite.

## En deux phrases

L'appli est solide sur l'affichage, les phrases et le vent ressenti, et les bugs trouvés cette semaine sont corrigés (en attente de publication). Les erreurs qui restent viennent surtout de la façon dont on fabrique la houle : la hauteur, la période et l'énergie ne viennent pas toujours du même modèle, et les prévisions lointaines sont présentées comme plus sûres qu'elles ne sont.

## Comment l'appli fabrique une note

- **Houle** : la hauteur vient du modèle de vagues (MF-WAM aujourd'hui et demain, puis ECMWF Wave), la période vient d'un autre modèle (GFS Wave, comme Windguru).
- **Énergie** : hauteur² × période² × 1,962 (kJ), réduite si la houle arrive de travers. C'est le chiffre central de la note.
- **Vent** : moyenne ECMWF + GFS, ressenti (moyen + 35 % de l'écart avec les rafales), classé offshore, side-off, side-on, onshore.
- **Note d'une heure** : l'énergie comparée au profil du surfeur, multipliée par le vent, les rafales, le clapot, la période courte et la direction. Puis les plafonds (« Parfait » exige un vent propre, « Très bon » pas de « dégrade »).
- **Étoiles du jour** : 60 % des meilleures heures, 40 % de la moyenne.
- **Météo** : Météo-France (AROME deux jours, ARPEGE environ quatre jours), puis ECMWF.

## Déjà corrigé (en attente de ton « go » pour publier)

- Écran vide quand on choisit « ECMWF Wave » dans les réglages : le nom envoyé au service était invalide (`ecmwf_wave` au lieu de `ecmwf_wam`).
- Libellé « Correct à Bon » alors que ça baissait : il suit maintenant l'heure d'avant (« Bon à Correct »).
- « Parfait » avec un vent de sud-ouest et deux étoiles : il exige un vent propre (moins de 5 km/h ou offshore).
- « Très bon » à côté de « dégrade la vague » : le titre donne le ton, la phrase suit.
- Encart « Actuellement » : quitte la nuit une heure avant le lever, garde le résumé de nuit jusqu'à deux heures après.
- Titre de la semaine : « ça va se caler » quand la journée s'améliore ; marée en trait fin avec heure et coef.

## Ce qui ne va pas encore, par ordre d'importance


### 1. La hauteur et la période viennent de deux modèles différents
- **Constat** : Aujourd'hui à 14 h sur Montalivet : MF-WAM donne 1,28 m à 9,1 s (266 kJ), GFS Wave 1,08 m à 11,15 s (285 kJ). L'appli prend 1,28 m (MF-WAM) avec 11,15 s (GFS) : **400 kJ, soit 40 à 50 % de plus que chaque modèle seul**. La période compte au carré : un écart de 2 s change l'énergie de 50 %.
- **À faire** : Prendre hauteur et période du **même** modèle, ou garder GFS Wave pour les deux. Garder la période GFS comme affichage de référence seulement si on accepte l'écart. À décider avec toi.

### 2. La houle secondaire est ignorée dans l'énergie et la note
- **Constat** : Elle est affichée mais ne compte pas. Or l'énergie est la **somme de tous les trains de houle** (vidéo 10).
- **À faire** : Ajouter l'énergie de la houle secondaire quand sa direction est compatible. Effet sur les journées à deux houles.

### 3. Les prévisions lointaines paraissent aussi sûres que celles du jour
- **Constat** : Une houle de 12 s arrive 15 à 20 h plus tôt ou plus tard selon la période réellement formée (vidéo 8). À J+4 et au-delà, un verdict et des étoiles précis sont trompeurs.
- **À faire** : Marquer les jours à partir de J+4 comme « tendance » (étoiles en pointillé, verdict adouci) et donner une plage d'heures pour l'arrivée d'une grosse houle.

### 4. Pas de vérification avec le réel
- **Constat** : Aucune comparaison avec une bouée ou une station. On ne sait pas de combien l'appli se trompe, spot par spot.
- **À faire** : Afficher la bouée la plus proche à côté de la prévision (chiffre mesuré). Piste à étudier : bouées Candhis ; accès à vérifier avant de promettre.

### 5. Raccord entre modèles à J+2
- **Constat** : MF-WAM aujourd'hui et demain, ECMWF ensuite. Sur Montalivet, à J+2 à 14 h : MF-WAM 1,42 m, ECMWF 1,56 m, GFS Wave 1,06 m. L'écart de 0,5 m entre modèles est aussi grand que la variation d'une journée à l'autre.
- **À faire** : Comparer les jours entre eux avec un seul modèle, ou caler ECMWF sur MF-WAM au raccord. À décider.

### 6. Aucun modèle ne voit la côte ni le fond
- **Constat** : Les modèles ont des pixels de 8 à 14 km : bancs de sable, baïnes, pente et profondeur du spot sont invisibles (vidéos 2, 4, 6).
- **À faire** : Un ajustement par spot appris du journal (« la prévision disait X, j'ai ressenti Y »). C'est de l'apprentissage, pas du calcul : à cadrer.

### 7. Météo : les modèles ne sont pas d'accord et l'appli n'en montre qu'un
- **Constat** : Aujourd'hui, les 11 modèles voient du ciel couvert mais placent la bruine à des heures différentes (matin pour ARPEGE et ECMWF, 16 h pour AROME, toute la journée pour ICON, UKMO, GEM). L'appli affiche AROME seul.
- **À faire** : Montrer l'accord entre modèles (« pluie incertaine ») et laisser choisir le modèle (maquette « Modèles de prévision » prête).

### 8. Vent : un seul choix pour tous les jours
- **Constat** : Moyenne ECMWF + GFS (pixels de 25 km) partout, alors que les modèles fins voient mieux la côte (vidéo 3). AROME HD est enregistré en coulisses pour comparer.
- **À faire** : Décider avec les mesures : comparer une semaine AROME HD, ECMWF + GFS et une station réelle (déjà noté dans `A_FAIRE.md`).

### 9. Période courte pénalisée deux fois
- **Constat** : L'énergie baisse déjà avec la période, et un facteur de période réduit encore la note sous le minimum du profil.
- **À faire** : À vérifier sur des journées connues avant de toucher : c'est peut-être voulu (houle courte = vague moins bien formée).

### 10. La hauteur affichée est une moyenne
- **Constat** : Hauteur significative : les séries montent d'environ un tiers de plus (vidéo 5). Pas faux, mais on ne le dit nulle part.
- **À faire** : Une phrase dans le « Comprendre » de la houle (contenu prêt).

## Plan proposé

- **Étape 1, tout de suite** : publier ce qui est prêt (écran vide, libellés, « Parfait », titres, marée, nuit). Sur ton « go ».
- **Étape 2** : hauteur et période du même modèle, et houle secondaire dans l'énergie. Le plus gros gain de justesse. Décisions à prendre avec toi (problème 1 et 2).
- **Étape 3** : jours lointains en « tendance » et plage d'heures pour l'arrivée des grosses houles (problème 3).
- **Étape 4** : échelle des notes (25 / 45 / 62 / 80 / 94, zone ± 3) et choix des modèles de prévision, une fois la maquette validée.
- **Étape 5** : section « Comprendre » dans l'appli (contenu et maquette prêts).
- **Étape 6** : bouée à côté de la prévision, puis ajustement par spot (problèmes 4 et 6), à étudier.

## Ce que je ne sais pas encore

- Si le mélange GFS/MF-WAM est voulu pour coller à Windguru : le code dit que oui, mais le chiffre d'énergie en sort 40 à 50 % plus haut qu'un seul modèle. À trancher avec toi.
- Quels réglages d'énergie tu as pris pour ton profil : les seuils de note sont calibrés sur ce chiffre ; en le changeant, il faudra recaler les seuils.
- Les chiffres de ce compte-rendu sont des prévisions du 13 octobre pour Montalivet : un seul instant, pas une statistique.
