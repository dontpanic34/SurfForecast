#!/usr/bin/env bash
# Installe le SDK Android (plateforme 37.0) pour compiler et tester le module Android en local ou dans une session cloud.
# À coller dans « Setup script » de l'environnement cloud, ou à lancer à la main : sh scripts/installer-sdk-android.sh
set -euo pipefail

SDK="${ANDROID_HOME:-$HOME/android-sdk}"
TOOLS_ZIP="commandlinetools-linux-11076708_latest.zip"
PLATEFORME="android-37.0"

mkdir -p "$SDK/cmdline-tools"
if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
  tmp="$(mktemp -d)"
  curl -fsSL -o "$tmp/$TOOLS_ZIP" "https://dl.google.com/android/repository/$TOOLS_ZIP"
  unzip -q "$tmp/$TOOLS_ZIP" -d "$tmp"
  rm -rf "$SDK/cmdline-tools/latest"
  mv "$tmp/cmdline-tools" "$SDK/cmdline-tools/latest"
  rm -rf "$tmp"
fi

export ANDROID_HOME="$SDK"
yes | "$SDK/cmdline-tools/latest/bin/sdkmanager" --licenses >/dev/null || true
"$SDK/cmdline-tools/latest/bin/sdkmanager" "platform-tools" "platforms;$PLATEFORME" "build-tools;36.0.0"

# Gradle trouve le SDK par ANDROID_HOME ou par local.properties (ignoré par git).
echo "sdk.dir=$SDK" > "$(dirname "$0")/../local.properties"
echo "SDK Android installé dans $SDK"
