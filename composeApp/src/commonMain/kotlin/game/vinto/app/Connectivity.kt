package game.vinto.app

import androidx.compose.runtime.staticCompositionLocalOf
import game.vinto.client.Reachability
import kotlinx.coroutines.flow.Flow

/**
 * What the platform knows about the network: the answer now, then every change.
 *
 * A flow rather than a function, unlike [systemPrefersReducedMotion], because this one
 * changes while the app is open — a phone walks into a lift — and the menu has to follow it
 * without being reopened. Each platform has its own way of saying so and each is a few lines:
 * Android's default-network callback, iOS's `NWPathMonitor`, the browser's `online` and
 * `offline` events. The desktop has nothing dependable and answers "cannot tell", which never
 * shuts a door; see [game.vinto.client.Reachability] for why that answer exists.
 *
 * Read once in `App` and handed down as [LocalReachability], for the same reason the motion
 * preference is: the one screen that reads it is the online menu, and threading a parameter
 * through everything between `App` and it would be plumbing for a single call site.
 */
expect fun platformReachability(): Flow<Reachability>

/**
 * The network as the online menu sees it. `UNKNOWN` by default, and that is the useful part:
 * a screen rendered in a test, a preview or a golden says nothing about a network nobody
 * described, rather than announcing one is missing.
 */
val LocalReachability = staticCompositionLocalOf { Reachability.UNKNOWN }
