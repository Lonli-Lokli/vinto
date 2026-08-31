package game.vinto.app

actual fun platformName(): String = "Web (Kotlin/Wasm)"

actual fun freshSeed(): Long = kotlin.random.Random.Default.nextLong()
