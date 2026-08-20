package game.vinto.app.game

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import game.vinto.client.Tone

private val PanelPad = 12.dp
private val Gap = 8.dp
private val Half = 4.dp
private val LogCorner = 6.dp
private val HelpSize = 34.dp

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
    table: Table,
    refusal: String?,
    recent: List<String>,
    floor: Dp,
    onMove: (Move) -> Unit,
    onHelp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            // The panel keeps [floor] whether it needs it or not, and grows past it smoothly
            // when it genuinely does — a King's fourteen ranks, or a refusal that runs to two
            // lines. The felt above takes whatever is left, so a panel that changed height
            // freely would move the whole table on every phase of every turn: cards, seats
            // and piles all shifting under a thumb that is halfway to one of them.
            .heightIn(min = floor)
            .animateContentSize(),
        color = RailFill,
    ) {
        // The Surface's minimum has to reach the content for the centring below to have room
        // to work in; a wrapped Column would simply be as tall as its own children.
        // No scrolling. A control you have to scroll to reach is one a player does not know
        // is there, and the panel's worst case — a King's fourteen ranks — fits because those
        // are drawn as a compact grid rather than as fourteen full-width buttons.
        //
        // Centred inside the reserved height, so a short panel — "Raph is playing", and
        // nothing to do about it — sits in the middle of the rail rather than clinging to the
        // felt above a void. It is a no-op for every panel that fills the space, which is most
        // of them, so the buttons do not move around between phases.
        Column(
            modifier = Modifier.fillMaxWidth().padding(PanelPad),
            verticalArrangement = Arrangement.spacedBy(Gap, Alignment.CenterVertically),
        ) {
            Heading(table = table, onHelp = onHelp)

            // The engine's own words, not a translation of them. A refusal is nearly always a
            // rule the player has not met yet, and paraphrasing it here would put a second,
            // drifting copy of the rules in the UI.
            refusal?.let { reason ->
                Text(
                    text = reason,
                    fontSize = DetailSize,
                    color = WarnInk,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }

            RecentActions(recent)

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

/** The prompt, the rule under it, and the way to ask for more. */
@Composable
private fun Heading(table: Table, onHelp: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(Gap),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = table.prompt,
                fontSize = PromptSize,
                fontWeight = FontWeight.Bold,
                color = RailInk,
            )
            table.detail?.let {
                Text(text = it, fontSize = DetailSize, color = RailInkDim)
            }
        }

        // Always present, never in the way. A card game's rules are the game, and a player who
        // has to leave the table to look one up is a player who guesses instead.
        Surface(
            onClick = onHelp,
            modifier = Modifier.size(HelpSize),
            shape = CircleShape,
            color = RailFill,
            border = BorderStroke(1.dp, RailBorder),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("?", fontSize = PromptSize, fontWeight = FontWeight.Bold, color = RailInkDim)
            }
        }
    }
}

/**
 * The last couple of moves, in words.
 *
 * Three bots take their turns in well under a second between one tap and the next, and
 * without this the player sees the discard pile change and has to work out what happened.
 * Two lines, not a transcript: it is there to be caught out of the corner of an eye.
 */
@Composable
private fun RecentActions(recent: List<String>) {
    if (recent.isEmpty()) return

    Surface(
        shape = RoundedCornerShape(LogCorner),
        color = RailFill,
        border = BorderStroke(1.dp, RailBorder),
        modifier = Modifier.fillMaxWidth(),
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
        modifier = Modifier.fillMaxWidth(),
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
