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

/**
 * Embeds `fixtures/prng/vectors.json` into a generated Kotlin constant for the tests.
 *
 * The parity test has to run on every target, including Kotlin/Native, where there is no
 * filesystem to read a fixture from. The alternative — a hand-copied Kotlin table of the
 * numbers — is exactly what the cross-language contract must not have, because a copy can
 * drift from the file TypeScript reads and the parity check would then pass while lying.
 *
 * So there is still a single shared file. It is the declared input of this task, the JSON
 * text is embedded verbatim (no reformatting, no reinterpretation of the numbers), and the
 * test parses it with the same serializer it always did. Change the fixture and this
 * regenerates; there is no second copy to forget.
 */
abstract class GeneratePrngVectorsSource : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val vectorsFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val json = vectorsFile.get().asFile.readText()

        val escaped = buildString(json.length + 64) {
            for (character in json) {
                when (character) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '$' -> append("\\$")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(character)
                }
            }
        }

        val packageDir = outputDir.get().asFile.resolve("game/vinto/shapes")
        packageDir.mkdirs()
        packageDir.resolve("PrngVectorsFixture.kt").writeText(
            buildString {
                appendLine("// Generated from fixtures/prng/vectors.json — do not edit; edit the fixture.")
                appendLine("// See :shared:shapes:generatePrngVectorsSource in shared/shapes/build.gradle.kts.")
                appendLine("package game.vinto.shapes")
                appendLine()
                appendLine("internal object PrngVectorsFixture {")
                appendLine("    /** The bytes of the shared vector file, verbatim. */")
                appendLine("    const val JSON: String = \"" + escaped + "\"")
                appendLine("}")
            },
        )
    }
}

val generatePrngVectorsSource =
    tasks.register<GeneratePrngVectorsSource>("generatePrngVectorsSource") {
        description = "Embeds the shared PRNG vector file so every target can run the parity check."
        // shared/shapes -> kmp -> repo root
        vectorsFile.set(rootProject.layout.projectDirectory.dir("..").file("fixtures/prng/vectors.json"))
        outputDir.set(layout.buildDirectory.dir("generated/prng-vectors/kotlin"))
    }

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
            implementation(libs.kotlinx.serialization.json)
        }
        // Passing the task provider carries the task dependency, so the sources are
        // generated before any target compiles its tests.
        commonTest.get().kotlin.srcDir(generatePrngVectorsSource)
    }
}
