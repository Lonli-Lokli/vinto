package game.vinto.bot

import game.vinto.shapes.Difficulty
import game.vinto.shapes.Rank
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Memory is how difficulty is implemented — the bot thinks identically at every level and
 * only remembers differently — so these are really tests of what "easy" means.
 */
class BotMemoryTest {

    @Test
    fun aHardBotRemembersPerfectly() {
        val memory = BotMemory("bot-1", Difficulty.HARD, Random(1))
        memory.observeCard(testCard(Rank.KING, "K_0"), "p2", 0)

        val remembered = memory.getCardMemory("p2", 0)
        assertNotNull(remembered)
        assertEquals(Rank.KING, remembered.card.rank)
    }

    @Test
    fun anEasyBotSometimesFailsToRemember() {
        // Accuracy is 0.4, so over many sightings most are lost. The point of the difficulty
        // setting is a worse memory, not worse judgement.
        val memory = BotMemory("bot-1", Difficulty.EASY, Random(7))
        repeat(40) { i -> memory.observeCard(testCard(Rank.TWO, "2_$i"), "p2", i) }

        assertTrue(memory.getMemorySize() < 40, "an easy bot remembered everything")
    }

    @Test
    fun memoryIsReproducibleFromASeed() {
        // The whole reason Random is injected: the same seed must play the same game.
        fun run(): Int {
            val memory = BotMemory("bot-1", Difficulty.EASY, Random(99))
            repeat(30) { i -> memory.observeCard(testCard(Rank.FIVE, "5_$i"), "p2", i) }
            repeat(5) { memory.processTurnBoundary() }
            return memory.getMemorySize()
        }
        assertEquals(run(), run())
    }

    @Test
    fun seeingACardTwiceRaisesConfidence() {
        val memory = BotMemory("bot-1", Difficulty.HARD, Random(1))
        memory.observeCard(testCard(Rank.NINE, "9_0"), "p2", 0)
        val first = memory.getConfidence("p2", 0)

        memory.observeCard(testCard(Rank.NINE, "9_0"), "p2", 0)
        assertTrue(memory.getConfidence("p2", 0) > first)
    }

    @Test
    fun aSeenCardLeavesTheUnaccountedPool() {
        val memory = BotMemory("bot-1", Difficulty.HARD, Random(1))
        val before = memory.getCardDistribution().getValue(Rank.KING)

        memory.observeCard(testCard(Rank.KING, "K_0"), "p2", 0)
        assertEquals(before - 1, memory.getCardDistribution().getValue(Rank.KING))

        // Forgetting puts it back: the bot no longer knows where it is.
        memory.forgetCard("p2", 0)
        assertEquals(before, memory.getCardDistribution().getValue(Rank.KING))
    }

    @Test
    fun anEasyBotCannotHoldMoreThanItsLimit() {
        val memory = BotMemory("bot-1", Difficulty.EASY, Random(3))
        repeat(20) { i -> memory.observeCard(testCard(Rank.THREE, "3_$i"), "p2", i) }

        assertTrue(memory.getMemorySize() <= 4, "easy memory exceeded its four-card limit")
    }

    @Test
    fun estimatesUseBeliefsRatherThanFacts() {
        val memory = BotMemory("bot-1", Difficulty.HARD, Random(1))
        // Nothing observed: the estimate is five average cards, not zero.
        val blind = estimatePlayerScore(handSize = 5, botMemory = memory, playerId = "p2")
        assertTrue(blind > 0, "an unseen hand was estimated at $blind")

        memory.observeCard(testCard(Rank.JOKER, "Joker1"), "p2", 0)
        val informed = estimatePlayerScore(handSize = 5, botMemory = memory, playerId = "p2")
        assertTrue(informed < blind, "seeing a Joker should lower the estimate")
    }

    @Test
    fun theDistributionStartsAsAFullDeck() {
        val memory = BotMemory("bot-1", Difficulty.HARD, Random(1))
        val distribution = memory.getCardDistribution()

        assertEquals(2, distribution.getValue(Rank.JOKER))
        assertEquals(4, distribution.getValue(Rank.KING))
        assertEquals(54, distribution.values.sum())
    }

    @Test
    fun samplingDrawsOnlyFromWhatIsLeft() {
        val memory = BotMemory("bot-1", Difficulty.HARD, Random(5))
        val sampled = List(200) { memory.sampleCardFromDistribution() }
        assertTrue(sampled.all { it != null })
    }

    @Test
    fun clearingForgetsEverything() {
        val memory = BotMemory("bot-1", Difficulty.HARD, Random(1))
        memory.observeCard(testCard(Rank.ACE, "A_0"), "p2", 0)
        memory.clear()

        assertNull(memory.getCardMemory("p2", 0))
        assertEquals(0, memory.getMemorySize())
        assertEquals(54, memory.getCardDistribution().values.sum())
    }
}
