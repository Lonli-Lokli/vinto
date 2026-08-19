package game.vinto.engine

import game.vinto.shapes.ActionPhase
import game.vinto.shapes.ActionTarget
import game.vinto.shapes.GameSubPhase
import game.vinto.shapes.Rank
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The King, ported from `packages/engine/src/lib/__tests__/card-actions/king.test.ts`.
 *
 * Value 0, and the most involved action in the game: name any card, declare what rank it is,
 * and play that rank's action. Get the declaration right and the action fires; get it wrong
 * and you take a penalty card.
 *
 * The toss-in window it opens is the interesting part — it covers the King *and* the declared
 * rank, which is why several of these tests are about which ranks are in the window rather
 * than about the cards themselves.
 */
class KingActionTest {

    private fun kingAimedAt(
        targetPlayerId: String,
        position: Int,
        card: game.vinto.shapes.Card? = null,
    ) = pending(
        testCard(Rank.KING, "king1"), "p1",
        targets = listOf(ActionTarget(targetPlayerId, position, card)),
    )

    @Test
    fun declaringANonActionRankOpensATossInOnThatRank() {
        val state = testState(
            subPhase = GameSubPhase.CHOOSING,
            players = listOf(
                testPlayer("p1", "Player 1", isHuman = true, cards = listOf(testCard(Rank.FIVE, "p1c1"))),
                testPlayer(
                    "p2", "Player 2", isHuman = false,
                    cards = listOf(testCard(Rank.SEVEN, "p2c1"), testCard(Rank.EIGHT, "p2c2")),
                ),
            ),
            pendingAction = kingAimedAt("p1", 0, testCard(Rank.FIVE, "p1c1")),
        )

        var next = unsafeReduce(state, useCardAction("p1"))
        assertEquals(GameSubPhase.AWAITING_ACTION, next.subPhase)

        next = unsafeReduce(next, declareKing("p1", Rank.FIVE))

        assertTrue(next.activeTossIn?.ranks?.contains(Rank.FIVE) == true)
        assertEquals("p1", next.activeTossIn?.initiatorId)
        assertEquals(GameSubPhase.TOSS_QUEUE_ACTIVE, next.subPhase)

        // The King goes down first and the declared card lands on top of it.
        assertEquals("king1", next.discardPile.at(-1)?.id)
        assertEquals("p1c1", next.discardPile.peekTop()?.id)
        assertNull(next.pendingAction)
    }

    @Test
    fun aJokerCanBeDeclaredToo() {
        val state = testState(
            subPhase = GameSubPhase.CHOOSING,
            players = listOf(
                testPlayer("p1", "Player 1", isHuman = true, cards = listOf(testCard(Rank.JOKER, "p1c1"))),
                testPlayer(
                    "p2", "Player 2", isHuman = false,
                    cards = listOf(testCard(Rank.SEVEN, "p2c1"), testCard(Rank.EIGHT, "p2c2")),
                ),
            ),
            pendingAction = kingAimedAt("p1", 0, testCard(Rank.JOKER, "p1c1")),
        )

        var next = unsafeReduce(state, useCardAction("p1"))
        assertEquals(GameSubPhase.AWAITING_ACTION, next.subPhase)

        next = unsafeReduce(next, declareKing("p1", Rank.JOKER))

        assertNotNull(next.activeTossIn)
        assertTrue(next.activeTossIn?.ranks?.contains(Rank.JOKER) == true)
        assertEquals(GameSubPhase.TOSS_QUEUE_ACTIVE, next.subPhase)
    }

