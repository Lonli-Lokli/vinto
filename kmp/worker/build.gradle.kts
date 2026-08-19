plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    js(IR) {
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
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
