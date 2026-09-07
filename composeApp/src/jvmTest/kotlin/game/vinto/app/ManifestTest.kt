package game.vinto.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The Android app asks for the network, and for nothing else.
 *
 * It asked for nothing at all, for the life of the branch. Android then refuses every socket
 * the process opens — so online play could not work, and **no crash report could ever leave
 * the device**: the handler fired, the envelope was built, and the platform denied the POST.
 * Two failures wearing one face, from a line nobody had written.
 *
 * Nothing in the Kotlin build could have caught it. `assembleDebug` produces a well-formed
 * APK, every JVM suite passes, and the Compose tests run in a process that has no permission
 * model at all. It took a phone, and then it took looking at the merged manifest.
 *
 * The second half of this — that the list stays *short* — matters as much as the first. A
 * permission is a question asked of a player, and this game has no business asking any of the
 * ones below.
 */
class ManifestTest {

    private fun manifest(): String {
        val file = File("../androidApp/src/main/AndroidManifest.xml")
        assertTrue(file.exists(), "the Android manifest moved; this test is stale")
        return file.readText()
    }

    @Test
    fun theAppMayReachTheNetwork() {
        assertTrue(
            manifest().contains("android.permission.INTERNET"),
            "no INTERNET permission: online play cannot work and no crash can be reported",
        )
    }

    /** And asks for nothing a card game has no business asking for. */
    @Test
    fun andAsksForNothingElseWorthAsking() {
        val text = manifest()
        listOf(
            "CAMERA",
            "RECORD_AUDIO",
            "ACCESS_FINE_LOCATION",
            "ACCESS_COARSE_LOCATION",
            "READ_CONTACTS",
            "READ_PHONE_STATE",
            "AD_ID",
        ).forEach { unwanted ->
            assertTrue(
                !text.contains(unwanted),
                "the manifest asks for $unwanted, which this game has no use for",
            )
        }
    }

    /**
     * The one activity is `singleTop`, so a second intent reaches the app that is already open.
     *
     * Without it the launch mode is `standard`, and a launch that carries
     * `FLAG_ACTIVITY_NEW_TASK` — which is every `am start`, and how a link handler starts an app
     * that is already running — brings the existing task to the front and **discards the intent**.
     * `onNewIntent` is then never called, so everything hanging off it is dead code: the second
     * invitation of an evening does nothing at all, which is exactly the case
     * `MainActivity.onNewIntent` was written for and could never have handled.
     *
     * It was found through the store captures, which drive one scene per `am start` and came out
     * as six photographs of the home screen. That is the same defect wearing a harmless face:
     * `CaptureHandleTest` proves the app understands every scene id, and nothing proved the id
     * ever arrived. This is the missing half, and it is asserted on the manifest rather than in
     * Compose because no test with a composition in it can see a launch mode.
     */
    @Test
    fun theActivityIsSingleTopSoASecondIntentIsDeliveredRatherThanDropped() {
        val text = manifest()
        val activity = text.substringAfter("android:name=\".MainActivity\"", "")
            .substringBefore("</activity>")
        assertTrue(activity.isNotBlank(), "MainActivity is not in the manifest; this test is stale")

        assertTrue(
            activity.contains("android:launchMode=\"singleTop\""),
            "MainActivity is not singleTop, so Android drops an intent aimed at the running app: " +
                "a second invite link does nothing, and a capture run photographs one screen six times",
        )
    }
}