    @Test
    fun aCorrectlyDeclaredSevenPlaysItsActionAndTheWindowCoversBothRanks() {
        val state = testState(
            subPhase = GameSubPhase.CHOOSING,
            players = listOf(
                testPlayer(
                    "p1", "Player 1", isHuman = true,
                    cards = listOf(
                        testCard(Rank.SEVEN, "p1c1"),
                        testCard(Rank.TWO, "p1c2"),
                        testCard(Rank.THREE, "p1c3"),
                    ),
                ),
            ),
            pendingAction = kingAimedAt("p1", 0, testCard(Rank.SEVEN, "p1c1")),
        )

        var next = unsafeReduce(state, useCardAction("p1"))
        assertEquals(GameSubPhase.AWAITING_ACTION, next.subPhase)

        next = unsafeReduce(next, declareKing("p1", Rank.SEVEN))

        assertEquals(2, next.activeTossIn?.ranks?.size)
        assertTrue(next.activeTossIn?.ranks?.contains(Rank.SEVEN) == true)
        assertTrue(next.activeTossIn?.ranks?.contains(Rank.KING) == true)
        // Still awaiting: the declared 7 now has its own action to aim.
        assertEquals(GameSubPhase.AWAITING_ACTION, next.subPhase)
    }

    @Test
    fun aWrongDeclarationCostsAPenaltyCardAndPlaysNoAction() {
        val state = testState(
            subPhase = GameSubPhase.CHOOSING,
            players = listOf(
                testPlayer(
                    "p1", "Player 1", isHuman = true,
                    cards = listOf(testCard(Rank.TWO, "p1c1"), testCard(Rank.THREE, "p1c2")),
                ),
            ),
            drawPile = pileOf(testCard(Rank.QUEEN, "penalty1")),
            pendingAction = kingAimedAt("p1", 0, testCard(Rank.SEVEN, "p1c1")),
        )

        var next = unsafeReduce(state, useCardAction("p1"))
        next = unsafeReduce(next, declareKing("p1", Rank.SEVEN))

        assertEquals(1, next.activeTossIn?.ranks?.size)
        assertTrue(next.activeTossIn?.ranks?.contains(Rank.KING) == true)
        assertEquals(3, next.players.first { it.id == "p1" }.cards.size)
        assertEquals(GameSubPhase.TOSS_QUEUE_ACTIVE, next.subPhase)
    }

    @Test
    fun aKingDeclaringKingOpensAWindowOnKingAlone() {
        val state = testState(
            subPhase = GameSubPhase.CHOOSING,
            players = listOf(
                testPlayer("p1", "Player 1", isHuman = true, cards = listOf(testCard(Rank.KING, "p1c1"))),
                testPlayer(
                    "p2", "Player 2", isHuman = false,
                    cards = listOf(testCard(Rank.SEVEN, "p2c1"), testCard(Rank.EIGHT, "p2c2")),
                ),
            ),
            pendingAction = kingAimedAt("p1", 0, testCard(Rank.KING, "p1c1")),
        )

        var next = unsafeReduce(state, useCardAction("p1"))
        next = unsafeReduce(next, declareKing("p1", Rank.KING))

        assertNotNull(next.activeTossIn)
        assertEquals(1, next.activeTossIn?.ranks?.size)
        assertTrue(next.activeTossIn?.ranks?.contains(Rank.KING) == true)
        assertEquals(GameSubPhase.AWAITING_ACTION, next.subPhase)
    }

    /**
     * The next player's card closes the window on everything before it.
     *
     * Three ways a turn can put a card on the pile — swap, discard, play the action — and all
     * three must retire the ranks already there. Otherwise a King stays tossable for the rest
     * of the round, which is the sort of thing nobody notices until a game goes strange.
     */
    private fun stateWithKingWindowAndNextPlayer(
        secondPlayerFirstCard: Rank,
        drawn: game.vinto.shapes.Card,
    ) = testState(
        subPhase = GameSubPhase.CHOOSING,
        turnNumber = 1,
        players = listOf(
            testPlayer(
                "p1", "Player 1", isHuman = true,
                cards = listOf(
                    testCard(Rank.SEVEN, "p1c1"),
                    testCard(Rank.TWO, "p1c2"),
                    testCard(Rank.THREE, "p1c3"),
                ),
            ),
            testPlayer(
                "p2", "Player 2", isHuman = true,
                cards = listOf(
                    testCard(secondPlayerFirstCard, "p2c1"),
                    testCard(Rank.TWO, "p2c2"),
                    testCard(Rank.THREE, "p2c3"),
                ),
            ),
        ),
        activeTossIn = tossIn(ranks = listOf(Rank.KING), initiatorId = "p1"),
        drawPile = pileOf(drawn),
        pendingAction = kingAimedAt("p1", 0),
    )

