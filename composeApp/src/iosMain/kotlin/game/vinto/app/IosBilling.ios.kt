package game.vinto.app

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterCurrencyStyle
import platform.StoreKit.SKPayment
import platform.StoreKit.SKPaymentQueue
import platform.StoreKit.SKPaymentTransaction
import platform.StoreKit.SKPaymentTransactionObserverProtocol
import platform.StoreKit.SKPaymentTransactionState
import platform.StoreKit.SKProduct
import platform.StoreKit.SKProductsRequest
import platform.StoreKit.SKProductsRequestDelegateProtocol
import platform.StoreKit.SKProductsResponse
import platform.darwin.NSObject

/**
 * StoreKit, for the one product this app sells.
 *
 * **StoreKit 1, not 2, and that is a toolchain fact rather than a preference.** StoreKit 2 is
 * Swift-only — its API is `async` types with no Objective-C surface — so Kotlin/Native cannot
 * reach it at all. `SKProductsRequest` and `SKPaymentQueue` are Objective-C and come through
 * `platform.StoreKit` with no bridging code in `iosApp`, which is the difference between this
 * file existing and a Swift shim nobody would remember to keep in step with it.
 *
 * A **consumable**, like the Play side and for the same reason: it unlocks nothing, so a
 * one-time product would let somebody say thanks exactly once. `finishTransaction` is what makes
 * the next one possible, and it is only honest to make it repeatable because it grants nothing.
 *
 * ### Why the price is cached
 *
 * [supportOffer] is a blocking function and `SKProductsRequest` is not. The price is fetched
 * once at [attach] and read from memory afterwards, exactly as on Android — a player who reaches
 * Settings before the App Store answers sees "not available here yet" and sees the price next
 * time. Never a spinner, and never a wrong price.
 *
 * ### The observer outlives everything on purpose
 *
 * `SKPaymentQueue` delivers a transaction whenever it finishes, which can be long after the
 * screen that started it — an interrupted purchase completes on the *next* launch. So the
 * observer is added once and never removed, and it finishes whatever arrives. Dropping it when a
 * screen goes is how a purchase gets stuck in the queue and charged without ever being handed
 * to the app.
 */
@OptIn(ExperimentalForeignApi::class)
object IosBilling {

    private var product: SKProduct? = null

    /** Long enough for a real payment method, short enough not to wait forever on silence. */
    private const val FLOW_TIMEOUT_MS = 10 * 60 * 1000L

    private var pending: CompletableDeferred<Boolean>? = null

    private val observer = object : NSObject(), SKPaymentTransactionObserverProtocol {
        override fun paymentQueue(queue: SKPaymentQueue, updatedTransactions: List<*>) {
            updatedTransactions.filterIsInstance<SKPaymentTransaction>().forEach { transaction ->
                when (transaction.transactionState) {
                    // Still in flight; StoreKit will call again with the outcome.
                    SKPaymentTransactionState.SKPaymentTransactionStatePurchasing -> {
                        Unit
                    }

                    SKPaymentTransactionState.SKPaymentTransactionStatePurchased,
                    SKPaymentTransactionState.SKPaymentTransactionStateRestored,
                    -> {
                        // Finished before the caller is told, so a purchase can never be left in
                        // the queue by a screen that went away while it was being paid for.
                        queue.finishTransaction(transaction)
                        pending?.complete(true)
                    }

                    // Failed, deferred, or anything a later StoreKit adds. All the same answer.
                    else -> {
                        queue.finishTransaction(transaction)
                        pending?.complete(false)
                    }
                }
            }
        }
    }

    private val products = object : NSObject(), SKProductsRequestDelegateProtocol {
        override fun productsRequest(request: SKProductsRequest, didReceiveResponse: SKProductsResponse) {
            product = didReceiveResponse.products.filterIsInstance<SKProduct>().firstOrNull()
        }
    }

    private var attached = false

    /**
     * Called from the iOS entry point: adds the observer and asks what the product costs.
     *
     * Idempotent, because Swift may ask for a second controller and two observers on one queue
     * would finish every transaction twice — the second call landing on an already-finished
     * transaction, which StoreKit treats as an error.
     */
    fun attach() {
        if (attached) return
        attached = true
        SKPaymentQueue.defaultQueue().addTransactionObserver(observer)
        val request = SKProductsRequest(productIdentifiers = setOf(SUPPORT_PRODUCT))
        request.delegate = products
        request.start()
    }

    /**
     * The price the App Store formatted, in the buyer's own currency.
     *
     * Formatted through `NSNumberFormatter` with the product's own locale rather than by this
     * app: a price is a number, a currency and a placement, and the placement differs by locale
     * in ways no format string carries. StoreKit 1 hands back the parts and expects this.
     */
    internal fun price(): String? {
        val offer = product ?: return null
        val formatter = NSNumberFormatter().apply {
            numberStyle = NSNumberFormatterCurrencyStyle
            locale = offer.priceLocale
        }
        return formatter.stringFromNumber(offer.price)
    }

    /** Puts the payment on the queue and waits for the observer to report what became of it. */
    internal suspend fun buy(): Boolean {
        val offer = product ?: return false
        if (!SKPaymentQueue.canMakePayments()) return false

        val waiting = CompletableDeferred<Boolean>()
        pending = waiting
        SKPaymentQueue.defaultQueue().addPayment(SKPayment.paymentWithProduct(offer))

        // Bounded: a payment can sit in "deferred" — Ask to Buy, for instance — for a very long
        // time, and the screen that started it should not wait for the life of the process.
        val paid = withTimeoutOrNull(FLOW_TIMEOUT_MS) { waiting.await() }
        pending = null
        return paid == true
    }
}
