package game.vinto.app.crash

/**
 * The `android.os.Build` fields that say whether a phone is an emulator, as data rather than as
 * seven arguments, so the decision is a function tested against strings read off real devices
 * (`EmulatedDeviceTest`) rather than against whichever device happens to be plugged in.
 *
 * In common code although only Android asks, because the tests that hold it run on the JVM. The
 * rules are games-core's (`games.core.telemetry.isEmulatorBuild`), copied rather than imported:
 * this repository does not depend on games-core, and the portfolio's rule that nothing reports
 * from an emulator is the same rule here.
 */
internal data class AndroidBuild(
    val fingerprint: String,
    val model: String,
    val product: String,
    val device: String,
    val hardware: String,
    val brand: String,
    val manufacturer: String,
)

/**
 * **`FINGERPRINT.startsWith(GENERIC)` is the check everyone copies, and it does not work.**
 *
 * The emulator the portfolio's store captures run on reports
 * `google/sdk_gphone64_arm64/emu64a:14/UE1A.230829.050/12077443:userdebug/dev-keys`, which
 * does not begin with GENERIC and would pass straight through. So three independent
 * questions are asked and any yes is taken. The same emulator answers all three, and that
 * redundancy is the point: Google has changed the shape of these strings before, and nobody
 * notices the day one of them stops matching.
 */
internal fun isEmulatorBuild(build: AndroidBuild): Boolean =
    build.hasEmulatorHardware() || build.hasEmulatorFingerprint() || build.hasEmulatorName()

/**
 * The machine underneath. `ranchu` is the current QEMU board, `goldfish` the one before it, and
 * `cutf`/`vsoc` are Cuttlefish, which is what a CI farm runs.
 */
private fun AndroidBuild.hasEmulatorHardware(): Boolean {
    val board = hardware.lowercase()
    return board == "goldfish" || board == "ranchu" || board.startsWith("cutf") || board.startsWith("vsoc")
}

private fun AndroidBuild.hasEmulatorFingerprint(): Boolean =
    fingerprint.startsWith(GENERIC) || fingerprint.startsWith("unknown") ||
        fingerprint.contains(VBOX) || fingerprint.contains("emulator")

/**
 * What it calls itself. Anchored rather than loose (`startsWith`, or a token joined by `_`), so a
 * real phone whose codename happens to contain "sdk" or "emu" keeps reporting.
 */
private fun AndroidBuild.hasEmulatorName(): Boolean =
    model.startsWith("sdk_") || model.contains("Emulator") ||
        model.contains("Android SDK built for") || model.contains("google_sdk") ||
        product.startsWith("sdk") || product.contains("_sdk") || product.contains("sdk_") ||
        product.contains(VBOX) ||
        device.startsWith("emu") || device.startsWith(GENERIC) || device.startsWith(VBOX) ||
        brand.startsWith(GENERIC) ||
        manufacturer.contains("Genymotion") || manufacturer.contains("unknown")

/** Prefixes and tokens more than one field uses. */
private const val GENERIC = "generic"
private const val VBOX = "vbox"
