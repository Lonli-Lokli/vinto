package game.vinto.app.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import game.vinto.app.art.Res
import game.vinto.app.art.board_arrival
import game.vinto.app.art.board_broken
import game.vinto.app.art.board_hand_unknown
import game.vinto.app.art.board_named
import game.vinto.app.art.board_ours
import game.vinto.app.art.board_then
import game.vinto.app.art.board_turn_stop_spoken
import game.vinto.app.art.board_unnamed
import game.vinto.app.art.board_unplayable
import game.vinto.app.art.board_would_rather
import game.vinto.app.art.card_arrives
import game.vinto.app.art.card_back
import game.vinto.app.art.label_plan_halt
import game.vinto.app.art.label_plan_play_all
import game.vinto.app.art.label_plan_replay
import game.vinto.app.art.says_asked
import game.vinto.app.art.says_blind
import game.vinto.app.art.says_is_a
import game.vinto.app.art.says_is_a_what
import game.vinto.app.art.says_throws_word
import game.vinto.app.chipWords
import game.vinto.app.detailed
import game.vinto.app.labelled
import game.vinto.app.saysWords
import game.vinto.app.speakerName
import game.vinto.app.stepWords
import game.vinto.app.theme.GameButton
import game.vinto.app.theme.Rail
import game.vinto.app.theme.Signal
import game.vinto.client.Anchor
import game.vinto.client.Board
import game.vinto.client.CardRef
import game.vinto.client.CardWord
import game.vinto.client.Choice
import game.vinto.client.HandReading
import game.vinto.client.Move
import game.vinto.client.PlanComposer
import game.vinto.client.PlanLine
import game.vinto.client.PlanTarget
import game.vinto.client.Question
import game.vinto.client.Says
import game.vinto.client.SeatChoice
import game.vinto.client.Slot
import game.vinto.client.Speaker
import game.vinto.client.StepHealth
import game.vinto.client.StepLine
import game.vinto.client.Stop
import game.vinto.client.Table
import game.vinto.client.Transport
import game.vinto.client.TurnSentence
import game.vinto.engine.PlayerView
import game.vinto.shapes.Rank
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * The coalition's plan, on the table (design D1), as the rail draws it: **five rows that never
 * move**, and a turn a page.
 *
 * The plan stands in for the talk people have at a real table — *"you play this Queen, then I
 * throw in mine and play mine"* — so each turn is a sentence, one word per decision, and the
 * rail is a pager with one turn per page and a last page where the plan lands. Under the page:
 * the answers to the word being asked for, the stops that are the pager's indicator, and the one
 * standing button. A row with less to say stays empty rather than pulling the next one up.
 *
 * **Boxed means touchable.** A decision is a boxed word; a fact and "and we'll see" are plain;
 * the next decision is a dashed box at the end of the line. A card in the sentence is drawn as
 * the card on the felt is — its back, its owner's face, the rank the table says it is, its
 * place — and a card that is not on the table yet is rose, tagged with the turn it arrives on.
 *
 * Nothing here can act on the round. Every control is a [Move.Quiet] and the router drops
 * anything else while the plan is open, so this file cannot dispatch a `GameAction` even by
 * accident (design D2).
 */

private val Gap = 8.dp
private val Half = 4.dp
private val Hair = 2.dp
private val WordSize = 13.sp
private val WarnInk = Color(0xFFFFA39E)
private val ChipCorner = 7.dp
private val ChipInset = 5.dp
private val MarkSize = 22.dp
private val FaceSize = 18.dp
private val LabelSize = 10.sp

/** Every row under the page is this tall, and so is every row inside it: one height, aligned. */
private val RowHigh = 44.dp

/** Slow enough to read a sentence off, which is the whole point of moving it at all. */
private val HintCreep = 18.dp

/** A card in the sentence: at the height of a word, with its badges hanging off its corners. */
private val MiniWidth = 22.dp
private val MiniHeight = 30.dp
private val MiniCorner = 3.dp
private val MiniFace = 11.dp
private val MiniBadge = 8.sp
private val MiniTag = 12.dp

/** The arrow between two stops: a shaft, and two barbs meeting at its tip. */
private const val THEN_TAIL = 0.15f
private const val THEN_TIP = 0.75f
private const val THEN_BARB = 0.55f
private const val THEN_HEAD = 0.2f

/** The finish flag: a staff, and two rows of two squares hung off it. */
private const val FLAG_STAFF = 0.26f
private const val FLAG_TOP = 0.2f
private const val FLAG_FOOT = 0.82f
private const val FLAG_CELL = 0.24f
private const val FLAG_STROKE = 0.07f

/** The dashed edge of a word on offer. */
private const val DashOn = 6f
private const val DashOff = 4f

/**
 * The plan's rail, whole: the page, the answers, the stops, the button.
 *
 * **Nothing here scrolls, and nothing here moves.** The page takes what the three rows under it
 * leave, which on the same phone is the same height on every turn; the rows under it are fixed.
 * At a doubled system font a long sentence scrolls sideways inside its own row rather than
 * pushing anything down.
 */
@Composable
internal fun PlanRail(
    board: Board,
    table: Table,
    onMove: (Move) -> Unit,
    modifier: Modifier = Modifier,
) {
    val naming = table.ranks.isNotEmpty()
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Half),
    ) {
        Pages(board, table, onMove, modifier = Modifier.weight(1f).fillMaxWidth())
        // While a rank is being asked the whole set takes the throw row and the answers row
        // together, drawn inside the page — so the answers row gives its height to the page
        // rather than sitting empty under a grid that needs it.
        if (!naming) Answers(board, table, onMove)
        PlanStops(board.transport, onMove)
        // Agree was the one standing button (design D17) and drew as `firstOrNull`. It has a
        // neighbour now — the press the coalition's turns are waiting on — and only ever one,
        // so they share the row evenly: an opinion on the left, the thing that gets on with the
        // game on the right, which is where a thumb already looks for it everywhere else.
        Row(
            modifier = Modifier.fillMaxWidth().height(RowHigh),
            horizontalArrangement = Arrangement.spacedBy(Half),
        ) {
            table.choices.forEach { choice ->
                GameButton(
                    label = labelled(choice.label),
                    tone = choice.tone.paint(),
                    onClick = { onMove(choice.move) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    compact = true,
                )
            }
        }
    }
}

