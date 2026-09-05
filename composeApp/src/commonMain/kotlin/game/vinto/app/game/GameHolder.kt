package game.vinto.app.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import game.vinto.app.crash.Where
import game.vinto.app.theme.LocalFeedback
import game.vinto.client.GameSession
import game.vinto.client.Move
import game.vinto.client.Question
import game.vinto.client.Table
import game.vinto.client.tableFor
import game.vinto.engine.PlayerView
import game.vinto.engine.PublicReveal
import game.vinto.shapes.CoalitionPlan
import game.vinto.shapes.TableTalk
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
    /**
     * Seats a bot is covering, collected by the screen so a change repaints.
     *
     * A `State` for the same reason [view] is one: the session publishes a flow, and a table
     * that read `.value` would label the seat correctly once and then never again.
     */
    private val away: State<Set<String>> = mutableStateOf(emptySet()),
    /** The coalition's shared plan, as the session last had it; the board is drawn from it. */
    private val plan: State<CoalitionPlan?> = mutableStateOf(null),
    /** What the round has turned face up so far, which is what tells a plan its claim was wrong. */
    private val reveals: State<List<PublicReveal>> = mutableStateOf(emptyList()),
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
     * The suggestion standing for this seat, if one is.
     *
     * Newest wins and there is only ever one: a rail offering three people's suggestions at
     * once is a rail nobody reads, and the freshest is the one that knows most about the
     * position. Cleared when it is acted on, declined, or a move lands — a suggestion about a
     * table that has since moved is not a suggestion any more.
     */
    var offered: TableTalk.Proposal? by mutableStateOf(null)
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
    val table: Table get() = tableFor(view.value, question, away.value, offered, plan.value, reveals.value)
    val isOver: Boolean get() = session.isOver

    /**
     * The table for a given view, which is not always the live one.
     *
     * While the bots' moves are being animated the screen is drawing a table a move or two in
     * the past (see `CardStage`), and it has to draw the *controls* from the same moment —
     * offering the buttons of a position the player cannot see yet is how a game gets played
     * by accident.
     */
    fun tableFor(view: PlayerView): Table =
        tableFor(view, question, away.value, offered, plan.value, reveals.value)

    /** One sentence off the channel, for the holder to keep if it is addressed here. */
    fun heard(talk: TableTalk) {
        if (talk is TableTalk.Proposal && talk.to == session.playerId) offered = talk
    }

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
            // Talk is not held behind `sending`. Nothing waits on a sentence — the room's
            // answer to one is the broadcast — so making it queue behind a move in flight
            // would mean a player who wanted to say "wait" had to wait first.
            // Not held behind `sending` either: ending your share of a window is not a move,
            // and a player who has finished talking should not wait on one.
            is Move.Done -> {
                refusal = session.doneConferring()
                if (refusal == null) question = Question.None
            }

            is Move.Say -> {
                refusal = session.say(move.talk)
                if (refusal == null) {
                    question = Question.None
                    offered = null
                }
            }

            // Planning is talk-shaped: nothing waits on it and the answer is the board coming
            // back, so neither is held behind `sending` either.
            is Move.Plan -> {
                refusal = session.editPlan(move.edit)
                if (refusal == null) question = Question.None
            }

            is Move.Agree -> {
                refusal = session.agreePlan(move.agree)
                if (refusal == null) question = Question.None
            }

            is Move.Send -> {
                if (sending) return
                sending = true
                try {
                    refusal = session.dispatch(move.action)
                    if (refusal == null) {
                        question = Question.None
                        offered = null
                    }
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
    val away = session.away.collectAsState()
    val plan = session.plan.collectAsState()
    val reveals = session.reveals.collectAsState()

    // The one place a local game and an online one both pass through, which is why the crash
    // reporter's address is written here rather than in each table screen. Cleared on the way
    // out, so a crash in the menu is not filed against the game before it.
    DisposableEffect(session) {
        onDispose { Where.atTable(null) }
    }
    Where.atTable(view.value)

    val holder = remember(session) { GameHolder(session, view, away, plan, reveals) }

    // The one place the talk channel becomes something a player can act on. A suggestion
    // addressed to this seat becomes the one-tap move at the top of the rail; everything else
    // is already in the strip, because the session puts it there.
    LaunchedEffect(session) {
        session.talk.collect { holder.heard(it) }
    }

    return holder
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
        {
                move ->
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

/**
 * The table's taps, minus the ones that would land on a hand the engine has already changed.
 *
 * The screen draws the table as it was after the move being animated, and the taps are built
 * from that same picture. While the picture lags the engine — a toss-in flying to the pile —
 * a tap on "card 3" names card 3 *as shown*, and the engine reads card 3 *as it is now*: a
 * hand that lost a card has slid, and the second tap on the same slot threw a different card,
 * which cost a penalty. Reported from a phone, twice on one window.
 *
 * Only the viewer's own hand is checked, and only their own taps are withheld: another seat's
 * throw changes nothing about where this player's cards are, so their window stays open while
 * the felt catches up — which, online, is the moment two people throw at once.
 */
internal fun Table.withoutStaleTaps(shown: PlayerView, live: PlayerView): Table {
    if (shown.showsTheSameHandAs(live)) return this
    return copy(taps = taps.filterKeys { it.playerId != shown.viewerId })
}

/**
 * Whether the hand drawn on screen is still the hand the engine holds.
 *
 * The one question two views of the same seat can be asked while the table is a move behind,
 * and the answer decides everything addressed *by position*: which card a tap names, and
 * which slot a coach's finger is over. False means the screen is drawing a hand that has
 * since gained or lost a card, so every position after the change points at its neighbour.
 */
internal fun PlayerView.showsTheSameHandAs(live: PlayerView): Boolean {
    val mine = players.firstOrNull { it.id == viewerId }?.cards
    val theirs = live.players.firstOrNull { it.id == viewerId }?.cards
    return mine == theirs
}
