package game.vinto.app.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import game.vinto.app.Counting
import game.vinto.app.LocalCounting
import game.vinto.app.art.Res
import game.vinto.app.art.chapter_covered
import game.vinto.app.art.chapter_to_come
import game.vinto.app.art.intro_part_covered
import game.vinto.app.art.intro_part_to_come
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
import game.vinto.client.Anchor
import game.vinto.client.Chapter
import game.vinto.client.INTRO_BEATS
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
import game.vinto.client.heldStill
import game.vinto.client.introStep
import game.vinto.client.lessonFor
import game.vinto.client.teachingSession
import game.vinto.engine.PlayerView
import game.vinto.engine.mySeat
import game.vinto.protocol.AnalyticsEvent
import game.vinto.shapes.Card
import game.vinto.shapes.GamePhase
import game.vinto.shapes.Rank
import kotlinx.coroutines.CoroutineDispatcher
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

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
 * of them will not read. **Fixed**, not merely allowed: the body is this tall whether the beat
 * is one line or six, so "Go on" does not move between one beat and the next. It used to be a
 * ceiling, and the box grew and shrank with every paragraph under a thumb with fourteen
 * beats to press.
 */
private val TalkMax = 260.dp

/** What the felt has to keep, around the talking coach, before the body gives height up. */
private val TalkChrome = 150.dp

/** Small enough to sit in a line of text, large enough to recognise on the felt. */
private val NoteCard = 34.dp

/**
 * The row the lesson holds its cards up in: this tall on every beat, whatever is in it.
 *
 * The cards were 46 dp wide and left-aligned, five small pictures in the corner of a
 * paragraph. They are the *subject* of these beats — the whole card tour is "this is what a
 * Jack looks like" — so they are centred and as large as the row allows: one card fills the
 * row's height, five share the width. The row keeps its height when it holds one card and
 * when it holds five, which is the other half of "Go on" staying put.
 */
private val HeldRow = 120.dp

/** The held-up row's share of a talk body that has had to shrink for a short screen. */
private const val HELD_SHARE = 0.42f

/** The deck's proportion, for sizing a held-up card from the row's height. */
private const val CARD_ASPECT = 825f / 1125f

/** Narrower than this and the band under the piles cannot hold a title; the coach goes up. */
private val BandMin = 168.dp

/** Above the hand, so the pointer at one of its cards is not under the coach's edge. */
private val BandGap = 8.dp

/** How far above the hand the band reaches: room for a two-line title and the dots. */
private val BandDepth = 96.dp

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
 *   time over. And while it talks, nothing on the table can be touched: the first thing a
 *   newcomer does with five breathing cards under a paragraph is tap one.
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
    val attendance = rememberAttendance()
    DisposableEffect(Unit) { onDispose { attendance.count(finished = false) } }

    val session = remember { teachingSession(botDispatcher) }
    val holder = rememberHolder(session)
    val log by session.log.collectAsState()
    val coaching = remember { Coached() }
    var helpOpen by remember { mutableStateOf(false) }
    val play = rememberActor(holder)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val room = Room(TableLayout.forScreen(maxWidth, maxHeight), maxHeight)

        CardStage(
            frames = session.frames,
            live = holder.current,
            sizes = room.layout.sizes,
            // A lesson runs at a lesson's speed. Somebody who set the table to brisk did so
            // knowing the game; somebody in here does not, and the pauses are most of what is
            // being taught — the beat where a player thinks, and the one where everybody else
            // reads what they did.
            pace = maxOf(pace.scale, Pace.CALM.scale),
            coaching = Coaching(
                hold = { coaching.showing?.talkId != null },
                pointer = coaching.showing?.point,
            ),
            recent = log,
        ) { shown, told ->
            val table = coaching.read(
                shown = shown,
                live = holder.tableFor(shown).beforeTheEnd(shown.vintoCallerId != null),
                // The coach reads the seat's own memory of its hand — what the player has
                // seen, never what they have not — so "give up your worst card" can name one.
                memory = session.rememberedHand(),
            )
            // Chapters reached, counted as the coach covers them. `Taught.chapters` only
            // grows, so its size is how far somebody got before they stopped.
            attendance.reached(coaching.taught.chapters.size)

            LessonTable(
                state = TableState(
                    view = shown,
                    table = table,
                    refusal = holder.refusal,
                    recent = told,
                    round = 1,
                    teaching = true,
                ),
                coaching = coaching,
                room = room,
                hooks = Hooks(
                    act = { coaching.act(it, play) },
                    onHelp = { helpOpen = true },
                    onSettings = onSettings,
                    onDone = {
                        attendance.count(finished = true)
                        onDone()
                    },
                ),
            )
        }
    }

    HelpSheet(open = helpOpen, now = holder.table.help, onDismiss = { helpOpen = false })
}

