package game.vinto.app.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import game.vinto.app.theme.LocalFeedback
import game.vinto.client.GameSession
import game.vinto.client.Move
import game.vinto.client.Question
import game.vinto.client.Table
import game.vinto.client.tableFor
import game.vinto.engine.PlayerView
import kotlinx.coroutines.launch

/**
 * The screen's hold on one game.
 *
 * It owns three things and nothing else: the session, the question the screen is currently
 * putting to the player, and the last refusal. Everything a screen draws comes from
 * [table], which is a pure function of the view — so this class cannot get the game wrong,
 * only how long it holds on to it.
 *
 * Bots think on [Dispatchers.Default]: a moderate search is up to 1.6 s of work, and doing
 * that on the frame thread would freeze the table mid-animation.
 */
class GameHolder(
    // The interface, not the local session: the holder is the seam design R1 promises, and
    // typing it here is what makes an online game the same screens over a different session.
    private val session: GameSession,
    private val view: State<PlayerView>,
) {
    /** Recent moves, oldest first, for the strip under the prompt. */
    val log get() = session.log

    /** What there is to see, for the stage to play. */
    val frames get() = session.frames

    var question: Question by mutableStateOf(Question.None)
        private set

    /** The last thing the engine refused, until the next move clears it. */
    var refusal: String? by mutableStateOf(null)
        private set

    /**
     * Whether a move is on the wire and unanswered.
     *
     * Always false for a heartbeat in a local game, and worth drawing in a remote one: a tap
     * that reaches a room over a phone's network takes long enough that a table which does
     * nothing looks like a table that missed it.
     */
    var sending: Boolean by mutableStateOf(false)
        private set

    val playerId: String get() = session.playerId
    val current: PlayerView get() = view.value
    val table: Table get() = tableFor(view.value, question)
    val isOver: Boolean get() = session.isOver

    /**
     * The table for a given view, which is not always the live one.
     *
     * While the bots' moves are being animated the screen is drawing a table a move or two in
     * the past (see `CardStage`), and it has to draw the *controls* from the same moment —
     * offering the buttons of a position the player cannot see yet is how a game gets played
     * by accident.
     */
    fun tableFor(view: PlayerView): Table = tableFor(view, question)

    /**
     * Acts on whatever the player touched.
     *
     * A question is answered here and goes no further; a move goes to the engine and, if it
     * lands, wipes the question — the screen's half-finished thought is finished.
     */
    suspend fun act(move: Move) {
        when (move) {
            is Move.Ask -> {
                question = move.question
                refusal = null
            }

            // One at a time, and the second tap is dropped rather than queued. Locally this
            // never mattered — the reducer answers in the same frame — but a remote session
            // holds a *single* waiter for the answer it is expecting, so a second move sent
            // while the first is in flight replaces that waiter and the first hangs until it
            // times out. The player sees their own first move stall because they hurried it.
            is Move.Send -> {
                if (sending) return
                sending = true
                try {
                    refusal = session.dispatch(move.action)
                    if (refusal == null) question = Question.None
                } finally {
                    sending = false
                }
            }
        }
    }
}

/** A holder for one round, rebuilt when the round is. */
@Composable
fun rememberHolder(session: GameSession): GameHolder {
    val view = session.view.collectAsState()
    return remember(session) { GameHolder(session, view) }
}

/**
 * Dispatches [move] from the composition, on a scope tied to the screen.
 *
 * @param onEachMove run after every move lands. The game is written down here rather than on
 *   a timer or at the end: a card game that loses your round because the phone rang is one
 *   you do not open again, and a round is a few kilobytes.
 */
@Composable
fun rememberActor(holder: GameHolder, onEachMove: () -> Unit = {}): (Move) -> Unit {
    val scope = rememberCoroutineScope()
    val feedback = LocalFeedback.current
    return remember(holder, scope, feedback) {
        { move ->
            scope.launch {
                holder.act(move)
                // A refusal is a rule the player has not met yet, and it arrives as a line of
                // small text in a panel they are not looking at. The phone says it too.
                if (holder.refusal != null) feedback.refuse()
                onEachMove()
            }
        }
    }
}
