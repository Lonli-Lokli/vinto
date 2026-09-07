package game.vinto.protocol

import game.vinto.engine.PlayerView
import game.vinto.shapes.CoalitionPlan
import game.vinto.shapes.GameAction
import game.vinto.shapes.PlanEdit
import game.vinto.shapes.TableTalk
import game.vinto.shapes.VintoJson
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The wire between a client and a room, as declarations rather than as habit.
 *
 * The shapes here are **pinned to what `index.mjs` already sends and accepts** — the wire
 * came first and this file transcribes it, which is why the discriminator rides *beside* the
 * payload fields (`{"type":"join","nickname":"Ann"}`) rather than wrapping them: that is what
 * `JSON.parse(raw); switch (msg.type)` has always read, and kotlinx's `classDiscriminator`
 * writes exactly it. `ProtocolWireTest` holds the pin with literals copied from the
 * JavaScript, so a drift on either side fails a test instead of a game.
 *
 * Compatibility rule, stated once and relied on everywhere: **the protocol only ever grows,
 * additively.** New message types and new optional fields are fine — [ProtocolJson] ignores
 * unknown keys so an older client survives a newer room — but a field never changes meaning
 * or type, and a message type is never removed while any client sends it.
 */

/**
 * The wire's version, bumped whenever a build could send or receive something an older build
 * cannot read: a new message type, a new game action inside an events entry, a field that
 * changes meaning. New optional fields are not a bump — `ignoreUnknownKeys` skips them.
 *
 * Sent with every join. The room keeps a floor ([MIN_PROTOCOL]) and refuses a join below it
 * at the door with [UPDATE_NEEDED_CODE], never mid-game: a client that sat down is a client
 * the room can talk to for the whole game. Between the floor and the current number the
 * client is seated and told, once, that a newer build is waiting ([UPDATE_AVAILABLE_CODE]).
 * A join without a number is version 1, which is every build shipped before the number
 * existed.
 *
 * History: 1 — the wire as first shipped. 2 — coalition play: `say`, `done-conferring`,
 * `edit-plan`, `agree-plan`, `said`, `notice`, and the `DECLARE_CARDS` action.
 */
public const val PROTOCOL_VERSION: Int = 3

/** The oldest protocol the room will seat. Below it, the join is refused with [UPDATE_NEEDED_CODE]. */
public const val MIN_PROTOCOL: Int = 2

/** The refusal code for a build below the floor: update the app, nothing else will help. */
public const val UPDATE_NEEDED_CODE: String = "update-needed"

/** The notice code for a build the room still seats but that has a newer one waiting. */
public const val UPDATE_AVAILABLE_CODE: String = "update-available"

/** How loudly a [ServerMessage.Notice] is meant: a line in the log, or a card in the way. */
@Serializable
enum class NoticeSeverity {
    @SerialName("info")
    INFO,

    @SerialName("warning")
    WARNING,
}

/** Everything a client may say to a room. One WebSocket message each, as JSON text. */
@Serializable
sealed interface ClientMessage {

    /**
     * Take a seat, or return to one. A token means "I already hold a seat here" — the room
     * seats by token, idempotently, which is the whole reconnect story. No token means
     * "issue me one", and the answer is the one message that ever carries it raw.
     */
    /**
     * Table talk: one typed sentence from the phrasebook, carrying no text.
     *
     * Not an action, and deliberately a separate message rather than a `GameAction` — none of
     * this is game state (design D6), so it must not reach the engine, a recording or a hash.
     * The room checks the speaker against the socket's own seat exactly as it does an action's
     * `actorId`, and caps how much one seat may say in a window.
     */
    /**
     * "I have said what I wanted to say."
     *
     * Closes the coalition's confer window early, the moment every connected member has sent
     * one — so three people who agree in five seconds are not held for twenty.
     */
    @Serializable
    @SerialName("done-conferring")
    data class DoneConferring(val token: String? = null) : ClientMessage

    @Serializable
    @SerialName("say")
    data class Say(val talk: TableTalk) : ClientMessage

    /**
     * One part of the coalition's shared plan, changed (design D7a).
     *
     * A part rather than the whole draft, so two members on different lanes cannot overwrite
     * each other. Refused for the caller, for a seat outside the coalition and for a lane whose
     * turn has begun; merged otherwise, with agreement reset to the editor. The room answers as
     * it answers [MoreTime] — an empty `events` per seat whose `plan` is the whole board and
     * whose `said` carries the bots' answers for their own lanes — and there is no `planned`
     * message. Spends the same budget [Say] does, being broadcast to every socket.
     */
    @Serializable
    @SerialName("edit-plan")
    data class EditPlan(
        val token: String? = null,
        val edit: PlanEdit,
    ) : ClientMessage

    /**
     * Yes or no to the standing plan as a whole.
     *
     * A yes also counts as [DoneConferring]: agreeing is how you finish talking, so the last
     * member to agree is what starts the round.
     */
    @Serializable
    @SerialName("agree-plan")
    data class AgreePlan(
        val token: String? = null,
        val agree: Boolean,
    ) : ClientMessage

