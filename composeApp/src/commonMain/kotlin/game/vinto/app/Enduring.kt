package game.vinto.app

import game.vinto.client.Thanks
import game.vinto.client.Vault
import game.vinto.client.loadThanks
import game.vinto.client.saveThanks

/**
 * Storage that outlives the app being deleted, where the platform has any.
 *
 * The ordinary [Vault] does not promise this. It is `SharedPreferences` on Android and
 * `NSUserDefaults` on iOS, and a deleted app takes both with it — so a count of how many times
 * somebody has said thanks, which is the one number here that is *theirs* rather than the
 * device's, would be lost by a phone upgrade or a reinstall.
 *
 * ### Why it is null on Android rather than a second store
 *
 * **Android's ordinary vault already endures.** `AndroidManifest.xml` sets `allowBackup`, and
 * both `backup_rules.xml` and `backup_rules_legacy.xml` name `vinto.xml` — the very file
 * `AndroidStorage` writes — so Auto Backup and device-to-device transfer already carry it to
 * the next install. Adding a second Android store would be a second copy of a solved problem,
 * and the merge below would have nothing to reconcile. Null here says "the one you have is the
 * durable one", which is a true statement rather than a missing feature.
 *
 * ### And why it is not a server
 *
 * There are no accounts, and `MONETIZATION.md` is emphatic that this purchase needs none. A
 * count kept server-side would need something to key it by, and the only candidate is the guest
 * id — which lives in the same vault and dies in the same reinstall. Each platform's own cloud
 * is the thing that already knows who this is without this app being told.
 *
 * ### What neither store can do
 *
 * Neither is a receipt. **A consumable's purchase history is not retrievable from either store**
 * — Play's `queryPurchasesAsync` returns only what is *unconsumed*, and a consumable finished
 * with StoreKit leaves the receipt — so this count cannot be rebuilt from the stores if both
 * copies go. It is a memento, it unlocks nothing, and nothing checks it. That is what makes an
 * imperfect answer the right size of answer.
 */
expect fun enduringVault(): Vault?

/**
 * What this player has given, taking the truer of the two readings.
 *
 * The larger, and that needs no clock and no conflict resolution: the count only ever goes up
 * (`Thanks.merge`), so a reinstall — local empty, cloud at four — and a device with no cloud at
 * all — local at four, cloud empty — are the same question with the same answer.
 */
fun thanksGiven(local: Vault, enduring: Vault? = enduringVault()): Thanks =
    local.loadThanks().merge(enduring?.loadThanks() ?: Thanks())

/**
 * Records [count] more thanks, in both stores.
 *
 * Merged *before* adding rather than after, so a device that has just been restored adds to what
 * it was restored with instead of starting again from its own empty copy.
 */
fun recordThanks(count: Int, local: Vault, enduring: Vault? = enduringVault()): Thanks {
    val next = thanksGiven(local, enduring).plus(count)
    local.saveThanks(next)
    enduring?.saveThanks(next)
    return next
}
