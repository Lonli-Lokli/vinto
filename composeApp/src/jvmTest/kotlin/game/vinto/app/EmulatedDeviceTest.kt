package game.vinto.app

import game.vinto.app.crash.AndroidBuild
import game.vinto.app.crash.Crashes
import game.vinto.app.crash.isEmulatorBuild
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Nothing reports from an emulator or a simulator.
 *
 * Vinto 1.1 (656) built its DSN into every build and asked only whether the build was a debug
 * one, so a release build on an emulator, and every store capture, reported as a player would.
 * The portfolio rule is that none of them reports at all; `Crashes.install` now asks first.
 *
 * Every Android string here was read off a device by games-core (`EmulatedDeviceTest` there),
 * whose rules these are, because a filter tested against strings somebody imagined passes its
 * own imagination. The iOS answer is one environment variable and needs no table.
 */
class EmulatedDeviceTest {

    @AfterTest
    fun forget() = Crashes.forget()

    /** `adb shell getprop` on an Android 14 arm64 emulator, the one the store captures run on. */
    @Test
    fun theEmulatorTheStoreCapturesRunOnIsCaught() {
        val studio = AndroidBuild(
            fingerprint = "google/sdk_gphone64_arm64/emu64a:14/UE1A.230829.050/12077443:userdebug/dev-keys",
            model = "sdk_gphone64_arm64",
            product = "sdk_gphone64_arm64",
            device = "emu64a",
            hardware = "ranchu",
            brand = "google",
            manufacturer = "Google",
        )
        assertTrue(isEmulatorBuild(studio), "the studio emulator must never reach Sentry")
        assertFalse(
            studio.fingerprint.startsWith("generic"),
            "the famous one-line check would have caught this, and it is why the rules are three",
        )
    }

    @Test
    fun genymotionAndCuttlefishAreCaughtToo() {
        val genymotion = AndroidBuild(
            fingerprint = "generic/vbox86p/vbox86p:8.1.0/OPM6.171019.030.K1/genymotion:userdebug/test-keys",
            model = "Samsung Galaxy S8",
            product = "vbox86p",
            device = "vbox86p",
            hardware = "vbox86",
            brand = "generic",
            manufacturer = "Genymotion",
        )
        val cuttlefish = AndroidBuild(
            fingerprint = "google/cf_x86_64_phone/vsoc_x86_64:13/TQ3A.230605.012/10161052:userdebug/dev-keys",
            model = "Cuttlefish x86_64 phone",
            product = "cf_x86_64_phone",
            device = "vsoc_x86_64",
            hardware = "cutf_cvm",
            brand = "google",
            manufacturer = "Google",
        )
        assertTrue(isEmulatorBuild(genymotion), "Genymotion names itself after a real phone")
        assertTrue(isEmulatorBuild(cuttlefish), "Cuttlefish is what a CI farm runs")
    }

    /** And a phone in somebody's hand still reports, codenames that contain "sdk" or "emu" included. */
    @Test
    fun realPhonesStillReport() {
        listOf(
            AndroidBuild(
                fingerprint = "google/shiba/shiba:14/AP2A.240905.003/12231197:user/release-keys",
                model = "Pixel 8",
                product = "shiba",
                device = "shiba",
                hardware = "shiba",
                brand = "google",
                manufacturer = "Google",
            ),
            AndroidBuild(
                fingerprint = "samsung/dm3qxeea/dm3q:14/UP1A.231005.007/S918BXXS4CXH3:user/release-keys",
                model = "SM-S918B",
                product = "dm3qxeea",
                device = "dm3q",
                hardware = "qcom",
                brand = "samsung",
                manufacturer = "samsung",
            ),
            AndroidBuild(
                fingerprint = "asus/EU_AI2205/AI2205:13/TKQ1.220829.002/33.0210.0210.180:user/release-keys",
                model = "ASUS_AI2205_C",
                product = "EU_AI2205",
                device = "AI2205",
                hardware = "qcom",
                brand = "asus",
                manufacturer = "asus",
            ),
        ).forEach { phone ->
            assertFalse(isEmulatorBuild(phone), "${phone.model} is a player's phone")
        }
    }

    /**
     * The reporter itself, with a DSN that would report: on an emulator it arms no handler, so a
     * crash there goes to the platform and nowhere else.
     */
    @Test
    fun anEmulatorInstallsNoReporter() {
        val before = Thread.getDefaultUncaughtExceptionHandler()
        Crashes.install(
            scope = CoroutineScope(Dispatchers.Unconfined),
            vault = null,
            dsn = "https://abc123@o1.ingest.us.sentry.io/456",
            emulated = true,
        )
        assertSame(before, Thread.getDefaultUncaughtExceptionHandler(), "an emulator armed the crash handler")
    }
}
