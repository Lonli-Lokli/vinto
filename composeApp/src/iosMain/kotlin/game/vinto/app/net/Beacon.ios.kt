package game.vinto.app.net

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
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
actual suspend fun postBeacon(url: String, body: String) {
    val target = NSURL.URLWithString(url) ?: return
    val request = NSMutableURLRequest.requestWithURL(target)
    request.setHTTPMethod("POST")
    request.setValue("application/json", forHTTPHeaderField = "content-type")
    request.setHTTPBody(body.toNSData())
    NSURLSession.sharedSession.dataTaskWithRequest(request) { _, _, _ -> }.resume()
}

@OptIn(ExperimentalForeignApi::class)
private fun String.toNSData(): NSData {
    val bytes = encodeToByteArray()
    if (bytes.isEmpty()) return NSData()
    return bytes.usePinned { pinned ->
        NSData.dataWithBytes(pinned.addressOf(0), bytes.size.toULong())
    }
}
