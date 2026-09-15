package game.vinto.app

import game.vinto.client.MemoryVault
import game.vinto.client.Thanks
import game.vinto.client.loadThanks
import game.vinto.client.saveThanks
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The count of thanks survives the app being deleted and installed again.
 *
 * The product owner's requirement, in as many words: it must survive a reinstall. The ordinary
 * vault cannot promise that on its own — `NSUserDefaults` goes with a deleted app — so there are
 * two stores on iOS and the interesting behaviour is entirely in how they are reconciled.
 *
 * Android needs no second store and has none: its vault file is already in `backup_rules.xml`.
 * `enduringVault()` answering null there is the case every test below covers as "no cloud".
 */
class EnduringThanksTest {

    @Test
    fun aReinstallReadsTheCountBackOffTheStoreThatOutlivedIt() {
        val cloud = MemoryVault()
        cloud.saveThanks(Thanks().plus(4))

        // What a fresh install looks like: the device's own store is empty and the cloud is not.
        val local = MemoryVault()

        assertEquals(4, thanksGiven(local, cloud).given)
    }

    @Test
    fun aDeviceWithNoCloudStoreAtAllStillCountsItsOwn() {
        val local = MemoryVault()
        local.saveThanks(Thanks().plus(2))

        // Android, and every platform that answers null — the vault it already has is the durable
        // one, so there is nothing to merge and nothing to lose.
        assertEquals(2, thanksGiven(local, enduring = null).given)
    }

    @Test
    fun givingAgainAfterARestoreAddsToWhatWasRestoredRatherThanStartingOver() {
        val cloud = MemoryVault()
        cloud.saveThanks(Thanks().plus(4))
        val local = MemoryVault()

        val after = recordThanks(count = 1, local = local, enduring = cloud)

        assertEquals(5, after.given, "the restored four were dropped on the way past")
        assertEquals(5, local.loadThanks().given, "the device's own copy was not brought up to date")
        assertEquals(5, cloud.loadThanks().given, "the durable copy was not written")
    }

    /**
     * A multi-quantity purchase lands as one number, not one thank-you.
     *
     * The end of the plumbing that starts at `Purchase.getQuantity()`: if any hop between Play's
     * sheet and this line dropped it, this is where three would read as one.
     */
    @Test
    fun aQuantityOfThreeReachesTheCountAsThree() {
        val local = MemoryVault()

        assertEquals(3, recordThanks(count = 3, local = local, enduring = null).given)
    }

    /** A refused or cancelled purchase answers 0, and must move nothing. */
    @Test
    fun aPurchaseThatDidNotHappenChangesNothing() {
        val local = MemoryVault()
        local.saveThanks(Thanks().plus(2))

        assertEquals(2, recordThanks(count = 0, local = local, enduring = null).given)
    }
}
