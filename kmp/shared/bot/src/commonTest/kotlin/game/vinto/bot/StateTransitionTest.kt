package game.vinto.bot

import game.vinto.shapes.Card
import game.vinto.shapes.Difficulty
import game.vinto.shapes.Pile
import game.vinto.shapes.Rank
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What the forward model has to get right.
 *
 * Not that it matches the game — it deliberately does not, and [StateTransition]'s own
 * comment lists where. What it has to get right is *itself*: a position it produces must be
 * one the rest of the search can read. The two ways that breaks are a hand whose cards and
 * memories have drifted out of alignment, and a transition that writes back into the state it
 * was given, which would corrupt every sibling branch of the tree at once.
 */
class StateTransitionTest {

    private fun memory() = BotMemory("bot-1", Difficulty.HARD, Random(1))

    private fun known(card: Card) =
        CardMemory(card, confidence = 1.0, lastSeen = 0, observations = 1)

    /** A hunch: below the confidence the model acts on. */
    private fun hunch(card: Card) =
        CardMemory(card, confidence = 0.3, lastSeen = 0, observations = 1)

    private fun seat(
        id: String,
        cards: Int = 4,
        knownCards: Map<Int, CardMemory> = emptyMap(),
        score: Double = 20.0,
    ) = MctsPlayerState(id, cardCount = cards, knownCards = knownCards, score = score)

    // Mirrors MctsGameState's own breadth, as in MoveGeneratorTest.
    @Suppress("LongParameterList")
    private fun state(
        players: List<MctsPlayerState>,
        currentIndex: Int = 0,
        hiddenCards: Map<String, Card> = emptyMap(),
        pendingCard: Card? = null,
        discardTop: Card? = null,
        deckSize: Int = 20,
        isTossInPhase: Boolean = false,
        turnCount: Int = 20,
    ) = MctsGameState(
        players = players,
        currentPlayerIndex = currentIndex,
        botPlayerId = players[currentIndex].id,
        discardPileTop = discardTop,
        discardPile = Pile(),
        deckSize = deckSize,
        botMemory = memory(),
        hiddenCards = hiddenCards,
        pendingCard = pendingCard,
        isTossInPhase = isTossInPhase,
        turnCount = turnCount,
    )

    private fun MctsGameState.seatNamed(id: String) = players.first { it.id == id }

    // --- the alignment property ------------------------------------------------------------

    @Test
    fun removingACardRenumbersTheCardsAndTheMemoriesTogether() {
        val two = testCard(Rank.TWO, "2_0")
        val seven = testCard(Rank.SEVEN, "7_0")
        val nine = testCard(Rank.NINE, "9_0")
        val king = testCard(Rank.KING, "K_0")

        val before = state(
            listOf(
                seat(
                    "bot-1",
                    cards = 4,
                    knownCards = mapOf(0 to known(two), 1 to known(seven), 2 to known(nine), 3 to known(king)),
                ),
                seat("p2", cards = 4),
            ),
            hiddenCards = mapOf(
                "bot-1-0" to two,
                "bot-1-1" to seven,
                "bot-1-2" to nine,
                "bot-1-3" to king,
                "p2-0" to testCard(Rank.THREE, "3_0"),
            ),
        )

        val after = StateTransition.applyMove(
            before,
            MctsMove(MctsMoveType.TOSS_IN, playerId = "bot-1", tossInPositions = listOf(1)),
        )

        val bot = after.seatNamed("bot-1")
        assertEquals(3, bot.cardCount)

        // Every surviving memory must still name the card actually sitting at that position.
        for (position in 0 until bot.cardCount) {
            val dealt = after.hiddenCards.getValue(after.hiddenCardKey("bot-1", position))
            assertEquals(dealt.id, bot.knownCards.getValue(position).card.id, "position $position")
        }
        assertEquals(listOf("2_0", "9_0", "K_0"), (0 until 3).map { bot.knownCards.getValue(it).card.id })

        // The vacated slot is gone rather than left dangling, and nobody else moved.
        assertFalse(after.hiddenCards.containsKey("bot-1-3"))
        assertEquals("3_0", after.hiddenCards.getValue("p2-0").id)
    }

