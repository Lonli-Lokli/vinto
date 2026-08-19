package game.vinto.bot

import game.vinto.shapes.Difficulty
import game.vinto.shapes.Rank
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Ported from `packages/bot/src/lib/__tests__/mcts-bot-heuristics.test.ts`, plus a case the
 * original could not have: that the Ace decision no longer reads hidden hands.
 */
class BotHeuristicsTest {

    private fun bot(cards: List<Rank>, known: List<Int>? = null) = testPlayer(
        id = "bot-1",
        name = "Bot",
        isHuman = false,
        cards = cards.mapIndexed { i, r -> testCard(r, "${r.serialName}_$i") },
        knownCardPositions = known ?: cards.indices.toList(),
    )

    @Test
    fun alwaysTakesAQueenFromTheDiscard() {
        // Two peeks and an optional swap is the strongest action in the game.
        assertTrue(shouldAlwaysTakeDiscardPeekCard(testCard(Rank.QUEEN, "Q_0"), bot(listOf(Rank.TWO))))
    }

    @Test
    fun takesASevenOrEightOnlyWhileSomethingIsUnknown() {
        val blindSpot = bot(listOf(Rank.TWO, Rank.THREE), known = listOf(0))
        assertTrue(shouldAlwaysTakeDiscardPeekCard(testCard(Rank.SEVEN, "7_0"), blindSpot))

        val knowsEverything = bot(listOf(Rank.TWO, Rank.THREE))
        assertFalse(shouldAlwaysTakeDiscardPeekCard(testCard(Rank.SEVEN, "7_0"), knowsEverything))
    }

    @Test
    fun neverTakesAPlayedCard() {
        val played = testCard(Rank.QUEEN, "Q_0").copy(played = true)
        assertFalse(shouldAlwaysTakeDiscardPeekCard(played, bot(listOf(Rank.TWO))))
        assertFalse(shouldAlwaysTakeDiscardPeekCard(null, bot(listOf(Rank.TWO))))
    }

    @Test
    fun tossesInOnlyKnownMatchingCards() {
        val known = bot(listOf(Rank.SEVEN, Rank.TWO))
        assertTrue(shouldParticipateInTossIn(listOf(Rank.SEVEN), known))
        assertFalse(shouldParticipateInTossIn(listOf(Rank.KING), known))

        // A matching card the bot has not seen is a guess, and a wrong guess costs a penalty
        // card and its whole participation for the round.
        val unseen = bot(listOf(Rank.SEVEN, Rank.TWO), known = listOf(1))
        assertFalse(shouldParticipateInTossIn(listOf(Rank.SEVEN), unseen))
    }

    @Test
    fun swapsAnAceRatherThanPlayItWhenHoldingSomethingExpensive() {
        val memory = BotMemory("bot-1", Difficulty.HARD, Random(1))
        val holder = bot(listOf(Rank.TEN, Rank.TWO))
        assertFalse(shouldUseAceAction(holder, listOf(holder), "bot-1", memory))
    }

    @Test
    fun theAceDecisionUsesBeliefsNotHiddenHands() {
        // TypeScript summed every opponent's real cards here. With an empty memory the bot
        // knows nothing about anyone, so it cannot identify a vulnerable opponent and must
        // decline — a bot reading the table would say yes.
        val memory = BotMemory("bot-1", Difficulty.HARD, Random(1))
        val self = bot(listOf(Rank.TWO, Rank.THREE))
        val nearlyDone = testPlayer(
            id = "p2",
            name = "Opponent",
            isHuman = false,
            cards = listOf(testCard(Rank.JOKER, "Joker1"), testCard(Rank.KING, "K_0")),
        )

        assertFalse(
            shouldUseAceAction(self, listOf(self, nearlyDone), "bot-1", memory),
            "the bot acted on a hand it had never seen",
        )
    }

    @Test
    fun countsUnknownCards() {
        assertTrue(countUnknownCards(bot(listOf(Rank.TWO, Rank.THREE), known = listOf(0))) == 1)
        assertTrue(countUnknownCards(bot(listOf(Rank.TWO, Rank.THREE))) == 0)
    }
}