/**
 * The table with the coach over it.
 *
 * **Over the table, not inside the rail.** The lesson used to live in the control panel,
 * which made the panel as tall as a lesson and the felt as short as whatever was left — four
 * hands, two piles and three name plates crushed into a third of the screen, with the side
 * seats' cards re-flowing into rows. A tutorial that deforms the game it is teaching is
 * teaching the wrong game. So it floats above the felt instead, and the table underneath is
 * laid out exactly as it is in a real round. *Where* over the felt is decided by what it is
 * pointing at and whether it is talking — see [Slot].
 */
@Composable
private fun LessonTable(state: TableState, coaching: Coached, room: Room, hooks: Hooks) {
    val lesson = coaching.showing
    val finished = state.view.phase == GamePhase.SCORING
    val talking = lesson?.talkId != null || finished
    val stage = LocalStage.current

    Box(modifier = Modifier.fillMaxSize()) {
        TableScreen(
            state = state,
            layout = room.layout,
            onMove = hooks.act,
            onHelp = { hooks.onHelp() },
            onSettings = hooks.onSettings,
            onReport = {},
            // Nothing to explain in here that the coach is not already explaining.
            onDeck = {},
            modifier = Modifier.fillMaxSize(),
        )

        val slot = slotFor(stage, lesson, state.view, room, talking)
        Coach(
            coaching = coaching,
            finished = finished,
            slot = slot,
            talkBody = room.talkBody,
            onDone = hooks.onDone,
            modifier = when (slot) {
                is Slot.Band -> Modifier.inBand(slot.room)
                Slot.Top -> overFelt(top = true, room.layout)
                Slot.Bottom -> overFelt(top = false, room.layout)
            },
        )
    }
}

/** What the screen around the lesson can do: play a move, open the help, the settings, leave. */
private class Hooks(
    val act: (Move) -> Unit,
    val onHelp: () -> Unit,
    val onSettings: () -> Unit,
    val onDone: () -> Unit,
)

/** The shape of the screen, and what follows from it for a coach lying on the felt. */
private class Room(val layout: TableLayout, screen: Dp) {
    /** Where the felt begins: under the header. */
    val feltTop: Dp = HeaderHeight

    /** And ends: at the rail in portrait, at the foot of the screen with the rail beside it. */
    val feltBottom: Dp = screen - if (layout.landscape) 0.dp else layout.railHeight

    /**
     * The talking coach fits the felt it lies on: on a short phone, or a phone on its side,
     * the body gives up height rather than pushing "Go on" under the rail.
     */
    val talkBody: Dp = minOf(TalkMax, maxOf(CoachMax, feltBottom - feltTop - TalkChrome))
}

/**
 * What the coach has said, what it is saying, and the one deviation it remarks on.
 *
 * Snapshot state, because the stage reads [showing] through `Coaching.hold` and has to be
 * told when a talk beat ends.
 */
private class Coached {
    var taught: Taught by mutableStateOf(Taught())
        private set
    var showing: Lesson? by mutableStateOf(null)
        private set
    var strayed: Boolean by mutableStateOf(false)
        private set

    /**
     * What each button on the table is called, so the screen can tell "they pressed the one
     * I pointed at" from "they pressed the other one" — the only deviation worth remarking on.
     */
    private var labels: Map<Move, Label> = emptyMap()

    /** Said once, on the move after the player first ignores the pointer, and then dropped. */
    private var alreadySaid = false

