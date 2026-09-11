package game.vinto.app.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import game.vinto.app.art.Res
import game.vinto.app.art.board_arrival_ours
import game.vinto.app.art.board_arrival_theirs
import game.vinto.app.art.board_arrival_title
import game.vinto.app.art.board_arrival_unseen
import game.vinto.app.art.board_broken
import game.vinto.app.art.board_from_the_deck
import game.vinto.app.art.board_nobody_throws_in
import game.vinto.app.art.board_now
import game.vinto.app.art.board_part_card
import game.vinto.app.art.board_part_card_called
import game.vinto.app.art.board_part_end
import game.vinto.app.art.board_shed
import game.vinto.app.art.board_step_take_discard
import game.vinto.app.art.board_turn_planned
import game.vinto.app.art.board_unplayable
import game.vinto.app.art.board_would_rather
import game.vinto.app.art.board_your_call
import game.vinto.app.art.label_keep_the_card
import game.vinto.app.art.label_let_the_card_go
import game.vinto.app.art.label_plan_halt
import game.vinto.app.art.label_play_the_card
import game.vinto.app.labelled
import game.vinto.app.speakerName
import game.vinto.app.stepWords
import game.vinto.app.theme.ButtonTone
import game.vinto.app.theme.GameButton
import game.vinto.app.theme.Rail
import game.vinto.client.Anchor
import game.vinto.client.Board
import game.vinto.client.CardRef
import game.vinto.client.Choice
import game.vinto.client.Label
import game.vinto.client.LaneLine
import game.vinto.client.Move
import game.vinto.client.PlanComposer
import game.vinto.client.PlanOutcome
import game.vinto.client.PlanTarget
import game.vinto.client.PlayKind
import game.vinto.client.Question
import game.vinto.client.ShedLine
import game.vinto.client.Speaker
import game.vinto.client.StepHealth
import game.vinto.client.StepLine
import game.vinto.client.Stop
import game.vinto.client.Table
import game.vinto.client.Transport
import game.vinto.client.TurnParts
import game.vinto.shapes.Opening
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.stringResource

/**
 * The coalition's plan, on the table (design D1).
 *
 * The plan used to be rows in the rail, which is where it went wrong: on a phone the rail is a
 * letterbox, and the plan is the one thing in this round that has to be read *whole*. Its
 * content is **where cards go**, so it belongs on the felt, between the seats that hold them —
 * and everything here is the furniture round that picture: the transport that moves through it,
 * the numbered turns, and the state it arrives at.
 *
 * Nothing here can act on the round. Every control is a [Move.Quiet] and the router drops
 * anything else while the plan is open, so this file cannot dispatch a `GameAction` even by
 * accident (design D2).
 */

private val Gap = 8.dp
private val Half = 4.dp
private val Hair = 2.dp
private val DetailSize = 13.sp
private val WarnInk = Color(0xFFFFA39E)

/**
 * The plan's rail, whole: how a turn is changed, the transport, the turns, and where it lands.
 *
 * **Nothing here scrolls, and that is the point.** The plan first went into the ordinary rail's
 * block, which does scroll — and the transport and the numbered turns went straight below the
 * fold, so the plan was read through a letterbox exactly as it had been before this change.
 * The plan is the one thing in this round that has to be read *entire*, so it takes the rail
 * over: no log strip, no card column, no prompt repeating what the selected turn already says.
 *
 * The turns take whatever height is left, because they are the content; everything else is a
 * fixed row above or below them.
 */
