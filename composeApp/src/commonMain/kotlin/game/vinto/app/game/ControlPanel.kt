package game.vinto.app.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isFinite
import androidx.compose.ui.unit.sp
import game.vinto.app.art.Res
import game.vinto.app.art.card_in_play
import game.vinto.app.art.table_sending
import game.vinto.app.asked
import game.vinto.app.detailed
import game.vinto.app.keyOf
import game.vinto.app.labelled
import game.vinto.app.said
import game.vinto.app.theme.BusyLine
import game.vinto.app.theme.ButtonTone
import game.vinto.app.theme.GameButton
import game.vinto.app.theme.Hairline
import game.vinto.app.theme.Rail
import game.vinto.app.theme.feltEdge
import game.vinto.client.Choice
import game.vinto.client.Detail
import game.vinto.client.Move
import game.vinto.client.RankChoice
import game.vinto.client.Say
import game.vinto.client.Table
import game.vinto.client.Target
import game.vinto.client.Tone
import game.vinto.client.echoedBy
import game.vinto.engine.CardView
import game.vinto.engine.PlayerView
import game.vinto.shapes.Card
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

private val PanelPad = 12.dp

/** The card in play, drawn in the rail: the size of a card in your own hand on a phone. */
private val RailCard = CardScale(56.dp, 78.dp)
private val Gap = 8.dp
private val Half = 4.dp
private val LogCorner = 6.dp

/** The well's depth in lines, its line pitch, and how much darker than the rail it sits. */
private const val LogLines = 3
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
     * True when the rail stands beside the felt (landscape) rather than under it. The size
     * comes from the caller's modifier either way; what this decides is which edge of the
     * rail touches the felt, and so which edge wears the moulding.
     */
    side: Boolean = false,
) {
    val table = state.table
    val stage = LocalStage.current
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
        // Three slots, because the rail is a fixed height and every box in it should be a
        // fixed size too — a table whose panels change shape between moves is a table the eye
        // has to find again after each one. The prompt is at the top, under the felt, and
        // keeps two lines' room whether or not the rule under it is still being said. The
        // choices are pinned to the foot, where a thumb rests, and share one row rather than
        // stacking: vertical room is the scarce thing on a phone and horizontal room is not.
        // Never scrolled, never pushed — a rail that scrolled to reach its second button had
        // that button half under the edge of the screen on every phone taller than the one
        // it was drawn on, and "Leave them" is not a control a player should have to go
        // looking for. The box of recent moves takes the middle at a fixed depth, and is the
        // one thing that gives way when a wrapped prompt or a large font leaves it less than
        // that: three moves ago matters less than reaching the move now.
        //
        // Scrolling survives only inside the slots, as the last resort for a doubled system
        // font: the prompt scrolls within its own room, and a King's fourteen chips within
        // theirs, and neither can push the buttons off the rail.
        val rows = table.choices.size + if (table.choices.any { it.tone == Tone.STAKES }) 1 else 0
        val crowded = table.ranks.isNotEmpty() || rows >= Crowded
        val recent = state.recent.filterNot { table.prompt.echoedBy(it) }
        val promptLine = with(LocalDensity.current) { (PromptSize * PromptLineFactor).toDp() }
        val twoLines = promptLine + with(LocalDensity.current) { (DetailSize * LogLineFactor).toDp() }
        // The head is as tall as the card it can hold, whether or not it is holding one, so
        // the log under it does not move when a card is drawn.
        val headRoom = maxOf(twoLines, RailCard.height)
        val inPlay = state.view.ownCardInPlay()

        RailSlots(
            modifier = Modifier.fillMaxWidth().padding(PanelPad),
            promptLine = promptLine,
            head = {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val room = maxHeight
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = headRoom),
                        horizontalArrangement = Arrangement.spacedBy(PanelPad),
                    ) {
                        // The card the player is deciding about, at a size its face can be
                        // read at, beside the words about it — as the web table showed it.
                        // The felt has it too, in the slot it was drawn into, at a size that
                        // says *where* it is rather than *what*; the rail is where the
                        // decision is made, and a phone has the width for both. Whole or not
                        // at all: above a rank grid the head keeps one line, and a sliver of
                        // a card is a thing that looks broken.
                        inPlay?.takeIf { room >= RailCard.height }?.let { card ->
                            CardFace(
                                card = CardView.Visible(card),
                                scale = RailCard,
                                label = stringResource(Res.string.card_in_play, card.rank.serialName),
                            )
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = headRoom)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(Gap),
                        ) {
                            Heading(table = table, teaching = state.teaching)
                            Answer(state)
                        }
                    }
                }
            },
            // The log is what happened *before* now. The prompt above is now, and the two are
            // built from the same narration, so the top line of the log was routinely the
            // heading again in smaller type — "You drew the A", under "You drew the A".
            // Counted in full-width rows rather than in buttons, because a stakes move brings
            // a rule and the word "or" down with it.
            middle = { if (!crowded) RecentActions(recent) },
            choices = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Gap),
                ) {
                    RankGrid(table.ranks, stage, onMove)
                    Choices(table, onMove)
                }
            },
        )
    }
}

