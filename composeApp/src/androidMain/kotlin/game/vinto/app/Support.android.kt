package game.vinto.app

/** What Play says it costs, from the answer cached when the activity attached. */
actual fun supportOffer(): Support =
    AndroidBilling.price()?.let { Support.Offered(it) } ?: Support.Unavailable

/**
 * Takes the payment.
 *
 * Every failure is the same `false`: cancelled, refused, no network, no product, no activity. A
 * player cannot act on the difference, and a thank-you that explained why it failed would be
 * telling them about our problems at the moment they were trying to be generous.
 */
actual suspend fun buySupport(): Boolean = AndroidBilling.buy()
