package game.vinto.app.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import game.vinto.app.theme.ButtonTone
import game.vinto.app.theme.GameButton
import game.vinto.app.theme.RailBorder
import game.vinto.app.theme.RailFill
import game.vinto.app.theme.RailInk
import game.vinto.app.theme.RailInkDim
import game.vinto.client.RoundResult

private val Pad = 16.dp
private val Gap = 10.dp
private val Corner = 8.dp

/**
 * The end of a round: what everybody was holding, what it was worth, and where that leaves
 * the game.
 *
 * Two numbers per player and they are easy to confuse, so they are labelled rather than
 * stacked: the **hand** is what was on the table, and the **points** are what the round was
 * worth under the rules — which are not the same thing at all. A caller who finishes on 12
 * against a coalition's 9 loses the round by one point while holding the higher total, and a
 * screen that showed only totals would make that look like a bug.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StandingsSheet(
    round: Int,
    you: String,
    result: RoundResult,
    standings: Map<String, Int>,
    onNextRound: () -> Unit,
    onQuit: () -> Unit,
) {
    val after = (standings.keys + result.points.keys).associateWith { id ->
        (standings[id] ?: 0) + (result.points[id] ?: 0)
    }

    ModalBottomSheet(
        onDismissRequest = onNextRound,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = RailFill,
        contentColor = RailInk,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Pad).fillMaxWidth().padding(bottom = Pad),
            verticalArrangement = Arrangement.spacedBy(Gap),
        ) {
            Text("Round $round", fontSize = TitleSize, fontWeight = FontWeight.Bold)

            Row(modifier = Modifier.fillMaxWidth()) {
                Text("", modifier = Modifier.weight(1f))
                Header("hand")
                Header("round")
                Header("game")
            }

            result.seats.sortedBy { result.hands[it.first] ?: 0 }.forEach { (id, name) ->
                Line(
                    name = name,
                    caller = id == result.callerId,
                    hand = result.hands[id],
                    round = result.points[id],
                    total = after[id],
                )
            }

            result.callerId?.let { callerId ->
                // The caller might be the person reading this, and "You called Vinto, so
                // their hand…" is the kind of sentence that makes a player wonder whose score
                // they are looking at.
                val line = if (callerId == you) {
                    "You called Vinto, so your hand was set against the best of the others'."
                } else {
                    val who = result.seats.firstOrNull { it.first == callerId }?.second ?: "Someone"
                    "$who called Vinto, so their hand was set against the best of yours."
                }
                Text(line, fontSize = BodySize, color = RailInkDim)
            }

            GameButton(
                label = "Deal the next round",
                tone = ButtonTone.PLAY,
                onClick = onNextRound,
                modifier = Modifier.fillMaxWidth(),
            )
            GameButton(
                label = "Stop here",
                tone = ButtonTone.NEUTRAL,
                onClick = onQuit,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun Header(label: String) {
    Text(
        label,
        fontSize = SmallSize,
        color = RailInkDim,
        modifier = Modifier.padding(horizontal = Gap),
    )
}

@Composable
private fun Line(name: String, caller: Boolean, hand: Int?, round: Int?, total: Int?) {
    Surface(
        shape = RoundedCornerShape(Corner),
        color = RailFill,
        border = BorderStroke(1.dp, RailBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(Gap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (caller) "$name  ·  Vinto" else name,
                fontSize = BodySize,
                fontWeight = if (caller) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.weight(1f),
            )
            Cell("${hand ?: "—"}", dim = true)
            Cell(round.signed(), dim = false)
            Cell("${total ?: 0}", dim = false, bold = true)
        }
    }
}

@Composable
private fun Cell(text: String, dim: Boolean, bold: Boolean = false) {
    Text(
        text = text,
        fontSize = BodySize,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        color = if (dim) RailInkDim else RailInk,
        modifier = Modifier.padding(horizontal = Gap),
    )
}

/** A round's points read as a change, so "+3" and "-1" say which way it went. */
private fun Int?.signed(): String = when {
    this == null -> "—"
    this > 0 -> "+$this"
    else -> "$this"
}

private val TitleSize = 20.sp
private val BodySize = 15.sp
private val SmallSize = 12.sp
