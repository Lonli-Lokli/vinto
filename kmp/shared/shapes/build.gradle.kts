plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    // jvm  — tests and tooling only; there is no JVM server (see design D1).
    // js   — the Cloudflare Worker bundle.
    // Android/iOS/wasmJs targets are added when the port reaches them; iOS cannot be
    // built on Windows, so those targets stay behind a host check when introduced.
    jvm()

    js(IR) {
        binaries.library()
        nodejs()
        useEsModules()
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