    @Test
    fun removingSeveralCardsAtOnceStillLeavesAContiguousHand() {
        val cards = listOf(
            testCard(Rank.TWO, "2_0"),
            testCard(Rank.SEVEN, "7_0"),
            testCard(Rank.THREE, "3_0"),
            testCard(Rank.SEVEN, "7_1"),
            testCard(Rank.FOUR, "4_0"),
        )
        val before = state(
            listOf(
                seat("bot-1", cards = 5, knownCards = cards.indices.associateWith { known(cards[it]) }),
                seat("p2", cards = 4),
            ),
            hiddenCards = cards.indices.associate { "bot-1-$it" to cards[it] },
        )

        val after = StateTransition.applyMove(
            before,
            MctsMove(MctsMoveType.TOSS_IN, playerId = "bot-1", tossInPositions = listOf(1, 3)),
        )

        val bot = after.seatNamed("bot-1")
        assertEquals(3, bot.cardCount)
        assertEquals(listOf("2_0", "3_0", "4_0"), (0 until 3).map { bot.knownCards.getValue(it).card.id })
        assertEquals(
            listOf("2_0", "3_0", "4_0"),
            (0 until 3).map { after.hiddenCards.getValue("bot-1-$it").id },
        )
    }

    // --- immutability ----------------------------------------------------------------------

    @Test
    fun aTransitionNeverWritesBackIntoTheStateItWasGiven() {
        val seven = testCard(Rank.SEVEN, "7_0")
        val before = state(
            listOf(seat("bot-1", cards = 2, knownCards = mapOf(0 to known(seven))), seat("p2", cards = 4)),
            hiddenCards = mapOf("bot-1-0" to seven, "bot-1-1" to testCard(Rank.TWO, "2_0")),
            pendingCard = testCard(Rank.SEVEN, "7_1"),
        )

        StateTransition.applyMove(
            before,
            MctsMove(MctsMoveType.DISCARD, playerId = "bot-1"),
        )

        // The move discards a 7, which tosses the bot's 7 in — so if the working copy leaked,
        // this seat would already be a card lighter.
        assertEquals(2, before.seatNamed("bot-1").cardCount)
        assertEquals(2, before.hiddenCards.size)
        assertEquals(1, before.seatNamed("bot-1").knownCards.size)
        assertEquals(20, before.turnCount)
        assertNull(before.discardPileTop)
    }

    // --- toss-in cascade -------------------------------------------------------------------

    @Test
    fun discardingOpensATossInWindowOnItsRankForEveryPlayer() {
        val botSeven = testCard(Rank.SEVEN, "7_bot")
        val rivalSeven = testCard(Rank.SEVEN, "7_p2")
        val rivalTwo = testCard(Rank.TWO, "2_p2")

        val before = state(
            listOf(
                seat("bot-1", cards = 2, knownCards = mapOf(0 to known(botSeven)), score = 9.0),
                seat("p2", cards = 2, knownCards = mapOf(0 to known(rivalSeven), 1 to known(rivalTwo)), score = 9.0),
            ),
            hiddenCards = mapOf(
                "bot-1-0" to botSeven,
                "bot-1-1" to testCard(Rank.THREE, "3_bot"),
                "p2-0" to rivalSeven,
                "p2-1" to rivalTwo,
            ),
            pendingCard = testCard(Rank.SEVEN, "7_drawn"),
        )

        val after = StateTransition.applyMove(before, MctsMove(MctsMoveType.DISCARD, playerId = "bot-1"))

        assertEquals(1, after.seatNamed("bot-1").cardCount)
        assertEquals(1, after.seatNamed("p2").cardCount)
        assertEquals(2.0, after.seatNamed("bot-1").score)
        assertEquals(2.0, after.seatNamed("p2").score)
        assertEquals("2_p2", after.hiddenCards.getValue("p2-0").id)
    }

    @Test
    fun aCardTheHolderOnlySuspectsIsNotTossedIn() {
        val suspected = testCard(Rank.SEVEN, "7_p2")
        val before = state(
            listOf(
                seat("bot-1", cards = 2),
                seat("p2", cards = 2, knownCards = mapOf(0 to hunch(suspected))),
            ),
            hiddenCards = mapOf("p2-0" to suspected),
            pendingCard = testCard(Rank.SEVEN, "7_drawn"),
        )

        val after = StateTransition.applyMove(before, MctsMove(MctsMoveType.DISCARD, playerId = "bot-1"))

        assertEquals(2, after.seatNamed("p2").cardCount)
    }

    // --- individual moves ------------------------------------------------------------------

