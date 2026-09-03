package game.vinto.app

/**
 * Where the rate button goes, which is a different address on each platform.
 *
 * An `expect` rather than a parse of [platformName], which returns "Android 34" / "iOS 26.5" for
 * a crash report and is free text — reading a product decision out of a diagnostic string is how
 * one gets silently wrong when somebody improves the wording.
 *
 * **The two platforms with no store both go to the app's own page**, which lists them. A player
 * on the web or the desktop window has nothing to review, and the honest answer is the page that
 * tells them where the app lives — not a Play listing that will refuse to open, and not a hidden
 * button, because a control that appears on two platforms and vanishes on the others is a bug
 * report waiting to be filed.
 */
expect fun storeReviewUrl(): String
