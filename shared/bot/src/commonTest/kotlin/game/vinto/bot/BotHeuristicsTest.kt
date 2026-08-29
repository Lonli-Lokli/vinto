package game.vinto.bot

import game.vinto.shapes.Difficulty
import game.vinto.shapes.PlayerState
import game.vinto.shapes.Rank
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The heuristics that shortcut the search, ported from
 * `legacy-web/packages/bot/src/lib/__tests__/mcts-bot-heuristics.test.ts`.
 *
 * These fire *before* MCTS and override it, so a wrong one is not a slightly worse bot — it
 * is a bot that never considers the alternative at all. That is why the coverage here is so
 * fine-grained: every rank that reaches these functions gets its own case, because the thing
 * being tested is a lookup table and a table gets one entry wrong at a time.
 *
 * One departure from the original, and it is the reason the old bot engine was deleted:
 * `shouldUseAceAction` in TypeScript reads every opponent's real hand. The Kotlin one takes a
 * [BotMemory] and estimates from what the bot has actually seen, so the scenarios below are
 * set up by *showing* it cards rather than by handing it the table.
 */
class BotHeuristicsTest {

    private val botId = "bot1"

    private fun bot(cards: List<Rank>, known: List<Int>? = null): PlayerState {
        val hand = cards.mapIndexed { index, rank -> testCard(rank, "c$index") }
        return testPlayer(
            botId,
            "Bot",
            isHuman = false,
            cards = hand,
            knownCardPositions = known ?: hand.indices.toList(),
        )
    }

    private fun opponent(id: String, cards: List<Rank>): PlayerState =
        testPlayer(
            id,
            id,
            isHuman = false,
            cards = cards.mapIndexed { index, rank -> testCard(rank, "$id-c$index") },
        )

    private fun memoryOf(vararg seen: Triple<String, Int, Rank>): BotMemory {
        val memory = BotMemory(botId, Difficulty.HARD, Random(1))
        seen.forEach { (playerId, position, rank) ->
            memory.observeCard(testCard(rank, "$playerId-c$position"), playerId, position)
        }
        return memory
    }

    // --- shouldAlwaysTakeDiscardPeekCard --------------------------------------------------

    @Test
    fun anUnusedQueenIsAlwaysWorthTakingWhateverTheBotKnows() {
        val queen = testCard(Rank.QUEEN, "discard-q")
        val fullyRead = bot(listOf(Rank.TWO, Rank.THREE, Rank.FOUR, Rank.FIVE, Rank.SIX))
        val partlyRead = bot(
            listOf(Rank.TWO, Rank.THREE, Rank.FOUR, Rank.FIVE, Rank.SIX),
            known = listOf(0, 1),
        )

        // A Queen sees two cards and may swap them, which is worth having even with nothing
        // of its own left to learn.
        assertTrue(shouldAlwaysTakeDiscardPeekCard(queen, fullyRead))
        assertTrue(shouldAlwaysTakeDiscardPeekCard(queen, partlyRead))
    }

    @Test
    fun aPlayedQueenIsNotWorthTaking() {
        val played = testCard(Rank.QUEEN, "discard-q").copy(played = true)
        assertFalse(shouldAlwaysTakeDiscardPeekCard(played, bot(listOf(Rank.TWO, Rank.THREE))))
    }

    @Test
    fun aSevenOrEightIsWorthTakingOnlyWhileSomethingIsUnread() {
        val unread = bot(listOf(Rank.TWO, Rank.THREE, Rank.FOUR), known = listOf(0))
        val fullyRead = bot(listOf(Rank.TWO, Rank.THREE, Rank.FOUR))

        assertTrue(shouldAlwaysTakeDiscardPeekCard(testCard(Rank.SEVEN, "d7"), unread))
        assertTrue(shouldAlwaysTakeDiscardPeekCard(testCard(Rank.EIGHT, "d8"), unread))

        // Nothing left to look at, so the peek is worth nothing and the card costs 7 or 8.
        assertFalse(shouldAlwaysTakeDiscardPeekCard(testCard(Rank.SEVEN, "d7"), fullyRead))
        assertFalse(shouldAlwaysTakeDiscardPeekCard(testCard(Rank.EIGHT, "d8"), fullyRead))
    }

    @Test
    fun aPlayedSevenIsNotWorthTakingEvenWithBlindSpots() {
        val played = testCard(Rank.SEVEN, "d7").copy(played = true)
        val unread = bot(listOf(Rank.TWO, Rank.THREE, Rank.FOUR), known = listOf(0))

        assertFalse(shouldAlwaysTakeDiscardPeekCard(played, unread))
    }

