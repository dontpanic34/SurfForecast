# Fabrication de l'atlas de nuages

1. `sr.py photo.jpg photo_x4.png` : agrandit une photo x4 (Real-ESRGAN `realesr-general-x4v3`, réseau réimplémenté en numpy, poids téléchargés depuis les versions GitHub de Real-ESRGAN dans `general.pth`). `load.py` lit les poids sans PyTorch.
2. `atlas.py separes_x4.png amas_x4.png` : détoure (démélange ciel / nuage), découpe en sprites, fabrique `atlas.png` (luminance dans le rouge, alpha) et `atlas_flou.png` (brume), et `atlas.json`.
3. Convertir en WebP (qualité 92) dans `shared/src/commonMain/composeResources/drawable/` (`sky_atlas_nuages.webp`, `sky_atlas_flou.webp`) et régénérer `CloudAtlas.kt` depuis `atlas.json`.
