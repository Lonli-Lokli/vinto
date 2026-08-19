package game.vinto.worker

import game.vinto.bot.BotRunner
import game.vinto.engine.ActionValidator
import game.vinto.engine.GameEngine
import game.vinto.engine.ReduceResult
import game.vinto.engine.Validation
import game.vinto.engine.initializeGame
import game.vinto.engine.projectView
import game.vinto.shapes.Difficulty
import game.vinto.shapes.GameAction
import game.vinto.shapes.GamePhase
import game.vinto.shapes.GameState
import game.vinto.shapes.Sha256
import game.vinto.shapes.VintoJson
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlin.random.Random

/**
 * The room, running the real engine (design D9).
 *
 * Every decision about a room lives here in Kotlin; the JavaScript in `cloudflare/index.mjs`
 * moves bytes and sockets and knows nothing about the game. That split is what lets the same
 * engine the JVM tests verify be the engine a player actually plays against.
 *
 * Three properties matter more than anything else in this file:
 *
 * - **A seat may only act as its own player.** The action's `playerId` is checked against the
 *   seat that sent it *before* the validator runs, so a client cannot draw for somebody else
 *   even with a perfectly well-formed action. `ActionValidator` would catch most of it; this
 *   catches the rest, and it is cheap.
 * - **A client is never sent a hidden card.** Everything leaving the room goes through
 *   [projectView], which replaces cards this seat may not see with a token holding nothing —
 *   not even an id, since ids encode rank.
 * - **Bots run here.** They need to sample opponents' hidden cards to search at all, so a
 *   client-side bot would need the very information the point above withholds.
 *
 * State is JSON in and JSON out, holding nothing in memory between calls: that is what makes
 * WebSocket hibernation safe, and it is why the Durable Object can be evicted mid-game.
 */

/**
 * The room's own fields are written even when they hold their defaults.
 *
 * `VintoJson` omits defaults, which is exactly right for `GameState` — the canonical form has
 * to match TypeScript byte for byte, and TypeScript writes nothing for an absent field. It is
 * exactly wrong here: an unoccupied seat would arrive with no `tokenHash` at all, and
 * `seat.tokenHash !== null` on the JavaScript side is `undefined !== null`, which is *true*.
 * Every empty seat would read as taken. Annotating the room's own types keeps one serialiser
 * for both jobs.
 */
private const val SEAT_COUNT = 4

/**
 * A game needs two people (design R2a).
 *
 * One human against three bots is precisely what `LocalGameSession` does on the device, for
 * free and offline. Hosting it would cost CPU and buy nothing, so the room refuses to.
 */
private const val MIN_HUMANS = 2

/** Long enough to notice and object to, short enough not to be a wait. */
private const val COUNTDOWN_MS = 10_000.0

/**
 * How long a seat is held before a bot plays it (design R5).
 *
 * Long enough for a tunnel, short enough that the other three are not left waiting on
 * somebody's signal. The seat is *held*, not given away — it still belongs to its token.
 */
private const val SEAT_GRACE_MS = 30_000.0

/**
 * How long a running session survives with fewer than two humans connected.
 *
 * Separate from the seat grace and answering a different question: seat grace asks whether the
 * game can continue, this asks whether it *should*. A lone human against three bots is what
 * the device does for free, so hosting it costs CPU and buys nothing (design R1).
 */
private const val LONELY_GRACE_MS = 60_000.0

/** No human connected at all: the room is over, whatever state it was in. */
private const val ROOM_TTL_MS = 120_000.0

/** Created and never started. A lobby nobody came to is still a storage row. */
private const val LOBBY_TTL_MS = 600_000.0

/** Long enough for everyone to read the scoreboard, and no longer. */
private const val FINISHED_TTL_MS = 600_000.0

/**
 * How many actions a seat may fire off at once, and how fast it earns more (design R6).
 *
 * This is the limit that matters, and not because actions are frequent — because they are
 * *expensive*. One action can hand three bots a turn each and cost 1.6 s of CPU
 * (`PLATFORM-GATE.md` 2a.1b), which is the dimension Cloudflare actually bills. An edge rule
 * cannot see inside a WebSocket, so this has to live here.
 *
 * A burst of ten with a sustained rate of one a second is far above anything a person does
 * with a card game and far below anything that costs money.
 */
private const val BUCKET_CAPACITY = 10.0
private const val BUCKET_REFILL_PER_SECOND = 1.0

private const val MILLIS_PER_SECOND = 1000.0

/** How many bot actions to run before handing control back; a guard, not a rule. */
private const val MAX_BOT_STEPS = 200

