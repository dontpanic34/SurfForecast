#!/usr/bin/env bash
# Prépare le site pour la publication : numéro de version, version.json et adresse du programme
# (surflog.js) propre à chaque version. Les fichiers wasm ont déjà un nom unique (empreinte), mais
# surflog.js garde toujours le même nom : sans ce « ?v=N », un navigateur ou Cloudflare pouvait en servir
# une ancienne copie. Avec « ?v=N », chaque version a une adresse neuve, donc jamais de copie périmée.
# Usage : web-release.sh <dossier-dist> <numero-de-build>
set -euo pipefail
DIST="$1"
N="$2"
VERSION="1.0.$N"

sed -i "s|<meta name=\"app-version\" content=\"dev\">|<meta name=\"app-version\" content=\"$VERSION\">|" "$DIST/index.html"
sed -i "s|src=\"surflog.js\"|src=\"surflog.js?v=$N\"|" "$DIST/index.html"
printf '{"version":"%s"}\n' "$VERSION" > "$DIST/version.json"

grep -q "app-version\" content=\"$VERSION\"" "$DIST/index.html" || { echo "Version non écrite dans index.html"; exit 1; }
grep -q "surflog.js?v=$N" "$DIST/index.html" || { echo "Adresse versionnée de surflog.js non écrite"; exit 1; }
ls -la "$DIST"
