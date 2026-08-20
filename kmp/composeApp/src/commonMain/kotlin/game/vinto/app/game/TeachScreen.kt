package game.vinto.app.game

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import game.vinto.client.Chapter
import game.vinto.client.Lesson
import game.vinto.client.Move
import game.vinto.client.Pace
import game.vinto.client.Table
import game.vinto.client.Taught
import game.vinto.client.Tone
import game.vinto.client.chapterOf
import game.vinto.client.lessonFor
import game.vinto.client.teachingSession
import game.vinto.shapes.GamePhase
import kotlinx.coroutines.CoroutineDispatcher

private val Pad = 12.dp
private val Tight = 4.dp
private val DotSize = 8.dp

/** As much of the rail as the coach may take before it starts scrolling. */
private val CoachMax = 190.dp

/**
 * How to play, by playing.
 *
 * Not a page of rules, and not a scripted walk with the buttons locked. It is a **real round**
 * — same engine, same validator, same bots — dealt from a deck somebody arranged so that the
 * cards the lesson needs turn up, with a director whispering to the bots and a coach in the
 * rail that names whatever is in front of the player and points at it.
 *
 * Four things make it a lesson rather than a game with captions:
 *
 * - **It talks in its own time.** A beat that is something to *read* holds the table until it
 *   is acknowledged. Nothing about the game pauses — those moves were made before any of them
 *   was drawn — it is the telling that waits, which is the one thing a screen may take its
 *   time over.
 * - **It points.** One white hand, at one thing, from just outside it. A card game is taught
 *   at a table by somebody putting a finger on a card.
 * - **It reads the position rather than a step counter.** Every legal move stays legal;
 *   deviate and the coach talks about wherever you have got to instead. A tutorial that
 *   refuses your moves teaches the sequence rather than the game, and the first refusal is the
 *   moment a player learns this is not really it.
 * - **The round ends properly.** The director has a bot call Vinto, so the final round, the
 *   coalition and the scoring are *played* rather than described — the half of the game a
 *   free-play tutorial never reaches.
 *
 * One thing is held back: **Call Vinto is hidden until somebody calls it.** It is the single
 * tap that would end the lesson before it began, it cannot be undone, and a newcomer cannot
 * yet know what the gold button does. Everything else is theirs from the first frame.
 */
