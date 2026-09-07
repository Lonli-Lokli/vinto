package game.vinto.app

/** What the App Store says it costs, from the answer cached at launch. */
actual fun supportOffer(): Support =
    IosBilling.price()?.let { Support.Offered(it) } ?: Support.Unavailable

/**
 * Takes the payment.
 *
 * Every failure is the same `false`: cancelled, refused, no network, no product, payments
 * disabled by a restriction. A player cannot act on the difference, and a thank-you that
 * explained why it failed would be telling them about our problems at the moment they were
 * trying to be generous.
 */
actual suspend fun buySupport(): Boolean = IosBilling.buy()
