package game.vinto.app.game

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import game.vinto.app.LocalCounting
import game.vinto.app.art.Res
import game.vinto.app.art.chapter_covered
import game.vinto.app.art.chapter_to_come
import game.vinto.app.art.teach_done
import game.vinto.app.art.teach_finished_body
import game.vinto.app.art.teach_finished_title
import game.vinto.app.art.teach_go_on
import game.vinto.app.art.teach_heading
import game.vinto.app.art.teach_watching
import game.vinto.app.elapsedMs
import game.vinto.app.glossed
import game.vinto.app.label
import game.vinto.app.noteOn
import game.vinto.app.taughtBody
import game.vinto.app.taughtTitle
import game.vinto.app.theme.ButtonTone
import game.vinto.app.theme.GameButton
import game.vinto.app.theme.Rail
import game.vinto.client.Chapter
import game.vinto.client.Label
import game.vinto.client.Lesson
import game.vinto.client.Move
import game.vinto.client.Pace
import game.vinto.client.STRAYED
import game.vinto.client.Table
import game.vinto.client.Target
import game.vinto.client.Taught
import game.vinto.client.Tone
import game.vinto.client.chapterOf
import game.vinto.client.lessonFor
import game.vinto.client.teachingSession
import game.vinto.protocol.AnalyticsEvent
import game.vinto.shapes.GamePhase
import game.vinto.shapes.Rank
import kotlinx.coroutines.CoroutineDispatcher
import org.jetbrains.compose.resources.stringResource

private val Pad = 12.dp
private val Tight = 4.dp
private val DotSize = 8.dp
private val Corner = 12.dp
private val Lift = 8.dp

/** As much of the rail as the coach may take, mid-play, before it starts scrolling. */
private val CoachMax = 120.dp

/** The widest the coach's card may lie, however wide the felt under it is. */
private val CoachWidth = 520.dp

/**
 * And how much while it is talking.
 *
 * More, because nothing is happening on the table during a talk beat — it is being held for
 * exactly this — and a paragraph the player has to discover is scrollable is a paragraph half
 * of them will not read. Fixed rather than merely allowed, so "Go on" does not move between
 * one beat and the next.
 */
private val TalkMax = 260.dp

/** Small enough to sit in a line of text, large enough to recognise on the felt. */
private val NoteCard = 34.dp

