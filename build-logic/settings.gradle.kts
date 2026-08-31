// The build that builds the build.
//
// `build-logic` is an *included* build rather than `buildSrc`, which is the difference
// between a change to a convention invalidating only what depends on it and invalidating
// the whole build's configuration cache.
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    // The same catalog the rest of the repository reads, so a version lives in exactly one
    // file whether it is a module's dependency or a convention's.
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
