package game.vinto.client

import game.vinto.engine.PlayerView
import game.vinto.shapes.GameAction
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A game, from the UI's point of view.
 *
 * The interface exists so that the UI cannot tell a local game from an online one. That is not
 * tidiness — it is design R1 made enforceable: single-player runs entirely on the device and
 * costs nothing to host, and it can only stay that way if choosing it is a matter of which
 * implementation gets constructed rather than of which screens exist.
 *
 * Two implementations are planned and only the first is here:
 *
 * - [LocalGameSession] — engine and bots in this process, no network, works on a plane.
 * - `RemoteGameSession` — the same surface over a WebSocket to the Durable Object, which is
 *   already server-authoritative and already sends exactly this `PlayerView`.
 *
 * The surface is deliberately narrow: a view to render, actions to send, frames to animate,
 * and events for the things that are not state — a bot having moved, a round having ended.
 */
interface GameSession {

    /** The seat this session plays. Everything rendered is from this player's side. */
    val playerId: String

    /**
     * What this player may see, and the only thing a screen should read.
     *
     * A `PlayerView` rather than a `GameState`: the local session redacts itself with the very
     * same `projectView` the server uses. A local game has no secrets to keep from its own
     * player, but rendering from the redacted shape is what stops a UI growing a dependency on
     * information that will not be there online.
     */
    val view: StateFlow<PlayerView>

    /**
     * Everything worth reacting to that is not the state itself.
     *
     * A stream rather than a latest value: one dispatch can produce several — the bots moved,
     * and then the round ended — and a flow that keeps only the newest would drop the one a
     * score screen is waiting for. One event is replayed to a late collector so a screen that
     * subscribes after a move still knows where it is.
     */
    val events: SharedFlow<SessionEvent>

    /**
     * Sends one action.
     *
     * Suspends because a bot's reply can take up to a second and a half of search (measured in
     * `PLATFORM-GATE.md` 2a.1b), which must not happen on whatever thread is drawing.
     *
     * Returns the reason it was refused, or null if it was accepted. Refusals are normal — a
     * UI can offer a move the rules disallow, and the answer is a message rather than a crash.
     */
    suspend fun dispatch(action: GameAction): String?

    /**
     * What there is to see, in the order it happened, each with the table it left behind.
     *
     * On the interface rather than on the local session alone, because this is the whole
     * point of design C1: a screen animates *frames*, and whether they were computed from a
     * local reducer or parsed off a socket is not its business. Lifting it is what let
     * `GameHolder` be typed to the interface and the same table serve both games.
     */
    val frames: SharedFlow<List<Frame>>

    /** What has happened lately, in words, newest last. May stay empty where nobody narrates. */
    val log: StateFlow<List<String>>

    /** Whether the game has finished; a session is done when this is true. */
    val isOver: Boolean
}

/** Things that happen to a session which are not simply a new view. */
sealed interface SessionEvent {

    /** Bots moved. Carries how many, so a UI can pace an animation against it. */
    data class BotsPlayed(val actions: Int) : SessionEvent

    /** The round ended; `scores` is per player, coalition members on their best hand. */
    data class RoundEnded(val scores: Map<String, Int>, val points: Map<String, Int>) : SessionEvent

    /** An action was refused, with the validator's reason. */
    data class Refused(val reason: String) : SessionEvent
}
