package game.vinto.app.net

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.create
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.setHTTPBody
import platform.Foundation.setHTTPMethod
import platform.Foundation.setValue

/**
 * `NSURLSession`, the same client the socket uses.
 *
 * Fired and not awaited: the task is resumed and the function returns. Nothing reads the
 * response, and a failure is a lost count.
 */
@OptIn(ExperimentalForeignApi::class)
actual suspend fun postBeacon(url: String, body: String, contentType: String, auth: String?) {
    val target = NSURL.URLWithString(url) ?: return
    val request = NSMutableURLRequest.requestWithURL(target)
    request.setHTTPMethod("POST")
    request.setValue(contentType, forHTTPHeaderField = "content-type")
    // `setValue:forHTTPHeaderField:` again, which is already proven to resolve here — no new
    // Objective-C name is introduced by this change, deliberately, because nothing on this
    // host can check one.
    if (auth != null) request.setValue(auth, forHTTPHeaderField = "x-sentry-auth")
    request.setHTTPBody(body.toNSData())
    NSURLSession.sharedSession.dataTaskWithRequest(request) { _, _, _ -> }.resume()
}

/**
 * The body as bytes Foundation will accept.
 *
 * `NSData.create`, not `NSData.dataWithBytes` — and that is not a style choice. Kotlin/Native
 * renames an Objective-C **class factory method** whose selector begins with its own class's
 * name: `+[NSData dataWithBytes:length:]` arrives here as `NSData.Companion.create`, so the
 * name in the header does not exist in Kotlin and the one that does has to be imported by
 * name, because it is an extension on the companion rather than a member. The same shape as
 * `setHTTPBody` above, and the same shape as the `NSMutableURLRequest` setters that caught
 * this file's first version — see README §1b. Only a compiler on a Mac finds these.
 *
 * `dataWithBytes:` copies, so the pinned buffer does not have to outlive this call.
 */
@OptIn(ExperimentalForeignApi::class)
private fun String.toNSData(): NSData {
    val bytes = encodeToByteArray()
    if (bytes.isEmpty()) return NSData()
    return bytes.usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
    }
}