/** Large enough to read the picture, small enough that five fit in a row. */
private val TourCard = 46.dp

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
fun TeachScreen(
    botDispatcher: CoroutineDispatcher?,
    pace: Pace,
    onSettings: () -> Unit,
    onDone: () -> Unit,
) {
    // Where somebody stops is the useful number: a lesson abandoned at beat three and one
    // abandoned at beat twelve are different problems, and only the second is about length.
    val counting = LocalCounting.current
    val startedAt = remember { elapsedMs() }
    var reached by remember { mutableIntStateOf(0) }
    var counted by remember { mutableStateOf(false) }

    fun countLesson(finished: Boolean) {
        if (counted) return
        counted = true
        counting.record(
            AnalyticsEvent.Lesson(
                finished = finished,
                reachedStage = reached,
                durationMs = (elapsedMs() - startedAt).toDouble(),
            ),
        )
    }

    DisposableEffect(Unit) { onDispose { countLesson(finished = false) } }

    val session = remember { teachingSession(botDispatcher) }
    val holder = rememberHolder(session)
    val log by session.log.collectAsState()
    var taught by remember { mutableStateOf(Taught()) }
    var showing by remember { mutableStateOf<Lesson?>(null) }
    var helpOpen by remember { mutableStateOf(false) }

    // What each button on the table is called, so the screen can tell "they pressed the one I
    // pointed at" from "they pressed the other one" — the only deviation worth remarking on.
    var labels by remember { mutableStateOf(emptyMap<Move, Label>()) }
    // Said once, on the move after the player first ignores the pointer, and then dropped.
    var strayed by remember { mutableStateOf(false) }
    var alreadySaid by remember { mutableStateOf(false) }

    // Every move the player makes passes through here on its way to the engine, which is the
    // one place that knows what they *did* rather than what the table now looks like: a swap
    // and a discard leave the same phase behind, and only the action says which happened.
    // Acting is also acknowledgement — whatever the coach was saying has been in front of
    // them, and repeating it would be the coach not listening.
    val play = rememberActor(holder)
    val act: (Move) -> Unit = { move ->
        if (strayed) {
            strayed = false
            alreadySaid = true
        } else if (!alreadySaid && ignoredThePointer(showing, labels[move])) {
            strayed = true
        }

        taught = taught.heard(showing)
        if (move is Move.Send) chapterOf(move.action)?.let { taught = taught.withChapter(it) }
        play(move)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val layout = TableLayout.forScreen(maxWidth, maxHeight)

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
            labels = table.choices.associate { it.move to it.label }
            showing = lessonFor(shown, table, taught)
            // Chapters reached, counted as the coach covers them. `Taught.chapters` only
            // grows, so its size is how far somebody got before they stopped.
            reached = maxOf(reached, taught.chapters.size)

            Box(modifier = Modifier.fillMaxSize()) {
                TableScreen(
                    state = TableState(
                        view = shown,
                        table = table,
                        refusal = holder.refusal,
                        recent = log,
                        round = 1,
                        teaching = true,
                    ),
                    layout = layout,
                    onMove = act,
                    onHelp = { helpOpen = true },
                    onSettings = onSettings,
                    onReport = {},
                    // Nothing to explain in here that the coach is not already explaining.
                    onDeck = {},
                    modifier = Modifier.fillMaxSize(),
                )

                // **Over the table, not inside the rail.**
                //
                // The lesson used to live in the control panel, which made the panel as tall
                // as a lesson and the felt as short as whatever was left — four hands, two
                // piles and three name plates crushed into a third of the screen, with the
                // side seats' cards re-flowing into rows. A tutorial that deforms the game it
                // is teaching is teaching the wrong game.
                //
                // So it floats above the rail instead, and the table underneath is laid out
                // exactly as it is in a real round. What it covers is the middle of the felt,
                // which is the emptiest part of it and the part nothing is happening in while
                // the coach has something to say.
                Coach(
                    lesson = showing,
                    taught = taught,
                    finished = shown.phase == GamePhase.SCORING,
                    strayed = strayed,
                    onRead = { taught = taught.heard(showing) },
                    onDone = {
                        countLesson(finished = true)
                        onDone()
                    },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(horizontal = Pad)
                        // Over the felt only. In landscape the rail stands at the side, and a
                        // coach lying across it would cover the very buttons the lessons
                        // point at.
                        .padding(end = if (layout.landscape) layout.railWidth else 0.dp)
                        .padding(top = HeaderHeight + Pad)
                        // A speech bubble, not a banner: on a desktop the felt is most of a
                        // metre wide, and a line of coaching stretched across all of it is
                        // unreadable in the way a newspaper set as one column would be.
                        .widthIn(max = CoachWidth),
                )
            }
        }
    }

    HelpSheet(open = helpOpen, now = holder.table.help, onDismiss = { helpOpen = false })
}

/**
 * Whether the player pressed a different button from the one being pointed at.
 *
 * Only about buttons, and only when both are known: a tap on a card is usually the lesson's
 * own instruction answered slightly differently — a different card of your own to peek at is
 * not a deviation, it is a choice the rules give you.
 */
private fun ignoredThePointer(lesson: Lesson?, chosen: Label?): Boolean {
    val pointedAt = (lesson?.point as? Target.Button)?.label ?: return false
    return chosen != null && chosen != pointedAt
}

/**
 * The lesson, floating above the rail.
 *
 * It began life inside the control panel, which was wrong in a way that only showed up on a
 * phone: the panel became as tall as a lesson and the felt as short as whatever was left, so
 * four hands, two piles and three name plates ended up crushed into a third of the screen with
 * the side seats' cards re-flowing into rows. A tutorial that deforms the game it is teaching
 * is teaching a different game.
 *
 * Above the rail it covers the middle of the felt instead — the emptiest part of the table,
 * and the part where nothing is happening while the coach has something to say. The table
 * underneath keeps exactly the layout it has in a real round.
 *
 * It is bounded and scrolls: a talk beat may take most of the felt, since the table is held
 * for it anyway, but a lesson given *during* play stays small enough to see past.
 */
