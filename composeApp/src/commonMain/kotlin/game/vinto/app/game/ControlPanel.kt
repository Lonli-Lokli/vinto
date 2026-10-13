package game.vinto.app.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.isFinite
import androidx.compose.ui.unit.sp
import game.vinto.app.TextMark
import game.vinto.app.art.Res
import game.vinto.app.art.card_described
import game.vinto.app.art.card_face_down
import game.vinto.app.art.card_in_play
import game.vinto.app.art.card_position
import game.vinto.app.art.card_the_deck
import game.vinto.app.art.rail_aim_empty
import game.vinto.app.art.rail_aim_first
import game.vinto.app.art.rail_aim_second
import game.vinto.app.art.rail_aiming_at
import game.vinto.app.art.rail_card_action
import game.vinto.app.art.rail_card_plain
import game.vinto.app.art.table_sending
import game.vinto.app.asked
import game.vinto.app.cardLong
import game.vinto.app.cardName
import game.vinto.app.detailed
import game.vinto.app.drawTrophy
import game.vinto.app.keyOf
import game.vinto.app.labelled
import game.vinto.app.said
import game.vinto.app.speakerName
import game.vinto.app.theme.BusyLine
import game.vinto.app.theme.ButtonTone
import game.vinto.app.theme.GameButton
import game.vinto.app.theme.GeneratedAvatar
import game.vinto.app.theme.Rail
import game.vinto.app.theme.feltEdge
import game.vinto.app.theme.roomForLabels
import game.vinto.client.Aim
import game.vinto.client.AimedCard
import game.vinto.client.Choice
import game.vinto.client.Detail
import game.vinto.client.Move
import game.vinto.client.RankChoice
import game.vinto.client.Say
import game.vinto.client.SeatChoice
import game.vinto.client.Speaker
import game.vinto.client.Table
import game.vinto.client.Target
import game.vinto.client.Tone
import game.vinto.engine.CardView
import game.vinto.engine.PlayerView
import game.vinto.shapes.Card
import game.vinto.shapes.GamePhase
import game.vinto.shapes.Rank
import game.vinto.shapes.TargetType
import game.vinto.shapes.actionIsLive
import game.vinto.shapes.getCardConfig
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private val PanelPad = 12.dp

/**
 * The widest the rail's controls are laid out, however wide the rail: about a phone and a half.
 * Under a portrait tablet's felt the rail is the screen's whole width, and spreading a phone's
 * prompt, card and two buttons across 820 points read as a phone app blown up.
 */
private val RailMost = 600.dp

/** One row of buttons: the room the foot keeps whether or not there is anything to press. */
private val FootRow = 50.dp

/**
 * How much of a side rail the control group takes, given how tall that rail is.
 *
 * A share rather than a fixed height, because a fixed one left most of a desktop rail as empty
 * cream: the group sat at its 340 in the middle of eight hundred points of nothing, which reads
 * as a panel that failed to load rather than as a considered margin. Growing with the rail gives
 * the room to the one tenant that can use it — the log of what has been happening — and keeps
 * the margins above and below in proportion to the thing between them.
 *
 * Clamped at both ends. The floor is the deepest the panel ever goes (a King's fourteen chips
 * over a prompt of two lines), so nothing is ever squeezed; the ceiling is where more height
 * stops being a control panel and starts being a column of log.
 */
private fun railGroup(rail: Dp): Dp = (rail * RAIL_GROUP_SHARE).coerceIn(TallRail, TallRailMost)

private const val RAIL_GROUP_SHARE = 0.6f
private val TallRail = 340.dp
private val TallRailMost = 620.dp

/**
 * The log's own strip at the foot of a tall rail.
 *
 * A fixed height on purpose: the whole point of moving it out of the block is that it stops
 * being something the rest of the panel has to make room for, and a strip that grew with its
 * contents would be the same problem wearing a different hat.
 */
private val LogTall = 132.dp

/** The card in play, drawn in the rail: no smaller than a card in your own hand on a phone. */
private val RailCard = CardScale(56.dp, 78.dp)

/**
 * The most of the rail's width the card column may take; the words beside it need the rest.
 *
 * One number for one card and for two, which is the point: a pair allowed to be as wide as
 * two cards would take the prompt's column with it, and the aim's job is done at any size the
 * faces can be told apart at — well under the size a card being *decided about* wants.
 */
private const val RailCardShare = 0.4f

/**
 * The narrow stack's card ([RailStack]): at most this share of the height above the stack's foot,
 * and of its width. A card being decided about, larger than a hand's, and the words and the log
 * under it keep the rest.
 */
private const val StackCardShare = 0.4f
private const val StackCardWidth = 0.6f

/** And the two cards a Jack or a Queen is aimed at, which share the stack's whole width with their mark. */
private const val StackPairShare = 1f

/** The stack's log runs down to its foot, so its well may be deeper than a phone's. */
private const val StackLogLines = 20

/** The mark between the two aimed cards, which is what says they exchange places. */
private val SwapMarkWidth = 14.dp
private val SwapMarkHeight = 12.dp

/** Where the two shafts sit in that box, how long a barb is, and how thick a stroke — all of its height. */
private const val SwapUpper = 0.25f
private const val SwapLower = 0.75f
private const val SwapHead = 0.25f
private const val SwapHair = 0.125f

/** The line under each aimed card: whose hand it is in, and which card along it. */
private val AimSize = 11.sp
private const val AimCaptionLines = 2

/** The outline of an end nothing has been chosen for. */
private val EmptyDash = 3.dp
private val EmptyStroke = 1.dp

private val Gap = 8.dp
private val Half = 4.dp
private val LogCorner = 6.dp

/**
 * Between two things one actor did in one turn: an arrow the app draws, in the place of a
 * character it used to type.
 *
 * The typed arrow was whatever each phone's fonts made of it, heavy on one and an outline on
 * another, and a screen reader read its character name out between two moves. The drawn one
 * sits in the text as an inline picture [ThenWide] by [ThenHigh] ems, so it grows with the
 * words; what a screen reader hears in its place is [ThenSaid], a pause.
 */
private const val ThenMark = "then"
private const val ThenSaid = ","
private val ThenWide = 1.2.em
private val ThenHigh = 1.em

/** The share of the arrow's box left empty before it and after it; a word space follows it too. */
private const val ThenBefore = 0.3f
private const val ThenAfter = 0.1f

/** How a test finds the lines of the log: the drawn arrow left them no character to look for. */
internal const val LOG_LINE = "log-line"

/** The round-ending call's picture, a little larger than the label so it reads at a glance. */
private val StakesMarkSize = 18.sp

/** How many turns the log keeps: this one, and the one it is answering. */
private const val TurnsKept = 2

/** The well's greatest depth in lines, its line pitch, and how much darker than the rail it sits. */
private const val LogLines = 8
private const val LogLineFactor = 1.4f
private const val PromptLineFactor = 1.35f
private const val LogWell = 0.45f