    @Test
    fun cardsThisHeuristicDoesNotClaimAreLeftToTheSearch() {
        val unread = bot(listOf(Rank.TWO, Rank.THREE, Rank.FOUR), known = listOf(0))

        // No action at all:
        assertFalse(shouldAlwaysTakeDiscardPeekCard(testCard(Rank.JOKER, "dj"), unread))
        assertFalse(shouldAlwaysTakeDiscardPeekCard(testCard(Rank.FIVE, "d5"), unread))
        // An action, but one whose value depends on the position — MCTS decides these.
        assertFalse(shouldAlwaysTakeDiscardPeekCard(testCard(Rank.KING, "dk"), unread))
        assertFalse(shouldAlwaysTakeDiscardPeekCard(testCard(Rank.JACK, "dja"), unread))
    }

    @Test
    fun anEmptyPileOrAnEmptyHandDecidesNothing() {
        assertFalse(shouldAlwaysTakeDiscardPeekCard(null, bot(listOf(Rank.TWO))))
        assertFalse(shouldAlwaysTakeDiscardPeekCard(testCard(Rank.SEVEN, "d7"), bot(emptyList())))
    }

    // --- shouldAlwaysUsePeekAction --------------------------------------------------------

    @Test
    fun aDrawnQueenIsAlwaysWorthPlaying() {
        val queen = testCard(Rank.QUEEN, "q1")

        assertTrue(shouldAlwaysUsePeekAction(queen, bot(listOf(Rank.TWO, Rank.THREE))))
        assertTrue(
            shouldAlwaysUsePeekAction(queen, bot(listOf(Rank.TWO, Rank.THREE), known = listOf(0))),
        )
    }

    @Test
    fun aDrawnSevenOrEightIsWorthPlayingOnlyWhileSomethingIsUnread() {
        val unread = bot(listOf(Rank.TWO, Rank.THREE, Rank.FOUR), known = listOf(0))
        val fullyRead = bot(listOf(Rank.TWO, Rank.THREE, Rank.FOUR))

        assertTrue(shouldAlwaysUsePeekAction(testCard(Rank.SEVEN, "s1"), unread))
        assertTrue(shouldAlwaysUsePeekAction(testCard(Rank.EIGHT, "e1"), unread))
        assertFalse(shouldAlwaysUsePeekAction(testCard(Rank.SEVEN, "s1"), fullyRead))
        assertFalse(shouldAlwaysUsePeekAction(testCard(Rank.EIGHT, "e1"), fullyRead))
    }

    @Test
    fun everyOtherActionIsLeftToTheSearchOrToItsOwnHeuristic() {
        val unread = bot(listOf(Rank.TWO, Rank.THREE, Rank.FOUR), known = listOf(0))

        assertFalse(shouldAlwaysUsePeekAction(testCard(Rank.JACK, "j1"), unread))
        assertFalse(shouldAlwaysUsePeekAction(testCard(Rank.KING, "k1"), unread))
        // The Ace has shouldUseAceAction of its own.
        assertFalse(shouldAlwaysUsePeekAction(testCard(Rank.ACE, "a1"), unread))
    }

    @Test
    fun aCardWithNoActionOrAlreadyPlayedIsNeverForced() {
        val unread = bot(listOf(Rank.TWO, Rank.THREE, Rank.FOUR), known = listOf(0))

        assertFalse(shouldAlwaysUsePeekAction(testCard(Rank.FIVE, "n1"), unread))
        assertFalse(shouldAlwaysUsePeekAction(testCard(Rank.JOKER, "jok"), unread))
        assertFalse(
            shouldAlwaysUsePeekAction(testCard(Rank.QUEEN, "q1").copy(played = true), unread),
        )
    }

    // --- shouldUseAceAction ---------------------------------------------------------------

    @Test
    fun anAceIsSwappedRatherThanPlayedWhenTheBotHoldsSomethingExpensive() {
        val botPlayer = bot(listOf(Rank.TEN, Rank.THREE, Rank.FIVE))
        val rival = opponent("p2", listOf(Rank.TWO, Rank.THREE))

        // A 10 in hand is worth more to be rid of than an opponent is worth inconveniencing.
        assertFalse(
            shouldUseAceAction(
                botPlayer,
                listOf(botPlayer, rival),
                botId,
                memoryOf(Triple("p2", 0, Rank.TWO), Triple("p2", 1, Rank.THREE)),
            ),
        )
    }

    @Test
    fun anAceIsPlayedWhenSomebodyLooksReadyToCallVinto() {
        val botPlayer = bot(listOf(Rank.FIVE, Rank.SIX, Rank.SEVEN))
        val rival = opponent("p2", listOf(Rank.TWO, Rank.THREE, Rank.ACE))

        // The bot is on 18 and has *seen* the rival's hand come to 6. Forcing a card on them
        // is the only lever it has. In TypeScript this test reads the rival's cards directly;
        // here the bot has to have observed them, which is the point.
        assertTrue(
            shouldUseAceAction(
                botPlayer,
                listOf(botPlayer, rival),
                botId,
                memoryOf(
                    Triple("p2", 0, Rank.TWO),
                    Triple("p2", 1, Rank.THREE),
                    Triple("p2", 2, Rank.ACE),
                ),
            ),
        )
    }

