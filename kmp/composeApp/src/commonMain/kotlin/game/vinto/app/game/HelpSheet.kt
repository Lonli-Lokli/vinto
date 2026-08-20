package game.vinto.app.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import game.vinto.app.theme.RailBorder
import game.vinto.app.theme.RailFill
import game.vinto.app.theme.RailInk
import game.vinto.app.theme.RailInkDim
import game.vinto.shapes.ALL_RANKS
import game.vinto.shapes.CardConfig
import game.vinto.shapes.getCardConfig

private val Pad = 16.dp
private val Gap = 10.dp
private val RowGap = 12.dp
private val Corner = 6.dp
private val Chip = 46.dp

/**
 * What the cards do.
 *
 * Two parts, in the order a player needs them: what is happening *now* — the rule that
 * applies to the move being asked for, or what the card in hand does — and then every rank,
 * because the answer to "what does a Queen do again" is the reason people stop playing card
 * games they have not played before.
 *
 * The words are `CARD_CONFIGS`, which was ported with the engine and is the same copy the web
 * app shows. One set of rules, written once.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpSheet(now: String?, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = RailFill,
        contentColor = RailInk,
        // Expanded, the sheet reaches the top of the screen, and without this the first line
        // sits under the clock.
        contentWindowInsets = { WindowInsets.systemBars },
    ) {
        LazyColumn(
            modifier = Modifier.padding(horizontal = Pad).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(RowGap),
        ) {
            now?.let {
                item {
                    Surface(
                        shape = RoundedCornerShape(Corner),
                        color = RailFill,
                        border = BorderStroke(1.dp, RailBorder),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(Gap)) {
                            Text("Right now", fontWeight = FontWeight.Bold, fontSize = TitleSize)
                            Text(it, fontSize = BodySize, color = RailInkDim)
                        }
                    }
                }
            }

            item {
                Text(
                    "The cards",
                    fontWeight = FontWeight.Bold,
                    fontSize = TitleSize,
                    modifier = Modifier.padding(top = Gap),
                )
            }

            items(ALL_RANKS) { rank -> RankRow(getCardConfig(rank)) }

            item {
                Text(
                    "What the table is telling you",
                    fontWeight = FontWeight.Bold,
                    fontSize = TitleSize,
                    modifier = Modifier.padding(top = Gap),
                )
            }

            items(SIGNALS) { signal -> SignalRow(signal) }

            item {
                Text(
                    "Every card in a hand counts against you, so the lowest total wins. " +
                        "Call Vinto when you think yours is lowest — everybody else then plays " +
                        "one more turn together, and only their best hand is compared to yours.",
                    fontSize = BodySize,
                    color = RailInkDim,
                    modifier = Modifier.padding(vertical = Pad),
                )
            }
        }
    }
}

/**
 * The table's own vocabulary, written down.
 *
 * A card game teaches its rules and then leaves its *signals* to be worked out — which glow
 * means "your turn", which means "this can be touched", which means "somebody just looked at
 * that card". They are not decoration: each one is information a player at a real table would
 * get from watching hands and faces, and a player who has not worked them out is playing a
 * different, worse game.
 *
 * The colours here are the ones the table actually draws (`SeatPlate.kt`, `CardFace.kt`); if
 * one changes there it must change here, which is the price of explaining anything.
 */
private data class Signal(val swatch: Color, val name: String, val meaning: String)

private val SIGNALS = listOf(
    Signal(
        swatch = Color(0xFF6FD3A6),
        name = "A green ring on a seat",
        meaning = "Whose turn it is. It flashes as the turn arrives, because three bots can " +
            "take theirs between one tap and the next.",
    ),
    Signal(
        swatch = Color(0xFFE0A32A),
        name = "A gold ring",
        meaning = "That player has called Vinto. Nobody may touch their cards for the rest " +
            "of the round.",
    ),
    Signal(
        swatch = Color(0xFFE0674A),
        name = "A red ring, and a hand that flinches",
        meaning = "A penalty card just landed there — a wrong guess, or a wrong toss-in.",
    ),
    Signal(
        swatch = Color(0xFF8AB4F8),
        name = "A blue ring",
        meaning = "The coalition: everybody except the Vinto caller, playing their last turn " +
            "together against that one hand.",
    ),
    Signal(
        swatch = Color(0xFFF2F5F0),
        name = "A card that breathes",
        meaning = "It can be touched right now. If nothing breathes, the table is waiting on " +
            "somebody else.",
    ),
    Signal(
        swatch = Color(0xFFF2C14E),
        name = "A card that lifts towards the middle",
        meaning = "Somebody is looking at it. Everyone sees *which* card was looked at — only " +
            "the player entitled to it sees the face.",
    ),
)

@Composable
private fun SignalRow(signal: Signal) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(RowGap),
        verticalAlignment = Alignment.Top,
    ) {
        Surface(
            modifier = Modifier.size(Chip),
            shape = RoundedCornerShape(Corner),
            color = RailFill,
            border = BorderStroke(SwatchRing, signal.swatch),
            content = {},
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(signal.name, fontWeight = FontWeight.SemiBold, fontSize = BodySize)
            Text(signal.meaning, fontSize = BodySize, color = RailInkDim)
        }
    }
}

@Composable
private fun RankRow(config: CardConfig) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(RowGap),
        verticalAlignment = Alignment.Top,
    ) {
        Surface(
            modifier = Modifier.size(Chip),
            shape = RoundedCornerShape(Corner),
            color = RailFill,
            border = BorderStroke(1.dp, RailBorder),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    config.rank.serialName.take(MAX_CHIP_CHARS),
                    fontWeight = FontWeight.Bold,
                    fontSize = TitleSize,
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                "${config.name} — worth ${config.value}",
                fontWeight = FontWeight.SemiBold,
                fontSize = BodySize,
            )
            Text(
                text = config.longDescription.ifEmpty { "No action." },
                fontSize = BodySize,
                color = RailInkDim,
            )
        }
    }
}

private val SwatchRing = 3.dp

private const val MAX_CHIP_CHARS = 3
private val TitleSize = 16.sp
private val BodySize = 14.sp
