package game.vinto.client

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The record of having said thanks — the one number a reinstall must not take away.
 *
 * `Stats` beside it is a record of *play* and it is clearable, because a bad run is somebody's
 * own business. This is a record of having paid, and the product owner's decision is that
 * "Clear it" must not touch it: a control for forgetting a losing streak that also quietly
 * erased four acts of generosity would be the wrong thing happening for a right-sounding reason.
 *
 * So it is its own key, and everything below is about the two ways it could still be lost.
 */
class ThanksTest {

    @Test
    fun aQuantityOfThreeIsThreeThanksRatherThanOnePurchase() {
        // What Play's own quantity selector produces: one transaction, one token, quantity 3.
        // The alternative reading — one purchase, one thank-you — would show a player who gave
        // three times in one tap a smaller number than one who crossed three sheets for the
        // same money.
        assertEquals(3, Thanks().plus(3).given)
        assertEquals(4, Thanks().plus(3).plus(1).given)
    }

    /** A store answering something absurd must not be able to make the number go backwards. */
    @Test
    fun aNonsenseQuantityCannotSubtractFromWhatIsAlreadyThere() {
        assertEquals(2, Thanks().plus(2).plus(0).given)
        assertEquals(2, Thanks().plus(2).plus(-5).given)
    }

    @Test
    fun itSurvivesTheRoundTripThroughAVault() {
        val vault = MemoryVault()
        vault.saveThanks(Thanks().plus(2))

        assertEquals(2, vault.loadThanks().given)
    }

    /**
     * A file this app wrote badly must not be a reason it cannot start — the [loadStats] rule,
     * for the same reason and with the same answer.
     */
    @Test
    fun rubbishInTheVaultReadsAsNoneRatherThanThrowing() {
        val vault = MemoryVault()
        vault.write("vinto.thanks", "{ not json at all")

        assertEquals(0, vault.loadThanks().given)
    }

    /**
     * The merge, and the whole reason there are two stores.
     *
     * A reinstall leaves the device's own copy empty while the one that outlived it is not, and
     * a device with no cloud store at all leaves it the other way round. Taking the larger is the
     * right answer to both, and it needs no clock, no conflict resolution and no ordering: the
     * number only ever goes up, so the bigger of two readings is always the truer one.
     */
    @Test
    fun theLargerOfTwoStoresWinsSoAReinstallDoesNotLoseTheCount() {
        assertEquals(4, Thanks().plus(4).merge(Thanks()).given)
        assertEquals(4, Thanks().merge(Thanks().plus(4)).given)
        assertEquals(4, Thanks().plus(1).merge(Thanks().plus(4)).given)
    }

    /**
     * Forgetting the game record leaves this alone.
     *
     * Held here rather than trusted to the two functions living in different files: they share a
     * vault, and the way this breaks is somebody widening `forgetStats` to "clear everything
     * local" without noticing what else moved in beside it.
     */
    @Test
    fun clearingTheGameRecordDoesNotClearTheThanks() {
        val vault = MemoryVault()
        vault.saveStats(Stats().plus(hand = 3, won = true))
        vault.saveThanks(Thanks().plus(2))

        vault.forgetStats()

        assertEquals(0, vault.loadStats().roundsPlayed)
        assertEquals(2, vault.loadThanks().given)
    }
}