@Composable
fun TeachScreen(botDispatcher: CoroutineDispatcher?, pace: Pace, onDone: () -> Unit) {
    val session = remember { teachingSession(botDispatcher) }
    val holder = rememberHolder(session)
    val log by session.log.collectAsState()
    var taught by remember { mutableStateOf(Taught()) }
    var showing by remember { mutableStateOf<Lesson?>(null) }
    var helpOpen by remember { mutableStateOf(false) }

    // Every move the player makes passes through here on its way to the engine, which is the
    // one place that knows what they *did* rather than what the table now looks like: a swap
    // and a discard leave the same phase behind, and only the action says which happened.
    // Acting is also acknowledgement — whatever the coach was saying has been in front of
    // them, and repeating it would be the coach not listening.
    val play = rememberActor(holder)
    val act: (Move) -> Unit = { move ->
        taught = taught.heard(showing)
        if (move is Move.Send) chapterOf(move.action)?.let { taught = taught.withChapter(it) }
        play(move)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val layout = TableLayout.forScreen(maxHeight)

        CardStage(
            frames = session.frames,
            live = holder.current,
            sizes = layout.sizes,
            // A lesson runs at a lesson's speed. Somebody who set the table to brisk did so
            // knowing the game; somebody in here does not, and the pauses are most of what is
            // being taught — the beat where a player thinks, and the one where everybody else
            // reads what they did.
            pace = maxOf(pace.scale, Pace.CALM.scale),
            coaching = Coaching(
                hold = { showing?.talkId != null },
                pointer = showing?.point,
            ),
        ) { shown ->
            val table = holder.tableFor(shown).beforeTheEnd(shown.vintoCallerId != null)
            showing = lessonFor(shown, table, taught)

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
                        lesson = showing,
                        prompt = table.prompt,
                        taught = taught,
                        finished = shown.phase == GamePhase.SCORING,
                        onRead = { taught = taught.heard(showing) },
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
 * The lesson strip: what is happening, why it matters, and how much of the game is left.
 *
 * It lives in the control rail's reserved height rather than in a band above the table.
 * Stacked above, it cost the felt 150 dp and the side seats' hands re-flowed into rows — the
 * lesson was being taught on a table that was not the one being learned.
 */
@Composable
private fun Coach(
    lesson: Lesson?,
    prompt: String,
    taught: Taught,
    finished: Boolean,
    onRead: () -> Unit,
    onDone: () -> Unit,
) {
    // The panel below already says what is being asked. The lesson names itself only when it
    // is talking about something else — otherwise the same sentence is on screen twice.
    val title = when {
        finished -> "That is the whole game"
        lesson?.title == null -> null
        lesson.title.equals(prompt, ignoreCase = true) -> null
        else -> lesson.title
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            // Bounded, and scrolling inside if it has to. The rail is shared with the game's
            // own controls, and a King's fourteen rank chips plus a three-line prompt plus a
            // coach is more than a phone has — something has to give, and it should be the
            // lesson's commentary rather than the table it is about.
            .heightIn(max = CoachMax)
            .verticalScroll(rememberScrollState())
            .animateContentSize(),
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
            Progress(taught.chapters)
        }

        Text(
            text = when {
                finished -> "Every hand is face up and the round is scored. Every rule you met " +
                "in here is one a real game plays by — there is no practice version of it."
                lesson != null -> lesson.body
                else -> "Watch what they take, and what they put back down."
            },
            fontSize = DetailSize,
            color = RailInkDim,
        )

        lesson?.note?.let { note ->
            Text(
                text = note,
                fontSize = DetailSize,
                fontWeight = FontWeight.SemiBold,
                color = NoteInk,
            )
        }

        lesson?.gloss?.let { gloss ->
            Text(text = gloss, fontSize = DetailSize, color = RailInkDim)
        }

        when {
            finished -> GameButton(
                label = "Done",
                tone = ButtonTone.PLAY,
                onClick = onDone,
                modifier = Modifier.fillMaxWidth().padding(top = Tight),
            )

            lesson?.talkId != null -> GameButton(
                label = "Go on",
                tone = ButtonTone.KEEP,
                onClick = onRead,
                modifier = Modifier.fillMaxWidth().padding(top = Tight),
            )

            else -> Unit
        }

        HorizontalDivider(color = RailBorder, modifier = Modifier.padding(top = Tight))
    }
}

/** A dot per chapter of the rules, filled once the player has met it. */
@Composable
private fun Progress(met: Set<Chapter>) {
    Row(horizontalArrangement = Arrangement.spacedBy(Tight)) {
        Chapter.entries.forEach { chapter ->
            Box(modifier = Modifier.size(DotSize)) {
                Surface(
                modifier = Modifier.fillMaxSize(),
                shape = CircleShape,
                color = if (chapter in met) CoachInk else RailBorder,
                content = {},
            )
            }
        }
    }
}

/**
 * The table, minus the one button that would end the lesson before it began.
 *
 * Calling Vinto on turn one is legal, irreversible, and the only deviation that cannot re-arm:
 * the round is simply over, with nothing taught. It is a landmine under somebody who cannot
 * yet know what the gold button does, so it is not offered until a bot has called and the
 * coach has explained what that means. From then on it is there like anything else.
 */
private fun Table.beforeTheEnd(called: Boolean): Table =
    if (called) this else copy(choices = choices.filterNot { it.tone == Tone.STAKES })

private val CoachInk = Color(0xFF6FD3A6)

/** A card being met for the first time: the same amber the game uses for a named rank. */
private val NoteInk = Color(0xFFF2C14E)

private val TitleSize = 16.sp
private val DetailSize = 13.sp
