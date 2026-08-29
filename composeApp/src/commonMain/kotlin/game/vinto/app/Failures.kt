package game.vinto.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import game.vinto.client.ConnectionState
import game.vinto.protocol.AnalyticsEvent
import game.vinto.protocol.FailureKind
import game.vinto.protocol.Surface
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

/**
 * Which part of the app is on screen, for the events that are about *where* rather than what.
 *
 * A composition local rather than a parameter because the thing that needs it is buried:
 * `CardStage` is the same code under a solo round, an online one and the lesson, and a stall
 * it reports is only worth having if it says which of the three it stalled in. Threading a
 * surface down through the table's whole parameter list to reach it would put plumbing in
 * every signature for the sake of one call.
 *
 * The default is the menu, which is where the app starts and the only surface that draws no
 * table at all.
 */
val LocalSurface = staticCompositionLocalOf { Surface.MENU }

/**
 * How many failed connection attempts amount to "lost" rather than "trying".
 *
 * `RemoteRoom` never gives up — the seat is held by a vaulted token, so retrying forever is
 * the right behaviour and there is no moment where the code declares defeat. What there is,
 * is a moment where the *player* has plainly lost the room, and that is what this names.
 * Five attempts is the backoff's 1 + 2 + 4 + 8 s, so roughly a quarter-minute of a table that
 * will not come back — long past a lift shaft or a tunnel, and short enough to still be about
 * the round they were in.
 */
const val LOST_AFTER_ATTEMPTS = 5

/** Whether the room has been away long enough that the player has lost it, not just blinked. */
fun ConnectionState.looksLost(): Boolean =
    this is ConnectionState.Reconnecting && attempt >= LOST_AFTER_ATTEMPTS

/**
 * Counts a connection the player has lost, once per screen.
 *
 * Once, because a socket that cannot reopen goes on failing at fifteen-second intervals for
 * as long as the app is left on the table, and a hundred identical points describe the same
 * one bad evening.
 */
@Composable
fun CountConnectionTrouble(connection: ConnectionState) {
    val counting = LocalCounting.current
    val surface = LocalSurface.current
    val reported = remember { mutableStateOf(false) }

    LaunchedEffect(connection.looksLost()) {
        if (connection.looksLost() && !reported.value) {
            reported.value = true
            counting.record(AnalyticsEvent.Failure(FailureKind.SOCKET_LOST, surface))
        }
    }
}

/**
 * Counts a move the engine refused.
 *
 * Every refusal is a defect by construction: the controls are drawn from the same `Table` the
 * validator judges, so a button the player could reach is a move the engine agreed to before
 * it was offered. A refusal therefore means the two disagreed — which the player experiences
 * as a tap that did nothing but print a line of small text, and which nobody would otherwise
 * hear about. Counting the rate is the whole point; the reason is not carried, because a
 * refusal string is written for a person and is exactly the free text §A2 forbids.
 */
@Composable
fun CountRefusals(refusal: String?) {
    val counting = LocalCounting.current
    val surface = LocalSurface.current

    LaunchedEffect(refusal) {
        if (refusal != null) counting.record(AnalyticsEvent.Failure(FailureKind.MOVE_REFUSED, surface))
    }
}

/**
 * How long a batch may sit without one move of it finishing before it counts as stuck.
 *
 * Not scaled by the pace setting, deliberately: the slowest legitimate move on the calm
 * setting is a few seconds, so thirty leaves an order of magnitude of headroom and does not
 * need to know what the animation layer's constants are today. It is a "this is not coming
 * back" threshold, not a performance budget.
 */
const val STAGE_STALL_MS = 30_000L

/** What the stage is doing, as the watchdog needs to see it. */
data class StageActivity(val draining: Boolean, val progress: Int)

/**
 * Reports the first time the stage stops making progress while it still has moves to play.
 *
 * Progress rather than elapsed time. A batch of eleven bot moves at the calm pace takes the
 * best part of a minute and is perfectly healthy; a batch that has not finished a single move
 * in thirty seconds has stopped. `collectLatest` is the whole mechanism — every change to
 * either field cancels the pending wait and starts it again, so the timer only ever runs out
 * on a stage that has genuinely gone quiet with work left.
 *
 * Once per screen. A wedged stage stays wedged, and a hundred identical points describe one
 * bad evening.
 */
suspend fun reportStalls(
    activity: Flow<StageActivity>,
    stallMs: Long = STAGE_STALL_MS,
    report: () -> Unit,
) {
    var reported = false
    activity.collectLatest { now ->
        if (!now.draining || reported) return@collectLatest
        delay(stallMs)
        reported = true
        report()
    }
}

/** Wires [reportStalls] to the app's counter, for the stage to call with what it is doing. */
@Composable
fun CountStalls(draining: Boolean, progress: Int) {
    val counting = LocalCounting.current
    val surface = LocalSurface.current
    val now = rememberUpdatedState(StageActivity(draining, progress))

    LaunchedEffect(counting, surface) {
        reportStalls(snapshotFlow { now.value }) {
            counting.record(AnalyticsEvent.Failure(FailureKind.STAGE_STALLED, surface))
        }
    }
}
