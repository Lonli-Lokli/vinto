package game.vinto.app.game

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import game.vinto.app.theme.ButtonTone
import game.vinto.app.theme.GameButton
import game.vinto.app.theme.RailFill
import game.vinto.client.LocalGame

private val Pad = 12.dp

/**
 * A game: rounds, one after another, with the score carried between them.
 *
 * The table is one round. What makes it a game is what happens when a round ends — the hands
 * go face-up, the points are banked, and the player decides whether to deal again. Online
 * that decision belongs to the room and its thirty-minute clock; locally there is nobody to
 * keep waiting, so it belongs to the player.
 */
@Composable
fun GameScreen(game: LocalGame, onQuit: () -> Unit) {
    // Keyed on the round, so dealing the next one rebuilds the table rather than trying to
    // reconcile the old one against a fresh deal.
    val round = game.round
    val session = game.session
    val holder = rememberHolder(session)
    val act = rememberActor(holder, onEachMove = game::save)
    val log by session.log.collectAsState()

    var helpOpen by remember { mutableStateOf(false) }
    var scoreOpen by remember(round) { mutableStateOf(false) }

    CardStage(scenes = session.scenes, sizes = TableSizes.forHeight(TableHeightGuess)) {
        Column(modifier = Modifier.fillMaxSize()) {
            TableScreen(
                state = TableState(
                    view = holder.current,
                    table = holder.table,
                    refusal = holder.refusal,
                    recent = log,
                    round = round,
                ),
                onMove = act,
                onHelp = { helpOpen = true },
                modifier = Modifier.weight(1f),
            )

            if (holder.isOver) RoundOver(onSee = { scoreOpen = true })
        }
    }

    if (helpOpen) {
        HelpSheet(now = holder.table.help, onDismiss = { helpOpen = false })
    }

    game.result?.takeIf { scoreOpen }?.let { result ->
        StandingsSheet(
            round = round,
            you = game.playerId,
            result = result,
            standings = game.standings,
            onNextRound = {
                scoreOpen = false
                game.nextRound()
            },
            onQuit = {
                scoreOpen = false
                onQuit()
            },
        )
    }
}

/**
 * The round is over; the hands are face-up and the score is one tap away.
 *
 * A button rather than the sheet opening itself, because the moment a round ends is the one
 * moment a player wants to look at the table — every hand is turned over, including the ones
 * they spent the round guessing at.
 */
@Composable
private fun RoundOver(onSee: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), color = RailFill) {
        Column(modifier = Modifier.padding(Pad)) {
            GameButton(
                label = "See the score",
                tone = ButtonTone.PLAY,
                onClick = onSee,
                modifier = Modifier.fillMaxWidth(),
            )
        }
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
