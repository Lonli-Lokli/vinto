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
 * was given, which would corrupt every sibling branch of the tree at once. And it has to
 * move real cards: the search is only as good as the world it plays forward.
 */
class StateTransitionTest {

    private fun memory() = BotMemory("bot-1", Difficulty.HARD, Random(1))

    private fun known(card: Card) =
        CardMemory(card, confidence = 1.0, lastSeen = 0, observations = 1)

    /** A hunch: below the confidence the model acts on. */
    private fun hunch(card: Card) =
        CardMemory(card, confidence = 0.3, lastSeen = 0, observations = 1)

    private fun seat(id: String, cards: Int = 4, knownCards: Map<Int, CardMemory> = emptyMap()) =
        MctsPlayerState(id, cardCount = cards, knownCards = knownCards)

    // Mirrors MctsGameState's own breadth, as in MoveGeneratorTest.
    @Suppress("LongParameterList")
    private fun state(
        players: List<MctsPlayerState>,
        currentIndex: Int = 0,
        hiddenCards: Map<String, Card> = emptyMap(),
        pendingCard: Card? = null,
        pendingOrigin: PendingOrigin = PendingOrigin.DRAWN,
        discardTop: Card? = null,
        deckSize: Int = 20,
        deckOrder: List<Card> = List(deckSize) { testCard(Rank.SIX, "deck-$it") },
        isTossInPhase: Boolean = false,
        turnCount: Int = 20,
    ) = MctsGameState(
        players = players,
        currentPlayerIndex = currentIndex,
        botPlayerId = players[currentIndex].id,
        discardPileTop = discardTop,
        discardPile = Pile(),
        deckSize = deckSize,
        deckOrder = deckOrder,
        discarded = listOfNotNull(discardTop),
        discardCount = listOfNotNull(discardTop).size,
        botMemory = memory(),
        hiddenCards = hiddenCards,
        pendingCard = pendingCard,
        pendingOrigin = pendingCard?.let { pendingOrigin },
        isTossInPhase = isTossInPhase,
        turnCount = turnCount,
    )

    private fun MctsGameState.seatNamed(id: String) = players.first { it.id == id }

    private fun MctsGameState.total(id: String) = StateTransition.handTotal(this, id)

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
            isTossInPhase = true,
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
        assertEquals(listOf("7_0"), after.discarded.map { it.id }, "the tossed card went to the pile")
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
            isTossInPhase = true,
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

        StateTransition.applyMove(before, MctsMove(MctsMoveType.DISCARD, playerId = "bot-1"))

