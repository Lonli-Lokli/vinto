package game.vinto.engine

import game.vinto.shapes.ActionPhase
import game.vinto.shapes.ActionTarget
import game.vinto.shapes.GameAction
import game.vinto.shapes.GameSubPhase
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.Rank
import game.vinto.shapes.TossInAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Queen, ported from `legacy-web/packages/engine/src/lib/__tests__/card-actions/queen.test.ts`.
 *
 * Value 10; the action peeks any two cards from two *different* players and then optionally
 * swaps them. The Jack's swap is blind, and this one is not — which is why the Queen has both
 * an execute and a skip, and the Jack effectively only needs the execute.
 */
class QueenActionTest {

    private fun executeQueenSwap(playerId: String) =
        GameAction.ExecuteQueenSwap(PlayerIdPayload(playerId))

    private fun skipQueenSwap(playerId: String) =
        GameAction.SkipQueenSwap(PlayerIdPayload(playerId))

    private fun threeSeats() = listOf(
        testPlayer("p1", "Player 1", isHuman = true),
        testPlayer(
            "p2",
            "Player 2",
            isHuman = false,
            cards = listOf(testCard(Rank.KING, "p2c1"), testCard(Rank.ACE, "p2c2")),
        ),
        testPlayer(
            "p3",
            "Player 3",
            isHuman = false,
            cards = listOf(testCard(Rank.SEVEN, "p3c1"), testCard(Rank.EIGHT, "p3c2")),
        ),
    )

    private fun aimedAtTwoSeats() = pending(
        testCard(Rank.QUEEN, "queen1"),
        "p1",
        targets = listOf(ActionTarget("p2", 0), ActionTarget("p3", 1)),
    )

    @Test
    fun decliningTheSwapLeavesBothCardsWhereTheyWere() {
        val state = testState(
            subPhase = GameSubPhase.AWAITING_ACTION,
            players = threeSeats(),
            pendingAction = aimedAtTwoSeats(),
        )

        val next = unsafeReduce(state, skipQueenSwap("p1"))

        assertEquals(Rank.KING, next.players[1].cards[0].rank)
        assertEquals(Rank.EIGHT, next.players[2].cards[1].rank)

        assertEquals("queen1", next.discardPile.peekTop()?.id)
        assertEquals(GameSubPhase.TOSS_QUEUE_ACTIVE, next.subPhase)
        assertTrue(next.activeTossIn?.ranks?.contains(Rank.QUEEN) == true)
        assertEquals("p1", next.activeTossIn?.initiatorId)
    }

    @Test
    fun takingTheSwapExchangesThem() {
        val state = testState(
            subPhase = GameSubPhase.AWAITING_ACTION,
            players = threeSeats(),
            pendingAction = aimedAtTwoSeats(),
        )

        val next = unsafeReduce(state, executeQueenSwap("p1"))

        assertEquals(Rank.EIGHT, next.players[1].cards[0].rank)
        assertEquals(Rank.KING, next.players[2].cards[1].rank)

        assertEquals("queen1", next.discardPile.peekTop()?.id)
        assertEquals(GameSubPhase.TOSS_QUEUE_ACTIVE, next.subPhase)
        assertTrue(next.activeTossIn?.ranks?.contains(Rank.QUEEN) == true)
    }

    @Test
    fun twoCardsFromTheSamePlayerAreRejectedWhicheverWayTheQueenFinishes() {
        val sameSeat = testState(
            subPhase = GameSubPhase.AWAITING_ACTION,
            players = listOf(
                testPlayer("p1", "Player 1", isHuman = true),
                testPlayer(
                    "p2",
                    "Player 2",
                    isHuman = false,
                    cards = listOf(
                        testCard(Rank.KING, "p2c1"),
                        testCard(Rank.ACE, "p2c2"),
                        testCard(Rank.SEVEN, "p2c3"),
                        testCard(Rank.EIGHT, "p2c4"),
                    ),
                ),
            ),
            pendingAction = pending(
                testCard(Rank.QUEEN, "queen1"),
                "p1",
                targets = listOf(ActionTarget("p2", 0), ActionTarget("p2", 3)),
            ),
        )

        assertTrue(rejects(sameSeat, skipQueenSwap("p1")), "skip accepted a same-player pair")
        assertTrue(rejects(sameSeat, executeQueenSwap("p1")), "execute accepted a same-player pair")
    }

