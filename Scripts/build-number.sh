#!/usr/bin/env sh
# Single source of truth for the app build number, used by BOTH platforms so the same commit yields
# the same number on iOS and Android (they still carry independent marketing versions — see VERSIONING.md).
#
#   build number = number of commits reachable from HEAD  (monotonic, deterministic, no stored state)
#
# Usage:
#   iOS archive : xcodebuild archive … CURRENT_PROJECT_VERSION="$(Scripts/build-number.sh)"
#   Android     : ./gradlew :composeApp:bundleRelease -PversionCode="$(Scripts/build-number.sh)"
#
# IT FAILS RATHER THAN GUESSING, and that reverses what this script used to do.
#
# It used to end `else echo 1` — "falls back to 1 in a non-git / exported tree so a build never fails
# on this". On 2026-09-04 that produced a real consequence: Vodar 1.2 was archived and uploaded to
# App Store Connect as BUILD 1, sitting above a 131, and Apple accepted it because CFBundleVersion
# only has to be unique within a marketing version. Nothing failed, nothing warned anywhere anyone
# looked, and the result is a binary in review that cannot be traced to a commit by any means — no
# tag, no symbolication, no reproduction.
#
# A build that stops is a five-minute problem. A build that ships an untraceable number is permanent.
#
# The explicit escape hatch replaces the silent one: set BUILD_NUMBER=<n> for an exported tree or a
# CI checkout that genuinely has no history. That is a decision someone makes, and it appears in the
# command line where it can be read.
set -e

if [ -n "$BUILD_NUMBER" ]; then
    echo "$BUILD_NUMBER"
    exit 0
fi

if ! git rev-parse --git-dir >/dev/null 2>&1; then
    echo "error: not a git checkout, so there is no commit count to build a number from." >&2
    echo "       Pass BUILD_NUMBER=<n> explicitly if this is deliberate (exported tree, CI)." >&2
    exit 1
fi

# A shallow clone counts only what it fetched, so it yields a plausible, wrong, SMALLER number — and
# a wrong number that looks right is worse than none. CI needs fetch-depth: 0.
if [ "$(git rev-parse --is-shallow-repository 2>/dev/null)" = "true" ]; then
    echo "error: shallow clone — the commit count would be wrong (use fetch-depth: 0)." >&2
    echo "       Pass BUILD_NUMBER=<n> explicitly if this is deliberate." >&2
    exit 1
fi

git rev-list --count HEAD
