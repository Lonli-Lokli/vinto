rootProject.name = "vinto-kmp"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

// Only the modules that exist today. The rest of the layout in
// openspec/changes/migrate-to-kotlin-multiplatform/design.md (D1) is added as it is ported.
include(":shared:shapes")
include(":worker")
