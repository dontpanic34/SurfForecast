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

## Fait cette nuit (vérifié en CI)

- Squelette Gradle KMP (`:shared`, plugin `com.android.kotlin.multiplatform.library`
  imposé par AGP 9) et CI dédiée `.github/workflows/build-kmp.yml`, déclenchée uniquement
  sur cette branche.
- Slice minimal dans `:shared` : `SharedForecastService` récupère houle (Open-Meteo
  Marine) + météo/vent (Open-Meteo Forecast) pour un point GPS et les fusionne en
  `List<HourlyForecastPoint>`. Test unitaire (mock Ktor) **exécuté sur Android et sur le
  simulateur iOS**.
- App iOS SwiftUI minimale (`iosApp/`, projet généré par XcodeGen) : liste les 24
  prochaines heures pour Montalivet (houle, période, vent, température) via le module
  partagé. **Build simulateur vert en CI**, non signé.
- Marées portées dans `:shared` (`SharedTideService`, api-maree.fr) avec la même
  logique que `SurfRepository.getTides()` : site le plus proche, PM/BM "de jour",
  repli du coefficient. Testé (site choisi, marées de jour, coef, erreur réseau).
  Affichées en tête de la liste iOS.
- L'app Android existante (`:app`) continue de builder dans cette nouvelle structure.
- **Ce slice est volontairement simplifié** : un seul modèle Open-Meteo (pas encore le
  choix court terme/long terme AROME/ECMWF/MFWAM de `SurfRepository.getHybridForecast()`
  côté Android), spot codé en dur (Montalivet).

## Voir l'app iOS sans Mac

Chaque run de `build-kmp.yml` lance l'app dans un simulateur et publie une capture
d'écran en artifact (`ios-screenshot-<n°>`) : page du run GitHub Actions → section
"Artifacts" → télécharger. Étape non bloquante (un souci de simulateur n'échoue pas le
build).

## Ouvrir le projet sur un Mac (si un jour tu en as un sous la main)

```
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
cd iosApp && brew install xcodegen && xcodegen generate && open SurfLog.xcodeproj
```

## Explicitement hors scope pour l'instant

- Fusion multi-modèles complète (parité avec `SurfRepository.kt`).
- Marées (api-maree.fr) et scoring (`SurfScoring.kt`).
- Widgets iOS (WidgetKit — architecture totalement différente d'Android AppWidget,
  chantier séparé).
- Journal de session (Room + photos) — reste Android-only pour l'instant.
- L'UI iOS au-delà de la liste horaire minimale (semaine, encarts houle/vent/marée,
  préférences, favoris...).
- Signature, TestFlight, App Store : **nécessite un compte Apple Developer (99$/an)
  que seul l'utilisateur peut créer** — rien de possible ici tant que ce n'est pas fait.

## Prochaines étapes (par ordre logique)

1. Porter le scoring dans `:shared`, puis la fusion multi-modèles complète.
2. Faire consommer `:shared` par `:app` (Android) à la place du code dupliqué actuel,
   une fois la parité de comportement vérifiée — pour ne garder qu'une seule source de
   vérité pour la logique.
3. Construire les écrans SwiftUI restants (semaine, houle, vent, marée, mer de vent,
   choix du spot...).
4. Quand prêt à distribuer : créer le compte Apple Developer, configurer signing et
   TestFlight (build appareil : il faudra aussi générer le framework `iosArm64`).