/**
 * The rail's three tenants, laid out by the rule above: choices at the foot, the prompt at
 * the head, and the middle given exactly what is left. A rail with nothing to choose centres
 * its words instead — "Raph is playing", and nothing to do about it, should sit in the middle
 * rather than cling to the felt above a void.
 */
@Composable
private fun RailSlots(
    modifier: Modifier,
    /** One line of the prompt, which the choices may never take from it. */
    promptLine: Dp,
    head: @Composable () -> Unit,
    middle: @Composable () -> Unit,
    choices: @Composable () -> Unit,
) {
    Layout(
        modifier = modifier,
        content = {
            Box(modifier = Modifier.layoutId(Slot.HEAD)) { head() }
            Box(modifier = Modifier.layoutId(Slot.MIDDLE)) { middle() }
            Box(modifier = Modifier.layoutId(Slot.CHOICES)) { choices() }
        },
        measurePolicy = railPolicy(Gap, promptLine),
    )
}

/**
 * Measured in the order they are entitled to the room: the choices take what they need, short
 * of the prompt's first line — a question the player cannot see is not one they can answer,
 * whatever they can press — then the prompt takes what it needs of the rest, and the middle
 * gets the remainder.
 */
private fun railPolicy(gapBetween: Dp, promptLine: Dp) = MeasurePolicy { measurables, constraints ->
    val gap = gapBetween.roundToPx()
    val room = RailRoom(constraints, gap)

    val foot = measurables.slot(Slot.CHOICES).measure(room.leaving(promptLine.roundToPx().plusGap(gap)))
    val head = measurables.slot(Slot.HEAD).measure(room.leaving(foot.height.plusGap(gap)))
    val middle = measurables.slot(Slot.MIDDLE)
        .measure(room.leaving(foot.height.plusGap(gap) + head.height.plusGap(gap)))

    val between = if (head.height > 0 && middle.height > 0) gap else 0
    val words = head.height + between + middle.height
    val used = words + (if (words > 0 && foot.height > 0) gap else 0) + foot.height
    val railHeight = if (room.bounded) room.height else used

    layout(room.width, railHeight) {
        // Words at the head when there is something to press at the foot; centred when there
        // is not.
        val top = if (foot.height > 0) 0 else ((railHeight - words) / 2).coerceAtLeast(0)
        head.placeRelative(0, top)
        middle.placeRelative(0, top + head.height + between)
        foot.placeRelative(0, railHeight - foot.height)
    }
}

/** The rail's height, and what is left of it once some of it is spoken for. */
private class RailRoom(constraints: Constraints, private val gap: Int) {
    val width = constraints.maxWidth
    val bounded = constraints.hasBoundedHeight
    val height = constraints.maxHeight
    private val loose = constraints.copy(minWidth = width, minHeight = 0)

    fun leaving(taken: Int): Constraints =
        if (bounded) loose.copy(maxHeight = (height - taken).coerceAtLeast(0)) else loose
}

/** A slot's height plus the gap that follows it — or nothing, for a slot with nothing in it. */
private fun Int.plusGap(gap: Int) = if (this > 0) this + gap else 0

private fun List<Measurable>.slot(slot: Slot) = first { it.layoutId == slot }

private enum class Slot { HEAD, MIDDLE, CHOICES }

/** What the player can do: the ordinary moves, and a stakes move set apart under a rule. */
@Composable
private fun Choices(table: Table, onMove: (Move) -> Unit) {
    // A stakes move is set below a rule, as the web app sets Call Vinto below an "or": it is
    // not the next step in what you were doing, it is a different thing to do, and the line
    // is what stops a thumb finding it by accident.
    val (ordinary, stakes) = table.choices.partition { it.tone != Tone.STAKES }
    if (ordinary.isEmpty() && stakes.isEmpty()) return

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Gap),
    ) {
        // Side by side, sharing the width: two or three choices in one row cost the rail one
        // button's height rather than two or three, and a phone has width to spare where it
        // has no height. A lone choice keeps the whole row, so the one thing to press is the
        // biggest thing to press.
        if (ordinary.size > 1) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Half),
            ) {
                ordinary.forEach { choice ->
                    ChoiceButton(choice, onMove, Modifier.weight(1f))
                }
            }
        } else {
            ordinary.forEach { choice -> ChoiceButton(choice, onMove) }
        }

        if (stakes.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Gap),
            ) {
                Hairline(modifier = Modifier.weight(1f), colour = Rail.line)
                Text("or", fontSize = DetailSize, color = Rail.inkDim)
                Hairline(modifier = Modifier.weight(1f), colour = Rail.line)
            }
            stakes.forEach { choice -> ChoiceButton(choice, onMove) }
        }
    }
}

