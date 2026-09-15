package game.vinto.app

/**
 * The JVM target exists to run the Compose tree in a test harness, not to ship a desktop app.
 * These actuals are here so that source set compiles at all.
 */
actual fun platformName(): String = "JVM ${System.getProperty("java.version")}"

actual fun freshSeed(): Long = java.security.SecureRandom().nextLong()

/**
 * Never. The desktop window `:composeApp:run` opens is a tool for looking at a UI change, and
 * this same target is what the Compose suites run on — so every crash it can produce is ours.
 */
actual fun isReleaseBuild(): Boolean = false
