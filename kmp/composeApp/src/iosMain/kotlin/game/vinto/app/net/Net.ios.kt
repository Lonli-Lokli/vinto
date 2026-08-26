package game.vinto.app.net

import game.vinto.client.CreatedRoom
import game.vinto.client.RoomConnector
import game.vinto.client.RoomSocket
import game.vinto.client.createRoomBody
import game.vinto.client.parseCreatedRoom
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
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
import platform.Foundation.sharedSession
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

    override suspend fun connect(code: String): RoomSocket {
        val url = NSURL.URLWithString(socketUrl(baseUrl, code)) ?: error("bad url")
        val task = NSURLSession.sharedSession.webSocketTaskWithURL(url)
        val incoming = Channel<String>(Channel.UNLIMITED)
        task.resume()
        pump(task, incoming)
        return IosRoomSocket(task, incoming)
    }

    /** Re-arms the receive after every message; Foundation delivers exactly one per ask. */
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
    override suspend fun createRoom(isPublic: Boolean, hostNickname: String): CreatedRoom {
        val url = NSURL.URLWithString("${httpBase(baseUrl)}/rooms") ?: error("bad url")
        val request = NSMutableURLRequest(uRL = url).apply {
            setHTTPMethod("POST")
            setValue("application/json", forHTTPHeaderField = "content-type")
            setHTTPBody(
                NSString.create(string = createRoomBody(isPublic, hostNickname))
                    .dataUsingEncoding(NSUTF8StringEncoding),
            )
        }

        val body = suspendCancellableCoroutine { continuation ->
            val task = NSURLSession.sharedSession.dataTaskWithRequest(request) { data, _, failure ->
                when {
                    failure != null && continuation.isActive ->
                        continuation.resumeWithException(
                            RuntimeException(failure.localizedDescription),
                        )

                    continuation.isActive ->
                        continuation.resume(data?.let { utf8(it) }.orEmpty())
                }
            }
            continuation.invokeOnCancellation { task.cancel() }
            task.resume()
        }
        return parseCreatedRoom(body)
    }

    @OptIn(BetaInteropApi::class)
    private fun utf8(data: NSData): String =
        NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString().orEmpty()
}

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
