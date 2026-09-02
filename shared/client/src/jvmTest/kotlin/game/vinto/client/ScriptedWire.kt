package game.vinto.client

import game.vinto.engine.PlayerView
import game.vinto.engine.initializeGame
import game.vinto.engine.projectView
import game.vinto.protocol.ClientMessage
import game.vinto.protocol.EventEntry
import game.vinto.protocol.LobbySeat
import game.vinto.protocol.LobbyView
import game.vinto.protocol.ProtocolJson
import game.vinto.protocol.PublicRoom
import game.vinto.protocol.PublicSeat
import game.vinto.protocol.RoomPhase
import game.vinto.protocol.ServerMessage
import game.vinto.shapes.Difficulty
import game.vinto.shapes.GameAction
import game.vinto.shapes.PlayerIdPayload
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.TestScope

/**
 * A room's wire, scripted: the transport faked and nothing else.
 *
 * The socket is a pair of channels and every message is built with the real [ProtocolJson]
 * — the same serializer the room's envelope builders use — so a suite driving this exercises
 * the client's actual parsing, cursor-keeping, frame-building and reconnect behaviour. It
 * began as `RemoteSessionTest`'s private harness; it lives here so the lifecycle cases can
 * share it rather than carry a second copy that drifts.
 */
internal const val WIRE_CODE = "TEST42"
internal const val WIRE_SEATS = 4

internal class ScriptedSocket : RoomSocket {
    val sent = mutableListOf<String>()
    private val channel = Channel<String>(Channel.UNLIMITED)
    override val incoming = channel

    /** Set to make the next sends throw — the socket is there and the write fails. */
    var sendFails: Throwable? = null

    override suspend fun send(text: String) {
        sendFails?.let { throw it }
        sent += text
    }

    fun deliver(text: String) {
        channel.trySend(text)
    }

    fun fail(cause: Throwable) {
        channel.close(cause)
    }

    override fun close() {
        channel.close()
    }

    /** The last thing the client said, decoded. */
    fun lastSent(): ClientMessageOrNull = ClientMessageOrNull(sent.lastOrNull())
}

/** A decoded outgoing message, or null when nothing has been sent. */
internal class ClientMessageOrNull(private val text: String?) {
    val message: ClientMessage?
        get() = text?.let { ProtocolJson.decodeFromString(ClientMessage.serializer(), it) }
}

/** Hands out sockets in order, counting how often it was asked; refuses when they run out. */
internal class ScriptedConnector(sockets: List<ScriptedSocket>) : RoomConnector {
    private val queue = ArrayDeque(sockets)
    var asked = 0
        private set

    override suspend fun connect(code: String): RoomAnswer<RoomSocket> {
        asked++
        val next = queue.removeFirstOrNull()
            ?: return RoomAnswer.Failed(RoomTrouble.OFFLINE, "no more sockets scripted")
        return RoomAnswer.Ok(next)
    }

    override suspend fun createRoom(isPublic: Boolean, hostNickname: String) =
        RoomAnswer.Ok(CreatedRoom(WIRE_CODE, "room-$WIRE_CODE"))

    /** Nothing driven by this harness browses; the room it drives is one it was handed. */
    override suspend fun listPublicRooms(): RoomAnswer<List<PublicRoom>> =
        RoomAnswer.Ok(emptyList())
}

internal class Wire(
    private val scope: TestScope,
    spareSockets: List<ScriptedSocket> = emptyList(),
    seed: Long = 9L,
) {
    val socket = ScriptedSocket()
    val vault = MemoryVault()
    val state = initializeGame(seed, Difficulty.EASY)
    val dealtView: PlayerView = projectView(state, state.players.first().id)
    val connector = ScriptedConnector(listOf(socket) + spareSockets)

    val room = RemoteRoom(
        connector = connector,
        code = WIRE_CODE,
        vault = vault,
        nickname = "Ann",
        scope = scope,
    )

    fun deliver(message: ServerMessage) =
        socket.deliver(ProtocolJson.encodeToString(ServerMessage.serializer(), message))

    fun deliverJoined(view: PlayerView?) = socket.deliver(joinedJson(view))

    fun joinedJson(view: PlayerView?): String = ProtocolJson.encodeToString(
        ServerMessage.serializer(),
        ServerMessage.Joined(
            seat = 0,
            token = "tok-1",
            seats = List(WIRE_SEATS) { PublicSeat(index = it, occupied = it == 0) },
            nextIndex = 0,
            lobby = lobbyWith(occupied = setOf(0)),
            view = view,
        ),
    )

    /** A lobby with the given seats occupied — bots where [bots] says so. */
    fun lobbyWith(
        occupied: Set<Int>,
        bots: Set<Int> = emptySet(),
        phase: RoomPhase = RoomPhase.LOBBY,
    ) = LobbyView(
        phase = phase,
        seats = List(WIRE_SEATS) {
            LobbySeat(it, occupied = it in occupied, isBot = it in bots, removable = it in bots)
        },
        humans = (occupied - bots).size,
    )

    /** Lets the room's loop drain what the wire delivered. */
    fun settle() = scope.testScheduler.advanceUntilIdle()

    /** Runs what is ready without advancing the clock — for steps holding a timeout. */
    fun pump() = scope.testScheduler.runCurrent()
}

/** One bot move by [playerId], as the room logs it, with the receiver's view after it. */
internal fun botEntry(index: Int, playerId: String, view: PlayerView?) = EventEntry(
    index = index,
    seat = 1,
    playerId = playerId,
    action = GameAction.DrawCard(PlayerIdPayload(playerId)),
    byBot = true,
    view = view,
)
