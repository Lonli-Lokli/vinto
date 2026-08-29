plugins {
    `kotlin-dsl`
}

// The plugins the conventions themselves apply. These are the Gradle *artifacts* rather than
// the plugin markers the catalog's `[plugins]` block names, because a precompiled script
// plugin needs them on its own compile classpath.
dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
    implementation("com.android.tools.build:gradle:${libs.versions.agp.get()}")
    implementation("org.jetbrains.kotlinx:kover-gradle-plugin:${libs.versions.kover.get()}")
}

kotlin {
    // Matches the toolchain the rest of the build compiles with, so the conventions are not
    // the one thing in the repository built against a different JDK.
    jvmToolchain(17)
}
