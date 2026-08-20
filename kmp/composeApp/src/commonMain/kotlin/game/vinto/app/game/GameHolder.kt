package game.vinto.app.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import game.vinto.client.LocalGameSession
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
    private val session: LocalGameSession,
    private val view: State<PlayerView>,
) {
    /** Recent moves, oldest first, for the strip under the prompt. */
    val log get() = session.log

    /** What there is to see, for the stage to play. */
    val scenes get() = session.scenes

    var question: Question by mutableStateOf(Question.None)
        private set

    /** The last thing the engine refused, until the next move clears it. */
    var refusal: String? by mutableStateOf(null)
        private set

    val playerId: String get() = session.playerId
    val current: PlayerView get() = view.value
    val table: Table get() = tableFor(view.value, question)
    val isOver: Boolean get() = session.isOver

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

            is Move.Send -> {
                refusal = session.dispatch(move.action)
                if (refusal == null) question = Question.None
            }
        }
    }
}

/** A holder for one round, rebuilt when the round is. */
@Composable
fun rememberHolder(session: LocalGameSession): GameHolder {
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
    return remember(holder, scope) {
        { move ->
            scope.launch {
                holder.act(move)
                onEachMove()
            }
        }
    }
}