/**
 * The pager: one page per turn, and the last where the plan lands.
 *
 * The page on screen follows the transport's position and the transport follows a swipe, and
 * the two cannot chase each other: a swipe asks for the stop the page names, which parks the
 * transport there, which the pager is already showing.
 */
@Composable
private fun Pages(board: Board, table: Table, onMove: (Move) -> Unit, modifier: Modifier) {
    val transport = board.transport
    // Only as far as the plan reads: a swipe cannot reach a page that is closed, any more than
    // its stop can be touched (`Transport.reach`). The stops row still draws them all, so the
    // turns are visible as turns rather than missing.
    val pages = transport.reach.coerceAtLeast(1)
    val state = rememberPagerState(initialPage = (transport.at - 1).coerceIn(0, pages - 1)) { pages }
    val stage = LocalStage.current
    val latest = rememberUpdatedState(transport)
    val send = rememberUpdatedState(onMove)

    LaunchedEffect(transport.at, pages) {
        val target = (transport.at - 1).coerceIn(0, pages - 1)
        if (state.currentPage != target) state.animateScrollToPage(target)
    }
    // A swipe asks for the stop the settled page names. **Keyed on the pager alone.** Restarted
    // on every new board, this read the page still on screen against the page the board had
    // just been sent to and sent the head straight back — so touching a stop turned the page
    // for a frame and turned it back, and no turn but the one on screen could be reached
    // (product owner). The board it compares against is the latest, read at the moment a page
    // settles, and a page that settles where the board already is asks for nothing.
    LaunchedEffect(state) {
        snapshotFlow { state.settledPage }.collect { page ->
            val now = latest.value
            if (page != now.at - 1) now.stops.getOrNull(page)?.go?.let { send.value(it) }
        }
    }

    HorizontalPager(
        state = state,
        modifier = modifier.markedAs(stage, "plan:pages"),
        userScrollEnabled = !transport.running,
        beyondViewportPageCount = 1,
    ) { page ->
        val sentence = board.pages.getOrNull(page)
        when {
            page == transport.turns && transport.turns > 0 -> LandsPage(board)
            sentence != null -> TurnPage(sentence, table, page + 1 == transport.at, onMove)
            else -> Spacer(Modifier.fillMaxSize())
        }
    }
}

/** One turn: its own row, and the throws under it — or the whole set of ranks while one is asked. */
@Composable
private fun TurnPage(sentence: TurnSentence, table: Table, current: Boolean, onMove: (Move) -> Unit) {
    val stage = LocalStage.current
    val spoken = stringResource(Res.string.board_turn_stop_spoken, sentence.number, speakerName(sentence.who))
    Column(
        modifier = Modifier.fillMaxSize().markedAs(stage, "plan:turn"),
        verticalArrangement = Arrangement.spacedBy(Half),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(RowHigh),
            horizontalArrangement = Arrangement.spacedBy(Half),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.semantics { contentDescription = spoken; heading() }) {
                sentence.nickname?.let { Avatar(name = it, size = FaceSize) }
            }
            Words(sentence.own.slots, onMove, dotted = true)
        }
        if (current && table.ranks.isNotEmpty()) {
            RankGrid(table.ranks, stage, onMove)
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().height(RowHigh),
                horizontalArrangement = Arrangement.spacedBy(Half),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RowLabel(stringResource(Res.string.board_then))
                Words(sentence.throws.flatMap { it.slots }, onMove, dotted = false)
            }
        }
    }
}

/** The words of one row, in a line that scrolls only when a doubled font makes it. */
@Composable
private fun Words(slots: List<Slot>, onMove: (Move) -> Unit, dotted: Boolean) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Half),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        slots.forEachIndexed { index, slot ->
            if (dotted && index > 0) Dot()
            Word(slot, onMove)
        }
    }
}

@Composable
private fun Dot() {
    Text(text = "·", fontSize = WordSize, color = Rail.inkDim)
}

@Composable
private fun RowLabel(text: String) {
    Text(
        text = text.uppercase(),
        fontSize = LabelSize,
        letterSpacing = 1.sp,
        color = Rail.inkDim,
        modifier = Modifier.width(MarkSize + Gap),
        maxLines = 1,
    )
}

/**
 * Where the plan lands (design D10 and D12): every hand as the plan leaves it, and no verdict.
 *
 * The felt behind this page shows the hands; the rows give the caller's total as the coalition
 * believes it, how many of their cards nobody has named, and each coalition hand the same way.
 * The app does **not** say whether that wins.
 */
@Composable
private fun LandsPage(board: Board) {
    val stage = LocalStage.current
    val outcome = board.outcome
    val felt = board.felt
    Column(
        modifier = Modifier.fillMaxSize().markedAs(stage, "plan:arrival"),
        verticalArrangement = Arrangement.spacedBy(Half),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(RowHigh),
            horizontalArrangement = Arrangement.spacedBy(Half),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.board_arrival),
                fontSize = WordSize,
                fontWeight = FontWeight.Bold,
                color = Rail.gold,
                modifier = Modifier.semantics { heading() },
            )
            if (outcome != null) {
                nicknameOf(felt, felt?.vintoCallerId)?.let { Avatar(name = it, size = FaceSize) }
                PlainWord(stringResource(Res.string.board_named, outcome.theirBelieved))
                Dot()
                PlainWord(stringResource(Res.string.board_unnamed, outcome.unseen))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().height(RowHigh).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Half),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RowLabel(stringResource(Res.string.board_ours))
            outcome?.hands?.forEachIndexed { index, hand ->
                if (index > 0) Dot()
                HandTotal(hand, nicknameOf(felt, hand.seat))
            }
        }
    }
}

