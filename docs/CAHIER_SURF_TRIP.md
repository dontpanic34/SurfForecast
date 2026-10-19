# Cahier du mode surf trip

Brouillon écrit avec le propriétaire (état : à développer ensemble, rien n'est codé). Chaque idée est marquée **décidé**, **à valider** ou **idée**.

## But
Un mode de l'appli pour quand on voyage : aider à décider où aller, quand bouger et quand attendre, et à s'organiser en van. Il ne charge pas l'écran normal : on l'active, ou il propose de lui-même de s'ouvrir.

## 1. Où surfer ? (le cœur du mode)
- **Décidé (19 octobre 2026)** : la tendance sur 7 jours est présentée **jour par jour** (option B de la maquette `ou-surfer-tendance.html`) : une ligne par jour avec les étoiles ici, et si ça vaut le déplacement, « → zone » avec ses étoiles ; un jour touché ouvre le détail heure par heure.
- **Décidé** : rayon au choix, 50 / 100 / 300 / 500 km. Fond de carte libre de droit (NASA Blue Marble), nos données (houle, vent, spots) dessus ; OpenStreetMap seulement plus tard, si besoin du détail de la côte.
- **Décidé** : l'entrée apparaît d'elle-même (encart sous la vue semaine, pastille sous le nom du spot) seulement quand la semaine est mauvaise ici ; la ligne « Où surfer ? » dans les réglages est toujours là.
- **À valider** : règle d'apparition. Proposition : au moins 3 jours sur 7 à moins de 2 étoiles.
- **À valider** : choix de l'entrée dans le mode surf trip (A réglages, B choix du spot, C pastille) : remplacé par la règle ci-dessus.

## 2. La décision : monter, attendre ou changer de spot
Une phrase claire en tête du mode, calculée à partir des mêmes notes que l'appli :
- « Reste ici, ça va » ; « Attends jeudi : ça se cale » ; « Bouge vers le sud : Mundaka vendredi, 5 étoiles » ; « Rien de bon dans 300 km, profite pour te reposer ».
- **Idée** : le coût du déplacement dans la phrase (distance, durée de route estimée).
- **Idée** : « fenêtre » : la plus longue période consécutive de bonnes conditions dans le rayon.

## 3. Prévisions et météo du séjour
- **Idée** : météo générale (pluie, température, vent, soleil) sur les jours du trip, pour décider d'une nuit, d'un séchage de combi.
- **Idée** : marées du jour sur le spot choisi, lever et coucher du soleil (déjà dans l'appli).
- **Idée** : alerte de vent fort ou de tempête (lien avec la vigilance Météo-France, dans `A_FAIRE.md`).

## 4. Le van
Sources possibles : OpenStreetMap (libre, sans clé, via l'API Overpass), avec ses limites : une donnée peut être vieille ou fausse, donc on affiche « selon OpenStreetMap ».
- **Idée** : douches, toilettes, eau potable, vidange (`amenity=shower`, `toilets`, `drinking_water`, `sanitary_dump_station`).
- **Idée** : aires de camping-car et parkings (`tourism=caravan_site`, `amenity=parking` avec `motorhome`). Ne montrer que ce qui est autorisé : le stationnement de nuit est interdit à beaucoup d'endroits (en France, selon les communes) ; un avertissement, pas de promesse.
- **Idée** : « se garer à l'abri du vent » : une aide par la direction du vent du jour (côté de la dune ou de la forêt à choisir). Approximatif : l'appli ne connaît pas le terrain, donc c'est un conseil, pas un calcul.
- **Idée** : spots proches de chaque aire, distance à pied, vue webcam.

## 5. Hors réseau
- **Idée** : garder en mémoire les dernières prévisions et les points d'eau et de stationnement du coin, pour les zones sans réseau (déjà : bandeau « Pas de réseau » avec l'heure des dernières prévisions).

## 6. Journal de trip
- **Idée** : les sessions du trip regroupées (étape, dates, spots), lien avec le journal existant.

## Questions pour la suite
1. Le mode s'active comment : un interrupteur dans les réglages, ou automatiquement quand on est loin de son spot habituel ?
2. Quels besoins du van avant les autres : douche, toilettes, stationnement, abri du vent ?
3. Faut-il enregistrer ses étapes (itinéraire) ou seulement conseiller ?
4. Les conseils de van pour la France seule d'abord, ou aussi l'Espagne, le Portugal et le Maroc ?