    @Test
    fun swappingPutsTheDrawnCardInHandAndDiscardsWhatItDisplaced() {
        val displaced = testCard(Rank.KING, "K_0")
        val drawn = testCard(Rank.THREE, "3_drawn")
        val before = state(
            listOf(seat("bot-1", cards = 2, score = 10.0), seat("p2", cards = 4)),
            hiddenCards = mapOf("bot-1-0" to displaced, "bot-1-1" to testCard(Rank.FIVE, "5_0")),
            pendingCard = drawn,
        )

        val after = StateTransition.applyMove(
            before,
            MctsMove(MctsMoveType.SWAP, playerId = "bot-1", swapPosition = 0),
        )

        assertEquals("3_drawn", after.hiddenCards.getValue("bot-1-0").id)
        assertEquals("3_drawn", after.seatNamed("bot-1").knownCards.getValue(0).card.id)
        assertEquals("K_0", after.discardPileTop?.id)
        assertFalse(after.discardPileTop?.played ?: true)
        assertNull(after.pendingCard)
        assertEquals(1, after.currentPlayerIndex)
    }

    @Test
    fun aJackExchangesTwoCardsAndMovesBothScores() {
        val mine = testCard(Rank.KING, "K_0")
        val theirs = testCard(Rank.FIVE, "5_0")
        val before = state(
            listOf(seat("bot-1", cards = 2, score = 10.0), seat("p2", cards = 2, score = 12.0)),
            hiddenCards = mapOf("bot-1-0" to mine, "p2-0" to theirs),
            pendingCard = testCard(Rank.JACK, "J_0"),
        )

        val after = StateTransition.applyMove(
            before,
            MctsMove(
                MctsMoveType.USE_ACTION,
                playerId = "bot-1",
                targets = listOf(MctsActionTarget("bot-1", 0), MctsActionTarget("p2", 0)),
            ),
        )

        assertEquals("5_0", after.hiddenCards.getValue("bot-1-0").id)
        assertEquals("K_0", after.hiddenCards.getValue("p2-0").id)
        // King is 0 and five is 5, so the bot takes on five points and the rival sheds them.
        assertEquals(15.0, after.seatNamed("bot-1").score)
        assertEquals(7.0, after.seatNamed("p2").score)
        assertTrue(after.discardPileTop?.played ?: false)
    }

    @Test
    fun aQueenThatDeclinesTheSwapStillLearnsBothCards() {
        val mine = testCard(Rank.KING, "K_0")
        val theirs = testCard(Rank.FIVE, "5_0")
        val before = state(
            listOf(seat("bot-1", cards = 2, score = 10.0), seat("p2", cards = 2, score = 12.0)),
            hiddenCards = mapOf("bot-1-0" to mine, "p2-0" to theirs),
            pendingCard = testCard(Rank.QUEEN, "Q_0"),
        )

        val after = StateTransition.applyMove(
            before,
            MctsMove(
                MctsMoveType.USE_ACTION,
                playerId = "bot-1",
                targets = listOf(MctsActionTarget("bot-1", 0), MctsActionTarget("p2", 0)),
                shouldSwap = false,
            ),
        )

        assertEquals("K_0", after.hiddenCards.getValue("bot-1-0").id)
        assertEquals("5_0", after.hiddenCards.getValue("p2-0").id)
        assertEquals("K_0", after.seatNamed("bot-1").knownCards.getValue(0).card.id)
        assertEquals("5_0", after.seatNamed("p2").knownCards.getValue(0).card.id)
        assertEquals(10.0, after.seatNamed("bot-1").score)
    }

    @Test
    fun anAceCostsItsVictimACardTheyCannotSee() {
        val before = state(
            listOf(seat("bot-1", cards = 2), seat("p2", cards = 3, score = 12.0)),
            hiddenCards = mapOf("p2-0" to testCard(Rank.FIVE, "5_0")),
            pendingCard = testCard(Rank.ACE, "A_0"),
        )

        val after = StateTransition.applyMove(
            before,
            MctsMove(
                MctsMoveType.USE_ACTION,
                playerId = "bot-1",
                targets = listOf(MctsActionTarget("p2", 0)),
            ),
        )

        assertEquals(4, after.seatNamed("p2").cardCount)
        assertTrue(after.seatNamed("p2").score > 12.0)
    }

