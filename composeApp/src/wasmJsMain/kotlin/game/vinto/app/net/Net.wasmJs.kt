package game.vinto.app.net

import game.vinto.client.RoomAnswer
import game.vinto.client.RoomConnector
import game.vinto.client.RoomSocket
import game.vinto.client.answering
import game.vinto.client.createRoomBody
import game.vinto.client.parseCreatedRoom
import game.vinto.client.parsePublicRooms
import game.vinto.client.requireOk
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.suspendCancellableCoroutine
import org.w3c.dom.WebSocket
import org.w3c.fetch.RequestInit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * The browser's network: its own `WebSocket` and `fetch` — the two APIs the web app this
 * client replaces has always used, with nothing bundled on top.
 */
actual fun platformRoomConnector(baseUrl: String): RoomConnector = WasmRoomConnector(baseUrl)

private class WasmRoomConnector(private val baseUrl: String) : RoomConnector {

    override suspend fun connect(code: String): RoomAnswer<RoomSocket> = answering {
        val incoming = Channel<String>(Channel.UNLIMITED)

        val socket = suspendCancellableCoroutine { continuation ->
            val ws = WebSocket(socketUrl(baseUrl, code))
            ws.onopen = { if (continuation.isActive) continuation.resume(ws) }
            ws.onmessage = { event ->
                val text = event.data?.toString()
                if (text != null) incoming.trySend(text)
            }
            ws.onclose = { incoming.close() }
            ws.onerror = {
                incoming.close(RuntimeException("socket error"))
                if (continuation.isActive) {
                    // No status, and there will not be one. A browser deliberately hides the
                    // HTTP response of a failed WebSocket upgrade from the page — it is the
                    // one platform of the four that cannot tell "no such room" from "no
                    // network". `RemoteRoom` gives up after a few tries for exactly this
                    // reason, so a wrong code here still ends in a sentence rather than a
                    // spinner; it is just a vaguer sentence.
                    continuation.resumeWithException(RuntimeException("could not connect"))
                }
            }
            continuation.invokeOnCancellation { ws.close() }
        }

        WasmRoomSocket(socket, incoming)
    }

    override suspend fun createRoom(isPublic: Boolean, hostNickname: String) = answering {
        val response = window.fetch(
            "${httpBase(baseUrl)}/rooms",
            RequestInit(method = "POST", body = createRoomBody(isPublic, hostNickname).toJsString()),
        ).await<org.w3c.fetch.Response>()
        val body = response.text().await<JsString>().toString()
        parseCreatedRoom(requireOk(response.status.toInt(), body))
    }

    override suspend fun listPublicRooms() = answering {
        val response = window.fetch(
            "${httpBase(baseUrl)}/rooms",
            RequestInit(method = "GET"),
        ).await<org.w3c.fetch.Response>()
        val body = response.text().await<JsString>().toString()
        parsePublicRooms(requireOk(response.status.toInt(), body))
    }
}

private class WasmRoomSocket(
    private val socket: WebSocket,
    override val incoming: Channel<String>,
) : RoomSocket {

    override suspend fun send(text: String) {
        socket.send(text)
    }

    override fun close() {
        runCatching { socket.close() }
        incoming.close()
    }
}
