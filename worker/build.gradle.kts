plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    /**
     * `@JsExport` is what this whole module is: the Kotlin room, named so a Worker can call
     * it. Kotlin still marks the annotation experimental, so every one of the ~50 exports
     * printed the same opt-in warning on every compile — 41 from `Exports.kt` alone.
     *
     * Opting in once says the same thing the code already says. The point is the one made
     * for `-Xexpect-actual-classes` in the root build: a warning nobody can act on is a
     * warning everybody learns to scroll past, and the next real one scrolls past with it.
     */
    compilerOptions {
        optIn.add("kotlin.js.ExperimentalJsExport")
    }

    js {
        // executable() + production webpack is what a Cloudflare Worker script would
        // actually contain, so this is the artefact the platform gate measures.
        binaries.executable()
        nodejs()
        useEsModules()
    }

    sourceSets {
        jsMain.dependencies {
            implementation(project(":shared:shapes"))
            implementation(project(":shared:engine"))
            implementation(project(":shared:bot"))
            implementation(project(":shared:protocol"))
            implementation(project(":shared:room"))
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
