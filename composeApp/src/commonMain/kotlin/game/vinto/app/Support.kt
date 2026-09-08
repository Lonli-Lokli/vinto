package game.vinto.app

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Buying the one thing this app sells: a way to say thanks.
 *
 * ### One product, one price, and why there is no amount to type
 *
 * The obvious shape for a tip jar is a box you type a number into, and **neither store allows
 * it**. An in-app purchase is a *product* whose price is set in App Store Connect or the Play
 * Console; a buyer chooses a product, never a figure. So the choice is between a small set of
 * fixed tiers and a single one, and this is a single one — £5 / €5 / $5, each store's own
 * five-unit price point, shown in the player's own currency because the store localises it.
 *
 * An arbitrary amount is only reachable by sending somebody to a web page, and on a phone that
 * is the App Store 3.1.1 case almost exactly: collecting money for a developer outside in-app
 * purchase, which Apple permits for approved charities and not for the rest of us. It risks the
 * whole submission rather than just the button.
 *
 * ### Which is why the web gets the link and the phones do not
 *
 * That rule is a *store's* rule, and the web and desktop builds are in no store — nobody reviews
 * them and no policy reaches them. So those two answer [Support.Elsewhere] and send somebody to
 * a page where they can give whatever they like, which is the thing the phones cannot offer.
 *
 * **The split is structural, not a promise.** The address is referenced only from the wasm and
 * JVM actuals; `SupportLinkTest` reads the Android and iOS sources and fails if it appears in
 * either. A rule kept by a comment is a rule somebody edits past.
 *
 * ### What it must never do
 *
 * `MONETIZATION.md` invariant 2: **gameplay is never for sale**. This buys nothing — no deck,
 * no felt, no advantage, and nothing removed. It is a consumable so it can be given more than
 * once, which is only honest because it unlocks nothing: a repeatable purchase that *did* grant
 * something would be a currency, and this file is the reason there is no currency.
 *
 * ### Absent-safe, like every other seam here
 *
 * A platform with no store answers [Support.Unavailable] and the sheet says so plainly rather
 * than showing a dead button — the RELIABILITY.md §6p rule that a trouble picks the sentence.
 * That is the permanent answer on the web and the desktop, and the current answer on both
 * phones: the product does not exist in either console yet, and `MONETIZATION.md` sequences
 * that after 9.10 because nothing can be sold before store releases exist. The seam reports
 * what the platform can actually do, so nothing here pretends.
 */
expect fun supportOffer(): Support

/** Whether a purchase can be made here at all, and what it costs if so. */
sealed interface Support {
    /**
     * The store has the product and is ready to sell it.
     *
     * [price] is the store's own formatted string — "£5.00", "5,00 €", "$5.00" — never a number
     * this app formats. Currency and its placement differ by locale in ways a format string
     * cannot carry, and the store already knows the answer for the account doing the buying.
     */
    data class Offered(val price: String) : Support

    /**
     * No store here, but somewhere to go — the web and the desktop.
     *
     * The one shape that allows a chosen amount, because the page on the other end takes any
     * figure. Available exactly where no store's policy applies, which is why this case exists
     * rather than reusing [Offered] with an empty price.
     */
    data class Elsewhere(val url: String) : Support

    /**
     * No store, no product, or a store that would not answer.
     *
     * One case rather than three on purpose. A player cannot act on the difference between "this
     * platform has no billing", "the product is not configured yet" and "the network is out" —
     * all three mean the same thing to them, and a sheet that explained which would be telling
     * them about our problems.
     */
    data object Unavailable : Support
}

/**
 * Asks the platform to take the payment, returning true when it went through.
 *
 * Deliberately coarse. There is no receipt to keep, nothing to unlock and nothing to restore: a
 * consumable that grants no entitlement has no state worth persisting, which is what makes this
 * the one purchase in `MONETIZATION.md` needing neither a vault entry nor a restore flow. What
 * the player gets is the transaction itself, and the app saying thank you.
 */
expect suspend fun buySupport(): Boolean

/** The one product id, shared by both consoles so a single string names it everywhere. */
const val SUPPORT_PRODUCT: String = "vinto.support.five"

/**
 * The offer to draw, when something other than this platform is answering.
 *
 * Null everywhere a player ever runs the app, and the settings fall back to [supportOffer] —
 * so nothing here changes what any of the four builds can actually do.
 *
 * It exists for one artefact. **App Store Connect will not accept an in-app purchase without a
 * review screenshot of it**, and the screenshot has to show the purchase as it appears in the
 * app — which on a desktop render is [Support.Elsewhere], the web's link, and on a phone before
 * the product exists in the console is [Support.Unavailable]. Both are honest answers and
 * neither is the picture Apple is asking for, so the shot would have to be taken by hand off a
 * TestFlight build that cannot exist until the product does. This is the handle that renders it
 * instead, from the real `SettingsScreen` (`IapShotTest`) — the same reasoning as `LocalPacing`,
 * which exists so a caller with nobody watching can drop the dwells.
 *
 * Staging a price is not staging a claim: the figure comes from the same `iaps` block in
 * `vydanne.config.mjs` that sets it in the console, and `SupportProductTest` fails if the two
 * drift apart.
 */
val LocalSupport = staticCompositionLocalOf<Support?> { null }