    /**
     * Reads the position and decides what to say about it; answers the table to offer.
     *
     * Nothing to touch while the coach is talking. The stage is held for the beat, so a move
     * made now would be made against a table the player has not seen the last moves of — and
     * a newcomer's first tap used to peek a card under the welcome.
     */
    fun read(shown: PlayerView, live: Table, memory: Map<Int, Card>): Table {
        labels = live.choices.associate { it.move to it.label }
        showing = lessonFor(shown, live, taught, memory)
        return if (showing?.talkId != null) live.heldStill() else live
    }

    /** The player pressed "Go on". */
    fun heard() {
        taught = taught.heard(showing)
    }

    /**
     * Every move the player makes passes through here on its way to the engine, which is the
     * one place that knows what they *did* rather than what the table now looks like: a swap
     * and a discard leave the same phase behind, and only the action says which happened.
     * Acting is also acknowledgement — whatever the coach was saying has been in front of
     * them, and repeating it would be the coach not listening.
     */
    fun act(move: Move, play: (Move) -> Unit) {
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
}

/**
 * Where somebody stops is the useful number: a lesson abandoned at beat three and one
 * abandoned at beat twelve are different problems, and only the second is about length.
 */
private class Attendance(private val counting: Counting, private val startedAt: Long) {
    private var reached = 0
    private var counted = false

    fun reached(stage: Int) {
        reached = maxOf(reached, stage)
    }

