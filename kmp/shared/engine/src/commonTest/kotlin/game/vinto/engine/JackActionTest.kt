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
 * The Jack, ported from `packages/engine/src/lib/__tests__/card-actions/jack.test.ts`.
 *
 * Value 10; the action swaps any two face-down cards belonging to two *different* players.
 * Blind — nobody looks first, which is what separates it from the Queen.
 */
class JackActionTest {

    private fun executeJackSwap(playerId: String) =
        GameAction.ExecuteJackSwap(PlayerIdPayload(playerId))

    private fun executeQueenSwap(playerId: String) =
        GameAction.ExecuteQueenSwap(PlayerIdPayload(playerId))

    @Test
    fun swappingTwoCardsFromDifferentPlayersExchangesThem() {
        val state = testState(
            subPhase = GameSubPhase.AWAITING_ACTION,
            players = listOf(
                testPlayer(
                    "p1", "Player 1", isHuman = true,
                    cards = listOf(testCard(Rank.KING, "p1c1"), testCard(Rank.QUEEN, "p1c2")),
                ),
                testPlayer(
                    "p2", "Player 2", isHuman = false,
                    cards = listOf(testCard(Rank.ACE, "p2c1"), testCard(Rank.TWO, "p2c2")),
                ),
            ),
            pendingAction = pending(
                testCard(Rank.JACK, "jack1"), "p1",
                targets = listOf(ActionTarget("p1", 0), ActionTarget("p2", 1)),
            ),
        )

        val next = unsafeReduce(state, executeJackSwap("p1"))

        assertEquals(Rank.TWO, next.players[0].cards[0].rank)
        assertEquals(Rank.KING, next.players[1].cards[1].rank)

        assertEquals("jack1", next.discardPile.peekTop()?.id)
        assertEquals(true, next.discardPile.peekTop()?.played)

        assertEquals(GameSubPhase.TOSS_QUEUE_ACTIVE, next.subPhase)
        assertTrue(next.activeTossIn?.ranks?.contains(Rank.JACK) == true)
    }

    @Test
    fun twoCardsFromTheSamePlayerAreRejected() {
        val state = testState(
            subPhase = GameSubPhase.AWAITING_ACTION,
            players = listOf(
                testPlayer("p1", "Player 1", isHuman = true),
                testPlayer(
                    "p2", "Player 2", isHuman = false,
                    cards = listOf(
                        testCard(Rank.TEN, "p2c1"), testCard(Rank.NINE, "p2c2"),
                        testCard(Rank.EIGHT, "p2c3"), testCard(Rank.SEVEN, "p2c4"),
                    ),
                ),
            ),
            pendingAction = pending(
                testCard(Rank.JACK, "jack1"), "p1",
                targets = listOf(ActionTarget("p2", 0), ActionTarget("p2", 3)),
            ),
        )

        assertTrue(rejects(state, executeJackSwap("p1")))
    }

    @Test
    fun aPlayersOwnTwoCardsAreRejectedTheSameWay() {
        val state = testState(
            subPhase = GameSubPhase.AWAITING_ACTION,
            players = listOf(
                testPlayer(
                    "p1", "Player 1", isHuman = true,
                    cards = listOf(
                        testCard(Rank.KING, "p1c1"),
                        testCard(Rank.ACE, "p1c2"),
                        testCard(Rank.SEVEN, "p1c3"),
                    ),
                ),
            ),
            pendingAction = pending(
                testCard(Rank.JACK, "jack1"), "p1",
                targets = listOf(ActionTarget("p1", 0), ActionTarget("p1", 2)),
            ),
        )

        assertTrue(rejects(state, executeJackSwap("p1")))
    }

    @Test
    fun aJackCanBeSwappedIntoHandInsteadOfPlayed() {
        val state = testState(
            subPhase = GameSubPhase.CHOOSING,
            players = listOf(
                testPlayer(
                    "p1", "Player 1", isHuman = true,
                    cards = listOf(testCard(Rank.KING, "p1c1"), testCard(Rank.ACE, "p1c2")),
                ),
            ),
            pendingAction = pending(
                testCard(Rank.JACK, "jack1"), "p1",
                actionPhase = ActionPhase.CHOOSING_ACTION,
            ),
        )

        val next = unsafeReduce(state, swapCard("p1", 0))

        assertEquals(GameSubPhase.TOSS_QUEUE_ACTIVE, next.subPhase)
        assertEquals("jack1", next.players[0].cards[0].id)
        assertEquals("p1c1", next.discardPile.peekTop()?.id)
        assertNull(next.pendingAction)
    }

