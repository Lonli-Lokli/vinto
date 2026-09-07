package game.vinto.app

/**
 * No store here — and no store's policy either, so this one can send somebody to a page.
 *
 * The phones cannot: an outside payment link is App Store 3.1.1 and Google's equivalent. This
 * build is in no store, nobody reviews it, and the page on the other end takes whatever amount
 * the giver chooses, which is the thing an in-app purchase structurally cannot offer.
 */
actual fun supportOffer(): Support = Support.Elsewhere(Pages.SUPPORT)

/**
 * Nothing to buy in-process: [supportOffer] hands back a link and the screen opens it.
 *
 * False rather than opening the page here, so there is exactly one place a link is followed and
 * one place a failure to open it is reported.
 */
actual suspend fun buySupport(): Boolean = false
