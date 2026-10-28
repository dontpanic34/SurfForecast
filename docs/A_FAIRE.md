# À faire

Mis à jour le 13 octobre 2026 en fin de journée. Le lot du jour est en ligne sur `web` (1.0.333) et `unified` (voir le journal).

## À trancher / valider par le propriétaire
- **Flèche 3 et icône de l'eau** : à juger à l'écran (l'icône de l'eau a été redessinée : thermomètre plein sur deux vagues).
- **Vent** : consensus de tous les modèles en ligne depuis le 21 octobre (médiane des vitesses, rafales les plus fortes). Décidé le 28 octobre : **on laisse tourner la collecte un mois de plus** (jusqu'à fin novembre) sans changer le vent. Bilan à mi-parcours dans le journal : les stations abritées donnent l'ancien consensus meilleur, mais la tempête de la semaine du 20 octobre a donné 60 km/h et rafales à 80 km/h et plus sur le front de mer (proche des 70 à 87 prévus par l'AROME HD). La station de Vendays est bonne pour la tendance, pas pour la direction du vent de plage. Idée : une **balise de plage** (anemomètre ouvert Windbird / Pioupiou, données publiques sans clé) chez le propriétaire, lue par la collecte puis par l'appli.
- **Coefficients de fréquence du volume** (+5 %, +10 %, +15 %) à valider.
- **Icônes B** de la semaine (le chiffre 1/2/3 règle aussi les segments) : à juger à l'usage.
- **Accueil en trois cartes et visite guidée courte** : à juger à l'usage.
- **Textes de l'échelle des niveaux** : à relire.
- **Liste des phrases** (`docs/maquettes/propositions-v3/phrases.html`) : dire lesquelles réécrire.
- **Marées de nuit sur la courbe** : aujourd'hui en texte « hors soleil » ; les mettre sur la courbe demande une bande de 24 h (à maquetter si voulu).

## À faire
- **Orientations des plages** : France faite à 27 spots sur 41 (voir le journal) ; à vérifier : les 14 spots incertains (jetées, embouchures, rochers) ; Espagne, Portugal et Maroc à mesurer avec une autre source (satellite de la NASA, environ ±10 à 15°).
- **Vue satellite récente du spot** (Copernicus Sentinel-2, niveau 1) : attend le compte Copernicus Data Space du propriétaire ; identifiants dans les secrets GitHub, jamais dans le dépôt.
- **Où surfer ? et mode surf trip** (19 octobre) : le calcul, l'écran jour par jour (option B), la ligne des réglages et l'encart sous la vue semaine sont codés (voir le journal). La **carte** est refaite façon carte météo (ruban + pastilles, jour + moment, conseil du coin) : à vérifier sur téléphone ; idée : affiner avec le propriétaire les orientations de plage du catalogue (Gironde d'abord). Reste aussi : la **pastille sous le nom du spot**, les **traductions** (EN, DE, NL, ES), la règle d'apparition à valider (3 jours sur 7 sous 2 étoiles), vérifier sur téléphone (durée du calcul, rayon 500 km, spots sans orientation), puis le cahier à développer ensemble dans `docs/CAHIER_SURF_TRIP.md`.
- **Vérifier sur téléphone le journal de session refait** (14 octobre) : barre de début, durée, « + Planche », « Prévu · Vécu » en couleur, « + » central de la barre du bas, anciennes sessions (étoiles lues comme des libellés), historique des relevés (Réglages > Aide). Le glisser des encarts (appui long) et les couleurs de la carte Météo aussi.
- **Journal de session, restes** : la photo qui remplit l'heure (idée de la maquette `journal-session-idees.html`, pas faite). Décidé le 14 octobre : pas de « Je pars surfer » ; le bandeau « Tu as surfé hier ? » existe déjà, on n'y touche pas.
- **Loupe de spot dans l'en-tête** (maquette `entete-spot-favoris.html` : 5 en-têtes et 5 façons de placer les favoris, à valider ; la première maquette `loupe-spot.html` est abandonnée : « 1 2 3 » pas compris) : loupe à gauche du nom, ouvre le choix du spot avec recherche active, pastilles 1 2 3 par spot pour choisir la place de favori.
- **Planche du jour, suite** : tenir compte de la marée. Décidé le 14 octobre : la planche par heure existe déjà, pas de phrase d'explication (trop long).
- **Vérifier sur téléphone** le trait de marée (collé au graphique, mot Montante/Descendante dessous, flèches, horaires).
- **Relecture des traductions (EN, DE, NL, ES)** : tout est traduit et relu une fois par l'assistant, pas par un humain ; certains textes composés (phrases du jour) peuvent sonner bizarre, et quelques libellés sont longs en néerlandais (ex. « Gemotiveerd » coupé). À relire par un locuteur ; tout texte français ajouté doit recevoir sa traduction.
- **Coefficient estimé** : comparer, quand api-maree.fr revient, le coefficient calculé (marnage de Brest d'Open-Meteo ÷ 6,1 m) aux vrais coefficients sur une quinzaine de jours, et ajouter un facteur de correction si l'écart est systématique. La copie des marées est faite depuis le 14 octobre dans le KV Cloudflare (voir JOURNAL).
- **Ligne « Mesuré » dans Actuellement** (à reprendre quand le propriétaire sera concentré dessus) : tout est codé sur la branche (relais `functions/api/obs`, `ObservedNow`, ligne sous la bouée) mais **pas publié**. Pour la mettre en ligne : ajouter la variable secrète `METEOFRANCE_API_KEY` dans le projet Cloudflare Pages (Settings, Variables and Secrets), puis le « go ».
- **Corriger le prévu avec le réel** (idée du 13 octobre) : décaler les 3 à 6 prochaines heures vers la mesure de la station ; à maquetter avant de coder.
- **Vent mesuré** (collecte en place depuis le 13 octobre) : après 3 à 4 semaines de relevés (`data/obs` sur la branche `data`), comparer chaque modèle de vent à la station la plus proche de chaque spot, choisir les poids du vent, puis afficher « mesuré » dans l'encart Actuellement. Lacanau n'a pas de station qui mesure le vent : choisir une station voisine.
- **Cheminées basses et ligne de temps** : codées le 13 octobre, vérifiées avec de fausses données ; à juger sur le téléphone (hauteur 32 dp, blocs de nuit, ligne de temps dans Actuellement, ligne d'infos de la météo).
- **Option « Bulletin marine »** de Météo-France (rubrique Prévisions du portail) : à regarder.
- **Vigilance Météo-France** (vent violent, vagues-submersion) : idée, pas de maquette.
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
- **Phase de test et journal de fiabilité** (idée du propriétaire, 13 octobre) : l'appli garde déjà un journal de prévisions sur le téléphone (2 jours, seulement quand l'appli s'ouvre). Proposition : une collecte automatique côté dépôt (tâche planifiée GitHub) qui enregistre, pour chaque spot de référence, les prévisions de chaque modèle à J, J+1, J+2 (et plus) et la mesure de la bouée, puis une page « Fiabilité » dans l'appli qui compare prévu et réel. Phase de test de 3 à 4 semaines, puis poids figés et suivi léger. Aucune clé ni donnée personnelle dans le dépôt public (sources à citer : Open-Meteo, Cerema).

## Reste après la refonte (13 octobre, soir)
- **Restyler les pages internes** Profil (questionnaire) et Matériel avec `SettingsKit`.
- **Aperçu avec les vraies valeurs de chaque modèle** sous le choix manuel (maquette `modeles-prevision`) : demande de récupérer les modèles non choisis pour les afficher ; pour l'instant, une fiche (finesse, portée, usage).
- **Séparer vent et météo** en manuel (aujourd'hui un seul choix « Vent et météo » règle le vent et le ciel).
- **Recaler les seuils d'énergie des profils** après le passage au consensus (les kJ sont plus bas) : à faire avec les premiers relevés contre la bouée.
- **Bouées hors de France** (Espagne, Portugal, Royaume-Uni) pour les spots sans bouée proche.
- **Bilan de la phase de test** (relevés des prévisions et des bouées) : à faire dans 3 à 4 semaines.



## Statistiques anonymes : réglage Cloudflare à faire par le propriétaire
1. Cloudflare > Workers & Pages > KV : créer un espace nommé `surflog-stats`.
2. Projet Pages `surflog` > Settings > Bindings : ajouter une liaison KV, nom `STATS`, espace `surflog-stats` (production).
3. Settings > Variables and Secrets : ajouter le secret `STATS_KEY` avec une longue clé de ton choix (ne jamais l'écrire dans le dépôt ni dans le chat).
4. Redéployer (un nouveau push suffit), puis ouvrir `https://surflog.fr/api/stats/` et entrer la clé.