private fun nicknameOf(view: PlayerView?, seat: String?): String? =
    view?.players?.firstOrNull { it.id == seat }?.nickname

@Composable
private fun HandTotal(hand: HandReading, nickname: String?) {
    val unknown = if (hand.unnamed > 0) {
        " " + stringResource(Res.string.board_hand_unknown, hand.unnamed)
    } else {
        ""
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Half), verticalAlignment = Alignment.CenterVertically) {
        nickname?.let { Avatar(name = it, size = FaceSize) }
        PlainWord(stringResource(Res.string.board_named, hand.named) + unknown)
    }
}

@Composable
private fun PlainWord(text: String) {
    Text(text = text, fontSize = WordSize, color = Rail.ink, maxLines = 1)
}

/**
 * The answers row: the alternatives to the word being asked for, the seats an Ace may make
 * draw, the turn's news where it has any, or one line saying where on the felt to touch — and
 * nothing, drawn at the same height, when there is none of those.
 *
 * The news shares this row rather than hanging under the sentence, because on a phone the
 * page is exactly its two rows deep and a third line under them was drawn off the bottom of
 * the pager: said in the semantics and seen by nobody.
 */
@Composable
private fun Answers(board: Board, table: Table, onMove: (Move) -> Unit) {
    val stage = LocalStage.current
    val seats = table.seats.filter { !it.planTurn }
    val sentence = board.sentence
    val chips = board.answers.isNotEmpty() || seats.isNotEmpty() || sentence?.hasNews == true

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(RowHigh)
            // Chips are a strip and scroll by hand; the hint is a sentence and must not, or it
            // is bounded by nothing and cannot know it does not fit. See the hint below.
            .then(if (chips) Modifier.horizontalScroll(rememberScrollState()) else Modifier)
            .markedAs(stage, "plan:answers"),
        horizontalArrangement = Arrangement.spacedBy(Half),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            board.answers.isNotEmpty() -> board.answers.forEach { AnswerChip(it, onMove) }
            seats.isNotEmpty() -> seats.forEach { SeatChip(it, onMove) }
            sentence != null && sentence.hasNews -> TurnNews(sentence, onMove)
            else -> table.detail?.let { hint ->
                Text(
                    text = detailed(hint),
                    fontSize = WordSize,
                    fontStyle = FontStyle.Italic,
                    color = Rail.inkDim,
                    maxLines = 1,
                    // Reported from a phone with the picture to prove it: the line ended mid-word
                    // at the right rim — "touch the pla" — and nothing said there was more. It was
                    // one line inside a strip that scrolls by hand, so it simply ran off, and the
                    // page is exactly two rows deep so it cannot wrap onto a third.
                    //
                    // "I think we should make in plan mode all of this auto scrollable slowly",
                    // which is what this is. It costs nothing when the line already fits: a
                    // marquee that is not overflowing does not animate.
                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE, velocity = HintCreep),
                )
            }
        }
    }
}

/** Whether the turn has something to say about itself beyond its words. */
private val TurnSentence.hasNews: Boolean
    get() = health == StepHealth.BROKEN || !playable || suggestion != null

/** One alternative, outlined in the coalition's blue: touch it and it takes the asked word's place. */
@Composable
private fun AnswerChip(choice: Choice, onMove: (Move) -> Unit) {
    val stage = LocalStage.current
    ChipBox(
        modifier = Modifier
            .markedAs(stage, "plan:answer:${choice.label}")
            .clickable(role = Role.Button) { onMove(choice.move) },
        fill = Color.Transparent,
        edge = Rail.asked,
    ) {
        Text(text = labelled(choice.label), fontSize = WordSize, color = Rail.ink, maxLines = 1)
    }
}

/** A seat as an answer — who an Ace makes draw — wearing its face. */
@Composable
private fun SeatChip(seat: SeatChoice, onMove: (Move) -> Unit) {
    val stage = LocalStage.current
    ChipBox(
        modifier = Modifier
            .markedAs(stage, "plan:seat:${seat.id}")
            .clickable(role = Role.Button) { onMove(seat.move) },
        fill = Color.Transparent,
        edge = Rail.asked,
    ) {
        Box(modifier = Modifier.clearAndSetSemantics { }) { Avatar(name = seat.nickname, size = FaceSize) }
        Text(text = speakerName(seat.who), fontSize = WordSize, color = Rail.ink, maxLines = 1)
    }
}

/**
 * The strip under the page: one stop per turn, "lands", and the two buttons that play the film.
 *
 * The stops are the pager's indicator and its other control: the lit one is the page on screen
 * and names it, touching another jumps there. ▶ watches this turn again from the table it
 * starts on; ▶▶ watches every turn from here to where the plan lands, and reads ■ while it runs.
 */
