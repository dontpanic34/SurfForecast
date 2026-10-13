# Connaissances : houle, vagues, prévisions

Notes prises à partir de vidéos que le propriétaire a envoyées, pour la future page « Comprendre la houle, les prévisions, la météo ». Ce sont des résumés, pas les textes complets. Rien de personnel ici : le dépôt est public.

## 1. La période change la taille (énergie)
- À même hauteur, plus la période est longue, plus la houle est puissante : l'énergie dépend du **carré de la période**.
- Exemple : 10 s contre 12 s, c'est 100 contre 144 : **+44 %** d'énergie, pas +20 %.
- Règle à retenir : environ **+20 % d'énergie par seconde de période en plus** (approximation).
- 1 m à 17 s contient autant d'eau déplacée qu'environ 4,5 m à 8 s (à surface égale).
- Dans l'appli : l'indice d'énergie utilise déjà H² × T².

## 2. Bancs de sable et baïnes
- Le fond de sable change : la marée descendante vide l'eau coincée par un sillon, la baïne, qui se creuse à chaque marée et à chaque vague.
- À la fin, le sillon s'élargit tellement qu'on revient à peu près au départ, et une autre baïne se forme plus loin ou à côté.
- Pour le surf : c'est le **banc de sable** qui compte. Une marche de sable où le fond remonte vite donne un pic net. Plus l'arête est franche et le sable harmonieux, meilleure est la vague.
- Un banc ou une baïne vit quelques semaines, selon la houle et les coefficients de marée : les vagues changent à mesure qu'ils se forment et meurent.
- Les fonds rocheux et les récifs ne changent pas.
- Dans l'appli : aucun modèle ne voit les bancs ; seul le journal de sessions peut les capter.

## 3. Résolution des modèles de prévision
- Un modèle découpe la zone en pixels. Plus il y en a, plus c'est précis, et plus le calcul est lourd.
- Un modèle ne voit que les phénomènes **5 à 7 fois plus grands que son pixel**. Une grille de 25 km oublie les îles ; même 5 km ne voit pas toute la côte.
- **Modèles fins** (locaux) : meilleurs pour l'effet du trait de côte. **Modèles globaux** : meilleurs pour les grandes échelles et les **houles longues** générées loin, que les modèles locaux peuvent mal voir.
- Dans l'appli : modèles fins pour les deux premiers jours (AROME, MF-WAM), globaux au-delà (ECMWF, GFS).

## 4. Houle et vagues
- La **houle** propage l'énergie ; la **vague** la dissipe (comme des freins).
- En arrivant près de la côte, la houle est freinée, l'eau « s'empile » comme des assiettes, et finit par tomber. Trois issues : elle gonfle sans déferler, elle s'effondre, ou elle plonge vers l'avant (le tube).
- Ce qui décide : la **période** (plus elle est longue, plus c'est fort) et la **pente du fond** (plus le fond remonte vite, plus la houle est freinée brutalement).
- **Breaker index** : une vague déferle dans une profondeur d'environ **1,3 fois sa hauteur** (1 m dans 1,30 m d'eau, 2 m dans 2,60 m). Valable pour les fonds qui remontent lentement.
- Le vent offshore, le courant d'une embouchure ou d'une baïne aident au tube.

## 5. Hauteur significative
- Les prévisions donnent la **hauteur significative** : la moyenne du **tiers des plus grosses** vagues.
- Le maximum observé est plus haut : la moyenne des 10 % des plus grosses (environ 1,3 fois la hauteur significative, approximation).
- « 0,80 à 1,10 m » veut dire 0,80 m significative, avec des séries qui montent jusqu'à 1,10 m.
- Une vague isolée peut dépasser beaucoup plus.

## 6. Les séries
- Des houles de périodes presque identiques et de même direction **s'additionnent** : en phase elles se renforcent, en opposition elles s'annulent. D'où les séries.
- Plus la période est longue, plus les plus grosses vagues du lot dépassent la hauteur annoncée.

## 7. Vitesse de phase et de groupe
- La houle est le **groupe**, la vague est la **phase**. Les vagues vont plus vite que le train de houle et passent de l'arrière à l'avant (comme la chenille d'un tank).
- En eau profonde : **vitesse de phase = 2 × vitesse de groupe**. Près de la côte, ça ne tient plus.
- La vitesse du groupe vaut environ 0,78 × la période (m/s) : 14 s, environ 11 m/s. Idée pour plus tard : estimer l'heure d'arrivée d'une houle (il faudrait la position de la tempête, que les modèles ne donnent pas).

## 8. Pourquoi les vagues arrivent plus tard (ou plus tôt) que prévu
- La vitesse du groupe de houle est environ **0,75 × la période** (en m/s).
- Exemple sur 2000 km : houle de **16 s** = environ 44 h ; houle de **12 s** = environ 59 h ; soit **15 h de décalage**.
- Une dépression un peu moins grande ou moins creuse réduit le **fetch** (la distance sur laquelle le vent souffle) : la période baisse (16 s devient 12 s), la houle est plus petite et arrive plus tard.
- Donc une prévision à plusieurs jours peut se décaler d'une demi-journée ou plus, et sa taille aussi, selon la période réellement formée.
- Idée pour l'appli : au-delà de J+3, présenter l'arrivée comme une plage d'heures plutôt qu'une heure précise.

## 9. Lire une carte météo pour choisir où et quand surfer
- **Pressions** : repérer les dépressions (le vent tourne autour d'elles), puis les anticyclones. Le vent est d'autant plus fort que les **isobares sont serrées** entre dépression et anticyclone. Source citée : prévisions de pression sur 16 jours, Atlantique Nord, GFS 27 km.
- **Carte de la période** plutôt que celle des hauteurs : la période dit mieux la houle (son énergie, qui se dissipe en route).
- Données d'un point de carte (exemple) : vague 2,6 m à 12 s d'ONO ; vent 0,3 m à 7 s (clapot) ; houle 2 de 2,5 m à 12 s d'ONO ; énergie 1796 kJ.
- Ensuite le **vent**, et seulement après un **site de prévision** pour le spot choisi.
- **Bouées** (application « La Bouée ») : mesures réelles de la station la plus proche, à comparer aux prévisions (exemple : bouée 0,3 à 0,5 m, 275° ; prévision 0,5 m, 152°).
- **Spectre fréquence-direction** : montre si la houle est unidirectionnelle et nette ou mélangée.
- Idée pour l'appli : afficher la bouée la plus proche à côté de la prévision, pour vérifier la prévision avec le réel.

## À venir
Le propriétaire enverra d'autres vidéos sur la houle. Ensuite : pages « Comprendre la houle », « Comprendre les prévisions », « Comprendre la météo ».