/**
 * What the player can do, and nothing else.
 *
 * A dark rail under the felt, in both light and dark, because that is what the web app does
 * and because it is right: the panel is the edge of the table, not a page. A light card
 * surface here makes the felt look like an image embedded in an app rather than the thing the
 * app is.
 *
 * Everything shown comes from [Table], which is a pure function of the game view — so this
 * composable has no opinion about the rules and cannot develop one. If a button is missing,
 * the answer is in `TableModel.kt` and a test can be written for it in milliseconds; if a
 * button is ugly, the answer is here.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ControlPanel(
    state: TableState,
    onMove: (Move) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Where the rail stands against the felt: under it, or beside it ([RailShape]). The size comes
     * from the caller's modifier either way; what this decides is which edge of the rail touches
     * the felt, and so which edge wears the moulding — and, for the narrow stack beside a status
     * column, how what is in it is laid out.
     */
    shape: RailShape = RailShape.UNDER,
    /** Where the turn's choices are drawn: here, or in a status column, which leaves the rail none. */
    choices: ChoicesAt = ChoicesAt.RAIL,
) {
    val table = state.table
    val side = shape != RailShape.UNDER
    val edge = MaterialTheme.colorScheme.feltEdge()
    Surface(
        modifier = modifier
            // The moulding between the cloth and the rail.
            //
            // Two fills meeting is a *layout*; a table has a beading where the felt is
            // tucked under the edge, and drawing it is what stops the rail's share of the
            // screen reading as a panel stuck beside a picture of a game. Four strokes: the
            // dark seam the cloth disappears into, the lit face of the moulding, and the
            // shadow it throws onto the rail. Along the top when the rail is under the felt;
            // down the left edge when it stands beside it.
            .drawBehind {
                val seam = Seam.toPx()
                if (side) {
                    drawRect(SeamDark, size = Size(seam, size.height))
                    drawRect(edge, topLeft = Offset(seam, 0f), size = Size(seam, size.height))
                    drawRect(
                        Brush.horizontalGradient(
                            0f to SeamShadow,
                            1f to Color.Transparent,
                            startX = seam * 2,
                            endX = seam * SHADOW_DEPTH,
                        ),
                    )
                } else {
                    drawRect(SeamDark, size = Size(size.width, seam))
                    drawRect(edge, topLeft = Offset(0f, seam), size = Size(size.width, seam))
                    drawRect(
                        Brush.verticalGradient(
                            0f to SeamShadow,
                            1f to Color.Transparent,
                            startY = seam * 2,
                            endY = seam * SHADOW_DEPTH,
                        ),
                    )
                }
            },
        color = Rail.fill,
    ) {
        // Two columns and a foot, because the rail is a fixed height and vertical room is the
        // scarce thing on a phone. The choices are pinned to the foot, where a thumb rests, and
        // share one row rather than stacking; never scrolled, never pushed — a rail that
        // scrolled to reach its second button had that button half under the edge of the
        // screen on every phone taller than the one it was drawn on. Everything above them is
        // one block that fills the rest: the card being decided about on the left, as tall as
        // the block, and beside it the prompt with the box of recent moves under it, the log
        // taking whatever the prompt leaves. Nothing in it is a fixed depth that could leave
        // a strip of rail empty above the buttons, and nothing in it moves between one move
        // and the next on the same phone, which is what a fixed box is for.
        //
        // Scrolling survives only as the last resort for a doubled system font: the prompt
        // scrolls within its own room, and a King's fourteen chips within theirs, and the
        // foot may never take the prompt's first line.
        // Only the *rank* grid crowds the foot: fourteen chips is four rows and there is no
        // log under them. Three seats is one row, and the space the card gave up is better
        // spent on what has been happening than left blank.
        val crowded = table.ranks.isNotEmpty() || table.board != null
        // Every line, the player's own draw included. It used to be folded out whenever
        // the prompt above it said the same thing — "You drew the Joker", twice — and what
        // that left in the box was the previous seat's move, so the log read as though your
        // draw had not happened and the last thing on the table was Don's (product owner).
        // A line repeated once is cheaper than a line missing.
        val recent = lastTurns(state.recent)
        val density = LocalDensity.current
        val promptLine = with(density) { (PromptSize * PromptLineFactor).toDp() }
        val twoLines = promptLine + with(density) { (DetailSize * LogLineFactor).toDp() }
        val inPlay = railCard(state.view, table)

        // The plan takes the rail whole (design D1). It cannot share the ordinary layout: the
        // block there *scrolls*, and the transport and the turns went straight below the fold —
        // the plan read through a letterbox, which is the exact report this change answers. The
        // plan's own rail has no scroll and no log, because it is the one thing in this round
        // that has to be read entire.
        val board = table.board
        if (board != null) {
            PlanRail(
                board,
                table,
                onMove,
                // Padded at the sides only. The rail is 240 dp and every point of it is spoken
                // for — the turns, the transport and the buttons all have to be *there*, so the
                // margin above and below is the first thing to give up.
                modifier = Modifier.fillMaxSize().padding(horizontal = PanelPad).widthIn(max = RailMost),
            )
        } else {
            val how = RailHow(shape, choices)
            RailBody(state, table, inPlay, recent, crowded, promptLine, twoLines, how, onMove)
        }
    }
}

/** Where the rail stands against the felt, and so how what is in it is laid out. */
enum class RailShape {
    /** Under the felt, the table upright: two columns over a foot. */
    UNDER,

    /** Beside the felt, the table on its side: the same two columns, the moulding down its edge. */
    BESIDE,

    /**
     * Beside the felt and a status column too, in a fifth of the screen: one stack, read from the
     * top ([RailStack]). The open iPhone Duo on its side.
     */
    STACK,
}

/** How the rail is being drawn: its [shape], and where the turn's [choices] are. */
private data class RailHow(val shape: RailShape, val choices: ChoicesAt) {
    val side: Boolean get() = shape != RailShape.UNDER
}

/** The two columns and the foot: everything the rail draws, laid out by the rule above. */
@Suppress("LongParameterList")
@Composable
private fun RailBody(
    state: TableState,
    table: Table,
    inPlay: CardView?,
    recent: List<Say>,
    crowded: Boolean,
    promptLine: Dp,
    twoLines: Dp,
    how: RailHow,
    onMove: (Move) -> Unit,
) {
    if (how.shape == RailShape.STACK) {
        RailStack(state, table, inPlay, recent, how.choices, onMove)
        return
    }

    // Never wider than [RailMost], centred: under a portrait tablet's felt the whole width was the
    // phone's controls stretched — two buttons each wider than a phone.
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth().wrapContentWidth().widthIn(max = RailMost).padding(PanelPad),
    ) {
        val rail = maxHeight
        // What the group actually gets, which on a tall rail is [TallRail] rather than the
        // window: the foot's cap is a share of the room the choices really have.
        val held = if (rail.isFinite) minOf(rail, railGroup(rail)) else rail
        val footCap =
            if (held.isFinite) (held - promptLine - Gap).coerceAtLeast(promptLine) else Dp.Unspecified

        // The controls are one group, and on a tall rail they are a group *in the middle of it*.
        //
        // Under a phone's felt the rail is about a third of the screen and the block fills it,
        // so pinning the choices to the foot puts them where a thumb rests with nothing wasted.
        // Standing beside a desktop felt the same column is the whole window: the block stretched
        // to eight hundred points, which put the prompt against the ceiling and the buttons
        // against the floor with a third of a metre of empty panel between them. That is the
        // "controls outside the table" reading — not the side rail itself, but a rail whose
        // contents had been pulled to its two ends.
        //
        // So a tall rail holds the same group at a phone's height and centres it on the felt it
        // stands beside. Nothing inside changes: the block still fills the group and the choices
        // still sit at its foot, which is what `RailFitsTest` measures.
        //
        // **Tall means room for both**: the group, and the log's strip under it. It meant only
        // "taller than the group", and between about 340 and 700 points — an open iPhone Duo on
        // its side, an iPad mini on its side — the group was centred over the strip's top and the
        // log's first line was drawn across SWAP CARDS (`UnfoldedPhonesTest`). A rail with room
        // for one but not both keeps the phone's arrangement, the log inside the block.
        val tall = rail.isFinite && rail >= railGroup(rail) + LogTall + Gap
        val group = if (tall) Modifier.fillMaxWidth().height(railGroup(rail)) else Modifier.fillMaxSize()

        // **On a tall rail the log has a place of its own, at the foot, and never moves.**
        //
        // It used to live inside the block, under the prompt, and only when the panel was not
        // otherwise busy — so a King's fourteen chips arrived, the log vanished, and everything
        // above it jumped. A record of what just happened is the one thing on this panel that is
        // *never* the thing you are being asked about, and it is exactly the thing that was
        // being moved by every question. Outside the centred group it is furniture: same place,
        // same height, whatever the turn is doing.
        //
        // The group is centred in what the strip leaves rather than in the whole rail, so the two
        // can never meet whatever the rail's height.
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Gap)) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(modifier = group, verticalArrangement = Arrangement.spacedBy(Gap)) {
                    RailBlock(
                        state,
                        table,
                        inPlay,
                        recent,
                        crowded = crowded || tall,
                        twoLines,
                        modifier = Modifier.weight(1f),
                    )
                    RailFoot(table, footCap, how.side, onMove, choices = how.choices)
                }
            }

            if (tall) {
                Box(modifier = Modifier.fillMaxWidth().height(LogTall)) {
                    RecentActions(recent)
                }
            }
        }
    }
}