    @Serializable
    @SerialName("join")
    data class Join(
        val token: String? = null,
        val nickname: String? = null,
        /**
         * The face this seat sits behind, alongside the name and for the same reason.
         *
         * Loose fields rather than a whole [PlayerProfile], mirroring [nickname]: the room
         * composes the profile, because it is the room that sanitises what a client claims and
         * a record handed over whole invites trusting it as sent. Null from a build older than
         * protocol 3, which lands on the default face rather than on nothing.
         */
        val avatarKind: Int? = null,
        val avatarSeed: Long? = null,
        val avatarGround: Int? = null,
        /**
         * The protocol this client speaks — [PROTOCOL_VERSION] of the build that sent it.
         * Absent from every build before the number existed, which the room reads as 1.
         */
        val protocol: Int? = null,
    ) : ClientMessage

    /** One game action, authorised by the token — never by the socket's memory of a seat. */
    @Serializable
    @SerialName("action")
    data class Action(
        val token: String? = null,
        val action: GameAction,
    ) : ClientMessage

    /** Everything after this log index, please. The log index is the sync cursor. */
    @Serializable
    @SerialName("resync")
    data class Resync(val sinceIndex: Int) : ClientMessage

    /** Fill the first empty seat with a bot. Any seated player may; the countdown undoes. */
    @Serializable
    @SerialName("add-bot")
    data class AddBot(val token: String? = null) : ClientMessage

    /** Take a filler bot back out, which cancels a running countdown. */
    @Serializable
    @SerialName("remove-bot")
    data class RemoveBot(
        val token: String? = null,
        val seat: Int,
    ) : ClientMessage

    /**
     * Give the seat up for good — the exit that is not a dropped connection.
     *
     * **The room cannot tell a closed socket from a tunnel**, and that is deliberate: the seat
     * token exists so a player can come back to a seat a bot has been keeping warm. The cost of
     * that promise is that nothing was ever able to say "I am finished with this room", so the
     * Leave button and the phone's back gesture did the identical thing — close the socket — and
     * a player who opened a room, backed out and opened another accumulated rooms that only the
     * registry's lease eventually swept.
     *
     * This is the other half. Backing out still just closes the socket and keeps the seat;
     * pressing Leave sends this, and the room frees the seat rather than holding it.
     *
     * Added in protocol 3. An older room ignores it — [ProtocolJson] sets `ignoreUnknownKeys` —
     * and the seat then behaves as it always did, which is why the floor did not have to move.
     */
    @Serializable
    @SerialName("leave")
    data class Leave(val token: String? = null) : ClientMessage

    /** Agree to another round. The last connected human to agree is what deals it. */
    @Serializable
    @SerialName("next-round")
    data class NextRound(val token: String? = null) : ClientMessage

    /**
     * Ask for more time on the open toss-in window.
     *
     * Only somebody the window is still waiting on may ask, and the room grants a bounded
     * number of extensions per window — the refusals arrive as [ServerMessage.Error]. The
     * refreshed countdown reaches every seat as `PlayerView.tossInMsRemaining` on an empty
     * `events` message, because every phone's clock has to jump together.
     */
    @Serializable
    @SerialName("more-time")
    data class MoreTime(val token: String? = null) : ClientMessage
}

