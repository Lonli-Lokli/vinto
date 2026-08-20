package game.vinto.app.game

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import game.vinto.app.theme.ButtonTone
import game.vinto.app.theme.GameButton
import game.vinto.app.theme.RailBorder
import game.vinto.app.theme.RailInkDim
import game.vinto.client.LocalGameSession
import game.vinto.client.Move
import game.vinto.client.Pace
import game.vinto.client.Table
import game.vinto.shapes.Difficulty
import game.vinto.shapes.GameAction
import game.vinto.shapes.GamePhase
import kotlinx.coroutines.CoroutineDispatcher

private val Pad = 12.dp
private val Tight = 4.dp
private val DotSize = 8.dp

/**
 * How to play, by playing.
 *
 * Not a page of rules and not a scripted walk with the buttons locked. It is a **real round**
 * against three easy bots, on the real table, with a coach above it that names the thing in
 * front of you and ticks it off once you have done it. Every mechanic in the game is a move
 * somebody has to make, so the shortest way to teach the game is to let it be played and to
 * say, at each moment, what the moment is.
 *
 * Two things follow from that, and both are deliberate:
 *
 * - **Nothing is blocked.** A tutorial that refuses every button but one teaches the sequence
 *   rather than the game, and the first time a player deviates it has to say no — which is the
 *   moment they learn that this is not really the game. Here every legal move is legal.
 * - **It cannot be failed or fallen out of.** The checklist is what has been *seen*, not a set
 *   of tasks in order: draw first or toss in first, it ticks the one you did. The round ends
 *   like any other round, and by then the list is usually full.
 *
 * The seed is fixed so that the deal is the same for everybody it is explained to, and the
 * game is never saved: opening the lesson does not take somebody's half-played game away.
 */
@Composable
fun TeachScreen(botDispatcher: CoroutineDispatcher?, pace: Pace, onDone: () -> Unit) {
    val session = remember { LocalGameSession(TEACHING_DEAL, Difficulty.EASY, botDispatcher) }
    val holder = rememberHolder(session)
    val log by session.log.collectAsState()
    var seen by remember { mutableStateOf(emptySet<Lesson>()) }
    var helpOpen by remember { mutableStateOf(false) }

    // Every move the player makes passes through here on its way to the engine, which is the
    // one place that knows what they *did* rather than what the table now looks like. A swap
    // and a discard leave the same phase behind; only the action says which one happened.
    val play = rememberActor(holder)
    val act: (Move) -> Unit = { move ->
        if (move is Move.Send) lessonOf(move.action)?.let { seen = seen + it }
        play(move)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val layout = TableLayout.forScreen(maxHeight)

        CardStage(
            frames = session.frames,
            live = holder.current,
            sizes = layout.sizes,
            pace = pace.scale,
        ) { shown ->
            val table = holder.tableFor(shown)

            TableScreen(
                state = TableState(
                    view = shown,
                    table = table,
                    refusal = holder.refusal,
                    recent = log,
                    round = 1,
                ),
                layout = layout,
                onMove = act,
                onHelp = { helpOpen = true },
                onReport = {},
                modifier = Modifier.fillMaxSize(),
                coach = {
                    Coach(
                        lesson = teaching(shown.phase, table, seen),
                        prompt = table.prompt,
                        seen = seen,
                        finished = shown.phase == GamePhase.SCORING,
                        onDone = onDone,
                    )
                },
            )
        }
    }

    if (helpOpen) {
        HelpSheet(now = holder.table.help, onDismiss = { helpOpen = false })
    }
}

/**
 * The lesson strip: what is happening, why it matters, and how much of the game is left to
 * meet.
 *
 * Above the table rather than over it. A coach that covers the cards teaches the player to
 * dismiss it, and everything it is talking about is on the felt underneath.
 */
@Composable
private fun Coach(
    lesson: Lesson?,
    prompt: String,
    seen: Set<Lesson>,
    finished: Boolean,
    onDone: () -> Unit,
) {
    // The panel below already says what is being asked. The lesson only names itself when it
    // is talking about something else — otherwise the same sentence is on the screen twice.
    val title = when {
        finished -> "That is the whole game"
        lesson == null -> null
        lesson.title.equals(prompt, ignoreCase = true) -> null
        else -> lesson.title
    }

    Column(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(Tight),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Pad),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title ?: "Learning the game",
                fontSize = TitleSize,
                fontWeight = FontWeight.Bold,
                color = CoachInk,
                modifier = Modifier.weight(1f),
            )
            Progress(seen)
        }

        Text(
            text = when {
                finished -> "The hands are face up and the round is scored. Every rule you " +
                    "have met here is the same one a real game plays by — there is no " +
                    "practice version of it."
                lesson != null -> lesson.detail
                else -> "The bots are taking their turns. Watching what they take from the " +
                    "pile is how you learn what is in their hands."
            },
            fontSize = DetailSize,
            color = RailInkDim,
        )

        if (finished || seen.size == Lesson.entries.size) {
            GameButton(
                label = if (finished) "Done" else "I have the idea",
                tone = ButtonTone.PLAY,
                onClick = onDone,
                modifier = Modifier.fillMaxWidth().padding(top = Tight),
            )
        }

        HorizontalDivider(color = RailBorder, modifier = Modifier.padding(top = Tight))
    }
}

