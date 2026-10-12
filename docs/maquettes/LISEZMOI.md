# Maquettes HTML (11 octobre 2026)

Fichiers autonomes, à ouvrir dans un navigateur.

- `ecran-semaine.html` : écran principal avec la phrase du jour et la température ressentie dans la semaine, et le déroulé en dessous. **Utilise les vrais nuages, soleil, eau et pluie de l'appli** : le placement des nuages est un portage fidèle de `CloudPlan.kt` (même générateur, mêmes seuils), les images sont embarquées. Pour refaire la maquette après un changement des sprites, régénérer les images embarquées depuis `shared/src/commonMain/composeResources/drawable/` et les rectangles de `CloudAtlas.kt`.
- `reglages.html` : refonte des réglages en pages courtes (maquette validée en principe).
- `brief-retravail.md` : brief de tâches et catalogue des phrases du déroulé (état du 10 octobre, avant les phrases sans « pour toi »).

Les maquettes « couvertures nuageuses » et « écran clair » ne sont pas dans le dépôt : leurs liens privés sont dans `docs/JOURNAL.md`.
- `profil-questions.html` : le questionnaire du profil (questions seules, cinq niveaux), 12 octobre 2026.
- `niveau.html` : propositions et essais pour le niveau, les phrases du vent et la page d'accueil des réglages (12 octobre 2026).
- `organigramme.html` : les cheminements du questionnaire vers les cinq niveaux, en diapos (12 octobre 2026).
