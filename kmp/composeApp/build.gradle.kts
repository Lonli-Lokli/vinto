import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

/**
 * Apple targets only exist on macOS — Kotlin/Native cannot build them elsewhere. Same host
 * check as `shared/shapes`; see that file and `docs/kotlin/README.md` §5.
 */
val isMacOs = System.getProperty("os.name").startsWith("Mac", ignoreCase = true)

if (!isMacOs) {
    logger.warn(
        "composeApp — building WITHOUT the iOS targets on ${System.getProperty("os.name")}. " +
            "Android and web are unaffected; the iosApp Xcode project cannot be built here.",
    )
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }

    if (isMacOs) {
        // A static framework is what `iosApp` embeds. `baseName` is the module name Swift
        // imports, so changing it means editing the Xcode project too.
        listOf(iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
            iosTarget.binaries.framework {
                baseName = "ComposeApp"
                isStatic = true
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
        }
    }
}

android {
    namespace = "game.vinto.app"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    defaultConfig {
        applicationId = "game.vinto.app"
        minSdk = libs.versions.androidMinSdk.get().toInt()
        targetSdk = libs.versions.androidTargetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        // No shrinking yet: there is no release pipeline until phase 8, and enabling R8
        // now would mean maintaining keep rules for code that is still being ported.
        getByName("release") {
            isMinifyEnabled = false
        }
    }
}
