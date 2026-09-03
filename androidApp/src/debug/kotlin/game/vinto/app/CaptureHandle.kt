package game.vinto.app

import android.content.Intent

/**
 * The debug build's answer: read the state a capture run asked for.
 *
 *     adb shell am start -n app.kupalinka.vinto/game.vinto.app.MainActivity \
 *       --es vinto.capture table
 *
 * `zdymak` drives exactly this; `MarketingScene` lists the states and says what each one is.
 *
 * **This file has a no-op twin in `src/release`**, and the pairing is the whole security model.
 * An intent extra is an entry point any app on the phone can send — `am start` is only the
 * convenient way to reach it — so a runtime `if (BuildConfig.DEBUG)` would be the wrong shape:
 * the code would still be in the shipped binary for somebody to find and call. A build-variant
 * gate means the release build does not contain a reader at all.
 *
 * The decision is recorded in `zdymak.config.mjs`, which chose a debug-only handle over an
 * always-on one so the release surface is unchanged and the store shots come from a debug binary.
 */
internal fun captureScene(intent: Intent?): String? = intent?.getStringExtra(CAPTURE_EXTRA)

/** Named once, because the twin must not drift from it and `adb` spells it by hand. */
private const val CAPTURE_EXTRA = "vinto.capture"