/** A leaky bucket: tokens left, and when that was last computed. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class Bucket(
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val tokens: Double = BUCKET_CAPACITY,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val lastRefillMs: Double = 0.0,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class Seat(
    val index: Int,
    /**
     * SHA-256 of the seat's token — never the token itself.
     *
     * Storing the hash means a leaked storage dump does not hand out seats, and it means the
     * room can prove a claim without being able to make one. The raw token exists in exactly
     * two places: the client that owns it, and the single message that delivered it.
     */
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val tokenHash: String? = null,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val nickname: String? = null,
    /**
     * The account seam (design R3). Null for every anonymous player, which is all of them
     * today. An account system later maps an account to an `ownerId` and lets it reclaim a
     * seat; nothing else in the room has to change for that to work.
     */
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val ownerId: String? = null,
    /**
     * A bot somebody added to fill the table (design R2a).
     *
     * Distinct from `tokenHash == null`, and the distinction is load-bearing: a seat can be a
     * bot *and* still belong to a token, which is what a disconnected human's seat looks like
     * once a bot has taken it over. Only the first kind may be displaced by a newcomer.
     */
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val isBot: Boolean = false,
    /** The engine player behind this seat once the game is dealt; null in the lobby. */
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val playerId: String? = null,
    /**
     * A bot has played this seat since its owner was last here (design R5).
     *
     * Reported once on reconnect and then cleared. Without it, coming back to a hand that has
     * changed reads as a bug rather than as the thirty seconds you were gone.
     */
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val botPlayedWhileAway: Boolean = false,
) {
    val occupied: Boolean get() = tokenHash != null || isBot

    /** A seat a newcomer may take: filler, not somebody's place. */
    val isFiller: Boolean get() = isBot && tokenHash == null
}

/**
 * One accepted action, in order.
 *
 * The log is the sync cursor: a client that reconnects asks for everything after the last
 * index it saw. Actions rather than states, because an action is small and a state is not.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class LoggedAction(
    val index: Int,
    val seat: Int,
    val playerId: String,
    val action: GameAction,
    /** True when the room's own bot driver produced this, rather than a client. */
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val byBot: Boolean = false,
)

/**
 * Where a room is in its life (design R2a, R5).
 *
 * `LOBBY` and `STARTING` differ only by whether a countdown is running, but they are separate
 * states rather than a nullable deadline because the transition between them is where the
 * alarm is set and cleared, and a transition is easier to get right than an invariant.
 */
