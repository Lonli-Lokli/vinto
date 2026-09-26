package game.vinto.app

import game.vinto.client.CreatedRoom
import game.vinto.client.MemoryVault
import game.vinto.client.RemoteRoom
import game.vinto.client.RoomAnswer
import game.vinto.client.RoomConnector
import game.vinto.client.RoomSocket
import game.vinto.protocol.LobbySeat
import game.vinto.protocol.LobbyView
import game.vinto.protocol.ProtocolJson
import game.vinto.protocol.PublicRoom
import game.vinto.protocol.RoomPhase
import game.vinto.protocol.ServerMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel

/**
 * A room that has already said [said], over a socket that speaks real wire bytes.
 *
 * The lobby is drawn from what `RemoteRoom` makes of the messages, so a screen test built on
 * this sees exactly what a phone would — including a refusal turned into a trouble — rather than
 * a model assembled by hand that the client could come to disagree with. The socket never
 * closes, so the room sits where the messages left it; [RemoteRoom.leave] when done.
 */
internal fun stagedRoom(vararg said: ServerMessage, code: String = STAGED_CODE): RemoteRoom {
    val incoming = Channel<String>(Channel.UNLIMITED)
    said.forEach { incoming.trySend(ProtocolJson.encodeToString(ServerMessage.serializer(), it)) }
    val socket = object : RoomSocket {
        override val incoming: ReceiveChannel<String> = incoming
        override suspend fun send(text: String) = Unit
        override fun close() {
            incoming.close()
        }
    }
    val connector = object : RoomConnector {
        override suspend fun connect(code: String): RoomAnswer<RoomSocket> = RoomAnswer.Ok(socket)
        override suspend fun createRoom(isPublic: Boolean, hostNickname: String): RoomAnswer<CreatedRoom> =
            RoomAnswer.Ok(CreatedRoom(code, "room-$code"))
        override suspend fun listPublicRooms(): RoomAnswer<List<PublicRoom>> = RoomAnswer.Ok(emptyList())
    }
    return RemoteRoom(connector, code, MemoryVault(), "Dusty Rowan", CoroutineScope(Dispatchers.Unconfined))
}

/**
 * A lobby as the store builds meet it: two people with minted names, a bot somebody added, and
 * one seat still open — every kind of seat the lobby draws, at once.
 */
internal fun busyLobby(): ServerMessage.Joined {
    val lobby = LobbyView(
        phase = RoomPhase.LOBBY,
        seats = listOf(
            LobbySeat(
                0,
                occupied = true,
                isBot = false,
                removable = false,
                nickname = "Dusty Pebble",
                avatarKind = 0,
                avatarSeed = 5,
                avatarGround = 2,
            ),
            LobbySeat(
                1,
                occupied = true,
                isBot = false,
                removable = false,
                nickname = "Dusty Rowan",
                avatarKind = 0,
                avatarSeed = 135,
                avatarGround = 7,
            ),
            LobbySeat(2, occupied = true, isBot = true, removable = true, nickname = "Tide"),
            LobbySeat(3, occupied = false, isBot = false, removable = false),
        ),
        humans = 2,
    )
    return ServerMessage.Joined(seat = 1, token = "staged", seats = emptyList(), nextIndex = 0, lobby = lobby)
}

internal const val STAGED_CODE = "G7V8EF"