@Composable
internal fun PlanRail(
    board: Board,
    table: Table,
    onMove: (Move) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Hair),
    ) {
        // **No hint line.** It read "carry a card to where it should go — or touch it, then
        // touch the place", which is help text in a game rather than in a lesson, and it took
        // two of the rail's lines on a phone to say something the felt already shows: the
        // composer lights the cards that can be picked up and the places each may go, before
        // anything is dragged. A control that explains itself needs no caption, and one that
        // needs a caption is the thing to fix. The `?` sheet is where words belong.

        // **No verbs for the turn.** DECLARE A RANK, CLEAR and THROW IN acted on the turn from
        // a strip below it, which put the turn in two places and said its shape in neither. The
        // row above is the turn *and* the way to change it, so what is left down here is the
        // one thing that is not about a turn at all.
        val perMember = table.choices.filterNot { it.label in TURN_EDITS }

        // Natural height, and never more than is left. `fill = false` is the whole of it: at an
        // ordinary font the turns take exactly the room they need and nothing scrolls, which is
        // the requirement; at a doubled system font they are capped and scroll inside their own
        // box rather than pushing the buttons off the bottom of the screen. The turns are the
        // one thing here that may give, because the transport and the buttons are single rows
        // that cannot be read at all if they are half over the edge.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Hair)) {
                // At the last stop the arrival comes *first*. It is what a member stepped to
                // the end to read, and behind three turns it was the one thing below the fold
                // on the one screen it is the point of.
                if (board.transport.arrived) board.outcome?.let { PlanArrival(it) }

                ComposingTurn(board, onMove)

                // The sheds every *other* seat has promised. This seat's own ride on its turn,
                // in the row above, because that is the turn they pay off. The plates carry the
                // fact that a seat has one ready (design D7); what a plate cannot carry is which
                // rank, and that is the half a teammate plans around.
                board.sheds.filterNot { it.who == board.turn?.who }.forEach { shed ->
                    val words =
                        stringResource(Res.string.board_shed, speakerName(shed.who), shed.rank.serialName)
                    val move = shed.move
                    Text(
                        text = words,
                        fontSize = DetailSize,
                        color = Rail.ink,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (move == null) Modifier else Modifier.clickable { onMove(move) }),
                    )
                }
            }
        }

        PlanChoices(perMember, onMove)
    }
}

/**
 * The plan's buttons, two to a row.
 *
 * Four short labels across a phone's rail came out as "DECL ARE A" and "THRO W IN" — a word
 * split in half is worse than any layout that could have avoided it, which is the rule
 * `Choices` already states for the ordinary rail. Here the count is known and small, so they
 * pair off rather than being measured.
 */
@Composable
private fun PlanChoices(choices: List<Choice>, onMove: (Move) -> Unit) {
    choices.chunked(TWO_UP).forEach { pair ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Hair),
        ) {
            pair.forEach { choice ->
                GameButton(
                    label = labelled(choice.label),
                    tone = choice.tone.paint(),
                    onClick = { onMove(choice.move) },
                    modifier = Modifier.weight(1f),
                    compact = true,
                )
            }
            // A lone button on the last row keeps its half rather than stretching to the full
            // width: a set of buttons that are not the same size does not read as a set.
            if (pair.size < TWO_UP) Spacer(Modifier.weight(1f))
        }
    }
}

private const val TWO_UP = 2

/** The choices that act on the turn being read rather than on the member reading it. */

/**
 * What the row above has taken over, and so must not appear as a button under it.
 *
 * `PlanAShed` joins the three: throwing in is part of the turn it pays off, not a promise made
 * beside it — which is why it read as a floating button about you rather than about the plan.
 */
private val TURN_EDITS =
    setOf(Label.PlanADeclare, Label.PlanTakeTheDiscard, Label.ClearLane, Label.PlanAShed)

/**
 * The plan's positions, named, in the band the switch that opened it lives in (design D14).
 *
 * A stop per turn — "Now", then whose turn each one ends — and the lit one is the table on the
 * felt. It was Back / Play / Next in the rail below, which is a way of *travelling* and never a
 * way of *arriving*: nothing said where the head was, so the same green felt meant "now" and
 * "after the whole plan" and the difference was how many times you had pressed. The one position
 * everybody wants — how the hands end up — was three presses away and announced nowhere.
 *
 * **In the header, where the countdown was.** While the plan is open the turns left to the
 * reveal are the plan's own turns, counted twice; the space is better spent saying which of
 * them is being read. That also gives the rail below its scroll back, which is the other half
 * of the same report.
 */
@Composable
internal fun PlanStops(transport: Transport, onMove: (Move) -> Unit) {
    val stage = LocalStage.current
    Row(
        modifier = Modifier.markedAs(stage, "plan:transport"),
        horizontalArrangement = Arrangement.spacedBy(Half),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        transport.stops.forEach { stop -> StopChip(stop, onMove) }
        transport.halt?.let { halt ->
            GameButton(
                label = stringResource(Res.string.label_plan_halt),
                tone = ButtonTone.NEUTRAL,
                onClick = { onMove(halt) },
                modifier = Modifier.markedAs(stage, "plan:halt"),
                compact = true,
            )
        }
    }
}

