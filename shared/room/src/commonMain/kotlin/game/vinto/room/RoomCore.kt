package game.vinto.room

import game.vinto.bot.BotRunner
import game.vinto.engine.ActionValidator
import game.vinto.engine.GameEngine
import game.vinto.engine.PublicReveal
import game.vinto.engine.ReduceResult
import game.vinto.engine.Validation
import game.vinto.engine.calculateFinalScores
import game.vinto.engine.calculateRoundPoints
import game.vinto.engine.initializeGame
import game.vinto.engine.projectView
import game.vinto.protocol.LobbySeat
import game.vinto.protocol.LobbyView
import game.vinto.protocol.LoggedAction
import game.vinto.protocol.PlayerProfile
import game.vinto.protocol.RoomPhase
import game.vinto.protocol.RoundResult
import game.vinto.shapes.Difficulty
import game.vinto.shapes.GameAction
import game.vinto.shapes.GamePhase
import game.vinto.shapes.GameState
import game.vinto.shapes.LeaderIdPayload
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.Prng
import game.vinto.shapes.Sha256
import game.vinto.shapes.VintoJson
import game.vinto.shapes.actorId
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

/**
 * How long a session lasts, from `VINTO_RULES.md` — "Play continues for a set time".
 *
 * Wall clock, and therefore the room's business and never the engine's: the reducer is pure
 * and the purity guard fails the build on any clock in it. A session ending is expressed to
 * the engine as "no further rounds", which is a fact about the room rather than about time.
 */
private const val SESSION_MS = 30 * 60 * 1000.0

/** How many bot actions to run before handing control back; a guard, not a rule. */
private const val MAX_BOT_STEPS = 200

/**
 * How long a toss-in window waits on humans before the room finishes it for them (9.4).
 *
 * The one place online play cannot simply wait: a toss-in holds *every* seat, so one person
 * looking at their phone is four people not playing. Fifteen seconds is long enough to
 * decide whether the card in your hand matches, and the deadline only exists while a human
 * is actually being waited on — bots resolve their windows in the same request that opened
 * them.
 */
private const val TOSS_IN_MS = 15_000.0

/**
 * How long the coalition may argue about its leader before the room appoints one (9.4).
 *
 * A little longer than the toss-in, because it is a real decision — but not open-ended,
 * because the final round is the one part of the game the caller is entitled to see played
 * out. The default is deterministic: the first coalition seat in table order.
 */
private const val LEADER_MS = 20_000.0

// PlayerProfile, RoundResult, LoggedAction, RoomPhase, LobbySeat and LobbyView moved verbatim
// to `shared/protocol` (game.vinto.protocol): they travel on the wire, so the client and the
// room must read one declaration rather than two that resemble each other. The room still
// owns how they are produced.

/**
 * Trims a nickname to something displayable, or gives it a name.
 *
 * 1–16 characters after collapsing whitespace, letters, digits, spaces and a little
 * punctuation. Not unique, and not meant to be — two players may both be "Bob", and the view
 * distinguishes them by seat. Rejecting duplicates would be a worse experience than the
 * ambiguity, and would leak who is already in a room.
 */
internal fun sanitiseNickname(raw: String, seatIndex: Int): String =
    cleanNickname(raw).ifEmpty { "Player ${seatIndex + 1}" }

/**
 * The same rule without the fallback, for the places where "no name" is a legitimate answer.
 *
 * A seat must be called something, so [sanitiseNickname] names an unnamed one after its
 * index. A room's host need not be: the public list simply shows no host. Splitting the two
 * is what lets the registry apply one rule rather than inventing a second — and it must apply
 * one, because a nickname posted to `/rooms` is displayed to strangers who never agreed to
 * read whatever length of whatever characters somebody sent.
 */
internal fun cleanNickname(raw: String): String {
    val collapsed = raw.trim().replace(Regex("\\s+"), " ")
    val allowed = collapsed.filter { it.isLetterOrDigit() || it == ' ' || it in "-_.'" }
    return allowed.take(MAX_NICKNAME_LENGTH).trim()
}

private const val MAX_NICKNAME_LENGTH = 16