    private fun declaredSevenThenHandedOver(state: game.vinto.shapes.GameState): game.vinto.shapes.GameState {
        var next = unsafeReduce(state, useCardAction("p1"))
        assertEquals(GameSubPhase.AWAITING_ACTION, next.subPhase)

        next = unsafeReduce(next, declareKing("p1", Rank.SEVEN))
        assertEquals(2, next.activeTossIn?.ranks?.size)
        assertTrue(next.activeTossIn?.ranks?.contains(Rank.SEVEN) == true)
        assertTrue(next.activeTossIn?.ranks?.contains(Rank.KING) == true)

        next = markPlayersReady(next, listOf("p1", "p2"))
        assertEquals(2, next.activeTossIn?.ranks?.size, "the window should survive the handover")
        assertEquals(1, next.currentPlayerIndex)

        return unsafeReduce(next, drawCard("p2"))
    }

    @Test
    fun theNextPlayersSwapRetiresTheKingWindow() {
        val state = stateWithKingWindowAndNextPlayer(Rank.QUEEN, testCard(Rank.FOUR, "draw1"))

        val next = unsafeReduce(declaredSevenThenHandedOver(state), swapCard("p2", 0))

        assertEquals(1, next.activeTossIn?.ranks?.size)
        assertTrue(next.activeTossIn?.ranks?.contains(Rank.QUEEN) == true)
    }

    @Test
    fun theNextPlayersDiscardRetiresTheKingWindow() {
        val state = stateWithKingWindowAndNextPlayer(Rank.QUEEN, testCard(Rank.FOUR, "draw1"))

        val next = unsafeReduce(declaredSevenThenHandedOver(state), discardCard("p2"))

        assertEquals(1, next.activeTossIn?.ranks?.size)
        assertTrue(next.activeTossIn?.ranks?.contains(Rank.FOUR) == true)
    }

    @Test
    fun theNextPlayersPlayedActionRetiresTheKingWindow() {
        val state = stateWithKingWindowAndNextPlayer(Rank.SIX, testCard(Rank.QUEEN, "draw1"))

        val next = unsafeReduce(declaredSevenThenHandedOver(state), useCardAction("p2"))

        assertEquals(1, next.activeTossIn?.ranks?.size)
        assertTrue(next.activeTossIn?.ranks?.contains(Rank.QUEEN) == true)
    }

    @Test
    fun aKingCanBeSwappedIntoHandInsteadOfDeclared() {
        val state = testState(
            subPhase = GameSubPhase.CHOOSING,
            players = listOf(
                testPlayer(
                    "p1", "Player 1", isHuman = true,
                    cards = listOf(testCard(Rank.ACE, "p1c1"), testCard(Rank.QUEEN, "p1c2")),
                ),
            ),
            pendingAction = pending(
                testCard(Rank.KING, "king1"), "p1",
                actionPhase = ActionPhase.CHOOSING_ACTION,
            ),
        )

        val next = unsafeReduce(state, swapCard("p1", 0))

        assertEquals(GameSubPhase.TOSS_QUEUE_ACTIVE, next.subPhase)
        assertEquals("king1", next.players[0].cards[0].id)
        assertNull(next.pendingAction)
    }

