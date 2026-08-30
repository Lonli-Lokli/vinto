import java.util.Properties

/**
 * The Android application: a manifest, an activity, and the launcher's idea of what this is.
 *
 * It exists because **AGP 9 will not let `com.android.application` share a module with the
 * Kotlin Multiplatform plugin** — "move the usage of 'com.android.application' into a separate
 * subproject", in its own words, with no property to bypass it the way the library plugin has
 * one. So `composeApp` is now a KMP *library* holding the whole UI and every `actual`, and
 * this is the thing Android launches.
 *
 * That turns out to be the arrangement the repository already had on the other side: `iosApp`
 * is a thin Xcode project embedding `composeApp`'s framework, and this is its counterpart.
 * The asymmetry was the accident; the symmetry is the fix.
 *
 * What lives here is only what an *application* is: the manifest, `MainActivity`, the launcher
 * icons and the window theme, the applicationId and version, and the signing config. Every
 * `expect`/`actual` for Android — storage, sockets, sound, haptics, the back button, the crash
 * hook — stays in `composeApp/src/androidMain`, because those are the library's platform half
 * rather than the app's.
 */
plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

// No `org.jetbrains.kotlin.android`: AGP 9 has built-in Kotlin support and refuses the plugin
// outright ("no longer required for Kotlin support since AGP 9.0").

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // The game, and everything under it. `composeApp` brings the Compose UI, the session, the
    // engine and the bots; this module adds an Activity and nothing else.
    implementation(project(":composeApp"))
    implementation(libs.androidx.activity.compose)
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
     * depends only on whether `keystore.properties` at the repository root exists:
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
