package game.vinto.app

/** What the App Store says it costs, from the answer cached at launch. */
actual fun supportOffer(): Support =
    IosBilling.price()?.let { Support.Offered(it) } ?: Support.Unavailable

/**
 * Takes the payment, and answers how many thanks it bought.
 *
 * In practice 1, because StoreKit has no quantity picker of its own and this app deliberately
 * draws none — `Support.kt` says why that is a store rule rather than an omission. It is read
 * from the payment all the same, so the number is the transaction's own rather than an assumption
 * that would quietly be wrong the day a picker existed.
 *
 * Every failure is the same `0`: cancelled, refused, no network, no product, payments disabled by
 * a restriction. A player cannot act on the difference, and a thank-you that explained why it
 * failed would be telling them about our problems at the moment they were trying to be generous.
 */
actual suspend fun buySupport(): Int = IosBilling.buy()