    @Test
    fun aMatchingKingCanBeTossedInAndQueuesItsAction() {
        val state = testState(
            subPhase = GameSubPhase.TOSS_QUEUE_ACTIVE,
            turnNumber = 1,
            players = listOf(
                testPlayer("p1", "Player 1", isHuman = true),
                testPlayer(
                    "p2", "Player 2", isHuman = false,
                    cards = listOf(testCard(Rank.KING, "p2k1"), testCard(Rank.SEVEN, "p2c2")),
                ),
            ),
            activeTossIn = tossIn(ranks = listOf(Rank.KING), initiatorId = "p1"),
        )

        val next = unsafeReduce(state, participateInTossIn("p2", listOf(0)))

        assertEquals(1, next.players[1].cards.size)
        assertEquals(1, next.activeTossIn?.queuedActions?.size)
        assertEquals(Rank.KING, next.activeTossIn?.queuedActions?.first()?.rank)
        assertTrue(next.activeTossIn?.participants?.contains("p2") == true)
    }

    @Test
    fun aTossedInKingCanBeDeclaredAgainstTheTossersOwnLastCard() {
        val state = testState(
            subPhase = GameSubPhase.TOSS_QUEUE_ACTIVE,
            turnNumber = 1,
            currentPlayerIndex = 1,
            players = listOf(
                testPlayer("p1", "Player 1", isHuman = true),
                testPlayer(
                    "p2", "Player 2", isHuman = false,
                    cards = listOf(testCard(Rank.KING, "p2k1"), testCard(Rank.FOUR, "p2c2")),
                ),
            ),
            activeTossIn = tossIn(
                ranks = listOf(Rank.KING),
                initiatorId = "p1",
                originalPlayerIndex = 1,
            ),
        )

        var next = unsafeReduce(state, participateInTossIn("p2", listOf(0)))

        assertEquals(1, next.players[1].cards.size)
        assertEquals(1, next.activeTossIn?.queuedActions?.size)

        next = markPlayersReady(next, listOf("p1", "p2"))
        assertEquals(1, next.currentPlayerIndex)
        assertEquals(1, next.activeTossIn?.queuedActions?.size)
        assertNotNull(next.pendingAction)

        next = unsafeReduce(next, useCardAction("p2"))
        next = unsafeReduce(next, selectTarget("p2", "p2", 0))
        next = unsafeReduce(next, declareKing("p2", Rank.FOUR))

        assertEquals(0, next.activeTossIn?.queuedActions?.size)
        assertEquals(2, next.activeTossIn?.ranks?.size)
        assertTrue(next.activeTossIn?.ranks?.contains(Rank.KING) == true)
        assertTrue(next.activeTossIn?.ranks?.contains(Rank.FOUR) == true)
        assertEquals(0, next.players[1].cards.size)
        assertEquals(GameSubPhase.TOSS_QUEUE_ACTIVE, next.subPhase)

        next = markPlayersReady(next, listOf("p1", "p2"))
        assertEquals(0, next.currentPlayerIndex)
    }

    @Test
    fun aDeclarationOutsideTheActionPhaseIsRejected() {
        val state = testState(
            subPhase = GameSubPhase.IDLE,
            pendingAction = pending(testCard(Rank.KING, "king1"), "p1"),
        )

        assertTrue(rejects(state, declareKing("p1", Rank.ACE)))
    }

    @Test
    fun anotherPlayerCannotDeclare() {
        val state = testState(
            subPhase = GameSubPhase.AWAITING_ACTION,
            currentPlayerIndex = 0,
            pendingAction = pending(testCard(Rank.KING, "king1"), "p1"),
        )

        assertTrue(rejects(state, declareKing("p2", Rank.ACE)))
    }

    @Test
    fun onlyAKingCanDeclare() {
        val state = testState(
            subPhase = GameSubPhase.AWAITING_ACTION,
            pendingAction = pending(testCard(Rank.QUEEN, "queen1"), "p1"),
        )

        assertTrue(rejects(state, declareKing("p1", Rank.ACE)))
    }
}
