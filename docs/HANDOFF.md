# Handoff : à lire au début d'une nouvelle conversation

Écrit le 1er novembre 2026. Complète `CLAUDE.md`, `docs/DECISIONS.md`, `docs/JOURNAL.md` et `docs/A_FAIRE.md` (à lire dans cet ordre, le journal a toutes les étapes). Le dépôt est public : jamais de clé, mot de passe ni identifiant dans les fichiers ni dans le chat ; les secrets restent dans GitHub ou Cloudflare, tapés par le propriétaire.

## Le projet
Surf Log : prévisions de surf (Kotlin Multiplatform / Compose). Site `surflog.fr` (branche `web`) et APK Android (branche `unified`). Langue : tout en français. Le propriétaire surfe à Montalivet (Gironde).

## Branches et publication
- Branche de travail : `claude/new-session-pume8w`. Collecte automatique des prévisions : branche `main` (workflow `collect-forecasts.yml`, scripts dans `scripts/collect/`), relevés dans la branche `data` (`data/runs/…`, `data/obs/…`, 4 relevés par jour depuis le 13 octobre).
- Publier = cherry-picker le commit de travail dans deux worktrees : `/tmp/wt-web` (branche `publi-web`, pousse vers `web`) et `/tmp/wt-unified` (branche `publi-unified`, pousse vers `unified`). Si ces dossiers n'existent plus : `git worktree add /tmp/wt-web -B publi-web origin/web` et pareil pour `unified`. Les commits qui ne touchent que `docs/` ne sont pas cherry-pickés : en cas de conflit sur `docs/JOURNAL.md`, faire `git checkout <commit> -- docs/` puis `git cherry-pick --continue`.
- Avant de pousser : `sh ./gradlew :shared:compileKotlinWasmJs :shared:testAndroidHostTest` dans le dépôt, puis `:app:compileDebugKotlin` dans `/tmp/wt-unified`. Puis `git push origin publi-web:web`, `git push origin publi-unified:unified`, `git push origin HEAD:claude/new-session-pume8w`.
- Le workflow `build-kmp.yml` construit le site et l'APK (il va chercher `unified` dans `apk-src`) et déploie sur Cloudflare Pages. Numéro de version = numéro de run. En ligne ? `curl https://surflog.fr/version.json` et `/apk-version.json` (les deux doivent avancer). Un déploiement Cloudflare qui échoue (« Unknown internal error ») se relance avec `rerun_failed_jobs`.
- Fichiers qui divergent sur `unified` : `LiveCamOverlayScreen` (dans `shared/src/androidMain`), `SessionLogDatabaseBuilder.android`, `MainScreen.kt`, `SurfController.kt`, `SurfIcons.kt`, `SurfLogApp.kt`, et `app/` (widget : `WidgetDataCache.kt`, `WidgetRefreshWorker.kt`). Ne jamais copier `app/` de la branche de travail vers `unified`.
- Règle du propriétaire : après codage et tests, publier le site (`web`) puis l'appli (`unified`) sans attendre un nouveau « go », puis lister les tâches restantes. En cas de doute de design : maquette interactive d'abord, code seulement sur « go ». Pas de code pendant une discussion pure (« Code rien, on parle »). Il déteste les régénérations inutiles et veut un avis quand il le demande.
- Outils : le MCP GitHub se déconnecte parfois (réessayer) ; la vérification en ligne passe aussi par `curl`. Mettre à jour `docs/JOURNAL.md` (et `A_FAIRE.md`, `DECISIONS.md`) dans le même commit que le code ; passer l'état à « en ligne » une fois publié.

