package game.vinto.bot

import game.vinto.shapes.Card
import game.vinto.shapes.Rank
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The swap heuristic, and one invariant that is not a heuristic at all.
 *
 * Most of this file is judgement and may legitimately differ from the TypeScript. The Joker
 * and King penalties are not: they exist so that no combination of other terms can persuade
 * the bot to give away the two cards worth keeping. That is the one property here worth
 * pinning down, and it is pinned by construction — the test stacks every other term as high
 * as it can and checks the swap still loses.
 */
class OutcomeSimulatorTest {

    private fun hand(vararg ranks: Rank) =
        ranks.mapIndexed { index, rank -> testCard(rank, "${rank.serialName}_$index") }

    private fun bot(cards: List<Card>, known: List<Int> = cards.indices.toList()) =
        testPlayer("bot-1", "Bot", isHuman = false, cards = cards, knownCardPositions = known)

    private fun context(
        botCards: List<Card>,
        known: List<Int> = botCards.indices.toList(),
        opponentKnowledge: Map<String, Map<Int, Card>> = emptyMap(),
    ): BotDecisionContext {
        val botPlayer = bot(botCards, known)
        val opponent = testPlayer("p2", "Rival", isHuman = true, cards = hand(Rank.SIX, Rank.SIX))
        return BotDecisionContext(
            botId = "bot-1",
            botPlayer = botPlayer,
            allPlayers = listOf(botPlayer, opponent),
            gameState = testState(players = listOf(botPlayer, opponent)),
            discardTop = null,
            discardPile = game.vinto.shapes.Pile(),
            opponentKnowledge = opponentKnowledge,
        )
    }

    // --- the floor -------------------------------------------------------------------------

    @Test
    fun givingAwayAJokerLosesToDiscardingNoMatterWhatElseIsOnOffer() {
        val joker = testCard(Rank.JOKER, "Joker_0")
        val drawn = testCard(Rank.SIX, "6_drawn")
        val botPlayer = bot(hand(Rank.JOKER, Rank.SIX, Rank.SIX))
        val ctx = context(botPlayer.cards)

        val swapScore = OutcomeSimulator.calculateStrategicOutcomeScore(
            OutcomeSimulator.simulateTurnOutcome(drawn, swapPosition = 0, botPlayer, ctx),
            drawn,
            joker,
        )
        val discardScore = OutcomeSimulator.calculateOutcomeScore(
            OutcomeSimulator.simulateDiscardOutcome(drawn, botPlayer),
        )

        assertTrue(swapScore < discardScore, "swap $swapScore should lose to discard $discardScore")
    }

    @Test
    fun theJokerPenaltyOutweighsEveryOtherTermCombined() {
        val joker = testCard(Rank.JOKER, "Joker_0")
        val drawn = testCard(Rank.SIX, "6_drawn")

        // Every other term stacked as high as it will go: a full hand of knowledge, no cards
        // left over, a score of zero. The penalty still has to swamp it.
        val bestImaginable = TurnOutcome(finalHandSize = 0, finalKnownCards = 99, finalScore = 0)
        val penalised =
            OutcomeSimulator.calculateStrategicOutcomeScore(bestImaginable, drawn, joker)

        assertTrue(penalised < 0, "a Joker swap scored $penalised, which is still positive")
        assertTrue(
            penalised < -OutcomeSimulator.calculateOutcomeScore(bestImaginable),
            "the penalty must exceed the best outcome the rest of the model can produce",
        )
    }

    @Test
    fun theSameFloorProtectsAKing() {
        val king = testCard(Rank.KING, "K_0")
        val drawn = testCard(Rank.SIX, "6_drawn")
        val bestImaginable = TurnOutcome(finalHandSize = 0, finalKnownCards = 99, finalScore = 0)

        assertTrue(OutcomeSimulator.calculateStrategicOutcomeScore(bestImaginable, drawn, king) < 0)
    }

    @Test
    fun tradingAWorseCardForABetterOneIsNotTaxed() {
        val expensive = testCard(Rank.SIX, "6_0")
        val cheap = testCard(Rank.TWO, "2_drawn")
        val outcome = TurnOutcome(finalHandSize = 3, finalKnownCards = 3, finalScore = 8)

        val strategic = OutcomeSimulator.calculateStrategicOutcomeScore(outcome, cheap, expensive)

        assertEquals(OutcomeSimulator.calculateOutcomeScore(outcome), strategic)
    }

    @Test
    fun tradingABetterCardForAWorseOneIsTaxed() {
        val cheap = testCard(Rank.TWO, "2_0")
        val expensive = testCard(Rank.SIX, "6_drawn")
        val outcome = TurnOutcome(finalHandSize = 3, finalKnownCards = 3, finalScore = 12)

        val strategic = OutcomeSimulator.calculateStrategicOutcomeScore(outcome, expensive, cheap)

        assertTrue(strategic < OutcomeSimulator.calculateOutcomeScore(outcome))
    }

    // --- what a swap actually buys ---------------------------------------------------------

    @Test
    fun swappingIntoABlindSpotCountsAsLearningACard() {
        val cards = hand(Rank.THREE, Rank.FOUR, Rank.FIVE)
        val botPlayer = bot(cards, known = listOf(0))
        val ctx = context(cards, known = listOf(0))
        val drawn = testCard(Rank.TWO, "2_drawn")

        val intoKnown = OutcomeSimulator.simulateTurnOutcome(drawn, 0, botPlayer, ctx)
        val intoUnknown = OutcomeSimulator.simulateTurnOutcome(drawn, 1, botPlayer, ctx)

        assertEquals(1, intoKnown.finalKnownCards, "position 0 was already read")
        assertTrue(intoUnknown.finalKnownCards > intoKnown.finalKnownCards)
    }