/**
 * One stop, as a chip: its number, and whose turn it ends.
 *
 * A chip rather than a button because there are four of them on a phone's header line and a
 * button's padding does not fit four — and because what these do is *select* a position rather
 * than perform an action, which is what the lit one says.
 *
 * The name is the point of the whole control. "Now" and "Dune" tell a member which table is on
 * the felt, which is the thing three shuttle buttons never said: pressing them moved the felt
 * and nothing on screen changed to say where it had moved to.
 */
@Composable
private fun StopChip(stop: Stop, onMove: (Move) -> Unit) {
    val stage = LocalStage.current
    val name = stop.seat?.let { speakerName(it) } ?: stringResource(Res.string.board_now)
    val go = stop.go

    // **A box around the word, not a taller word.** A stop is a control and is the size every
    // other control is — these came out 42x28 the first time the plan's row was measured, sized
    // by the word in them rather than by the thumb that has to find them. The height has to be
    // on something that *holds* the text: asking the text itself to be 44 and then to wrap its
    // own content puts it straight back to 28, which is what the first attempt did.
    Box(
        modifier = Modifier
            // Both directions: the first pass set only the height and a short name — "Now" — was
            // still 42dp wide, which is a target that measures right and misses left and right.
            .sizeIn(minWidth = TapTarget, minHeight = TapTarget)
            .markedAs(stage, "plan:stop:${stop.at}")
            .clip(RoundedCornerShape(Gap))
            .background(if (stop.here) Rail.gold else Color.Transparent)
            .then(if (go == null) Modifier else Modifier.clickable { onMove(go) })
            .padding(horizontal = Gap)
            // **Selected, not merely gold.** Which stop is being read is the one thing this
            // control exists to say, and saying it in colour alone puts it out of reach of a
            // screen reader — the bar this app already ships against. A tab, because that is
            // what a row of positions with one of them current is.
            .semantics {
                contentDescription = name
                selected = stop.here
                role = Role.Tab
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name,
            fontSize = DetailSize,
            fontWeight = if (stop.here) FontWeight.Bold else FontWeight.Normal,
            color = if (stop.here) Rail.fill else Rail.gold,
            maxLines = 1,
        )
    }
}

/**
 * The turn being composed, as a row of parts (design D4).
 *
 * **One list of turns, not two.** The rail listed all three while the header listed them again
 * as stops, a position out of step with each other. The header keeps the list, because that is
 * the one that also moves the felt; what is left here is the turn you would be editing from
 * where the head is parked.
 *
 * **And it is a row, not a sentence.** "Turn 1, Tide: swap Tide's card 1 with your card 1" under
 * a row of verbs said the turn twice and its shape not at all — neither half named which of the
 * two piles the card came from. A turn is a sequence, so this draws one: take from *here*, do
 * *this* with it, on *that*, and *these people* throw in. Touching a part opens the question that
 * part is about.
 *
 * At the last stop there is no turn to compose and the arrival is drawn in its place.
 */
@Composable
private fun ComposingTurn(board: Board, onMove: (Move) -> Unit) {
    val turn = board.turn ?: return
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Hair),
    ) {
        TurnRow(turn, onMove)
        TurnNews(turn, onMove)
    }
}

/** The row itself: whose turn, where the card comes from, and what becomes of it. */
@Composable
private fun TurnRow(turn: TurnParts, onMove: (Move) -> Unit) {
    val stage = LocalStage.current
    Row(
        modifier = Modifier.fillMaxWidth().markedAs(stage, "plan:turn"),
        horizontalArrangement = Arrangement.spacedBy(Half),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = speakerName(turn.who),
            fontSize = DetailSize,
            fontWeight = FontWeight.Bold,
            color = Rail.ink,
            modifier = Modifier.padding(end = Half),
        )

        TurnPart(
            mark = turn.opening?.let { if (it == Opening.TAKE_THE_DISCARD) Mark.PILE else Mark.DECK },
            spoken = openingWords(turn.opening),
            move = turn.changeOpening,
            onMove = onMove,
        )
        Bullet()
        TurnPart(
            mark = turn.play?.let { markOf(it) },
            spoken = turn.play?.let { playWords(it) } ?: stringResource(Res.string.board_your_call),
            move = turn.changePlay,
            onMove = onMove,
        )

        // The third and fourth parts appear only when there is something for them to say: a row
        // of empty boxes is a form, and the turn is a conversation.
        turn.detail?.let { detail ->
            Bullet()
            TurnDetail(detail, turn.changeDetail, onMove)
        }
        if (turn.tossers.isNotEmpty() || turn.addToss != null) {
            Bullet()
            TurnToss(turn, onMove)
        }
    }
}