@Composable
internal fun PlanStops(transport: Transport, onMove: (Move) -> Unit) {
    val stage = LocalStage.current
    Row(
        modifier = Modifier.fillMaxWidth().height(RowHigh).markedAs(stage, "plan:transport"),
        horizontalArrangement = Arrangement.spacedBy(Half),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Joined by arrows rather than each carrying the word "Turn": the row reads as the order
        // the seats play in, which is what the word was for, and the width it gives back is what
        // the two buttons need.
        //
        // **And it still scrolls**, because that width is not enough on its own. Only the lit
        // stop spells its seat's name, and online a name is minted rather than typed — the
        // longest is "Patient Harbour", fifteen characters against a fixture's four. A row out
        // of width takes it out of its last child, so ▶ measured 24dp under that name with the
        // stops sharing the row. The stops take what the buttons leave and scroll inside it; the
        // buttons are thumbs and never give any of it back (`TouchTargetTest`).
        Row(
            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Half),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            transport.stops.forEachIndexed { index, stop ->
                if (index > 0) ThenArrow()
                StopChip(stop, transport, onMove)
            }
        }
        val here = transport.stops.firstOrNull { it.here }
        val replay = stringResource(Res.string.label_plan_replay)
        TransportButton(replay, here?.replay, "plan:replay", onMove) { ink -> drawPlay(ink) }
        val halt = transport.halt
        if (halt != null) {
            val stop = stringResource(Res.string.label_plan_halt)
            TransportButton(stop, halt, "plan:halt", onMove, lit = true) { ink -> drawHalt(ink) }
        } else {
            val all = stringResource(Res.string.label_plan_play_all)
            TransportButton(all, transport.playAll, "plan:play-all", onMove) { ink -> drawPlayAll(ink) }
        }
    }
}

/**
 * One stop: the turn's number and the seat's face, and the seat's name on the one lit — or
 * "lands" for the last page. A tab, because that is what a row of pages with one of them
 * current is, and the current one says so to a screen reader.
 */
@Composable
private fun StopChip(stop: Stop, transport: Transport, onMove: (Move) -> Unit) {
    val stage = LocalStage.current
    val words = stopWords(stop, transport)
    val go = stop.go
    // A closed page is drawn dim rather than dropped: the turn is still somebody's turn, and a
    // row that lost it would say the plan is shorter than it is (`Transport.reach`).
    val ink = when {
        stop.here -> Rail.fill
        stop.locked -> Rail.inkDim
        else -> Rail.ink
    }
    Row(
        modifier = Modifier
            .sizeIn(minWidth = TapTarget, minHeight = TapTarget)
            .markedAs(stage, "plan:stop:${stop.at}")
            .then(if (go == null) Modifier else Modifier.clickable { onMove(go) })
            .chipGround(if (stop.here) Rail.gold else Rail.chip)
            .padding(horizontal = Gap)
            .semantics(mergeDescendants = true) {
                contentDescription = words.spoken
                selected = stop.here
                if (stop.locked) disabled()
                role = Role.Tab
            },
        horizontalArrangement = Arrangement.spacedBy(Half),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (stop.seat == null) {
            Canvas(modifier = Modifier.size(MarkSize)) { drawFinish(ink) }
        } else {
            TurnMark(words.label, stop.here)
        }
        stop.nickname?.let { nickname ->
            Box(modifier = Modifier.clearAndSetSemantics { }) { Avatar(name = nickname, size = FaceSize) }
        }
        words.name?.let { name ->
            Text(text = name, fontSize = WordSize, fontWeight = FontWeight.Bold, color = ink, maxLines = 1)
        }
    }
}

/** A stop's words: what it shows, the name it shows beside its face while lit, and what it says whole. */
private class StopWords(val label: String, val name: String?, val spoken: String)

/**
 * A turn's numeral, drawn as the mark a rose card wears for the turn it arrives on
 * (`RoseBack`) — one circle, one digit — so a stop and the cards that name it read as the same
 * thing. Not cleared from the semantics, unlike the arrow: the chip merges it and answers with
 * its own "Turn 2, Dune", so the digit costs a screen reader nothing and stays something a test
 * can see drawn.
 *
 * **It introduces no colour pair.** The mark is the chip it sits on, inverted — gold ground
 * and panel ink while lit, reading ink and chip ground while not — and contrast is a ratio
 * between two luminances, so an inversion clears exactly what the chip's own label clears.
 * That is why `ContrastTest` needs no new entry for it.
 */
@Composable
private fun TurnMark(numeral: String, here: Boolean) {
    Box(
        modifier = Modifier
            .size(MiniTag)
            .background(if (here) Rail.fill else Rail.ink, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = numeral,
            fontSize = MiniBadge,
            fontWeight = FontWeight.Bold,
            color = if (here) Rail.gold else Rail.chip,
        )
    }
}

/**
 * Between two stops: play passes on. The same mark the film draws over the felt between turns
 * (design D19), so the row reads as the order the seats play in rather than as four buttons.
 * Decorative — the order is already in the tabs.
 */
@Composable
private fun ThenArrow() {
    // Read outside the draw lambda: a theme colour is a composable read, and a draw scope is
    // not a composable one — the same reason `TradeArrow` hands its ink to `drawTrade`.
    val ink = Rail.inkDim
    Canvas(modifier = Modifier.size(MiniTag).clearAndSetSemantics { }) {
        val w = size.width
        val mid = size.height / 2
        val head = w * THEN_HEAD
        val tip = w * THEN_TIP
        drawLine(ink, Offset(w * THEN_TAIL, mid), Offset(tip, mid), strokeWidth = Hair.toPx())
        drawLine(ink, Offset(w * THEN_BARB, mid - head), Offset(tip, mid), strokeWidth = Hair.toPx())
        drawLine(ink, Offset(w * THEN_BARB, mid + head), Offset(tip, mid), strokeWidth = Hair.toPx())
    }
}

