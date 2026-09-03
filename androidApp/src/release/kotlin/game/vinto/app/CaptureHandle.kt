package game.vinto.app

import android.content.Intent

/**
 * The release build's answer: there is no capture handle.
 *
 * Not a disabled one — an absent one. The debug twin in `src/debug` reads an intent extra, which
 * is an entry point any app on the phone can send, so this is a build-variant gate rather than a
 * runtime check: nothing to find in the shipped binary, and nothing to get wrong later by
 * inverting a boolean.
 *
 * `MainActivity` calls this unconditionally and passes the result to `App`, so the two variants
 * differ by this file and nothing else.
 */
@Suppress("FunctionOnlyReturningConstant", "UnusedParameter")
internal fun captureScene(intent: Intent?): String? = null