/** Which mark stands for each of the three things a turn can do with its card. */
private fun markOf(play: PlayKind): Mark = when (play) {
    PlayKind.PLAY_IT -> Mark.PLAY
    PlayKind.KEEP_IT -> Mark.KEEP
    PlayKind.BIN_IT -> Mark.BIN
}

/** What the step names: two ends of a trade, or one chip. */
@Composable
private fun TurnDetail(detail: StepLine, move: Move?, onMove: (Move) -> Unit) {
    if (detail is StepLine.Swap) {
        TradeChips(detail, move, onMove)
        return
    }
    TurnChip(words = partWords(detail), spoken = stepWords(detail), move = move, onMove = onMove)
}

/** Who throws in on what this turn puts down — the payoff the plan is arranged around. */
@Composable
private fun TurnToss(turn: TurnParts, onMove: (Move) -> Unit) {
    TurnPart(
        mark = Mark.TOSS.takeIf { turn.tossers.isNotEmpty() },
        spoken = tossWords(turn.tossers),
        move = turn.addToss,
        onMove = onMove,
    )
    turn.tossers.forEach { shed ->
        TurnChip(
            words = shed.rank.serialName,
            spoken = stringResource(Res.string.board_shed, speakerName(shed.who), shed.rank.serialName),
            move = shed.move as? Move.Quiet,
            onMove = onMove,
        )
    }
}

/**
 * **News, not caption.**
 *
 * A claim a reveal has proved wrong, or a card a step names that has since gone, is a thing
 * that happened and neither repairs itself (design D13) — so it is said, under the row, and only
 * when there is something to say. Almost always there is not, and the belt is one line.
 */
@Composable
private fun TurnNews(turn: TurnParts, onMove: (Move) -> Unit) {
    val broken = turn.health == StepHealth.BROKEN
    plannedNotes(broken, turn.playable).forEach { note ->
        Text(
            text = (if (broken) "\u2717 " else "") + note,
            fontSize = DetailSize,
            color = if (broken) WarnInk else Rail.inkDim,
        )
    }
    turn.suggestion?.let { WouldRather(turn.who, it, turn.useSuggestion, onMove) }
}

/**
 * What a step names, short enough for a chip.
 *
 * The **sentence** — "swap Tide's card 1 with your card 1" — is what a screen reader is given
 * and is not what the row draws: a caption that long is the thing the row replaced. This names
 * the cards or the rank and stops.
 */
@Composable
private fun partWords(detail: StepLine): String = when (detail) {
    // Never reached: a trade is two chips with a drawn arrow between them, because the obvious
    // character for it is a bet on the font and the chip came out with a box in it.
    is StepLine.Swap -> {
        ""
    }

    is StepLine.Declare -> {
        detail.rank.serialName
    }
    is StepLine.PutDown -> {
        val rank = detail.rank
        if (rank == null) {
            stringResource(Res.string.board_part_card, detail.slot)
        } else {
            stringResource(Res.string.board_part_card_called, detail.slot, rank.serialName)
        }
    }

    // None of them names anything beyond itself — see `namesSomething`, which keeps them off
    // the row in the first place.
    StepLine.TakeTheDiscard, StepLine.Bin, StepLine.UseIt -> {
        ""
    }
}

/** Where the turn's card comes from, said for a screen reader. */
@Composable
private fun openingWords(opening: Opening?): String = when (opening) {
    Opening.DRAW -> stringResource(Res.string.board_from_the_deck)
    Opening.TAKE_THE_DISCARD -> stringResource(Res.string.board_step_take_discard)
    null -> stringResource(Res.string.board_your_call)
}

/** What becomes of it. */
@Composable
private fun playWords(play: PlayKind): String = when (play) {
    PlayKind.PLAY_IT -> stringResource(Res.string.label_play_the_card)
    PlayKind.KEEP_IT -> stringResource(Res.string.label_keep_the_card)
    PlayKind.BIN_IT -> stringResource(Res.string.label_let_the_card_go)
}

