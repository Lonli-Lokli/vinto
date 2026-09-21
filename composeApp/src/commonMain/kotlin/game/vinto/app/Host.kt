package game.vinto.app

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Which kind of machine this is, for the handful of decisions that genuinely differ by it.
 *
 * **Not [platformName].** That returns "Android 34" and "Web (Kotlin/Wasm)" — sentences for a
 * crash report, and branching on them would mean parsing prose that exists to be read by a person.
 * This is the same seam every other platform difference in this app already uses: the target is
 * forced to answer, and the answer is a value the code can switch on.
 *
 * Three cases rather than five because three is what anything asks. A phone is a phone whether it
 * runs Android or iOS — same thumb, same width, same store rules — and the two places that care
 * about *which* phone ([supportOffer], the back gesture) already have their own seams for it.
 */
enum class Host {
    /** Android and iOS: a thumb, a narrow screen, and a store whose rules apply. */
    PHONE,

    /** The desktop app: a pointer, a window that can be any shape, and no store. */
    DESKTOP,

    /** The browser: a pointer or a thumb, and the one place a page can take a payment. */
    WEB,
}

/**
 * The host this build runs on.
 *
 * A `val` rather than a function because it cannot change while the app is running — the window
 * can be resized and the phone can be rotated, and neither of those makes a desktop a phone. What
 * varies with the window is the *shape*, which the header reads separately (`TableLayout`).
 */
expect val host: Host

/**
 * The host a screen should draw itself for, which is [host] unless somebody says otherwise.
 *
 * The one caller that says otherwise is the store renderer. `StoreShotsTest` draws the app's own
 * screens headless at a phone's pixels and a phone's density, which is the whole reason the shots
 * cannot drift from the app — but the JVM's `actual` is [Host.DESKTOP], so the header drew its
 * desktop shape into a 411 dp window and the wordmark was pushed off the left of six screenshots
 * on their way to two stores. Every one of them a valid PNG of a real screen.
 *
 * Same seam and same reason as `LocalPacing`: a caller with nobody watching drops the dwells
 * without the animation code knowing it is being hurried. Nothing else overrides it, and [host]
 * remains the answer for anything asking which machine this actually *is* — a store rule, a back
 * gesture, where a payment may be taken.
 */
val LocalHost = staticCompositionLocalOf { host }
