#!/usr/bin/env bash
# Capture code (kupalinka-capture-a47d267ea8c1795fbb7df224): only for the capture build; CLAUDE.md, "The capture build"
# Vinto's Play screenshots, from the capture build (app.kupalinka.vinto.capture, the only build with the store
# scenes): installs it on the attached device (`./gradlew :androidApp:installCapture`: R8 like release, the debug key;
# INSTALL=0 keeps the one installed), then zdymak opens CaptureActivity once per scene under the shared capture id, the
# intent extra it reads, and takes each shot with the clean status bar. CaptureActivity recreates itself on each new
# scene, so no restart is needed between them. Extra arguments go to zdymak (`--record --states king,queen,call`).
#
# A heavy job (Gradle and a device): run it through this Mac's heavy-job lock, with the device claimed.
#
#   OUT=./marketing/captures/android-tablet Scripts/capture-android.sh --size 2560x1440 --density 320
set -euo pipefail

cd "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

CAPTURE_ID="kupalinka-capture-a47d267ea8c1795fbb7df224" # the handle key (composeApp src/captureMain CaptureId.kt)
COMPONENT="app.kupalinka.vinto.capture/game.vinto.app.CaptureActivity"
OUT="${OUT:-./marketing/captures/android}"
STATES="${STATES:-home,teach,table,score,lobby,plan}"
SETTLE="${SETTLE:-90}"
ZDYMAK="${ZDYMAK:-npx zdymak}"

if [ "${INSTALL:-1}" = "1" ]; then
    ./gradlew :androidApp:installCapture
fi

$ZDYMAK capture --platform android --component "$COMPONENT" --arg "$CAPTURE_ID" --states "$STATES" \
    --settle "$SETTLE" --out "$OUT" "$@"
