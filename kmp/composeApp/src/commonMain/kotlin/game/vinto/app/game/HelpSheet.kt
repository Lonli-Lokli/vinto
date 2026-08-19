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

private const val MAX_CHIP_CHARS = 3
private val TitleSize = 16.sp
private val BodySize = 14.sp
