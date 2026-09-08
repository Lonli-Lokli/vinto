package game.vinto.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * One product id, asked of two consoles, declared in two languages — and now held by a test.
 *
 * `Support.kt` and `MONETIZATION.md` both say the same thing in prose: `SUPPORT_PRODUCT` is what
 * the app asks Play Billing and StoreKit for, `vydanne.config.mjs`'s `iaps` is what goes into App
 * Store Connect, and the two must be the same string. Nothing checked it. A drift there is the
 * nastiest kind of release bug because **it does not look like one**: the store answers "no such
 * product", `queryProductDetails` returns nothing, `supportOffer()` reports
 * [Support.Unavailable] — which is exactly what a phone with no network, an unfinished console
 * record or a debug build all report too. The button says "Not available here yet" and is telling
 * the truth about a typo.
 *
 * So this reads the config the way `SupportLinkTest` reads the platform sources: as text, from
 * the repository, because the thing being checked is that two files agree and neither can import
 * the other.
 *
 * ## What it deliberately does not check
 *
 * The field lengths (30 / 45), which `vydanne iap` already validates against the store's own
 * limits and would be a second copy of somebody else's rule. And the Play half, which has no
 * declaration to read: vydanne does not touch Play's `inappproducts` API, so that id is typed
 * into the Play Console by hand — the reason [theSameIdIsWrittenDownForPlayToo] pins the
 * document that says which string to type.
 */
class SupportProductTest {

    @Test
    fun theAppAsksBothStoresForTheProductTheConsolesAreToldToCreate() {
        val config = repoFile("vydanne.config.mjs").readText()

        val declared = Regex("""productId:\s*'([^']+)'""").findAll(config).map { it.groupValues[1] }.toList()
        assertTrue(
            declared.isNotEmpty(),
            "vydanne.config.mjs declares no iaps — the App Store half of $SUPPORT_PRODUCT is gone",
        )
        assertEquals(
            listOf(SUPPORT_PRODUCT),
            declared,
            "the ids in vydanne.config.mjs are not the ids the app buys. A store answers " +
                "'no such product' for a mismatch, and the app reports it as Unavailable — " +
                "indistinguishable from having no network. See Support.kt",
        )

        // Consumable, and it is not a preference: a non-consumable is owned forever, so a second
        // thank-you would be refused by the store as "already purchased" — a receipt for a thing
        // that does not exist. MONETIZATION.md carries the argument.
        assertTrue(
            Regex("""type:\s*'consumable'""").containsMatchIn(config),
            "the support product is no longer declared consumable, so it can only be given once",
        )
    }

    /**
     * The id Play is told to create, in the one document a person reads before typing it.
     *
     * Play's console record is made by hand — there is no declaration for a test to compare
     * against — so what can be held is that the runbook somebody follows names the id the app
     * actually asks for, rather than one that was right when it was written.
     */
    @Test
    fun theSameIdIsWrittenDownForPlayToo() {
        val doc = repoFile("docs/design/MONETIZATION.md").readText()
        assertTrue(
            doc.contains(SUPPORT_PRODUCT),
            "MONETIZATION.md no longer names $SUPPORT_PRODUCT, and it is what the Play Console " +
                "record is typed from",
        )
    }

    /**
     * A jvmTest runs with `composeApp/` as its working directory — that is why `SupportLinkTest`
     * beside this one can say `File("src")` — so a file at the repository root is one climb up.
     * The fallback is there only so an IDE launched from the root still finds it.
     */
    private fun repoFile(path: String): File {
        val fromModule = File("../$path")
        return if (fromModule.exists()) fromModule else File(path)
    }
}
