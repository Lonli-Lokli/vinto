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

    /**
     * The Compose Multiplatform plugin, on a module that composes almost nothing.
     *
     * It is here for **resources**, not for the compiler. `composeApp` declares every string,
     * every card and every portrait under `composeResources/`, and it is the Compose plugin
     * applied to the *packaging* module that collects a dependency's resources into the APK.
     * Without it the build succeeds, the APK is well-formed, and it contains not one of them
     * — the first `Res.string` lookup throws and the app dies on the launcher.
     *
     * That is exactly what happened on the first build after the module split, and nothing
     * caught it: the JVM tests read resources off the classpath, so they passed, and the only
     * thing that can see the gap is an APK on a device.
     */
    alias(libs.plugins.composeMultiplatform)
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
        // The identity both stores know this app by, and the one thing here that can never
        // be changed after a release: Android treats a different applicationId as a different
        // app, so a rename post-launch abandons every install and every review. It is
        // `app.kupalinka.vinto` to sit under the studio's domain, alongside the two hostnames
        // the game already answers on — and it was changed while 9.10 had not shipped, which
        // is the only window in which it is free.
        //
        // `namespace` above is deliberately NOT this. That is the package R and BuildConfig
        // are generated into, it matches the Kotlin source, and AGP has never required the two
        // to agree.
        applicationId = "app.kupalinka.vinto"
        minSdk = libs.versions.androidMinSdk.get().toInt()
        targetSdk = libs.versions.androidTargetSdk.get().toInt()
        // The build number, and it is NEVER hand-edited — see VERSIONING.md. Play refuses an
        // upload whose versionCode does not strictly exceed the last one on the track, and the
        // commit count is monotonic for free, needs no stored state, and gives the same number
        // to the iOS archive built from the same commit.
        //
        // `-PversionCode=` overrides it, which is what a shallow CI checkout needs: counting
        // commits in a truncated clone is not monotonic. A tree with no git at all falls back
        // to 1 rather than failing the build.
        // `providers.exec` rather than a plain `"git".execute()`: the configuration cache is ON
        // in this build (gradle.properties says why), and shelling out at configuration time any
        // other way is a cache violation that fails the build rather than degrading it.
        versionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull()
            ?: runCatching {
                project.providers.exec { commandLine("git", "rev-list", "--count", "HEAD") }
                    .standardOutput.asText.get().trim().toInt()
            }.getOrDefault(1)

        // The human semver, bumped by hand at a release and deliberately NOT synced with iOS.
        // Stores gate uploads on the build number rising within a marketing version; they do not
        // care that two platforms share one, and forcing lockstep would mean burning a version on
        // one platform to match the other.
        versionName = "1.0"
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
        /**
         * R8 is ON, and it halves the download.
         *
         * The note here used to say "no shrinking yet: there is no release pipeline until phase 8,
         * and enabling R8 now would mean maintaining keep rules for code that is still being
         * ported." Both halves have expired — this IS phase 8, and the port finished — so it was
         * measured rather than argued about: **10.30 MB unminified against 5.93 MB with R8**, on
         * the same commit. For a card game that is the difference between a download somebody
         * waits for and one they do not.
         *
         * `proguard-rules.pro` is nearly empty and explains why at length: nothing here resolves a
         * class by name at runtime. Read it before adding a keep.
         *
         * `isShrinkResources` is safe alongside it for a reason worth knowing rather than
         * assuming: it removes unused Android `res/`, and every string, card and portrait this app
         * draws is an *asset* under `assets/composeResources/` instead. Verified on the bundle
         * rather than believed — 19 locale string tables, 19 drawables and 4 fonts are all still
         * inside the minified .aab.
         *
         * **The check R8 needs and a build cannot give it is a device.** Its failures are runtime
         * ones: the build stays green and the app dies on a screen. Nothing here has been run on a
         * phone yet, so installing the release build once and walking a round is the outstanding
         * step — ship-and-operate 3.3 was going to want that anyway.
         */
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug")
        }
    }
}

/**
 * Packaging the Compose resources by hand, because 1.12 does not.
 *
 * Compose Multiplatform 1.12 registers `copyAndroidMainComposeResourcesToAndroidAssets` in a
 * module using `com.android.kotlin.multiplatform.library` and never configures its
 * `outputDirectory`, so the task cannot run: *"property 'outputDirectory' doesn't have a
 * configured value"*. Nothing depends on it, so **the build succeeds and the APK contains not
 * one string, card or portrait** — and the first `Res.string` lookup throws, which is to say
 * the app dies on the launcher.
 *
 * Nothing in this repository could have caught it. The JVM suites read resources off the
 * classpath and pass; `assembleDebug` produces a well-formed APK; `:composeApp:jvmTest` is
 * 124 green. Only installing the thing finds the hole, which is why it took a person opening
 * it on a phone.
 *
 * So the layout the runtime expects — `assets/composeResources/<packageOfResClass>/…` — is
 * assembled here from the resources `composeApp` has already prepared. Delete all of this the
 * day the plugin configures its own task; the check that it is still needed is that the APK
 * contains `assets/composeResources/`.
 */
