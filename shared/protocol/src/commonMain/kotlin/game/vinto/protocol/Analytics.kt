package game.vinto.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Everything the game is allowed to count, and nothing else.
 *
 * This is a **closed** type on purpose. The privacy rule in `docs/kotlin/HOSTING.md` §6c is not
 * "remember not to log the room code" — it is that there is nowhere to put it. Every field
 * below is an enum, a boolean or a number; not one of them can carry a string a player typed
 * or a server minted. A nickname, a room code, a seat token and an IP are not filtered out
 * downstream, they are unrepresentable here, and `AnalyticsPrivacyTest` fails the build if
 * that stops being true.
 *
 * It lives in `shared/protocol` rather than in the room because it *is* wire: the room writes
 * these to Analytics Engine and clients post them to `/e`, so both sides have to agree on the
 * vocabulary — which is what this module is for.
 *
 * The split between what the server sends and what a client sends is by *who can know*
 * (design §A3): the room is authoritative, so it already holds every fact about an online
 * game and clients are never asked to report one back. A client reports **only its own offline
 * play** — a solo round and the lesson — and nothing else at all.
 *
 * It used to report two other things, and both are gone. The *menu funnel* was a client's guess
 * at intent before a room existed; it is not play, and what it measured is not worth a second
 * channel. *Failures* were counted here and are now Sentry's, because a stalled stage is a
 * defect with a stack trace rather than a number on a chart — see [FailureKind].
 */
@Serializable
public sealed interface AnalyticsEvent {

    /**
     * The one `index` a data point gets, and what every query groups by.
     *
     * It is also each case's `@SerialName`, so the discriminator on the wire and the index in
     * the store are the same word. A client posting `{"type":"round_end",...}` and a query
     * saying `WHERE index1 = 'round_end'` are then talking about the same thing, which is one
     * fewer mapping to keep in step.
     */
    public val name: String

    // --- what only the room can know -------------------------------------------------------

    @Serializable
    @SerialName("room_created")
    public data class RoomCreated(val listed: Boolean, val difficulty: Difficulty) : AnalyticsEvent {
        override val name: String get() = "room_created"
    }

    @Serializable
    @SerialName("seat_filled")
    public data class SeatFilled(val humans: Int, val bots: Int, val byBot: Boolean) : AnalyticsEvent {
        override val name: String get() = "seat_filled"
    }

    @Serializable
    @SerialName("seat_vacated")
    public data class SeatVacated(val humans: Int, val bots: Int, val grace: Boolean) : AnalyticsEvent {
        override val name: String get() = "seat_vacated"
    }

    /** A dropped seat that the grace period handed to a bot rather than held open. */
    @Serializable
    @SerialName("bot_took_over")
    public data class BotTookOver(val humans: Int) : AnalyticsEvent {
        override val name: String get() = "bot_took_over"
    }

    @Serializable
    @SerialName("reconnected")
    public data class Reconnected(val awayMs: Double) : AnalyticsEvent {
        override val name: String get() = "reconnected"
    }

    @Serializable
    @SerialName("round_start")
    public data class RoundStart(val humans: Int, val bots: Int, val roundNumber: Int) : AnalyticsEvent {
        override val name: String get() = "round_start"
    }

    @Serializable
    @SerialName("round_end")
    public data class RoundEnd(
        /**
         * Accepted actions in the round, not turns.
         *
         * Named for what the room can actually count: `roundStartLogIndex` marks where a
         * round's slice of the log begins, so this is exact. A "turn" would be a guess at how
         * those actions group, and a number that is nearly right is worse in a store than one
         * that is exactly something else.
         */
        val actions: Int,
        val durationMs: Double,
        val endedBy: RoundEnding,
        val callerWon: Boolean,
    ) : AnalyticsEvent {
        override val name: String get() = "round_end"
    }

    @Serializable
    @SerialName("session_ended")
    public data class SessionEnded(val reason: SessionEnding, val rounds: Int, val durationMs: Double) :
        AnalyticsEvent {
        override val name: String get() = "session_ended"
    }

    // --- what only a client can know, which is its own offline play and nothing else --------

    @Serializable
    @SerialName("solo_round")
    public data class SoloRound(
        val finished: Boolean,
        val difficulty: Difficulty,
        val turns: Int,
        val durationMs: Double,
    ) : AnalyticsEvent {
        override val name: String get() = "solo_round"
    }

    @Serializable
    @SerialName("lesson")
    public data class Lesson(val finished: Boolean, val reachedStage: Int, val durationMs: Double) :
        AnalyticsEvent {
        override val name: String get() = "lesson"
    }
}

@Serializable
public enum class Difficulty { EASY, MODERATE, HARD }

