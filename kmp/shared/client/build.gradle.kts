import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidLibrary)
}

/** Same host check as `shared/shapes`; see that file and `docs/kotlin/README.md` §5. */
val isMacOs = System.getProperty("os.name").startsWith("Mac", ignoreCase = true)

if (!isMacOs) {
    logger.warn(
        "shared:client — building WITHOUT the iOS targets on ${System.getProperty("os.name")}. " +
            "Apple targets require macOS; shared-code breakage on iOS will not surface here.",
    )
}

/**
 * A session test plays a *whole round*, and a round is a hundred-odd MCTS searches — up to
 * 1.6 s each on the slower targets (`docs/kotlin/PLATFORM-GATE.md` 2a.1b). Mocha's two-second
 * default fails those on wall clock while the code is perfectly correct, which is the kind of
 * red that teaches people to re-run rather than to look.
 */
val SLOW_TEST_TIMEOUT = "600s"

kotlin {
    jvm()

    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    js(IR) {
        binaries.library()
        nodejs {
            testTask {
                useMocha { timeout = SLOW_TEST_TIMEOUT }
            }
        }
        useEsModules()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        binaries.library()
        nodejs {
            testTask {
                useMocha { timeout = SLOW_TEST_TIMEOUT }
            }
        }
    }

    if (isMacOs) {
        iosArm64()
        iosSimulatorArm64()
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":shared:shapes"))
            api(project(":shared:engine"))
            api(project(":shared:bot"))
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

android {
    namespace = "game.vinto.client"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.androidMinSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

/**
 * `NoNetworkGuardTest` installs a `SecurityManager` to intercept every route to the network,
 * and JDK 17 refuses to install one at runtime unless the JVM was started expecting it.
 */
tasks.named<Test>("jvmTest") {
    jvmArgs("-Djava.security.manager=allow")
}
