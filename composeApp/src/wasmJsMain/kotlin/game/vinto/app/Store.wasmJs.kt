package game.vinto.app

/**
 * The web build is played in a browser and was never installed, so it has no listing to review.
 * Its page names both stores, which is what somebody pressing "rate" on the web actually wants.
 */
actual fun storeReviewUrl(): String = Pages.THIS_APP
