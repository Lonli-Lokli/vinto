package game.vinto.app

import game.vinto.client.Reachability
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * No desktop-wide signal worth trusting: `NetworkInterface` lists adapters, not whether any
 * of them goes anywhere. "Cannot tell" never shuts the door, and the screens behind it keep
 * their own failure handling, which is what a desktop had before and still has.
 */
actual fun platformReachability(): Flow<Reachability> = flowOf(Reachability.UNKNOWN)