@Composable
private fun stopWords(stop: Stop, transport: Transport): StopWords {
    val seat = stop.seat
    if (seat == null && stop.at == transport.stops.size) {
        // Nothing to draw: the chip wears the finish mark. "Lands" was this repository's own
        // metaphor rather than a word a player reaches for, and a screen reader was getting the
        // same unclear word the screen was — so what it says now is what the page actually is.
        return StopWords(label = "", name = null, spoken = stringResource(Res.string.board_arrival))
    }
    val who = seat?.let { speakerName(it) }.orEmpty()
    return StopWords(
        // The numeral, not the word: what a stop is for is said by the face beside it and the
        // arrow after it, and the word was costing the row the width its two buttons needed.
        // The screen reader still hears the whole of it.
        label = stop.at.toString(),
        name = who.takeIf { stop.here && seat != null },
        spoken = stringResource(Res.string.board_turn_stop_spoken, stop.at, who),
    )
}

/** A chip's ground, drawn inset so a row of chips reads as a line of words rather than a row of buttons. */
private fun Modifier.chipGround(ground: Color): Modifier = drawBehind {
    val inset = ChipInset.toPx()
    drawRoundRect(
        color = ground,
        topLeft = Offset(0f, inset),
        size = Size(size.width, size.height - inset * 2),
        cornerRadius = CornerRadius(ChipCorner.toPx()),
    )
}

/** One of the two film buttons, and the halt that replaces the second while it runs. */
@Composable
private fun TransportButton(
    description: String,
    move: Move.Quiet?,
    mark: String,
    onMove: (Move) -> Unit,
    lit: Boolean = false,
    glyph: DrawScope.(Color) -> Unit,
) {
    val stage = LocalStage.current
    val ink = if (lit) Rail.fill else Rail.ink
    val fill = if (lit) Rail.gold else Rail.chip
    val ground = if (move == null) fill.copy(alpha = DISABLED) else fill
    val pen = if (move == null) ink.copy(alpha = DISABLED) else ink
    Box(
        modifier = Modifier
            .sizeIn(minWidth = TapTarget, minHeight = TapTarget)
            .markedAs(stage, mark)
            .then(if (move == null) Modifier else Modifier.clickable { onMove(move) })
            .chipGround(ground)
            .semantics { contentDescription = description; role = Role.Button },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(MarkSize)) { glyph(pen) }
    }
}

private const val DISABLED = 0.45f

/**
 * The finish: a chequered flag on a staff, for the page the plan arrives at.
 *
 * Not a media mark, deliberately — ▶ and ▶▶ sit inches away and a third triangle among the
 * stops would read as a third button rather than as a place. A flag reads as somewhere the film
 * gets to. It says nothing about who won, which the app does not say anywhere (design D12).
 */
private fun DrawScope.drawFinish(ink: Color) {
    val w = size.minDimension
    val staff = w * FLAG_STAFF
    drawLine(
        ink,
        Offset(staff, w * FLAG_TOP),
        Offset(staff, w * FLAG_FOOT),
        strokeWidth = w * FLAG_STROKE,
    )
    // Two rows of two, the diagonal pair filled: the fewest squares that still read as chequered.
    val cell = w * FLAG_CELL
    for (row in 0..1) {
        for (column in 0..1) {
            if ((row + column) % 2 != 0) continue
            drawRect(
                ink,
                topLeft = Offset(staff + column * cell, w * FLAG_TOP + row * cell),
                size = Size(cell, cell),
            )
        }
    }
    drawRect(
        ink,
        topLeft = Offset(staff, w * FLAG_TOP),
        size = Size(cell * 2, cell * 2),
        style = Stroke(w * FLAG_STROKE),
    )
}

/** A play mark: a triangle pointing the way the film runs. */
private fun DrawScope.drawPlay(ink: Color) {
    val w = size.minDimension
    val path = Path().apply {
        moveTo(w * PLAY_LEFT, w * PLAY_TOP)
        lineTo(w * PLAY_RIGHT, w * PLAY_MID)
        lineTo(w * PLAY_LEFT, w * PLAY_BOTTOM)
        close()
    }
    drawPath(path, ink)
}

/** Two of them, close together: every turn. */
private fun DrawScope.drawPlayAll(ink: Color) {
    val w = size.minDimension
    listOf(0f, w * PLAY_ALL_STEP).forEach { dx ->
        val path = Path().apply {
            moveTo(w * PLAY_ALL_LEFT + dx, w * PLAY_TOP)
            lineTo(w * PLAY_ALL_RIGHT + dx, w * PLAY_MID)
            lineTo(w * PLAY_ALL_LEFT + dx, w * PLAY_BOTTOM)
            close()
        }
        drawPath(path, ink)
    }
}

/** A square: stop. */
private fun DrawScope.drawHalt(ink: Color) {
    val w = size.minDimension
    drawRect(ink, topLeft = Offset(w * STOP_FROM, w * STOP_FROM), size = Size(w * STOP_SIDE, w * STOP_SIDE))
}

private const val PLAY_LEFT = 0.3f
private const val PLAY_RIGHT = 0.78f
private const val PLAY_TOP = 0.22f
private const val PLAY_MID = 0.5f
private const val PLAY_BOTTOM = 0.78f
private const val PLAY_ALL_LEFT = 0.12f
private const val PLAY_ALL_RIGHT = 0.5f
private const val PLAY_ALL_STEP = 0.4f
private const val STOP_FROM = 0.26f
private const val STOP_SIDE = 0.48f

// ---------------------------------------------------------------------------- the words

/**
 * One word of the sentence.
 *
 * **Boxed means touchable.** A decision is a filled box; the word being asked for wears the
 * coalition's blue; the next decision is a dashed box; a fact, and the whole of the caller's
 * view, is plain dim text with nothing to touch. Every box is a thumb's size to hit whatever
 * its word measures, and every word says its whole sentence to a screen reader.
 */