    fun count(finished: Boolean) {
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
}

@Composable
private fun rememberAttendance(): Attendance {
    val counting = LocalCounting.current
    return remember { Attendance(counting, elapsedMs()) }
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
 * Where the coach lies over the felt.
 *
 * Three places, and the reason there are three is two reports from a phone. **Talking**, the
 * coach is a tall card and goes to whichever end of the felt is *away from what it is pointing
 * at*: at the top by default, and at the bottom when the pointer is in the upper half — the
 * tour beat used to point at the discard from under its own edge, and the seats beat at a
 * plate the coach was lying on. **Playing**, it is one line and goes in the [Band]: the empty
 * felt between the side seats, under the piles and above the player's own hand. At the top
 * it covered the opposite seat's whole row for the length of the round, which is the row a
 * player most needs to watch. The band is measured from the table as it lies, and if the
 * table has not been measured yet, or leaves no band worth the name, the top is the fallback.
 */
private sealed interface Slot {
    data object Top : Slot
    data object Bottom : Slot

    /** The room, in the stage's pixels: left and right edges, and the hand's top as bottom. */
    data class Band(val room: Rect) : Slot
}

@Composable
private fun slotFor(stage: Stage, lesson: Lesson?, view: PlayerView, room: Room, talking: Boolean): Slot {
    val density = LocalDensity.current
    if (talking) {
        val target = lesson?.point ?: return Slot.Top
        val rect = stage.boundsOf(target.key()) ?: return Slot.Top
        val middle = with(density) { ((room.feltTop + room.feltBottom) / 2).toPx() }
        return if (rect.center.y < middle) Slot.Bottom else Slot.Top
    }

    val band = bandOf(stage, view, depth = with(density) { BandDepth.toPx() }) ?: return Slot.Top
    return if (band.width >= with(density) { BandMin.toPx() }) Slot.Band(band) else Slot.Top
}

/**
 * The empty felt under the piles: bounded by the two side seats and the player's own hand.
 *
 * Read off what the table reported as it laid out — the seats' plates and cards, the hand's
 * cards — rather than re-deriving the felt's arithmetic here, so a change to the table's
 * layout moves the coach with it. Only what reaches into the strip [depth] above the hand
 * narrows the band: the left seat's plate sits at the *top* of its column and the right
 * seat's at the bottom, so counting both plates would give away a third of a phone's width
 * to a plate that is nowhere near the coach. Null until every edge has been measured.
 */
private fun bandOf(stage: Stage, view: PlayerView, depth: Float): Rect? {
    val mine = view.mySeat ?: return null
    val others = view.players.filter { it.id != mine.id }
    val left = others.getOrNull(0) ?: return null
    val right = others.getOrNull(2) ?: return null

    fun seatRects(id: String, cards: Int): List<Rect> =
        listOfNotNull(stage.boundsOf(Target.Seat(id).key())) +
            (0 until cards).mapNotNull { stage.boundsOf(Anchor.Seat(id, it).key()) }

    val hand = seatRects(mine.id, mine.cards.size).filter { it.height > 0f }
    val handTop = hand.minOfOrNull { it.top } ?: return null
    val strip = handTop - depth

    val leftSeat = seatRects(left.id, left.cards.size).ifEmpty { return null }
    val rightSeat = seatRects(right.id, right.cards.size).ifEmpty { return null }
    val leftEdge = leftSeat.filter { it.bottom > strip }.maxOfOrNull { it.right }
        ?: leftSeat.minOf { it.left }
    val rightEdge = rightSeat.filter { it.bottom > strip }.minOfOrNull { it.left }
        ?: rightSeat.maxOf { it.right }
    if (rightEdge <= leftEdge) return null

    return Rect(left = leftEdge, top = strip, right = rightEdge, bottom = handTop)
}

/**
 * The coach at one end of the felt, over it and nothing else.
 *
 * In landscape the rail stands at the side, and a coach lying across it would cover the
 * very buttons the lessons point at. And a speech bubble, not a banner: on a desktop the
 * felt is most of a metre wide, and a line of coaching stretched across all of it is
 * unreadable in the way a newspaper set as one column would be.
 */
private fun BoxScope.overFelt(top: Boolean, layout: TableLayout): Modifier = Modifier
    .align(if (top) Alignment.TopCenter else Alignment.BottomCenter)
    .padding(horizontal = Pad)
    .padding(end = if (layout.landscape) layout.railWidth else 0.dp)
    .padding(
        top = HeaderHeight + Pad,
        bottom = Pad + if (layout.landscape) 0.dp else layout.railHeight,
    )
    .widthIn(max = CoachWidth)

/**
 * Lays the coach across [room], its bottom resting just above the hand.
 *
 * Measured against the room's width and placed by hand, because the room is a rectangle in
 * the stage's pixels rather than an alignment. The node itself takes the whole parent — the
 * coach is a tappable surface, and Compose only delivers a tap to a node whose own bounds
 * contain it, so a node the size of the coach placed *outside* itself would be a coach
 * nobody could open.
 */
private fun Modifier.inBand(room: Rect): Modifier = layout { measurable, constraints ->
    val pad = Pad.roundToPx()
    val width = (room.width.roundToInt() - 2 * pad).coerceAtLeast(0)
    val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = width))
    val x = (room.left + (room.width - placeable.width) / 2f).roundToInt()
    val y = (room.bottom - BandGap.toPx() - placeable.height).roundToInt().coerceAtLeast(0)
    layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(x, y) }
}

/**
 * The lesson, floating over the felt.
 *
 * It covers whatever part of the table the beat is not about — see [Slot] — and the table
 * underneath keeps exactly the layout it has in a real round.
 *
 * A talk beat is a fixed height and scrolls inside it; a lesson given *during* play stays
 * small enough to see past, and one tap opens it.
 */
@Composable
private fun Coach(
    coaching: Coached,
    finished: Boolean,
    slot: Slot,
    talkBody: Dp,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lesson = coaching.showing
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
            // In the band the coach is narrow, and a title beside nine dots is a title
            // wrapped to four lines; there the dots go under it.
            CoachHead(lesson, coaching.taught, finished, stacked = slot is Slot.Band && !showing)
            if (showing) CoachBody(lesson, finished, coaching.strayed, talkBody)
            CoachFoot(lesson, finished, onRead = { coaching.heard() }, onDone = onDone)
        }
    }
}

/** The heading and the dots: side by side where there is room, stacked where there is not. */
@Composable
private fun CoachHead(lesson: Lesson?, taught: Taught, finished: Boolean, stacked: Boolean) {
    val title = if (finished) {
        stringResource(Res.string.teach_finished_title)
    } else {
        lesson?.teaches?.let { taughtTitle(it) } ?: stringResource(Res.string.teach_heading)
    }

    if (stacked) {
        Text(text = title, fontSize = TitleSize, fontWeight = FontWeight.Bold, color = Rail.coach)
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Progress(lesson, taught)
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Pad),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                fontSize = TitleSize,
                fontWeight = FontWeight.Bold,
                color = Rail.coach,
                modifier = Modifier.weight(1f),
            )
            Progress(lesson, taught)
        }
    }
}

