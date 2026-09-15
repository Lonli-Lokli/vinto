package game.vinto.app

import game.vinto.client.Vault
import platform.Foundation.NSUbiquitousKeyValueStore

/**
 * iCloud's key-value store, which a deleted app does not take with it.
 *
 * The iOS half of the reinstall promise, and the platform's own documented answer for exactly
 * this size of thing: a few small values that belong to the person rather than to the device. It
 * syncs across their devices as a side effect, which for this number is right — somebody who
 * said thanks on a phone did not do it on behalf of that phone.
 *
 * **Why not the keychain**, which also survives a delete: that survival is a well-known
 * behaviour rather than a documented promise, and Apple has changed its mind about it before. A
 * counter is not a credential, and building on an undocumented edge to store a memento is
 * borrowing a risk for no benefit.
 *
 * **Why not StoreKit's own history**: there is none to read. A consumable that has been finished
 * is gone from the receipt, exactly as a consumed purchase is gone from Play's
 * `queryPurchasesAsync`. Neither store will tell an app how many times somebody tipped.
 *
 * ### Absent-safe, which is the whole reason this can ship today
 *
 * The store needs the `com.apple.developer.ubiquity-kvstore-identifier` entitlement *and* a
 * player signed into iCloud. With either missing it reads nothing and writes nothing — no throw,
 * no dialog — and `thanksGiven` merges that empty reading with the local one and gets the local
 * one. So the app behaves exactly as it did before on a device that cannot use it, which is the
 * same arrangement every telemetry path here already has.
 *
 * The entitlement is declared in `iosApp/iosApp/iosApp.entitlements`, beside
 * `associated-domains` and for the reason written there: adding a capability changes
 * *provisioning*, and doing that during a release is how a build stops signing on the afternoon
 * it was meant to ship. Turning it on in the developer portal is the remaining step, and it
 * needs no code change when it happens.
 */
actual fun enduringVault(): Vault? = CloudVault()

private class CloudVault : Vault {
    private val store = NSUbiquitousKeyValueStore.defaultStore

    override fun read(key: String): String? = store.stringForKey(key)

    /**
     * `synchronize` writes to disk; it does not wait for the network and cannot fail visibly.
     * iCloud uploads in its own time, which is the right trade for a number nothing blocks on.
     */
    override fun write(key: String, value: String) {
        store.setString(value, key)
        store.synchronize()
    }

    override fun erase(key: String) {
        store.removeObjectForKey(key)
        store.synchronize()
    }
}
