package game.vinto.app.net

import game.vinto.client.CreatedRoom
import game.vinto.client.RoomConnector
import game.vinto.client.RoomSocket
import game.vinto.client.createRoomBody
import game.vinto.client.parseCreatedRoom
import game.vinto.client.parsePublicRooms
import game.vinto.protocol.PublicRoom
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

    override suspend fun connect(code: String): RoomSocket {
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
                    if (continuation.isActive) continuation.resumeWithException(t)
                }
            }
            client.newWebSocket(request, listener)
            continuation.invokeOnCancellation { incoming.close() }
        }

        return AndroidRoomSocket(socket, incoming)
    }

    override suspend fun createRoom(isPublic: Boolean, hostNickname: String): CreatedRoom =
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

    override suspend fun listPublicRooms(): List<PublicRoom> =
        withContext(Dispatchers.IO) {
            parsePublicRooms(body(Request.Builder().url("${httpBase(baseUrl)}/rooms").get().build()))
        }

    /** One request, one string, cancellation included — the only shape either call needs. */
    private suspend fun body(request: Request): String =
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
                            continuation.resume(it.body?.string().orEmpty())
                        }
                    }
                }
            })
        }

    private companion object {
        val JSON = "application/json".toMediaType()
    }
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
