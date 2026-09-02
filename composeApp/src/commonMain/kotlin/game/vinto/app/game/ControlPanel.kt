package game.vinto.app.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isFinite
import androidx.compose.ui.unit.sp
import game.vinto.app.art.Res
import game.vinto.app.art.card_in_play
import game.vinto.app.art.rail_card_action
import game.vinto.app.art.rail_card_plain
import game.vinto.app.art.table_sending
import game.vinto.app.asked
import game.vinto.app.detailed
import game.vinto.app.keyOf
import game.vinto.app.labelled
import game.vinto.app.said
import game.vinto.app.theme.BusyLine
import game.vinto.app.theme.ButtonTone
import game.vinto.app.theme.GameButton
import game.vinto.app.theme.Rail
import game.vinto.app.theme.feltEdge
import game.vinto.client.Choice
import game.vinto.client.Detail
import game.vinto.client.Label
import game.vinto.client.Move
import game.vinto.client.RankChoice
import game.vinto.client.Say
import game.vinto.client.Speaker
import game.vinto.client.Table
import game.vinto.client.Target
import game.vinto.client.Tone
import game.vinto.client.echoedBy
import game.vinto.engine.CardView
import game.vinto.engine.PlayerView
import game.vinto.shapes.Card
import game.vinto.shapes.Rank
import game.vinto.shapes.getCardConfig
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

private val PanelPad = 12.dp

/** One row of buttons: the room the foot keeps whether or not there is anything to press. */
private val FootRow = 50.dp

/** The card in play, drawn in the rail: no smaller than a card in your own hand on a phone. */
private val RailCard = CardScale(56.dp, 78.dp)

/** The most of the rail's width the card may take; the words beside it need the rest. */
private const val RailCardShare = 0.4f
private val Gap = 8.dp
private val Half = 4.dp
private val LogCorner = 6.dp

/** Between two things one actor did in one turn. */
private const val LogJoin = " ➜ "

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
     * True when the rail stands beside the felt (landscape) rather than under it. The size
     * comes from the caller's modifier either way; what this decides is which edge of the
     * rail touches the felt, and so which edge wears the moulding.
     */
    side: Boolean = false,
) {
    val table = state.table
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
        val crowded = table.ranks.isNotEmpty()
        val recent = lastTurns(state.recent).filterNot { table.prompt.echoedBy(it) }
        val density = LocalDensity.current
        val promptLine = with(density) { (PromptSize * PromptLineFactor).toDp() }
        val twoLines = promptLine + with(density) { (DetailSize * LogLineFactor).toDp() }
        val inPlay = cardTheRailIsAbout(state.view, table)

        RailBody(state, table, inPlay, recent, crowded, promptLine, twoLines, onMove)
    }
}

/** The two columns and the foot: everything the rail draws, laid out by the rule above. */
@Suppress("LongParameterList")
@Composable
private fun RailBody(
    state: TableState,
    table: Table,
    inPlay: Card?,
    recent: List<Say>,
    crowded: Boolean,
    promptLine: Dp,
    twoLines: Dp,
    onMove: (Move) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(PanelPad)) {
        val rail = maxHeight
        val footCap =
            if (rail.isFinite) (rail - promptLine - Gap).coerceAtLeast(promptLine) else Dp.Unspecified

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(Gap),
        ) {
            RailBlock(state, table, inPlay, recent, crowded, twoLines, modifier = Modifier.weight(1f))
            RailFoot(table, footCap, onMove)
        }
    }
}

/** The card being decided about, and beside it the prompt over the box of recent moves. */
@Suppress("LongParameterList")
@Composable
private fun RailBlock(
    state: TableState,
    table: Table,
    inPlay: Card?,
    recent: List<Say>,
    crowded: Boolean,
    twoLines: Dp,
    modifier: Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val block = maxHeight
        val cardScale = railCardFor(block, maxWidth)
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
            // The column is kept whether or not there is a card in it: an empty slot where the
            // card goes, so the words beside it are the same width on every turn. A column that
            // came and went with the card moved the prompt and the log sideways on every move.
            if (cardScale != null) {
                if (inPlay != null) {
                    CardFace(
                        card = CardView.Visible(inPlay),
                        scale = cardScale,
                        label = stringResource(Res.string.card_in_play, inPlay.rank.serialName),
                    )
                } else {
                    EmptySlot(cardScale, "")
                }
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
                    Heading(table = table, teaching = state.teaching, shown = inPlay)
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
 * The rank grid, when there is one, and the choices under it — pinned, and never the whole
 * rail. It keeps one row's room even with nothing to press — "Raph is playing" — so the block
 * above it, and the log in it, are the same size on every turn of the round: a box that grew
 * when the buttons went and shrank when they came back was the one thing still moving.
 */
@Composable
private fun RailFoot(table: Table, footCap: Dp, onMove: (Move) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = FootRow, max = footCap)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Gap),
    ) {
        RankGrid(table.ranks, LocalStage.current, onMove)
        Choices(table, onMove)
    }
}

