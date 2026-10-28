package game.vinto.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import game.vinto.app.crash.Crashes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob

/**
 * The scope everything long-lived rides on: the socket loop, the analytics sink, a room's
 * reconnects.
 *
 * With a handler, so a coroutine that fails out here is *reported* rather than printed to a
 * console nobody is reading — these are exactly the failures that leave the app looking fine and
 * doing nothing, which no fatal handler will ever see.
 *
 * **And supervised, which Compose's own scope is not.** `rememberCoroutineScope` gives its scope a
 * plain `Job` whose parent is the Recomposer's effect job, and a plain job is cancelled by a child
 * that fails and cancels its parent in turn — so one failed send ended the Recomposer, and the
 * screen stayed as it was, deaf to every tap, with the handler's report the only sign
 * (`OneFailedJobLeavesTheAppRunningTest`). A `SupervisorJob` under it keeps the lifetime — it is
 * still cancelled when the app leaves the composition — and stops a failure going further up.
 */
@Composable
internal fun rememberAppScope(): CoroutineScope {
    val composition = rememberCoroutineScope { Crashes.handler() }
    return remember(composition) {
        CoroutineScope(composition.coroutineContext + SupervisorJob(composition.coroutineContext[Job]))
    }
}
