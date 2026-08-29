import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.kover)
}

/**
 * Server-side code, so server-side targets: JS because Cloudflare is where it runs, and JVM
 * because the JVM is where it can finally be *tested* — the room's rules spent their first
 * months exercised only through wrangler gate scripts, this module is what ends that. No
 * Android, iOS or Wasm: a phone never hosts a room.
 */
kotlin {
    // Pinned like the six modules that have an `androidTarget` to pin it on. This one is
    // server-side and has none, which is the only reason it was the odd module out — not a
    // decision that its bytecode could float with whatever JDK happened to run the build.
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

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
        jvmTest.dependencies {
            // The two-client harness plays real RemoteGameSessions against this room's own
            // entry points — the whole online stack minus the platform, on one JVM.
            implementation(project(":shared:client"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
