plugins {
    id("vinto.kmp.library")
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":shared:shapes"))
            api(project(":shared:engine"))
            api(project(":shared:bot"))
            api(project(":shared:protocol"))
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


/**
 * `NoNetworkGuardTest` installs a `SecurityManager` to intercept every route to the network,
 * and JDK 17 refuses to install one at runtime unless the JVM was started expecting it.
 */
tasks.named<Test>("jvmTest") {
    jvmArgs("-Djava.security.manager=allow")
}
