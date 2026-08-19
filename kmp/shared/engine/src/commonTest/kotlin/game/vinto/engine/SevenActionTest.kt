package game.vinto.engine

import game.vinto.shapes.ActionPhase
import game.vinto.shapes.GameSubPhase
import game.vinto.shapes.Rank
import game.vinto.shapes.TossInAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The 7, ported from `packages/engine/src/lib/__tests__/card-actions/seven.test.ts`.
 *
 * Value 7; the action peeks one of your own cards, and finishing it opens a toss-in window
 * on rank 7.
 */
class SevenActionTest {

    @Test
    fun peekingYourOwnCardFinishesTheTurnAndOpensATossIn() {
        val seven = testCard(Rank.SEVEN, "seven1")
        val state = testState(
            subPhase = GameSubPhase.AWAITING_ACTION,
            players = listOf(
                testPlayer(
                    "p1", "Player 1", isHuman = true,
                    cards = listOf(testCard(Rank.KING, "p1c1"), testCard(Rank.QUEEN, "p1c2")),
                ),
            ),
            pendingAction = pending(seven, "p1"),
        )

        var next = unsafeReduce(state, selectTarget("p1", "p1", 1))
        next = unsafeReduce(next, confirmPeek("p1"))

        assertEquals(GameSubPhase.TOSS_QUEUE_ACTIVE, next.subPhase)
        assertTrue(next.activeTossIn?.ranks?.contains(Rank.SEVEN) == true)
        assertEquals("seven1", next.discardPile.peekTop()?.id)
    }

    @Test
    fun aSevenCanBeSwappedIntoHandInsteadOfPlayed() {
        val seven = testCard(Rank.SEVEN, "seven1")
        val state = testState(
            subPhase = GameSubPhase.CHOOSING,
            players = listOf(
                testPlayer(
                    "p1", "Player 1", isHuman = true,
                    cards = listOf(testCard(Rank.KING, "p1c1"), testCard(Rank.QUEEN, "p1c2")),
                ),
            ),
            pendingAction = pending(seven, "p1", actionPhase = ActionPhase.CHOOSING_ACTION),
        )

        val next = unsafeReduce(state, swapCard("p1", 0))

        assertEquals(GameSubPhase.TOSS_QUEUE_ACTIVE, next.subPhase)
        assertEquals("seven1", next.players[0].cards[0].id)
    }

    @Test
    fun aMatchingSevenCanBeTossedInAndQueuesItsAction() {
        val state = testState(
            subPhase = GameSubPhase.TOSS_QUEUE_ACTIVE,
            turnNumber = 1,
            players = listOf(
                testPlayer("p1", "Player 1", isHuman = true),
                testPlayer(
                    "p2", "Player 2", isHuman = false,
                    cards = listOf(testCard(Rank.SEVEN, "p2s1"), testCard(Rank.EIGHT, "p2c2")),
                ),
            ),
            activeTossIn = tossIn(ranks = listOf(Rank.SEVEN), initiatorId = "p1"),
        )

        val next = unsafeReduce(state, participateInTossIn("p2", listOf(0)))

        assertEquals(1, next.players[1].cards.size)
        assertEquals(1, next.activeTossIn?.queuedActions?.size)
        assertEquals(Rank.SEVEN, next.activeTossIn?.queuedActions?.first()?.rank)
    }

    @Test
    fun aQueuedSevenIsPlayedByWhoeverTossedItIn() {
        val state = testState(
            subPhase = GameSubPhase.TOSS_QUEUE_ACTIVE,
            turnNumber = 1,
            players = listOf(
                testPlayer("p1", "Player 1", isHuman = true),
                testPlayer(
                    "p2", "Player 2", isHuman = false,
                    cards = listOf(testCard(Rank.ACE, "p2c1"), testCard(Rank.KING, "p2c2")),
                ),
            ),
            activeTossIn = tossIn(
                ranks = listOf(Rank.SEVEN),
                initiatorId = "p1",
                participants = listOf("p2"),
                queuedActions = listOf(TossInAction(playerId = "p2", rank = Rank.SEVEN, position = 0)),
            ),
        )

        var next = markPlayersReady(state, listOf("p1", "p2"))
        next = unsafeReduce(next, useCardAction("p2"))

        assertEquals(GameSubPhase.AWAITING_ACTION, next.subPhase)
        assertEquals(Rank.SEVEN, next.pendingAction?.card?.rank)

        next = unsafeReduce(next, selectTarget("p2", "p2", 0))
        next = unsafeReduce(next, confirmPeek("p2"))

        assertEquals(GameSubPhase.TOSS_QUEUE_ACTIVE, next.subPhase)
        assertTrue(next.activeTossIn?.ranks?.contains(Rank.SEVEN) == true)
    }

    @Test
    fun anotherPlayerCannotAimSomeoneElsesSeven() {
        val state = testState(
            subPhase = GameSubPhase.AWAITING_ACTION,
            currentPlayerIndex = 0,
            pendingAction = pending(testCard(Rank.SEVEN, "seven1"), "p1"),
        )

        assertTrue(rejects(state, selectTarget("p2", "p2", 0)))
    }
}
