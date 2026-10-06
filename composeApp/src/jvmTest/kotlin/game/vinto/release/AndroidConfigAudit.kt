// A COPY of games-core's `games.core.release.AndroidConfigAudit`
// (gulnya: src/desktopMain/kotlin/games/core/release/AndroidConfigAudit.kt, as of b2fb1f6, 2026-10-05).
// Vinto does not depend on games-core and must build without ../gulnya checked out, so the audit every
// other game imports is carried here by hand. KEEP THE TWO IN STEP: change games-core's first, then bring
// the change here, so Vinto's release is held to the same rules as the other four. The checks and their
// messages are the same; what differs is the package, this header, and the layout Vinto's detekt asks
// for (110 columns, constants first, wrapped call chains, and the R8 checks in three small functions).
package game.vinto.release

import java.io.File

/**
 * **The shipped Android configuration, asserted rather than assumed.**
 *
 * These obligations live entirely in build files and a manifest, which is exactly where nothing
 * looks at them. [complaints] checks every one, so a game that calls it — and every game's
 * `AndroidReleaseConfigTest` does — inherits each one, including a game that does not exist yet:
 *
 * - **java.time on Android 7.** minSdk is 24 and `java.time` arrived in API 26, so without core
 *   library desugaring the first `LocalDate` crashes the app on every launch. See
 *   [desugaringComplaints]; Niva 1.1 and Vodar 1.1 shipped exactly this.
 * - **DEX optimization.** From February 2027 Google Play requires "a minimum of 25% coverage across
 *   optimization, shrinking, and obfuscation using a tool such as R8". Every app here clears that
 *   comfortably today, and the risk is not the current state but a plausible future edit: a
 *   `-dontobfuscate` added to make a crash report readable, or `isMinifyEnabled = false` to chase a
 *   bug at 2am, ships green and turns a passing gate into a failing one months later. This does not
 *   *measure* the coverage — only Play Console's per-bundle DEX insight does — it asserts that the
 *   machine which produces it is still switched on.
 * - **The new-phone journey.** These are paid offline games whose entire account is one
 *   SharedPreferences file, so Auto Backup and device-to-device transfer are the whole story, and a
 *   backup rule that names a file the app no longer writes backs up nothing at all. Silently. The
 *   player finds out on the one day it matters and there is no recovering it.
 *
 * A test reads files rather than behaviour, which is unusual here and is the point: no rendered
 * frame, engine pin or contrast audit can see any of this.
 *
 * **What this cannot see.** It reads the build file, not the artifact: it proves desugaring is
 * switched on, not that every API the app reaches exists on an API 24 phone. Nothing that runs on a
 * JVM can prove that — a desktop test has `java.time`, `java.nio.file` and a full `Base64` whether or
 * not an Android 7 phone does. And Gradle does not know these files are test inputs, so a re-run
 * after editing only a build file can be served UP-TO-DATE; a release must run this test with
 * `--rerun`, or clean.
 */
object AndroidConfigAudit {

    /** The first Android API level with `java.time` built in. */
    private const val JAVA_TIME_API = 26

    /**
     * Everything wrong with [module]'s Android configuration, empty when it is right.
     *
     * [module] is the app module directory (`composeApp`, or `androidApp` in Vinto), [prefsName] the
     * SharedPreferences file the game installs, without the `.xml`. [manifestDir] is where the
     * manifest and `res/` live under the module — `src/androidMain` for a Compose Multiplatform app
     * module, `src/main` for a plain Android one.
     */
    fun complaints(module: File, prefsName: String, manifestDir: String = "src/androidMain"): List<String> {
        val out = mutableListOf<String>()
        out += desugaringComplaints(module)
        out += dexComplaints(module)
        out += backupComplaints(module, prefsName, manifestDir)
        out += AndroidPlatformAudit.complaints(module, manifestDir)
        return out
    }

    // ---- java.time --------------------------------------------------------------------------

