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
