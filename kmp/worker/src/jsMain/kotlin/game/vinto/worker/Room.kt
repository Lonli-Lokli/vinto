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

/** How many bot actions to run before handing control back; a guard, not a rule. */
private const val MAX_BOT_STEPS = 200

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class Seat(
    val index: Int,
    /** The engine's player id for this seat, fixed when the room is dealt. */
    val playerId: String,
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
) {
    /** An unclaimed seat is played by a bot, per design D9. */
    val occupied: Boolean get() = tokenHash != null
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

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class RoomState(
    val roomId: String,
    val seed: Long,
    val difficulty: Difficulty,
    val seats: List<Seat>,
    val game: GameState,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val log: List<LoggedAction> = emptyList(),
) {
    val nextIndex: Int get() = log.size
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
private data class JoinResult(
    val state: RoomState,
    val seat: Int,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val error: String? = null,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
private data class ActionResult(
    val state: RoomState,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val events: List<LoggedAction> = emptyList(),
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val error: String? = null,
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
fun newRoom(roomId: String, seed: Double, difficulty: String): String {
    val chosen = Difficulty.entries.firstOrNull { it.serialName == difficulty } ?: Difficulty.MODERATE
    val game = initializeGame(seed.toLong(), chosen)

    val state = RoomState(
        roomId = roomId,
        seed = seed.toLong(),
        difficulty = chosen,
        seats = game.players.mapIndexed { index, player -> Seat(index = index, playerId = player.id) },
        game = game,
    )
    return VintoJson.encodeToString(state)
}

/**
 * Seats a client, idempotently by the token they hold.
 *
 * A reconnecting player returns to the seat they had rather than consuming a new one, which
 * is the whole reconnect story in design D9 — and the reason a dropped player's seat can be
 * played by a bot in the meantime without losing it.
 */
@JsExport
fun joinRoom(stateJson: String, token: String, nickname: String): String {
    val state = VintoJson.decodeFromString(RoomState.serializer(), stateJson)
    val hash = Sha256.hex(token)

    // A token that already holds a seat returns to it. This is the reconnect story, and it
    // is safe in a way the old `clientId` was not: knowing somebody's *name* proves nothing,
    // and the only thing that resumes a seat is the secret the room issued for it.
    state.seats.firstOrNull { it.tokenHash == hash }?.let {
        return VintoJson.encodeToString(JoinResult(state, it.index))
    }

    val free = state.seats.firstOrNull { !it.occupied }
        ?: return VintoJson.encodeToString(JoinResult(state, -1, "room is full"))

    val seated = state.seats.map {
        if (it.index == free.index) it.copy(tokenHash = hash, nickname = nickname) else it
    }

    // The engine player behind the seat becomes a human, and forgets what it was dealt.
    //
    // `initializeGame` deals one human and three bots, and the bots start knowing two of
    // their own cards — the peek every player is entitled to, taken for them because a bot
    // has no setup step. Seating a person on one of those without this would hand them two
    // cards they never looked at, and would leave the room's own bot driver playing that
    // seat, because `BotRunner` decides who it may act for from `isBot`.
    val players = state.game.players.map { player ->
        if (player.id == state.seats[free.index].playerId) {
            player.copy(isHuman = true, isBot = false, knownCardPositions = emptyList())
        } else {
            player
        }
    }

    return VintoJson.encodeToString(
        JoinResult(state.copy(seats = seated, game = state.game.copy(players = players)), free.index),
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
fun applyAction(stateJson: String, token: String, actionJson: String): String {
    val state = VintoJson.decodeFromString(RoomState.serializer(), stateJson)

    // The seat is *derived* from the credential rather than asserted next to it. There is no
    // seat parameter to disagree with the token, so there is no way to send one seat's token
    // and another seat's number and see which check notices first.
    val seatEntry = state.seats.firstOrNull { it.tokenHash == Sha256.hex(token) }
        ?: return VintoJson.encodeToString(ActionResult(state, error = "no seat holds that token"))
    val seat = seatEntry.index

    val action = try {
        VintoJson.decodeFromString(GameAction.serializer(), actionJson)
    } catch (failure: IllegalArgumentException) {
        return VintoJson.encodeToString(ActionResult(state, error = "unreadable action: ${failure.message}"))
    }

    // The seat boundary, checked before the engine sees anything. An action whose payload
    // names another player is refused here whether or not it would have been legal for them.
    actorOf(action)?.let { claimed ->
        if (claimed != seatEntry.playerId) {
            return VintoJson.encodeToString(
                ActionResult(state, error = "seat $seat may only act as ${seatEntry.playerId}"),
            )
        }
    }

    when (val validation = ActionValidator.validate(state.game, action)) {
        is Validation.Invalid ->
            return VintoJson.encodeToString(ActionResult(state, error = validation.reason))

        Validation.Valid -> Unit
    }

    val reduced = when (val result = GameEngine.reduce(state.game, action)) {
        is ReduceResult.Success -> result.state
        is ReduceResult.Failure -> return VintoJson.encodeToString(ActionResult(state, error = result.reason))
    }

    val accepted = LoggedAction(
        index = state.nextIndex,
        seat = seat,
        playerId = seatEntry.playerId,
        action = action,
    )

    val afterBots = playBots(state.copy(game = reduced, log = state.log + accepted))
    return VintoJson.encodeToString(
        ActionResult(afterBots, events = afterBots.log.drop(state.log.size)),
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
    val runner = BotRunner(start.difficulty, Random(start.seed))
    var state = start
    var steps = 0

    while (steps++ < MAX_BOT_STEPS && state.game.phase != GamePhase.SCORING) {
        val action = runner.nextAction(state.game) ?: break
        val actor = actorOf(action)
        val seat = state.seats.firstOrNull { it.playerId == actor }

        // Three reasons to stop, all of them "this is not the room's move to make":
        //  - it belongs to a seated person, whatever the runner thinks;
        //  - the validator refuses it, which would mean the room and its own driver
        //    disagreed about the rules;
        //  - the engine refuses it after validation, which should be impossible.
        val reduced = when {
            seat != null && seat.occupied -> null
            ActionValidator.validate(state.game, action) is Validation.Invalid -> null
            else -> (GameEngine.reduce(state.game, action) as? ReduceResult.Success)?.state
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

    return VintoJson.encodeToString(ViewResult(view = projectView(state.game, seatEntry.playerId)))
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

/** Exposed for the gate harness; `SEAT_COUNT` is a design constant, not a setting. */
@JsExport
fun seatCount(): Int = SEAT_COUNT
