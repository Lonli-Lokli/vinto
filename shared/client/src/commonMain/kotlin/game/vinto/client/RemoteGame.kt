package game.vinto.client

import game.vinto.engine.PlayerView
import game.vinto.engine.PublicReveal
import game.vinto.protocol.ClientMessage
import game.vinto.protocol.LobbyView
import game.vinto.protocol.ProtocolJson
import game.vinto.protocol.RoundResult
import game.vinto.protocol.ServerMessage
import game.vinto.shapes.GameAction
import game.vinto.shapes.GamePhase
import game.vinto.shapes.actorId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonNull

/**
 * Where this client stands with the room service. [Reconnecting] is a state rather than a
 * spinner because the session keeps working through it — the seat is held by its token, and
 * the resync lands the table on the present when the socket comes back.
 */
sealed interface ConnectionState {
    data object Connecting : ConnectionState
    data object Connected : ConnectionState
    data class Reconnecting(val attempt: Int) : ConnectionState

    /** Over for good: the room closed, the session ended, or the player left. */
    data class Closed(val reason: String) : ConnectionState
}

/**
 * One room, from this client's side: the socket, the lobby, and — once dealt — the game.
 *
 * The split between this class and [RemoteGameSession] mirrors the room's own life. A room
 * exists before any game does, so the connection and the lobby live here; a session exists
 * per **deal**, appearing on [session] when a view first arrives and replaced wholesale on
 * the next `started` — the same "new round, new session" shape `LocalGame` has, which is
 * what lets a screen key on the session instance in both worlds.
 *
 * Reconnects are this class's whole reason to be a loop: the socket drops, the seat token
 * survives in the vault, and the loop joins again with backoff and resyncs the session's
 * cursor. A player who tunnels through a dead spot comes back to the present, not to a
 * replay of everything they missed — the catch-up collapses to one frame by design.
 */