## Ce qui est en ligne (version 1.0.502 au 1er novembre, puis l'offshore adouci)
- **Où surfer ?** (réglages, section Voyage, et encart sous la vue semaine quand au moins 3 jours sur 7 sont sous 2 étoiles) : carte façon carte météo : fond NASA Blue Marble, ruban de côte coloré par la note + pastilles rondes (moyenne par portion de côte, un chiffre par spot en zoomant), rayon 50 / 100 / 300 / 500 km, jour + moment (matin 9 h / midi 13 h / après-midi 17 h), pastille de jour « ici ★ x » et « → ★ y » quand ça vaut le déplacement, « Le conseil du coin » (vent chez soi, orientation de plage qu'il faudrait, spots qui répondent). Code : `WhereToSurf.kt`, `WhereToSurfDialog.kt`, `WhereToSurfMap.kt`, `SurfRepository.fetchRegionScan`.
- **Vent** : `windConsensus` (`WindConsensus.kt`) : médiane des vitesses de ECMWF, GFS, AROME HD, AROME, ARPEGE ; rafales = moyenne des deux plus fortes ; direction moyenne pondérée. Le widget et l'écran principal affichent le vent ressenti (`windFeelKmh`).
- **Notation** : réfraction de la houle (écart de direction x 0,75), libellé « Abrité de la houle », libellés « Massif » (3 m et plus, trop gros) et « Flat » (sous 0,4 m), offshore adouci (courbe propre jusqu'à 20 km/h, aplatissement réduit de moitié à partir de 12 s, rafales divisées par deux en offshore).
- **Orientations des plages françaises** mesurées sur la limite terre-mer de l'IGN (WFS `BDTOPO_V3:limite_terre_mer`) pour 27 spots sur 41 ; 14 spots incertains gardent l'ancienne valeur (voir le journal).
- **Menu du spot actif** : toucher son nom ouvre « Changer de spot » et « Voir sur Google Maps » (avec coordonnées et orientation).
- **Site** : détection de WebAssembly GC (iOS avant 18.2) avec message clair et bouton « Recharger ».

## Décisions récentes à ne pas rouvrir sans le propriétaire
- Le test du vent continue **jusqu'à fin novembre sans changer le vent** (bilan à mi-parcours dans le journal : les stations sont abritées, la tempête de fin octobre a donné 60 km/h et rafales à 80 sur le front de mer). Pas de changement de la règle de taille (« taille complète » à 250 kJ) avant la fin du test : sur demain 2 novembre, Surf-Forecast donne 0/10, l'appli 1 étoile.
- **Pas de spots secrets dans le catalogue** (dépôt public) ; ne jamais écrire leur nom dans le dépôt.
- Écarté : iOS natif (plus tard), alertes, partage, fenêtre intégrée (iframe) pour les webcams.

## À faire (ordre proposé)
1. **Vue satellite récente du spot** (Copernicus Sentinel-2, niveau 1) : données ouvertes chez Amazon (catalogue Earth Search, sans compte), un passage tous les 2 à 5 jours, nuages fréquents ; faire d'abord un test sur Montalivet (image claire la plus récente, vignette autour du spot) et la montrer au propriétaire avant tout code.
2. **Orientations des plages** : Espagne, Portugal, Maroc (source satellite NASA, ±10 à 15°), et les 14 spots français incertains (jetées, embouchures) à vérifier à la boussole ou sur Géoportail.
3. **Balise de plage** (anémomètre Windbird / OpenWindMap, environ 360 € TTC + 20 €/an, données publiques sans clé) : lue par la collecte puis par l'appli ; en attendant, ajouter à la collecte la balise Windbird la plus proche (31 km).
4. **Cahier du mode surf trip** (`docs/CAHIER_SURF_TRIP.md`, brouillon) : monter / attendre / changer de spot, météo du séjour, conseils de van (OpenStreetMap), hors réseau, journal de trip.
5. **Comparaison avec Windguru et Yadusurf** (illisibles automatiquement : demander des captures au propriétaire).
6. **Page blanche iPhone** : attendre la version d'iOS des utilisateurs touchés ; le nouveau message d'erreur dira la cause si ce n'est pas iOS < 18.2.
7. **Traductions** (EN, DE, NL, ES) des nouveaux textes de « Où surfer ? » ; relecture des Excel de traduction.
8. **Reste dans `docs/A_FAIRE.md`** : notation du vent des étoiles, questionnaire de niveau, synchronisation du profil et du journal, relais Cloudflare avec cache pour Open-Meteo, arrivée de la houle, 8 adresses GoSurf mortes, vérifier sur téléphone les nouveautés.

## Pièges connus
- Open-Meteo : multi-points = réponse en liste ; clés suffixées par modèle quand plusieurs modèles sont demandés ; AROME ne couvre que la France.
- Les tests wasm navigateur ne tournent pas (« aucun test découvert ») : seuls `:shared:testAndroidHostTest` comptent.
- Un test qui encode une ancienne règle se met à jour avec la nouvelle valeur et un commentaire qui dit pourquoi, jamais en le supprimant.
- Le classifieur de sécurité de l'outil Bash tombe parfois en panne : réessayer la même commande une fois.
