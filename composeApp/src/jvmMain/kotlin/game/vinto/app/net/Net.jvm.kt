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
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.WebSocket
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

        val socket = client.newWebSocketBuilder()
            .buildAsync(URI.create(socketUrl(baseUrl, code)), listener)
            .join()

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
            parseCreatedRoom(response.body())
        }

    override suspend fun listPublicRooms(): List<PublicRoom> =
        withContext(Dispatchers.IO) {
            val response = client.send(
                HttpRequest.newBuilder(URI.create("${httpBase(baseUrl)}/rooms")).GET().build(),
                HttpResponse.BodyHandlers.ofString(),
            )
            parsePublicRooms(response.body())
        }
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
