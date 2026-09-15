package game.vinto.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Saying thanks a second time, after a first one that Play never heard was finished.
 *
 * **The report**: the button comes back with Play's own dialog — *"Error. You already own this
 * item."* — and it never works again. That is `ITEM_ALREADY_OWNED`, and on a product meant to be
 * repeatable it means exactly one thing: a purchase is sitting on the account unconsumed. Play
 * refuses to sell a one-time product twice while the first one is still owned, so a single
 * leftover locks the button for the life of the install.
 *
 * **Consuming inside the flow is not enough, and that is what this holds.** Play reports a
 * purchase through a listener on the client, whenever it hears about one — which is very often
 * not during the call that started it. A slow payment method lands `PENDING` and turns
 * `PURCHASED` minutes later; the process can die between the payment and the consume; the sheet
 * can be dismissed in ways that report nothing until the next connection. Every one of those
 * leaves a purchase owned and nothing left running to consume it, and the account is stuck from
 * then on. The only way back is to **ask Play what is owned** and consume that, which is a
 * query the client has to make on its own behalf rather than a callback it can wait for.
 *
 * ### What this test can and cannot prove
 *
 * `AndroidBilling` is `androidMain` and its work is Play's, so there is no purchase to fake on
 * the JVM and nothing here replays a billing flow. What it does is the same thing
 * `SupportLinkTest` beside it does: read the actual as text and fail if the structural property
 * has gone. A missing file fails rather than passes, so it cannot quietly stop looking.
 */
class SupportRepeatsTest {

    private val sources = File("src")

    private fun actual(path: String): String {
        val file = File(sources, path)
        assertTrue(file.exists(), "no $path to check — has the actual moved?")
        return file.readText()
    }

    /**
     * The sweep exists at all: something asks Play for the purchases the account already holds.
     *
     * Without it, `consumeAsync` is only ever reached by a purchase that arrives while a `buy()`
     * is still waiting on it — and the leftovers that cause the report are by definition the
     * ones that did not.
     */
    @Test
    fun aPurchaseLeftUnconsumedDoesNotBlockEverySayingOfThanksAfterIt() {
        val billing = actual("androidMain/kotlin/game/vinto/app/AndroidBilling.android.kt")

        assertTrue(
            billing.contains("queryPurchasesAsync"),
            "nothing asks Play which purchases the account still owns, so a purchase the flow " +
                "did not get to consume — a pending one that completed later, one interrupted " +
                "by the process dying — stays owned forever and Play answers ITEM_ALREADY_OWNED " +
                "to every thank-you after it. See MONETIZATION.md: the product is repeatable " +
                "only because it is consumed",
        )
    }

    /**
     * And the sweep runs where the leftovers actually are: before the next flow is launched.
     *
     * A sweep only on connection would clear an install that is already stuck on its *next*
     * launch, which is a fix somebody has to close the app to receive. Clearing before launching
     * is what makes the button work on the tap that found the problem.
     */
    @Test
    fun theSweepRunsBeforeTheFlowRatherThanOnlyAtStartup() {
        val billing = actual("androidMain/kotlin/game/vinto/app/AndroidBilling.android.kt")

        val buy = billing.substringAfter("internal suspend fun buy()", "")
        assertTrue(buy.isNotBlank(), "AndroidBilling.buy has moved; this test is stale")
        assertTrue(
            buy.contains("sweep"),
            "buy() launches Play's sheet without first clearing anything left owned, so the " +
                "first tap after a stuck purchase still comes back 'You already own this item'",
        )
    }

    /**
     * The purchase Play reports outside a waiting `buy()` is consumed too.
     *
     * This is the case that creates the leftover in the first place: a `PENDING` purchase that
     * turns `PURCHASED` after the flow gave up, delivered to a listener with nobody waiting on
     * it. Dropping it there is what the sweep then has to go and find.
     */
    @Test
    fun aPurchaseReportedWithNobodyWaitingIsStillConsumed() {
        val billing = actual("androidMain/kotlin/game/vinto/app/AndroidBilling.android.kt")

        // Anchored on the declaration, not the import above it: slicing from the first mention
        // of the type would read the whole file and pass on any mention of consuming anywhere.
        val listener = billing.substringAfter("private val updates", "").substringBefore("fun attach")
        assertTrue(listener.isNotBlank(), "the purchases listener has moved; this test is stale")
        assertTrue(
            listener.contains("consume") || listener.contains("collect"),
            "the listener hands its purchases to whatever `buy()` is waiting and no further, so " +
                "one that arrives late — after the flow timed out, or after the process was " +
                "rebuilt — is never consumed and owns the product from then on",
        )
    }

    /**
     * The iOS half of the same property, so the two phones cannot drift apart on it.
     *
     * StoreKit's equivalent of a leftover is an unfinished transaction, and it has the shape
     * right already: the observer is added once and finishes whatever arrives, whenever it
     * arrives. This fails if that ever narrows to the transaction a `buy()` is waiting for.
     */
    @Test
    fun theAppStoreHalfFinishesWhateverArrivesToo() {
        val billing = actual("iosMain/kotlin/game/vinto/app/IosBilling.ios.kt")

        assertTrue(
            billing.contains("finishTransaction"),
            "nothing finishes a StoreKit transaction, so a purchase stays in the queue and is " +
                "redelivered on every launch — the App Store's version of the same report",
        )
        assertTrue(
            billing.contains("addTransactionObserver"),
            "the observer is gone, so a transaction that completes after the screen went away " +
                "is never handed to the app at all",
        )
    }
}
