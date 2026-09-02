package game.vinto.app

import game.vinto.client.Reachability
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_cancel
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.Network.nw_path_status_unsatisfied
import platform.darwin.dispatch_get_main_queue

/**
 * iOS says it through `NWPathMonitor`, which reports the first path as soon as it starts and
 * every change after. `satisfied` is a network; `unsatisfied` is aeroplane mode or nothing
 * connected; the other two statuses — invalid, and "satisfiable" by bringing an interface up,
 * as an on-demand VPN would — are honestly "cannot tell", and never shut the door.
 *
 * Updates are delivered on the main queue so the state they land in is the one the
 * composition reads.
 */
@OptIn(ExperimentalForeignApi::class)
actual fun platformReachability(): Flow<Reachability> = callbackFlow {
    val monitor = nw_path_monitor_create()
    nw_path_monitor_set_update_handler(monitor) { path ->
        trySend(
            when (nw_path_get_status(path)) {
                nw_path_status_satisfied -> Reachability.ONLINE
                nw_path_status_unsatisfied -> Reachability.OFFLINE
                else -> Reachability.UNKNOWN
            },
        )
    }
    nw_path_monitor_set_queue(monitor, dispatch_get_main_queue())
    nw_path_monitor_start(monitor)
    awaitClose { nw_path_monitor_cancel(monitor) }
}
