package game.vinto.bot

import game.vinto.shapes.Card
import game.vinto.shapes.Difficulty
import game.vinto.shapes.Rank
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

/**
 * A bot that has just thrown a card in must not throw the card that slid into its place.
 *
 * Reported from a phone: Mikey tossed in a J, correctly, and then tossed in a Q as a J and
 * took the penalty. A successful toss removes the card and renumbers everything after it;
 * the bot's memory kept the index it was written with, so "position p is a J" outlived the
 * J and now described the Q that had moved into p. The refresh before each decision did
 * re-read the position — but on easy and moderate a read silently fails some of the time,
 * and a failed read left the stale belief standing rather than nothing.
 */
class TossInRenumbersMemoryTest {

    /**
     * A die whose observation rolls are scripted: with [fail] set every roll is as high as
     * it goes, so a read on a fallible difficulty misses; otherwise every roll is zero and
     * it lands. Forgetting rolls read the same die and go the way that keeps the memory
     * still, so what the test sees is the refresh alone.
     */
    private class ScriptedRandom : Random() {
        var fail = false
        override fun nextBits(bitCount: Int): Int = if (fail) ((1L shl bitCount) - 1).toInt() else 0
    }

    private fun contextWith(hand: List<Card>, known: List<Int> = hand.indices.toList()): BotDecisionContext {
        val bot = testPlayer("bot", "Bot", isHuman = false, cards = hand, knownCardPositions = known)
        val state = testState(
            players = listOf(bot, testPlayer("p2", "P2", isHuman = true)),
            turnNumber = 1,
        )
        return botContext("bot", state)
    }

    @Test
    fun aMissedGlanceAfterAThrowDoesNotLeaveTheOldCardBelievedAtThatPosition() {
        val die = ScriptedRandom()
        val service = MctsBotDecisionService(Difficulty.MODERATE, die)

        val before = contextWith(
            listOf(testCard(Rank.JACK, "j"), testCard(Rank.QUEEN, "q"), testCard(Rank.FIVE, "f")),
        )
        assertEquals(
            mapOf(0 to Rank.JACK, 1 to Rank.QUEEN, 2 to Rank.FIVE),
            service.believedOwnCards(before),
            "the bot did not read its own hand",
        )

        // The J is thrown in and the Q slides into its place — and this glance misses.
        die.fail = true
        val after = contextWith(listOf(testCard(Rank.QUEEN, "q"), testCard(Rank.FIVE, "f")))
        val believed = service.believedOwnCards(after)

        assertNotEquals(Rank.JACK, believed[0], "the thrown J is still believed to be at position 0")
        for ((position, rank) in believed) {
            assertEquals(
                after.botPlayer.cards[position].rank,
                rank,
                "position $position is believed to be a $rank and is not",
            )
        }
    }

    @Test
    fun aThrownCardsPositionIsNotBelievedWhenAnUnreadCardSlidIntoIt() {
        val service = MctsBotDecisionService(Difficulty.HARD, Random(1))

        // The J at 0 and the 5 at 2 have been read; the card between them never was.
        val before = contextWith(
            listOf(testCard(Rank.JACK, "j"), testCard(Rank.QUEEN, "q"), testCard(Rank.FIVE, "f")),
            known = listOf(0, 2),
        )
        assertEquals(mapOf(0 to Rank.JACK, 2 to Rank.FIVE), service.believedOwnCards(before))

        // The J is thrown in; the unread card is at 0 now, and the engine says so.
        val after = contextWith(
            listOf(testCard(Rank.QUEEN, "q"), testCard(Rank.FIVE, "f")),
            known = listOf(1),
        )
        val believed = service.believedOwnCards(after)

        assertFalse(0 in believed, "a card the bot has never read is believed to be the J it threw")
        assertEquals(mapOf(1 to Rank.FIVE), believed)
    }
}