/** A dot per lesson, filled once it has been met. Six of them; the whole game is six things. */
@Composable
private fun Progress(seen: Set<Lesson>) {
    Row(horizontalArrangement = Arrangement.spacedBy(Tight)) {
        Lesson.entries.forEach { lesson ->
            Box(
                modifier = Modifier.size(DotSize),
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = CircleShape,
                    color = if (lesson in seen) CoachInk else RailBorder,
                    content = {},
                )
            }
        }
    }
}

/**
 * The six things there are to learn.
 *
 * One per mechanic, in the order a round happens to meet them — which is not an order anybody
 * is held to. The words are the rules' own (`docs/game-engine/VINTO_RULES.md`), shortened to
 * what fits above a table.
 */
private enum class Lesson(val title: String, val detail: String) {
    PEEK(
        title = "Look at two of your cards",
        detail = "Five cards, face down, and you may look at two of them before the round " +
            "starts. Everybody else does the same. From here on it is memory: yours against " +
            "three other people's.",
    ),
    DRAW(
        title = "Take a card",
        detail = "From the deck, sight unseen — or the top of the discard pile, but only if " +
            "it is an action card nobody has used, and then you must use it.",
    ),
    DECIDE(
        title = "Keep it or throw it",
        detail = "Put the card into your hand — face down, in place of one you own, which " +
            "goes face up on the pile — or throw it away. Low cards are worth keeping: the " +
            "hand you want is the smallest one at the table.",
    ),
    ACTION(
        title = "Use what the card does",
        detail = "7 and 8 look at one of your own cards, 9 and 10 at somebody else's, a Jack " +
            "swaps two cards blind, a Queen looks at two and then decides, a King borrows " +
            "another rank's action. An Ace makes somebody draw.",
    ),
    TOSS(
        title = "Throw in a match",
        detail = "The moment a card goes on the pile, anybody holding that rank may throw " +
            "theirs in and be rid of it — and use its action too. Guess wrong and you take a " +
            "penalty card, and you are out of throwing in for the rest of the round.",
    ),
    VINTO(
        title = "Call it",
        detail = "At the end of your turn, if you think your hand is the lowest, call Vinto. " +
            "Everybody else gets one more turn and nobody may touch your cards. Be right and " +
            "you take the round; be wrong and the rest of the table takes it from you.",
    ),
}

/** Which lesson a move is proof of. Null for the bookkeeping moves nobody has to understand. */
private fun lessonOf(action: GameAction): Lesson? = when (action) {
    is GameAction.PeekSetupCard -> Lesson.PEEK
    is GameAction.DrawCard -> Lesson.DRAW
    is GameAction.SwapCard, is GameAction.DiscardCard -> Lesson.DECIDE
    is GameAction.UseCardAction, is GameAction.PlayDiscard, is GameAction.SelectActionTarget,
    is GameAction.DeclareKingAction,
    -> Lesson.ACTION
    is GameAction.ParticipateInTossIn -> Lesson.TOSS
    is GameAction.CallVinto -> Lesson.VINTO
    else -> null
}

/**
 * What to talk about now.
 *
 * Driven by the table in front of the player rather than by a step counter, so the coach is
 * about the position rather than about the tutorial. A lesson already met is still shown when
 * its moment comes round again — a rule is not learned the first time it is read, and the
 * second King of the round is exactly when somebody wants to be told what a King does.
 */
private fun teaching(phase: GamePhase, table: Table, seen: Set<Lesson>): Lesson? = when {
    phase == GamePhase.SETUP -> Lesson.PEEK
    table.ranks.isNotEmpty() -> Lesson.ACTION
    table.tossable() -> Lesson.TOSS
    table.choices.isEmpty() -> null
    Lesson.DRAW !in seen -> Lesson.DRAW
    Lesson.DECIDE !in seen -> Lesson.DECIDE
    else -> Lesson.entries.firstOrNull { it !in seen }
}

/** Whether the table is offering to throw a card in — the one lesson with its own window. */
private fun Table.tossable(): Boolean =
    choices.any { (it.move as? Move.Send)?.action is GameAction.ParticipateInTossIn } ||
        taps.values.any { (it as? Move.Send)?.action is GameAction.ParticipateInTossIn }

/**
 * The deal everybody is taught on.
 *
 * Fixed, so that the lesson is the same one twice and a question about it has an answer. It is
 * an ordinary deal — nothing is stacked — because a hand arranged to make the rules look tidy
 * teaches a game nobody is about to play.
 */
private const val TEACHING_DEAL = 20_260_820L

private val CoachInk = Color(0xFF6FD3A6)

private val TitleSize = 16.sp
private val DetailSize = 13.sp