/** Who throws in on it, which is the payoff the whole plan is arranged around. */
@Composable
private fun tossWords(tossers: List<ShedLine>): String {
    if (tossers.isEmpty()) return stringResource(Res.string.board_nobody_throws_in)
    // Built rather than joined: `stringResource` is a composable and cannot be called once per
    // item inside a lambda, so each line is resolved in the composition and then put together.
    val said = tossers.map { stringResource(Res.string.board_shed, speakerName(it.who), it.rank.serialName) }
    return said.joinToString(", ")
}

/** The six marks a turn is drawn with. See `TurnMarks`. */
private enum class Mark { DECK, PILE, PLAY, KEEP, BIN, TOSS }

/**
 * One part of the row: its mark, or an empty frame where nobody has said yet.
 *
 * An empty frame rather than nothing, because the part still exists — every turn takes a card
 * from somewhere and does something with it — and a gap would read as the row being shorter
 * rather than as a decision waiting.
 */
@Composable
private fun TurnPart(mark: Mark?, spoken: String, move: Move?, onMove: (Move) -> Unit) {
    val stage = LocalStage.current
    val ink = if (mark == null) Rail.inkDim else Rail.ink

    Box(
        modifier = Modifier
            .size(PartSize)
            .clip(RoundedCornerShape(TurnCorner))
            .background(if (mark == null) Color.Transparent else Rail.line)
            .then(if (move == null) Modifier else Modifier.clickable { onMove(move) })
            .markedAs(stage, "plan:part:${mark ?: "empty"}")
            .semantics { contentDescription = spoken },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(MarkSize)) {
            when (mark) {
                Mark.DECK -> drawDeck(ink)
                Mark.PILE -> drawPile(ink)
                Mark.PLAY -> drawPlay(ink)
                Mark.KEEP -> drawKeep(ink)
                Mark.BIN -> drawBin(ink)
                Mark.TOSS -> drawToss(ink)
                null -> drawEmptyPart(ink)
            }
        }
    }
}

/** The two ends of a planned trade, with the arrow between them drawn rather than written. */
@Composable
private fun TradeChips(detail: StepLine.Swap, move: Move?, onMove: (Move) -> Unit) {
    val spoken = stepWords(detail)
    val ink = Rail.ink
    TurnChip(
        words = stringResource(Res.string.board_part_end, speakerName(detail.fromWho), detail.fromSlot),
        spoken = spoken,
        move = move,
        onMove = onMove,
    )
    Canvas(modifier = Modifier.size(MarkSize)) { drawTrade(ink) }
    TurnChip(
        words = stringResource(Res.string.board_part_end, speakerName(detail.toWho), detail.toSlot),
        spoken = spoken,
        move = move,
        onMove = onMove,
    )
}

/** A part with words in it rather than a mark: a rank, or what a swap names. */
@Composable
private fun TurnChip(words: String, spoken: String, move: Move?, onMove: (Move) -> Unit) {
    Box(
        modifier = Modifier
            .sizeIn(minWidth = TapTarget, minHeight = TapTarget)
            .clip(RoundedCornerShape(TurnCorner))
            .background(Rail.line)
            .then(if (move == null) Modifier else Modifier.clickable { onMove(move) })
            .padding(horizontal = Gap)
            // The sentence, not the chip's shorthand: the row is read at a glance by eye and
            // read out in full by everything else.
            .semantics { contentDescription = spoken },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = words, fontSize = DetailSize, color = Rail.ink, maxLines = 1)
    }
}

/** The dot between two parts: one thing happens, and then the next. */
@Composable
private fun Bullet() {
    Text(text = "\u00b7", fontSize = DetailSize, color = Rail.inkDim)
}

/** A part nobody has answered: a dashed square, which is a question rather than a blank. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawEmptyPart(ink: Color) {
    val w = size.minDimension
    val r = w * EMPTY_DOT
    for (x in listOf(EMPTY_NEAR, EMPTY_FAR)) {
        for (y in listOf(EMPTY_NEAR, EMPTY_FAR)) {
            drawCircle(ink, radius = r, center = androidx.compose.ui.geometry.Offset(w * x, w * y))
        }
    }
}

private const val EMPTY_DOT = 0.06f
private const val EMPTY_NEAR = 0.3f
private const val EMPTY_FAR = 0.7f

/**
 * A part of the row is a control, so it is the size every other control is.
 *
 * It was 30dp, chosen so a row of four would fit a phone — which is sizing a target by the space
 * left over rather than by the finger that has to find it. `TouchTargetTest` never measured this
 * row: it drew the ordinary turn and the rank rail, and the plan is a third table neither
 * reaches. The mark inside stays small; what grew is the area.
 */
