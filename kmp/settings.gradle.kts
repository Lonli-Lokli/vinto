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
include(":worker")
include(":composeApp")
