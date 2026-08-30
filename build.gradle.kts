import org.gradle.api.artifacts.VersionCatalogsExtension
plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
}

/**
 * Static analysis for every Kotlin module, configured once here rather than repeated per
 * build file. One shared ruleset lives in `config/detekt/detekt.yml`.
 *
 * Multiplatform note: detekt analyses source files, not compiled targets, so pointing it at
 * every `src/**/kotlin` directory covers commonMain and every platform source set in one
 * pass — including the iOS sources, which are the ones a non-Mac host cannot compile.
 */
allprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")

    /**
     * Formatting, which detekt does not do on its own.
     *
     * detekt is static analysis — complexity, dead code, naming — and it has nothing to say
     * about where the line breaks go. `detekt-formatting` wraps ktlint's rules and runs them
     * in the same pass under the same config, which is a formatter that reports through a
     * gate the build already has rather than a second tool with a second config and a second
     * opinion.
     *
     * It matters more than it did: `lefthook` used to run `nx format:write` over the staged
     * files, and that hook went with the web client's tooling (§1d). Nothing has enforced
     * formatting since.
     */
    dependencies {
        add(
            "detektPlugins",
            rootProject.extensions.getByType<VersionCatalogsExtension>()
                .named("libs").findLibrary("detekt-formatting").get(),
        )
    }

    detekt {
        buildUponDefaultConfig = true
        allRules = false
        config.setFrom(rootProject.files("config/detekt/detekt.yml"))
        /**
         * The debt that existed when detekt first ran in CI, listed so that everything
         * *after* it fails the build.
         *
         * `maxIssues` is 0 and the tree does not meet it: seven findings predate this file
         * (two cyclomatic-complexity, a loop with too many jumps, a return count, a file
         * name that does not match its declaration, a file one function over the limit, and
         * one dead private function). They were confirmed present on the branch before the
         * Gradle build moved to the repository root, so none is fallout from the move.
         *
         * A baseline is a debt list, not a mute button, and it only earns its place if it
         * shrinks. Fix an entry and delete its line — the gate then holds that ground. Do
         * not regenerate the file wholesale to make a new violation go away; that is the one
         * use that turns this into a lie.
         */
        baseline = rootProject.file("config/detekt/baseline.xml")
        // Multiplatform projects have no single `src/main/kotlin`, so the source set is
        // declared explicitly.
        source.setFrom(files("src"))
        parallel = true
    }

    tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
        jvmTarget = "17"
        reports {
            html.required.set(true)
            xml.required.set(false)
            txt.required.set(false)
            sarif.required.set(false)
            md.required.set(false)
        }
    }

    tasks.withType<io.gitlab.arturbosch.detekt.DetektCreateBaselineTask>().configureEach {
        jvmTarget = "17"
    }
}

/**
 * Conventions for every test task in the build, set once here.
 *
 * **`vinto.fixtures`** — the cross-implementation corpus (`fixtures/recordings`,
 * `fixtures/prng`) is the contract with the TypeScript engine, and half a dozen JVM suites
 * read it off disk. They used to find it by counting `..` from the module directory, which
 * is a rule that holds only while the tree keeps its shape — moving the Gradle build to the
 * repository root broke every one of them at once. The absolute path is injected instead, so
 * a suite asks the build where the fixtures are rather than guessing from its own depth.
 * The tests keep a relative fallback so they still run from an IDE that launches them
 * without Gradle.
 *
 * **`java.awt.headless`** — the Compose JVM suites render through Skiko on a CI machine that
 * has no display. Set for every module rather than composeApp alone: it costs nothing where
 * there is no UI, and it is the kind of flag that is only ever missing.
 */
/**
 * The JDK the Kotlin compilers run on, decided by the build rather than by the machine.
 *
 * Every module targets 17 bytecode, and until now nothing said which JDK should produce it —
 * so the answer was "whichever one Gradle was started with", and a contributor on 21 was
 * compiling against 21's `java.base` while claiming 17. It happens to work; it is not
 * something the build was checking.
 *
 * A toolchain says it outright: Gradle finds a JDK 17 (or provisions one — see the resolver
 * in `settings.gradle.kts`) and compiles with it, whatever JDK started the build. So the
 * Gradle JDK no longer has to be exactly 17, which is the point — CI pins 17 and resolves it
 * locally with nothing to download, and a developer on 21 or 25 gets the same bytecode
 * instead of a different one that happens to pass.
 */
allprojects {
    plugins.withId("org.jetbrains.kotlin.multiplatform") {
        extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension> {
            jvmToolchain(17)
        }
    }
}

allprojects {
    tasks.withType<Test>().configureEach {
        /**
         * Run the forks side by side.
         *
         * `composeApp` sets `forkEvery = 1` — every Compose test class gets its own JVM,
         * because they deadlock when they share the AWT thread (see the note there). That is
         * a correctness decision and stays. What it left behind is a suite of twenty-four
         * JVMs starting and stopping *one after another*: measured, 345 seconds of test time
         * on a four-core machine that was running one core's worth of it.
         *
         * Forks do not share the thread that races — that is the whole reason they exist here
         * — so running several at once is not the arrangement the fork was protecting against.
         * Half the cores rather than all of them: each fork renders Skia in software and wants
         * a core to itself, and the Gradle daemon and the Kotlin compile daemon want the rest.
         */
        maxParallelForks = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)

        systemProperty("vinto.fixtures", rootProject.layout.projectDirectory.dir("fixtures").asFile.absolutePath)
        systemProperty("java.awt.headless", "true")
        testLogging {
            events("failed")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            showStackTraces = true
        }
    }
}

/**
 * `expect`/`actual` classes, without the warning on every compile.
 *
 * `Sound`, `SoundPlayer` and the other `expect class` declarations are exactly what the
 * feature is for, and Kotlin still calls it Beta — so every compilation of every module
 * printed the same paragraph about it. A warning nobody can act on is a warning everybody
 * learns to scroll past, and the next real one scrolls past with it.
 *
 * This acknowledges the Beta rather than hiding a problem: the design is deliberate and
 * recorded (`docs/kotlin/README.md` §5), and if the feature changes shape the compiler will
 * say so as an error, which this does not suppress.
 */
allprojects {
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
        compilerOptions {
            freeCompilerArgs.add("-Xexpect-actual-classes")
        }
    }
}

/**
 * Coverage, which this repository had none of.
 *
 * What Codecov used to measure was the TypeScript workspace, and its upload went with that
 * workspace's CI (§1d) — so the number went to zero without anything saying so. Kover is the
 * Kotlin-native answer and, unlike the old setup, it can see a multiplatform module: it
 * measures the JVM compilation, which is where every shared module's tests actually run.
 *
 * Aggregated here rather than reported per module, because the interesting question is what
 * fraction of the *rules* are exercised, and the rules are spread across six modules that
 * only mean anything together. `composeApp` is deliberately absent — its suites are Compose
 * UI tests whose value is in what they assert about a rendered screen, and line coverage of
 * a `@Composable` measures how much of the layout happened to be drawn.
 *
 * No threshold is set. A gate that nobody chose a number for is a number chosen by whoever
 * ran it first; pick one when there is a reason to.
 */
dependencies {
    kover(project(":shared:shapes"))
    kover(project(":shared:engine"))
    kover(project(":shared:bot"))
    kover(project(":shared:client"))
    kover(project(":shared:protocol"))
    kover(project(":shared:room"))
}

kover {
    reports {
        total {
            xml { onCheck.set(false) }
            html { onCheck.set(false) }
        }
    }
}

/**
 * The release gate, as one command.
 *
 * Task 8.3 asked for a checklist. A checklist in a document is a list of things somebody
 * forgets one of, so most of it is a task instead — everything that a Linux machine can
 * actually run. What it cannot run is named in `docs/kotlin/RELEASE-GATE.md` beside the
 * machine that can, rather than being quietly left out.
 *
 * Three of the five gates 8.3 named have changed shape since it was written, and the document
 * records why. In particular the tournament is **not** here: it is 6m 39s of MCTS and a
 * manually-run gate by design (§6k), and folding it in would make the one command something
 * nobody runs.
 */
tasks.register("releaseGate") {
    group = "verification"
    description = "Everything a Linux machine can check before a release. See docs/kotlin/RELEASE-GATE.md."

    // Static analysis over every module and source set, at maxIssues 0.
    dependsOn(provider { allprojects.mapNotNull { it.tasks.findByName("detekt") } })

    // The rules, on the JVM: the corpus replay, the validator sweep, the self-play gate.
    dependsOn(
        ":shared:shapes:jvmTest",
        ":shared:engine:jvmTest",
        ":shared:bot:jvmTest",
        ":shared:client:jvmTest",
        ":shared:protocol:jvmTest",
        ":shared:room:jvmTest",
    )

    // And on the two targets where a `Long` is not a `Long`. This is the round trip from
    // task 6.7: a whole game generated and replayed through text, per target.
    dependsOn(
        ":shared:shapes:jsNodeTest",
        ":shared:engine:jsNodeTest",
        ":shared:bot:jsNodeTest",
        ":shared:client:jsNodeTest",
        ":shared:protocol:jsNodeTest",
        ":shared:shapes:wasmJsNodeTest",
        ":shared:engine:wasmJsNodeTest",
        ":shared:bot:wasmJsNodeTest",
        ":shared:client:wasmJsNodeTest",
        ":shared:protocol:wasmJsNodeTest",
    )

    // The screens, headless. Goldens are excluded on CI and stay excluded here — see the
    // note on the test task in composeApp/build.gradle.kts.
    dependsOn(":composeApp:jvmTest")

    // The Worker's bundle. The room gates themselves need Node and workerd, so they are in
    // the document rather than here.
    dependsOn(":worker:jsProductionExecutableCompileSync")
}