/**
 * How large the card in play is drawn: as tall as the block beside the words, short of a
 * share of the rail's width the words need — and not at all when the block is shorter than
 * a card in your own hand, which is the size a face stops being readable at.
 */
private fun railCardFor(block: Dp, width: Dp): CardScale? {
    val tallest = if (block.isFinite) block else RailCard.height
    val height = minOf(tallest, width * RailCardShare * (RailCard.height / RailCard.width))
    if (height < RailCard.height) return null
    return CardScale(height * (RailCard.width / RailCard.height), height)
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
private fun Choices(table: Table, onMove: (Move) -> Unit) {
    val choices = table.choices
    if (choices.isEmpty()) return

    if (choices.size > 1) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Half),
        ) {
            choices.forEach { choice ->
                ChoiceButton(choice, onMove, Modifier.weight(1f))
            }
        }
    } else {
        ChoiceButton(choices.single(), onMove)
    }
}

/**
 * The card the prompt is about, if the rail can show one.
 *
 * Whoever's it is: a card being decided about is public — the rules have a drawn card
 * revealed — and "Raph is playing" beside the Queen he is aiming says more than the words
 * alone. In a toss-in window, the card that went down: "the 4 went down" beside a 4 is the
 * whole question. And before anything is drawn, the card on offer from the pile, when the
 * prompt offers it: the decision then is *about* that card.
 * It used to be the viewer's own pending card and nothing else, which read as the rail
 * sometimes showing a card and sometimes not, for no reason a player could see.
 */
private fun cardTheRailIsAbout(view: PlayerView, table: Table): Card? {
    (view.pendingAction?.card as? CardView.Visible)?.let { return it.card }
    // A toss-in window is about the card that went down; so is an offer to take it.
    val aboutThePile = view.activeTossIn != null || table.choices.any { it.label is Label.UseFromPile }
    if (aboutThePile) return view.discardTop
    return null
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
private fun Heading(table: Table, teaching: Boolean, shown: Card?) {
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
    return if (config.action == null) {
        stringResource(Res.string.rail_card_plain, config.name, config.value)
    } else {
        stringResource(Res.string.rail_card_action, config.name, config.value, config.longDescription)
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
 * as deep as the rail can afford — the same depth on the same phone from one move to the next,
 * so the buttons under it never move — and what there is to read scrolls inside it rather than
 * pushing the controls down. The newest line is written in full ink and the rest dimmed, so
 * the eye finds "now" without a marker.
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
    // One line per actor rather than per action, joined by an arrow: "Don draws a card ➜
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
    LaunchedEffect(rendered.size) {
        if (rendered.isNotEmpty()) listState.animateScrollToItem(rendered.lastIndex)
    }

    // As deep as the rail can afford, and absent below one line: the rail hands this box what
    // its prompt and its buttons leave, and a log with no room for a line is a strip of well
    // with nothing readable in it. On one phone that is always the same number of lines,
    // which is what keeps the box a fixed size from one move to the next.
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

/**
 * The current turn and the one before it: what was just played, and what the player was
 * answering when they played it. The rail kept every line of the round, which was a
 * transcript to scroll rather than a table to read; a turn starts where somebody draws or
 * takes from the pile, and the deal's own line starts everything over.
 */
internal fun lastTurns(recent: List<Say>, turns: Int = TurnsKept): List<Say> {
    var starts = 0
    for (index in recent.indices.reversed()) {
        val entry = recent[index]
        if (entry is Say.RoundBegins) return recent.subList(index, recent.size)
        if (entry is Say.Drew || entry is Say.DrewKnown || entry is Say.Took) {
            starts++
            if (starts == turns) return recent.subList(index, recent.size)
        }
    }
    return recent
}

/** One line per run of moves by the same actor, joined by [LogJoin]; the table's own lines stand alone. */
internal fun foldedByActor(lines: List<Pair<Speaker, String>>): List<String> {
    val folded = ArrayList<String>(lines.size)
    var lastWho: Speaker? = null
    for ((who, line) in lines) {
        if (folded.isNotEmpty() && who != Speaker.Nobody && who == lastWho) {
            folded[folded.lastIndex] = folded.last() + LogJoin + line
        } else {
            folded += line
        }
        lastWho = who
    }
    return folded
}

/** How many lines of log fit in [room], up to the well's greatest depth; none when less than one. */
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
