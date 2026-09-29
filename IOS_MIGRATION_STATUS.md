# Migration iOS / Kotlin Multiplatform — état d'avancement

Branche dédiée : `ios-kmp-migration`. **La branche `main-26mfzy` (Android pur) n'est pas
touchée** par ce chantier — l'app Android continue de fonctionner et de se builder
exactement comme avant.

## Approche

- Module `:shared` (Kotlin Multiplatform, cibles `androidTarget` + `iosX64`/`iosArm64`/`iosSimulatorArm64`)
  qui portera à terme toute la logique métier (prévisions, marées, scoring, unités).
- UI Android : Jetpack Compose, inchangée (le module `:app` existant n'est pas encore
  branché sur `:shared` — voir "Prochaines étapes").
- UI iOS : SwiftUI, à écrire, consommant le framework `SharedCore.framework` généré
  par `:shared`.
- Réseau : Retrofit+Gson et `HttpURLConnection` (Android/JVM only) remplacés par
  **Ktor** (multiplateforme) + **kotlinx.serialization** dans `:shared`.
- Dates : `java.time.*` (JVM only) remplacé par **kotlinx-datetime** dans `:shared`.

## Fait cette nuit

- Squelette Gradle KMP (`:shared` module, version catalog, CI dédiée
  `.github/workflows/build-kmp.yml` qui build Android+iOS sur push vers cette branche).
- Slice minimal fonctionnel dans `:shared` : `SharedForecastService` — récupère houle
  (Open-Meteo Marine) + météo/vent (Open-Meteo Forecast) pour un point GPS donné et les
  fusionne en `List<HourlyForecastPoint>`. Testé (`SharedForecastServiceTest`, mock Ktor).
- **Ce slice est volontairement simplifié** : un seul modèle Open-Meteo (pas encore le
  choix court terme/long terme AROME/ECMWF/MFWAM de `SurfRepository.getHybridForecast()`
  côté Android), pas encore les marées (api-maree.fr).

## Explicitement hors scope pour l'instant

- Fusion multi-modèles complète (parité avec `SurfRepository.kt`).
- Marées (api-maree.fr) et scoring (`SurfScoring.kt`).
- Widgets iOS (WidgetKit — architecture totalement différente d'Android AppWidget,
  chantier séparé).
- Journal de session (Room + photos) — reste Android-only pour l'instant.
- Toute l'UI SwiftUI (aucun écran iOS n'existe encore à ce stade).
- Signature, TestFlight, App Store : **nécessite un compte Apple Developer (99$/an)
  que seul l'utilisateur peut créer** — rien de possible ici tant que ce n'est pas fait.

## Prochaines étapes (par ordre logique)

1. Vérifier que `build-kmp.yml` passe au vert sur les deux jobs (Android + iOS).
2. Porter les marées et le scoring dans `:shared`, puis la fusion multi-modèles complète.
3. Faire consommer `:shared` par `:app` (Android) à la place du code dupliqué actuel,
   une fois la parité de comportement vérifiée — pour ne garder qu'une seule source de
   vérité pour la logique.
4. Scaffolder un projet Xcode/SwiftUI consommant `SharedCore.framework`, avec un
   premier écran (conditions actuelles du spot favori).
5. Construire les écrans SwiftUI restants (semaine, houle, vent, marée, mer de vent...).
6. Quand prêt à distribuer : créer le compte Apple Developer, configurer signing et
   TestFlight.
