#!/usr/bin/env bash
# Capture code (kupalinka-capture-a47d267ea8c1795fbb7df224): only for the capture build, gulnya/claude/capture-build.md
# Vinto's App Store screenshots, from the capture build: zdymak builds the Capture configuration (scheme
# iosApp-capture: release Kotlin and Swift with the store scenes, app.kupalinka.vinto.capture, unsigned for the
# simulator) and installs it on DEVICE, then launches it once per scene with the launch argument
# `-<shared capture id> <scene>`, which CaptureViewController reads, the status bar pinned at 9:41. No other build has
# the scenes. BUILD=0 reuses the capture app already installed. Extra arguments go to zdymak.
#
# A heavy job (an Xcode build and a booted simulator): run it through gulnya's heavy lock, with the device claimed.
# zdymak's --configuration needs zdymak 1.4.0 or later (package.json).
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

build=()
if [ "${BUILD:-1}" = "1" ]; then
    build=(--build --project iosApp/iosApp.xcodeproj --scheme iosApp-capture --configuration Capture)
fi

$ZDYMAK capture --platform ios --device "$DEVICE" --bundle "$BUNDLE" --arg "-$CAPTURE_ID" --states "$STATES" \
    --settle "$SETTLE" ${build[@]+"${build[@]}"} --out "$OUT" "$@"
