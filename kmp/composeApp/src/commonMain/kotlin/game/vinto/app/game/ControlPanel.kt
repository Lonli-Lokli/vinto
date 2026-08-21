package game.vinto.app.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import game.vinto.app.theme.ButtonTone
import game.vinto.app.theme.GameButton
import game.vinto.app.theme.RailBorder
import game.vinto.app.theme.RailFill
import game.vinto.app.theme.RailInk
import game.vinto.app.theme.RailInkDim
import game.vinto.client.Choice
import game.vinto.client.Move
import game.vinto.client.Table
import game.vinto.client.Target
import game.vinto.client.Tone

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
    height: Dp,
    onMove: (Move) -> Unit,
    modifier: Modifier = Modifier,
) {
    val table = state.table
    val stage = LocalStage.current
    Surface(
        modifier = modifier.fillMaxWidth().height(height),
        color = RailFill,
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
            Heading(table = table)

            // The engine's own words, not a translation of them. A refusal is nearly always a
            // rule the player has not met yet, and paraphrasing it here would put a second,
            // drifting copy of the rules in the UI.
            state.refusal?.let { reason ->
                Text(
                    text = reason,
                    fontSize = DetailSize,
                    color = WarnInk,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }

            // The one thing that gives way when the rail is crowded. Naming a rank asks for
            // fourteen chips and two buttons, and what happened three moves ago matters less
            // than being able to reach them.
            if (table.ranks.isEmpty()) RecentActions(state.recent)

            if (table.ranks.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Half),
                    verticalArrangement = Arrangement.spacedBy(Half),
                ) {
                    table.ranks.forEach { rank ->
                        GameButton(
                            label = rank.rank.serialName,
                            tone = ButtonTone.DECLARE,
                            onClick = { onMove(rank.move) },
                            modifier = Modifier.markedAs(stage, "rank:${rank.rank.serialName}"),
                            compact = true,
                        )
                    }
                }
            }

            // A stakes move is set below a rule, as the web app sets Call Vinto below an
            // "or": it is not the next step in what you were doing, it is a different thing
            // to do, and the line is what stops a thumb finding it by accident.
            val (ordinary, stakes) = table.choices.partition { it.tone != Tone.STAKES }
            ordinary.forEach { choice -> ChoiceButton(choice, onMove) }

            if (stakes.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Gap),
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f), color = RailBorder)
                    Text("or", fontSize = DetailSize, color = RailInkDim)
                    HorizontalDivider(modifier = Modifier.weight(1f), color = RailBorder)
                }
                stakes.forEach { choice -> ChoiceButton(choice, onMove) }
            }
        }
    }
}

/** The prompt, and the rule under it. */
@Composable
private fun Heading(table: Table) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = table.prompt,
            fontSize = PromptSize,
            fontWeight = FontWeight.Bold,
            color = RailInk,
        )
        table.detail?.let { detail ->
            Text(text = detail, fontSize = DetailSize, color = RailInkDim)
        }
    }
}

@Composable
private fun RecentActions(recent: List<String>) {
    if (recent.isEmpty()) return

    Surface(
        shape = RoundedCornerShape(LogCorner),
        color = RailFill,
        border = BorderStroke(1.dp, RailBorder),
        modifier = Modifier.fillMaxWidth().markedAs(LocalStage.current, Target.LOG),
    ) {
        Column(modifier = Modifier.padding(Gap)) {
            recent.takeLast(RECENT_SHOWN).forEach { line ->
                Text(text = line, fontSize = DetailSize, color = RailInkDim)
            }
        }
    }
}

@Composable
private fun ChoiceButton(choice: Choice, onMove: (Move) -> Unit) {
    GameButton(
        label = choice.label,
        tone = choice.tone.paint(),
        onClick = { onMove(choice.move) },
        // By its label, which is what the lesson knows it by — the model chose the words and
        // the coach quotes them, so a button the coach points at is the button on screen.
        modifier = Modifier.fillMaxWidth().markedAs(LocalStage.current, "choice:${choice.label}"),
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

private val PromptSize = 17.sp
private val DetailSize = 13.sp
private val WarnInk = androidx.compose.ui.graphics.Color(0xFFFFA39E)