/** The one button: "Go on" through a talk beat, "Done" at the end, nothing during play. */
@Composable
private fun CoachFoot(lesson: Lesson?, finished: Boolean, onRead: () -> Unit, onDone: () -> Unit) {
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

/**
 * Everything the coach is saying, under its heading.
 *
 * Split out because the card around it has enough to think about — whether it is talking,
 * whether the player has opened it, and what to do when they press the button.
 */
@Composable
private fun CoachBody(lesson: Lesson?, finished: Boolean, strayed: Boolean, talkBody: Dp) {
    val talking = lesson?.talkId != null || finished
    Column(
        modifier = Modifier
            .then(if (talking) Modifier.height(talkBody) else Modifier.heightIn(max = CoachMax))
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Tight),
    ) {
        HeldUpCards(lesson?.cards.orEmpty(), row = minOf(HeldRow, talkBody * HELD_SHARE))

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
 * The cards a lesson is talking about, held up: centred, and as large as the row allows.
 *
 * A rank explained in words is a rank the player then has to match against a picture on the
 * felt. These are the same drawables the table deals from, so there is nothing to match. See
 * [HeldRow] for why the row is a fixed height whatever it holds.
 */
@Composable
private fun HeldUpCards(cards: List<Rank>, row: Dp) {
    if (cards.isEmpty()) return

    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth().height(row).padding(vertical = Tight),
        contentAlignment = Alignment.Center,
    ) {
        val gap = Tight * 2
        val byHeight = (row - Tight * 2) * CARD_ASPECT
        val byWidth = (maxWidth - gap * (cards.size - 1)) / cards.size
        val width = minOf(byHeight, byWidth)

        Row(
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            cards.forEach { rank -> CardPicture(rank = rank, width = width) }
        }
    }
}

/**
 * The row of dots over the coach.
 *
 * Two things, in turn. While the coach is still introducing the game — the welcome, the
 * cards, the tour — a dot per beat, filled as each is read: nothing has been played yet, so
 * the chapters would stand still through fourteen taps of "Go on", and did (product owner).
 * From the moment the table is handed over, a dot per chapter of the rules, filled once the
 * player has met it.
 *
 * Each dot says what it is. They had no accessible name at all until now — nine unlabelled
 * circles conveying progress by colour alone, which is exactly the information a screen reader
 * cannot get. The words existed the whole time, in an unused `Chapter.label`.
 */
@Composable
private fun Progress(lesson: Lesson?, taught: Taught) {
    val step = introStep(lesson)
    Row(horizontalArrangement = Arrangement.spacedBy(Tight)) {
        if (step != null) {
            INTRO_BEATS.forEachIndexed { index, _ ->
                val read = index <= step
                val said = if (read) {
                    stringResource(Res.string.intro_part_covered, index + 1, INTRO_BEATS.size)
                } else {
                    stringResource(Res.string.intro_part_to_come, index + 1, INTRO_BEATS.size)
                }
                Dot(filled = read, said = said, size = IntroDot)
            }
        } else {
            Chapter.entries.forEach { chapter ->
                val name = stringResource(chapter.label())
                val met = chapter in taught.chapters
                val said = if (met) {
                    stringResource(Res.string.chapter_covered, name)
                } else {
                    stringResource(Res.string.chapter_to_come, name)
                }
                Dot(filled = met, said = said, size = DotSize)
            }
        }
    }
}

@Composable
private fun Dot(filled: Boolean, said: String, size: Dp) {
    Box(modifier = Modifier.size(size).semantics { contentDescription = said }) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = CircleShape,
            color = if (filled) Rail.coach else Rail.line,
            content = {},
        )
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

/** Fourteen of these have to fit beside a title, so they are smaller than the nine chapters'. */
private val IntroDot = 6.dp