    @Test
    fun passingClosesTheTossInWindowAndTossingInDoesNot() {
        val seven = testCard(Rank.SEVEN, "7_0")
        val open = state(
            listOf(seat("bot-1", cards = 2, knownCards = mapOf(0 to known(seven))), seat("p2")),
            hiddenCards = mapOf("bot-1-0" to seven, "bot-1-1" to testCard(Rank.TWO, "2_0")),
            isTossInPhase = true,
        )

        val afterTossIn = StateTransition.applyMove(
            open,
            MctsMove(MctsMoveType.TOSS_IN, playerId = "bot-1", tossInPositions = listOf(0)),
        )
        assertTrue(afterTossIn.isTossInPhase, "a toss-in window stays open for further toss-ins")
        assertEquals(open.turnCount, afterTossIn.turnCount)
        assertEquals(open.currentPlayerIndex, afterTossIn.currentPlayerIndex)

        val afterPass = StateTransition.applyMove(open, MctsMove(MctsMoveType.PASS, playerId = "bot-1"))
        assertFalse(afterPass.isTossInPhase)
        assertEquals(open.turnCount + 1, afterPass.turnCount)
    }

    @Test
    fun callingVintoEndsTheSearchAndAwardsItToTheLowestHand() {
        val before = state(
            listOf(seat("bot-1", score = 8.0), seat("p2", score = 14.0), seat("p3", score = 6.0)),
        )

        val after = StateTransition.applyMove(before, MctsMove(MctsMoveType.CALL_VINTO, playerId = "bot-1"))

        assertTrue(after.isTerminal)
        assertTrue(after.finalTurnTriggered)
        assertEquals("p3", after.winner)
    }

    @Test
    fun drawingCostsADeckCardAndTheTurn() {
        val before = state(listOf(seat("bot-1"), seat("p2")), deckSize = 5)
        val after = StateTransition.applyMove(before, MctsMove(MctsMoveType.DRAW, playerId = "bot-1"))

        assertEquals(4, after.deckSize)
        assertEquals(1, after.currentPlayerIndex)
        assertEquals(before.turnCount + 1, after.turnCount)
    }

    // --- terminal conditions ---------------------------------------------------------------

    @Test
    fun theSearchStopsOnAnEmptyHandAnEmptyDeckOrARunawayRollout() {
        val running = state(listOf(seat("bot-1"), seat("p2")), deckSize = 5)
        assertFalse(StateTransition.isTerminal(running))

        assertTrue(StateTransition.isTerminal(running.copy(deckSize = 0)))
        assertTrue(StateTransition.isTerminal(running.copy(turnCount = 201)))
        assertTrue(
            StateTransition.isTerminal(
                running.copy(players = listOf(seat("bot-1", cards = 0), seat("p2"))),
            ),
        )
        assertTrue(StateTransition.isTerminal(running.copy(isTerminal = true)))
    }

    @Test
    fun anUnseenCardIsScoredAsAnEstimateRatherThanAsNothing() {
        val before = state(
            listOf(seat("bot-1", cards = 3), seat("p2")),
            hiddenCards = mapOf("bot-1-0" to testCard(Rank.TWO, "2_0")),
        )

        // Two known points plus two cards nobody has seen; the estimate must not be zero, or
        // an unread hand would look like a winning one.
        assertTrue(StateTransition.calculatePlayerScore(before, "bot-1") > 2.0)
        assertEquals(50.0, StateTransition.calculatePlayerScore(before, "nobody"))
    }

    @Test
    fun updatingEstimatesRederivesEveryScoreFromTheDealtCards() {
        val before = state(
            listOf(seat("bot-1", cards = 2, score = 99.0), seat("p2", cards = 1, score = 99.0)),
            hiddenCards = mapOf(
                "bot-1-0" to testCard(Rank.TWO, "2_0"),
                "bot-1-1" to testCard(Rank.THREE, "3_0"),
                "p2-0" to testCard(Rank.KING, "K_0"),
            ),
        )

        val after = StateTransition.updateScoreEstimates(before)

        assertEquals(5.0, after.seatNamed("bot-1").score)
        assertEquals(0.0, after.seatNamed("p2").score)
    }

    @Test
    fun aLastCardTossedInEndsTheGameAndTheLookaheadSaysSo() {
        val before = state(listOf(seat("bot-1", cards = 1), seat("p2", cards = 3)))

        assertTrue(
            StateTransition.wouldMoveEndGame(
                before,
                MctsMove(MctsMoveType.TOSS_IN, playerId = "bot-1", tossInPositions = listOf(0)),
            ),
        )
        assertFalse(
            StateTransition.wouldMoveEndGame(
                before,
                MctsMove(MctsMoveType.TOSS_IN, playerId = "p2", tossInPositions = listOf(0)),
            ),
        )
        assertTrue(
            StateTransition.wouldMoveEndGame(before, MctsMove(MctsMoveType.CALL_VINTO, playerId = "p2")),
        )
        assertFalse(StateTransition.wouldMoveEndGame(before, MctsMove(MctsMoveType.DRAW, playerId = "bot-1")))
    }
}
