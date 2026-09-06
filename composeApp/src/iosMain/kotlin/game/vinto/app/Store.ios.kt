package game.vinto.app

/** `?action=write-review`, so this opens the review sheet rather than the listing. */
actual fun storeReviewUrl(): String = Pages.APPLE_REVIEW

actual fun storeListingUrl(): String = Pages.APPLE_LISTING
