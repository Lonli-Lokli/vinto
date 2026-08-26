package game.vinto.app.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import game.vinto.app.art.Res
import game.vinto.app.art.score_column_game
import game.vinto.app.art.score_column_hand
import game.vinto.app.art.score_column_round
import game.vinto.app.art.score_next_round
import game.vinto.app.art.score_round
import game.vinto.app.art.score_stop
import game.vinto.app.art.score_they_called
import game.vinto.app.art.score_you_called
import game.vinto.app.theme.ButtonTone
import game.vinto.app.theme.GameButton
import game.vinto.app.theme.Rail
import game.vinto.client.RoundResult
import org.jetbrains.compose.resources.stringResource

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
        containerColor = Rail.fill,
        contentColor = Rail.ink,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = Pad)
                .fillMaxWidth()
                // A sheet on a phone lying on its side has less height than four seats and
                // two buttons; the scroll is what keeps "Next round" reachable there, and it
                // is inert anywhere the content fits.
                .verticalScroll(rememberScrollState())
                .padding(bottom = Pad),
            verticalArrangement = Arrangement.spacedBy(Gap),
        ) {
            Text(
                text = stringResource(Res.string.score_round, round),
                fontSize = TitleSize,
                fontWeight = FontWeight.Bold,
            )

            Row(modifier = Modifier.fillMaxWidth()) {
                Text("", modifier = Modifier.weight(1f))
                Header(stringResource(Res.string.score_column_hand))
                Header(stringResource(Res.string.score_column_round))
                Header(stringResource(Res.string.score_column_game))
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
                    stringResource(Res.string.score_you_called)
                } else {
                    val who = result.seats.firstOrNull { it.first == callerId }?.second
                    stringResource(Res.string.score_they_called, who.orEmpty())
                }
                Text(line, fontSize = BodySize, color = Rail.inkDim)
            }

            GameButton(
                label = stringResource(Res.string.score_next_round),
                tone = ButtonTone.PLAY,
                onClick = onNextRound,
                modifier = Modifier.fillMaxWidth(),
            )
            GameButton(
                label = stringResource(Res.string.score_stop),
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
        color = Rail.inkDim,
        modifier = Modifier.padding(horizontal = Gap),
    )
}

@Composable
private fun Line(name: String, caller: Boolean, hand: Int?, round: Int?, total: Int?) {
    Surface(
        shape = RoundedCornerShape(Corner),
        color = Rail.fill,
        border = BorderStroke(1.dp, Rail.line),
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
        color = if (dim) Rail.inkDim else Rail.ink,
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
