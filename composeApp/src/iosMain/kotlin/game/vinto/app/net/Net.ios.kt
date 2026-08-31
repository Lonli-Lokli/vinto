package game.vinto.app.net

import game.vinto.client.RoomAnswer
import game.vinto.client.RoomConnector
import game.vinto.client.RoomSocket
import game.vinto.client.answering
import game.vinto.client.createRoomBody
import game.vinto.client.parseCreatedRoom
import game.vinto.client.parsePublicRooms
import game.vinto.client.requireOk
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionWebSocketMessage
import platform.Foundation.NSURLSessionWebSocketTask
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.dataUsingEncoding
import platform.Foundation.setHTTPBody
import platform.Foundation.setHTTPMethod
import platform.Foundation.setValue
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * iOS's network: `NSURLSessionWebSocketTask`, in Foundation since iOS 13 — the platform's
 * own socket, no dependency at all. Receiving is a callback per message that must be
 * re-armed after each one; [pump] is that loop, feeding the channel until the task fails or
 * closes, which closes the channel, which is the disconnect signal upstairs.
 */
actual fun platformRoomConnector(baseUrl: String): RoomConnector = IosRoomConnector(baseUrl)

private class IosRoomConnector(private val baseUrl: String) : RoomConnector {

    override suspend fun connect(code: String): RoomAnswer<RoomSocket> = answering {
        val url = NSURL.URLWithString(socketUrl(baseUrl, code)) ?: error("bad url")
        val task = NSURLSession.sharedSession.webSocketTaskWithURL(url)
        val incoming = Channel<String>(Channel.UNLIMITED)
        task.resume()
        pump(task, incoming)
        IosRoomSocket(task, incoming)
    }

    /**
     * Re-arms the receive after every message; Foundation delivers exactly one per ask.
     *
     * The refused-upgrade status is not available here: `NSURLSessionWebSocketTask` reports it
     * through the session delegate rather than through this handler, and this connector has no
     * delegate on purpose. So iOS, like the browser, cannot tell a code nobody has from a
     * network that is not there — `RemoteRoom` giving up after a few tries is what turns both
     * into a sentence rather than a spinner that never stops.
     */
    private fun pump(task: NSURLSessionWebSocketTask, incoming: Channel<String>) {
        task.receiveMessageWithCompletionHandler { message, failure ->
            when {
                failure != null -> incoming.close(RuntimeException(failure.localizedDescription))
                message != null -> {
                    message.string?.let { incoming.trySend(it) }
                    pump(task, incoming)
                }

                else -> incoming.close()
            }
        }
    }

    @OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)
    override suspend fun createRoom(isPublic: Boolean, hostNickname: String) = answering {
        val url = NSURL.URLWithString("${httpBase(baseUrl)}/rooms") ?: error("bad url")
        val request = NSMutableURLRequest(uRL = url).apply {
            setHTTPMethod("POST")
            setValue("application/json", forHTTPHeaderField = "content-type")
            setHTTPBody(
                NSString.create(string = createRoomBody(isPublic, hostNickname))
                    .dataUsingEncoding(NSUTF8StringEncoding),
            )
        }
        parseCreatedRoom(body(request))
    }

    override suspend fun listPublicRooms() = answering {
        val url = NSURL.URLWithString("${httpBase(baseUrl)}/rooms") ?: error("bad url")
        val request = NSMutableURLRequest(uRL = url).apply { setHTTPMethod("GET") }
        parsePublicRooms(body(request))
    }

    /**
     * One request, one string, cancellation included — the only shape either call needs.
     *
     * The **status** is read rather than discarded, which is what the `_` in the middle of
     * this signature used to do. Without it a 404 or a 503 arrives as a successful call whose
     * body is `no such room`, and that goes straight into a JSON parser: the player is shown a
     * serialization error about a character offset instead of being told the room is not there.
     *
     * `NSHTTPURLResponse.statusCode` is a plain readonly property, so it is not one of the
     * binding traps README §7 collects — no category setter, no class factory renamed to
     * `create`. It still cannot be compiled on a machine without Xcode, which is why it is one
     * line and shaped exactly like the other three platforms'.
     */
    @OptIn(ExperimentalForeignApi::class)
    private suspend fun body(request: NSMutableURLRequest): String =
        suspendCancellableCoroutine { continuation ->
            val task = NSURLSession.sharedSession.dataTaskWithRequest(request) { data, response, failure ->
                val status = (response as? NSHTTPURLResponse)?.statusCode?.toInt() ?: OK
                when {
                    failure != null && continuation.isActive ->
                        continuation.resumeWithException(
                            RuntimeException(failure.localizedDescription),
                        )

                    continuation.isActive -> {
                        val body = data?.let { utf8(it) }.orEmpty()
                        @Suppress("TooGenericExceptionCaught")
                        try {
                            continuation.resume(requireOk(status, body))
                        } catch (refused: Exception) {
                            continuation.resumeWithException(refused)
                        }
                    }
                }
            }
            continuation.invokeOnCancellation { task.cancel() }
            task.resume()
        }

    @OptIn(BetaInteropApi::class)
    private fun utf8(data: NSData): String =
        NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString().orEmpty()
}

/** What a response with no status is taken to be: fine, since the failure path has its own. */
private const val OK = 200

private class IosRoomSocket(
    private val task: NSURLSessionWebSocketTask,
    override val incoming: Channel<String>,
) : RoomSocket {

    override suspend fun send(text: String) = suspendCancellableCoroutine { continuation ->
        task.sendMessage(NSURLSessionWebSocketMessage(text)) { failure ->
            when {
                failure != null && continuation.isActive ->
                    continuation.resumeWithException(RuntimeException(failure.localizedDescription))

                continuation.isActive -> continuation.resume(Unit)
            }
        }
    }

    override fun close() {
        task.cancelWithCloseCode(NORMAL_CLOSURE, reason = null)
        incoming.close()
    }

    private companion object {
        const val NORMAL_CLOSURE = 1000L
    }
}