/** The card being decided about, and beside it the prompt over the box of recent moves. */
@Suppress("LongParameterList")
@Composable
private fun RailBlock(
    state: TableState,
    table: Table,
    inPlay: CardView?,
    recent: List<Say>,
    crowded: Boolean,
    twoLines: Dp,
    modifier: Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val block = maxHeight
        val room = maxWidth
        val caption = with(LocalDensity.current) { (AimSize * LogLineFactor).toDp() } * AimCaptionLines
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(PanelPad),
        ) {
            // The card the player is deciding about, at a size its face can be read at,
            // beside the words about it — as the web table showed it. The felt has it too, in
            // the slot it was drawn into, at a size that says *where* it is rather than
            // *what*; the rail is where the decision is made. Whole or not at all: above a
            // rank grid the block is a line deep, and a sliver of a card is a thing that
            // looks broken.
            // The column is there for the whole of the viewer's turn and not otherwise, and it
            // always holds a card (`railCard` says which), so nothing beside it moves between
            // one move and the next of the same turn. It came and went with the card once,
            // and then held an empty slot on every bot's turn; both moved, or looked wrong.
            //
            // The exception, and the reason [Table.aim] exists: while a Jack or a Queen is
            // being aimed, the column holds the two cards it is aimed *at* instead. The card
            // in play would be the Queen — which the player chose to play a moment ago and
            // can read off the discard — while the pair being swapped, the actual decision,
            // was one line of small text. The web table drew the pair, and it was right to.
            val aim = table.aim
            if (aim != null) {
                railPairFor(block, room, caption)?.let { AimedPair(aim, it, caption) }
            } else if (inPlay != null) {
                railCardFor(block, room)?.let { InPlayCard(inPlay, it) }
            }
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(Gap),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = minOf(twoLines, block), max = block)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Gap),
                ) {
                    val shown = (inPlay as? CardView.Visible)?.card
                    Heading(table = table, teaching = state.teaching, shown = shown)
                    // What the plan says about the turn on play, as information only: one line
                    // of the plan's own words, and nothing to touch.
                    table.planLine?.let { PlanLineText(it) }
                    Answer(state)
                }
                // The log is what happened *before* now. The prompt above is now, and the
                // two are built from the same narration, so the top line of the log was
                // routinely the heading again in smaller type. Only a rank grid takes its room.
                if (!crowded) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f)) { RecentActions(recent) }
                }
            }
        }
    }
}

/**
 * The narrow rail beside an open iPhone Duo's felt: one stack, read from the top ([RailShape.STACK]).
 *
 * The owner's, 2026-10-13: the drawn card, then what it is and what the table is asking, then the
 * log, so that no part of the rail stands empty. Beside a status column the turn's choices stand in
 * the column ([ChoicesAt.COLUMN]) and the rail is a fifth of the screen, where a phone's two columns
 * and a foot left the card a thumb wide beside a prompt broken at every second word, over a foot
 * with nothing in it. A picker the column cannot hold — a King's ranks, the rank put down by a swap
 * and the Just Swap that is its alternative — stays here, at the foot ([RailFoot]), and takes its
 * room first, so the answers are never under the edge; what is above it shares what is left
 * ([StackAbove]).
 */
@Composable
private fun RailStack(
    state: TableState,
    table: Table,
    inPlay: CardView?,
    recent: List<Say>,
    choices: ChoicesAt,
    onMove: (Move) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(PanelPad)) {
        val rail = maxHeight
        val promptLine = with(LocalDensity.current) { (PromptSize * PromptLineFactor).toDp() }
        // As in the phone's rail: the foot may never take the prompt's first line.
        val footCap =
            if (rail.isFinite) (rail - promptLine - Gap).coerceAtLeast(promptLine) else Dp.Unspecified
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Gap)) {
            // Weighted, so the foot below is measured first and this takes what it leaves.
            StackAbove(state, table, inPlay, recent, Modifier.fillMaxWidth().weight(1f))
            RailFoot(table, footCap, side = true, onMove = onMove, narrow = true, choices = choices)
        }
    }
}

/**
 * Everything in the stack above its foot: the card, the words about it, the log.
 *
 * The card takes a share of the height and no more than a share of the width, and is drawn whole or
 * not at all, as it is in a phone's rail; the words come under it, scrolling within what is left
 * after the card; the log takes the rest, to the foot.
 */
@Composable
private fun StackAbove(
    state: TableState,
    table: Table,
    inPlay: CardView?,
    recent: List<Say>,
    modifier: Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val above = maxHeight
        val room = maxWidth
        val caption = with(LocalDensity.current) { (AimSize * LogLineFactor).toDp() } * AimCaptionLines
        val cardRoom = if (above.isFinite) above * StackCardShare else RailCard.height
        val aim = table.aim
        val pair = aim?.let { railPairFor(cardRoom, room, caption, share = StackPairShare) }
        val shown = inPlay?.takeIf { aim == null }
        val card = shown?.let { railCardFor(cardRoom, room, share = StackCardWidth) }
        val taken = pair?.let { it.height + caption } ?: card?.height ?: 0.dp
        val wordsCap = if (above.isFinite) (above - taken - Gap).coerceAtLeast(0.dp) else Dp.Unspecified
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Gap)) {
            StackCard(aim, pair, shown, card, caption)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = wordsCap)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Gap),
            ) {
                Heading(table = table, teaching = state.teaching, shown = (inPlay as? CardView.Visible)?.card)
                table.planLine?.let { PlanLineText(it) }
                Answer(state)
            }
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) { RecentActions(recent, most = StackLogLines) }
        }
    }
}

/**
 * The stack's card: the two a Jack or a Queen is aimed at, or the card in play — each only where
 * [StackAbove] found room to draw it whole.
 */
@Composable
private fun StackCard(aim: Aim?, pair: CardScale?, shown: CardView?, card: CardScale?, caption: Dp) {
    if (aim != null && pair != null) AimedPair(aim, pair, caption)
    if (shown != null && card != null) InPlayCard(shown, card)
}

/** The card in play, named as the card being decided about — or as the deck, before the draw. */
@Composable
private fun InPlayCard(inPlay: CardView, scale: CardScale) {
    val label = when (inPlay) {
        is CardView.Visible -> stringResource(Res.string.card_in_play, inPlay.card.rank.serialName)
        CardView.Hidden -> stringResource(Res.string.card_the_deck)
    }
    CardFace(card = inPlay, scale = scale, label = label)
}