/**
 * Everything a room may say to a client.
 *
 * A rule that shapes several of these: **a view is per-seat and never broadcast.** Two seats
 * are entitled to different cards, so any message carrying a [PlayerView] is built once per
 * socket; the events beside it are public and identical for everyone.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
sealed interface ServerMessage {

    /**
     * The answer to [ClientMessage.Join], to that socket alone — the one and only message
     * that carries the raw [token].
     *
     * [view] is null in a lobby, deliberately: there is no game and therefore no view, and a
     * made-up empty one would leave the client unable to tell "not dealt" from "dealt,
     * nothing to see" — which need different screens.
     */
    @Serializable
    @SerialName("joined")
    data class Joined(
        val seat: Int,
        val token: String,
        val seats: List<PublicSeat>,
        val nextIndex: Int,
        val lobby: LobbyView,
        @EncodeDefault(EncodeDefault.Mode.ALWAYS) val view: PlayerView? = null,
        /** See [Sync.plan]. Here so an app restarted mid-final-round lands on the present plan. */
        val plan: CoalitionPlan? = null,
        /** The protocol the room speaks, so a client can say so in a report. */
        val protocol: Int? = null,
    ) : ServerMessage

    /**
     * Something the room wants a person told that is not a refusal and not game state: a
     * build that still works but has a newer one waiting, for now. A screen shows it once,
     * with a way to act and a way to carry on. A build older than this message skips it.
     */
    @Serializable
    @SerialName("notice")
    data class Notice(
        val code: String,
        val message: String,
        val severity: NoticeSeverity = NoticeSeverity.WARNING,
    ) : ServerMessage

    /**
     * Accepted actions — the sender's own echo included, since the server is authoritative
     * and nobody applies optimistically — with this seat's view of where they left the table.
     * The bots' moves ride in the same batch, so one send answers "what happened because of
     * that".
     *
     * Each entry carries this seat's view *after that action* (see [EventEntry]), which is
     * what a client animates from; the top-level [view] is where the batch ends, kept for
     * readers that only want the destination.
     */
    @Serializable
    @SerialName("events")
    data class Events(
        val events: List<EventEntry>,
        val nextIndex: Int,
        @EncodeDefault(EncodeDefault.Mode.ALWAYS) val view: PlayerView? = null,
        /** See [Sync.away]. */
        val away: List<String> = emptyList(),
        /**
         * What the bots said while making these moves.
         *
         * Carried **inside** the events message rather than as a message of its own, because
         * a socket gets one prebuilt string per response: a separate `said` would need a list
         * per seat and a second send. It also arrives in step with the moves it comments on,
         * which is what a strip wants.
         */
        val said: List<TableTalk> = emptyList(),
        /** See [Sync.plan]. On every batch, because a lane locks on an ordinary action. */
        val plan: CoalitionPlan? = null,
    ) : ServerMessage

    /**
     * The answer to [ClientMessage.Resync]: the log from the cursor, where it now ends, and —
     * so a reconnector can land on the present rather than on its stale last view — the
     * current per-seat [view]. The catch-up entries carry no per-event views (the room does
     * not keep every past state); a client jumps the cursor and renders [view].
     */
    @Serializable
    @SerialName("sync")
    data class Sync(
        val events: List<EventEntry>,
        val nextIndex: Int,
        val view: PlayerView? = null,
        /**
         * The seats a bot is playing because their person has gone, by engine player id.
         *
         * It cannot ride on the [PlayerView]: `isHuman` and `isBot` are inside the canonical
         * state hash, so the room deliberately never writes the takeover into the game — a
         * round whose recording could not replay would be a worse bug than a missing label.
         * This is the room telling the table what the state is not allowed to say.
         *
         * Empty by default, and omitted when empty, so a table with everybody present sends
         * nothing extra.
         */
        val away: List<String> = emptyList(),
        /**
         * The coalition's shared plan as it stands, or absent when none does.
         *
         * Beside the view rather than in it, for the reason [away] is: the plan is room state
         * and never game state (design D6), so it must not ride inside a `PlayerView` that the
         * engine projects. The same board goes to every seat, the caller's included — it is
         * built only from public claims. A client sets its copy from whichever message carries
         * it, so a reconnect lands on the present plan rather than on the one it remembered.
         */
        val plan: CoalitionPlan? = null,
    ) : ServerMessage

    /** Somebody said something. Broadcast to every seat, the Vinto caller included. */
    @Serializable
    @SerialName("said")
    data class Said(val talk: TableTalk) : ServerMessage

    /** The lobby changed: somebody joined, left, or a bot was added or removed. Broadcast. */
    @Serializable
    @SerialName("lobby")
    data class Lobby(val lobby: LobbyView) : ServerMessage

    /**
     * A round was dealt. [standings] rides along when the deal followed a between-rounds
     * agreement and is absent on the first deal — the countdown path has nothing to report.
     */
    @Serializable
    @SerialName("started")
    data class Started(
        @EncodeDefault(EncodeDefault.Mode.ALWAYS) val view: PlayerView? = null,
        val nextIndex: Int,
        val standings: List<RoundResult>? = null,
    ) : ServerMessage

    /** A round finished and the session continues: the scores so far, awaiting agreement. */
    @Serializable
    @SerialName("between-rounds")
    data class BetweenRounds(
        @EncodeDefault(EncodeDefault.Mode.ALWAYS) val view: PlayerView? = null,
        val standings: List<RoundResult>,
        val nextIndex: Int,
    ) : ServerMessage

    /** The session is over but the room still stands — the scoreboard outlives the game. */
    @Serializable
    @SerialName("ended")
    data class Ended(val reason: String) : ServerMessage

    /** The room is going away; the socket closes right after. */
    @Serializable
    @SerialName("closed")
    data class Closed(val reason: String) : ServerMessage

    /**
     * A refusal. [retryAfterMs] is present exactly when the refusal was a rate limit, so a
     * client can back off rather than hammer.
     */
    @Serializable
    @SerialName("error")
    data class Error(
        val message: String,
        val retryAfterMs: Double? = null,
        /**
         * A machine-readable reason, for the refusals a screen has to act on rather than show.
         * [UPDATE_NEEDED_CODE] is the one so far: the app is below the room's floor and no
         * retry will help. The [message] is still a sentence, because a build older than this
         * field shows it as it is.
         */
        val code: String? = null,
    ) : ServerMessage
}

/**
 * The serializer both ends of the wire use.
 *
 * Built *from* [VintoJson] so the payloads inside a message — `GameAction`, `PlayerView` —
 * encode exactly as the engine's canonical form does; the two deltas are the message layer's
 * own. `classDiscriminator = "type"` writes the tag JavaScript has always switched on, and
 * `ignoreUnknownKeys` is the compatibility rule made mechanical: an older client reading a
 * newer room's message skips what it does not know instead of dying on it.
 */
val ProtocolJson: Json = Json(from = VintoJson) {
    classDiscriminator = "type"
    ignoreUnknownKeys = true
}
