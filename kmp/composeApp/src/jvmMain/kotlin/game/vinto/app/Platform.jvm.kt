package game.vinto.app

/**
 * The JVM target exists to run the Compose tree in a test harness, not to ship a desktop app.
 * These actuals are here so that source set compiles at all.
 */
actual fun platformName(): String = "JVM ${System.getProperty("java.version")}"

actual fun freshSeed(): Long = java.security.SecureRandom().nextLong()