/**
 * A session: several rounds, one clock, cumulative points.
 *
 * The room holds this rather than the engine, because the engine deals with a *round*. A
 * session is scheduling and bookkeeping, and neither belongs in a pure reducer.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class SessionState(
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val rounds: List<RoundResult> = emptyList(),
    /** Thirty minutes from the **first deal**, not from when the room was made. */
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val endsAtEpochMs: Double? = null,
    /**
     * The round the buzzer threw away, if any.
     *
     * Recorded because the standings cannot be recomputed from the round recordings alone —
     * the engine replays every round it is given and has no idea one of them did not count.
     */
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val discardedRound: Int? = null,
    /** Seats who have agreed to another round. Cleared when one is dealt. */
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val readyForNext: List<Int> = emptyList(),
) {
    /** Cumulative round points, which is what the final ranking is made of. */
    val standings: Map<String, Int>
        get() = rounds.flatMap { it.points.entries }
            .groupBy({ it.key }, { it.value })
            .mapValues { (_, values) -> values.sum() }
}

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
    /**
     * Everything display-related about whoever holds this seat.
     *
     * A record rather than a loose nickname so that the next thing anybody wants to show —
     * an avatar, a flag, a pronoun — is a field here rather than a change to every message
     * that carries a seat.
     */
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val profile: PlayerProfile? = null,
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

    // --- the round on record (migrate task 9.2) --------------------------------------------
    //
    // Enough to reconstruct the round in progress — or the one just filed — as a
    // `GameRecording`: the state it was dealt into, where its actions start on the log, and
    // the seed it was dealt from. All default-added, so a stored room from before this
    // feature decodes; a room that predates them simply has no recording to serve.
    /** The dealt state of the current round, after the seat mutation. Replaced per deal. */
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val roundInitial: GameState? = null,
    /** Where the current round's actions begin on the (never-truncated) log. */
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val roundStartLogIndex: Int = 0,
    /**
     * When the current round was dealt, in epoch milliseconds.
     *
     * Only analytics reads it: how long a round takes is a fact nobody was recording, and it
     * is the difference between "people play" and "people start and leave". Defaulted and
     * nullable so a room stored before this field existed still decodes.
     */
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val roundStartedAtEpochMs: Double? = null,
    /** The seed the current round was dealt from — `seedForRound` at deal time. */
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val roundSeed: Long = 0,
    /**
     * The state a just-filed round ended on, kept because `closeSession` discards [game]
     * when a room finishes — and the recording of the last round outlives the round.
     */
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val roundFinal: GameState? = null,

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
    /** Rounds played, points carried, and what the clock says (design R2, R2b). */
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val session: SessionState = SessionState(),

    // --- pacing (migrate task 9.4) ---------------------------------------------------------
    //
    // Wall-clock deadlines on the two situations where the whole table waits on a human:
    // an open toss-in window, and the coalition's leader choice. Recomputed by `withPacing`
    // after every change to the game; the running deadline is kept rather than refreshed, so
    // unrelated actions do not push it back. Both are the room's business, never the
    // engine's — the reducer has no clock, so the expiry arrives as an ordinary action.
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val tossInDeadlineEpochMs: Double? = null,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val leaderDeadlineEpochMs: Double? = null,
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
            tossInDeadlineEpochMs,
            leaderDeadlineEpochMs,
            finishedAtEpochMs?.plus(FINISHED_TTL_MS),
            if (phase == RoomPhase.LOBBY || phase == RoomPhase.STARTING) {
                createdAtEpochMs + LOBBY_TTL_MS
            } else {
                null
            },
            session.endsAtEpochMs,
        ).plus(seatGrace.values).minOrNull()
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class JoinResult(
    val state: RoomState,
    val seat: Int,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val error: String? = null,
    /** Set on a reconnect where a bot took a turn in the meantime; see [Seat.botPlayedWhileAway]. */
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val botPlayedWhileAway: Boolean = false,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class ActionResult(
    val state: RoomState,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val events: List<LoggedAction> = emptyList(),
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val error: String? = null,
    /** Set when the refusal was a rate limit, so a client can back off rather than hammer. */
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val retryAfterMs: Double? = null,
)

@Serializable
internal data class SyncResult(val events: List<LoggedAction>, val nextIndex: Int)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class ViewResult(
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
            it.copy(
                tokenHash = hash,
                profile = PlayerProfile(nickname = sanitiseNickname(nickname, it.index)),
                isBot = false,
            )
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
        if (it.index == free.index) {
            it.copy(isBot = true, profile = PlayerProfile(nickname = "Bot ${free.index + 1}"))
        } else {
            it
        }
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

    val roundSeed = seedForRound(state.seed, state.session.rounds.size)
    val dealt = initializeGame(roundSeed, state.difficulty)

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
        // The round's recording begins here: the dealt state after the seat mutation is the
        // `initialState` a replay starts from, and the log index marks where its actions do.
        roundInitial = dealt.copy(players = players),
        roundStartLogIndex = state.log.size,
        roundStartedAtEpochMs = nowMs,
        roundSeed = roundSeed,
        roundFinal = null,
        session = state.session.copy(
            // The clock starts at the FIRST deal, so a lobby that took a while to fill does
            // not eat into the game. Later rounds inherit the deadline they were dealt under.
            endsAtEpochMs = state.session.endsAtEpochMs ?: (nowMs + SESSION_MS),
            readyForNext = emptyList(),
        ),
    )

    return VintoJson.encodeToString(JoinResult(withPacing(playBots(started), nowMs), 0))
}