/**
 * The grid of answers, when there is one, and the choices under it — pinned, and never the
 * whole rail. It keeps one row's room even with nothing to press — "Raph is playing" — so the
 * block above it, and the log in it, are the same size on every turn of the round: a box that
 * grew when the buttons went and shrank when they came back was the one thing still moving.
 *
 * Two questions fill the grid, and they are the same question: name one of these. A King names
 * a rank; an Ace and the coalition name a player.
 */
@Composable
private fun RailFoot(
    table: Table,
    footCap: Dp,
    side: Boolean,
    onMove: (Move) -> Unit,
    /** The narrow stack's foot, a fifth of the screen wide: its grids keep a thumb's width a chip. */
    narrow: Boolean = false,
    /**
     * Where the choices are. In a status column the foot is not drawn at all: the choices are the
     * whole of what it holds then — a set with a picker in it stays here ([choicesAt]) — and an empty
     * row kept for them is a row the card and the log can use.
     */
    choices: ChoicesAt = ChoicesAt.RAIL,
) {
    if (choices == ChoicesAt.COLUMN) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = FootRow, max = footCap)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Gap),
    ) {
        RankGrid(table.ranks, LocalStage.current, onMove, narrow)
        SeatGrid(table.seats, onMove, narrow)
        Choices(table, side, onMove)
    }
}

/** A stable key for a seat's lane, for the lesson's pointer and for tests. */
private fun speakerKey(who: Speaker): String = when (who) {
    Speaker.You -> "you"
    is Speaker.Named -> who.nickname
    Speaker.Nobody -> "nobody"
}

/**
 * How large the card in play is drawn: as tall as the block beside the words, short of a
 * share of the rail's width the words need — and not at all when the block is shorter than
 * a card in your own hand, which is the size a face stops being readable at.
 */
private fun railCardFor(block: Dp, width: Dp, share: Float = RailCardShare): CardScale? {
    val tallest = if (block.isFinite) block else RailCard.height
    val height = minOf(tallest, width * share * (RailCard.height / RailCard.width))
    if (height < RailCard.height) return null
    return CardScale(height * (RailCard.width / RailCard.height), height)
}

/**
 * How large each of the two aimed cards is drawn: the same column, split between them.
 *
 * Two differences from the single card. The pair has to leave height for the line naming each
 * card's owner and slot, so [caption] comes off the block first. And its floor is the tap
 * target rather than a hand's card — nothing here is tapped, and a face this small is still a
 * rank and a colour, which is what the player is comparing. Below that the pair is dropped
 * whole rather than drawn as two slivers.
 */
private fun railPairFor(block: Dp, width: Dp, caption: Dp, share: Float = RailCardShare): CardScale? {
    val tallest = (if (block.isFinite) block else RailCard.height) - caption
    val each = (width * share - SwapMarkWidth - Half * 2) / 2
    val height = minOf(tallest, each * (RailCard.height / RailCard.width))
    if (height < TapTarget) return null
    return CardScale(height * (RailCard.width / RailCard.height), height)
}

/**
 * The two cards a Jack or a Queen is pointed at, side by side, with the mark that says they
 * exchange places between them.
 *
 * Each end is drawn whether or not it has been chosen, so the column is the same shape from
 * the moment the action starts to the moment it resolves — and an empty end is itself the
 * clearest statement of what the table is waiting for. A chosen card wears the same gold ring
 * it wears on the felt, which is what ties the two pictures together.
 */
@Composable
private fun AimedPair(aim: Aim, scale: CardScale, caption: Dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(Half)) {
        AimSlot(aim.first, scale, caption, stringResource(Res.string.rail_aim_first))
        // Against the middle of the cards, not the middle of the row: the captions hang below
        // them, and a mark centred on the whole column sits under the cards it is between.
        Box(modifier = Modifier.height(scale.height), contentAlignment = Alignment.Center) {
            SwapMark()
        }
        AimSlot(aim.second, scale, caption, stringResource(Res.string.rail_aim_second))
    }
}

/**
 * One end of the aim: the card if it has been chosen, and under it whose hand it is in.
 *
 * The face is whatever the view carried, which is the projection's decision and not this
 * screen's: a Queen looks before it swaps and its targets arrive face-up, a Jack does not and
 * they arrive as backs. Drawing the card as it comes is what stops the rail becoming a second
 * place the redaction rule has to be right in.
 */
@Composable
private fun AimSlot(aimed: AimedCard?, scale: CardScale, caption: Dp, waiting: String) {
    val width = maxOf(scale.width, TapTarget)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (aimed == null) {
            EmptySlot(scale)
        } else {
            // Spoken as the card itself, prefixed — never the bare phrase the felt uses for
            // the same card, or the two would be one node to anything counting them.
            val face = when (val card = aimed.card) {
                is CardView.Visible ->
                    stringResource(Res.string.card_described, card.card.rank.serialName, card.card.value)

                CardView.Hidden -> stringResource(Res.string.card_face_down)
            }
            CardFace(
                card = aimed.card,
                scale = scale,
                state = CardState(chosen = true),
                label = stringResource(Res.string.rail_aiming_at, face),
            )
        }
        Text(
            text = aimed
                ?.let { stringResource(Res.string.card_position, speakerName(it.who), it.slot) }
                ?: waiting,
            fontSize = AimSize,
            lineHeight = AimSize * LogLineFactor,
            color = Rail.inkDim,
            textAlign = TextAlign.Center,
            maxLines = AimCaptionLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(width).height(caption),
        )
    }
}

/** An end nothing has been chosen for: a card's outline, drawn open. */
@Composable
private fun EmptySlot(scale: CardScale) {
    val empty = stringResource(Res.string.rail_aim_empty)
    val ink = Rail.inkDim
    // An outline standing in for a card wears a card's corner, not a chip's — see [ART_CORNER].
    val corner = with(LocalDensity.current) { scale.corner.toPx() }
    val dash = with(LocalDensity.current) { EmptyDash.toPx() }
    val stroke = with(LocalDensity.current) { EmptyStroke.toPx() }

    Box(
        // The same footprint a card claims, so choosing one does not re-pitch the row.
        modifier = Modifier
            .size(maxOf(scale.width, TapTarget), maxOf(scale.height, TapTarget))
            .semantics { contentDescription = empty },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(scale.width, scale.height)
                .drawBehind {
                    drawRoundRect(
                        color = ink,
                        cornerRadius = CornerRadius(corner, corner),
                        style = Stroke(
                            width = stroke,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash)),
                        ),
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "?", fontSize = PromptSize, color = ink)
        }
    }
}

/**
 * Two arrows passing, drawn rather than set.
 *
 * A character would do it in one line, and would be the one glyph in the app whose presence
 * in the bundled face nothing checks — `FontCoverageTest` reads the translations, not the
 * source. Eight strokes of a canvas cannot go missing on somebody's phone.
 */
@Composable
private fun SwapMark() {
    val ink = Rail.inkDim
    Canvas(modifier = Modifier.size(SwapMarkWidth, SwapMarkHeight)) {
        val head = size.height * SwapHead
        val hair = size.height * SwapHair
        // The upper arrow points right and the lower one left, which is what a pair passing
        // looks like — two arrows the same way round is a queue.
        listOf(size.height * SwapUpper to true, size.height * SwapLower to false)
            .forEach { (y, rightwards) ->
                val from = if (rightwards) 0f else size.width
                val to = if (rightwards) size.width else 0f
                val barb = if (rightwards) to - head else to + head
                drawLine(ink, Offset(from, y), Offset(to, y), strokeWidth = hair)
                drawLine(ink, Offset(to, y), Offset(barb, y - head), strokeWidth = hair)
                drawLine(ink, Offset(to, y), Offset(barb, y + head), strokeWidth = hair)
            }
    }
}

