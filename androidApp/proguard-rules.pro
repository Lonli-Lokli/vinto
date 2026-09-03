# R8 keep rules for the release build.
#
# Deliberately almost empty, and that is a claim worth checking rather than a hope. Nothing in
# this app looks a class up by name at runtime:
#
#   * kotlinx-serialization resolves every serializer at COMPILE time. Every call site names its
#     serializer explicitly (`VintoJson.decodeFromString(GameRecording.serializer(), …)`) or uses
#     the reified form, so there is no reflective `serializer()` lookup for R8 to break. The
#     library also ships its own consumer rules, which is what keeps the generated `$serializer`
#     classes and their `Companion` fields.
#   * Compose resources are ASSETS, not code — `assets/composeResources/…` — so neither shrinking
#     nor obfuscation can reach them. `isShrinkResources` removes unused Android `res/`, of which
#     this app has almost none: the launcher icons and the window theme.
#   * The crash reporter builds its Sentry envelope by hand rather than serialising a model, and
#     the analytics events are a sealed hierarchy walked by `when`, not by reflection. The one
#     thing that DOES use reflection is `AnalyticsPrivacyTest`, and a test is not in the release
#     binary.
#
# So the rule that matters is the one that is absent: do not add keeps speculatively. Every keep
# is a piece of the app R8 may not shrink, and a file of defensive keeps copied from a blog is how
# minification quietly stops paying for itself.
#
# WHAT TO DO IF SOMETHING BREAKS. R8 failures are RUNTIME failures — the build stays green and the
# app dies on a screen. Symptoms are a `SerializationException` naming a class that plainly
# exists, or a `ClassNotFoundException` for something only ever referenced by name. Add the
# narrowest keep that fixes it, with a comment saying which screen it was, rather than widening an
# existing one.
#
# Line numbers are kept so a Sentry stack trace from a release build is readable at all; the
# source file name is renamed rather than stripped, which is what makes the mapping meaningful.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
