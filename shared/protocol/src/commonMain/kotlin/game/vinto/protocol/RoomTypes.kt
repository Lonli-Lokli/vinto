package game.vinto.protocol

import game.vinto.engine.PlayerView
import game.vinto.shapes.Card
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
 * A card the table was shown, in wire form.
 *
 * The engine's `PublicReveal`, transcribed: a King's declaration and a failed toss-in turn a
 * card face up *in the hand it is in* — public for that moment and private again afterwards.
 * That makes it an event rather than a fact about the game: it exists only on the reduce
 * result, is never in any state, and a client that missed the message has missed the moment.
 * Choreography needs it to flip the right card at the right time.
 */
@Serializable
data class RevealedCard(
    val playerId: String,
    val position: Int,
    val card: Card,
)

/**
 * One accepted action **as sent**: the [LoggedAction] fields, plus what this action showed.
 *
 * [view] is the receiving seat's redacted view of the table *after this action* — which is
 * what lets a remote client animate a batch of bot moves one at a time instead of jumping to
 * the end: the same `choreograph(action, before, after)` a local game runs needs a view per
 * step, and the log alone carries only actions. Per-seat by construction, so an [EventEntry]
 * only ever exists inside a message built for one socket.
 *
 * Both additions default to absent, which is the compatibility story in both directions: a
 * stored [LoggedAction] parses as an entry with no view (a client treats that like a sync —
 * cursor jump, one catch-up frame), and an older reader skips the new keys entirely.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class EventEntry(
    val index: Int,
    val seat: Int,
    val playerId: String,
    val action: GameAction,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val byBot: Boolean = false,
    val view: PlayerView? = null,
    val revealed: List<RevealedCard> = emptyList(),
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

/**
 * One room on the public list, as somebody browsing sees it before joining.
 *
 * An **allow-list**, and deliberately not the registry's own record with a field removed. The
 * registry entry also carries the Durable Object's name and a hash of whoever asked for the
 * room, neither of which is a stranger's business; a projection that strips named fields
 * publishes the next field somebody adds, and does it silently. This type says what is
 * public, so adding to the registry cannot widen it.
 *
 * Nothing here identifies a person. [hostNickname] is display text the room sanitises on the
 * way in — never a name anybody chose to be found by, and never a way to be seated.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class PublicRoom(
    val code: String,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val hostNickname: String? = null,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val humans: Int = 0,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val seatsFilled: Int = 0,
    /**
     * How long until the deal, if a countdown is running. Absent means nobody is waiting on a
     * clock.
     *
     * A duration and not a deadline, for the reason [LobbyView] carries both and the client
     * reads this one: a phone whose clock is a minute out would render an absolute deadline as
     * a minute of nonsense, and there is no reason to make a browser trust its own clock to
     * read a number the service already knows.
     */
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val msUntilStart: Double? = null,
)

/** The answer to `GET /rooms`: the public rooms, and nothing about the private ones. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class PublicRooms(
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val rooms: List<PublicRoom> = emptyList(),
)