/**
 * What the player can do, in one row.
 *
 * A stakes move — Call Vinto — used to sit alone under a rule and the word "or", which was
 * a second row the rail had to find room for on exactly the turns it also had a prompt and
 * a rule to show. It shares the row now, in its own tone: the gold is what says "this is a
 * different kind of thing to press", and a row is what keeps the foot one height on every
 * turn. A lone choice keeps the whole row, so the one thing to press is the biggest.
 */
@Composable
private fun Choices(table: Table, side: Boolean, onMove: (Move) -> Unit) {
    val choices = table.choices
    if (choices.isEmpty()) return

    if (choices.size == 1) {
        ChoiceButton(choices.single(), onMove)
        return
    }

    // Side by side while the words fit, stacked when they do not — and *measured*, in this
    // language, for these buttons, at this width.
    //
    // Splitting the width evenly is right under a phone's felt, where three choices get about
    // 119 dp each. Beside a desktop felt the same three share a rail and get 89, and
    // `GameButton`'s two-line allowance then breaks the longest *word* across them: the discard
    // button read "DISCAR / D". A word split in half is worse than any layout that could have
    // avoided it.
    //
    // The threshold is not a number. Three things move independently — the locale (nineteen of
    // them, and "DISCARD" is "VERWERFEN" in one of them), the choice set (whatever the turn is
    // asking), and the room available — so `roomForLabels` asks the button itself how much width
    // *these* words need, and the answer follows a translation without anybody editing it.
    //
    // Stacked, each is the full width of the rail, which also makes them the same size as each
    // other: three buttons of three different widths was the other half of the report.
    // **Only where the rail stands at the side**, which is the other half of the rule and the
    // half `RailFitsTest` insisted on. Under a phone's felt the rail is wide and *short*: there
    // is width to share and no height to stack into, so four buttons in a column at a doubled
    // system font walk straight off the bottom of the screen — which is the exact regression
    // that test was written for, three screenshots of a button half under the edge. Beside the
    // felt the scarcity is the other way round, and stacking is the answer rather than the
    // problem. So the arrangement follows the rail's own shape, and the measurement below only
    // decides whether the side rail needs it.
    // Beside the felt, whole labels: there is height to stack into, so a label is never broken.
    val room = roomForLabels(choices.map { labelled(it.label) }, whole = side)
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val each = (maxWidth - Half * (choices.size - 1)) / choices.size
        if (!side || each >= room) {
            // One size, not just one width. `weight(1f)` already made them equally wide, and
            // they still came out different *heights* the moment one label wrapped to two lines
            // and its neighbour did not — "SWAP CARDS" standing a head taller than "DISCARD"
            // beside it. Buttons sitting together read as a set, and a set with one member
            // taller than the rest reads as a mistake. `IntrinsicSize.Min` measures the row
            // against its tallest member and `fillMaxHeight` brings the others up to it.
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(Half),
            ) {
                choices.forEach { choice ->
                    ChoiceButton(choice, onMove, Modifier.weight(1f).fillMaxHeight())
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Half),
            ) {
                choices.forEach { choice ->
                    ChoiceButton(choice, onMove, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/**
 * The card in the rail — and whether the rail has a column for one at all.
 *
 * The column is there for the whole of the viewer's turn and for none of anybody else's. A
 * card being decided about is public, so it *could* show a bot's Queen being aimed; what
 * that bought was a rail that changed shape with every move of every seat, a card for one
 * beat and an empty slot the next, which the player read as the layout jumping — and on a
 * bot's turn the words are the thing to read, so they take the width.
 *
 * Within the turn it always holds a card, so nothing beside it moves from one move to the
 * next: the pending card while there is one, whoever's — a bot's tossed-in Jack resolving
 * inside the viewer's window is still the viewer's turn; otherwise the top of the pile,
 * which is the card the turn is about before a draw (take it?), after a swap (declare it?)
 * and in the toss-in window (match it?); and the deck's back when the pile is empty, which
 * is the first turn of a round and the card about to be drawn.
 *
 * **Except while the table is asking the player to point at something**, when there is no
 * card in the column: an aim takes it (the two cards being swapped), and a seat question
 * gives it up entirely, because by then the player has drawn the card, read it and chosen to
 * play it and the rail would be showing it a fourth time. What is undecided is who, and the
 * answer to that is on the foot.
 */
internal fun railCard(view: PlayerView, table: Table): CardView? {
    if (table.aim != null || table.seats.isNotEmpty()) return null
    if (!yourTurn(view)) return null
    // A peek that has found its card shows *that* card, not the one that bought the look:
    // the rail was holding up the 8 and saying what an 8 does while the Joker it had just
    // turned over lay on the felt unexplained (product owner). The look is the news.
    peekedCard(view)?.let { return it }
    (view.pendingAction?.card as? CardView.Visible)?.let { return it }
    // With nothing in play the rail falls back to the top of the pile — but only face up
    // when it is a card the player could take. A played 8 held up large beside "Your turn",
    // with "Eight, worth 8: Peek at one of your own cards" under it, read as a card on
    // offer, and it was not (product owner). The pile itself keeps its face: what went down
    // is public and the whole table reads it. The rail is about *your* choice.
    val top = view.discardTop ?: return CardView.Hidden
    return if (top.actionIsLive()) CardView.Visible(top) else CardView.Hidden
}

/**
 * The card a 7, 8, 9 or 10 has turned up for the viewer, while it is being looked at.
 *
 * **Read off the hand, not off the target.** `PendingTargetView.card` looks like the obvious
 * source and is always a back for a peek: the engine records a card on an `ActionTarget` only
 * when it has to *move* one, so a Jack's two targets carry cards and a Seven's carry nothing.
 * Asking the target was how this came to be dead code — the rail went on holding up the Seven
 * while the Joker it had turned over lay on the felt unexplained, which is the thing this
 * function exists to stop (product owner, a second time).
 *
 * The hand is where the projection puts a peeked card, for exactly one target and exactly one
 * viewer (`revealedByCurrentAction`), so a face found here is one the redaction already
 * allowed. Filling the target in instead would have been a change to `GameState` — and to
 * every hash in the frozen corpus.
 */
private fun peekedCard(view: PlayerView): CardView.Visible? {
    val pending = view.pendingAction ?: return null
    if (pending.playerId != view.viewerId) return null
    val looks = pending.targetType == TargetType.OWN_CARD || pending.targetType == TargetType.OPPONENT_CARD
    if (!looks) return null
    return pending.targets.firstNotNullOfOrNull { target ->
        val seat = view.players.firstOrNull { it.id == target.playerId }
        seat?.cards?.getOrNull(target.position) as? CardView.Visible
    }
}

/**
 * Whether the turn on the table is the viewer's. Inside a toss-in window the turn is still
 * the one that opened it, whoever is throwing a card into it; setup and scoring are nobody's.
 */
private fun yourTurn(view: PlayerView): Boolean {
    if (view.phase != GamePhase.PLAYING && view.phase != GamePhase.FINAL) return false
    val owner = view.activeTossIn?.originalPlayerIndex ?: view.currentPlayerIndex
    return view.players.getOrNull(owner)?.id == view.viewerId
}

/**
 * The line under the heading that answers the player's last touch.
 *
 * Two things share the slot because they are the same question — "did that work?" — and only
 * one of them can be true at a time: a move is either still on the wire or it has come back
 * with a reason. A spinner anywhere else on the table would compete with the cards for the
 * eye it is trying to reassure.
 */
@Composable
private fun ColumnScope.Answer(state: TableState) {
    // The engine's own words, not a translation of them. A refusal is nearly always a rule
    // the player has not met yet, and paraphrasing it here would put a second, drifting copy
    // of the rules in the UI.
    state.refusal?.let { reason ->
        Text(
            text = reason,
            fontSize = DetailSize,
            color = WarnInk,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }

    if (state.sending) {
        BusyLine(
            label = stringResource(Res.string.table_sending),
            modifier = Modifier.align(Alignment.CenterHorizontally),
            colour = Rail.inkDim,
        )
    }
}

/**
 * The prompt; under it, the card the rail is showing, explained — its name, its points and
 * what it does, for whoever's card it is — and, when it is still worth saying, the rule.
 */
@Composable
internal fun Heading(table: Table, teaching: Boolean, shown: Card?) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = asked(table.prompt),
            fontSize = PromptSize,
            fontWeight = FontWeight.Bold,
            color = Rail.ink,
            // The rail's de-facto heading: a reader jumping by headings lands on what the
            // table is asking, which is the one line that matters.
            modifier = Modifier.semantics { heading() },
        )
        shown?.let { card ->
            Text(text = cardLine(card.rank), fontSize = DetailSize, color = Rail.inkDim)
        }
        // A rule that only repeats what the card line just said is not said twice.
        table.detail
            ?.takeIf { !(it is Detail.WhatTheCardDoes && shown != null) }
            ?.takeIf { worthSaying(it, teaching) }
            ?.let { detail -> Text(text = detailed(detail), fontSize = DetailSize, color = Rail.inkDim) }
    }
}

/** What a card is and does, in one line: the help sheet's row, without the sheet. */
@Composable
private fun cardLine(rank: Rank): String {
    val config = getCardConfig(rank)
    // The value and the action come off CardConfig — a number and an enum are rules. The WORDS
    // come from CardWords, or this line is English inside a translated screen.
    return if (config.action == null) {
        stringResource(Res.string.rail_card_plain, cardName(rank), config.value)
    } else {
        stringResource(Res.string.rail_card_action, cardName(rank), config.value, cardLong(rank))
    }
}

/**
 * Whether to spell out the rule under the prompt.
 *
 * A hint is worth two lines of the rail the first couple of times a player meets it and is
 * noise on the twentieth — "Wrong rank costs you a penalty card" under every toss-in window
 * of every turn of every round. But dropping it outright is worse for the player it was
 * written for, so it fades on the same terms a person would offer it on: said plainly to
 * begin with, and after that only to somebody who has stopped to think.
 *
 * This is the pattern the card games worth copying use — the tutorial teaches, the table
 * gets out of the way, and help comes back on hesitation rather than on a schedule. It costs
 * nothing in layout, because the rail is a fixed height whether the line is there or not.
 *
 * Under a coach ([TableState.teaching]) it never fades: the whole point of that screen is the
 * words.
 */
@Composable
private fun worthSaying(detail: Detail, teaching: Boolean): Boolean {
    if (teaching) return true

    // Keyed by the message rather than by its words. The count now survives a translation,
    // and two hints that merely read alike in English no longer share a tally.
    val met = remember { mutableMapOf<Detail, Int>() }
    val before = met[detail] ?: 0
    var hesitated by remember(detail) { mutableStateOf(false) }

    LaunchedEffect(detail) {
        met[detail] = before + 1
        delay(HesitationMs)
        hesitated = true
    }

    return before < FreelyOffered || hesitated
}

/**
 * The turn so far, in the phone's language.
 *
 * Rendering happens here rather than in the model, which is the whole of WORDS.md §6h's change: the
 * log arrives as [Say] — what happened — and becomes words where the resources are.
 *
 * It used to be the last two lines, which on a table where a turn is a draw, a swap, a
 * toss-in window and three throws meant the toss-ins were gone before they were read. It is
 * a well now: every line the rail keeps, newest at the foot and the eye kept there, in a box
 * as deep as the rail can afford — the same depth on the same phone from one move to the next,
 * so the buttons under it never move — and what there is to read scrolls inside it rather than
 * pushing the controls down. The newest line is written in full ink and the rest dimmed, so
 * the eye finds "now" without a marker.
 *
 * Nothing is folded out for repeating the prompt. There was an `echoedBy` rule that dropped
 * "You drew the 5" when the heading already said it, and the box then showed the move
 * *before* yours as its newest line, which read as the log having missed your draw.
 */
@Composable
private fun RecentActions(recent: List<Say>, most: Int = LogLines) {
    if (recent.isEmpty()) return

    // A plain loop rather than `map`: `said` is a composable, and a composable call inside
    // a non-inline lambda is not one the compiler will accept.
    // One line per actor rather than per action, joined by an arrow: "Don draws a card, then
    // Don swaps card 1, dropping the 9" is one thing that happened, read once, and the line
    // grows in place as the turn goes on rather than pushing the last one up. A move by
    // somebody else, or the table's own line, starts a new one.
    val lines = ArrayList<Pair<Speaker, String>>(recent.size)
    for (entry in recent) lines += entry.who to said(entry)
    val rendered = foldedByActor(lines)

    // Sized in lines rather than points, so a large system font gets the same number of
    // lines rather than fewer, clipped.
    val lineHeight = with(LocalDensity.current) { (DetailSize * LogLineFactor).toDp() }
    val listState = rememberLazyListState()

    // As deep as the rail can afford, and absent below one line: the rail hands this box what
    // its prompt and its buttons leave, and a log with no room for a line is a strip of well
    // with nothing readable in it. On one phone that is always the same number of lines,
    // which is what keeps the box a fixed size from one move to the next.
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val lines = logDepth(room = maxHeight, lineHeight = lineHeight, most = most)
        if (lines < 1) return@BoxWithConstraints

        KeepTheTailInView(listState, rendered, lines)

        Surface(
            shape = RoundedCornerShape(LogCorner),
            color = Rail.line.copy(alpha = LogWell),
            modifier = Modifier.fillMaxWidth().markedAs(LocalStage.current, Target.LOG),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(lineHeight * lines + Gap * 2)
                    .padding(horizontal = Gap, vertical = Gap),
            ) {
                // Keyed on the line itself, so a run of moves folding into one growing line
                // re-uses that item rather than re-keying everything under it by position.
                itemsIndexed(rendered, key = { index, moves -> "$index:$moves" }) { index, moves ->
                    LogLine(moves, ink = if (index == rendered.lastIndex) Rail.ink else Rail.inkDim)
                }
            }
        }
    }
}

