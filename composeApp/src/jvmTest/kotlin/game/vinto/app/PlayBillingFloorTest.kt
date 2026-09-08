package game.vinto.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The Play Billing Library is new enough for Play to accept the upload.
 *
 * Google enforces a **floor** on this one, and it enforces it at the door rather than in the
 * build: a bundle built against too old a library uploads, validates, and is then refused by
 * `edits.commit` with a 403 — "Artifact with version code N uses Play Billing Library version
 * 7.1.1 and must update to at least version 8.0.0". Nothing on this machine knows that, so the
 * cost of the old pin is a signed release bundle, a track upload, and the round trip to find out.
 *
 * That is exactly what happened to build 501. The pin was 7.1.1 — current when the support
 * purchase was written, and past its deadline by the time the build went out.
 *
 * **The floor moves.** Google raises it roughly yearly and announces it in the billing
 * deprecation FAQ; this test is a tripwire for the version we know about rather than a
 * prediction. When Play refuses an upload naming a higher number, raise [FLOOR] to it and the
 * message here will say why it moved.
 */
class PlayBillingFloorTest {

    @Test
    fun theBillingLibraryIsAtOrAboveThePlayFloor() {
        val catalog = File("../gradle/libs.versions.toml")
        assertTrue(catalog.exists(), "the version catalog moved; this test is stale")

        val pinned = Regex("""^playBilling\s*=\s*"([^"]+)"""", RegexOption.MULTILINE)
            .find(catalog.readText())
            ?.groupValues
            ?.get(1)
        assertTrue(pinned != null, "no `playBilling` in the version catalog; this test is stale")

        assertTrue(
            atLeast(pinned, FLOOR),
            "Play Billing is pinned at $pinned and Play refuses anything below $FLOOR — an " +
                "upload gets as far as edits.commit and comes back 403. Raise `playBilling` in " +
                "gradle/libs.versions.toml.",
        )
    }

    /** Numeric compare, segment by segment, so "10.0.0" is above "9.1.0" rather than below it. */
    private fun atLeast(version: String, floor: String): Boolean {
        val a = version.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val b = floor.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        repeat(maxOf(a.size, b.size)) { i ->
            val l = a.getOrElse(i) { 0 }
            val r = b.getOrElse(i) { 0 }
            if (l != r) return l > r
        }
        return true
    }

    private companion object {
        /** What Play refused build 501 for, in as many words. */
        const val FLOOR = "8.0.0"
    }
}
