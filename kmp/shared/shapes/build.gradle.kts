plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

/**
 * Kotlin/Native cannot build Apple targets anywhere except macOS, so they are declared
 * behind a host check. On Windows or Linux the build simply has fewer targets; on macOS
 * the iOS targets appear with no further configuration.
 *
 * Consequence to be aware of: the set of targets differs per machine, so a `commonMain`
 * change that breaks iOS will only surface on a Mac (or on the macOS CI runner). Run
 * `./gradlew build` on macOS before trusting a shared-code change.
 */
val isMacOs = System.getProperty("os.name").startsWith("Mac", ignoreCase = true)

kotlin {
    // jvm  — tests and tooling only; there is no JVM server (see design D1).
    // js   — the Cloudflare Worker bundle.
    jvm()

    js(IR) {
        binaries.library()
        nodejs()
        useEsModules()
    }

    if (isMacOs) {
        iosArm64()
        iosSimulatorArm64()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