/**
 * One line of the well: one actor's moves in order, with a drawn arrow between each two.
 *
 * The arrow points the way the line reads, so it turns round in Arabic, Hebrew and Urdu. It is
 * drawn in the line's own ink, newest or dimmed, so it never stands out from the words it joins.
 */
@Composable
private fun LogLine(moves: List<String>, ink: Color) {
    val backwards = LocalLayoutDirection.current == LayoutDirection.Rtl
    val arrow = remember(ink, backwards) {
        mapOf(
            ThenMark to InlineTextContent(
                Placeholder(ThenWide, ThenHigh, PlaceholderVerticalAlign.TextCenter),
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    scale(scaleX = if (backwards) -1f else 1f, scaleY = 1f) {
                        inset(
                            left = size.width * ThenBefore,
                            top = 0f,
                            right = size.width * ThenAfter,
                            bottom = 0f,
                        ) { drawArrowRight(ink) }
                    }
                }
            },
        )
    }
    Text(
        text = logLine(moves),
        inlineContent = arrow,
        fontSize = DetailSize,
        lineHeight = DetailSize * LogLineFactor,
        color = ink,
        modifier = Modifier.testTag(LOG_LINE),
    )
}

/**
 * A folded line as text: the moves, and between each two the arrow's place, which a screen
 * reader hears as a comma. Apart from [LogLine] so a test can read what is said without drawing.
 */
