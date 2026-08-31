package game.vinto.app.net

import game.vinto.client.RoomAnswer
import game.vinto.client.RoomConnector
import game.vinto.client.RoomServiceException
import game.vinto.client.RoomSocket
import game.vinto.client.answering
import game.vinto.client.createRoomBody
import game.vinto.client.parseCreatedRoom
import game.vinto.client.parsePublicRooms
import game.vinto.client.requireOk
import game.vinto.client.troubleFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Android's network: OkHttp, which is the platform's de-facto socket — `HttpsURLConnection`
 * has no WebSocket at all and `java.net.http` never shipped in the Android SDK. One client
 * instance per connector, as OkHttp's own docs insist: it owns a pool and an executor, and
 * a client per call is a thread leak wearing a convenience.
 */
actual fun platformRoomConnector(baseUrl: String): RoomConnector = AndroidRoomConnector(baseUrl)

private class AndroidRoomConnector(private val baseUrl: String) : RoomConnector {
    private val client = OkHttpClient()

    override suspend fun connect(code: String): RoomAnswer<RoomSocket> = answering {
        val incoming = Channel<String>(Channel.UNLIMITED)

        val request = Request.Builder().url(socketUrl(baseUrl, code)).build()
        val socket = suspendCancellableCoroutine { continuation ->
            val listener = object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    if (continuation.isActive) continuation.resume(webSocket)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    incoming.trySend(text)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    incoming.close()
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    incoming.close(t)
                    // OkHttp hands over the response that refused the upgrade, and it is the
                    // only thing that can tell a code nobody has (404) from a service that is
                    // closed (503) from a network that is not there at all. Discarding it —
                    // which is what this did — leaves the room reconnecting for ever against
                    // an answer that will never change.
                    if (continuation.isActive) continuation.resumeWithException(upgradeTrouble(t, response))
                }
            }
            client.newWebSocket(request, listener)
            continuation.invokeOnCancellation { incoming.close() }
        }

        AndroidRoomSocket(socket, incoming)
    }

    override suspend fun createRoom(isPublic: Boolean, hostNickname: String) = answering {
        withContext(Dispatchers.IO) {
            parseCreatedRoom(
                body(
                    Request.Builder()
                        .url("${httpBase(baseUrl)}/rooms")
                        .post(createRoomBody(isPublic, hostNickname).toRequestBody(JSON))
                        .build(),
                ),
            )
        }
    }

    override suspend fun listPublicRooms() = answering {
        withContext(Dispatchers.IO) {
            parsePublicRooms(body(Request.Builder().url("${httpBase(baseUrl)}/rooms").get().build()))
        }
    }

    /**
     * One request, one string, cancellation included — the only shape either call needs.
     *
     * The **status** goes through [requireOk] rather than being discarded, which it was. A 404
     * or a 503 used to be handed to a JSON parser along with whatever the service had said in
     * plain text, so the player was shown a serialization error about an offset instead of
     * "no such room".
     */
    private suspend fun body(request: Request): String =
        requireOk(
            suspendCancellableCoroutine { continuation ->
                val call = client.newCall(request)
                continuation.invokeOnCancellation { call.cancel() }
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        response.use {
                            if (continuation.isActive) {
                                continuation.resume(it.code to it.body?.string().orEmpty())
                            }
                        }
                    }
                })
            },
        )

    /** [requireOk] over the pair OkHttp actually gives back. */
    private fun requireOk(answer: Pair<Int, String>): String = requireOk(answer.first, answer.second)

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}

/** What a refused upgrade means, when OkHttp knew — the transport failure otherwise. */
private fun upgradeTrouble(failed: Throwable, response: Response?): Throwable {
    val trouble = response?.let { troubleFor(it.code) } ?: return failed
    // The body is already consumed by the time this runs; `message` is the reason phrase,
    // which is worth as much and is always there.
    val said = response.message.ifBlank { "the room refused the connection" }
    return RoomServiceException(trouble, said, failed)
}

private class AndroidRoomSocket(
    private val socket: WebSocket,
    override val incoming: Channel<String>,
) : RoomSocket {

    override suspend fun send(text: String) {
        // OkHttp queues sends on its own writer thread; a false return means the socket is
        // already closed, which upstream learns from the channel closing.
        if (!socket.send(text)) error("socket closed")
    }

    override fun close() {
        runCatching { socket.close(NORMAL_CLOSURE, "bye") }
        incoming.close()
    }

    private companion object {
        const val NORMAL_CLOSURE = 1000
    }
}
