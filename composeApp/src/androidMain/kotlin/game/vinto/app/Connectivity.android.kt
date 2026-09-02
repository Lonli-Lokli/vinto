package game.vinto.app

import android.net.ConnectivityManager
import android.net.Network
import game.vinto.client.Reachability
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Android says it through the default-network callback. Every callback re-reads whether a
 * default network exists rather than trusting which callback it was: when Wi-Fi hands over to
 * cellular the platform's `onLost` for the old one may land after `onAvailable` for the new,
 * and a menu that went "offline" on that `onLost` would stay offline until the next change.
 *
 * Read through the application context [AndroidStorage] holds; before `attach` has run there
 * is nothing to ask and the answer is "cannot tell", which never shuts the door. Needs
 * `ACCESS_NETWORK_STATE`, a normal permission the manifest declares beside `INTERNET`.
 */
actual fun platformReachability(): Flow<Reachability> = callbackFlow {
    val manager = AndroidStorage.context?.getSystemService(ConnectivityManager::class.java)
    if (manager == null) {
        trySend(Reachability.UNKNOWN)
        awaitClose()
    } else {
        fun now(): Reachability =
            if (manager.activeNetwork != null) Reachability.ONLINE else Reachability.OFFLINE

        trySend(now())
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(now())
            }

            override fun onLost(network: Network) {
                trySend(now())
            }
        }
        manager.registerDefaultNetworkCallback(callback)
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }
}