    @Test
    fun aQueenCanBeSwappedIntoHandInsteadOfPlayed() {
        val state = testState(
            subPhase = GameSubPhase.CHOOSING,
            players = listOf(
                testPlayer(
                    "p1",
                    "Player 1",
                    isHuman = true,
                    cards = listOf(testCard(Rank.KING, "p1c1"), testCard(Rank.ACE, "p1c2")),
                ),
            ),
            pendingAction = pending(
                testCard(Rank.QUEEN, "queen1"),
                "p1",
                actionPhase = ActionPhase.CHOOSING_ACTION,
            ),
        )

        val next = unsafeReduce(state, swapCard("p1", 0))

        assertEquals(GameSubPhase.TOSS_QUEUE_ACTIVE, next.subPhase)
        assertEquals("queen1", next.players[0].cards[0].id)
        assertNull(next.pendingAction)
    }

    @Test
    fun aMatchingQueenCanBeTossedInAndQueuesItsAction() {
        val state = testState(
            subPhase = GameSubPhase.TOSS_QUEUE_ACTIVE,
            turnNumber = 1,
            players = listOf(
                testPlayer("p1", "Player 1", isHuman = true),
                testPlayer(
                    "p2",
                    "Player 2",
                    isHuman = false,
                    cards = listOf(testCard(Rank.QUEEN, "p2q1"), testCard(Rank.SEVEN, "p2c2")),
                ),
            ),
            activeTossIn = tossIn(ranks = listOf(Rank.QUEEN), initiatorId = "p1"),
        )

        val next = unsafeReduce(state, participateInTossIn("p2", listOf(0)))

        assertEquals(1, next.players[1].cards.size)
        assertEquals(1, next.activeTossIn?.queuedActions?.size)
        assertEquals(Rank.QUEEN, next.activeTossIn?.queuedActions?.first()?.rank)
        assertTrue(next.activeTossIn?.participants?.contains("p2") == true)
    }

    @Test
    fun aQueuedQueenContinuesTheSameTossInRatherThanStartingAnother() {
        val state = testState(
            subPhase = GameSubPhase.TOSS_QUEUE_ACTIVE,
            players = listOf(
                testPlayer("p1", "Player 1", isHuman = true, cards = listOf(testCard(Rank.KING, "p1c1"))),
                testPlayer("p2", "Player 2", isHuman = false, cards = listOf(testCard(Rank.ACE, "p2c1"))),
                testPlayer("p3", "Player 3", isHuman = false, cards = listOf(testCard(Rank.SEVEN, "p3c1"))),
            ),
            activeTossIn = tossIn(
                ranks = listOf(Rank.QUEEN),
                initiatorId = "p1",
                participants = listOf("p2"),
                queuedActions = listOf(TossInAction(playerId = "p2", rank = Rank.QUEEN, position = 0)),
            ),
        )

        var next = markPlayersReady(state, listOf("p1", "p2", "p3"))
        next = unsafeReduce(next, useCardAction("p2"))

        assertEquals(GameSubPhase.AWAITING_ACTION, next.subPhase)
        assertEquals(Rank.QUEEN, next.pendingAction?.card?.rank)
        assertEquals("p2", next.pendingAction?.playerId)

        next = unsafeReduce(next, selectTarget("p2", "p1", 0))
        next = unsafeReduce(next, selectTarget("p2", "p3", 0))
        next = unsafeReduce(next, executeQueenSwap("p2"))

        assertEquals(GameSubPhase.TOSS_QUEUE_ACTIVE, next.subPhase)
        assertTrue(next.activeTossIn?.ranks?.contains(Rank.QUEEN) == true)
        assertNull(next.pendingAction)
    }

    @Test
    fun aSwapOutsideTheActionPhaseIsRejected() {
        val state = testState(
            subPhase = GameSubPhase.IDLE,
            pendingAction = aimedAtTwoSeats(),
        )

        assertTrue(rejects(state, executeQueenSwap("p1")))
    }

    @Test
    fun anotherPlayerCannotExecuteTheSwap() {
        val state = testState(
            subPhase = GameSubPhase.AWAITING_ACTION,
            currentPlayerIndex = 0,
            players = threeSeats(),
            pendingAction = aimedAtTwoSeats(),
        )

        assertTrue(rejects(state, executeQueenSwap("p2")))
    }

    @Test
    fun theQueenNeedsExactlyTwoTargetsNeitherFewerNorMore() {
        val one = testState(
            subPhase = GameSubPhase.AWAITING_ACTION,
            pendingAction = pending(
                testCard(Rank.QUEEN, "queen1"),
                "p1",
                targets = listOf(ActionTarget("p2", 0)),
            ),
        )
        val three = testState(
            subPhase = GameSubPhase.AWAITING_ACTION,
            pendingAction = pending(
                testCard(Rank.QUEEN, "queen1"),
                "p1",
                targets = listOf(ActionTarget("p2", 0), ActionTarget("p2", 1), ActionTarget("p2", 2)),
            ),
        )

        assertTrue(rejects(one, executeQueenSwap("p1")), "one target was accepted")
        assertTrue(rejects(three, executeQueenSwap("p1")), "three targets were accepted")
    }
}
