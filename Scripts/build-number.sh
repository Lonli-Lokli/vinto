#!/usr/bin/env sh
# Single source of truth for the app build number, used by BOTH platforms so the same commit
# yields the same number on iOS and Android. They still carry independent marketing versions —
# see VERSIONING.md for why that is deliberate.
#
#   build number = number of commits reachable from HEAD  (monotonic, deterministic, no state)
#
# Usage:
#   iOS archive : xcodebuild archive … CURRENT_PROJECT_VERSION="$(Scripts/build-number.sh)"
#   Android     : ./gradlew :androidApp:bundleRelease -PversionCode="$(Scripts/build-number.sh)"
#
# Requires a FULL clone. A shallow CI checkout counts the commits it happens to have, which is
# not monotonic — use `fetch-depth: 0`. Falls back to 1 in a non-git or exported tree so that a
# build never fails on this alone.
set -e
if git rev-parse --git-dir >/dev/null 2>&1; then
    git rev-list --count HEAD
else
    echo 1
fi
