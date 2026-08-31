package game.vinto.app

/**
 * The browser's own share sheet, where the browser has one.
 *
 * `navigator.share` is the phone case — Android Chrome and iOS Safari both open the system
 * sheet — and it is exactly where an invitation matters most, because the person receiving it
 * is one tap from the room. Desktop browsers largely do not implement it, which is what the
 * `false` is for: the caller falls back to the clipboard, which they do implement.
 *
 * Both are fired and forgotten rather than awaited. `share()` resolves when the sheet closes
 * and *rejects* when the person dismisses it — a dismissal is not a failure, and treating it
 * as one would make the button report an error for the most ordinary thing anybody does with
 * a share sheet. What is reported here is only whether the browser took the request at all.
 */
actual fun shareText(subject: String, body: String): Boolean = webShare(subject, body)

// detekt reads Kotlin, not the JavaScript body below, so it cannot see that both parameters
// are referenced there by name. The suppression is the price of `js()` interop and belongs on
// the function that uses it, not in the config.
@Suppress("UnusedParameter")
private fun webShare(title: String, text: String): Boolean = js(
    """{
      try {
        if (!navigator.share) return false;
        navigator.share({ title: title, text: text }).catch(function () {});
        return true;
      } catch (e) {
        return false;
      }
    }""",
)
