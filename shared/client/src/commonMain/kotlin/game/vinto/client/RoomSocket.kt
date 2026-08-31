package game.vinto.client

import game.vinto.protocol.ProtocolJson
import game.vinto.protocol.PublicRoom
import game.vinto.protocol.PublicRooms
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * The network, as two interfaces this module never implements.
 *
 * `shared/client` is proven network-free by `NoNetworkGuardTest`, and stays that way: what
 * lives here is the *shape* of a connection, and the platform actuals live in the app —
 * `java.net.http` on the JVM, OkHttp on Android, `NSURLSessionWebSocketTask` on iOS, the
 * browser's own `WebSocket` on Wasm. Deliberately not Ktor: the protocol is JSON text over
 * one socket, every platform ships a client for that, and a multiplatform HTTP framework is
 * a large dependency for the one verb this app uses.
 *
 * Everything is strings, because the wire is: `ProtocolJson` is applied by the session, not
 * the socket, so a fake socket in a test speaks real wire bytes.
 */
interface RoomSocket {
    /**
     * Messages as the room sends them, in order. The channel **closes** when the socket
     * does — cleanly or not — which is the one disconnect signal a consumer needs; a cause,
     * when there is one, arrives as the channel's close exception.
     */
    val incoming: ReceiveChannel<String>

    /** Sends one message. Throws if the socket is no longer usable. */
    suspend fun send(text: String)

    /** Closes the socket; [incoming] closes with it. Safe to call twice. */
    fun close()
}

/**
 * How a client reaches the room service: one WebSocket per room, and the REST that precedes it.
 *
 * **Nothing here throws.** Every method answers with a [RoomAnswer], which is a sealed pair, so
 * a caller cannot compile without a branch for the failure — and the failure is a
 * [RoomTrouble] the connector already named rather than whichever exception that platform's
 * HTTP client happens to use. Four transports threw four families of exception at screens that
 * wrote `catch (e: Exception)` and hoped; the compiler had nothing to say about a call site
 * that forgot, and a forgotten one is a crash rather than a message.
 */
interface RoomConnector {
    /** Opens a socket to the room behind [code]. */
    suspend fun connect(code: String): RoomAnswer<RoomSocket>

    /** `POST /rooms`: brings a room into existence and returns its code. */
    suspend fun createRoom(isPublic: Boolean, hostNickname: String): RoomAnswer<CreatedRoom>

    /**
     * `GET /rooms`: the public rooms, for somebody browsing rather than holding a code.
     *
     * An empty list is an ordinary answer — a quiet evening is not a failure — so
     * [RoomAnswer.Failed] means the service could not be reached or refused, which is the case
     * a screen must tell a person about.
     */
    suspend fun listPublicRooms(): RoomAnswer<List<PublicRoom>>
}

/** What `POST /rooms` answers with. */
data class CreatedRoom(val code: String, val roomId: String)

/**
 * Parses `POST /rooms`' answer — here rather than in each platform connector, so the four
 * of them stay transport and nothing else. Throws on an error body, because a room that was
 * not created has no code to return.
 */
fun parseCreatedRoom(json: String): CreatedRoom {
    val body = ProtocolJson.parseToJsonElement(json).jsonObject
    body["error"]?.let { error("the service refused: ${(it as? JsonPrimitive)?.content}") }
    return CreatedRoom(
        code = body.getValue("code").jsonPrimitive.content,
        roomId = body.getValue("roomId").jsonPrimitive.content,
    )
}

/**
 * Parses `GET /rooms`' answer, in the one place all four connectors can share it.
 *
 * Unknown fields are ignored rather than fatal: a client in somebody's pocket is older than
 * the service it is talking to, and a room list is not the wire contract worth refusing over
 * — unlike the recording format, where an unmodelled field means the parity gate is lying.
 */
fun parsePublicRooms(json: String): List<PublicRoom> =
    ProtocolJson.decodeFromString(PublicRooms.serializer(), json).rooms

/** The request body the same endpoint takes. */
fun createRoomBody(isPublic: Boolean, hostNickname: String): String =
    ProtocolJson.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put("isPublic", isPublic)
            put("hostNickname", hostNickname)
        },
    )
