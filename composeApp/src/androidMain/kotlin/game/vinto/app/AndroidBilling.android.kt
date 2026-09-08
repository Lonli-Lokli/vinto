package game.vinto.app

import android.app.Activity
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.consumePurchase
import com.android.billingclient.api.queryProductDetails
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Play Billing, for the one product this app sells.
 *
 * A **consumable**, deliberately. It unlocks nothing — `MONETIZATION.md` invariant 2 is that
 * gameplay is never for sale — so there is no entitlement to own, and a one-time product would
 * let somebody say thanks exactly once. Consuming it the moment it arrives is what makes it
 * repeatable, and that is only honest *because* it grants nothing.
 *
 * There is no receipt verification and no vault entry, for the same reason: a tampered client
 * showing itself a thank-you it did not pay for costs nobody anything and confers no advantage.
 * There is nothing here a server would be protecting.
 *
 * ### Why the price is cached rather than fetched when asked
 *
 * [supportOffer] is a blocking function — the settings row reads it once while composing — and
 * Play's product query is asynchronous. Blocking a frame on a network call to render a row is
 * the wrong trade, so the price is fetched once when the activity attaches and read from memory
 * afterwards. The cost is honest and small: on the very first launch a player who reaches
 * Settings before Play answers sees "not available here yet" and sees the price on their next
 * visit. Never a spinner, and never a wrong price.
 *
 * ### The activity, and why it is held
 *
 * `launchBillingFlow` needs an `Activity` — Play draws its sheet over one — and this seam is a
 * plain suspend function with no composition to read one from. So `MainActivity` hands it over
 * on the way up, exactly as it already does for storage (`AndroidStorage.attach`), and takes it
 * back on the way down so a finished activity is never handed to Play.
 */
object AndroidBilling {

    private var client: BillingClient? = null
    private var details: ProductDetails? = null
    private var activity: Activity? = null

    /**
     * Whatever purchase Play last reported, waiting to be collected by [buy].
     *
     * A field rather than a parameter because Play answers through a listener registered on the
     * client, not through the call that started the flow — so the coroutine that launched it has
     * to be handed the result from outside itself.
     */

    /** Long enough for a real payment method, short enough not to wait forever on silence. */
    private const val FLOW_TIMEOUT_MS = 10 * 60 * 1000L

    private var pending: CompletableDeferred<List<Purchase>?>? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val updates = PurchasesUpdatedListener { result, purchases ->
        // Every non-OK result completes with null, including cancellation. The caller turns all
        // of them into the same `false`: a player cannot act on the difference between refused,
        // cancelled and unreachable.
        pending?.complete(purchases.takeIf { result.responseCode == BillingClient.BillingResponseCode.OK })
    }

    /** Called from `MainActivity`. Idempotent: a configuration change replaces, never stacks. */
    fun attach(activity: Activity) {
        this.activity = activity
        if (client != null) return

        val built = BillingClient.newBuilder(activity.applicationContext)
            .setListener(updates)
            // Required since Billing 7: a one-time product can be left pending by a slow payment
            // method, and the client refuses to build without saying you have considered it.
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .build()
        client = built
        built.startConnection(
            object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) warm(built)
                }

                // No retry. A store that was not there when the app opened is a store the player
                // is told about honestly rather than one this object keeps knocking at.
                override fun onBillingServiceDisconnected() = Unit
            },
        )
    }

    /** Called when the activity goes, so a dead one is never handed to Play's sheet. */
    fun detach(activity: Activity) {
        if (this.activity === activity) this.activity = null
    }

    /** Asks Play what the product costs, once, and keeps the answer. */
    private fun warm(client: BillingClient) {
        scope.launch {
            val query = QueryProductDetailsParams.newBuilder()
                .setProductList(
                    listOf(
                        QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(SUPPORT_PRODUCT)
                            .setProductType(BillingClient.ProductType.INAPP)
                            .build(),
                    ),
                )
                .build()

            val answer = runCatching { client.queryProductDetails(query) }.getOrNull()
            details = answer?.productDetailsList?.firstOrNull()
        }
    }

    /**
     * The price Play formatted, in the buyer's own currency, or null when there is none.
     *
     * `oneTimePurchaseOfferDetails` is the single-offer accessor, and since Play's one-time
     * products became a product *containing* purchase options it does not mean "the only one" —
     * it means **the option marked backwards compatible**, of however many exist. The first `buy`
     * option created is marked automatically, so one option is safe; a product left with none
     * marked answers null here, which this reports as [Support.Unavailable] and the screen reports
     * as "not available here yet". That is indistinguishable from having no network, on a product
     * that looks correct in the console. `MONETIZATION.md` says which flag to go and look at.
     *
     * Blank rather than null is a separate case and a real one — an active product priced in no
     * territory the buyer is in — and it is `SettingsScreen` that handles it, because what is
     * wrong there is the label rather than the offer (`SupportPriceTest`).
     */
    internal fun price(): String? =
        details?.oneTimePurchaseOfferDetails?.formattedPrice

    /**
     * Runs the flow and consumes what it produces.
     *
     * Consumed rather than acknowledged-and-kept: Play cancels an unacknowledged purchase after
     * three days, and consuming is what both acknowledges it and makes the next one possible.
     */
    internal suspend fun buy(): Boolean {
        val client = client ?: return false
        val activity = activity ?: return false
        val product = details ?: return false

        val flow = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(product)
                        .build(),
                ),
            )
            .build()

        val waiting = CompletableDeferred<List<Purchase>?>()
        pending = waiting
        client.launchBillingFlow(activity, flow)

        // Bounded, because a listener that never fires would otherwise hang the coroutine for
        // the life of the process — Play's sheet can be dismissed in ways that report nothing.
        val purchases = withTimeoutOrNull(FLOW_TIMEOUT_MS) { waiting.await() }
        pending = null
        val bought = purchases.orEmpty().filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        if (bought.isEmpty()) return false

        bought.forEach {
            runCatching {
                client.consumePurchase(ConsumeParams.newBuilder().setPurchaseToken(it.purchaseToken).build())
            }
        }
        return true
    }
}
