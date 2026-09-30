# Migration iOS / Kotlin Multiplatform — état d'avancement

Branche dédiée : `ios-kmp-migration`. **La branche `main-26mfzy` (Android pur) n'est pas
touchée** par ce chantier : l'app Android continue de fonctionner et de se builder
exactement comme avant.

## Approche

- Module `:shared` (Kotlin Multiplatform, cibles Android + `iosArm64`/`iosSimulatorArm64`).
- **UI : Compose Multiplatform.** Les écrans Jetpack Compose d'`app/` sont recopiés tels
  quels dans `:shared` (mêmes tailles, couleurs, textes, dessins) et tournent sur iOS
  avec le même code. Pas de redesign : l'app iOS est la même que l'app Android.
- Ce qui change pour que ça compile aussi sur iOS, sans changer le rendu :
  - réseau : Retrofit/Gson → **Ktor** + **kotlinx.serialization** ;
  - dates : `java.time` → **kotlinx-datetime** ;
  - texte dans les Canvas : `android.graphics.Paint` → `TextMeasurer` (même taille,
    même graisse, même ligne de base) ;
  - préférences : `SharedPreferences` → interface `KeyValueStore`
    (SharedPreferences sur Android, NSUserDefaults sur iOS, **mêmes clés**) ;
  - `SurfViewModel` → `SurfController` (même état, mêmes règles) ;
  - icônes Material et police Inter / logo embarqués via les ressources Compose.
- App iOS (`iosApp/`, projet XcodeGen) : simple coquille SwiftUI qui affiche l'écran
  Compose partagé.

## Déjà porté (vérifié en CI, Android + simulateur iOS)

- Prévisions multi-modèles (AROME/ECMWF/ARPEGE + MFWAM/ECMWF WAM), marées, soleil,
  scoring "Meilleur créneau" — avec tests unitaires exécutés sur Android et iOS.
- Écran principal : bandeau spot + spots proches, statut "Meilleur créneau", overlay
  temps réel, favoris, thème clair/sombre, ordre des cartes (glisser) et cartes
  repliables.
- Cartes : Prévision semaine, Déroulé de la journée, Houle, Vent, Mer de vent, Météo,
  Heure par heure. Détail météo. Préférences. Choix du spot.

## Pas encore porté

- Journal de sessions / quiver (Room + photos) : bouton présent, message "bientôt".
- Lecteur webcam intégré (WebView) : pour l'instant la webcam s'ouvre dans le navigateur.
- Cache hors connexion.
- Widgets : Android seulement (sur iOS il faudra un widget WidgetKit en SwiftUI).
- `:app` (Android) ne consomme pas encore `:shared` : à faire une fois la parité validée,
  pour n'avoir qu'une seule source de vérité.
- Signature / TestFlight / App Store : nécessite un compte Apple Developer (99 $/an).

## Voir l'app iOS sans Mac

Chaque run de `build-kmp.yml` lance l'app dans un simulateur et publie une capture
d'écran en artifact (`ios-screenshot-<n°>`) : page du run GitHub Actions → section
"Artifacts" → télécharger. Étape non bloquante.

## Ouvrir le projet sur un Mac

```
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
cd iosApp && brew install xcodegen && xcodegen generate && open SurfLog.xcodeproj
```
