#!/usr/bin/env bash
# Capture code (kupalinka-capture-a47d267ea8c1795fbb7df224): only for the capture build; CLAUDE.md, "The capture build"
# Vinto's App Store screenshots, from the capture build: zdymak builds the Capture configuration (scheme
# iosApp-capture: release Kotlin and Swift with the store scenes, app.kupalinka.vinto.capture, unsigned for the
# simulator) and installs it on DEVICE, then launches it once per scene with the launch argument
# `-<shared capture id> <scene>`, which CaptureViewController reads, the status bar pinned at 9:41. No other build has
# the scenes. Extra arguments go to zdymak.
#
# `npm run capture-ios-set` runs the iPhone and iPad sets from ONE build. BUILD=1 (the default) builds the capture app
# and zdymak writes its path to APP_FILE (`--app-out`); BUILD=0 builds nothing and has zdymak install that app
# (`--app`) on whichever simulator DEVICE names: the one arm64 simulator build serves the iPhone and the iPad alike
# (niva/android-host-tests-prefs AC-11). zdymak boots the simulator only when it is not running and shuts down only
# what it booted, so this script never boots or shuts one down itself (AC-12). zdymak's --app needs 1.6.0 (package.json).
#
# A heavy job (an Xcode build and a booted simulator): run it through this Mac's heavy-job lock, with the devices claimed
# and booted under the claim, so every part finds them up.
#
#   DEVICE="iPad Pro 13-inch (M5)" OUT=./marketing/captures/ios-ipad Scripts/capture-ios.sh
set -euo pipefail

cd "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

CAPTURE_ID="kupalinka-capture-a47d267ea8c1795fbb7df224" # the handle key (composeApp src/captureMain CaptureId.kt)
BUNDLE="app.kupalinka.vinto.capture"
DEVICE="${DEVICE:-iPhone 18 Pro Max}"
OUT="${OUT:-./marketing/captures/ios}"
STATES="${STATES:-home,teach,table,score,lobby,plan}"
SETTLE="${SETTLE:-45}"
ZDYMAK="${ZDYMAK:-npx zdymak}"

# Where the BUILD=1 run's zdymak writes the built app's path, for the BUILD=0 parts that install it.
APP_FILE="${APP_FILE:-build/capture-ios/app-path.txt}"
if [ "${BUILD:-1}" = "1" ]; then
    build=(--build --project iosApp/iosApp.xcodeproj --scheme iosApp-capture --configuration Capture --app-out "$APP_FILE")
else
    [ -s "$APP_FILE" ] || { echo "capture-ios: BUILD=0 installs the app a BUILD=1 run built, and $APP_FILE is missing" >&2; exit 2; }
    build=(--app "$(cat "$APP_FILE")")
fi

$ZDYMAK capture --platform ios --device "$DEVICE" --bundle "$BUNDLE" --arg "-$CAPTURE_ID" --states "$STATES" \
    --settle "$SETTLE" "${build[@]}" --out "$OUT" "$@"