@Composable
private fun Word(slot: Slot, onMove: (Move) -> Unit) {
    val stage = LocalStage.current
    val open = slot.open
    val askedNow = stringResource(Res.string.says_asked)
    val plain = open == null && !slot.asked && !slot.offer
    val ink = when {
        slot.asked -> Rail.onAsked
        plain -> Rail.inkDim
        else -> Rail.ink
    }
    val fill = when {
        slot.asked -> Rail.asked
        slot.offer || plain -> Color.Transparent
        else -> Rail.chip
    }
    val spoken = saysWords(slot.says)

    ChipBox(
        modifier = Modifier
            .markedAs(stage, "plan:word:${slot.says::class.simpleName.orEmpty()}")
            .then(if (open == null) Modifier else Modifier.clickable(role = Role.Button) { onMove(open) })
            .semantics(mergeDescendants = true) {
                contentDescription = spoken
                if (slot.asked) stateDescription = askedNow
            },
        fill = fill,
        // The box's own ground is a shade off the rail's; what says "touchable" at 3:1 is the
        // outline, the same one every other control on the rail wears (`Rail.edge`).
        edge = if (open != null && !slot.asked) Rail.edge else null,
        dashed = if (slot.offer) Rail.edge else null,
        padded = !plain,
    ) {
        WordContent(slot, ink, plain, onMove)
    }
}

/**
 * A word's box: a thumb's height to hit, a word's height to see. The ground is drawn inset,
 * so the row of them reads as a line of words rather than as a row of buttons.
 */
@Composable
private fun ChipBox(
    modifier: Modifier,
    fill: Color,
    edge: Color?,
    dashed: Color? = null,
    padded: Boolean = true,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .sizeIn(minWidth = if (padded) TapTarget else 0.dp, minHeight = TapTarget)
            .drawBehind {
                val inset = ChipInset.toPx()
                val corner = CornerRadius(ChipCorner.toPx())
                val box = Size(size.width, size.height - inset * 2)
                if (fill != Color.Transparent) drawRoundRect(fill, Offset(0f, inset), box, corner)
                if (edge != null) {
                    drawRoundRect(edge, Offset(0f, inset), box, corner, style = Stroke(Hair.toPx()))
                }
                if (dashed != null) {
                    drawRoundRect(
                        dashed,
                        Offset(0f, inset),
                        box,
                        corner,
                        style = Stroke(
                            Hair.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(DashOn, DashOff)),
                        ),
                    )
                }
            }
            .padding(horizontal = if (padded) Gap else Hair),
        horizontalArrangement = Arrangement.spacedBy(Half, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        content = { content() },
    )
}

/** The word itself: its text, and the cards it names drawn as cards. */
@Composable
private fun WordContent(slot: Slot, ink: Color, plain: Boolean, onMove: (Move) -> Unit) {
    when (val says = slot.says) {
        is Says.Takes -> {
            WordText(chipWords(says), ink, plain)
            says.rank?.let { FaceUp(it) }
        }

        is Says.Drew -> {
            WordText(chipWords(says), ink, plain)
            FaceUp(says.rank)
        }

        is Says.PutsDown -> {
            WordText(chipWords(says), ink, plain)
            MiniCard(says.card)
        }

        is Says.Trade -> {
            MiniCard(says.from)
            TradeArrow(says, slot.toggle, onMove)
            MiniCard(says.to)
        }

        is Says.Looks -> {
            WordText(chipWords(says), ink, plain)
            MiniCard(says.card)
        }

        is Says.Points -> {
            MiniCard(says.card)
            WordText(pointsWords(says.rank), ink, plain)
        }

        is Says.Throws -> {
            ThrowWords(says, ink, plain)
        }

        Says.Draws, Says.WellSee, Says.AndThen, Says.WhatWith, Says.PlaysIt, Says.WhichCard, Says.LetsItGo,
        is Says.CallIt, is Says.Called, Says.WhichTwo, Says.WhichToLookAt, Says.WhichToPointAt,
        is Says.Names, is Says.Forces,
        Says.WhoDraws, Says.WhichToThrow, Says.AddThrow,
        -> {
            WordText(chipWords(says), ink, plain)
        }
    }
}

@Composable
private fun pointsWords(rank: Rank?): String =
    if (rank == null) {
        stringResource(
            Res.string.says_is_a_what,
        )
    } else {
        stringResource(Res.string.says_is_a, rank.serialName)
    }

/** "[face] throws in [card]", and "blind" after it where the table cannot vouch for the match. */
@Composable
private fun ThrowWords(says: Says.Throws, ink: Color, plain: Boolean) {
    says.card?.nickname?.let {
        Box(
            modifier = Modifier.clearAndSetSemantics { },
        ) { Avatar(name = it, size = MiniFace) }
    }
    WordText(stringResource(Res.string.says_throws_word), ink, plain)
    val card = says.card
    val rank = says.rank
    when {
        card != null -> MiniCard(card)
        rank != null -> FaceUp(rank)
    }
    if (says.blind) WordText(stringResource(Res.string.says_blind), ink, plain = true)
}

@Composable
private fun WordText(text: String, ink: Color, plain: Boolean) {
    Text(
        text = text,
        fontSize = WordSize,
        fontStyle = if (plain) FontStyle.Italic else FontStyle.Normal,
        color = ink,
        maxLines = 1,
    )
}

/** A card face up in the sentence: the pile's, or the one drawn. The same art as on the felt. */
@Composable
private fun FaceUp(rank: Rank) {
    Box(modifier = Modifier.clearAndSetSemantics { }) { CardPicture(rank, width = MiniWidth) }
}

/**
 * A card in the sentence, drawn as it lies on the felt: its back, its owner's face on one
 * corner, the rank the table says it is on the other, its place at the foot — and rose, with
 * the turn it arrives on where the face would be, for a card the plan draws or deals.
 */
