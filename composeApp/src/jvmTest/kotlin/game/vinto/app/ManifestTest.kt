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
}
