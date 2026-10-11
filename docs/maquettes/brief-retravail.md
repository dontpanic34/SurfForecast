# Surf Log : ce qui reste à retravailler, et toutes les phrases du déroulé

Document à donner à une autre IA pour réfléchir aux tâches. Rédigé le 10/10, d'après l'état du code (branches `web` pour le site surflog.fr et `unified` pour l'appli Android).

## 1. Contexte en bref
- Appli Kotlin Multiplatform (Compose), site web (wasm) + Android. Prévisions de surf (Montalivet, Lacanau, Capbreton...).
- Écran principal : « Prévisions de la semaine » (étoiles, 3 segments de couleur par jour, vent coloré, courbe de houle sur fond météo), « Déroulé de la journée » (heure, libellé, phrase, jauge à 5 segments, marées, vent, courbe de houle, bande jour/nuit), cartes Houle, Vent, Maintenant, etc.
- Méthode de travail : maquette d'abord (HTML), validation, puis code. Rien n'est mis en ligne sans « go ». « En ligne » veut dire site ET appli.
- Maquettes de référence (privées) : réglages https://claude.ai/artifact/6X9Pg1aQLgtDyXbRMw8EtX , couvertures nuageuses https://claude.ai/artifact/2CuNStCYcrYZcrSKU9UoQk , écran clair https://claude.ai/artifact/WhTz5PUEqUZKEVaBs1yNyr

## 2. Déjà en ligne (à valider sur téléphone)
- Déroulé du jour refait : fenêtre lever-0,5 h → coucher+0,5 h, vent en couleur (pastille « Vent 2 h / Vent auto / Vent 1 h »), marées et coefficient, courbe de houle prolongée d'un bord à l'autre, bande jour/nuit, soleil qui suit l'heure.
- Fond météo en images (nuages, soleil, pluie, neige), océan opaque sous la courbe de houle avec liseret blanc fin (semaine) et trait coloré fin (jour).
- Trois ambiances de nuages : peu nuageux (30 %, nuages blancs, éclaircies), très nuageux (70 %, gris), couvert (100 %, sombre et bas, sans soleil). Ciel bleuté en thème clair. Pluie visible en thème clair. Image d'eau de pluie fondue selon l'intensité + anneaux animés dans l'eau.
- Thème clair en bleu-gris (fond de l'écran et cartes). Thème sombre inchangé.
- Réglages dans Affichage : place du graphique semaine (43 %), du jour (43 %), de l'océan (35 %).
- Texte de la houle (taille, période) en blanc avec contour.

## 3. À retravailler / à décider (par ordre de priorité)
1. **Note et couleur du vent incohérentes (#12).** Constat : vent SSO/SO à 11-14 km/h en orange ou rouge alors que le libellé dit « Très bon, bonne taille, vent propre ». Cause : deux modèles du vent.
   - Couleur : seuils fixes par catégorie (offshore / travers / onshore) avec une liste figée de directions (plage supposée plein ouest, donc SSO est classé onshore), sans la taille de la vague ni le profil. Calme ≤ 6 km/h jaune ; offshore (E, ENE, ESE, SE, NE) jaune ≤ 20, orange ≤ 30, rouge > 30 ; travers (N, S, NNE, SSE) jaune ≤ 10, orange ≤ 20, rouge > 20 ; onshore (le reste) orange ≤ 15, rouge > 15.
   - Note (SurfScoring) : catégorie avec l'orientation réelle de la plage (écart ≤ 45° onshore, ≥ 135° offshore, sinon travers), courbes continues, tolérance au vent du profil, vent divisé par la taille de la vague (jusqu'à 1,6), rafales pondérées à 50 %. Le libellé « vent propre » vient du facteur vent de la note.
   - Proposition en maquette : une seule règle. La couleur du vent = le facteur vent de la note (jaune ≥ 0,9 ; orange 0,6 à 0,9 ; rouge < 0,6). À valider : les valeurs, et l'effet du profil et de la taille de vague sur les couleurs.
2. **Harmoniser les phrases** (voir section 4) : doublons, tournures proches (« abîme » / « gâche »), phrases qui contredisent la couleur du vent, phrases manquantes (ciel, pluie, marée).
3. **Refonte des réglages** (maquette faite, pas codée) : 5 pages courtes (Profil, Matériel, Affichage, Écran principal, Prévisions et données, Appli) + page « Comment la note est calculée ». Décisions prises : orientation de la plage supprimée comme réglage (chaque spot a la sienne) ; rafales et clapot automatiques ; 13 nombres du profil remplacés par 3 curseurs (vagues que tu aimes, tolérance au vent, vagues creuses) ; modèles de prévision cachés sous « Avancé » ; liste d'encarts avec poignée + interrupteur (plus de ▲ ▼) ; « Créneaux par jour » règle aussi le vent.
4. **« i » d'explication sur chaque encart (#7)** : calcul de la note, houle (hauteur, période, kJ), vent (tableau des seuils).
5. **Calcul des planches, des notes et du volume (#9)** : BoardVolume, SurfScoring, étoiles ; option par spot pour la houle qui ferme.
6. **Petits défauts / vérifications** :
   - Semaine : « 1,0 m 10 s » du premier jour peut passer derrière l'échelle des hauteurs (bord gauche).
   - Vérifier sur téléphone : lisibilité des nuages blancs en thème clair, discrétion des anneaux de pluie, fluidité de l'animation pluie/neige, équilibre des 43 %.
   - Vérifier que la construction de l'appli (APK) passe après les dernières poussées.
   - Hauteur du soleil selon la saison (zénith).
   - Idée : afficher le ciel dans le déroulé (« Ciel dégagé », « Pluie faible »...) : aujourd'hui l'appli n'écrit aucune phrase météo dans le déroulé.

## 4. Toutes les phrases que l'appli peut dire dans le déroulé de la journée

### 4.1 Le libellé (gros texte coloré à droite de l'heure)
Calculé depuis la note (0 à 100) et de la raison de l'heure. Dix libellés :

| Libellé | Quand | Couleur | Jauge (segments sur 5) |
|---|---|---|---|
| Mauvais | note < 20 | bordeaux | 1 |
| Médiocre | 20 à 39 | rouge | 2 |
| Correct | 40 à 54 | jaune | 3 |
| Bon | 55 à 69 | vert sombre | 4 |
| Très bon | 70 à 84 | vert vif | 5 |
| Parfait | ≥ 85 | vert vif | 5 |
| Challengeant | houle trop puissante pour le profil, sans être trop grosse | orange | 3 |
| Trop petit | énergie < 85 % du minimum du profil, ou vague < 0,4 m | orange | 3 |
| Trop gros | énergie au-delà du maximum du profil | orange | 3 |
| Trop de vent | le vent seul fait descendre le facteur sous 0,4 | rouge | 2 |

**Libellés avec tendance** : quand la note est à 4 points ou moins d'une limite (20, 40, 55, 70, 85), le libellé dit vers où ça va. Cinq combinaisons : « Mauvais à Médiocre », « Médiocre à Correct », « Correct à Bon », « Bon à Très bon », « Très bon à Parfait ». Les libellés hors qualité (Challengeant, Trop petit, Trop gros, Trop de vent) n'ont pas de tendance.

### 4.2 La phrase (texte gris à côté du libellé)
Une seule phrase par heure. Règle de priorité (la première qui s'applique) :

1. Vague < 0,4 m → **« Trop petit pour toi »**
2. Énergie > maximum du profil (profil avec plafond) → **« Trop gros pour toi »**
3. Pas trop gros, mais l'offshore rend la vague trop creuse → **« Trop creux pour toi »** (libellé Challengeant, note 30)
4. Énergie sous le minimum du profil (× 0,85) → **« Trop petit pour toi »**
5. Facteur vent < 0,4 → selon la catégorie du vent :
   - onshore : **« Onshore abîme la vague »**
   - travers : **« Sideshore abîme la vague »**
   - offshore : **« Offshore trop fort »**
6. Houle ressentie > maximum confortable (Challengeant) → selon la catégorie du vent :
   - offshore : **« Creux et puissant »**
   - onshore : **« Gros, accessible »**
   - travers : **« Gros, vent de côté »**
7. Sinon (cas normal) : on prend le facteur qui pèse le plus parmi taille, vent, rafales, clapot, période, direction de la houle.
   - Si le pire facteur est ≥ 0,85 (rien ne pèse) : **« Bonne taille, vent propre »** si le facteur vent ≥ 0,9, sinon **« Bonne taille, vent léger »**.
   - Sinon, deux niveaux (« fort » si le facteur est < 0,55, « léger » entre 0,55 et 0,85) :

| Facteur | Fort (< 0,55) | Léger (0,55 à 0,85) |
|---|---|---|
| Taille | « Petite pour toi » | « Un peu petite pour toi » |
| Vent onshore | « Onshore, gâche la vague » | « Un peu de vent de mer » |
| Vent travers | « Vent de côté fort » | « Vent de côté qui gêne » |
| Vent offshore | « Offshore trop fort » | « Offshore un peu fort » |
| Rafales | « Rafales fortes » | « Des rafales gênent » |
| Clapot | « Beaucoup de clapot » | « Un peu de clapot » |
| Période | « Houle trop courte » | « Houle un peu courte » |
| Direction de la houle | « Houle de biais » (un seul niveau) | |

Total : 29 phrases distinctes (dont 2 doublons : « Offshore trop fort » apparaît aux étapes 5 et 7 ; « Trop petit pour toi » aux étapes 1 et 4).

Points à discuter : « abîme » (étape 5) et « gâche » (étape 7) disent la même chose ; « Gros, accessible » est ambigu (c'est un cas Challengeant) ; « vent propre » / « vent léger » se contredisent avec la couleur du vent (voir tâche 1) ; aucune phrase ne parle du ciel, de la pluie, de la marée ou de l'heure (lever, coucher).

### 4.3 Les autres textes du déroulé
- **Heure** : « 14 h » (gros texte), et dans la bande horaire « 08:14 » (lever), « 19:26 » (coucher), « 10 h, 12 h, ... » (heures paires), « 14:00 » (heure choisie).
- **Titre sans heure choisie** : « Déroulé de la journée ».
- **Vent** (16 directions, en français) : N, NNE, NE, ENE, E, ESE, SE, SSE, S, SSO, SO, OSO, O, ONO, NO, NNO ; valeur dans l'unité choisie (km/h, nœuds, m/s) ; flèche et texte colorés (jaune, orange, rouge).
- **Pastille du choix du vent** : « Vent 2 h », « Vent auto », « Vent 1 h » (un toucher passe au suivant).
- **Marées** (à droite de la jauge) : « ▲ hh:mm » marée haute, « ▼ hh:mm » marée basse, coefficient avec une pastille (vert < 55, orange 55 à 80, rouge > 80).
- **Graduations de la houle** : 0,5 / 1 / 1,5 / 2 (selon l'échelle) ; **taille** « 1,0 m » et **période** « 11 s » à côté du point.
- **Soleil** : pas de texte (icône qui monte et descend selon l'heure).

### 4.4 Ce que dit la semaine (pour mémoire)
Pas de texte de libellé : étoile + note (« ★ 3,5 »), 3 segments colorés par jour, vent (direction + valeur, coloré), période sur la courbe, énergie en kJ sous chaque jour.

## 5. Questions à poser à l'IA
1. Quelle règle unique pour le vent (note + couleur) qui reste simple à expliquer dans un « i » ?
2. Quel vocabulaire commun pour les 29 phrases (ton, longueur max, pas de doublon) et quelles phrases ajouter (ciel, pluie, marée, lever/coucher) ?
3. Comment présenter les réglages du profil (niveau + 3 curseurs) sans perdre en précision ?
4. Comment estimer le volume de planche et le relier à la note ?
5. Quelles explications mettre dans chaque « i » ?