@Composable
private fun MiniCard(card: CardWord) {
    val fresh = card.fresh
    val spoken = fresh?.let { stringResource(Res.string.card_arrives, it) }
    Box(
        modifier = Modifier
            .size(MiniWidth, MiniHeight)
            .then(
                if (spoken ==
                    null
                ) {
                    Modifier.clearAndSetSemantics { }
                } else {
                    Modifier.semantics { contentDescription = spoken }
                },
            ),
    ) {
        if (fresh != null) {
            RoseBack(modifier = Modifier.fillMaxSize(), tag = fresh)
        } else {
            Image(
                painter = painterResource(Res.drawable.card_back),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(MiniCorner)),
            )
            card.nickname?.let { nickname ->
                Box(modifier = Modifier.align(Alignment.TopStart).offset(x = -Half, y = -Half)) {
                    Avatar(name = nickname, size = MiniFace)
                }
            }
        }
        card.rank?.let { rank ->
            RankBadge(rank, Modifier.align(Alignment.TopEnd).offset(x = Half, y = -Half))
        }
        Text(
            text = card.slot.toString(),
            fontSize = MiniBadge,
            color = if (fresh != null) Signal.roseInk else SlotInk,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 2.dp),
        )
    }
}

/** The rank the table says a card is, on its corner — the same plaque a claim wears on the felt. */
@Composable
private fun RankBadge(rank: Rank, modifier: Modifier) {
    Box(
        modifier = modifier
            .background(BadgeFill, RoundedCornerShape(MiniTag))
            .border(1.dp, BadgeEdge, RoundedCornerShape(MiniTag))
            .padding(horizontal = 3.dp),
    ) {
        Text(
            text = rank.serialName,
            fontSize = MiniBadge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
        )
    }
}

private val BadgeFill = Color(0xFF2B2D3A)
private val BadgeEdge = Color(0xFF4A4D5E)
private val SlotInk = Color(0xFFDAD5C4)

/**
 * The rose ground of a card that is not on the table yet, with the turn it arrives on in the
 * corner and a question mark where nobody knows what it is. The same rose the felt draws, so
 * the eye pairs the word with the card.
 */
@Composable
private fun RoseBack(modifier: Modifier, tag: Int) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(MiniCorner))
            .background(Signal.rose)
            .border(1.dp, Signal.roseEdge, RoundedCornerShape(MiniCorner)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "?", fontSize = WordSize, fontWeight = FontWeight.Bold, color = Signal.roseInk)
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = -Half, y = -Half)
                .size(MiniTag)
                .background(Signal.roseInk, CircleShape)
                .border(1.dp, Signal.rose, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = tag.toString(),
                fontSize = MiniBadge,
                fontWeight = FontWeight.Bold,
                color = Signal.rose,
            )
        }
    }
}

/**
 * The arrow between the two cards of a trade: lit, the Queen swaps them; dim, she only looks.
 * Touching it flips the two — one control, because at a table that is one word: "…and swap
 * them if mine is lower".
 */
@Composable
private fun TradeArrow(says: Says.Trade, toggle: Move.Quiet?, onMove: (Move) -> Unit) {
    val stage = LocalStage.current
    val swap = says.swap
    val ink = if (swap) Rail.onAsked else Rail.inkDim
    val ground = if (swap) Rail.asked else Color.Transparent
    val rim = if (swap) Color.Transparent else Rail.edge
    val state = when {
        toggle == null -> null
        swap -> stringResource(Res.string.says_asked)
        else -> ""
    }
    // A switch is announced by its name and its state, and this one had only a state — the word
    // it sits in is merged into the chip around it, so a screen reader landing on the switch
    // itself heard "on" and nothing about what was on. It says the clause it controls, which is
    // the sentence the eye reads off the same two cards and the arrow between them.
    val named = saysWords(says).takeIf { toggle != null }
    // A thumb's box to hit, a mark's box to see: the arrow is the smallest control on the rail
    // and is sized like every other one this app offers a finger (`TouchTargetTest`).
    Box(
        modifier = Modifier
            .sizeIn(minWidth = TapTarget, minHeight = TapTarget)
            .then(if (toggle == null) Modifier else Modifier.clickable(role = Role.Switch) { onMove(toggle) })
            .markedAs(stage, "plan:arrow")
            .semantics {
                named?.let { contentDescription = it }
                state?.let { stateDescription = it }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(MarkSize)
                .clip(RoundedCornerShape(Half))
                .background(ground)
                .border(1.dp, rim, RoundedCornerShape(Half)),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(MarkSize)) { drawTrade(ink) }
        }
    }
}

/**
 * **News, not caption.**
 *
 * A claim a reveal has proved wrong, or a card a step names that has since gone, is a thing
 * that happened and neither repairs itself (design D13) — so it is said, in the row under the
 * sentence, and only when there is something to say. Almost always there is not.
 */
@Composable
private fun TurnNews(sentence: TurnSentence, onMove: (Move) -> Unit) {
    val broken = sentence.health == StepHealth.BROKEN
    if (broken) {
        Text(
            text = "✗ " + stringResource(Res.string.board_broken),
            fontSize = WordSize,
            color = WarnInk,
            maxLines = 1,
        )
    }
    if (!sentence.playable) {
        Text(
            text = stringResource(Res.string.board_unplayable),
            fontSize = WordSize,
            color = if (broken) WarnInk else Rail.inkDim,
            maxLines = 1,
        )
    }
    sentence.suggestion?.let { WouldRather(sentence.who, it, sentence.useSuggestion, onMove) }
}

/**
 * "Nina would rather …": the turn owner's own alternative to the step somebody set for them.
 * Beside the step and not over it, and only on the turn being read (design D8).
 */