internal fun logLine(moves: List<String>): AnnotatedString = buildAnnotatedString {
    moves.forEachIndexed { index, move ->
        if (index > 0) {
            appendInlineContent(ThenMark, ThenSaid)
            append(' ')
        }
        append(move)
    }
}

/**
 * Keeps the eye at the foot of the well. Two corrections, both of which it took a phone to see.
 *
 * **Keyed on what the lines say, not how many there are.** A run of moves by one actor folds
 * into a single line that grows in place, so the newest thing that happened routinely changes
 * no count at all — an effect watching `rendered.size` sat still through the whole of a
 * player's own turn, which is precisely the turn they are reading the box for.
 *
 * **And scrolled past the last line's top, not to it.** `animateScrollToItem` lands an item's
 * *head* at the head of the box; for a folded line deeper than the well that shows its oldest
 * half and leaves the newest below the fold. Whatever hangs over is taken off afterwards,
 * once the layout knows how tall the line came out — it cannot be known before, because it
 * depends on where the words wrapped.
 *
 * [depth] is a key rather than an argument: the well is re-measured when the rail's other
 * tenants change, and a box that just got shallower has to find its foot again.
 */
@Composable
private fun KeepTheTailInView(state: LazyListState, rendered: List<List<String>>, depth: Int) {
    LaunchedEffect(rendered, depth) {
        if (rendered.isEmpty()) return@LaunchedEffect
        state.animateScrollToItem(rendered.lastIndex)
        val info = state.layoutInfo
        val overhang = info.visibleItemsInfo.lastOrNull()
            ?.let { it.offset + it.size - info.viewportEndOffset }
            ?: 0
        if (overhang > 0) state.animateScrollBy(overhang.toFloat())
    }
}

/**
 * The current turn and the one before it: what was just played, and what the player was
 * answering when they played it. The rail kept every line of the round, which was a
 * transcript to scroll rather than a table to read; a turn starts where somebody draws or
 * takes from the pile, and the deal's own line starts everything over.
 *
 * **A copy, never a view.** [recent] is the stage's stepped log, a snapshot list the stage
 * appends to as frames play, and the rail's box is composed inside a `BoxWithConstraints` —
 * that is, during measure, some time after this ran. A `subList` of a snapshot list is a live
 * view that checks the list has not moved since it was made, and by then it had:
 * `SwapAnimationTest` met a `ConcurrentModificationException` on the first read. The fold
 * that used to follow this call copied the view by accident; this copies it on purpose.
 */
internal fun lastTurns(recent: List<Say>, turns: Int = TurnsKept): List<Say> {
    var starts = 0
    for (index in recent.indices.reversed()) {
        val entry = recent[index]
        if (entry is Say.RoundBegins) return recent.subList(index, recent.size).toList()
        if (entry is Say.Drew || entry is Say.DrewKnown || entry is Say.Took) {
            starts++
            if (starts == turns) return recent.subList(index, recent.size).toList()
        }
    }
    return recent.toList()
}

/**
 * One line per run of moves by the same actor, each line its moves in order, joined on screen by
 * [logLine]; the table's own lines stand alone. Kept as the moves rather than glued into one
 * string, because what goes between them is a drawing now and a string has no room for one.
 */
internal fun foldedByActor(lines: List<Pair<Speaker, String>>): List<List<String>> {
    val folded = ArrayList<List<String>>(lines.size)
    var lastWho: Speaker? = null
    for ((who, line) in lines) {
        if (folded.isNotEmpty() && who != Speaker.Nobody && who == lastWho) {
            folded[folded.lastIndex] = folded.last() + line
        } else {
            folded.add(listOf(line))
        }
        lastWho = who
    }
    return folded
}

/** How many lines of log fit in [room], up to the well's greatest depth; none when less than one. */
private fun logDepth(room: Dp, lineHeight: Dp, most: Int): Int =
    if (room.isFinite) minOf(most, ((room - Gap * 2) / lineHeight).toInt()) else most

/**
 * One of the turn's choices: a button across the rail, or — given its [pill] — a pill in a status
 * column, a mark over one short word ([ChoicePill]).
 */
@Composable
internal fun ChoiceButton(
    choice: Choice,
    onMove: (Move) -> Unit,
    modifier: Modifier = Modifier,
    pill: Pill? = null,
) {
    if (pill != null) {
        ChoicePill(choice, pill, onMove, modifier)
        return
    }
    GameButton(
        label = labelled(choice.label),
        tone = choice.tone.paint(),
        onClick = { onMove(choice.move) },
        // By its label, which is what the lesson knows it by — the model chose the words and
        // the coach quotes them, so a button the coach points at is the button on screen.
        modifier = modifier
            .fillMaxWidth()
            // Keyed by identity rather than by the rendered words, so the lesson still
            // finds a button after a translation. See `keyOf`.
            .markedAs(LocalStage.current, "choice:${keyOf(choice.label)}"),
        // A trophy the app draws, where a typed emoji was a different picture on every phone.
        leadingContent = if (choice.tone == Tone.STAKES) {
            { StakesMark() }
        } else {
            null
        },
        // `GameButton` already draws a spinner in the label's place and swallows its own taps;
        // this is the one line that was missing to make the in-flight move visible on the
        // control the player pressed. Read off the stage for the same reason the card does.
        busy = LocalStage.current.acting,
    )
}

/**
 * A choice as a pill in a status column: the move's own mark over one short word, in the move's own
 * tone, and spoken by its whole name ([pillSaid]) — "Swap Cards" for a pill that says SWAP, which is
 * what the rail's button for it is called, so the lesson and Voice Control know it by one name.
 */
@Composable
private fun ChoicePill(choice: Choice, pill: Pill, onMove: (Move) -> Unit, modifier: Modifier) {
    val word = stringResource(pill.word)
    GameButton(
        label = word,
        said = pillSaid(word, labelled(choice.label)),
        tone = choice.tone.paint(),
        onClick = { onMove(choice.move) },
        // Marked as the rail's button is, so the lesson's pointer finds it wherever it stands.
        modifier = modifier
            .size(PillWide, PillHigh)
            .markedAs(LocalStage.current, "choice:${keyOf(choice.label)}"),
        // A drawn mark in the ink the button wears, dimmed with it, and silent: the word under it
        // and the name it is spoken by already say what it is.
        leadingContent = {
            Icon(painterResource(pill.icon), contentDescription = null, modifier = Modifier.size(PillMark))
        },
        stacked = true,
        compact = true,
        busy = LocalStage.current.acting,
    )
}

/**
 * The round-ending call's picture, in whatever ink the button is wearing, dimmed with it when
 * the button cannot be pressed. Silent, as `GameButton` asks of anything before its label.
 */
@Composable
private fun StakesMark() {
    TextMark(fontSize = StakesMarkSize, ink = LocalContentColor.current) { drawTrophy(it) }
}

