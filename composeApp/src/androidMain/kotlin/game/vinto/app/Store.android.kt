package game.vinto.app

/**
 * The https listing rather than `market://details?id=…`.
 *
 * The custom scheme opens the Play app one step earlier, but it has no handler at all on a
 * device without Play Store — a plain AOSP build, or a Huawei — and there it throws rather than
 * degrading. The https address is claimed by the Play app where it exists and opens in a browser
 * where it does not, which is the same destination and never a dead end.
 */
actual fun storeReviewUrl(): String = Pages.PLAY_REVIEW
