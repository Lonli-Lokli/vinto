package game.vinto.app.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import game.vinto.app.art.Res
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
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

private val PanelPad = 12.dp
private val Gap = 8.dp
private val Half = 4.dp
private val LogCorner = 6.dp

private const val RECENT_SHOWN = 2

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
        // The Surface's minimum has to reach the content for the centring below to have room
        // to work in; a wrapped Column would simply be as tall as its own children.
        // The rail is a fixed height, so this is what adapts. Centred, so a short panel —
        // "Raph is playing", and nothing to do about it — sits in the middle rather than
        // clinging to the felt above a void, and a full one fills the space either way, so the
        // buttons stay where a thumb left them.
        //
        // Scrolling is the last resort rather than the design: the worst case is a King's
        // fourteen ranks, which fit because they are a compact grid and because the box of
        // recent moves stands aside for them (below). It exists so that a large system font
        // cannot push a button off the bottom of the rail.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(PanelPad),
            verticalArrangement = Arrangement.spacedBy(Gap, Alignment.CenterVertically),
        ) {
            Heading(table = table, teaching = state.teaching)

            Answer(state)

            // The one thing that gives way when the rail is crowded, and the rail is a fixed
            // height, so something has to. Naming a rank asks for fourteen chips and two
            // buttons; a drawn action card asks for three full-width ones. Either way what
            // happened three moves ago matters less than being able to reach them — and the
            // heading has already said what the moment is.
            // The log is what happened *before* now. The prompt above is now, and the two
            // are built from the same narration, so the top line of the log was routinely the
            // heading again in smaller type — "You drew the A", under "You drew the A".
            // Counted in full-width rows rather than in buttons, because a stakes move
            // brings a rule and the word "or" down with it — which is what pushed "Call
            // Vinto" off the bottom of the rail while the button count still said two.
            val rows = table.choices.size + if (table.choices.any { it.tone == Tone.STAKES }) 1 else 0
            val crowded = table.ranks.isNotEmpty() || rows >= Crowded
            if (!crowded) RecentActions(state.recent.filterNot { table.prompt.echoedBy(it) })

            RankGrid(table.ranks, stage, onMove)

            // A stakes move is set below a rule, as the web app sets Call Vinto below an
            // "or": it is not the next step in what you were doing, it is a different thing
            // to do, and the line is what stops a thumb finding it by accident.
            val (ordinary, stakes) = table.choices.partition { it.tone != Tone.STAKES }

            // Side by side under a rank grid, stacked everywhere else. Fourteen chips, a
            // heading and two full-width buttons is more than the rail has: the last button
            // was drawn six points tall against the bottom of the screen, which is a control
            // that exists and cannot be pressed.
            if (table.ranks.isNotEmpty() && ordinary.size > 1) {
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
 * The last few moves, in the phone's language.
 *
 * Rendering happens here rather than in the model, which is the whole of §6h's change: the
 * log arrives as [Say] — what happened — and becomes words where the resources are.
 *
 * The caller drops any line that only repeats the prompt, by the model's own `echoedBy` rule.
 * That was briefly a comparison of two *rendered* strings — which worked by coincidence, an
 * [Ask] and a [Say] being different types that happen to produce the same words in English.
 * As a rule it survives a language where they do not.
 */
@Composable
private fun RecentActions(recent: List<Say>) {
    if (recent.isEmpty()) return

    Surface(
        shape = RoundedCornerShape(LogCorner),
        color = Rail.fill,
        border = BorderStroke(1.dp, Rail.line),
        modifier = Modifier.fillMaxWidth().markedAs(LocalStage.current, Target.LOG),
    ) {
        Column(modifier = Modifier.padding(Gap)) {
            // A plain loop rather than `map`: `said` is a composable, and a composable call
            // inside a non-inline lambda is not one the compiler will accept.
            val rendered = ArrayList<String>(recent.size)
            for (entry in recent) rendered += said(entry)

            rendered.takeLast(RECENT_SHOWN).forEach { line ->
                Text(text = line, fontSize = DetailSize, color = Rail.inkDim)
            }
        }
    }
}

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