/** The model says what kind of move it is; this says what that kind looks like. */
internal fun Tone.paint(): ButtonTone = when (this) {
    Tone.PLAY -> ButtonTone.PLAY
    Tone.KEEP -> ButtonTone.KEEP
    Tone.NEUTRAL -> ButtonTone.NEUTRAL
    Tone.STAKES -> ButtonTone.STAKES
    Tone.DECLARE -> ButtonTone.DECLARE
}

/**
 * The answers to "name a player", in the same place and the same dress as the ranks.
 *
 * Both moves that name a person — an Ace, and the coalition choosing whose hand it plays —
 * could only be answered by tapping a seat's plate out on the felt, and nothing said so. The
 * rail asked "Who draws a card?" and offered one button, "Put it down", so the screen read as
 * a question whose only answer was to give up. The plates are still tappable and are still the
 * better gesture; this is the part that says there is a choice.
 *
 * Amber, like the rank grid, because it is the same verb: name one of these, and the game
 * moves. Never a row wider than three, which is all there ever are — the table is four seats
 * and one of them is always excluded, the player themselves for an Ace and the caller for the
 * coalition.
 */
@Composable
internal fun SeatGrid(
    seats: List<SeatChoice>,
    onMove: (Move) -> Unit,
    /** In the narrow stack, where three to a row would cut the portraits: as many as hold one whole. */
    narrow: Boolean = false,
) {
    if (seats.isEmpty()) return
    if (!narrow) {
        SeatRows(seats, onMove, SEATS_PER_ROW)
        return
    }
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        SeatRows(seats, onMove, minOf(SEATS_PER_ROW, across(maxWidth, SeatLeast)))
    }
}

/** The seats on offer, [perRow] to a row. See [SeatGrid]. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeatRows(seats: List<SeatChoice>, onMove: (Move) -> Unit, perRow: Int) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Half),
        verticalArrangement = Arrangement.spacedBy(Half),
        maxItemsInEachRow = perRow,
    ) {
        seats.forEach { seat ->
            // Keyed on the seat's own name rather than on what the button calls them: the
            // label reads "You" for your own seat, and "You" is not a face.
            val chosen = chosenFace(seat.nickname)
            val portrait = portraitOrNull(seat.nickname)
            val hasFace = chosen != null || portrait != null
            GameButton(
                label = speakerName(seat.who),
                tone = ButtonTone.DECLARE,
                onClick = { onMove(seat.move) },
                modifier = Modifier.weight(1f),
                compact = true,
                leadingContent = when {
                    chosen != null -> {
                        { GeneratedAvatar(chosen.traits(), chosen.ground(), SeatFaceSize) }
                    }
                    portrait != null -> {
                        { SeatFace(portrait) }
                    }
                    else -> null
                },
                // Above the name where there is a face to show, beside it where there is not:
                // a plaque with a blank space over the word is worse than a plain plaque.
                stacked = hasFace,
            )
        }
    }
}

/**
 * A seat's portrait, in the button that names them.
 *
 * The same face the plate wears on the felt, which is the point — three names in a row are
 * three words to read, and three faces are the same three seats the player has been looking
 * at all round. Silent to a screen reader: the name is right beside it.
 */
@Composable
private fun SeatFace(portrait: DrawableResource) {
    Image(
        painter = painterResource(portrait),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(SeatFaceSize).clip(CircleShape),
    )
}

/**
 * The answers to "name a card", as evenly shared rows of plaques.
 *
 * Left to size themselves they came out as tall narrow pills — a "2" is one character wide
 * and the button was only as wide as its padding, while the height was held at a thumb.
 * Sharing each row out evenly makes them plaques instead: wider than they are tall, the same
 * size whether they say 2 or Joker, and in the same place every time.
 *
 * The King's fourteen sit seven to a row; the swap declaration's eight action ranks sit
 * four to a row, because seven-and-one is a row of plaques and an orphan. A muted chip is
 * still a chip — the King may name an actionless rank on purpose — it just does not dress
 * like the common case.
 *
 * On the claim rail the plaques are **toggles**, and a picked one is drawn held down. Nothing
 * here knows which rail it is: the model says whether a rank is in the claim, the same way it
 * says whether one is muted.
 */
@Composable
internal fun RankGrid(
    ranks: List<RankChoice>,
    stage: Stage,
    onMove: (Move) -> Unit,
    /**
     * In the narrow stack beside an open Duo's felt, a fifth of the screen across, where seven to a
     * row would be chips a third of a thumb wide: there a row holds as many as are a thumb wide each.
     */
    narrow: Boolean = false,
) {
    if (ranks.isEmpty()) return
    val designed = if (ranks.size <= ACTION_RANKS) ACTION_RANKS_PER_ROW else RANKS_PER_ROW
    if (!narrow) {
        RankRows(ranks, stage, onMove, designed)
        return
    }
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        RankRows(ranks, stage, onMove, minOf(designed, across(maxWidth, TapTarget)))
    }
}

/** The ranks on offer, [perRow] to a row. See [RankGrid]. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RankRows(ranks: List<RankChoice>, stage: Stage, onMove: (Move) -> Unit, perRow: Int) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Half),
        verticalArrangement = Arrangement.spacedBy(Half),
        maxItemsInEachRow = perRow,
    ) {
        ranks.forEach { rank ->
            GameButton(
                label = rank.rank.serialName,
                tone = if (rank.muted) ButtonTone.NEUTRAL else ButtonTone.DECLARE,
                onClick = { onMove(rank.move) },
                modifier = Modifier
                    .weight(1f)
                    .markedAs(stage, "rank:${rank.rank.serialName}"),
                compact = true,
                // Held down for as long as it is part of the claim. A claim is built rather
                // than picked now — several ranks means the card is one of them — so the rail
                // has to say which of its fourteen are in.
                selected = rank.picked,
            )
        }
    }
}

/** How many things at least [each] wide fit across [width], [Half] apart; never none. */
private fun across(width: Dp, each: Dp): Int = ((width + Half) / (each + Half)).toInt().coerceAtLeast(1)

/** Seven and seven: the fourteen ranks, in two rows that fill the rail's width. */
private const val RANKS_PER_ROW = 7

/** The swap declaration's action ranks: a grid of no more than this many is theirs. */
private const val ACTION_RANKS = 8

/** Four and four: the eight action ranks of the swap declaration. */
private const val ACTION_RANKS_PER_ROW = 4

/** Every seat question excludes exactly one of the four seats, so three is the whole row. */
private const val SEATS_PER_ROW = 3

/**
 * The portrait in a seat button.
 *
 * Large on purpose, and this is the size the web table used: the artwork is the reason a row
 * of three reads as three opponents rather than three words, and at the 26dp it started at it
 * was a coloured dot with none of the drawing in it. It sits above the name rather than beside
 * it, so the width is the tile's rather than what is left after the label.
 */
private val SeatFaceSize = 56.dp

/** The narrowest a seat button is where it is narrowed: its portrait, and the button's edge either side. */
private val SeatLeast = 64.dp

/** How many times a hint is given before it waits to be asked for. */
private const val FreelyOffered = 2

/** How long a player has to sit still before the rule appears anyway. */
private const val HesitationMs = 5_000L

/** The beading between felt and rail: thin, and the whole illusion. */
private val Seam = 2.dp
private val SeamDark = Color(0x66000000)
private val SeamShadow = Color(0x40000000)
private const val SHADOW_DEPTH = 9f

private val PromptSize = 17.sp
private val DetailSize = 13.sp
private val WarnInk = androidx.compose.ui.graphics.Color(0xFFFFA39E)