    @Test
    fun anAceIsSwappedByDefaultWithNoExpensiveCardAndNoThreat() {
        val botPlayer = bot(listOf(Rank.FIVE, Rank.SIX, Rank.SEVEN))
        val rival = opponent("p2", listOf(Rank.FIVE, Rank.SIX, Rank.SEVEN, Rank.EIGHT))

        assertFalse(
            shouldUseAceAction(
                botPlayer,
                listOf(botPlayer, rival),
                botId,
                memoryOf(
                    Triple("p2", 0, Rank.FIVE),
                    Triple("p2", 1, Rank.SIX),
                    Triple("p2", 2, Rank.SEVEN),
                    Triple("p2", 3, Rank.EIGHT),
                ),
            ),
        )
    }

    @Test
    fun theAceDecisionNeverReadsAHandTheBotHasNotSeen() {
        // The guard against the bug that got the previous bot engine deleted. Two tables
        // identical to the bot's eyes — it has seen nothing of either — must decide the same
        // way, however different the hidden cards are.
        val botPlayer = bot(listOf(Rank.FIVE, Rank.SIX, Rank.SEVEN))
        val harmless = opponent("p2", listOf(Rank.TEN, Rank.TEN, Rank.TEN))
        val terrifying = opponent("p2", listOf(Rank.JOKER, Rank.KING, Rank.KING))

        assertEquals(
            shouldUseAceAction(botPlayer, listOf(botPlayer, harmless), botId, memoryOf()),
            shouldUseAceAction(botPlayer, listOf(botPlayer, terrifying), botId, memoryOf()),
            "the decision changed with cards the bot cannot see",
        )
    }

    // --- shouldParticipateInTossIn --------------------------------------------------------

    @Test
    fun aMatchingKnownCardIsWorthTossingIn() {
        val botPlayer = bot(listOf(Rank.SEVEN, Rank.THREE, Rank.KING))
        assertTrue(shouldParticipateInTossIn(listOf(Rank.SEVEN), botPlayer))
    }

    @Test
    fun noMatchMeansNoTossIn() {
        val botPlayer = bot(listOf(Rank.TWO, Rank.THREE, Rank.KING))
        assertFalse(shouldParticipateInTossIn(listOf(Rank.SEVEN), botPlayer))
    }

    @Test
    fun anyOneOfSeveralOpenRanksIsEnough() {
        val botPlayer = bot(listOf(Rank.TEN, Rank.JACK, Rank.KING))
        assertTrue(shouldParticipateInTossIn(listOf(Rank.TEN, Rank.JACK, Rank.QUEEN), botPlayer))
    }

    @Test
    fun aJokerIsNeverTossedInBecauseItIsWorthKeeping() {
        val botPlayer = bot(listOf(Rank.JOKER, Rank.TWO))
        // -1 is the best card in the game; shedding it raises the hand.
        assertFalse(shouldParticipateInTossIn(listOf(Rank.JOKER), botPlayer))
    }

    @Test
    fun aCardTheBotHasNotReadIsNotTossedIn() {
        // The rule behind it: a wrong toss-in costs a penalty card and bars the player from
        // the rest of the window, so a guess is never worth it.
        val unread = bot(listOf(Rank.SEVEN, Rank.THREE), known = listOf(1))
        assertFalse(shouldParticipateInTossIn(listOf(Rank.SEVEN), unread))
    }

    // --- countUnknownCards and calculateHandScore -----------------------------------------

    @Test
    fun unknownCardsAreCounted() {
        assertEquals(
            3,
            countUnknownCards(
                bot(listOf(Rank.TWO, Rank.THREE, Rank.FOUR, Rank.FIVE, Rank.SIX), known = listOf(0, 1)),
            ),
        )
        assertEquals(0, countUnknownCards(bot(listOf(Rank.TWO, Rank.THREE))))
        assertEquals(
            3,
            countUnknownCards(bot(listOf(Rank.TWO, Rank.THREE, Rank.FOUR), known = emptyList())),
        )
    }

    @Test
    fun aHandIsWorthTheSumOfItsCards() {
        val hand = listOf(
            testCard(Rank.TWO, "c1"),
            testCard(Rank.FIVE, "c2"),
            testCard(Rank.TEN, "c3"),
            testCard(Rank.KING, "c4"),
            testCard(Rank.JOKER, "c5"),
        )

        assertEquals(16, calculateHandScore(hand))
        assertEquals(0, calculateHandScore(emptyList()))
        assertEquals(
            -1,
            calculateHandScore(
                listOf(
                    testCard(Rank.JOKER, "c1"),
                    testCard(Rank.JOKER, "c2"),
                    testCard(Rank.ACE, "c3"),
                ),
            ),
        )
    }
}