@Serializable
enum class RoomPhase { LOBBY, STARTING, PLAYING, BETWEEN_ROUNDS, FINISHED }

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class RoomState(
    val roomId: String,
    val seed: Long,
    val difficulty: Difficulty,
    val seats: List<Seat>,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val phase: RoomPhase = RoomPhase.LOBBY,
    /**
     * When the countdown expires, in epoch milliseconds. Null unless [phase] is `STARTING`.
     *
     * Wall clock, and therefore not the engine's business: it arrives from `index.mjs` on
     * every call that could move it, the same way the seed and the tokens do.
     */
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val startsAtEpochMs: Double? = null,
    /** Null until the game is dealt, which happens when the countdown expires — not before. */
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val game: GameState? = null,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val log: List<LoggedAction> = emptyList(),

    // --- deadlines (design R5) -------------------------------------------------------------
    //
    // Five of them, and a Durable Object has one alarm. They are therefore kept as data and
    // the alarm is scheduled for whichever is earliest ([nextAlarmAt]); when it fires, every
    // deadline is evaluated rather than the one that was expected. That is the difference
    // between a lifecycle that survives eviction and one that works until it does not.
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val createdAtEpochMs: Double = 0.0,
    /** Seat index → when a bot takes it over. Removed on reconnect. */
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val seatGrace: Map<Int, Double> = emptyMap(),
    /** When a session with fewer than two humans ends. Null while two or more are connected. */
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val lonelyUntilEpochMs: Double? = null,
    /** When a room with nobody connected is deleted. Null while anybody is. */
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val emptyUntilEpochMs: Double? = null,
    /** When a finished room is deleted, so the scoreboard outlives the game but not by much. */
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val finishedAtEpochMs: Double? = null,
    /** Seats with a live socket. Sockets are the platform's, so this arrives from `index.mjs`. */
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val connectedSeats: List<Int> = emptyList(),
    /**
     * Seat index → how much budget it has left, and when that was last worked out.
     *
     * Kept in the room's own state rather than in memory, because the object hibernates
     * between messages: an in-memory bucket would refill itself completely every time the
     * room was evicted, which is exactly when a flood would be cheapest to run.
     */
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val buckets: Map<Int, Bucket> = emptyMap(),
) {
    val nextIndex: Int get() = log.size

    val humanCount: Int get() = seats.count { it.tokenHash != null }

    val allSeatsFilled: Boolean get() = seats.all { it.occupied }

    /**
     * Whether a game could start right now.
     *
     * Two humans is the floor and it is not negotiable: one person against three bots is what
     * the device does for free, so hosting it buys nothing (design R1).
     */
    val canStart: Boolean get() = allSeatsFilled && humanCount >= MIN_HUMANS

    /** Humans with a socket open right now, as opposed to humans who hold a seat. */
    val connectedHumans: Int
        get() = seats.count { it.tokenHash != null && it.index in connectedSeats }

    val inSession: Boolean
        get() = phase == RoomPhase.PLAYING || phase == RoomPhase.BETWEEN_ROUNDS

    /**
     * The earliest thing that has to happen, or null if nothing is pending.
     *
     * The whole point of keeping deadlines as data: one alarm, whichever comes first, and the
     * handler works out what actually expired rather than assuming.
     */
    val nextAlarmAt: Double?
        get() = listOfNotNull(
            startsAtEpochMs,
            lonelyUntilEpochMs,
            emptyUntilEpochMs,
            finishedAtEpochMs?.plus(FINISHED_TTL_MS),
            if (phase == RoomPhase.LOBBY || phase == RoomPhase.STARTING) {
                createdAtEpochMs + LOBBY_TTL_MS
            } else {
                null
            },
        ).plus(seatGrace.values).minOrNull()
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
private data class JoinResult(
    val state: RoomState,
    val seat: Int,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val error: String? = null,
    /** Set on a reconnect where a bot took a turn in the meantime; see [Seat.botPlayedWhileAway]. */
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val botPlayedWhileAway: Boolean = false,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
private data class ActionResult(
    val state: RoomState,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val events: List<LoggedAction> = emptyList(),
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val error: String? = null,
    /** Set when the refusal was a rate limit, so a client can back off rather than hammer. */
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val retryAfterMs: Double? = null,
)

@Serializable
private data class SyncResult(val events: List<LoggedAction>, val nextIndex: Int)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
private data class ViewResult(
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val view: game.vinto.engine.PlayerView? = null,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val error: String? = null,
)

/**
 * Deals a real game from a seed the **server** chose.
 *
 * The seed used to come off the query string, which meant a client could reload until it was
 * dealt a Joker and a King. It is now generated where the platform's random source lives —
 * `index.mjs` — and passed in. Kotlin never reaches for randomness itself; that is the same
 * rule the engine follows and for the same reason.
 */
@JsExport
fun newRoom(roomId: String, seed: Double, difficulty: String, nowMs: Double): String {
    val chosen = Difficulty.entries.firstOrNull { it.serialName == difficulty } ?: Difficulty.MODERATE

    val state = RoomState(
        roomId = roomId,
        seed = seed.toLong(),
        difficulty = chosen,
        seats = (0 until SEAT_COUNT).map { Seat(index = it) },
        phase = RoomPhase.LOBBY,
        createdAtEpochMs = nowMs,
        // A room nobody ever connects to is already on the clock. Without this a lobby that
        // failed to attract anybody would sit in storage until somebody noticed.
        emptyUntilEpochMs = nowMs + ROOM_TTL_MS,
    )
    return VintoJson.encodeToString(state)
}

/**
 * Recomputes whether a countdown should be running, after any seat change.
 *
 * The deadline is set **only on the transition into `STARTING`**, never refreshed while it is
 * already running. That is what makes the two rules in R2a hold at once: a human taking a
 * bot's seat at t=8s does not push the start back, while emptying a seat and refilling it
 * gives everybody the full ten seconds again — because emptying passed through `LOBBY` first.
 */
private fun withCountdown(state: RoomState, nowMs: Double): RoomState = when {
    state.phase != RoomPhase.LOBBY && state.phase != RoomPhase.STARTING -> state

    state.canStart && state.phase == RoomPhase.LOBBY ->
        state.copy(phase = RoomPhase.STARTING, startsAtEpochMs = nowMs + COUNTDOWN_MS)

    state.canStart -> state

    state.phase == RoomPhase.STARTING ->
        state.copy(phase = RoomPhase.LOBBY, startsAtEpochMs = null)

    else -> state
}

/**
 * Seats a client, idempotently by the token they hold.
 *
 * A reconnecting player returns to the seat they had rather than consuming a new one, which
 * is the whole reconnect story in design D9 — and the reason a dropped player's seat can be
 * played by a bot in the meantime without losing it.
 */
@Suppress("ReturnCount")
@JsExport
fun joinRoom(stateJson: String, token: String, nickname: String, nowMs: Double): String {
    val state = VintoJson.decodeFromString(RoomState.serializer(), stateJson)
    val hash = Sha256.hex(token)

    // A token that already holds a seat returns to it. This is the reconnect story, and it is
    // safe in a way the old `clientId` was not: knowing somebody's *name* proves nothing, and
    // the only thing that resumes a seat is the secret the room issued for it.
    state.seats.firstOrNull { it.tokenHash == hash }?.let { seat ->
        // Coming back. If a bot played while they were away the hand has moved on, so say so
        // once and clear it — and hand the seat back, which means it stops being a bot.
        val resumed = state.copy(
            seats = state.seats.map {
                if (it.index == seat.index) it.copy(isBot = false, botPlayedWhileAway = false) else it
            },
            seatGrace = state.seatGrace - seat.index,
        )
        return VintoJson.encodeToString(
            JoinResult(resumed, seat.index, botPlayedWhileAway = seat.botPlayedWhileAway),
        )
    }

    if (state.phase != RoomPhase.LOBBY && state.phase != RoomPhase.STARTING) {
        return VintoJson.encodeToString(JoinResult(state, -1, "the game has already started"))
    }

    // A free seat first; failing that, displace a bot somebody added as filler. A bot playing
    // a *disconnected human's* seat is not filler and is not displaceable — that seat belongs
    // to its token, and a stranger taking it while its owner reconnects would make the token
    // guarantee meaningless in the one situation it exists for.
    val target = state.seats.firstOrNull { !it.occupied }
        ?: state.seats.firstOrNull { it.isFiller }
        ?: return VintoJson.encodeToString(JoinResult(state, -1, "room is full"))

    val seated = state.seats.map {
        if (it.index == target.index) {
            it.copy(tokenHash = hash, nickname = nickname, isBot = false)
        } else {
            it
        }
    }

    val next = withCountdown(state.copy(seats = seated), nowMs)
    return VintoJson.encodeToString(JoinResult(next, target.index))
}

/**
 * Adds a bot to the first empty seat.
 *
 * **Any seated player may do this, not only whoever made the room.** A lobby where only the
 * creator can act stalls the moment the creator is the one who wandered off, and the countdown
 * is what keeps the flattening safe: a start somebody else did not want is undoable for ten
 * seconds by taking the bot back out.
 */
@Suppress("ReturnCount")
@JsExport
fun addBot(stateJson: String, token: String, nowMs: Double): String {
    val state = VintoJson.decodeFromString(RoomState.serializer(), stateJson)

    if (state.seats.none { it.tokenHash == Sha256.hex(token) }) {
        return VintoJson.encodeToString(JoinResult(state, -1, "only a seated player may add a bot"))
    }
    if (state.phase != RoomPhase.LOBBY && state.phase != RoomPhase.STARTING) {
        return VintoJson.encodeToString(JoinResult(state, -1, "the game has already started"))
    }

    val free = state.seats.firstOrNull { !it.occupied }
        ?: return VintoJson.encodeToString(JoinResult(state, -1, "every seat is taken"))

    val seats = state.seats.map {
        if (it.index == free.index) it.copy(isBot = true, nickname = "Bot ${free.index + 1}") else it
    }

    val next = withCountdown(state.copy(seats = seats), nowMs)
    return VintoJson.encodeToString(JoinResult(next, free.index))
}

/**
 * Takes a bot back out, which cancels a countdown if one was running.
 *
 * This is the other half of "anyone may add one". Without it, adding a bot is a unilateral
 * decision to start the game; with it, it is a proposal that stands for ten seconds.
 */
@Suppress("ReturnCount")
@JsExport
fun removeBot(stateJson: String, token: String, seatIndex: Int, nowMs: Double): String {
    val state = VintoJson.decodeFromString(RoomState.serializer(), stateJson)

    if (state.seats.none { it.tokenHash == Sha256.hex(token) }) {
        return VintoJson.encodeToString(JoinResult(state, -1, "only a seated player may remove a bot"))
    }
    if (state.phase != RoomPhase.LOBBY && state.phase != RoomPhase.STARTING) {
        return VintoJson.encodeToString(JoinResult(state, -1, "the game has already started"))
    }

    val seat = state.seats.getOrNull(seatIndex)
        ?: return VintoJson.encodeToString(JoinResult(state, -1, "unknown seat $seatIndex"))
    if (!seat.isFiller) {
        return VintoJson.encodeToString(JoinResult(state, -1, "seat $seatIndex is not a bot"))
    }

    val seats = state.seats.map {
        if (it.index == seatIndex) Seat(index = it.index) else it
    }

    val next = withCountdown(state.copy(seats = seats), nowMs)
    return VintoJson.encodeToString(JoinResult(next, seatIndex))
}

/**
 * Deals the game. Called when the countdown expires — from the alarm, never from a message.
 *
 * Dealing here rather than at room creation is what makes the lobby honest. `initializeGame`
 * deals one human and three bots, and its bots begin knowing two of their own cards — the peek
 * a person has to take for themselves. Handing a seat like that to somebody who joined later
 * would give them two cards they never looked at.
 */
@Suppress("ReturnCount")
@JsExport
fun startGame(stateJson: String, nowMs: Double): String {
    val state = VintoJson.decodeFromString(RoomState.serializer(), stateJson)

    if (state.phase != RoomPhase.STARTING) {
        return VintoJson.encodeToString(JoinResult(state, -1, "the room is not starting"))
    }
    if (!state.canStart) {
        return VintoJson.encodeToString(
            JoinResult(state, -1, "a game needs $MIN_HUMANS humans and four seats"),
        )
    }
    state.startsAtEpochMs?.let { deadline ->
        if (nowMs < deadline) {
            return VintoJson.encodeToString(JoinResult(state, -1, "the countdown has not expired"))
        }
    }

    val dealt = initializeGame(state.seed, state.difficulty)

    // The engine's idea of who is human is made to match the room's. A seat with a token is a
    // person and starts having seen nothing; a seat without one is a bot and keeps its peek.
    val players = dealt.players.mapIndexed { index, player ->
        val seat = state.seats[index]
        if (seat.tokenHash != null) {
            player.copy(isHuman = true, isBot = false, knownCardPositions = emptyList())
        } else {
            player.copy(isHuman = false, isBot = true)
        }
    }

    val seats = state.seats.mapIndexed { index, seat -> seat.copy(playerId = players[index].id) }

    val started = state.copy(
        phase = RoomPhase.PLAYING,
        startsAtEpochMs = null,
        seats = seats,
        game = dealt.copy(players = players),
    )

    return VintoJson.encodeToString(JoinResult(playBots(started), 0))
}

/** What a bucket had to say: either a charge went through, or how long to wait. */
private data class Spend(val state: RoomState, val retryAfterMs: Double?)

/**
 * Charges one action to a seat's budget.
 *
 * Refill is computed from elapsed time rather than accrued on a tick, because there are no
 * ticks: the object sleeps between messages, and the only clock it has is the one that arrives
 * with the next one.
 */
private fun spendBudget(state: RoomState, seat: Int, nowMs: Double): Spend {
    val bucket = state.buckets[seat] ?: Bucket(lastRefillMs = nowMs)
    val elapsedSeconds = maxOf(0.0, nowMs - bucket.lastRefillMs) / MILLIS_PER_SECOND
    val available = minOf(BUCKET_CAPACITY, bucket.tokens + elapsedSeconds * BUCKET_REFILL_PER_SECOND)

    if (available < 1.0) {
        // Refused, and the refusal is *cheap* — no validation, no reduce, and above all no bot
        // search. Serving a throttled action at a discount would defeat the point of throttling
        // the expensive thing.
        val waitSeconds = (1.0 - available) / BUCKET_REFILL_PER_SECOND
        return Spend(
            state.copy(buckets = state.buckets + (seat to Bucket(available, nowMs))),
            retryAfterMs = waitSeconds * MILLIS_PER_SECOND,
        )
    }

    return Spend(
        state.copy(buckets = state.buckets + (seat to Bucket(available - 1.0, nowMs))),
        retryAfterMs = null,
    )
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
private data class LifecycleResult(
    val state: RoomState,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val nextAlarmAtEpochMs: Double? = null,
    /** The room asked to be deleted. The caller owns storage, so the caller does it. */
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val deleted: Boolean = false,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val started: Boolean = false,
    /** Seats a bot has just taken over, so their owners can be told when they return. */
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val tookOver: List<Int> = emptyList(),
)

/**
 * Recomputes every deadline from who is connected.
 *
 * Called whenever a socket opens or closes. Presence is the platform's business — sockets
 * survive hibernation and `ctx.getWebSockets()` is authoritative after a wake — so the caller
 * passes the seats it can see rather than the room trying to remember.
 */
@JsExport
fun updatePresence(stateJson: String, connectedSeatsCsv: String, nowMs: Double): String {
    val state = VintoJson.decodeFromString(RoomState.serializer(), stateJson)
    val connected = connectedSeatsCsv.split(",").mapNotNull { it.trim().toIntOrNull() }

    val present = state.copy(connectedSeats = connected)

    // A seat whose socket came back keeps its seat and loses its grace. A seat whose socket
    // went away starts one — the seat is held, not surrendered, because it belongs to a token.
    val grace = present.seats
        .filter { it.tokenHash != null }
        .fold(present.seatGrace) { acc, seat ->
            when {
                seat.index in connected -> acc - seat.index
                acc.containsKey(seat.index) -> acc
                seat.isBot -> acc // already taken over; nothing further to schedule
                else -> acc + (seat.index to nowMs + SEAT_GRACE_MS)
            }
        }

    val humans = present.connectedHumans

    // Two clocks, two questions. `lonely` asks whether a session should continue at all;
    // `empty` asks whether the room should exist. They overlap on purpose — a game losing its
    // last human trips both, and the earlier one wins, which is the lonely one.
    val lonely = when {
        !present.inSession -> null
        humans >= MIN_HUMANS -> null
        present.lonelyUntilEpochMs != null -> present.lonelyUntilEpochMs
        else -> nowMs + LONELY_GRACE_MS
    }

    val empty = when {
        humans > 0 -> null
        present.emptyUntilEpochMs != null -> present.emptyUntilEpochMs
        else -> nowMs + ROOM_TTL_MS
    }

    val next = present.copy(seatGrace = grace, lonelyUntilEpochMs = lonely, emptyUntilEpochMs = empty)
    return VintoJson.encodeToString(LifecycleResult(next, nextAlarmAtEpochMs = next.nextAlarmAt))
}

/**
 * Whatever was due. Called from the alarm, and never assumes which deadline woke it.
 *
 * Order matters and is not arbitrary: deletion is checked before takeover, because a room
 * that is ending has no use for a bot playing one more turn, and doing it the other way round
 * would burn a search on a game nobody is left to see.
 */
@Suppress("ReturnCount")
@JsExport
fun onAlarm(stateJson: String, nowMs: Double): String {
    var state = VintoJson.decodeFromString(RoomState.serializer(), stateJson)

    val due = { at: Double? -> at != null && nowMs >= at }

    // 1. The room is over, for any of three reasons.
    val lobbyExpired = (state.phase == RoomPhase.LOBBY || state.phase == RoomPhase.STARTING) &&
        nowMs >= state.createdAtEpochMs + LOBBY_TTL_MS
    val finishedExpired = state.finishedAtEpochMs?.let { nowMs >= it + FINISHED_TTL_MS } == true

    if (due(state.emptyUntilEpochMs) || lobbyExpired || finishedExpired) {
        return VintoJson.encodeToString(LifecycleResult(state, deleted = true))
    }

    // 2. A session with nobody left to play it ends, and is then deleted on the finished TTL
    //    like any other finished room — the scoreboard is still worth a moment.
    if (due(state.lonelyUntilEpochMs)) {
        state = state.copy(
            phase = RoomPhase.FINISHED,
            lonelyUntilEpochMs = null,
            startsAtEpochMs = null,
            finishedAtEpochMs = nowMs,
        )
        return VintoJson.encodeToString(
            LifecycleResult(state, nextAlarmAtEpochMs = state.nextAlarmAt),
        )
    }

    // 3. The countdown.
    if (due(state.startsAtEpochMs) && state.canStart) {
        val started = VintoJson.decodeFromString(
            JoinResult.serializer(),
            startGame(VintoJson.encodeToString(state), nowMs),
        )
        if (started.error == null) {
            val next = started.state
            return VintoJson.encodeToString(
                LifecycleResult(next, nextAlarmAtEpochMs = next.nextAlarmAt, started = true),
            )
        }
    }

    // 4. Seats whose grace has run out are played by bots. The seat keeps its token — it is
    //    held for its owner, not handed to anybody else (design R2a).
    val expired = state.seatGrace.filterValues { nowMs >= it }.keys
    if (expired.isNotEmpty()) {
        val seats = state.seats.map {
            if (it.index in expired) it.copy(isBot = true, botPlayedWhileAway = true) else it
        }
        state = state.copy(seats = seats, seatGrace = state.seatGrace - expired)
        state = playBots(state)
        return VintoJson.encodeToString(
            LifecycleResult(
                state,
                nextAlarmAtEpochMs = state.nextAlarmAt,
                tookOver = expired.toList(),
            ),
        )
    }

    return VintoJson.encodeToString(
        LifecycleResult(state, nextAlarmAtEpochMs = state.nextAlarmAt),
    )
}

/**
 * Applies one client action, then plays out every bot turn that follows it.
 *
 * Returning the bots' actions in the same response is deliberate: a client sends one thing
 * and receives everything that happened because of it, so there is no round of polling to
 * discover that three bots have moved.
 */
@Suppress("ReturnCount")
@JsExport
fun applyAction(stateJson: String, token: String, actionJson: String, nowMs: Double): String {
    val state = VintoJson.decodeFromString(RoomState.serializer(), stateJson)

    // The seat is *derived* from the credential rather than asserted next to it. There is no
    // seat parameter to disagree with the token, so there is no way to send one seat's token
    // and another seat's number and see which check notices first.
    val seatEntry = state.seats.firstOrNull { it.tokenHash == Sha256.hex(token) }
        ?: return VintoJson.encodeToString(ActionResult(state, error = "no seat holds that token"))
    val seat = seatEntry.index

    // The budget is charged before anything else costs anything. Order matters: validating
    // first would be cheap, but reducing and then running three bot searches is not, and a
    // flood is only bounded if the refusal happens before the expensive part.
    val spend = spendBudget(state, seat, nowMs)
    spend.retryAfterMs?.let {
        return VintoJson.encodeToString(
            ActionResult(spend.state, error = "too many actions", retryAfterMs = it),
        )
    }
    val charged = spend.state

    // No game, nothing to act on. A lobby refuses game actions rather than dealing one on
    // demand, or the countdown would be advisory.
    val game = charged.game
        ?: return VintoJson.encodeToString(ActionResult(charged, error = "the game has not started"))

    val action = try {
        VintoJson.decodeFromString(GameAction.serializer(), actionJson)
    } catch (failure: IllegalArgumentException) {
        return VintoJson.encodeToString(
            ActionResult(charged, error = "unreadable action: ${failure.message}"),
        )
    }

    // The seat boundary, checked before the engine sees anything. An action whose payload
    // names another player is refused here whether or not it would have been legal for them.
    actorOf(action)?.let { claimed ->
        if (claimed != seatEntry.playerId) {
            return VintoJson.encodeToString(
                ActionResult(charged, error = "seat $seat may only act as ${seatEntry.playerId}"),
            )
        }
    }

    when (val validation = ActionValidator.validate(game, action)) {
        is Validation.Invalid ->
            return VintoJson.encodeToString(ActionResult(charged, error = validation.reason))

        Validation.Valid -> Unit
    }

    val reduced = when (val result = GameEngine.reduce(game, action)) {
        is ReduceResult.Success -> result.state
        is ReduceResult.Failure ->
            return VintoJson.encodeToString(ActionResult(charged, error = result.reason))
    }

    val accepted = LoggedAction(
        index = charged.nextIndex,
        seat = seat,
        playerId = seatEntry.playerId ?: "",
        action = action,
    )

    val afterBots = playBots(charged.copy(game = reduced, log = charged.log + accepted))
    return VintoJson.encodeToString(
        ActionResult(afterBots, events = afterBots.log.drop(charged.log.size)),
    )
}

/**
 * Runs the room's bots until it is a seated player's move again.
 *
 * Every bot action goes through the same validator a client's would. That is not caution
 * about the bot so much as about the seam: if the room ever accepted something from its own
 * driver that it would refuse from a player, the two would be playing different games.
 */
private fun playBots(start: RoomState): RoomState {
    if (start.game == null) return start

    val runner = BotRunner(start.difficulty, Random(start.seed))
    var state = start
    var steps = 0

    while (steps++ < MAX_BOT_STEPS && state.game?.phase != GamePhase.SCORING) {
        val game = state.game ?: break
        val action = runner.nextAction(game) ?: break
        val actor = actorOf(action)
        val seat = state.seats.firstOrNull { it.playerId == actor }

        // Three reasons to stop, all of them "this is not the room's move to make":
        //  - it belongs to a seated *person*, whatever the runner thinks. `tokenHash`, not
        //    `occupied`: since the lobby landed, an occupied seat may be a bot the room is
        //    supposed to play, and using `occupied` here made the room refuse to move its
        //    own bots — a game that stopped dead the first time a window opened;
        //  - the validator refuses it, which would mean the room and its own driver
        //    disagreed about the rules;
        //  - the engine refuses it after validation, which should be impossible.
        val reduced = when {
            seat != null && seat.tokenHash != null -> null
            ActionValidator.validate(game, action) is Validation.Invalid -> null
            else -> (GameEngine.reduce(game, action) as? ReduceResult.Success)?.state
        } ?: break

        state = state.copy(
            game = reduced,
            log = state.log + LoggedAction(
                index = state.nextIndex,
                seat = seat?.index ?: -1,
                playerId = actor ?: "",
                action = action,
                byBot = true,
            ),
        )
    }

    return state
}

/**
 * What one seat is allowed to see.
 *
 * The only shape of the game that ever leaves the room. Sending `RoomState` instead would
 * hand every client every hand, which is the failure the whole server-authoritative design
 * exists to prevent.
 */
@JsExport
fun viewForSeat(stateJson: String, seat: Int): String {
    val state = VintoJson.decodeFromString(RoomState.serializer(), stateJson)
    val seatEntry = state.seats.getOrNull(seat)
        ?: return VintoJson.encodeToString(ViewResult(error = "unknown seat $seat"))
    val game = state.game
        ?: return VintoJson.encodeToString(ViewResult(error = "the game has not started"))
    val playerId = seatEntry.playerId
        ?: return VintoJson.encodeToString(ViewResult(error = "seat $seat has no player yet"))

    return VintoJson.encodeToString(ViewResult(view = projectView(game, playerId)))
}

/**
 * Which seat a token holds, or -1.
 *
 * Lets the socket layer resolve a client to a seat without ever holding a seat number it did
 * not derive from a credential.
 */
@JsExport
fun seatForToken(stateJson: String, token: String): Int {
    val state = VintoJson.decodeFromString(RoomState.serializer(), stateJson)
    val hash = Sha256.hex(token)
    return state.seats.firstOrNull { it.tokenHash == hash }?.index ?: -1
}

/** Events a reconnecting client has not seen. The log index is the cursor (design D9). */
@JsExport
fun eventsSince(stateJson: String, sinceIndex: Int): String {
    val state = VintoJson.decodeFromString(RoomState.serializer(), stateJson)
    val from = sinceIndex.coerceIn(0, state.log.size)
    return VintoJson.encodeToString(SyncResult(state.log.drop(from), state.nextIndex))
}

/**
 * Who an action claims to be from.
 *
 * `null` for the few actions that name nobody — setting the coalition leader, and the debug
 * ones — which are checked by the validator alone.
 */
// Detekt reads this as complex; what it is measuring is the size of the action union, not
// the difficulty of the code. An exhaustive `when` with no `else` is the point: a new action
// becomes a compile error here, which is where a missing seat check would otherwise hide.
@Suppress("CyclomaticComplexMethod")
private fun actorOf(action: GameAction): String? = when (action) {
    is GameAction.DrawCard -> action.payload.playerId
    is GameAction.PlayDiscard -> action.payload.playerId
    is GameAction.SwapCard -> action.payload.playerId
    is GameAction.DiscardCard -> action.payload.playerId
    is GameAction.UseCardAction -> action.payload.playerId
    is GameAction.SelectActionTarget -> action.payload.playerId
    is GameAction.ConfirmPeek -> action.payload.playerId
    is GameAction.SkipPeek -> action.payload.playerId
    is GameAction.ExecuteJackSwap -> action.payload.playerId
    is GameAction.SkipJackSwap -> action.payload.playerId
    is GameAction.ExecuteQueenSwap -> action.payload.playerId
    is GameAction.SkipQueenSwap -> action.payload.playerId
    is GameAction.DeclareKingAction -> action.payload.playerId
    is GameAction.ParticipateInTossIn -> action.payload.playerId
    is GameAction.PlayerTossInFinished -> action.payload.playerId
    is GameAction.FinishTossInPeriod -> action.payload.initiatorId
    is GameAction.CallVinto -> action.payload.playerId
    is GameAction.ProcessAiTurn -> action.payload.playerId
    is GameAction.PeekSetupCard -> action.payload.playerId
    is GameAction.FinishSetup -> action.payload.playerId
    is GameAction.SetCoalitionLeader -> null
    is GameAction.UpdateDifficulty -> null
    is GameAction.SetNextDrawCard -> null
    is GameAction.SwapHandWithDeck -> null
    is GameAction.Empty -> null
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
private data class LobbySeat(
    val index: Int,
    val occupied: Boolean,
    val isBot: Boolean,
    /** True only for a bot somebody added as filler — the ones a newcomer may displace. */
    val removable: Boolean,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val nickname: String? = null,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
private data class LobbyView(
    val phase: RoomPhase,
    val seats: List<LobbySeat>,
    val humans: Int,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val startsAtEpochMs: Double? = null,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val msUntilStart: Double? = null,
)

/**
 * The lobby, as anybody in it may see it.
 *
 * Deliberately not [RoomState]: that holds token hashes and, once dealt, every hand. This is
 * seat occupancy and a countdown, which is all a lobby screen needs and all it should get.
 */
@JsExport
fun lobbyView(stateJson: String, nowMs: Double): String {
    val state = VintoJson.decodeFromString(RoomState.serializer(), stateJson)

    return VintoJson.encodeToString(
        LobbyView(
            phase = state.phase,
            seats = state.seats.map {
                LobbySeat(
                    index = it.index,
                    occupied = it.occupied,
                    isBot = it.isBot,
                    removable = it.isFiller,
                    nickname = it.nickname,
                )
            },
            humans = state.humanCount,
            startsAtEpochMs = state.startsAtEpochMs,
            msUntilStart = state.startsAtEpochMs?.let { maxOf(0.0, it - nowMs) },
        ),
    )
}

/** Exposed for the gate harness; `SEAT_COUNT` is a design constant, not a setting. */
@JsExport
fun seatCount(): Int = SEAT_COUNT

/**
 * The earliest deadline this room is waiting on, or 0 for none.
 *
 * Exported rather than recomputed in JavaScript, which is where the first version of this put
 * it: five deadlines duplicated across two languages is a drift waiting to happen, and the
 * symptom would be an alarm that fires at the wrong time — which looks like nothing at all
 * until a room fails to clean itself up.
 */
@JsExport
fun nextAlarmAt(stateJson: String): Double =
    VintoJson.decodeFromString(RoomState.serializer(), stateJson).nextAlarmAt ?: 0.0

/** The countdown length, so a harness cannot drift from the implementation. */
@JsExport
fun countdownMs(): Double = COUNTDOWN_MS
