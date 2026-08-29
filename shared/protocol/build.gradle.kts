plugins {
    id("vinto.kmp.library")
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // `api`, because a protocol module that hides the types its messages are made of
            // would force every consumer to re-import both anyway.
            api(project(":shared:shapes"))
            api(project(":shared:engine"))
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.serialization.json)
        }
    }
}

android {
    namespace = "game.vinto.protocol"
}