@Serializable
public enum class RoundEnding { VINTO_CALLED, DECK_EXHAUSTED, ABANDONED }

@Serializable
public enum class SessionEnding { PLAYED_OUT, TOO_FEW_HUMANS, TIMED_OUT, EVERYBODY_LEFT }

/**
 * Which part of the app something happened in.
 *
 * Not an analytics field any more — it rides along on a Sentry report, which is where the
 * things that use it went. Kept here because it is still shared vocabulary between the client
 * and the room, and because moving it would be churn for a rename.
 */
@Serializable
public enum class Surface { SOLO, ONLINE, LESSON, MENU }

/**
 * The four ways the app fails a player without crashing.
 *
 * **These are reported to Sentry, not counted here.** They were an analytics event, which put
 * them in a store that answers "how many" and cannot answer "which one, and what was it doing"
 * — and a stalled stage or a refused move is a defect to be *fixed*, not a rate to be watched.
 * Sentry already takes a non-fatal from `Crashes.report`, with the breadcrumbs and the deal's
 * address attached, and the room already has its own pipe in `worker/cloudflare/sentry.mjs`.
 * One place for what broke, one for what people did.
 */
@Serializable
public enum class FailureKind {
    /** The animation queue stopped draining and the round could not be finished on screen. */
    STAGE_STALLED,

    /** The socket gave up reconnecting. */
    SOCKET_LOST,

    /** The engine refused a move the UI believed was legal. */
    MOVE_REFUSED,

    /** A screen failed to render. */
    RENDER_FAILED,
}

/**
 * What an invocation of the room cost, carried on every server event.
 *
 * This is the whole reason the room reports rather than estimates: a Durable Object has a
 * 30-second CPU budget per request and spends most of it on MCTS, and `PLATFORM-GATE.md`
 * measured exactly one worst case. "What does a round cost" decides whether online play stays
 * free, and it is free to collect here.
 */
@Serializable
public data class Cost(val wallMs: Double, val requests: Double)

/**
 * A Workers Analytics Engine data point: one index, some strings, some numbers.
 *
 * That is the entire schema WAE offers, so the discipline is in what goes where — the event
 * name is the index because it is what every query groups by, blobs hold only low-cardinality
 * strings (enum names, never free text), and doubles hold counts and durations.
 */
@Serializable
public data class DataPoint(
    val indexes: List<String>,
    val blobs: List<String>,
    val doubles: List<Double>,
)

private fun flag(value: Boolean): Double = if (value) 1.0 else 0.0

/**
 * Flattens an event, plus what it cost, into the shape WAE stores.
 *
 * [sampleRate] rides along as a double so a query can weight a sampled count instead of
 * quietly under-reporting it (design §A8). Events that are never sampled carry 1.0.
 */
public fun AnalyticsEvent.toDataPoint(cost: Cost? = null, sampleRate: Double = 1.0): DataPoint {
    val blobs = mutableListOf<String>()
    val doubles = mutableListOf(sampleRate)

    when (this) {
        is AnalyticsEvent.RoomCreated -> {
            blobs += difficulty.name
            doubles += flag(listed)
        }
        is AnalyticsEvent.SeatFilled -> doubles += listOf(humans.toDouble(), bots.toDouble(), flag(byBot))
        is AnalyticsEvent.SeatVacated -> doubles += listOf(humans.toDouble(), bots.toDouble(), flag(grace))
        is AnalyticsEvent.BotTookOver -> doubles += humans.toDouble()
        is AnalyticsEvent.Reconnected -> doubles += awayMs
        is AnalyticsEvent.RoundStart ->
            doubles += listOf(humans.toDouble(), bots.toDouble(), roundNumber.toDouble())
        is AnalyticsEvent.RoundEnd -> {
            blobs += endedBy.name
            doubles += listOf(actions.toDouble(), durationMs, flag(callerWon))
        }
        is AnalyticsEvent.SessionEnded -> {
            blobs += reason.name
            doubles += listOf(rounds.toDouble(), durationMs)
        }
        is AnalyticsEvent.SoloRound -> {
            blobs += difficulty.name
            doubles += listOf(flag(finished), turns.toDouble(), durationMs)
        }
        is AnalyticsEvent.Lesson -> doubles += listOf(flag(finished), reachedStage.toDouble(), durationMs)
    }

    if (cost != null) doubles += listOf(cost.wallMs, cost.requests)

    return DataPoint(indexes = listOf(name), blobs = blobs, doubles = doubles)
}

/** The JSON the Worker shim hands to `writeDataPoint`. */
public val AnalyticsJson: Json = Json { encodeDefaults = true }
