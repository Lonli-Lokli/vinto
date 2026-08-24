import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

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
    /**
     * A JVM target that ships nowhere.
     *
     * It exists so the Compose tree can be tested without a device: `runComposeUiTest` renders
     * the real composables headlessly, which catches the failures the presenter tests cannot
     * see — a composition that throws, a screen that draws nothing, a button whose label the
     * player never gets. An emulator would catch those too, on a machine that has one.
     */
    jvm()

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
            implementation(compose.components.resources)

            // The game itself. `shared:client` brings the engine and the bots with it, so a
            // single-player game needs nothing else — no network dependency appears here,
            // and `NoNetworkGuardTest` is what keeps that true.
            implementation(project(":shared:client"))
            implementation(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

/**
 * One JVM per test class.
 *
 * These are Compose *desktop* tests: each one stands up a real Skia surface on the AWT event
 * thread, and they share that thread with each other when they share a JVM. Sharing it turned
 * out to be a coin flip — with the whole suite in one process, a later test would deadlock
 * about two runs in three, the worker waiting inside `EventQueue.invokeAndWait` while the AWT
 * thread sat blocked on a Compose lock, and no test ever failed: the suite simply stopped.
 * Measured at 4 hangs in 6 runs, in a suite that is otherwise green in under half a minute.
 *
 * Forking per class costs a JVM start apiece and buys a suite that finishes every time, which
 * is the only kind that can guard anything. It is not a diagnosis of the underlying race —
 * that is Compose's to answer — it is a refusal to share the thread that races.
 */
tasks.withType<Test>().configureEach {
    forkEvery = 1
}

/**
 * The card art and the four player portraits, shared with the web app they were drawn for
 * (`apps/vinto/src/app/images`). One deck, one set of faces: a second, plainer deck for the
 * Kotlin clients would make them look like a different game rather than the same one.
 */
compose.resources {
    publicResClass = true
    packageOfResClass = "game.vinto.app.art"
    generateResClass = always
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

    /**
     * Signing a release build.
     *
     * Android will not install an unsigned APK, so `assembleRelease` needs a key even when
     * the destination is one developer's own phone. Two paths, and which one is taken
     * depends only on whether `kmp/keystore.properties` exists:
     *
     *   * It does — a real upload key, named by that file (gitignored, and the file names
     *     the keystore rather than containing it). This is what a Play build uses.
     *   * It does not — the debug key, so a release build still assembles and installs on a
     *     machine that has never been given one. Such an APK is a real release build in
     *     every respect except *who* signed it: no debugger, no `debuggable` flag. It just
     *     cannot be published, and cannot be upgraded in place by a properly signed one
     *     later — Android treats a change of signing key as a different app.
     *
     * The fallback is the point. A build that fails on a missing secret makes "put it on my
     * phone" a task with a setup step in front of it, and the release variant then goes
     * untested until the day it has to work.
     */
    val keystore = rootProject.file("keystore.properties").takeIf { it.exists() }?.let { file ->
        Properties().apply { file.inputStream().use(::load) }
    }

    signingConfigs {
        if (keystore != null) {
            create("release") {
                storeFile = rootProject.file(keystore.getProperty("storeFile"))
                storePassword = keystore.getProperty("storePassword")
                keyAlias = keystore.getProperty("keyAlias")
                keyPassword = keystore.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        // No shrinking yet: there is no release pipeline until phase 8, and enabling R8
        // now would mean maintaining keep rules for code that is still being ported.
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug")
        }
    }
}
