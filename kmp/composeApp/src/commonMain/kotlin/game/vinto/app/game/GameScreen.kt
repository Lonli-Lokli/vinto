package game.vinto.app.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import game.vinto.app.art.Res
import game.vinto.app.art.deck_body
import game.vinto.app.art.deck_dismiss
import game.vinto.app.art.deck_title
import game.vinto.app.art.report_body
import game.vinto.app.art.report_copy
import game.vinto.app.art.report_dismiss
import game.vinto.app.art.report_send
import game.vinto.app.art.report_subject
import game.vinto.app.art.report_title
import game.vinto.app.art.table_see_score
import game.vinto.app.nowIso
import game.vinto.app.shareReport
import game.vinto.app.theme.ButtonTone
import game.vinto.app.theme.GameButton
import game.vinto.app.theme.Rail
import game.vinto.client.LocalGame
import game.vinto.client.Pace
import game.vinto.client.toJson
import game.vinto.shapes.GamePhase
import org.jetbrains.compose.resources.stringResource

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
fun GameScreen(game: LocalGame, pace: Pace, onQuit: () -> Unit) {
    // Keyed on the round, so dealing the next one rebuilds the table rather than trying to
    // reconcile the old one against a fresh deal.
    val round = game.round
    val session = game.session
    val holder = rememberHolder(session)
    val act = rememberActor(holder, onEachMove = game::save)
    val log by session.log.collectAsState()

    var helpOpen by remember { mutableStateOf(false) }
    var scoreOpen by remember(round) { mutableStateOf(false) }
    val reportSubject = stringResource(Res.string.report_subject)
    var reported by remember { mutableStateOf(false) }
    var deckOpen by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Decided once, from the screen. Everything about how large a card is drawn — on the
        // table and in flight — comes from here, so the two cannot disagree and neither can
        // change while a round is being played. The shape of the screen picks the
        // arrangement too: a rotation is the one thing that re-decides it mid-round.
        val layout = TableLayout.forScreen(maxWidth, maxHeight)

        CardStage(
            frames = session.frames,
            live = holder.current,
            sizes = layout.sizes,
            pace = pace.scale,
        ) { shown ->
            Column(modifier = Modifier.fillMaxSize()) {
                TableScreen(
                    // `shown` rather than the live view: while the bots' moves are being played
                    // out this is the table as it was after the move currently on screen, and it
                    // catches up the moment there is nothing left to animate.
                    state = TableState(
                        view = shown,
                        table = holder.tableFor(shown),
                        refusal = holder.refusal,
                        recent = log,
                        round = round,
                    ),
                    layout = layout,
                    onMove = act,
                    onHelp = { helpOpen = true },
                    // The whole game, in the format the replay harness already reads. A bug
                    // report for a card game is worth what it is reproducible for, and "the bots
                    // got stuck" is worth nothing — this is the seed, every action in order, and
                    // a hash after each one, so the exact deal can be played back and the first
                    // action that disagrees is the bug's address.
                    onReport = { reported = true },
                    onDeck = { deckOpen = true },
                    modifier = Modifier.weight(1f),
                )

                // On the shown table, not the live one: the round is over when the player has
                // seen it end, which is a second or two after the engine says so.
                if (shown.phase == GamePhase.SCORING) RoundOver(onSee = { scoreOpen = true })
            }
        }
    }

    if (reported) {
        ReportProblem(
            onSend = {
                val report = session.report(at = nowIso(), label = "reported from the table")
                val subject = reportSubject
                if (!shareReport(subject, report.toJson())) {
                    clipboard.setText(AnnotatedString(report.toJson()))
                }
                reported = false
            },
            onCopy = {
                val report = session.report(at = nowIso(), label = "reported from the table")
                clipboard.setText(AnnotatedString(report.toJson()))
                reported = false
            },
            onDismiss = { reported = false },
        )
    }

    if (deckOpen) {
        DeckExplained(left = holder.current.drawPileSize, onDismiss = { deckOpen = false })
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
 * Reporting a problem, offered rather than performed.
 *
 * It used to copy the game to the clipboard and then tell the player it had — which leaves
 * them holding a wall of JSON and no idea where to put it, and is where most bug reports
 * stop. Now it says what would be sent and what is *not* in it, and hands the sending to the
 * platform's own share sheet, where the player already knows how to mail it, message it or
 * keep it. The clipboard is still there as the second answer, and as the answer on platforms
 * that have nothing to share with.
 */
@Composable
private fun ReportProblem(onSend: () -> Unit, onCopy: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Rail.fill,
        titleContentColor = Rail.ink,
        textContentColor = Rail.inkDim,
        title = { Text(stringResource(Res.string.report_title)) },
        text = { Text(stringResource(Res.string.report_body)) },
        // All three stacked in the confirm slot rather than split across the dialog's two:
        // a row of two and a wrapped third is what that produces, and these are three answers
        // to one question, not two and an afterthought.
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(DialogGap),
            ) {
                GameButton(
                    label = stringResource(Res.string.report_send),
                    tone = ButtonTone.PLAY,
                    onClick = onSend,
                    modifier = Modifier.fillMaxWidth(),
                )
                GameButton(
                    label = stringResource(Res.string.report_copy),
                    tone = ButtonTone.NEUTRAL,
                    onClick = onCopy,
                    modifier = Modifier.fillMaxWidth(),
                )
                GameButton(
                    label = stringResource(Res.string.report_dismiss),
                    tone = ButtonTone.NEUTRAL,
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}

/**
 * What the number in the corner is.
 *
 * It is the one figure on the screen that decides how a round ends, and it was silent: a
 * count with no units and nothing to tap. The reshuffle is the part worth saying — everything
 * anybody had learned from watching the pile becomes worthless the moment the deck runs dry,
 * which is a reason to call Vinto rather than a curiosity.
 */
@Composable
private fun DeckExplained(left: Int, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Rail.fill,
        titleContentColor = Rail.ink,
        textContentColor = Rail.inkDim,
        title = { Text(stringResource(Res.string.deck_title)) },
        text = { Text(stringResource(Res.string.deck_body, left)) },
        confirmButton = {
            GameButton(
                label = stringResource(Res.string.deck_dismiss),
                tone = ButtonTone.NEUTRAL,
                onClick = onDismiss,
            )
        },
    )
}

private val DialogGap = 6.dp

/**
 * The round is over; the hands are face-up and the score is one tap away.
 *
 * A button rather than the sheet opening itself, because the moment a round ends is the one
 * moment a player wants to look at the table — every hand is turned over, including the ones
 * they spent the round guessing at.
 */
@Composable
private fun RoundOver(onSee: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), color = Rail.fill) {
        Column(modifier = Modifier.padding(Pad)) {
            GameButton(
                label = stringResource(Res.string.table_see_score),
                tone = ButtonTone.PLAY,
                onClick = onSee,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

