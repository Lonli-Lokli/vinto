package game.vinto.app

/**
 * A web page, in a tab of its own — or an app, in this one.
 *
 * The split is not tidiness. `window.open` on a `vinto://` link opens a blank tab and hands the
 * scheme to it, so on a phone the app launches behind an empty page the person then has to
 * close; some browsers block the pop-up outright and nothing happens at all. A scheme the
 * browser cannot render is a **hand-off**, so it goes to this tab's own location and the system
 * takes it from there.
 */
actual fun openUrl(url: String): Boolean =
    if (url.startsWith("http://") || url.startsWith("https://")) openInTab(url) else handOff(url)

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

/**
 * A scheme the system owns, offered to it.
 *
 * `true` without a promise: a browser gives no answer for an external scheme, so what this
 * reports is that the offer was made. Nothing is staked on it — the screen that calls this
 * keeps its own button for joining right here, so an app that is not installed costs a
 * dismissed dialog and nothing else.
 */
@Suppress("UnusedParameter")
private fun handOff(url: String): Boolean =
    js("(function(){ window.location.href = url; return true; })()")
