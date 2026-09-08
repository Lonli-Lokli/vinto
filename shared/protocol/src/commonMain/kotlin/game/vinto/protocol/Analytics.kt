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
 * A Workers Analytics Engine data point, in the portfolio's **shared, self-describing** schema.
 *
 *     indexes  [game]
 *     blobs    [game, event, k1, v1, k2, v2, k3, v3, m1, m2, m3]
 *     doubles  [sampleRate, n1, n2, n3]
 *
 * `k`/`v` are tag names and their values; `m` names the measure each `n` holds.
 *
 * ### It used to be positional, and that is what changed
 *
 * `blobs[0]` meant the difficulty on one event and the ending reason on another, and `doubles[2]`
 * was a duration here and a turn count there. That works exactly as long as the queries live in
 * this repository, because the meaning of `double2` is written down nowhere a reader can check —
 * and it is why the dashboard had to be Vinto's own, with Vinto's SQL, duplicated per game.
 *
 * Naming the fields is what lets one dashboard draw every game from configuration. A reader that
 * knows only this layout can answer "count `round_end` grouped by tag `size`" without knowing
 * what a round is. `workers/px/src/collect.ts` in the `kupalinka` repository writes the same
 * shape for browsers, and `panels.ts` beside it compiles a game's published config against it.
 *
 * **The privacy rule is unchanged and is what makes tags safe.** A tag value can only come from
 * an enum name or a number here, because [AnalyticsEvent] has nothing else to give it —
 * `AnalyticsPrivacyTest` still fails the build if a free string ever becomes representable.
 */
@Serializable
public data class DataPoint(
    val indexes: List<String>,
    val blobs: List<String>,
    val doubles: List<Double>,
)

/**
 * Matching the shared collector: three tags, four measures.
 *
 * A tag is a GROUP BY key, and each one multiplies the cardinality Analytics Engine samples
 * against; a measure is another column on the same row and costs nothing to carry. Four is what
 * [AnalyticsEvent.RoundEnd] needs — a duration, an action count, and the two numbers saying what
 * the room cost to serve it.
 */
private const val TAGS = 3
private const val MEASURES = 4

/**
 * The field names, once each.
 *
 * They are the vocabulary a dashboard's configuration is written against — a panel says
 * `"by": "tag:humans"` and means [HUMANS] — so a typo in one of them is a chart that silently
 * draws nothing. Naming them here makes that a compile error instead, and is why `AnalyticsPrivacyTest`
 * can hold a closed list of every field this file can emit.
 */
private const val HUMANS = "humans"
private const val BOTS = "bots"
private const val DIFFICULTY = "difficulty"
private const val FINISHED = "finished"
private const val DURATION_MS = "duration_ms"

private fun flag(value: Boolean): Double = if (value) 1.0 else 0.0

/**
 * Flattens an event, plus what it cost, into the shape the shared dataset stores.
 *
 * Tags are what a panel GROUPS BY and measures are what it aggregates, so the split is by how a
 * number gets read rather than by its type: `humans` is a tag because the useful question is
 * "how many rounds had four", and `duration_ms` is a measure because the useful question is its
 * average. A field that is genuinely both is a tag — a measure cannot be grouped by, and a tag
 * can still be counted.
 *
 * [sampleRate] rides along so a query can weight a sampled count instead of quietly
 * under-reporting it (design §A8). Events that are never sampled carry 1.0.
 */
public fun AnalyticsEvent.toDataPoint(cost: Cost? = null, sampleRate: Double = 1.0): DataPoint {
    val tags = mutableListOf<Pair<String, String>>()
    val measures = mutableListOf<Pair<String, Double>>()

    when (this) {
        is AnalyticsEvent.RoomCreated -> {
            tags += DIFFICULTY to difficulty.name
            tags += "listed" to listed.toString()
        }
        is AnalyticsEvent.SeatFilled -> {
            tags += HUMANS to humans.toString()
            tags += BOTS to bots.toString()
            tags += "by_bot" to byBot.toString()
        }
        is AnalyticsEvent.SeatVacated -> {
            tags += HUMANS to humans.toString()
            tags += BOTS to bots.toString()
            tags += "grace" to grace.toString()
        }
        is AnalyticsEvent.BotTookOver -> tags += HUMANS to humans.toString()
        is AnalyticsEvent.Reconnected -> measures += "away_ms" to awayMs
        is AnalyticsEvent.RoundStart -> {
            tags += HUMANS to humans.toString()
            tags += BOTS to bots.toString()
            measures += "round_number" to roundNumber.toDouble()
        }
        is AnalyticsEvent.RoundEnd -> {
            tags += "ended_by" to endedBy.name
            tags += "caller_won" to callerWon.toString()
            measures += DURATION_MS to durationMs
            measures += "actions" to actions.toDouble()
        }
        is AnalyticsEvent.SessionEnded -> {
            tags += "reason" to reason.name
            measures += DURATION_MS to durationMs
            measures += "rounds" to rounds.toDouble()
        }
        is AnalyticsEvent.SoloRound -> {
            tags += DIFFICULTY to difficulty.name
            tags += FINISHED to finished.toString()
            measures += DURATION_MS to durationMs
            measures += "turns" to turns.toDouble()
        }
        is AnalyticsEvent.Lesson -> {
            tags += FINISHED to finished.toString()
            measures += DURATION_MS to durationMs
            measures += "reached_stage" to reachedStage.toDouble()
        }
    }

    // What a room cost to run, on every event that reports one. Measures rather than tags: the
    // question is "what does a busy hour cost", which is a sum, never a grouping.
    if (cost != null) {
        measures += "wall_ms" to cost.wallMs
        measures += "requests" to cost.requests
    }

    val keptTags = tags.take(TAGS)
    val keptMeasures = measures.take(MEASURES)
    fun <T> padded(list: List<T>, to: Int, filler: T) = list + List(maxOf(0, to - list.size)) { filler }

    return DataPoint(
        indexes = listOf(GAME),
        blobs = listOf(GAME, name) +
            padded(keptTags.flatMap { listOf(it.first, it.second) }, TAGS * 2, "") +
            padded(keptMeasures.map { it.first }, MEASURES, ""),
        doubles = listOf(sampleRate) + padded(keptMeasures.map { it.second }, MEASURES, 0.0),
    )
}

/**
 * The index every point carries, and the name the dashboard knows this game by.
 *
 * It is the first label of the host the web build is served from (`vinto.kupalinka.app`), because
 * that is what the portfolio's visit beacon calls this game — deriving it the same way here means
 * the two halves of one game's numbers cannot end up filed under two names.
 */
public const val GAME: String = "vinto"

/** The JSON the Worker shim hands to `writeDataPoint`. */
public val AnalyticsJson: Json = Json { encodeDefaults = true }
