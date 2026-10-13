package game.vinto.app.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import game.vinto.app.art.Res
import game.vinto.app.art.table_round_turn
import game.vinto.app.theme.Rail
import game.vinto.app.theme.WholeWords
import game.vinto.client.Move
import org.jetbrains.compose.resources.stringResource

/**
 * The table's own controls, stood up in the status column the system keeps down one edge ([Chrome]):
 * the closed iPhone Duo's, and the open one's on its side.
 *
 * In the owner's order (2026-10-13), from under the clock and the camera down:
 *
 *  1. **The way out**, first, where a column of controls begins: found by looking for it, and never
 *     by a thumb resting at the foot — the two reasons it is the last thing in a header row, turned
 *     on end.
 *  2. **The rules and the settings**, what a top bar would hold.
 *  3. **The round and the turn**, R1 / T1.
 *  4. **The plan's switch**, in a final round with a plan to open.
 *  5. Room, and then **the turn's choices at the foot**, under the thumb, as pills ([ChoicesAt.COLUMN]):
 *     the one most often taken lowest, and Call Vinto always the highest of them ([Pill.rank]).
 *
 * Marks without their words, whatever the host: the column is 84 points across and "Leave the table"
 * is not. The rail beside the felt then draws no choices of its own; the pickers — a King's ranks,
 * naming a rank after a swap — stay in the rail with whatever comes with them, and the column keeps
 * its fixed controls only ([choicesAt]).
 *
 * Laid in the stretch of the column the system leaves ([ColumnRun]): it starts under the clock and the
 * camera rather than among them, and stops short of anything at the foot.
 */
@Composable
internal fun StatusColumn(
    laid: Laid,
    run: ColumnRun,
    onMove: (Move) -> Unit,
    onHelp: (HelpTopic?) -> Unit,
    onSettings: () -> Unit,
    onLeave: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val state = laid.state
    val table = state.table
    Column(
        modifier = modifier.width(run.width).height(run.bottom).padding(top = run.top),
        verticalArrangement = Arrangement.spacedBy(ColumnGap),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        onLeave?.let { LeaveChip(it, wide = false) }
        RulesChip(onHelp, wide = false)
        SettingsChip(onSettings, wide = false)
        Text(
            text = stringResource(Res.string.table_round_turn, state.round, state.view.turnNumber),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Rail.ink,
            textAlign = TextAlign.Center,
            // One line, smaller rather than broken: "R12 / T40" over two lines at a large system
            // font would push the pills under the foot of the column ([pillsThatFit] counts one).
            maxLines = 1,
            autoSize = WholeWords(CounterLeast, CounterMost),
        )
        table.planSummary?.let { PlanSwitch(LocalStage.current.planning, it, onMove, stacked = true) }

        Spacer(modifier = Modifier.weight(1f))

        if (laid.choices == ChoicesAt.COLUMN) {
            columnOrder(table.choices).forEach { choice ->
                ChoiceButton(choice, onMove, pill = pillOf(choice.label))
            }
        }
    }
}

/** The counter's type, at most the header's, and as small as one line of it in the column needs. */
private val CounterMost = 12.sp
private val CounterLeast = 8.sp