private val PartSize = TapTarget
private val MarkSize = 22.dp

/**
 * One turn still to come: its number, whose it is, and what the plan has it doing.
 *
 * A turn whose claim a reveal has contradicted wears a cross and says so, for as long as the
 * plan holds that step (design D13). One whose card has merely *moved* says nothing at all: the
 * table watched it go, so nothing has been learned and there is nothing to announce.
 */
@Composable
private fun PlannedRow(turn: LaneLine, edits: List<Choice>, onMove: (Move) -> Unit) {
    val stage = LocalStage.current
    val broken = turn.health == StepHealth.BROKEN
    val line = plannedWords(turn)
    val notes = plannedNotes(broken, turn.playable)
    val spoken = (listOf(line) + notes).joinToString(". ")

    Column(modifier = turnBox(stage, turn, spoken)) {
        Text(
            text = (if (broken) "\u2717 " else "") + line,
            fontSize = DetailSize,
            fontWeight = FontWeight.Bold,
            color = plannedInk(broken, turn.playable),
        )
        notes.forEach { note ->
            Text(text = note, fontSize = DetailSize, color = if (broken) WarnInk else Rail.inkDim)
        }
        turn.suggestion?.let { WouldRather(turn.who, it, turn.useSuggestion, onMove) }
        if (edits.isNotEmpty()) PlanChoices(edits, onMove)
    }
}

/**
 * The turn's own box.
 *
 * **No longer tappable.** It used to park the transport on itself, which was the rail's half of
 * a job the header's stops now do alone — and did it one position out, so tapping the third row
 * lit the second stop. There is one turn drawn here and it is already the one being read, so
 * there is nowhere for a tap on it to go.
 *
 * `Rail.line` is theme-aware, and that is not a detail: `Slate.fill` is the felt's furniture and
 * is dark in *both* schemes, so a turn drawn on it put `Rail.ink` — which follows the theme — as
 * dark ink on dark slate the moment the light scheme was picked. The light golden caught it.
 */
@Composable
private fun turnBox(stage: Stage, turn: LaneLine, spoken: String): Modifier = Modifier
    .fillMaxWidth()
    .clip(RoundedCornerShape(TurnCorner))
    .background(Rail.line)
    .markedAs(stage, "plan:turn:${turn.number}")
    .padding(horizontal = Half, vertical = Hair)
    .semantics { contentDescription = spoken }

private val TurnCorner = 6.dp

/**
 * What a turn has to say beyond its step: a claim under it proved wrong, or a card it names
 * gone. Both are news and neither repairs itself, so they are said rather than drawn (design D13).
 */
@Composable
private fun plannedNotes(broken: Boolean, playable: Boolean): List<String> = listOfNotNull(
    stringResource(Res.string.board_broken).takeIf { broken },
    stringResource(Res.string.board_unplayable).takeIf { !playable },
)

/** "Turn 2, Nina: swap your card 2 with Don's card 3" — or "your call", which is a real answer. */
@Composable
private fun plannedWords(turn: LaneLine): String {
    val words = turn.step?.let { stepWords(it) } ?: stringResource(Res.string.board_your_call)
    return stringResource(Res.string.board_turn_planned, turn.number, speakerName(turn.who), words)
}

/** Warning ink for a disproved claim, dim for a turn that cannot be drawn, plain otherwise. */
@Composable
private fun plannedInk(broken: Boolean, playable: Boolean): Color = when {
    broken -> WarnInk
    playable -> Rail.ink
    else -> Rail.inkDim
}

/**
 * "Nina would rather …": the turn owner's own alternative to the step somebody set for them.
 *
 * Beside the step and not over it — nobody's edit is written over by a bot — and only on the
 * turn being read (design D8). Tapping it puts it on the plan, which is an ordinary edit by
 * whoever tapped, so there is no second control to learn.
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
    Text(
        text = words,
        fontSize = DetailSize,
        color = Rail.ink,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (use == null) Modifier else Modifier.clickable { onMove(use) })
            .markedAs(stage, "plan:suggest"),
    )
}

/**
 * Where the plan lands (design D10 and D12): three numbers, and no verdict.
 *
 * The coalition's best hand, the caller's total as the coalition believes it, and how many of
 * the caller's cards nobody has spoken about. The app does **not** say whether that wins. The
 * comparison is two numbers, they are both on the screen, and a coalition round whose whole
 * pleasure is three people arguing about what to do becomes dragging cards until the app says
 * WINS. The unseen count travels with them because a believed total stated without saying how
 * much of it is a guess is a number pretending to be information.
 */
