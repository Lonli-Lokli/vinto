package game.vinto.app.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import game.vinto.app.art.Res
import game.vinto.app.art.score_caller_won
import game.vinto.app.art.score_coalition_won
import game.vinto.app.art.score_column_game
import game.vinto.app.art.score_column_hand
import game.vinto.app.art.score_column_round
import game.vinto.app.art.score_deck_ended
import game.vinto.app.art.score_deck_out
import game.vinto.app.art.score_level
import game.vinto.app.art.score_next_round
import game.vinto.app.art.score_role_best
import game.vinto.app.art.score_role_caller
import game.vinto.app.art.score_round
import game.vinto.app.art.score_stop
import game.vinto.app.art.score_they_called
import game.vinto.app.art.score_versus
import game.vinto.app.art.score_versus_none
import game.vinto.app.art.score_you_called
import game.vinto.app.theme.ButtonTone
import game.vinto.app.theme.GameButton
import game.vinto.app.theme.Rail
import game.vinto.app.theme.Slate
import game.vinto.client.RoundOutcome
import game.vinto.client.RoundResult
import game.vinto.client.bestCoalitionHands
import game.vinto.client.outcomeOf
import org.jetbrains.compose.resources.painterResource
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
            Verdict(round, outcomeOf(result.hands, result.callerId))

            Row(modifier = Modifier.fillMaxWidth()) {
                Text("", modifier = Modifier.weight(1f))
                Header(stringResource(Res.string.score_column_hand))
                Header(stringResource(Res.string.score_column_round))
                Header(stringResource(Res.string.score_column_game))
            }

            val decided = bestCoalitionHands(result.hands, result.callerId)
            result.seats.sortedBy { result.hands[it.first] ?: 0 }.forEach { (id, name) ->
                Line(
                    name = name,
                    caller = id == result.callerId,
                    // The hand the round was actually decided against, marked — it is the one
                    // number in the table that the +3 and the −1 were both worked out from,
                    // and until now nothing said which row it was.
                    decisive = id in decided,
                    hand = result.hands[id],
                    round = result.points[id],
                    total = after[id],
                )
            }

            // Why the round ended, whichever way it did. A call names its caller; a round
            // with no caller can only have ended on the deck, and a player who never called
            // and never heard one has every right to ask why the hands went face-up.
            val callerId = result.callerId
            val line = when {
                callerId == you -> stringResource(Res.string.score_you_called)
                callerId != null -> {
                    val who = result.seats.firstOrNull { it.first == callerId }?.second
                    stringResource(Res.string.score_they_called, who.orEmpty())
                }

                else -> stringResource(Res.string.score_deck_ended)
            }
            Text(line, fontSize = BodySize, color = Rail.inkDim)

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

/**
 * How the round came out, before the numbers that say so.
 *
 * The sheet used to open with "Round 3" and leave the reader to derive the winner from a
 * column of +3 and −1. That is arithmetic at the moment somebody wants an answer, and it is
 * the thing the web client did better: it ends a round on a sentence naming the outcome and
 * the two totals that decided it. This is that, in this app's voice rather than that one's —
 * no confetti and no exclamation mark, because the same screen has to carry a loss.
 *
 * The round number stays, small, above it. It was the heading and it is not the news.
 */
@Composable
private fun Verdict(round: Int, outcome: RoundOutcome) {
    Column {
        Text(
            text = stringResource(Res.string.score_round, round),
            fontSize = SmallSize,
            color = Rail.inkDim,
        )
        Text(
            text = when (outcome) {
                is RoundOutcome.CallerWon -> stringResource(Res.string.score_caller_won)
                is RoundOutcome.Level -> stringResource(Res.string.score_level)
                is RoundOutcome.CoalitionWon -> stringResource(Res.string.score_coalition_won)
                RoundOutcome.DeckRanOut -> stringResource(Res.string.score_deck_out)
            },
            fontSize = TitleSize,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = when (outcome) {
                is RoundOutcome.CallerWon ->
                    stringResource(Res.string.score_versus, outcome.caller, outcome.best)

                is RoundOutcome.Level ->
                    stringResource(Res.string.score_versus, outcome.caller, outcome.best)

                is RoundOutcome.CoalitionWon ->
                    stringResource(Res.string.score_versus, outcome.caller, outcome.best)

                RoundOutcome.DeckRanOut -> stringResource(Res.string.score_versus_none)
            },
            fontSize = BodySize,
            color = Rail.inkDim,
        )
    }
}

@Composable
private fun Header(label: String) {
    Text(
        label,
        fontSize = SmallSize,
        color = Rail.inkDim,
        modifier = Modifier.padding(horizontal = Gap).semantics { heading() },
    )
}

/**
 * One player's round: who they are, what they held, and what it paid.
 *
 * The portrait is the same one their seat wore on the felt, which is the point — three of the
 * four names belong to bots a player has been watching for ten minutes and knows by face
 * before they know by name. Reading a score sheet should not require re-learning who is who.
 *
 * Two rows carry a mark under the name. The **caller** is the hand everything else was
 * measured against, and the **decisive** hand is the one it was measured against *to* — until
 * now the sheet showed both numbers and named neither, so the +3 and the −1 arrived without
 * their reason.
 */
@Composable
private fun Line(
    name: String,
    caller: Boolean,
    decisive: Boolean,
    hand: Int?,
    round: Int?,
    total: Int?,
) {
    Surface(
        shape = RoundedCornerShape(Corner),
        color = Rail.fill,
        // The two rows the round turned on are ringed rather than tinted: a fill would fight
        // the felt behind the sheet, and there can legitimately be two decisive rows when
        // players tie.
        border = BorderStroke(
            if (caller || decisive) MarkRing else 1.dp,
            when {
                caller -> Slate.gold
                decisive -> Rail.brand
                else -> Rail.line
            },
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(Gap),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Gap),
        ) {
            Image(
                painter = painterResource(portraitFor(name)),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(FaceSize).clip(CircleShape),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    fontSize = BodySize,
                    fontWeight = if (caller) FontWeight.Bold else FontWeight.Normal,
                )
                val mark = when {
                    caller -> stringResource(Res.string.score_role_caller)
                    decisive -> stringResource(Res.string.score_role_best)
                    else -> null
                }
                mark?.let {
                    Text(
                        text = it,
                        fontSize = SmallSize,
                        color = if (caller) Slate.gold else Rail.brand,
                    )
                }
            }
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

/** The same face the seat wore on the felt, at the size a list wants. */
private val FaceSize = 32.dp

/** A ring rather than a hairline, for the two rows the round turned on. */
private val MarkRing = 2.dp

private val TitleSize = 20.sp
private val BodySize = 15.sp
private val SmallSize = 12.sp
