package game.vinto.app.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import game.vinto.app.CountRefusals
import game.vinto.app.LocalCounting
import game.vinto.app.LocalVault
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
import game.vinto.app.counted
import game.vinto.app.elapsedMs
import game.vinto.app.nowIso
import game.vinto.app.shareText
import game.vinto.app.theme.ButtonTone
import game.vinto.app.theme.GameButton
import game.vinto.app.theme.LocalSounds
import game.vinto.app.theme.Rail
import game.vinto.app.theme.Sfx
import game.vinto.app.theme.VintoDialog
import game.vinto.client.LocalGame
import game.vinto.client.Pace
import game.vinto.client.RoundResult
import game.vinto.client.dealScenes
import game.vinto.client.loadStats
import game.vinto.client.plus
import game.vinto.client.saveStats
import game.vinto.client.toJson
import game.vinto.protocol.AnalyticsEvent
import game.vinto.shapes.GamePhase
import game.vinto.shapes.Rank
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
fun GameScreen(game: LocalGame, pace: Pace, onSettings: () -> Unit, onQuit: () -> Unit) {
    val countRound = rememberRoundCount(game)

    // Keyed on the round, so dealing the next one rebuilds the table rather than trying to
    // reconcile the old one against a fresh deal.
    val round = game.round
    val session = game.session
    val holder = rememberHolder(session)
    val act = rememberActor(holder, onEachMove = game::save)
    val log by session.log.collectAsState()

    // A refused move is a defect wherever it happens; the surface says which table it was.
    CountRefusals(holder.refusal)

    val help = remember { HelpState() }
    var scoreOpen by remember(round) { mutableStateOf(false) }
    val reportSubject = stringResource(Res.string.report_subject)
    var reported by remember { mutableStateOf(false) }
    var deckOpen by remember { mutableStateOf(false) }

    // `LocalClipboardManager` is deprecated in favour of `LocalClipboard`, and the
    // replacement is still not usable from common code in Compose Multiplatform 1.12:
    // `Clipboard.setClipEntry` takes a `ClipEntry`, and the only way to build one from a
    // string is `ClipEntry.withPlainText`, which is declared **per platform** rather than in
    // commonMain. Checked rather than assumed — a one-line probe calling it from commonMain
    // compiles for wasmJs and fails for the JVM with "Unresolved reference". So migrating
    // still means an `expect`/`actual` per platform, which is the four hand-written
    // implementations `Share.kt` deliberately does not have, "for a job the framework has
    // already done".
    //
    // Suppressed rather than left warning, because a warning nobody can act on is noise that
    // hides the ones somebody can. Delete the suppression the day `withPlainText` reaches
    // commonMain; the probe above is how to check.
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Decided once, from the screen. Everything about how large a card is drawn — on the
        // table and in flight — comes from here, so the two cannot disagree and neither can
        // change while a round is being played. The shape of the screen picks the
        // arrangement too: a rotation is the one thing that re-decides it mid-round.
        val layout = TableLayout.forScreen(maxWidth, maxHeight)

        // The opening deal, played once per fresh deal: read before the effect that marks it
        // shown, and remembered per round so a rebuilt screen — a rotation — does not deal
        // the same cards twice.
        val opening = remember(round) {
            if (game.freshlyDealt) dealScenes(session.view.value) else emptyList()
        }
        LaunchedEffect(round) { game.dealShown() }

        CardStage(
            frames = session.frames,
            live = holder.current,
            sizes = layout.sizes,
            pace = pace.scale,
            opening = opening,
            recent = log,
        ) { shown, told ->
            Column(modifier = Modifier.fillMaxSize()) {
                TableScreen(
                    // `shown` rather than the live view: while the bots' moves are being played
                    // out this is the table as it was after the move currently on screen, and it
                    // catches up the moment there is nothing left to animate.
                    state = TableState(
                        view = shown,
                        table = holder.tableFor(shown).withoutStaleTaps(shown, holder.current),
                        refusal = holder.refusal,
                        recent = told,
                        round = round,
                    ),
                    layout = layout,
                    onMove = act,
                    onHelp = help::show,
                    onSettings = onSettings,
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

    // Composed whether or not it is showing: `VintoDialog` animates on `open`, and a dialog
    // that is only composed while visible has nothing to animate *from*.
    ReportProblem(
        open = reported,
        onSend = {
            val report = session.report(at = nowIso(), label = "reported from the table")
            val subject = reportSubject
            if (!shareText(subject, report.toJson())) {
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

    DeckExplained(
        open = deckOpen,
        left = holder.current.drawPileSize,
        onDismiss = { deckOpen = false },
    )

    HelpSheet(open = help.open, now = holder.table.help, focus = help.focus, onDismiss = help::dismiss)

    if (scoreOpen) {
        SoloScore(
            game = game,
            round = round,
            countRound = countRound,
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
 * The score sheet, once the round has one and the player has asked to see it.
 *
 * The caller tests `scoreOpen` first, and deliberately not `game.result?.takeIf { scoreOpen }`.
 *
 * `LocalGame.result` is a plain getter over the session, not snapshot state, so reading it
 * subscribes to nothing. With the null-check first the `takeIf` short-circuits for as long
 * as the round is unfinished — which is nearly all of it — and `scoreOpen` is never read
 * at all, so this scope never subscribes to it either. Pressing "See the score" then set a
 * flag nobody was watching and drew nothing.
 *
 * What hid it is that the table is usually still animating when the round ends: the next
 * frame recomposes this for its own reasons, finds both conditions true, and the sheet
 * appears a beat late looking like pacing. On a table that has stopped — every card
 * landed, nothing queued — there is no next frame, and the button is simply dead.
 */
@Composable
private fun SoloScore(
    game: LocalGame,
    round: Int,
    countRound: (Boolean) -> Unit,
    onNextRound: () -> Unit,
    onQuit: () -> Unit,
) {
    game.result?.let { result ->
        countRound(true)
        RecordRound(result, game.playerId)
        StandingsSheet(
            // Composed only once there is a result to show, so it is showing whenever it
            // exists. `VintoSheet` animates from first composition, so this still rises.
            open = true,
            round = round,
            you = game.playerId,
            result = result,
            standings = game.standings,
            onNextRound = onNextRound,
            onQuit = onQuit,
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
private fun ReportProblem(
    open: Boolean,
    onSend: () -> Unit,
    onCopy: () -> Unit,
    onDismiss: () -> Unit,
) {
    VintoDialog(
        open = open,
        onDismiss = onDismiss,
        title = stringResource(Res.string.report_title),
        body = stringResource(Res.string.report_body),
    ) {
        // All three stacked rather than split across a confirm and a dismiss slot: a row of
        // two with a wrapped third is what that produces, and these are three answers to one
        // question rather than two and an afterthought.
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
    }
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
private fun DeckExplained(open: Boolean, left: Int, onDismiss: () -> Unit) {
    VintoDialog(
        open = open,
        onDismiss = onDismiss,
        title = stringResource(Res.string.deck_title),
        body = stringResource(Res.string.deck_body, left),
    ) {
        GameButton(
            label = stringResource(Res.string.deck_dismiss),
            tone = ButtonTone.NEUTRAL,
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
        )
    }
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
    // The round's one chime, fired when the player is *shown* the end — this strip appears
    // on the shown table, a beat after the engine finished — and once, because this
    // composable exists exactly once per round's ending.
    val sounds = LocalSounds.current
    LaunchedEffect(Unit) { sounds.play(Sfx.CHIME) }

    Surface(modifier = Modifier.fillMaxWidth(), color = Rail.fill) {
        Column(
            modifier = Modifier.padding(Pad).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // A button, not a bar: full width on a phone, capped and centred on a desktop,
            // where a strip-spanning button reads as a banner rather than a control.
            GameButton(
                label = stringResource(Res.string.table_see_score),
                tone = ButtonTone.PLAY,
                onClick = onSee,
                modifier = Modifier.widthIn(max = RoundOverWidth).fillMaxWidth(),
            )
        }
    }
}

/** The widest "See the score" needs to be, on any screen. */
private val RoundOverWidth = 420.dp

/**
 * Counts one solo round, and hands back the way to say it finished.
 *
 * Counted from the screen rather than from the engine, because a round the model finished and
 * the player walked out of is a different fact from one they watched end, and only the screen
 * can tell them apart. Abandonment is therefore the *default*: `DisposableEffect` fires
 * however the screen goes away — a quit, a back gesture, the app being killed mid-recompose —
 * and seeing the score overwrites it first. Once per round, whichever happens.
 */
@Composable
private fun rememberRoundCount(game: LocalGame): (Boolean) -> Unit {
    val counting = LocalCounting.current
    val startedAt = remember(game.round) { elapsedMs() }
    val counted = remember(game.round) { mutableStateOf(false) }

    val count = remember(game.round, counting) {
        fun(finished: Boolean) {
            if (!counted.value) {
                counted.value = true
                counting.record(
                    AnalyticsEvent.SoloRound(
                        finished = finished,
                        difficulty = game.difficulty.counted(),
                        turns = game.session.view.value.turnNumber,
                        durationMs = (elapsedMs() - startedAt).toDouble(),
                    ),
                )
            }
        }
    }

    DisposableEffect(count) { onDispose { count(false) } }
    return count
}

/**
 * Adds this round to what the device remembers about its owner.
 *
 * Written when the score sheet opens rather than when the round ends, so a round the player
 * never looked at is not counted — the same rule `rememberRoundCount` uses for the anonymous
 * count, and for the same reason: a round abandoned mid-way is a different fact from one
 * played out.
 *
 * `remember(result)` makes it once per round. Without it, every recomposition while the sheet
 * is open would add another win.
 */
@Composable
private fun RecordRound(result: RoundResult, viewerId: String) {
    val vault = LocalVault.current
    remember(result) {
        vault?.let { it.saveStats(it.loadStats().plus(result, viewerId) ?: return@let) }
    }
}

/**
 * The help sheet's two facts: whether it is up, and the one card it was asked about — null
 * for the whole reference, which is what the "?" opens.
 */
private class HelpState {
    var open by mutableStateOf(false)
        private set
    var focus by mutableStateOf<Rank?>(null)
        private set

    fun show(rank: Rank?) {
        focus = rank
        open = true
    }

    fun dismiss() {
        open = false
    }
}