@Composable
private fun PlanArrival(outcome: PlanOutcome) {
    val stage = LocalStage.current
    Column(
        modifier = Modifier.fillMaxWidth().markedAs(stage, "plan:arrival"),
        verticalArrangement = Arrangement.spacedBy(Hair),
    ) {
        Text(
            text = stringResource(Res.string.board_arrival_title),
            fontSize = DetailSize,
            fontWeight = FontWeight.Bold,
            color = Rail.inkDim,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(Res.string.board_arrival_ours, outcome.ourBest),
            fontSize = DetailSize,
            color = Rail.ink,
        )
        Text(
            text = stringResource(Res.string.board_arrival_theirs, outcome.theirBelieved),
            fontSize = DetailSize,
            color = Rail.ink,
        )
        Text(
            text = stringResource(Res.string.board_arrival_unseen, outcome.unseen),
            fontSize = DetailSize,
            color = Rail.inkDim,
        )
    }
}

/**
 * Runs the film, and parks the head on the last turn when it has finished drawing.
 *
 * The transport's "running" is a *request*: the screen asks for the plan to be played, the
 * stage draws it at whatever pace the player has chosen, and only the stage knows when that is
 * over — the length of a turn depends on the pace dial, on reduced motion, and on how much the
 * turn actually moves. So this waits for the felt to start and then to go quiet, and parks the
 * head where the plan lands, which is also where the composer wakes up again (design D14).
 *
 * The first wait is bounded. A plan whose turns have nothing to draw — every lane undecided —
 * would otherwise sit "running" for ever waiting on an animation that was never coming.
 */
@Composable
internal fun PlanRunner(table: Table, onMove: (Move) -> Unit) {
    val stage = LocalStage.current
    val board = table.board
    // Where the film is going, which is a named stop now rather than always the end: a member
    // who pressed ② is watching the first two turns and the head must park on the second.
    val target = board?.transport?.travellingTo

    LaunchedEffect(target) {
        if (target == null) return@LaunchedEffect
        withTimeoutOrNull(FILM_STARTS_MS) { snapshotFlow { stage.drawing }.first { it } }
        snapshotFlow { stage.drawing }.first { !it }
        onMove(Move.Ask(Question.ThePlan(at = target)))
    }
}

/** How long the felt is given to start drawing the film before it is taken as having none. */
private const val FILM_STARTS_MS = 1_500L

// ---------------------------------------------------------------------------- carrying a card

/**
 * Carrying a card to where the plan should put it (design D5).
 *
 * **A drag begins on movement, not after a hold** (design D5a). A long press is the tax paid
 * for telling a drag apart from a scroll, and in plan mode there is nothing to scroll, nothing
 * to tap-to-play and nothing to swipe — so there is nothing to disambiguate against and no
 * reason to make a player wait.
 *
 * A release only lands on a place the composer has already lit. Anything else puts the card
 * back and changes nothing, which is what makes a mis-drop cost nothing rather than quietly
 * editing the plan.
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

/**
 * The card in hand right now: one being carried, or one picked up by touching it.
 *
 * The two paths share this on purpose (design D5). A drag lights its destinations before the
 * release; a touch has to light exactly the same ones, or the non-dragging path is a lesser
 * path — offered, but played blind.
 */
@Composable
private fun inHand(board: Board?): CardRef? = LocalStage.current.carrying ?: board?.picked

/** Whether this card is a place the plan would accept the card in hand. */
@Composable
internal fun wantedNow(ref: CardRef, board: Board?, composer: PlanComposer?): Boolean {
    val held = inHand(board) ?: return false
    if (held == ref) return false
    return composer?.drops?.get(held)?.containsKey(PlanTarget.Card(ref)) == true
}

/** Whether the discard pile is a place the card in hand could be put down. */
@Composable
internal fun discardWanted(board: Board?, composer: PlanComposer?): Boolean {
    val held = inHand(board) ?: return false
    return composer?.drops?.get(held)?.containsKey(PlanTarget.Discard) == true
}