/**
 * The card the person holding the phone is deciding about, if there is one and it is theirs.
 *
 * Another seat's pending card is on the felt for everyone to watch; it is not this player's
 * decision, and the rail is about what this player can do.
 */
private fun PlayerView.ownCardInPlay(): Card? {
    val pending = pendingAction ?: return null
    if (pending.playerId != viewerId) return null
    return (pending.card as? CardView.Visible)?.card
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

/** The prompt, and — when it is still worth saying — the rule under it. */
@Composable
private fun Heading(table: Table, teaching: Boolean) {
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
        table.detail?.takeIf { worthSaying(it, teaching) }?.let { detail ->
            Text(text = detailed(detail), fontSize = DetailSize, color = Rail.inkDim)
        }
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
 * Rendering happens here rather than in the model, which is the whole of §6h's change: the
 * log arrives as [Say] — what happened — and becomes words where the resources are.
 *
 * It used to be the last two lines, which on a table where a turn is a draw, a swap, a
 * toss-in window and three throws meant the toss-ins were gone before they were read. It is
 * a well now: every line the rail keeps, newest at the foot and the eye kept there, in a box
 * whose height is fixed at a few lines so the buttons under it never move — what there is to
 * read scrolls inside it rather than pushing the controls down. The newest line is written in
 * full ink and the rest dimmed, so the eye finds "now" without a marker.
 *
 * The caller drops any line that only repeats the prompt, by the model's own `echoedBy` rule.
 * That was briefly a comparison of two *rendered* strings — which worked by coincidence, an
 * [Ask] and a [Say] being different types that happen to produce the same words in English.
 * As a rule it survives a language where they do not.
 */
@Composable
private fun RecentActions(recent: List<Say>) {
    if (recent.isEmpty()) return

    // A plain loop rather than `map`: `said` is a composable, and a composable call inside
    // a non-inline lambda is not one the compiler will accept.
    val rendered = ArrayList<String>(recent.size)
    for (entry in recent) rendered += said(entry)

    // Sized in lines rather than points, so a large system font gets the same number of
    // lines rather than fewer, clipped.
    val lineHeight = with(LocalDensity.current) { (DetailSize * LogLineFactor).toDp() }
    val listState = rememberLazyListState()
    LaunchedEffect(rendered.size) {
        if (rendered.isNotEmpty()) listState.animateScrollToItem(rendered.lastIndex)
    }

    // As deep as the rail can afford, up to the well's full depth, and absent below one line:
    // the rail hands this box what its prompt and its buttons leave, and a log with no room
    // for a line is a strip of well with nothing readable in it.
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val lines = logDepth(room = maxHeight, lineHeight = lineHeight)
        if (lines < 1) return@BoxWithConstraints

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
                itemsIndexed(rendered) { index, line ->
                    Text(
                        text = line,
                        fontSize = DetailSize,
                        lineHeight = DetailSize * LogLineFactor,
                        color = if (index == rendered.lastIndex) Rail.ink else Rail.inkDim,
                    )
                }
            }
        }
    }
}

/** How many lines of log fit in [room], up to the well's full depth; none when less than one. */
private fun logDepth(room: Dp, lineHeight: Dp): Int =
    if (room.isFinite) minOf(LogLines, ((room - Gap * 2) / lineHeight).toInt()) else LogLines

@Composable
private fun ChoiceButton(choice: Choice, onMove: (Move) -> Unit, modifier: Modifier = Modifier) {
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
        leading = if (choice.tone == Tone.STAKES) "🏆" else null,
    )
}

/** The model says what kind of move it is; this says what that kind looks like. */
private fun Tone.paint(): ButtonTone = when (this) {
    Tone.PLAY -> ButtonTone.PLAY
    Tone.KEEP -> ButtonTone.KEEP
    Tone.NEUTRAL -> ButtonTone.NEUTRAL
    Tone.STAKES -> ButtonTone.STAKES
    Tone.DECLARE -> ButtonTone.DECLARE
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
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RankGrid(ranks: List<RankChoice>, stage: Stage, onMove: (Move) -> Unit) {
    if (ranks.isEmpty()) return

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Half),
        verticalArrangement = Arrangement.spacedBy(Half),
        maxItemsInEachRow = if (ranks.size <= 8) ACTION_RANKS_PER_ROW else RANKS_PER_ROW,
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
            )
        }
    }
}

/** Seven and seven: the fourteen ranks, in two rows that fill the rail's width. */
private const val RANKS_PER_ROW = 7

/** Four and four: the eight action ranks of the swap declaration. */
private const val ACTION_RANKS_PER_ROW = 4

/** The number of full-width rows that leaves the rail no room for anything else. */
private const val Crowded = 3

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
