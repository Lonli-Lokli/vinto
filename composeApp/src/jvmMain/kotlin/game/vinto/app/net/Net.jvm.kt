package game.vinto.app.net

import game.vinto.client.CreatedRoom
import game.vinto.client.RoomConnector
import game.vinto.client.RoomServiceException
import game.vinto.client.RoomSocket
import game.vinto.client.createRoomBody
import game.vinto.client.parseCreatedRoom
import game.vinto.client.parsePublicRooms
import game.vinto.client.requireOk
import game.vinto.client.troubleFor
import game.vinto.protocol.PublicRoom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.WebSocket
import java.net.http.WebSocketHandshakeException
import java.util.concurrent.CompletionException
import java.util.concurrent.CompletionStage

/**
 * The desktop's network: `java.net.http`, in the JDK since 11 — nothing to add, nothing to
 * shade. The listener accumulates partial text frames (the API is allowed to split them) and
 * hands whole messages to a channel, whose closing is the disconnect signal upstairs.
 */
actual fun platformRoomConnector(baseUrl: String): RoomConnector = JvmRoomConnector(baseUrl)

private class JvmRoomConnector(private val baseUrl: String) : RoomConnector {
    private val client: HttpClient = HttpClient.newHttpClient()

    override suspend fun connect(code: String): RoomSocket = withContext(Dispatchers.IO) {
        val incoming = Channel<String>(Channel.UNLIMITED)

        val listener = object : WebSocket.Listener {
            private val partial = StringBuilder()

            override fun onText(
                socket: WebSocket,
                data: CharSequence,
                last: Boolean,
            ): CompletionStage<*>? {
                partial.append(data)
                if (last) {
                    incoming.trySend(partial.toString())
                    partial.setLength(0)
                }
                socket.request(1)
                return null
            }

            override fun onClose(socket: WebSocket, statusCode: Int, reason: String): CompletionStage<*>? {
                incoming.close()
                return null
            }

            override fun onError(socket: WebSocket, error: Throwable) {
                incoming.close(error)
            }
        }

        // The handshake's own status, when there is one. `java.net.http` reports a refused
        // upgrade as a `WebSocketHandshakeException` carrying the HTTP response — which is how
        // a 404 for a code nobody has, or a 503 from a closed service, becomes a sentence
        // rather than "connection failed" and a spinner that never stops.
        val socket = try {
            client.newWebSocketBuilder()
                .buildAsync(URI.create(socketUrl(baseUrl, code)), listener)
                .join()
        } catch (failed: CompletionException) {
            incoming.close()
            throw upgradeTrouble(failed)
        }

        JvmRoomSocket(socket, incoming)
    }

    override suspend fun createRoom(isPublic: Boolean, hostNickname: String): CreatedRoom =
        withContext(Dispatchers.IO) {
            val response = client.send(
                HttpRequest.newBuilder(URI.create("${httpBase(baseUrl)}/rooms"))
                    .POST(HttpRequest.BodyPublishers.ofString(createRoomBody(isPublic, hostNickname)))
                    .header("content-type", "application/json")
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            )
            parseCreatedRoom(requireOk(response.statusCode(), response.body()))
        }

    override suspend fun listPublicRooms(): List<PublicRoom> =
        withContext(Dispatchers.IO) {
            val response = client.send(
                HttpRequest.newBuilder(URI.create("${httpBase(baseUrl)}/rooms")).GET().build(),
                HttpResponse.BodyHandlers.ofString(),
            )
            parsePublicRooms(requireOk(response.statusCode(), response.body()))
        }
}

/**
 * What a refused upgrade means, out of the exception the JDK wraps it in.
 *
 * `join()` wraps everything in a `CompletionException`, and the interesting case is one whose
 * cause is a `WebSocketHandshakeException`: that one carries the whole HTTP response, so the
 * status the service chose survives. Anything else really is a transport failure and is left
 * as it was — the loop above retries those, which is right.
 */
private fun upgradeTrouble(failed: CompletionException): Throwable {
    val handshake = failed.cause as? WebSocketHandshakeException ?: return failed
    val response = handshake.response
    val trouble = troubleFor(response.statusCode()) ?: return failed
    // `HttpResponse<?>` from the handshake is untyped, so the body is whatever it is; a
    // string when the service answered in text, and worth nothing otherwise.
    val said = (response.body() as? String)?.trim().orEmpty()
    return RoomServiceException(trouble, said.ifEmpty { "the room refused the connection" }, failed)
}

private class JvmRoomSocket(
    private val socket: WebSocket,
    override val incoming: Channel<String>,
) : RoomSocket {

    override suspend fun send(text: String) = withContext(Dispatchers.IO) {
        socket.sendText(text, true).join()
        Unit
    }

    override fun close() {
        runCatching { socket.sendClose(WebSocket.NORMAL_CLOSURE, "bye") }
        incoming.close()
    }
}