@Composable
private fun Coach(
    lesson: Lesson?,
    taught: Taught,
    finished: Boolean,
    strayed: Boolean,
    onRead: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val talking = lesson?.talkId != null || finished
    if (lesson == null && !finished) return

    // Open while it is talking, because a talk beat holds the table and there is nothing else
    // to look at. **Shut while the game is being played**, down to one line, because
    // everything under it is something the player has to be able to see and touch — their own
    // hand, the deck, the pile. One tap opens it for as long as they want it.
    var open by remember(lesson?.talkId, lesson?.teaches) { mutableStateOf(false) }
    val showing = talking || open

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Corner),
        color = Rail.fill,
        border = BorderStroke(1.dp, Rail.line),
        shadowElevation = Lift,
        onClick = { if (!talking) open = !open },
    ) {
        Column(
            modifier = Modifier.padding(Pad),
            verticalArrangement = Arrangement.spacedBy(Tight),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Pad),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = when {
                        finished -> stringResource(Res.string.teach_finished_title)
                        else -> lesson?.teaches?.let { taughtTitle(it) }
                            ?: stringResource(Res.string.teach_heading)
                    },
                    fontSize = TitleSize,
                    fontWeight = FontWeight.Bold,
                    color = Rail.coach,
                    modifier = Modifier.weight(1f),
                )
                Progress(taught.chapters)
            }

            if (showing) CoachBody(lesson, finished, strayed)

            when {
                finished -> GameButton(
                    label = stringResource(Res.string.teach_done),
                    tone = ButtonTone.PLAY,
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth(),
                )

                lesson?.talkId != null -> GameButton(
                    label = stringResource(Res.string.teach_go_on),
                    tone = ButtonTone.KEEP,
                    onClick = onRead,
                    modifier = Modifier.fillMaxWidth(),
                )

                else -> Unit
            }
        }
    }
}

/**
 * Everything the coach is saying, under its heading.
 *
 * Split out because the card around it has enough to think about — whether it is talking,
 * whether the player has opened it, and what to do when they press the button.
 */
@Composable
private fun CoachBody(lesson: Lesson?, finished: Boolean, strayed: Boolean) {
    Column(
        modifier = Modifier
            .heightIn(max = if (lesson?.talkId != null || finished) TalkMax else CoachMax)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Tight),
    ) {
        HeldUpCards(lesson?.cards.orEmpty())

        if (strayed) {
            Text(text = taughtBody(STRAYED), fontSize = DetailSize, color = Rail.note)
        }

        Text(
            text = when {
                finished -> stringResource(Res.string.teach_finished_body)
                lesson != null -> taughtBody(lesson.teaches)
                else -> stringResource(Res.string.teach_watching)
            },
            fontSize = DetailSize,
            color = Rail.inkDim,
        )

        lesson?.noteRank?.let { rank -> Note(noteOn(rank), rank) }

        lesson?.gloss?.let { gloss ->
            Text(text = glossed(gloss), fontSize = DetailSize, color = Rail.inkDim)
        }
    }
}

/** A card being met for the first time, with the card. */
@Composable
private fun Note(note: String, rank: Rank?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Tight * 2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        rank?.let { CardPicture(rank = it, width = NoteCard) }

        Text(
            text = note,
            fontSize = DetailSize,
            fontWeight = FontWeight.SemiBold,
            color = Rail.note,
        )
    }
}

/**
 * The cards a lesson is talking about, held up.
 *
 * A rank explained in words is a rank the player then has to match against a picture on the
 * felt. These are the same drawables the table deals from, so there is nothing to match.
 */
@Composable
private fun HeldUpCards(cards: List<Rank>) {
    if (cards.isEmpty()) return

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Tight),
        horizontalArrangement = Arrangement.spacedBy(Tight * 2),
    ) {
        cards.forEach { rank -> CardPicture(rank = rank, width = TourCard) }
    }
}

/**
 * A dot per chapter of the rules, filled once the player has met it.
 *
 * Each dot says what it is. They had no accessible name at all until now — nine unlabelled
 * circles conveying progress by colour alone, which is exactly the information a screen reader
 * cannot get. The words existed the whole time, in an unused `Chapter.label`.
 */
@Composable
private fun Progress(met: Set<Chapter>) {
    Row(horizontalArrangement = Arrangement.spacedBy(Tight)) {
        Chapter.entries.forEach { chapter ->
            val name = stringResource(chapter.label())
            val said = if (chapter in met) {
                stringResource(Res.string.chapter_covered, name)
            } else {
                stringResource(Res.string.chapter_to_come, name)
            }

            Box(modifier = Modifier.size(DotSize).semantics { contentDescription = said }) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = CircleShape,
                    color = if (chapter in met) Rail.coach else Rail.line,
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

private val TitleSize = 16.sp
private val DetailSize = 13.sp
