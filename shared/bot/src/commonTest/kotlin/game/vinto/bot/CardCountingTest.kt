package game.vinto.bot

import game.vinto.shapes.Difficulty
import game.vinto.shapes.Rank
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The bot counts cards the way a person at the table can: a full deck, minus what is lying
 * in plain sight on the pile, minus what it trusts itself to have seen in hands. The count
 * is *derived* from that knowledge every time it is asked for, which is what makes it
 * immune to the drift the old running tally suffered — overwritten memories, re-seen
 * cards, unobserved discards, reshuffles.
 */
class CardCountingTest {

    private fun memory(difficulty: Difficulty = Difficulty.HARD, seed: Int = 1) =
        BotMemory("bot", difficulty, Random(seed))

    @Test
    fun aRankFullyAccountedForCanNeverBeDrawn() {
        val memory = memory()
        // Two sevens on the pile, two read in hands: all four accounted for.
        memory.syncVisibleCards(listOf(Rank.SEVEN, Rank.SEVEN))
        memory.observeCard(testCard(Rank.SEVEN, "s3"), "bot", 0)
        memory.observeCard(testCard(Rank.SEVEN, "s4"), "opponent", 1)

        assertEquals(0, memory.getCardDistribution()[Rank.SEVEN])
        repeat(500) {
            assertTrue(memory.sampleCardFromDistribution() != Rank.SEVEN, "drew an exhausted rank")
        }
    }

    @Test
    fun aPileOfSmallCardsRaisesTheExpectedValueOfWhatIsLeft() {
        val memory = memory()
        val fresh = averageRemainingCardValue(memory)

        // The table has burned through its small cards; what remains skews expensive.
        memory.syncVisibleCards(
            listOf(
                Rank.TWO, Rank.TWO, Rank.TWO, Rank.TWO,
                Rank.THREE, Rank.THREE, Rank.THREE, Rank.THREE,
                Rank.JOKER, Rank.JOKER, Rank.ACE, Rank.ACE,
            ),
        )
        val afterSmallDiscards = averageRemainingCardValue(memory)
        assertTrue(
            afterSmallDiscards > fresh,
            "a pile of cheap cards should raise the average of the rest " +
                "($fresh -> $afterSmallDiscards)",
        )

        // A reshuffle folds the pile back under the deck; only its top stays visible, and
        // the same sync that subtracted the pile restores the pool.
        memory.syncVisibleCards(listOf(Rank.TWO))
        val afterReshuffle = averageRemainingCardValue(memory)
        assertTrue(afterReshuffle < afterSmallDiscards, "the reshuffle never reached the pool")
    }

    @Test
    fun anOverwrittenMemoryReturnsItsCardToThePool() {
        val memory = memory()
        memory.observeCard(testCard(Rank.KING, "k1"), "opponent", 0)
        assertEquals(3, memory.getCardDistribution()[Rank.KING])

        // A different card arrives at the same position; the King the memory pointed at is
        // no longer accounted for anywhere, and the pool must say so. The old running tally
        // kept it subtracted forever.
        memory.observeCard(testCard(Rank.QUEEN, "q1"), "opponent", 0)
        assertEquals(4, memory.getCardDistribution()[Rank.KING])
        assertEquals(3, memory.getCardDistribution()[Rank.QUEEN])
    }

    @Test
    fun theSameCardSeenAtANewPositionCountsOnce() {
        val memory = memory()
        memory.observeCard(testCard(Rank.NINE, "n1"), "opponent", 2)
        // The hand shrank and the same nine now sits at position 1; the stale entry is
        // forgotten and the sighting re-recorded — one nine gone from the pool, not two.
        memory.forgetCard("opponent", 2)
        memory.observeCard(testCard(Rank.NINE, "n1"), "opponent", 1)

        assertEquals(3, memory.getCardDistribution()[Rank.NINE])
    }

    @Test
    fun aClearedMemoryStartsFromAFullDeckAgain() {
        val memory = memory()
        memory.syncVisibleCards(listOf(Rank.FIVE, Rank.FIVE))
        memory.observeCard(testCard(Rank.FIVE, "f1"), "bot", 0)
        memory.clear()

        assertEquals(4, memory.getCardDistribution()[Rank.FIVE])
        assertEquals(54, memory.getCardDistribution().values.sum())
    }
}