@Suppress("TooManyFunctions")
class RemoteRoom(
    private val connector: RoomConnector,
    val code: String,
    private val vault: Vault,
    private val nickname: String,
    scope: CoroutineScope,
) {
    private val _connection = MutableStateFlow<ConnectionState>(ConnectionState.Connecting)
    val connection: StateFlow<ConnectionState> = _connection.asStateFlow()

    private val _lobby = MutableStateFlow<LobbyView?>(null)
    val lobby: StateFlow<LobbyView?> = _lobby.asStateFlow()

    /** The rounds played so far, as the room reports them. The score screen's source. */
    private val _standings = MutableStateFlow<List<RoundResult>>(emptyList())
    val standings: StateFlow<List<RoundResult>> = _standings.asStateFlow()

    private val _session = MutableStateFlow<RemoteGameSession?>(null)
    val session: StateFlow<RemoteGameSession?> = _session.asStateFlow()

    private val _seat = MutableStateFlow<Int?>(null)
    val seat: StateFlow<Int?> = _seat.asStateFlow()

    /** The session is over but the room still stands — the scoreboard's closing line. */
    private val _ended = MutableStateFlow<String?>(null)
    val ended: StateFlow<String?> = _ended.asStateFlow()

    /** Refusals that belong to no dispatch — a lobby op the room said no to. */
    private val _notices = MutableSharedFlow<String>(
        extraBufferCapacity = NOTICE_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val notices: SharedFlow<String> = _notices.asSharedFlow()

    private var socket: RoomSocket? = null
    private val sender: CoroutineScope = scope
    private val running = scope.launch { run() }

    /**
     * Seats with a change in flight, so the lobby can spin the seat rather than the screen.
     *
     * Adding a bot is a message, not a call: it goes out and the answer arrives later as a
     * whole new lobby. Between those two moments the button has visibly done nothing, and a
     * person taps it again — which is how a table ends up with two bots nobody asked for. The
     * seat holds a spinner instead, which says *where* the wait is; a bar across the top would
     * say only that something, somewhere, is happening.
     */
    private val _pendingSeats = MutableStateFlow<Set<Int>>(emptySet())
    val pendingSeats: StateFlow<Set<Int>> = _pendingSeats.asStateFlow()

    // ------------------------------------------------------------------ the lobby's verbs

    /** Adds a bot to the first free seat — which is the one the room will fill, so it spins. */
    fun addBot() {
        _lobby.value?.seats?.firstOrNull { !it.occupied }?.let { markPending(it.index) }
        fire(ClientMessage.AddBot(token()))
    }

    fun removeBot(seat: Int) {
        markPending(seat)
        fire(ClientMessage.RemoveBot(token(), seat))
    }

    fun nextRound() = fire(ClientMessage.NextRound(token()))

    /**
     * Holds a seat pending until the next lobby broadcast, or until the room has plainly not
     * answered.
     *
     * The timeout is the point rather than a belt: a refused op comes back as a notice and no
     * new lobby, and a socket that drops mid-request comes back as nothing at all. Without it
     * the seat spins for as long as the room is open, which is the failure mode people
     * remember about lobbies.
     */
    private fun markPending(seat: Int) {
        _pendingSeats.value = _pendingSeats.value + seat
        sender.launch {
            delay(PENDING_TIMEOUT_MS)
            _pendingSeats.value = _pendingSeats.value - seat
        }
    }

    /** Leaves for good. The seat token stays vaulted — the seat is reclaimable until the room dies. */
    fun leave() {
        _connection.value = ConnectionState.Closed("left the room")
        socket?.close()
        running.cancel()
    }

    // ------------------------------------------------------------------ the loop

    private suspend fun run() {
        var attempt = 0
        while (sender.isActive) {
            try {
                _connection.value = if (attempt == 0) {
                    ConnectionState.Connecting
                } else {
                    ConnectionState.Reconnecting(attempt)
                }
                val opened = connector.connect(code)
                socket = opened
                opened.send(encode(ClientMessage.Join(token(), nickname)))

                for (text in opened.incoming) {
                    attempt = 0
                    handle(text)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // The socket died or never opened; the backoff below is the whole handling.
            }

            if (_connection.value is ConnectionState.Closed) return
            attempt++
            delay(backoffMs(attempt))
        }
    }

    private fun handle(text: String) {
        val message = try {
            ProtocolJson.decodeFromString(ServerMessage.serializer(), text)
        } catch (_: IllegalArgumentException) {
            // A message this build does not know is a newer room being additive; skip it.
            return
        }

        when (message) {
            is ServerMessage.Joined -> joined(message)
            is ServerMessage.Lobby -> {
                _lobby.value = message.lobby
                // The lobby is the authority on who is sitting where, so its arrival is what
                // ends every wait — whichever seat the room actually changed.
                _pendingSeats.value = emptySet()
            }
            is ServerMessage.Started -> started(message)
            is ServerMessage.BetweenRounds -> {
                _standings.value = message.standings
                _session.value?.landOn(message.view, message.nextIndex)
            }

            is ServerMessage.Events -> _session.value?.applyEvents(message)
            is ServerMessage.Sync -> _session.value?.applySync(message)
            is ServerMessage.Ended -> _ended.value = message.reason
            is ServerMessage.Closed -> {
                _connection.value = ConnectionState.Closed(message.reason)
                socket?.close()
            }

            is ServerMessage.Error -> {
                val handled = _session.value?.refused(message.message) == true
                if (!handled) _notices.tryEmit(message.message)
            }
        }
    }

    private fun joined(message: ServerMessage.Joined) {
        // The one message that carries the raw token; it goes into the vault before anything
        // else happens, because it is delivered exactly once and it *is* the seat.
        vault.saveSeatToken(code, message.token)
        _seat.value = message.seat
        _lobby.value = message.lobby
        _connection.value = ConnectionState.Connected

        val view = message.view
        val session = _session.value
        when {
            // Reconnected mid-game: the session survives; ask for what it missed. The sync
            // that answers jumps the cursor and lands the table on the present.
            session != null -> fire(ClientMessage.Resync(session.cursor))
            view != null -> _session.value = newSession(view, message.nextIndex)
            else -> Unit // a lobby; the session appears with the deal
        }
    }

    private fun started(message: ServerMessage.Started) {
        message.standings?.let { _standings.value = it }
        val view = message.view ?: return
        // A new deal is a new session, replaced wholesale — the same "new round, new
        // session" shape LocalGame has, so screens key on the instance in both worlds.
        _session.value = newSession(view, message.nextIndex)
    }

    private fun newSession(view: PlayerView, nextIndex: Int) =
        RemoteGameSession(
            initialView = view,
            initialNextIndex = nextIndex,
            token = ::token,
            sendText = { text ->
                socket?.send(text) ?: error("not connected")
            },
        )

    private fun token(): String? = vault.seatToken(code)

    private fun fire(message: ClientMessage) {
        sender.launch {
            try {
                socket?.send(encode(message))
            } catch (_: Exception) {
                // A send into a dying socket; the reconnect loop is already on it.
            }
        }
    }

    private fun encode(message: ClientMessage): String =
        ProtocolJson.encodeToString(ClientMessage.serializer(), message)

    private companion object {
        const val NOTICE_BUFFER = 16

        /**
         * How long a seat may sit pending before the spinner gives up.
         *
         * Long enough that a slow round trip is not mistaken for a lost one, short enough
         * that nobody watches it: a lobby op is one message each way, so five seconds is
         * already far past normal.
         */
        const val PENDING_TIMEOUT_MS = 5_000L

        const val BACKOFF_BASE_MS = 1_000.0
        const val BACKOFF_CAP_MS = 15_000.0

        fun backoffMs(attempt: Int): Long {
            var wait = BACKOFF_BASE_MS
            repeat(attempt - 1) { wait = minOf(wait * 2, BACKOFF_CAP_MS) }
            return wait.toLong()
        }
    }
}

/**
 * One dealt round, over the wire — the same [GameSession] surface the local game has, which
 * is the whole point (design R1/C1): `GameHolder`, `CardStage` and every screen above them
 * cannot tell this from a [LocalGameSession].
 *
 * Frames are built exactly the way the local session builds them: per event, from the view
 * before and the view after, through the same [choreograph] — the per-event views riding on
 * the wire (choreography change 4.1) exist precisely so this line of code could. Catch-up
 * paths (a `sync`, entries without views) collapse to a single frame that lands the table on
 * the present; that is the design, not a shortcut — nobody wants a replay of the minute
 * their tunnel ate.
 *
 * A dispatch resolves against the wire: the server echoes every accepted action back in the
 * events (nobody applies optimistically), so acceptance is "my echo arrived" and refusal is
 * the `error` that came instead.
 */
class RemoteGameSession internal constructor(
    initialView: PlayerView,
    initialNextIndex: Int,
    private val token: () -> String?,
    private val sendText: suspend (String) -> Unit,
) : GameSession {

    private val _view = MutableStateFlow(initialView)
    override val view: StateFlow<PlayerView> = _view.asStateFlow()

    override val playerId: String = initialView.viewerId

    private val _events = MutableSharedFlow<SessionEvent>(
        replay = 1,
        extraBufferCapacity = BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val events: SharedFlow<SessionEvent> = _events.asSharedFlow()

    private val _frames = MutableSharedFlow<List<Frame>>(
        replay = 1,
        extraBufferCapacity = BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val frames: SharedFlow<List<Frame>> = _frames.asSharedFlow()

    // Nobody narrates an online game yet: narration reads full states, and a client has
    // only views. The strip stays empty rather than wrong.
    private val _log = MutableStateFlow<List<String>>(emptyList())
    override val log: StateFlow<List<String>> = _log.asStateFlow()

    override val isOver: Boolean get() = _view.value.phase == GamePhase.SCORING

    /** The log index this session has seen through — the resync cursor. */
    var cursor: Int = initialNextIndex
        private set

    private var pending: CompletableDeferred<String?>? = null
    private var pendingAction: GameAction? = null

    override suspend fun dispatch(action: GameAction): String? {
        val waiter = CompletableDeferred<String?>()
        pending = waiter
        pendingAction = action

        return try {
            sendText(
                ProtocolJson.encodeToString(
                    ClientMessage.serializer(),
                    ClientMessage.Action(token(), action),
                ),
            )
            withTimeout(DISPATCH_TIMEOUT_MS) { waiter.await() }
        } catch (_: TimeoutCancellationException) {
            "the room did not answer"
        } catch (_: IllegalStateException) {
            "not connected"
        } finally {
            pending = null
            pendingAction = null
        }
    }

    // ------------------------------------------------------------------ fed by RemoteRoom

    internal fun applyEvents(message: ServerMessage.Events) {
        val fresh = message.events.filter { it.index >= cursor }
        val batch = mutableListOf<Frame>()
        var bots = 0
        var jumped = fresh.isEmpty() && message.view != null
        var last = _view.value

        for (entry in fresh) {
            val after = entry.view
            if (after == null) {
                // A stored entry with no view — a catch-up; the landing frame below covers it.
                jumped = true
            } else {
                val reveals = entry.revealed.map { PublicReveal(it.playerId, it.position, it.card) }
                batch += Frame(
                    entry.action,
                    choreograph(entry.action, last, after) + revealScene(reveals),
                    after,
                )
                last = after
            }
            if (entry.byBot) bots++

            // The echo: this seat's own accepted action coming back is the dispatch
            // landing. Actorless actions (choosing a coalition leader names nobody) echo
            // with no player, so for those the action alone is the match.
            val mine = entry.playerId == playerId || entry.action.actorId == null
            if (mine && entry.action == pendingAction) {
                pending?.complete(null)
            }
        }

        cursor = maxOf(cursor, message.nextIndex)
        val landing = message.view ?: last
        if (jumped) batch += landingFrame(landing)

        deliver(batch, bots, landing)
    }

    internal fun applySync(message: ServerMessage.Sync) {
        cursor = maxOf(cursor, message.nextIndex)
        val landing = message.view ?: return
        deliver(listOf(landingFrame(landing)), bots = 0, landing = landing)
    }

    /** `between-rounds`: the round is scored and this is where the table now stands. */
    internal fun landOn(view: PlayerView?, nextIndex: Int) {
        cursor = maxOf(cursor, nextIndex)
        view?.let { deliver(listOf(landingFrame(it)), bots = 0, landing = it) }
    }

    /** @return true when the refusal answered a dispatch in flight. */
    internal fun refused(reason: String): Boolean {
        val waiter = pending ?: run {
            _events.tryEmit(SessionEvent.Refused(reason))
            return true
        }
        waiter.complete(reason)
        return true
    }

    /**
     * Publishes one batch: bots first, then the end if this batch reached it, then the view
     * — the same order the local session announces things in, because screens rely on it.
     *
     * `RoundEnded.points` stays empty here: what a round *paid* is the room's bookkeeping
     * and arrives in `between-rounds` standings ([RemoteRoom.standings]); the view knows
     * only the hands.
     */
    private fun deliver(batch: List<Frame>, bots: Int, landing: PlayerView) {
        if (bots > 0) _events.tryEmit(SessionEvent.BotsPlayed(bots))

        val wasOver = _view.value.phase == GamePhase.SCORING
        if (!wasOver && landing.phase == GamePhase.SCORING) {
            _events.tryEmit(
                SessionEvent.RoundEnded(scores = landing.scores.orEmpty(), points = emptyMap()),
            )
        }

        _view.value = landing
        if (batch.isNotEmpty()) _frames.tryEmit(batch)
    }

    /** A frame that lands the table somewhere without narrating the journey. */
    private fun landingFrame(view: PlayerView) =
        Frame(GameAction.Empty(JsonNull), scenes = emptyList(), view = view)

    private companion object {
        const val BUFFER = 64
        const val DISPATCH_TIMEOUT_MS = 10_000L
    }
}
