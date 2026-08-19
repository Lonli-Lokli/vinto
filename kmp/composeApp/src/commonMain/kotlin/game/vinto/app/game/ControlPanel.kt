package game.vinto.app.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import game.vinto.client.Choice
import game.vinto.client.Move
import game.vinto.client.Table
import game.vinto.client.Tone

private val PanelPad = 12.dp
private val Gap = 8.dp
private val MinTap = 48.dp
private val PanelLift = 3.dp
private val LogCorner = 8.dp
private val Half = 4.dp

/** Roughly a third of a phone. Past that the table stops being a table. */
private val PanelCeiling = 300.dp

private const val RECENT_SHOWN = 2
private const val LOG_ALPHA = 0.5f

/**
 * What the player can do, and nothing else.
 *
 * Everything here comes from [Table], which is a pure function of the game view — so this
 * composable has no opinion about the rules and cannot develop one. If a button is missing,
 * the answer is in `TableModel.kt` and a test can be written for it in milliseconds; if a
 * button is ugly, the answer is here. Keeping those two questions apart is the whole reason
 * the split exists.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ControlPanel(
    table: Table,
    refusal: String?,
    recent: List<String>,
    onMove: (Move) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = PanelLift,
        shadowElevation = PanelLift,
    ) {
        // Bounded and scrollable, because the panel's height is not ours to choose: a King
        // puts fourteen rank chips here, and at large font sizes that is most of a phone. The
        // table has to survive the worst case rather than the usual one.
        Column(
            modifier = Modifier
                .padding(PanelPad)
                .heightIn(max = PanelCeiling)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Gap),
        ) {
            Text(
                text = table.prompt,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )

            // The engine's own words, not a translation of them. A refusal is nearly always a
            // rule the player has not met yet, and paraphrasing it here would put a second,
            // drifting copy of the rules in the UI.
            refusal?.let { reason ->
                Text(
                    text = reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
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
                        AssistChip(
                            onClick = { onMove(rank.move) },
                            label = { Text(rank.rank.serialName) },
                        )
                    }
                }
            }

            // A risky choice is set below a rule, as the web app sets Call Vinto below an
            // "or": it is not the next step in what you were doing, it is a different thing
            // to do, and the line is what stops a thumb finding it by accident.
            val (ordinary, risky) = table.choices.partition { it.tone != Tone.RISKY }
            ordinary.forEach { choice -> ChoiceButton(choice, onMove) }

            if (risky.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Gap),
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f))
                    Text("or", style = MaterialTheme.typography.labelMedium)
                    HorizontalDivider(modifier = Modifier.weight(1f))
                }
                risky.forEach { choice -> ChoiceButton(choice, onMove) }
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
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = LOG_ALPHA),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(Gap)) {
            recent.takeLast(RECENT_SHOWN).forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ChoiceButton(choice: Choice, onMove: (Move) -> Unit) {
    val modifier = Modifier.fillMaxWidth().heightIn(min = MinTap)
    val label = @Composable { Text(choice.label) }

    when (choice.tone) {
        Tone.PRIMARY -> Button(onClick = { onMove(choice.move) }, modifier = modifier) { label() }

        Tone.NORMAL -> OutlinedButton(onClick = { onMove(choice.move) }, modifier = modifier) { label() }

        // Calling Vinto and throwing cards in are both bets. Colouring them as errors would
        // say "you have gone wrong"; they are simply the moves you can lose by.
        Tone.RISKY -> Button(
            onClick = { onMove(choice.move) },
            modifier = modifier,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
            ),
        ) { label() }
    }
}

/** A one-word button, for the rare case of choosing a whole player rather than a card. */
@Composable
fun SmallAction(label: String, onClick: () -> Unit) {
    androidx.compose.material3.TextButton(onClick = onClick) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}
