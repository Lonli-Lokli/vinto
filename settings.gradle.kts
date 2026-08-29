pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

// Lets Gradle fetch the JDK that the toolchain in `build.gradle.kts` asks for when the
// machine has none. Without it a toolchain is a *requirement* to have JDK 17 installed,
// which would be a stricter rule than the one it replaces rather than a looser one. CI
// installs 17 itself, so nothing is downloaded there.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

// Kept as "vinto-kmp" even though the build is no longer under `kmp/`. The name is not
// cosmetic: Kotlin/JS derives the output bundle from it, so the Worker shim's import of
// `vinto-kmp-worker.mjs` (worker/cloudflare/index.mjs and the gate scripts) is spelled with
// it, and `Recorder.PRODUCER` writes "vinto-kmp/local" into every recording's header.
// Renaming means editing those in the same commit; there is nothing to gain by it.
rootProject.name = "vinto-kmp"

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
