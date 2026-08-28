// Kept as "vinto-kmp" even though the build is no longer under `kmp/`. The name is not
// cosmetic: Kotlin/JS derives the output bundle from it, so the Worker shim's import of
// `vinto-kmp-worker.mjs` (worker/cloudflare/index.mjs and the gate scripts) is spelled with
// it, and `Recorder.PRODUCER` writes "vinto-kmp/local" into every recording's header.
// Renaming means editing those in the same commit; there is nothing to gain by it.
rootProject.name = "vinto-kmp"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        // Compose Multiplatform pulls androidx artifacts from Google's Maven.
        google()
    }
}

// Only the modules that exist today. The rest of the layout in
// openspec/changes/migrate-to-kotlin-multiplatform/design.md (D1) is added as it is ported.
include(":shared:shapes")
include(":shared:engine")
include(":shared:bot")
include(":shared:client")
include(":shared:protocol")
include(":shared:room")
include(":worker")
include(":composeApp")
