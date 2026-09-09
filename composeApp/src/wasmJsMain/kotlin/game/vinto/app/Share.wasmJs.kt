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

/**
 * The same sheet, carrying a file.
 *
 * `navigator.canShare({ files })` is asked first and is not the same question as `navigator.share`
 * existing: a browser can have the sheet and still refuse an attachment, and calling `share` with
 * files it will not take throws rather than falling back. When it says no, this returns false and
 * the caller sends the text — which is the share that matters — instead of nothing.
 *
 * The bytes cross into JavaScript one at a time through a callback rather than as a typed array,
 * because Kotlin/Wasm's `ByteArray` is not a JS object and the interop that would hand it over
 * whole is not available from a `js()` body. It is one allocation of a few tens of kilobytes on a
 * tap, which is the cheapest thing on this path by a wide margin.
 */
actual fun sharePicture(subject: String, body: String, picture: ByteArray): Boolean =
    webSharePicture(subject, body, picture.size) { picture[it].toInt() }

@Suppress("UnusedParameter")
private fun webSharePicture(title: String, text: String, size: Int, byteAt: (Int) -> Int): Boolean = js(
    """{
      try {
        if (!navigator.share || !navigator.canShare) return false;
        var bytes = new Uint8Array(size);
        for (var i = 0; i < size; i++) bytes[i] = byteAt(i) & 255;
        var file = new File([bytes], 'vinto-code.png', { type: 'image/png' });
        if (!navigator.canShare({ files: [file] })) return false;
        navigator.share({ title: title, text: text, files: [file] }).catch(function () {});
        return true;
      } catch (e) {
        return false;
      }
    }""",
)