    @Test
    fun aMatchingJackCanBeTossedInAndQueuesItsAction() {
        val state = testState(
            subPhase = GameSubPhase.TOSS_QUEUE_ACTIVE,
            turnNumber = 1,
            players = listOf(
                testPlayer("p1", "Player 1", isHuman = true),
                testPlayer(
                    "p2", "Player 2", isHuman = false,
                    cards = listOf(testCard(Rank.JACK, "p2j1"), testCard(Rank.SEVEN, "p2c2")),
                ),
            ),
            activeTossIn = tossIn(ranks = listOf(Rank.JACK), initiatorId = "p1"),
        )

        val next = unsafeReduce(state, participateInTossIn("p2", listOf(0)))

        assertEquals(1, next.players[1].cards.size)
        assertEquals(1, next.activeTossIn?.queuedActions?.size)
        assertEquals(Rank.JACK, next.activeTossIn?.queuedActions?.first()?.rank)
        assertTrue(next.activeTossIn?.participants?.contains("p2") == true)
    }

    @Test
    fun aQueuedJackIsAimedAndSwappedByWhoeverTossedItIn() {
        val state = testState(
            subPhase = GameSubPhase.TOSS_QUEUE_ACTIVE,
            players = listOf(
                testPlayer("p1", "Player 1", isHuman = true, cards = listOf(testCard(Rank.KING, "p1c1"))),
                testPlayer("p2", "Player 2", isHuman = false, cards = listOf(testCard(Rank.ACE, "p2c1"))),
                testPlayer("p3", "Player 3", isHuman = false, cards = listOf(testCard(Rank.SEVEN, "p3c1"))),
            ),
            activeTossIn = tossIn(
                ranks = listOf(Rank.JACK),
                initiatorId = "p1",
                participants = listOf("p2"),
                queuedActions = listOf(TossInAction(playerId = "p2", rank = Rank.JACK, position = 0)),
            ),
        )

        var next = markPlayersReady(state, listOf("p1", "p2", "p3"))
        next = unsafeReduce(next, useCardAction("p2"))

        assertEquals(GameSubPhase.AWAITING_ACTION, next.subPhase)
        assertEquals(Rank.JACK, next.pendingAction?.card?.rank)
        assertEquals("p2", next.pendingAction?.playerId)

        next = unsafeReduce(next, selectTarget("p2", "p1", 0))
        next = unsafeReduce(next, selectTarget("p2", "p3", 0))
        next = unsafeReduce(next, executeJackSwap("p2"))

        assertEquals(GameSubPhase.TOSS_QUEUE_ACTIVE, next.subPhase)
        assertEquals(0, next.activeTossIn?.queuedActions?.size)
        assertNull(next.pendingAction)
        assertEquals(Rank.SEVEN, next.players.first { it.id == "p1" }.cards[0].rank)
        assertEquals(Rank.KING, next.players.first { it.id == "p3" }.cards[0].rank)
    }

    @Test
    fun aSwapOutsideTheActionPhaseIsRejected() {
        val state = testState(
            subPhase = GameSubPhase.IDLE,
            pendingAction = pending(
                testCard(Rank.JACK, "jack1"), "p1",
                targets = listOf(ActionTarget("p1", 0), ActionTarget("p2", 0)),
            ),
        )

        assertTrue(rejects(state, executeQueenSwap("p1")))
    }

    @Test
    fun anotherPlayerCannotExecuteTheSwap() {
        val state = testState(
            subPhase = GameSubPhase.AWAITING_ACTION,
            currentPlayerIndex = 0,
            pendingAction = pending(
                testCard(Rank.JACK, "jack1"), "p1",
                targets = listOf(ActionTarget("p1", 0), ActionTarget("p2", 0)),
            ),
        )

        assertTrue(rejects(state, executeJackSwap("p2")))
    }

    @Test
    fun aSwapWithFewerThanTwoTargetsIsRejected() {
        val state = testState(
            subPhase = GameSubPhase.AWAITING_ACTION,
            pendingAction = pending(
                testCard(Rank.JACK, "jack1"), "p1",
                targets = listOf(ActionTarget("p1", 0)),
            ),
        )

        assertTrue(rejects(state, executeJackSwap("p1")))
    }
}