@Composable
private fun WouldRather(
    who: Speaker,
    suggestion: StepLine,
    use: Move?,
    onMove: (Move) -> Unit,
) {
    val stage = LocalStage.current
    val words = stringResource(Res.string.board_would_rather, speakerName(who), stepWords(suggestion))
    Box(
        modifier = Modifier
            .sizeIn(minWidth = TapTarget, minHeight = TapTarget)
            .then(if (use == null) Modifier else Modifier.clickable { onMove(use) })
            .markedAs(stage, "plan:suggest"),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text = words, fontSize = WordSize, color = Rail.ink, maxLines = 1)
    }
}

// ---------------------------------------------------------------------------- the live rail

/**
 * The plan's line on the live rail: what the plan says about the turn on play, in the plan's
 * own words, one line, and nothing to touch.
 *
 * A line of text and not the sentence's row of chips, because the rail under a phone's felt
 * is a letterbox with a log in it: a row of a thumb's height under the prompt took the log's
 * last line on exactly the turns the log narrates a scramble. Nothing at all when the plan
 * has nothing to say about this turn — the line comes once, when a plan is made, and after
 * that changes in place.
 */
@Composable
internal fun PlanLineText(line: PlanLine) {
    if (line.says.isEmpty()) return
    val stage = LocalStage.current
    val words = line.says.map { saysWords(it) }
    Text(
        text = words.joinToString(WordDot),
        fontSize = WordSize,
        color = Rail.note,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().markedAs(stage, "plan:line"),
    )
}

/** Between two words of a turn said in one line — the same mark the rail's rows draw between chips. */
private const val WordDot = " · "

/**
 * Runs the film, and parks the head on the page it was going to when the felt has finished
 * drawing.
 *
 * The transport's "running" is a *request*: the screen asks for the plan to be played, the
 * stage draws it at whatever pace the player has chosen, and only the stage knows when that is
 * over. So this waits for the felt to start and then for the last turn's cards to land, and
 * parks the head on the page the film was going to — with the turn's result on the felt
 * (design D14). Parked as the cards land rather than when the stage goes quiet: the read beat
 * after them is the felt's, and a head still travelling through it left the felt to fall back
 * on the page's start table for a frame when it ended.
 *
 * The first wait is bounded. A plan whose turns have nothing to draw — every lane undecided —
 * would otherwise sit "running" for ever waiting on an animation that was never coming.
 */
@Composable
internal fun PlanRunner(table: Table, onMove: (Move) -> Unit) {
    val stage = LocalStage.current
    val board = table.board
    val target = board?.transport?.travellingTo
    // The last turn the film draws on the way: a turn nobody has decided has a page and no film.
    val last = if (board == null || target == null) {
        null
    } else {
        (1..minOf(target, board.transport.turns)).lastOrNull { board.watchable.getOrElse(it - 1) { false } }
    }

    LaunchedEffect(target) {
        if (target == null) return@LaunchedEffect
        withTimeoutOrNull(FILM_STARTS_MS) { snapshotFlow { stage.drawing }.first { it } }
        snapshotFlow {
            val landed = last != null && stage.ghostPlayed == last
            !stage.drawing || landed
        }.first { it }
        onMove(Move.Ask(Question.ThePlan(at = target, landed = true)))
    }
}

/** How long the felt is given to start drawing the film before it is taken as having none. */
private const val FILM_STARTS_MS = 1_500L

// ---------------------------------------------------------------------------- carrying a card

/**
 * Carrying a card to where the plan should put it (design D5).
 *
 * **A drag begins on movement, not after a hold** (design D5a): in plan mode there is nothing
 * to scroll, nothing to tap-to-play and nothing to swipe on the felt. A release only lands on
 * a place the composer has already lit; anything else puts the card back and changes nothing.
 */
@Composable
internal fun Modifier.carriable(
    ref: CardRef,
    composer: PlanComposer?,
    onMove: (Move) -> Unit,
): Modifier {
    val stage = LocalStage.current
    val drops = composer?.drops?.get(ref).orEmpty()
    if (drops.isEmpty()) return this

    return pointerInput(ref, drops.keys) {
        detectDragGestures(
            onDragStart = {
                stage.carrying = ref
                stage.carriedTo = stage.berthOf(Anchor.Seat(ref.playerId, ref.position))
                    ?.centre
                    ?: Offset.Zero
                stage.carriedOver = null
            },
            onDrag = { change, delta ->
                change.consume()
                stage.carriedTo += delta
                stage.carriedOver = stage.placeAt(stage.carriedTo, drops.keys)
            },
            onDragEnd = {
                val landed = stage.carriedOver
                stage.carrying = null
                stage.carriedOver = null
                // Only a place that was lit. A release over the felt, over the caller's hand,
                // or over the card's own slot changes nothing at all.
                drops[landed]?.let(onMove)
            },
            onDragCancel = {
                stage.carrying = null
                stage.carriedOver = null
            },
        )
    }
}

/** The card in hand right now: one being carried, or one picked up by touching it. */
@Composable
private fun inHand(board: Board?): CardRef? = LocalStage.current.carrying ?: board?.picked

/**
 * Whether this card is a place the plan would accept the card in hand — or, with nothing in
 * hand, one the table can vouch for as a throw (`Board.wanted`).
 */
@Composable
internal fun wantedNow(ref: CardRef, board: Board?, composer: PlanComposer?): Boolean {
    val held = inHand(board) ?: return ref in board?.wanted.orEmpty()
    if (held == ref) return false
    return composer?.drops?.get(held)?.containsKey(PlanTarget.Card(ref)) == true
}

/** Whether the discard pile is a place the card in hand could be put down. */
@Composable
internal fun discardWanted(board: Board?, composer: PlanComposer?): Boolean {
    val held = inHand(board) ?: return false
    return composer?.drops?.get(held)?.containsKey(PlanTarget.Discard) == true
}
