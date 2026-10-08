#!/usr/bin/env bash
# Prépare le site pour la publication : numéro de version, version.json, et fichiers du programme
# rangés dans un dossier propre à chaque version (v<N>/). Chaque mise à jour a ainsi de NOUVELLES
# adresses de fichiers : aucun navigateur ni cache (Cloudflare compris) ne peut servir une ancienne copie.
# Si le site rangé ainsi ne démarre pas (test dans Chrome sans écran), on publie la disposition à plat.
# Usage : web-release.sh <dossier-dist> <numero-de-build>
set -euo pipefail
DIST="$1"
N="$2"
V="v$N"
VERSION="1.0.$N"

sed -i "s|<meta name=\"app-version\" content=\"dev\">|<meta name=\"app-version\" content=\"$VERSION\">|" "$DIST/index.html"
printf '{"version":"%s"}\n' "$VERSION" > "$DIST/version.json"

rm -rf "$DIST.flat"
cp -r "$DIST" "$DIST.flat"

mkdir "$DIST/$V"
for f in "$DIST"/*; do
  [ -f "$f" ] || continue
  case "$(basename "$f")" in
    index.html|manifest.webmanifest|sw.js|_headers|version.json|*.png|*.ico|*.svg) ;;
    *) mv "$f" "$DIST/$V/" ;;
  esac
done
sed -i "s|src=\"surflog.js\"|src=\"$V/surflog.js\"|" "$DIST/index.html"

boots() {
  (cd "$DIST" && python3 -m http.server 8099 >/dev/null 2>&1 & echo $! > /tmp/web-release-srv.pid)
  sleep 2
  local out
  out=$(google-chrome --headless=new --no-sandbox --disable-gpu --window-size=430,932 \
    --virtual-time-budget=30000 --dump-dom http://localhost:8099/ 2>/dev/null || true)
  kill "$(cat /tmp/web-release-srv.pid)" 2>/dev/null || true
  # main() retire l'écran de chargement puis crée le canvas : les deux doivent être vrais.
  echo "$out" | grep -q '<canvas' && ! echo "$out" | grep -q 'id="loading"'
}

if boots; then
  echo "Disposition versionnée ($V/) validée."
else
  echo "::warning::Le site rangé dans $V/ n'a pas démarré au test : publication à plat (sans dossier de version)."
  rm -rf "$DIST"
  mv "$DIST.flat" "$DIST"
fi
rm -rf "$DIST.flat"
ls -laR "$DIST" | head -40
