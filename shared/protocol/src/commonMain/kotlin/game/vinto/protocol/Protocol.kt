package game.vinto.protocol

import game.vinto.engine.PlayerView
import game.vinto.shapes.GameAction
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

/** Everything a client may say to a room. One WebSocket message each, as JSON text. */
@Serializable
sealed interface ClientMessage {

    /**
     * Take a seat, or return to one. A token means "I already hold a seat here" — the room
     * seats by token, idempotently, which is the whole reconnect story. No token means
     * "issue me one", and the answer is the one message that ever carries it raw.
     */
    @Serializable
    @SerialName("join")
    data class Join(
        val token: String? = null,
        val nickname: String? = null,
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
    ) : ServerMessage

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
