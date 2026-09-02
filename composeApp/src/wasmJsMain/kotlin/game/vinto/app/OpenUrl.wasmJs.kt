package game.vinto.app

actual fun openUrl(url: String): Boolean = openInTab(url)

/**
 * One expression, which is the shape TRAPS.md §7 recommends for reaching a browser global from Wasm.
 *
 * `noopener` is not decoration: without it the page opened here is handed a `window.opener`
 * reference back to the game — a live handle to a tab holding somebody's seat token.
 *
 * A blocked pop-up is `null` rather than an exception, so the return value is the whole of the
 * error handling and there is nothing to catch.
 *
 * detekt reads Kotlin and not the JavaScript, so it cannot see the parameter used there.
 */
@Suppress("UnusedParameter")
private fun openInTab(url: String): Boolean =
    js("window.open(url, '_blank', 'noopener,noreferrer') != null")
