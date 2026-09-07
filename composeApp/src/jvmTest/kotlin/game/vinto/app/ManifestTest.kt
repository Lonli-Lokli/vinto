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
    /**
     * The Compose view on iOS gets the WHOLE screen, and insets it once.
     *
     * SwiftUI lays a `UIViewControllerRepresentable` out INSIDE the container safe area unless
     * told otherwise, and `App.kt` already pads by `WindowInsets.safeDrawing`. Respecting the
     * safe area on both sides applies it twice: measured against the same screen on Android,
     * the iPhone spent 79pt above the wordmark where Android spent 18 — about 61pt of dead
     * felt on every screen, on the platform with the least of it to spare. It also loses the
     * effect `App.kt` describes, where the rail is painted BEHIND the bars so they read as the
     * edge of the table rather than a border around it: what sat behind the status bar was the
     * window's own black, because the app was not drawing there at all.
     *
     * `.ignoresSafeArea(.keyboard)` alone is the JetBrains template's line and is what this
     * project shipped. It is right about the keyboard — Compose has its own handler — and says
     * nothing about the top, which is the half that mattered.
     *
     * Asserted on the Swift source because nothing else can see it: no Compose test has a
     * SwiftUI parent, and the whole defect lives in how that parent laid its child out.
     */
    @Test
    fun theIosHostHandsComposeTheWholeScreenRatherThanTheSafeAreaOnly() {
        val file = File("../iosApp/iosApp/ContentView.swift")
        assertTrue(file.exists(), "ContentView.swift moved; this test is stale")
        val swift = file.readText()
        assertTrue(
            swift.contains("ComposeView()"),
            "ContentView no longer hosts ComposeView; this test is stale",
        )

        // `.ignoresSafeArea()`, `(.all)` and `(.container...)` all cover the top edge; the
        // keyboard-only form does not, and is precisely the line that caused the double inset.
        val allEdges = Regex("\\.ignoresSafeArea\\(\\s*(\\)|\\.all|\\.container)")
            .containsMatchIn(swift)
        assertTrue(
            allEdges,
            "the iOS host respects the container safe area, so Compose is inset twice — " +
                "about 61pt of dead space above every screen (see App.kt's safeDrawing padding)",
        )
    }
}
