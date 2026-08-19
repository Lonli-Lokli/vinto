package game.vinto.engine

import game.vinto.shapes.ActionPhase
import game.vinto.shapes.GameSubPhase
import game.vinto.shapes.Rank
import game.vinto.shapes.TossInAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The 8, ported from `packages/engine/src/lib/__tests__/card-actions/eight.test.ts`.
 *
 * Value 8, and the same action as the 7: peek one of your own cards. Kept as its own file
 * rather than folded into [SevenActionTest] because the two ranks are separate cards with
 * separate toss-in windows, and a shortcut that tested one and assumed the other would miss
 * a config mistake in exactly the way that matters.
 */
class EightActionTest {

    @Test
    fun peekingYourOwnCardFinishesTheTurnAndOpensATossIn() {
        val eight = testCard(Rank.EIGHT, "eight1")
        val state = testState(
            subPhase = GameSubPhase.AWAITING_ACTION,
            players = listOf(
                testPlayer(
                    "p1", "Player 1", isHuman = true,
                    cards = listOf(testCard(Rank.KING, "p1c1"), testCard(Rank.QUEEN, "p1c2")),
                ),
            ),
            pendingAction = pending(eight, "p1"),
        )

        var next = unsafeReduce(state, selectTarget("p1", "p1", 1))
        next = unsafeReduce(next, confirmPeek("p1"))

        assertEquals(GameSubPhase.TOSS_QUEUE_ACTIVE, next.subPhase)
        assertTrue(next.activeTossIn?.ranks?.contains(Rank.EIGHT) == true)
        assertEquals("eight1", next.discardPile.peekTop()?.id)
    }

    @Test
    fun anEightCanBeSwappedIntoHandInsteadOfPlayed() {
        val eight = testCard(Rank.EIGHT, "eight1")
        val state = testState(
            subPhase = GameSubPhase.CHOOSING,
            players = listOf(
                testPlayer(
                    "p1", "Player 1", isHuman = true,
                    cards = listOf(testCard(Rank.KING, "p1c1"), testCard(Rank.QUEEN, "p1c2")),
                ),
            ),
            pendingAction = pending(eight, "p1", actionPhase = ActionPhase.CHOOSING_ACTION),
        )

        val next = unsafeReduce(state, swapCard("p1", 0))

        assertEquals(GameSubPhase.TOSS_QUEUE_ACTIVE, next.subPhase)
        assertEquals("eight1", next.players[0].cards[0].id)
    }

    @Test
    fun aMatchingEightCanBeTossedInAndQueuesItsAction() {
        val state = testState(
            subPhase = GameSubPhase.TOSS_QUEUE_ACTIVE,
            turnNumber = 1,
            players = listOf(
                testPlayer("p1", "Player 1", isHuman = true),
                testPlayer(
                    "p2", "Player 2", isHuman = false,
                    cards = listOf(testCard(Rank.EIGHT, "p2e1"), testCard(Rank.SEVEN, "p2c2")),
                ),
            ),
            activeTossIn = tossIn(ranks = listOf(Rank.EIGHT), initiatorId = "p1"),
        )

        val next = unsafeReduce(state, participateInTossIn("p2", listOf(0)))

        assertEquals(1, next.players[1].cards.size)
        assertEquals(1, next.activeTossIn?.queuedActions?.size)
        assertEquals(Rank.EIGHT, next.activeTossIn?.queuedActions?.first()?.rank)
    }

    @Test
    fun aQueuedEightIsPlayedByWhoeverTossedItIn() {
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
                ranks = listOf(Rank.EIGHT),
                initiatorId = "p1",
                participants = listOf("p2"),
                queuedActions = listOf(TossInAction(playerId = "p2", rank = Rank.EIGHT, position = 0)),
            ),
        )

        var next = markPlayersReady(state, listOf("p1", "p2"))
        next = unsafeReduce(next, useCardAction("p2"))

        assertEquals(GameSubPhase.AWAITING_ACTION, next.subPhase)
        assertEquals(Rank.EIGHT, next.pendingAction?.card?.rank)

        next = unsafeReduce(next, selectTarget("p2", "p2", 0))
        next = unsafeReduce(next, confirmPeek("p2"))

        assertEquals(GameSubPhase.TOSS_QUEUE_ACTIVE, next.subPhase)
        assertTrue(next.activeTossIn?.ranks?.contains(Rank.EIGHT) == true)
    }

    @Test
    fun anotherPlayerCannotAimSomeoneElsesEight() {
        val state = testState(
            subPhase = GameSubPhase.AWAITING_ACTION,
            currentPlayerIndex = 0,
            pendingAction = pending(testCard(Rank.EIGHT, "eight1"), "p1"),
        )

        assertTrue(rejects(state, selectTarget("p2", "p2", 0)))
    }
}
