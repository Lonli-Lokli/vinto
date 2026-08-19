package game.vinto.worker

import game.vinto.shapes.Prng
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Room state and transitions for the Durable Object, task 2a.3.
 *
 * This is **not** the game engine — that is phase 4. What it models is the shape the
 * Durable Object needs regardless of what the engine does: exactly four seats, an
 * authoritative append-only log, a monotonic index used as the sync cursor, and seeded
 * randomness carried in the state rather than drawn from ambient `Math.random`.
 *
 * Everything here is a pure function over JSON in and JSON out. That is deliberate:
 * - it is the boundary design D2 specifies, so replacing these transitions with
 *   `GameEngine.reduce` later changes the body and not the seam;
 * - it keeps the JavaScript shim free of game knowledge — it moves bytes and sockets;
 * - it means the Durable Object holds no authoritative state in memory, which is what
 *   makes WebSocket hibernation safe (design D9).
 */

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

const val SEAT_COUNT = 4

/** A 52-card deck plus two jokers — the bound every action draws against. */
private const val DECK_SIZE = 54

@Serializable
data class Seat(
    val index: Int,
    val clientId: String? = null,
    val nickname: String? = null,
) {
    /** Empty seats are filled by bots at start, per design D9. */
    val occupied: Boolean get() = clientId != null
}

@Serializable
data class LoggedAction(
    val index: Int,
    val seat: Int,
    val type: String,
    /** Drawn from the seeded generator, so the log is reproducible from `seed` alone. */
    val value: Int,
    val rngState: Long,
)

@Serializable
data class RoomState(
    val roomId: String,
    val seed: Int,
    val rngState: Long,
    val seats: List<Seat>,
    val log: List<LoggedAction> = emptyList(),
) {
    val nextIndex: Int get() = log.size
}

@Serializable
private data class JoinResult(val state: RoomState, val seat: Int, val error: String? = null)

@Serializable
private data class ActionResult(
    val state: RoomState,
    val event: LoggedAction? = null,
    val error: String? = null,
)

@Serializable
private data class SyncResult(val events: List<LoggedAction>, val nextIndex: Int)

@JsExport
fun newRoom(roomId: String, seed: Int): String {
    val state = RoomState(
        roomId = roomId,
        seed = seed,
        rngState = Prng.seed(seed.toLong()),
        seats = (0 until SEAT_COUNT).map { Seat(index = it) },
    )
    return json.encodeToString(state)
}

/**
 * Seats a client. Idempotent by `clientId` so a reconnecting player returns to the same
 * seat rather than consuming a new one — the reconnect path in design D9 depends on this.
 */
@JsExport
fun joinRoom(stateJson: String, clientId: String, nickname: String): String {
    val state = json.decodeFromString<RoomState>(stateJson)

    val existing = state.seats.firstOrNull { it.clientId == clientId }
    if (existing != null) return json.encodeToString(JoinResult(state, existing.index))

    val free = state.seats.firstOrNull { !it.occupied }
        ?: return json.encodeToString(JoinResult(state, -1, "room is full"))

    val seated = state.seats.map {
        if (it.index == free.index) it.copy(clientId = clientId, nickname = nickname) else it
    }
    return json.encodeToString(JoinResult(state.copy(seats = seated), free.index))
}

/**
 * Applies one action and appends it to the log, advancing the seeded generator.
 *
 * The engine will replace the body; the contract that must survive is that the new state
 * is a pure function of the old state and the action, so the log replays to the same
 * result on any machine.
 */
@JsExport
fun applyAction(stateJson: String, seat: Int, type: String): String {
    val state = json.decodeFromString<RoomState>(stateJson)

    if (seat !in 0 until SEAT_COUNT) {
        return json.encodeToString(ActionResult(state, null, "unknown seat $seat"))
    }
    if (!state.seats[seat].occupied) {
        return json.encodeToString(ActionResult(state, null, "seat $seat is not occupied"))
    }

    val draw = Prng.nextInt(state.rngState, DECK_SIZE)
    val event = LoggedAction(
        index = state.nextIndex,
        seat = seat,
        type = type,
        value = draw.value.toInt(),
        rngState = draw.state,
    )
    val next = state.copy(rngState = draw.state, log = state.log + event)
    return json.encodeToString(ActionResult(next, event))
}

/** Events a reconnecting client has not seen. The log index is the sync cursor (D9). */
@JsExport
fun eventsSince(stateJson: String, sinceIndex: Int): String {
    val state = json.decodeFromString<RoomState>(stateJson)
    val from = sinceIndex.coerceIn(0, state.log.size)
    return json.encodeToString(SyncResult(state.log.drop(from), state.nextIndex))
}
