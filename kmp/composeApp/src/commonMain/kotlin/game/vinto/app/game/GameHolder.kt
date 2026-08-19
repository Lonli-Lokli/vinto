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
import game.vinto.shapes.Difficulty
import kotlinx.coroutines.Dispatchers
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

    /** Cards that visibly moved, for the overlay to fly. */
    val flights get() = session.flights

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

/**
 * A game that survives recomposition, and the collectors that feed it.
 *
 * Keyed on the seed so that "play again" is a new key rather than a mutation — which is also
 * why the seed is a parameter here: choosing one is ambient randomness, and the session
 * refuses to do it for exactly that reason.
 */
@Composable
fun rememberGame(seed: Long, difficulty: Difficulty): GameHolder {
    val session = remember(seed, difficulty) {
        LocalGameSession(seed = seed, difficulty = difficulty, botDispatcher = Dispatchers.Default)
    }
    val view = session.view.collectAsState()
    return remember(session) { GameHolder(session, view) }
}

/** Dispatches [move] from the composition, on a scope tied to the screen. */
@Composable
fun rememberActor(holder: GameHolder): (Move) -> Unit {
    val scope = rememberCoroutineScope()
    return remember(holder, scope) { { move -> scope.launch { holder.act(move) } } }
}