private val resPackage = "game.vinto.app.art"

/**
 * `Sync`, not `Copy`, and the difference is what keeps a DELETED resource out of the package.
 *
 * A `Copy` only ever adds: anything already sitting in the destination stays there, whatever the
 * source now contains. So a drawable removed from `composeApp/src/commonMain/composeResources/`
 * remains in `androidApp/build/generated/composeAssets/` from the previous build, gets picked up
 * by the asset merge, and **ships**. `Sync` mirrors the source instead, deleting what is no
 * longer in it.
 *
 * This was found rather than foreseen, on the first Play bundle ever built. The four seat
 * portraits had just been replaced — the old ones were derivative of somebody else's characters,
 * which is why they had to go (`brand/avatars/_shared.md`) — and `unzip -l` on the .aab showed
 * `avatar_donatello.png`, `avatar_leonardo.png`, `avatar_michelangelo.png` and
 * `avatar_raphael.png` all still inside it, next to the four replacements. The source tree was
 * clean, every test was green, and the bundle carried the exact files the release existed to
 * remove.
 *
 * Nothing else would have caught it. `git status` is clean, `grep` finds nothing, and the app
 * looks right because the code asks for the new names — the old bytes are simply along for the
 * ride. The only check that sees it is listing the archive, which is why that is now a step in
 * DEPLOYMENT.md rather than a thing somebody thought to do once.
 */
private val composeResourceAssets = tasks.register<Sync>("assembleComposeResourceAssets") {
    dependsOn(":composeApp:prepareComposeResourcesTaskForCommonMain")
    from(
        project(":composeApp").layout.buildDirectory
            .dir("generated/compose/resourceGenerator/preparedResources/commonMain/composeResources"),
    )
    into(layout.buildDirectory.dir("generated/composeAssets/composeResources/$resPackage"))
}

android {
    // A plain path, not a `Provider`: the Android source-set API refuses providers ("it is not
    // possible for Android Studio to determine if the Provider points to a directory that
    // contains generated files"). The ordering is carried by the task dependency below.
    //
    // `directories.add` rather than `srcDir`, which AGP 9 deprecates in favour of the mutable
    // set. Same effect, and one fewer warning on every configuration of this build.
    sourceSets.getByName("main").assets.directories.add(
        layout.buildDirectory.dir("generated/composeAssets").get().asFile.path,
    )
}

/**
 * So the assets exist before anything READS them, which is a wider set of tasks than it looks.
 *
 * This used to match only `merge*Assets`, which is what packages them, and that was enough for
 * every build anybody had run. **`bundleRelease` fails on it**, with two configuration errors
 * rather than a missing file:
 *
 *     Task ':androidApp:lintVitalAnalyzeRelease' uses this output of task
 *     ':androidApp:assembleComposeResourceAssets' without declaring an explicit or implicit
 *     dependency. This can lead to incorrect results being produced…
 *
 * `lintVital` is the release-only lint pass — it does not run for `assembleDebug` or for the JVM
 * suites — so the first thing that ever asked for it was the first Play bundle ever built. It
 * walks the merged asset directory to look for problems in it, which makes it a consumer of the
 * generated directory just as much as the packaging step is, and Gradle's dependency validation
 * refuses to guess the ordering.
 *
 * Matched on the name rather than by wiring the four tasks explicitly, because AGP names them per
 * variant (`lintVitalAnalyzeRelease`, `generateReleaseLintVitalReportModel`, and the plain `lint*`
 * pair) and a list of literals goes stale the first time a build type is added. The whole block
 * disappears the day Compose Multiplatform configures its own task — the header above says how to
 * tell whether that day has come.
 *
 * `ignoreCase` is load-bearing, and getting it wrong fixes exactly half the problem: AGP
 * capitalises the word where it sits mid-name (`generateReleaseLintVitalReportModel`) and not
 * where it starts one (`lintVitalAnalyzeRelease`). A `contains("Lint")` therefore silences one of
 * the two errors and leaves the other, which reads as the fix not having worked at all.
 */
tasks.matching {
    (it.name.startsWith("merge") && it.name.endsWith("Assets")) ||
        it.name.contains("lint", ignoreCase = true)
}.configureEach {
    dependsOn(composeResourceAssets)
}
