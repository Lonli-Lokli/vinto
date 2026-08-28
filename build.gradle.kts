plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.detekt)
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

    detekt {
        buildUponDefaultConfig = true
        allRules = false
        config.setFrom(rootProject.files("config/detekt/detekt.yml"))
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
allprojects {
    tasks.withType<Test>().configureEach {
        systemProperty("vinto.fixtures", rootProject.layout.projectDirectory.dir("fixtures").asFile.absolutePath)
        systemProperty("java.awt.headless", "true")
        testLogging {
            events("failed")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            showStackTraces = true
        }
    }
}
