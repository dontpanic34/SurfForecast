# À faire

Mis à jour le 13 octobre 2026 au soir. Tout le lot validé est codé et en ligne sur le site (`web`), CI verte.

## À trancher / valider par le propriétaire
- **Flèche 3 et icône de l'eau** : à juger à l'écran (l'icône de l'eau a été redessinée : thermomètre plein sur deux vagues).
- **Vent** : moyenne ECMWF + GFS en ligne ; l'AROME HD est noté dans la source de chaque heure du journal des prévisions pour comparer pendant une semaine, puis décider de l'abandonner ou non.
- **Coefficients de fréquence du volume** (+5 %, +10 %, +15 %) à valider.
- **Icônes B** de la semaine (le chiffre 1/2/3 règle aussi les segments) : à juger à l'usage.
- **Accueil en trois cartes et visite guidée courte** : à juger à l'usage.
- **Textes de l'échelle des niveaux** : à relire.
- **Liste des phrases** (`docs/maquettes/propositions-v3/phrases.html`) : dire lesquelles réécrire.
- **Marées de nuit sur la courbe** : aujourd'hui en texte « hors soleil » ; les mettre sur la courbe demande une bande de 24 h (à maquetter si voulu).

## À faire
- **`unified` (appli Android)** : en retard depuis `d2f3cff`. À publier seulement sur demande.
- **Légendes qui évitent le point** : fait pour la courbe de marée et le déroulé ; à étendre aux autres encarts (houle, vent, semaine) s'il en reste qui se recouvrent.
- **Taille des flèches de clapot** : taille moyenne fixe, pas de règle.
- **Windguru, Windy, Yadusurf** : pas lisibles automatiquement ; relever à la main si on veut les comparer.

## Idées gardées
- « Tu surfes sur quoi ? » en choix multiple dans le matériel.
- Nettoyer les anciens réglages inutilisés (`volume_level_idx`, `body_height_cm`, `body_fitness`, `stars_offset`, `show_*_card`).
- Nuages : transitions plus douces si un défaut revient.

## Bouées (13 octobre 2026, à décider)
- **Candhis (Cerema)** : réseau national de bouées, données sous licence ouverte Etalab 2.0 (citer le Cerema et l'organisme de la campagne, la date de mise à jour, ne pas altérer les données). Bouée temps réel la plus proche des spots de Gironde : **03302 Cap Ferret [TR]** (mesure toutes les 30 min : H1/3, Hmax, Th1/3, direction au pic, étalement, température de l'eau). Pas d'API publique documentée : les chiffres sont dans la page web de la station. Le serveur n'autorise pas l'appel direct depuis le navigateur (pas d'en-tête CORS) : il faudrait un petit relais. À étudier : demander un accès propre au Cerema, ou regarder les jeux de données sur data.gouv.fr.
- **Mesure de l'appli contre la bouée** (premier essai, 11 au 13 octobre, Cap Ferret) : le mélange actuel (hauteur de MF-WAM + période de GFS Wave) donne environ 845 kJ à 7 h le 13, la bouée environ 534 kJ ; MF-WAM seul ou ECMWF seul sont à environ 10 % de la bouée, avec leurs propres périodes. La période de GFS Wave est plus longue d'environ 2 s que celle de la bouée (définitions un peu différentes, à vérifier).
- **WaveWatch III** : c'est le modèle derrière GFS Wave (déjà utilisé). Pas d'autre source WW3 nécessaire pour l'instant.
- **Consensus des modèles de vagues** (validé dans le principe le 13 octobre) : test sur 48 h contre la bouée de Cap Ferret, écart moyen d'énergie : appli actuelle +95 %, GFS Wave +84 %, MF-WAM +43 %, médiane des 3 modèles +32 %, ECMWF seul +20 %. Tous les modèles surestiment ; le consensus est bien meilleur que le mélange actuel, mais ECMWF seul est meilleur sur cet échantillon (une seule bouée, 48 h, périodes de définitions différentes). À refaire sur plusieurs semaines avant de coder.
- **Bouée de référence par spot** : liste des 27 bouées Candhis temps réel dans `docs/BOUEES_CANDHIS.md`. Choisir la bouée à la main pour chaque spot (exposition, profondeur), pas la plus proche automatiquement : par exemple Royan (14 m de fond, à l'entrée de l'estuaire) est la plus proche de Montalivet mais ne voit pas la même houle que la côte ouverte. Afficher le nom de la bouée et la distance. Spots sans bouée raisonnable : ne rien afficher.
- **Deux consensus** (idée du propriétaire, 13 octobre) : un consensus court terme (J+0, J+1) qui pèse davantage les modèles fins et ceux qui collent le mieux à la bouée de référence du spot, et un consensus long terme (J+2 et plus) fait des modèles globaux ; fondu entre les deux autour de J+2 pour éviter un saut. Les poids viennent de la comparaison avec les bouées, pas d'un a priori. Départ proposé tant qu'on n'a pas l'historique : court terme = MF-WAM + ECMWF (GFS Wave écarté, il surestimait de +84 % sur le test) ; long terme = médiane des trois.
