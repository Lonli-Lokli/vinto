package game.vinto.app

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
