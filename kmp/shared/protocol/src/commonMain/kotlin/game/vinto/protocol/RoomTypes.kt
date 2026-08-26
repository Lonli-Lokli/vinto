package game.vinto.protocol

import game.vinto.shapes.GameAction
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/**
 * The room-facing types that travel on the wire, moved here **verbatim** from the worker's
 * `Room.kt` so that the client and the room read the same declarations rather than two that
 * resemble each other. The room still owns everything about how they are *produced*; this
 * module owns only their shape.
 *
 * Every type keeps the room's `@EncodeDefault(ALWAYS)` discipline, and for the same reason it
 * was adopted there: `VintoJson` omits defaults — the canonical `GameState` form requires it —
 * so a field JavaScript checks with `!== null` has to be written even when it holds null, or
 * `undefined !== null` quietly answers the wrong question.
 */

/**
 * What is known about the person behind a token, beyond the fact that they hold a seat.
 *
 * A record rather than a bare nickname, because this is the thing that grows: an avatar, a
 * preferred language, a pronoun — none of which are worth a schema change to the seat when
 * they arrive. Everything in it is **display-only**: nothing here identifies, authorises or
 * seats anybody — that is the token's job, and keeping the two apart is why a nickname cannot
 * be used to take somebody's place.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class PlayerProfile(
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val nickname: String = "",
)

/** One finished round: what the hands came to, and what that was worth. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class RoundResult(
    val roundNumber: Int,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val vintoCallerId: String? = null,
    /** Hand totals, coalition members scored on their best (see `calculateFinalScores`). */
    val scores: Map<String, Int>,
    /** What the round paid, per `VINTO_RULES.md`: +3/-1, a tie to the caller. */
    val points: Map<String, Int>,
)

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

/** One seat of the lobby, as anybody in the room may see it. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class LobbySeat(
    val index: Int,
    val occupied: Boolean,
    val isBot: Boolean,
    /** True only for a bot somebody added as filler — the ones a newcomer may displace. */
    val removable: Boolean,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val nickname: String? = null,
)

/**
 * The lobby, as anybody in it may see it.
 *
 * Deliberately not the room's state: that holds token hashes and, once dealt, every hand.
 * This is seat occupancy and a countdown, which is all a lobby screen needs and all it
 * should get.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class LobbyView(
    val phase: RoomPhase,
    val seats: List<LobbySeat>,
    val humans: Int,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val startsAtEpochMs: Double? = null,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val msUntilStart: Double? = null,
)

/**
 * A seat as everybody else may see it.
 *
 * The room's `Seat` minus its `tokenHash`: the hash is not a secret the way the token is, but
 * it is a credential's shadow and it has no business on the wire. Until now this stripping
 * lived as an ad-hoc object literal in `index.mjs` (`#publicSeats`); declaring it beside the
 * messages that carry it is what lets both ends read one definition.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class PublicSeat(
    val index: Int,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val playerId: String? = null,
    /**
     * The whole profile, not a nickname picked out of it: whatever is added there next is
     * display-only by construction, so it can travel without a decision each time.
     */
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val profile: PlayerProfile? = null,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val ownerId: String? = null,
    val occupied: Boolean,
)