    /**
     * Everything wrong with [module]'s core library desugaring, empty when it is set up — or
     * unneeded because `androidMinSdk` is already 26 or higher.
     *
     * kotlinx-datetime, which `DailySeed` is built on, IS `java.time` on Android, and `java.time`
     * only exists from API 26. Every game here has minSdk 24, so on Android 7.0 and 7.1 the first
     * `LocalDate` throws `NoClassDefFoundError: Ljava/time/LocalDate;` — and in `DailySeed.EPOCH`
     * that is a static initialiser, so it is a launch crash, on every start, with nothing to do
     * about it. Niva shipped exactly that in 1.1 (Sentry 7769930732, a Galaxy J2 Pro on Android
     * 7.1.1) and no test noticed, because every test runs on a JVM that has `java.time`.
     *
     * This reads the build file rather than the dex, like the rest of this audit. A game with no
     * kotlinx-datetime dependency of its own still pulls it in through games-core, so the check
     * does not try to prove the dependency is present — it asks for the desugaring regardless.
     */
    fun desugaringComplaints(module: File): List<String> {
        val buildFile = File(module, "build.gradle.kts")
        if (!buildFile.isFile) return listOf("${buildFile.path} is missing")

        val minSdk = minSdkOf(module)
        if (minSdk != null && minSdk >= JAVA_TIME_API) return emptyList()

        val source = stripComments(buildFile.readText())
        val out = mutableListOf<String>()
        if (!source.contains("isCoreLibraryDesugaringEnabled = true")) {
            out += "${buildFile.path} does not set isCoreLibraryDesugaringEnabled = true under " +
                "compileOptions — minSdk is ${minSdk ?: "unknown"}, java.time needs API " +
                "$JAVA_TIME_API, and kotlinx-datetime (so DailySeed, so the first frame) crashes with " +
                "NoClassDefFoundError: Ljava/time/LocalDate; on Android 7.0 and 7.1"
        }
        if (!source.contains("coreLibraryDesugaring(")) {
            out += "${buildFile.path} has no coreLibraryDesugaring(…) dependency — the flag alone " +
                "does nothing without com.android.tools:desugar_jdk_libs"
        }
        return out
    }