/** The humans an open toss-in window is still waiting on. Empty when nothing waits. */
private fun laggingHumans(state: RoomState): List<String> {
    val game = state.game ?: return emptyList()
    val toss = game.activeTossIn ?: return emptyList()
    return game.players
        .filter { it.isHuman && it.id !in toss.playersReadyForNextTurn }
        .map { it.id }
}

/** Whether the final round is stalled on the coalition choosing its leader. */
private fun awaitingLeader(state: RoomState): Boolean {
    val game = state.game ?: return false
    return game.phase == GamePhase.FINAL &&
        game.vintoCallerId != null &&
        game.coalitionLeaderId == null
}

/**
 * Recomputes the pacing deadlines from the game on the table.
 *
 * A deadline exists exactly while its situation does, and a *running* one is kept rather
 * than refreshed — an unrelated action must not buy the lagging player more time. Applied
 * after everything that changes the game: an action, a deal, a takeover, an expiry.
 */
private fun withPacing(state: RoomState, nowMs: Double): RoomState {
    val playing = state.phase == RoomPhase.PLAYING
    return state.copy(
        tossInDeadlineEpochMs = if (playing && laggingHumans(state).isNotEmpty()) {
            state.tossInDeadlineEpochMs ?: (nowMs + TOSS_IN_MS)
        } else {
            null
        },
        leaderDeadlineEpochMs = if (playing && awaitingLeader(state)) {
            state.leaderDeadlineEpochMs ?: (nowMs + LEADER_MS)
        } else {
            null
        },
    )
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
internal data class LifecycleResult(
    val state: RoomState,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val nextAlarmAtEpochMs: Double? = null,
    /** The room asked to be deleted. The caller owns storage, so the caller does it. */
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val deleted: Boolean = false,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val started: Boolean = false,
    /** Seats a bot has just taken over, so their owners can be told when they return. */
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val tookOver: List<Int> = emptyList(),
)

/**
 * A round that has reached scoring, filed and put away.
 *
 * Whether the session continues turns on the clock, and this is the only place that asks: past
 * the buzzer the session ends here rather than dealing again, which is how "a declared Vinto
 * plays out" works — the round finishes normally and *then* finds the session over.
 */
private fun settleRound(state: RoomState, nowMs: Double): RoomState {
    if (state.game?.phase != GamePhase.SCORING) return state

    val recorded = recordRoundEnd(state)
    val sessionOver = recorded.session.endsAtEpochMs?.let { nowMs >= it } == true

    return if (sessionOver) {
        recorded.copy(
            phase = RoomPhase.FINISHED,
            game = null,
            finishedAtEpochMs = nowMs,
        )
    } else {
        recorded.copy(phase = RoomPhase.BETWEEN_ROUNDS)
    }
}

/**
 * Agreeing to another round.
 *
 * Every *connected* human has to agree, and the last one to say so is what deals it. Somebody
 * who declines simply leaves — their seat becomes a bot, the table carries on if two humans
 * remain, and the lonely grace takes it from there if not. One mechanism rather than two.
 */
@Suppress("ReturnCount")
fun readyForNextRound(stateJson: String, token: String, nowMs: Double): String {
    val state = VintoJson.decodeFromString(RoomState.serializer(), stateJson)

    val seat = state.seats.firstOrNull { it.tokenHash == Sha256.hex(token) }
        ?: return VintoJson.encodeToString(JoinResult(state, -1, "no seat holds that token"))
    if (state.phase != RoomPhase.BETWEEN_ROUNDS) {
        return VintoJson.encodeToString(JoinResult(state, -1, "there is no round to agree to"))
    }

    val agreed = (state.session.readyForNext + seat.index).distinct()

    // Everyone connected has to agree — but if presence has not been recorded at all, fall
    // back to every seated human rather than to nobody. Counting an empty set would make one
    // click deal the round, which is the opposite of what "everyone agrees" means.
    val connectedHumanSeats = state.seats.count { it.tokenHash != null && it.index in state.connectedSeats }
    val waitingOn = if (connectedHumanSeats > 0) {
        connectedHumanSeats
    } else {
        state.seats.count { it.tokenHash != null }
    }

    val ready = state.copy(session = state.session.copy(readyForNext = agreed))
    if (agreed.size < waitingOn) {
        return VintoJson.encodeToString(JoinResult(ready, seat.index))
    }

    // Everyone agreed. A new round is always dealt while the session is live, even with a
    // minute left — no cutoff to tune, and nothing to explain about why the game refused.
    val dealt = VintoJson.decodeFromString(
        JoinResult.serializer(),
        startGame(
            VintoJson.encodeToString(ready.copy(phase = RoomPhase.STARTING, startsAtEpochMs = nowMs)),
            nowMs,
        ),
    )
    return VintoJson.encodeToString(JoinResult(dealt.state, seat.index, error = dealt.error))
}

/**
 * The seed for round `n`, derived from the session's.
 *
 * Advancing the same generator the engine uses, `n` times, rather than adding `n` to the seed:
 * nearby seeds produce unrelated shuffles in mulberry32 so either would *look* fine, but this
 * one means a whole session is reproducible from one number by walking the same path the
 * engine walks, with no second rule to keep in step.
 */
private fun seedForRound(sessionSeed: Long, round: Int): Long {
    var state = Prng.seed(sessionSeed)
    repeat(round) { state = Prng.next(state).state }
    return state
}

/**
 * Whether the session is over, and what happens to the round in progress (design R2b).
 *
 * At the buzzer a round where Vinto has been declared plays out and is scored — the overrun is
 * bounded and short, one turn per coalition member. Any other round is discarded, and that
 * applies uniformly, including when no round has completed: a session can end with no winner.
 * Uniform beats special-cased here, and the visible clock is what makes it fair.
 */
private fun closeSession(state: RoomState, nowMs: Double): RoomState {
    val game = state.game
    val vintoDeclared = game?.vintoCallerId != null

    // A declared Vinto is allowed to finish. The room stays in play and the buzzer is left
    // behind — the round ending is what closes the session, in [recordRoundEnd].
    if (vintoDeclared && game.phase != GamePhase.SCORING) return state

    val scored = if (game != null && game.phase == GamePhase.SCORING) {
        recordRoundEnd(state)
    } else {
        // Discarded: recorded as such, because the standings cannot be recomputed from the
        // round recordings alone.
        state.copy(session = state.session.copy(discardedRound = state.session.rounds.size + 1))
    }

    return scored.copy(
        phase = RoomPhase.FINISHED,
        game = null,
        startsAtEpochMs = null,
        finishedAtEpochMs = nowMs,
    )
}

/** Files a finished round into the session: hand totals, and what the round paid. */
private fun recordRoundEnd(state: RoomState): RoomState {
    val game = state.game ?: return state

    val result = RoundResult(
        roundNumber = state.session.rounds.size + 1,
        vintoCallerId = game.vintoCallerId,
        scores = calculateFinalScores(game.players, game.vintoCallerId),
        points = calculateRoundPoints(game.players, game.vintoCallerId),
    )

    return state.copy(
        session = state.session.copy(rounds = state.session.rounds + result),
        // Kept beside the session because `closeSession` discards `game` when the room
        // finishes, and the round's recording (`roundRecording`) needs where it ended.
        roundFinal = game,
    )
}

/**
 * Recomputes every deadline from who is connected.
 *
 * Called whenever a socket opens or closes. Presence is the platform's business — sockets
 * survive hibernation and `ctx.getWebSockets()` is authoritative after a wake — so the caller
 * passes the seats it can see rather than the room trying to remember.
 */
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
fun onAlarm(stateJson: String, nowMs: Double): String =
    VintoJson.encodeToString(onAlarmTracked(stateJson, nowMs).result)

/** [onAlarm]'s outcome with the takeover branch's steps kept for the envelope builders. */
internal data class TrackedAlarm(
    val result: LifecycleResult,
    val steps: List<Step> = emptyList(),
)

@Suppress("ReturnCount")
internal fun onAlarmTracked(stateJson: String, nowMs: Double): TrackedAlarm {
    var state = VintoJson.decodeFromString(RoomState.serializer(), stateJson)

    val due = { at: Double? -> at != null && nowMs >= at }

    // 1. The room is over, for any of three reasons.
    val lobbyExpired = (state.phase == RoomPhase.LOBBY || state.phase == RoomPhase.STARTING) &&
        nowMs >= state.createdAtEpochMs + LOBBY_TTL_MS
    val finishedExpired = state.finishedAtEpochMs?.let { nowMs >= it + FINISHED_TTL_MS } == true

    if (due(state.emptyUntilEpochMs) || lobbyExpired || finishedExpired) {
        return TrackedAlarm(LifecycleResult(state, deleted = true))
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
        return TrackedAlarm(LifecycleResult(state, nextAlarmAtEpochMs = state.nextAlarmAt))
    }

    // 3. The buzzer. A round with Vinto declared is left to finish — `closeSession` returns
    //    the state untouched — and the session ends when that round reaches scoring instead.
    if (due(state.session.endsAtEpochMs) && state.inSession) {
        val closed = closeSession(state, nowMs)
        if (closed !== state) {
            return TrackedAlarm(LifecycleResult(closed, nextAlarmAtEpochMs = closed.nextAlarmAt))
        }
    }

    // 4. The countdown.
    if (due(state.startsAtEpochMs) && state.canStart) {
        val started = VintoJson.decodeFromString(
            JoinResult.serializer(),
            startGame(VintoJson.encodeToString(state), nowMs),
        )
        if (started.error == null) {
            val next = started.state
            return TrackedAlarm(
                LifecycleResult(next, nextAlarmAtEpochMs = next.nextAlarmAt, started = true),
            )
        }
    }

    // 5. Seats whose grace has run out are played by bots. The seat keeps its token — it is
    //    held for its owner, not handed to anybody else (design R2a).
    val expired = state.seatGrace.filterValues { nowMs >= it }.keys
    if (expired.isNotEmpty()) {
        val seats = state.seats.map {
            if (it.index in expired) it.copy(isBot = true, botPlayedWhileAway = true) else it
        }
        state = state.copy(seats = seats, seatGrace = state.seatGrace - expired)
        val played = playBotsTracked(state)
        state = withPacing(played.state, nowMs)
        return TrackedAlarm(
            LifecycleResult(
                state,
                nextAlarmAtEpochMs = state.nextAlarmAt,
                tookOver = expired.toList(),
            ),
            steps = played.steps,
        )
    }

    // 6. Pacing (9.4): the table has out-waited a human, and the room moves for them.
    if (due(state.tossInDeadlineEpochMs) || due(state.leaderDeadlineEpochMs)) {
        return expirePacing(
            state,
            nowMs,
            tossInDue = due(state.tossInDeadlineEpochMs),
            leaderDue = due(state.leaderDeadlineEpochMs),
        )
    }

    return TrackedAlarm(LifecycleResult(state, nextAlarmAtEpochMs = state.nextAlarmAt))
}

/**
 * A pacing deadline expired: the room moves for the humans it out-waited — through the same
 * validate-and-reduce path a client's action takes, logged `byBot`, so an expiry is
 * indistinguishable on the wire from a very slow "done".
 */
private fun expirePacing(
    state: RoomState,
    nowMs: Double,
    tossInDue: Boolean,
    leaderDue: Boolean,
): TrackedAlarm {
    val synthesized = mutableListOf<GameAction>()
    if (tossInDue) {
        laggingHumans(state).forEach {
            synthesized += GameAction.PlayerTossInFinished(PlayerIdPayload(it))
        }
    }
    if (leaderDue && awaitingLeader(state)) {
        // Deterministic default: the first coalition seat in table order. Not a choice
        // anybody made, but one everybody can predict — which is what a default is for.
        val game = state.game
        game?.players?.firstOrNull { it.id != game.vintoCallerId }?.let {
            synthesized += GameAction.SetCoalitionLeader(LeaderIdPayload(it.id))
        }
    }

    var working = state.copy(tossInDeadlineEpochMs = null, leaderDeadlineEpochMs = null)
    val steps = mutableListOf<Step>()
    for (action in synthesized) {
        // An action the window's closing has already made moot is skipped, not an error:
        // finishing for the first laggard can finish the window for the second.
        val game = working.game ?: break
        if (ActionValidator.validate(game, action) is Validation.Invalid) continue
        val success = GameEngine.reduce(game, action) as? ReduceResult.Success ?: continue
        val entry = LoggedAction(
            index = working.nextIndex,
            seat = working.seats.firstOrNull { it.playerId == action.actorId }?.index ?: -1,
            playerId = action.actorId ?: "",
            action = action,
            byBot = true,
        )
        working = working.copy(game = success.state, log = working.log + entry)
        steps += Step(entry, success.state, success.revealed)
    }

    val played = playBotsTracked(working)
    working = withPacing(settleRound(played.state, nowMs), nowMs)
    return TrackedAlarm(
        LifecycleResult(working, nextAlarmAtEpochMs = working.nextAlarmAt),
        steps = steps + played.steps,
    )
}

/**
 * Applies one client action, then plays out every bot turn that follows it.
 *
 * Returning the bots' actions in the same response is deliberate: a client sends one thing
 * and receives everything that happened because of it, so there is no round of polling to
 * discover that three bots have moved.
 */
fun applyAction(stateJson: String, token: String, actionJson: String, nowMs: Double): String {
    val applied = applyActionApplied(stateJson, token, actionJson, nowMs)
    return VintoJson.encodeToString(
        ActionResult(
            applied.state,
            events = applied.steps.map { it.logged },
            error = applied.error,
            retryAfterMs = applied.retryAfterMs,
        ),
    )
}

/** What one client action came to, with every applied step's state kept for the envelopes. */
internal data class Applied(
    val state: RoomState,
    val steps: List<Step> = emptyList(),
    val error: String? = null,
    val retryAfterMs: Double? = null,
)

@Suppress("ReturnCount")
internal fun applyActionApplied(
    stateJson: String,
    token: String,
    actionJson: String,
    nowMs: Double,
): Applied {
    val state = VintoJson.decodeFromString(RoomState.serializer(), stateJson)

    // The seat is *derived* from the credential rather than asserted next to it. There is no
    // seat parameter to disagree with the token, so there is no way to send one seat's token
    // and another seat's number and see which check notices first.
    val seatEntry = state.seats.firstOrNull { it.tokenHash == Sha256.hex(token) }
        ?: return Applied(state, error = "no seat holds that token")
    val seat = seatEntry.index

    // The budget is charged before anything else costs anything. Order matters: validating
    // first would be cheap, but reducing and then running three bot searches is not, and a
    // flood is only bounded if the refusal happens before the expensive part.
    val spend = spendBudget(state, seat, nowMs)
    spend.retryAfterMs?.let {
        return Applied(spend.state, error = "too many actions", retryAfterMs = it)
    }
    val charged = spend.state

    // No game, nothing to act on. A lobby refuses game actions rather than dealing one on
    // demand, or the countdown would be advisory.
    val game = charged.game
        ?: return Applied(charged, error = "the game has not started")

    val action = try {
        VintoJson.decodeFromString(GameAction.serializer(), actionJson)
    } catch (failure: IllegalArgumentException) {
        return Applied(charged, error = "unreadable action: ${failure.message}")
    }

    // The seat boundary, checked before the engine sees anything. An action whose payload
    // names another player is refused here whether or not it would have been legal for them.
    action.actorId?.let { claimed ->
        if (claimed != seatEntry.playerId) {
            return Applied(charged, error = "seat $seat may only act as ${seatEntry.playerId}")
        }
    }

    when (val validation = ActionValidator.validate(game, action)) {
        is Validation.Invalid -> return Applied(charged, error = validation.reason)

        Validation.Valid -> Unit
    }

    val result = when (val reduce = GameEngine.reduce(game, action)) {
        is ReduceResult.Success -> reduce
        is ReduceResult.Failure -> return Applied(charged, error = reduce.reason)
    }
    val reduced = result.state

    val accepted = LoggedAction(
        index = charged.nextIndex,
        seat = seat,
        playerId = seatEntry.playerId ?: "",
        action = action,
    )

    val played = playBotsTracked(
        charged.copy(game = reduced, log = charged.log + accepted),
        // The bots watched this player's move too; it seeds the runner's table model.
        playerMove = ObservedMove(action, before = game, after = reduced),
    )
    val settled = settleRound(played.state, nowMs)

    return Applied(
        withPacing(settled, nowMs),
        steps = listOf(Step(accepted, reduced, result.revealed)) + played.steps,
    )
}

/**
 * Runs the room's bots until it is a seated player's move again.
 *
 * Every bot action goes through the same validator a client's would. That is not caution
 * about the bot so much as about the seam: if the room ever accepted something from its own
 * driver that it would refuse from a player, the two would be playing different games.
 */

/** An accepted action with the states around it, for the bots' table model. */
private data class ObservedMove(
    val action: GameAction,
    val before: GameState,
    val after: GameState,
)

/**
 * One applied action with the state it left behind and what it turned face up — the raw
 * material of the envelope builders in `Envelopes.kt`. Never stored: states per step exist
 * only for the request that produced them, which is why per-event views are built at send
 * time and a resync's catch-up carries none.
 */
internal data class Step(
    val logged: LoggedAction,
    val after: GameState,
    val revealed: List<PublicReveal>,
)

internal data class PlayedOut(val state: RoomState, val steps: List<Step>)

private fun playBots(start: RoomState, playerMove: ObservedMove? = null): RoomState =
    playBotsTracked(start, playerMove).state

private fun playBotsTracked(start: RoomState, playerMove: ObservedMove? = null): PlayedOut {
    if (start.game == null) return PlayedOut(start, emptyList())

    val runner = BotRunner(start.difficulty, Random(start.seed))
    // The runner here is rebuilt per request, so its table model only spans the moves of
    // this request; the durable cross-request knowledge is the engine's `opponentKnowledge`.
    playerMove?.let { runner.observe(it.action, it.before, it.after) }
    var state = start
    var steps = 0
    val trail = mutableListOf<Step>()

    while (steps++ < MAX_BOT_STEPS && state.game?.phase != GamePhase.SCORING) {
        val game = state.game ?: break
        val action = runner.nextAction(game) ?: break
        val actor = action.actorId
        val seat = state.seats.firstOrNull { it.playerId == actor }

        // Three reasons to stop, all of them "this is not the room's move to make":
        //  - it belongs to a seated *person*, whatever the runner thinks. `tokenHash`, not
        //    `occupied`: since the lobby landed, an occupied seat may be a bot the room is
        //    supposed to play, and using `occupied` here made the room refuse to move its
        //    own bots — a game that stopped dead the first time a window opened;
        //  - the validator refuses it, which would mean the room and its own driver
        //    disagreed about the rules;
        //  - the engine refuses it after validation, which should be impossible.
        val success = when {
            seat != null && seat.tokenHash != null -> null
            ActionValidator.validate(game, action) is Validation.Invalid -> null
            else -> GameEngine.reduce(game, action) as? ReduceResult.Success
        } ?: break
        val reduced = success.state

        runner.observe(action, before = game, after = reduced)

        val entry = LoggedAction(
            index = state.nextIndex,
            seat = seat?.index ?: -1,
            playerId = actor ?: "",
            action = action,
            byBot = true,
        )
        state = state.copy(game = reduced, log = state.log + entry)
        trail += Step(entry, reduced, success.revealed)
    }

    return PlayedOut(state, trail)
}

/**
 * What one seat is allowed to see.
 *
 * The only shape of the game that ever leaves the room. Sending `RoomState` instead would
 * hand every client every hand, which is the failure the whole server-authoritative design
 * exists to prevent.
 */
fun viewForSeat(stateJson: String, seat: Int, nowMs: Double): String {
    val state = VintoJson.decodeFromString(RoomState.serializer(), stateJson)
    val seatEntry = state.seats.getOrNull(seat)
        ?: return VintoJson.encodeToString(ViewResult(error = "unknown seat $seat"))
    val game = state.game
        ?: return VintoJson.encodeToString(ViewResult(error = "the game has not started"))
    val playerId = seatEntry.playerId
        ?: return VintoJson.encodeToString(ViewResult(error = "seat $seat has no player yet"))

    // The clock is handed to the projection rather than looked up by it: the engine has none,
    // and a session length is the room's business.
    val remaining = state.session.endsAtEpochMs?.let { maxOf(0.0, it - nowMs).toLong() }
    return VintoJson.encodeToString(ViewResult(view = projectView(game, playerId, remaining)))
}

/**
 * Which seat a token holds, or -1.
 *
 * Lets the socket layer resolve a client to a seat without ever holding a seat number it did
 * not derive from a credential.
 */
fun seatForToken(stateJson: String, token: String): Int {
    val state = VintoJson.decodeFromString(RoomState.serializer(), stateJson)
    val hash = Sha256.hex(token)
    return state.seats.firstOrNull { it.tokenHash == hash }?.index ?: -1
}

/** Events a reconnecting client has not seen. The log index is the cursor (design D9). */
fun eventsSince(stateJson: String, sinceIndex: Int): String {
    val state = VintoJson.decodeFromString(RoomState.serializer(), stateJson)
    val from = sinceIndex.coerceIn(0, state.log.size)
    return VintoJson.encodeToString(SyncResult(state.log.drop(from), state.nextIndex))
}

/**
 * The lobby, as anybody in it may see it.
 *
 * Deliberately not [RoomState]: that holds token hashes and, once dealt, every hand. This is
 * seat occupancy and a countdown, which is all a lobby screen needs and all it should get.
 */
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
                    nickname = it.profile?.nickname,
                )
            },
            humans = state.humanCount,
            startsAtEpochMs = state.startsAtEpochMs,
            msUntilStart = state.startsAtEpochMs?.let { maxOf(0.0, it - nowMs) },
        ),
    )
}

/** Exposed for the gate harness; `SEAT_COUNT` is a design constant, not a setting. */
fun seatCount(): Int = SEAT_COUNT

/**
 * The earliest deadline this room is waiting on, or 0 for none.
 *
 * Exported rather than recomputed in JavaScript, which is where the first version of this put
 * it: five deadlines duplicated across two languages is a drift waiting to happen, and the
 * symptom would be an alarm that fires at the wrong time — which looks like nothing at all
 * until a room fails to clean itself up.
 */
fun nextAlarmAt(stateJson: String): Double =
    VintoJson.decodeFromString(RoomState.serializer(), stateJson).nextAlarmAt ?: 0.0

/** The session length, so a harness cannot drift from the implementation. */
fun sessionMs(): Double = SESSION_MS

/** The countdown length, so a harness cannot drift from the implementation. */
fun countdownMs(): Double = COUNTDOWN_MS