        // The move discards a 7, which tosses the bot's 7 in — so if the working copy leaked,
        // this seat would already be a card lighter.
        assertEquals(2, before.seatNamed("bot-1").cardCount)
        assertEquals(2, before.hiddenCards.size)
        assertEquals(1, before.seatNamed("bot-1").knownCards.size)
        assertEquals(20, before.turnCount)
        assertNull(before.discardPileTop)
        assertEquals(20, before.deckOrder.size)
    }

    // --- toss-in cascade -------------------------------------------------------------------

    @Test
    fun discardingOpensATossInWindowOnItsRankForEveryPlayer() {
        val botSeven = testCard(Rank.SEVEN, "7_bot")
        val rivalSeven = testCard(Rank.SEVEN, "7_p2")
        val rivalTwo = testCard(Rank.TWO, "2_p2")

        val before = state(
            listOf(
                seat("bot-1", cards = 2, knownCards = mapOf(0 to known(botSeven))),
                seat("p2", cards = 2),
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
        assertEquals(3, after.total("bot-1"))
        assertEquals(2, after.total("p2"))
        assertEquals("2_p2", after.hiddenCards.getValue("p2-0").id)
    }

    @Test
    fun theBotOnlyTossesWhatItRemembersButARivalKnowsItsOwnHand() {
        // The bot's second card is a 7 it has never read: it stays. The rival's unread 7 is
        // one the rival knows about, and goes.
        val before = state(
            listOf(seat("bot-1", cards = 2), seat("p2", cards = 2)),
            hiddenCards = mapOf(
                "bot-1-0" to testCard(Rank.THREE, "3_bot"),
                "bot-1-1" to testCard(Rank.SEVEN, "7_bot"),
                "p2-0" to testCard(Rank.SEVEN, "7_p2"),
                "p2-1" to testCard(Rank.TWO, "2_p2"),
            ),
            pendingCard = testCard(Rank.SEVEN, "7_drawn"),
        )

        val after = StateTransition.applyMove(before, MctsMove(MctsMoveType.DISCARD, playerId = "bot-1"))

        assertEquals(2, after.seatNamed("bot-1").cardCount)
        assertEquals(1, after.seatNamed("p2").cardCount)
    }

    @Test
    fun aCardTheBotOnlySuspectsItHoldsIsNotTossedIn() {
        val suspected = testCard(Rank.SEVEN, "7_bot")
        val before = state(
            listOf(seat("bot-1", cards = 2, knownCards = mapOf(0 to hunch(suspected))), seat("p2", cards = 2)),
            hiddenCards = mapOf("bot-1-0" to suspected, "bot-1-1" to testCard(Rank.TWO, "2_bot")),
            pendingCard = testCard(Rank.SEVEN, "7_drawn"),
        )

        val after = StateTransition.applyMove(before, MctsMove(MctsMoveType.DISCARD, playerId = "bot-1"))

        assertEquals(2, after.seatNamed("bot-1").cardCount)
    }

    // --- individual moves ------------------------------------------------------------------

    @Test
    fun drawingDealsTheTopOfTheSampledDeckAndWaitsForAReply() {
        val deck = listOf(testCard(Rank.QUEEN, "Q_top"), testCard(Rank.TWO, "2_next"))
        val before = state(listOf(seat("bot-1"), seat("p2")), deckSize = 2, deckOrder = deck)

        val after = StateTransition.applyMove(before, MctsMove(MctsMoveType.DRAW, playerId = "bot-1"))

        assertEquals("Q_top", after.pendingCard?.id)
        assertEquals(PendingOrigin.DRAWN, after.pendingOrigin)
        assertEquals(1, after.deckSize)
        assertEquals(listOf("2_next"), after.deckOrder.map { it.id })
        assertEquals(0, after.currentPlayerIndex, "the turn is not over until the card is dealt with")
    }

    @Test
    fun takingTheDiscardCommitsItsTakerToPlayingIt() {
        val jack = testCard(Rank.JACK, "J_top")
        val before = state(listOf(seat("bot-1"), seat("p2")), discardTop = jack)

        val after = StateTransition.applyMove(before, MctsMove(MctsMoveType.TAKE_DISCARD, playerId = "bot-1"))

        assertEquals("J_top", after.pendingCard?.id)
        assertEquals(PendingOrigin.COMMITTED, after.pendingOrigin)
        assertNull(after.discardPileTop)
        assertEquals(0, after.discardCount)
    }

    @Test
    fun swappingPutsTheDrawnCardInHandAndDiscardsWhatItDisplaced() {
        val displaced = testCard(Rank.FIVE, "5_0")
        val drawn = testCard(Rank.THREE, "3_drawn")
        val before = state(
            listOf(seat("bot-1", cards = 2), seat("p2", cards = 4)),
            hiddenCards = mapOf("bot-1-0" to displaced, "bot-1-1" to testCard(Rank.FIVE, "5_1")),
            pendingCard = drawn,
        )

        val after = StateTransition.applyMove(
            before,
            MctsMove(MctsMoveType.SWAP, playerId = "bot-1", swapPosition = 0),
        )

        assertEquals("3_drawn", after.hiddenCards.getValue("bot-1-0").id)
        assertEquals("3_drawn", after.seatNamed("bot-1").knownCards.getValue(0).card.id)
        assertEquals("5_0", after.discardPileTop?.id)
        assertFalse(after.discardPileTop?.played ?: true)
        assertNull(after.pendingCard)
        assertTrue(after.awaitingVintoDecision, "the turn ends with the Vinto question")
        assertEquals(0, after.currentPlayerIndex)
    }

    @Test
    fun swappingOutAKnownActionCardDeclaresItAndBorrowsItsAction() {
        val king = testCard(Rank.KING, "K_0")
        val before = state(
            listOf(seat("bot-1", cards = 2, knownCards = mapOf(0 to known(king))), seat("p2", cards = 2)),
            hiddenCards = mapOf(
                "bot-1-0" to king,
                "bot-1-1" to testCard(Rank.FIVE, "5_1"),
                "p2-0" to testCard(Rank.TEN, "10_p2"),
                "p2-1" to testCard(Rank.TWO, "2_p2"),
            ),
            pendingCard = testCard(Rank.THREE, "3_drawn"),
        )

        val after = StateTransition.applyMove(
            before,
            MctsMove(MctsMoveType.SWAP, playerId = "bot-1", swapPosition = 0),
        )

        assertEquals("K_0", after.pendingCard?.id)
        assertEquals(PendingOrigin.BORROWED, after.pendingOrigin)
        assertFalse(after.awaitingVintoDecision, "the turn waits for the borrowed action")
        assertEquals(listOf(Rank.KING), after.queuedTossRanks)
    }

    @Test
    fun aJackExchangesTwoCardsAndWhatIsKnownAboutThem() {
        val mine = testCard(Rank.KING, "K_0")
        val theirs = testCard(Rank.FIVE, "5_0")
        val before = state(
            listOf(
                seat("bot-1", cards = 2, knownCards = mapOf(0 to known(mine))),
                seat("p2", cards = 2),
            ),
            hiddenCards = mapOf(
                "bot-1-0" to mine,
                "bot-1-1" to testCard(Rank.TWO, "2_0"),
                "p2-0" to theirs,
                "p2-1" to testCard(Rank.TWO, "2_1"),
            ),
            pendingCard = testCard(Rank.JACK, "J_0"),
        )

        val after = StateTransition.applyMove(
            before,
            MctsMove(
                MctsMoveType.USE_ACTION,
                playerId = "bot-1",
                targets = listOf(MctsActionTarget("bot-1", 0), MctsActionTarget("p2", 0)),
                shouldSwap = true,
            ),
        )

        assertEquals("5_0", after.hiddenCards.getValue("bot-1-0").id)
        assertEquals("K_0", after.hiddenCards.getValue("p2-0").id)
        // King is 0 and five is 5, so the bot takes on five points and the rival sheds them.
        assertEquals(7, after.total("bot-1"))
        assertEquals(2, after.total("p2"))
        // The bot knew its King; it now knows the King is in the rival's hand, and not what it got.
        assertEquals("K_0", after.seatNamed("p2").knownCards.getValue(0).card.id)
        assertNull(after.seatNamed("bot-1").knownCards[0])
        assertTrue(after.discardPileTop?.played ?: false)
    }

    @Test
    fun aDeclinedJackMovesNothing() {
        val mine = testCard(Rank.KING, "K_0")
        val theirs = testCard(Rank.FIVE, "5_0")
        val before = state(
            listOf(seat("bot-1", cards = 1), seat("p2", cards = 1)),
            hiddenCards = mapOf("bot-1-0" to mine, "p2-0" to theirs),
            pendingCard = testCard(Rank.JACK, "J_0"),
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
    }

    @Test
    fun aQueenLearnsBothCardsAndTradesOnlyWhenThatShedsPoints() {
        val mine = testCard(Rank.KING, "K_0")
        val theirs = testCard(Rank.FIVE, "5_0")
        val before = state(
            listOf(seat("bot-1", cards = 1), seat("p2", cards = 1)),
            hiddenCards = mapOf("bot-1-0" to mine, "p2-0" to theirs),
            pendingCard = testCard(Rank.QUEEN, "Q_0"),
        )
        val move = MctsMove(
            MctsMoveType.USE_ACTION,
            playerId = "bot-1",
            targets = listOf(MctsActionTarget("bot-1", 0), MctsActionTarget("p2", 0)),
            shouldSwap = true,
        )

        val kept = StateTransition.applyMove(before, move)
        assertEquals("K_0", kept.hiddenCards.getValue("bot-1-0").id, "a King is not traded for a 5")
        assertEquals("K_0", kept.seatNamed("bot-1").knownCards.getValue(0).card.id)
        assertEquals("5_0", kept.seatNamed("p2").knownCards.getValue(0).card.id)

        val worse = before.copy(hiddenCards = mapOf("bot-1-0" to theirs, "p2-0" to mine))
        val traded = StateTransition.applyMove(worse, move)
        assertEquals("K_0", traded.hiddenCards.getValue("bot-1-0").id, "a 5 is traded for a King")
    }

    @Test
    fun anAceCostsItsVictimACardOffTheDeck() {
        val deck = listOf(testCard(Rank.NINE, "9_deck"))
        val before = state(
            listOf(seat("bot-1", cards = 2), seat("p2", cards = 1)),
            hiddenCards = mapOf("p2-0" to testCard(Rank.FIVE, "5_0")),
            pendingCard = testCard(Rank.ACE, "A_0"),
            deckSize = 1,
            deckOrder = deck,
        )

        val after = StateTransition.applyMove(
            before,
            MctsMove(MctsMoveType.USE_ACTION, playerId = "bot-1", targets = listOf(MctsActionTarget("p2", 0))),
        )

        assertEquals(2, after.seatNamed("p2").cardCount)
        assertEquals("9_deck", after.hiddenCards.getValue("p2-1").id)
        assertEquals(0, after.deckSize)
        assertNull(after.seatNamed("p2").knownCards[1], "nobody saw the forced card")
    }

    @Test
    fun aKingTakesTheNamedCardOutOfItsHandAndBorrowsItsAction() {
        val jack = testCard(Rank.JACK, "J_p2")
        val before = state(
            listOf(
                seat("bot-1", cards = 1),
                seat("p2", cards = 2, knownCards = mapOf(0 to known(jack))),
            ),
            hiddenCards = mapOf(
                "bot-1-0" to testCard(Rank.TWO, "2_0"),
                "p2-0" to jack,
                "p2-1" to testCard(Rank.FOUR, "4_p2"),
            ),
            pendingCard = testCard(Rank.KING, "K_0"),
        )

        val after = StateTransition.applyMove(
            before,
            MctsMove(
                MctsMoveType.USE_ACTION,
                playerId = "bot-1",
                targets = listOf(MctsActionTarget("p2", 0)),
                declaredRank = Rank.JACK,
            ),
        )

        assertEquals(1, after.seatNamed("p2").cardCount)
        assertEquals("4_p2", after.hiddenCards.getValue("p2-0").id, "the hand closed up")
        assertEquals("J_p2", after.pendingCard?.id, "the declared Jack is the declarer's to play")
        assertEquals(PendingOrigin.BORROWED, after.pendingOrigin)
        assertEquals("K_0", after.discardPileTop?.id)
        assertEquals(setOf(Rank.JACK, Rank.KING), after.queuedTossRanks.toSet())
    }

    @Test
    fun aKingNamingAPlainCardOpensTheWindowOnBothRanks() {
        val before = state(
            listOf(
                seat("bot-1", cards = 2),
                seat("p2", cards = 2),
            ),
            hiddenCards = mapOf(
                "bot-1-0" to testCard(Rank.FIVE, "5_bot"),
                "bot-1-1" to testCard(Rank.KING, "K_bot"),
                "p2-0" to testCard(Rank.FIVE, "5_p2"),
                "p2-1" to testCard(Rank.TWO, "2_p2"),
            ),
            pendingCard = testCard(Rank.KING, "K_0"),
        )

        val after = StateTransition.applyMove(
            before,
            MctsMove(
                MctsMoveType.USE_ACTION,
                playerId = "bot-1",
                targets = listOf(MctsActionTarget("p2", 0)),
                declaredRank = Rank.FIVE,
            ),
        )

        // The rival's 5 left by declaration; the rival knows nothing else matches. The bot
        // has not read its own cards, so it tosses nothing — a 5 and a King it cannot name.
        assertEquals(1, after.seatNamed("p2").cardCount)
        assertEquals(2, after.seatNamed("bot-1").cardCount)
        assertNull(after.pendingCard)
        assertTrue(after.queuedTossRanks.isEmpty(), "the window was resolved")
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
    fun callingVintoGivesEverybodyElseOneTurnAndThenScores() {
        val before = state(listOf(seat("bot-1"), seat("p2"), seat("p3")))
            .copy(awaitingVintoDecision = true)

        val called = StateTransition.applyMove(before, MctsMove(MctsMoveType.CALL_VINTO, playerId = "bot-1"))
        assertEquals("bot-1", called.vintoCallerId)
        assertTrue(called.finalTurnTriggered)
        assertFalse(called.isTerminal)
        assertEquals(1, called.currentPlayerIndex)

        // Two more turns, each ending in a pass with no Vinto question (somebody has called).
        var state = called
        repeat(2) {
            val mover = state.currentPlayer!!.id
            state = StateTransition.applyMove(state, MctsMove(MctsMoveType.DRAW, playerId = mover))
            state = StateTransition.applyMove(state, MctsMove(MctsMoveType.DISCARD, playerId = mover))
            assertFalse(state.awaitingVintoDecision)
        }
        assertTrue(state.isTerminal, "the round scores when the turn comes back to the caller")
    }

    @Test
    fun passingTheVintoQuestionMovesPlayOn() {
        val before = state(listOf(seat("bot-1"), seat("p2"))).copy(awaitingVintoDecision = true)
        val after = StateTransition.applyMove(before, MctsMove(MctsMoveType.PASS, playerId = "bot-1"))

        assertFalse(after.awaitingVintoDecision)
        assertEquals(1, after.currentPlayerIndex)
        assertEquals(before.turnCount + 1, after.turnCount)
    }

    @Test
    fun theVintoQuestionIsNotAskedInTheOpening() {
        val before = state(listOf(seat("bot-1"), seat("p2")), pendingCard = testCard(Rank.TWO, "2_d"), turnCount = 1)
        val after = StateTransition.applyMove(before, MctsMove(MctsMoveType.DISCARD, playerId = "bot-1"))

        assertFalse(after.awaitingVintoDecision)
        assertEquals(1, after.currentPlayerIndex)
    }

    // --- terminal conditions and totals ----------------------------------------------------

    @Test
    fun theSearchStopsOnAScoredRoundAStarvedDeckOrARunawayRollout() {
        val running = state(listOf(seat("bot-1"), seat("p2")), deckSize = 5)
        assertFalse(StateTransition.isTerminal(running))

        assertTrue(StateTransition.isTerminal(running.copy(deckSize = 0, discardCount = 1)))
        assertFalse(StateTransition.isTerminal(running.copy(deckSize = 0, discardCount = 5)), "a pile can fold back")
        assertTrue(StateTransition.isTerminal(running.copy(turnCount = 201)))
        assertTrue(StateTransition.isTerminal(running.copy(isTerminal = true)))
    }

    @Test
    fun anUnreadCardIsPricedAtTheAverageOfWhatIsLeftRatherThanAsNothing() {
        val before = state(
            listOf(seat("bot-1", cards = 3), seat("p2")),
            hiddenCards = mapOf("bot-1-0" to testCard(Rank.TWO, "2_0")),
        )

        // Two known points plus two cards nobody has seen; the estimate must not be zero, or
        // an unread hand would look like a winning one.
        assertTrue(StateTransition.handTotal(before, "bot-1") > 2)
        assertEquals(0, StateTransition.handTotal(before, "nobody"))
    }

    // --- the reshuffle -------------------------------------------------------------------

    @Test
    fun aDeckRunningDryFoldsTheDiscardPileBackInLikeTheRealGame() {
        // One card left on the deck and three on the pile. Ending the turn folds the pile
        // back in, keeping only its top card, and the folded cards are drawable again.
        val pile = listOf(testCard(Rank.TWO, "2_p"), testCard(Rank.THREE, "3_p"), testCard(Rank.FOUR, "4_p"))
        val before = state(
            listOf(seat("bot-1"), seat("p2")),
            deckSize = 1,
            deckOrder = listOf(testCard(Rank.SIX, "6_d")),
        ).copy(discarded = pile, discardCount = 3, discardPileTop = pile.last(), awaitingVintoDecision = true)

        val after = StateTransition.applyMove(before, MctsMove(MctsMoveType.PASS, playerId = "bot-1"))

        assertEquals(3, after.deckSize)
        assertEquals(1, after.discardCount)
        assertEquals(listOf("6_d", "2_p", "3_p"), after.deckOrder.map { it.id })
        assertEquals(listOf("4_p"), after.discarded.map { it.id })
        assertFalse(StateTransition.isTerminal(after), "a reshuffled deck is not an ended game")
    }

    @Test
    fun anEmptyDeckWithNothingToFoldBackIsStillTerminal() {
        val starved = state(listOf(seat("bot-1"), seat("p2")), deckSize = 0, deckOrder = emptyList())
            .copy(discardCount = 1)

        assertTrue(StateTransition.isTerminal(starved))
    }
}
