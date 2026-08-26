plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

/**
 * Server-side code, so server-side targets: JS because Cloudflare is where it runs, and JVM
 * because the JVM is where it can finally be *tested* — the room's rules spent their first
 * months exercised only through wrangler gate scripts, this module is what ends that. No
 * Android, iOS or Wasm: a phone never hosts a room.
 */
kotlin {
    jvm()

    js(IR) {
        binaries.library()
        nodejs()
        useEsModules()
    }

    sourceSets {
        commonMain.dependencies {
            // The wire types ride through the room's own state, so consumers see them.
            api(project(":shared:protocol"))
            implementation(project(":shared:shapes"))
            implementation(project(":shared:engine"))
            implementation(project(":shared:bot"))
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