    /** `androidMinSdk` from the repo's version catalog, one level above the app module, or null. */
    private fun minSdkOf(module: File): Int? {
        val catalog = File(module.absoluteFile.parentFile, "gradle/libs.versions.toml")
        if (!catalog.isFile) return null
        return Regex("""androidMinSdk\s*=\s*"(\d+)"""")
            .find(catalog.readText())
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
    }

    // ---- R8 ---------------------------------------------------------------------------------

    private fun dexComplaints(module: File): List<String> {
        val buildFile = File(module, "build.gradle.kts")
        if (!buildFile.isFile) return listOf("${buildFile.path} is missing")
        return releaseBuildTypeComplaints(buildFile) +
            proguardRulesComplaints(module) +
            r8ModeComplaints(module)
    }

    private fun releaseBuildTypeComplaints(buildFile: File): List<String> {
        val out = mutableListOf<String>()
        val release = releaseBlock(stripComments(buildFile.readText()))
        if (release == null) {
            out += "no `getByName(\"release\")` build type in ${buildFile.path}"
        } else {
            if (!release.contains("isMinifyEnabled = true")) {
                out += "the release build type does not set isMinifyEnabled = true — R8 is off, and " +
                    "Play requires 25% DEX optimization coverage from February 2027"
            }
            if (!release.contains("isShrinkResources = true")) {
                out += "the release build type does not set isShrinkResources = true"
            }
            if (!release.contains("proguard-android-optimize.txt")) {
                out += "the release build type does not use proguard-android-optimize.txt — the " +
                    "plain proguard-android.txt carries -dontoptimize, which is the half of the " +
                    "requirement it would silently drop"
            }
        }
        return out
    }

    private fun proguardRulesComplaints(module: File): List<String> {
        val out = mutableListOf<String>()
        val rules = File(module, "proguard-rules.pro")
        if (rules.isFile) {
            val text = stripComments(rules.readText(), "#")
            for (kill in listOf("-dontobfuscate", "-dontoptimize", "-dontshrink")) {
                if (text.contains(kill)) {
                    out += "${rules.path} contains $kill, which removes one of the three things " +
                        "Play measures coverage across"
                }
            }
        }
        return out
    }

    private fun r8ModeComplaints(module: File): List<String> {
        val out = mutableListOf<String>()
        // gradle.properties sits at the repo root, one level above the app module.
        val properties = File(module.absoluteFile.parentFile, "gradle.properties")
        if (properties.isFile) {
            val text = stripComments(properties.readText(), "#")
            if (Regex("""android\.enableR8\.fullMode\s*=\s*false""").containsMatchIn(text)) {
                out += "gradle.properties disables R8 full mode"
            }
        }
        return out
    }

    /** From `getByName("release") {` to its matching brace, or null. Counts braces rather than
     *  matching a regex, because the block contains nested ones and a regex would stop at the first. */
    private fun releaseBlock(source: String): String? {
        val start = source.indexOf("getByName(\"release\")")
        if (start < 0) return null
        val open = source.indexOf('{', start)
        if (open < 0) return null
        var depth = 0
        for (i in open until source.length) {
            when (source[i]) {
                '{' -> {
                    depth++
                }
                '}' -> {
                    depth--
                    if (depth == 0) return source.substring(open, i + 1)
                }
            }
        }
        return null
    }

    // ---- backup -----------------------------------------------------------------------------

    private fun backupComplaints(module: File, prefsName: String, manifestDir: String): List<String> {
        val out = mutableListOf<String>()
        val base = File(module, manifestDir)
        val manifest = File(base, "AndroidManifest.xml")
        if (!manifest.isFile) return listOf("${manifest.path} is missing")

        val text = manifest.readText()
        for ((attribute, expected) in listOf(
            "android:allowBackup" to "true",
            "android:dataExtractionRules" to "@xml/backup_rules",
            "android:fullBackupContent" to "@xml/backup_rules_legacy",
        )) {
            if (!text.contains("""$attribute="$expected"""")) {
                out += "the manifest does not declare $attribute=\"$expected\" — without it the app " +
                    "takes Android's default and nobody has decided what moves to a new phone"
            }
        }

        val include = """<include domain="sharedpref" path="$prefsName.xml" />"""
        val rules = File(base, "res/xml/backup_rules.xml")
        if (!rules.isFile) {
            out += "${rules.path} is missing"
        } else {
            val body = rules.readText()
            for (section in listOf("cloud-backup", "device-transfer")) {
                val block = between(body, "<$section>", "</$section>")
                when {
                    block == null -> out += "backup_rules.xml has no <$section> section"
                    !block.contains(include) ->
                        out += "backup_rules.xml <$section> does not include sharedpref " +
                            "\"$prefsName.xml\", which is the file this game actually writes — " +
                            "a rule naming the wrong file backs up nothing and says nothing"
                }
            }
        }

        val legacy = File(base, "res/xml/backup_rules_legacy.xml")
        if (!legacy.isFile) {
            out += "${legacy.path} is missing — minSdk 24 means Android 11 and below read this one " +
                "and ignore data-extraction-rules entirely"
        } else if (!legacy.readText().contains(include)) {
            out += "backup_rules_legacy.xml does not include sharedpref \"$prefsName.xml\""
        }
        return out
    }

    /**
     * Every distinct `PrefsHost.install("…")` argument under [sourceRoot].
     *
     * A game installs its storage name once per platform entry point — Android, iOS and desktop —
     * so there are three literals that have to stay equal to each other and to the backup rules.
     * Test source sets are skipped: they deliberately install a scratch name so a test run cannot
     * touch a developer's real save.
     */
    fun installedPrefsNames(sourceRoot: File): Set<String> {
        if (!sourceRoot.isDirectory) return emptySet()
        val pattern = Regex("""PrefsHost\.install\(\s*"([^"]+)"\s*\)""")
        return sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { it.path.contains("Test/") || it.path.contains("Test.kt") }
            .flatMap { file -> pattern.findAll(file.readText()).map { it.groupValues[1] } }
            .toSet()
    }

    private fun between(text: String, open: String, close: String): String? {
        val a = text.indexOf(open)
        if (a < 0) return null
        val b = text.indexOf(close, a)
        return if (b < 0) null else text.substring(a + open.length, b)
    }

    private fun stripComments(text: String, lineMarker: String = "//"): String =
        text.lineSequence().joinToString("\n") { line ->
            val at = line.indexOf(lineMarker)
            if (at >= 0) line.substring(0, at) else line
        }
}
