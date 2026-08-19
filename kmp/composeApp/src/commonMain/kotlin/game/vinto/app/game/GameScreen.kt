package game.vinto.app.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import game.vinto.shapes.Difficulty

private val Pad = 16.dp
private val Gap = 8.dp

/**
 * One round, from the deal to the score.
 *
 * A round rather than a session: online, a room runs several rounds against a thirty-minute
 * clock, and locally there is no clock and nobody to wait for — so "play again" deals a new
 * game rather than continuing one. That difference lives here, in the screen, and not in the
 * session, which is the same on both sides of it.
 */
@Composable
fun GameScreen(seed: Long, difficulty: Difficulty, onQuit: () -> Unit, onPlayAgain: () -> Unit) {
    val holder = rememberGame(seed, difficulty)
    val act = rememberActor(holder)
    val log by holder.log.collectAsState()
    var helpOpen by rememberSaveable { mutableStateOf(false) }

    CardStage(scenes = holder.scenes, sizes = TableSizes.forHeight(TableHeightGuess)) {
        Column(modifier = Modifier.fillMaxSize()) {
            TableScreen(
                view = holder.current,
                table = holder.table,
                refusal = holder.refusal,
                recent = log,
                onMove = act,
                onHelp = { helpOpen = true },
                modifier = Modifier.weight(1f),
            )

            if (holder.isOver) {
                RoundOver(onPlayAgain = onPlayAgain, onQuit = onQuit)
            }
        }
    }

    if (helpOpen) {
        HelpSheet(now = holder.table.help, onDismiss = { helpOpen = false })
    }
}

/**
 * The size a card in flight is drawn at.
 *
 * A card crossing the table between two seats of different sizes has to be drawn at *some*
 * size, and picking either end makes it appear to jump on arrival at the other. The player's
 * own size is the compromise, and it is the one they are looking at.
 */
private val TableHeightGuess = 640.dp

@Composable
private fun RoundOver(onPlayAgain: () -> Unit, onQuit: () -> Unit) {
    Surface(tonalElevation = Gap / 2) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Pad),
            verticalArrangement = Arrangement.spacedBy(Gap),
        ) {
            Text(
                "Every hand is turned over",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Button(onClick = onPlayAgain, modifier = Modifier.fillMaxWidth()) { Text("Deal again") }
            TextButton(onClick = onQuit, modifier = Modifier.fillMaxWidth()) { Text("Back") }
        }
    }
}
