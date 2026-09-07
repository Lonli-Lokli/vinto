package game.vinto.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The outside payment link never reaches a build that is in a store.
 *
 * Sending a player to a web page to pay the developer is **App Store 3.1.1** — permitted for
 * approved charities, and not for the rest of us — and Google's Payments policy says the same.
 * It is not a small refusal either: it risks the submission rather than the button, which is why
 * the phones get a fixed-price in-app purchase and the web and desktop get the page.
 *
 * The split lives in the `expect`/`actual` boundary, and this is what holds it. A comment saying
 * "do not use this on Android" is a comment somebody edits past at the end of a long afternoon;
 * a test that reads the source and names the file is not. It is the same shape as
 * `CardCopyIsTranslatedTest` and `PartialFunctionTest`, which both scan sources for the thing
 * that must not be there.
 *
 * Deliberately matched on the **host** rather than the whole address: a change of handle or a
 * tracking parameter must not open a hole in this, and any page on that host is the same policy
 * problem whatever its path.
 */
class SupportLinkTest {

    private val sources = File("src")

    /** Every actual that ships inside a store submission. */
    private val storeBuilds = listOf(
        "androidMain/kotlin/game/vinto/app/Support.android.kt",
        "iosMain/kotlin/game/vinto/app/Support.ios.kt",
    )

    @Test
    fun theStoreBuildsNeverMentionTheOutsidePaymentHost() {
        val host = Pages.SUPPORT.removePrefix("https://").substringBefore('/')
        assertTrue(host.isNotBlank(), "the support address has no host: ${Pages.SUPPORT}")

        val offenders = storeBuilds.filter { path ->
            val file = File(sources, path)
            // A missing file is a failure, not a pass: this test is worthless if it silently
            // stops looking because somebody renamed the actual it was watching.
            assertTrue(file.exists(), "no $path to check — has the actual moved?")
            file.readText().contains(host)
        }

        assertEquals(
            emptyList(),
            offenders,
            "an outside payment link reached a store build. On a phone that is App Store 3.1.1 " +
                "and Google's Payments policy, and it risks the submission — the phones sell a " +
                "fixed-price in-app purchase instead (see Support.kt)",
        )
    }

    /**
     * Stronger than the host check: a store build must not produce a link **at all**.
     *
     * Matching one address catches the mistake of pasting this one into a phone actual, and
     * misses the shape of it — an actual returning [Support.Elsewhere] with some other address
     * is the same policy problem wearing a different domain. Since the header control and the
     * settings row both draw whatever the offer carries, the guarantee worth holding is that
     * the phones never return that case in the first place.
     */
    @Test
    fun aStoreBuildNeverOffersAnOutsideLinkAtAll() {
        val offenders = storeBuilds.filter { path ->
            val file = File(sources, path)
            assertTrue(file.exists(), "no $path to check — has the actual moved?")
            // The type name, not a URL: whatever address it carried, constructing this case on
            // a phone is what sends somebody out of the app to pay.
            file.readText().contains("Support.Elsewhere") || file.readText().contains("Elsewhere(")
        }

        assertEquals(
            emptyList(),
            offenders,
            "a store build offers an outside payment link. The phones must answer Unavailable " +
                "until the in-app purchase exists — see Support.kt",
        )
    }

    /**
     * And the two that are in no store still offer it, so the split is a split rather than a
     * feature that quietly went away on every platform at once.
     */
    @Test
    fun theBuildsWithNoStoreDoOfferIt() {
        val host = Pages.SUPPORT.removePrefix("https://").substringBefore('/')
        listOf(
            "wasmJsMain/kotlin/game/vinto/app/Support.wasmJs.kt",
            "jvmMain/kotlin/game/vinto/app/Support.jvm.kt",
        ).forEach { path ->
            val file = File(sources, path)
            assertTrue(file.exists(), "no $path to check — has the actual moved?")
            assertTrue(
                file.readText().contains("Pages.SUPPORT") || file.readText().contains(host),
                "$path stopped offering the page, so nobody can say thanks anywhere",
            )
        }
    }

    /** It is absolute https on the host it claims to be, like every other address in `Pages`. */
    @Test
    fun theAddressIsWellFormed() {
        assertTrue(
            Pages.SUPPORT.startsWith("https://buymeacoffee.com/"),
            "the support address is not the page it is documented as: ${Pages.SUPPORT}",
        )
    }
}