    @Test
    fun onlyCardsTheBotHasReadCanBeTossedIn() {
        val cards = hand(Rank.SEVEN, Rank.SEVEN, Rank.THREE)

        val readsBoth = OutcomeSimulator.simulateTossInCascade(
            Rank.SEVEN,
            currentHandSize = 3,
            currentScore = 17,
            botPlayer = bot(cards),
        )
        val readsOne = OutcomeSimulator.simulateTossInCascade(
            Rank.SEVEN,
            currentHandSize = 3,
            currentScore = 17,
            botPlayer = bot(cards, known = listOf(0)),
        )

        assertEquals(1, readsBoth.handSize)
        assertEquals(3, readsBoth.score)
        assertEquals(2, readsOne.handSize)
        assertEquals(10, readsOne.score)
    }

    @Test
    fun discardingChangesNothingButTheTossInItOpens() {
        val cards = hand(Rank.SEVEN, Rank.THREE)
        val botPlayer = bot(cards)

        val matching = OutcomeSimulator.simulateDiscardOutcome(testCard(Rank.SEVEN, "7_d"), botPlayer)
        val notMatching = OutcomeSimulator.simulateDiscardOutcome(testCard(Rank.FOUR, "4_d"), botPlayer)

        assertEquals(1, matching.finalHandSize)
        assertEquals(2, notMatching.finalHandSize)
        // Knowledge is unchanged either way: the drawn card never entered the hand.
        assertEquals(2, matching.finalKnownCards)
        assertEquals(2, notMatching.finalKnownCards)
    }

    // --- what an action is worth -----------------------------------------------------------

    @Test
    fun aKingIsWorthMoreWhenThereIsMoreToPointItAt() {
        val king = testCard(Rank.KING, "K_0")
        val ctx = context(hand(Rank.THREE))

        val bare = OutcomeSimulator.simulateActionKnowledgeGain(king, bot(hand(Rank.THREE, Rank.FOUR)), ctx)
        val withSecondKing =
            OutcomeSimulator.simulateActionKnowledgeGain(king, bot(hand(Rank.KING, Rank.FOUR)), ctx)
        val withDeclarableAction =
            OutcomeSimulator.simulateActionKnowledgeGain(king, bot(hand(Rank.QUEEN, Rank.FOUR)), ctx)

        assertTrue(withSecondKing > bare)
        assertTrue(withDeclarableAction > bare)
        assertTrue(withSecondKing > withDeclarableAction, "a second King cascades; a Queen does not")
    }

    @Test
    fun aPeekIsWorthNothingToABotThatHasAlreadyReadItsHand() {
        val ctx = context(hand(Rank.THREE))
        val seven = testCard(Rank.SEVEN, "7_0")
        val cards = hand(Rank.THREE, Rank.FOUR)

        assertEquals(0, OutcomeSimulator.simulateActionKnowledgeGain(seven, bot(cards), ctx))
        assertTrue(
            OutcomeSimulator.simulateActionKnowledgeGain(seven, bot(cards, known = listOf(0)), ctx) > 0,
        )
    }

    @Test
    fun aSwapActionNeedsBothABlindSpotAndSomethingKnownToFillItWith() {
        val jack = testCard(Rank.JACK, "J_0")
        val cards = hand(Rank.THREE, Rank.FOUR)
        val blindSpot = listOf(0)
        val knowsARivalCard = mapOf("p2" to mapOf(0 to testCard(Rank.TWO, "2_p2")))

        assertEquals(
            0,
            OutcomeSimulator.simulateActionKnowledgeGain(
                jack,
                bot(cards, blindSpot),
                context(cards, blindSpot),
            ),
            "nothing known about anyone else to swap for",
        )
        assertEquals(
            0,
            OutcomeSimulator.simulateActionKnowledgeGain(
                jack,
                bot(cards),
                context(cards, opponentKnowledge = knowsARivalCard),
            ),
            "no blind spot of its own left to fill",
        )
        assertTrue(
            OutcomeSimulator.simulateActionKnowledgeGain(
                jack,
                bot(cards, blindSpot),
                context(cards, blindSpot, knowsARivalCard),
            ) > 0,
        )
    }

    @Test
    fun aCardWithNoActionIsWorthNoKnowledge() {
        val ctx = context(hand(Rank.THREE))
        val cards = hand(Rank.THREE, Rank.FOUR)

        assertEquals(
            0,
            OutcomeSimulator.simulateActionKnowledgeGain(testCard(Rank.FIVE, "5_0"), bot(cards, listOf(0)), ctx),
        )
        assertEquals(
            0,
            OutcomeSimulator.simulateActionKnowledgeGain(testCard(Rank.JOKER, "Joker_0"), bot(cards, listOf(0)), ctx),
        )
    }

    // --- the weighting ---------------------------------------------------------------------

    @Test
    fun knowledgeOutranksHandSizeWhichOutranksScore() {
        val base = TurnOutcome(finalHandSize = 4, finalKnownCards = 2, finalScore = 20)

        val oneMoreKnown = base.copy(finalKnownCards = 3)
        val oneFewerCard = base.copy(finalHandSize = 3)
        val onePointBetter = base.copy(finalScore = 19)

        val baseScore = OutcomeSimulator.calculateOutcomeScore(base)
        val knowledgeGain = OutcomeSimulator.calculateOutcomeScore(oneMoreKnown) - baseScore
        val handSizeGain = OutcomeSimulator.calculateOutcomeScore(oneFewerCard) - baseScore
        val scoreGain = OutcomeSimulator.calculateOutcomeScore(onePointBetter) - baseScore

        assertTrue(knowledgeGain > handSizeGain)
        assertTrue(handSizeGain > scoreGain)
        assertTrue(scoreGain > 0)
    }
}
