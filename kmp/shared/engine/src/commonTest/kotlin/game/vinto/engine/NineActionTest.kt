package game.vinto.engine

import game.vinto.shapes.ActionPhase
import game.vinto.shapes.GameSubPhase
import game.vinto.shapes.Rank
import game.vinto.shapes.TossInAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The 9, ported from `packages/engine/src/lib/__tests__/card-actions/nine.test.ts`.
 *
 * Value 9; the action peeks one card belonging to another player, and finishing it opens a
 * toss-in window on rank 9.
 */
class NineActionTest {

    @Test
    fun peekingAnOpponentsCardFinishesTheTurnAndOpensATossIn() {
        val card = testCard(Rank.NINE, "nine1")
        val state = testState(
            subPhase = GameSubPhase.AWAITING_ACTION,
            players = listOf(
                testPlayer("p1", "Player 1", isHuman = true),
                testPlayer(
                    "p2", "Player 2", isHuman = false,
                    cards = listOf(testCard(Rank.KING, "p2c1"), testCard(Rank.ACE, "p2c2")),
                ),
            ),
            pendingAction = pending(card, "p1"),
        )

        var next = unsafeReduce(state, selectTarget("p1", "p2", 0))
        next = unsafeReduce(next, confirmPeek("p1"))

        assertEquals(GameSubPhase.TOSS_QUEUE_ACTIVE, next.subPhase)
        assertTrue(next.activeTossIn?.ranks?.contains(Rank.NINE) == true)
        assertEquals("nine1", next.discardPile.peekTop()?.id)
    }

    @Test
    fun peekingTeachesTheViewerWhatItSaw() {
        // Not in the TypeScript, which only checks the phase moved on. A peek that reveals
        // nothing is the same as no peek at all, so what it wrote is worth asserting.
        val state = testState(
            subPhase = GameSubPhase.AWAITING_ACTION,
            players = listOf(
                testPlayer("p1", "Player 1", isHuman = true),
                testPlayer("p2", "Player 2", isHuman = false, cards = listOf(testCard(Rank.KING, "p2c1"))),
            ),
            pendingAction = pending(testCard(Rank.NINE, "nine1"), "p1"),
        )

        val next = unsafeReduce(state, selectTarget("p1", "p2", 0))

        assertEquals(
            "p2c1",
            next.players[0].opponentKnowledge?.get("p2")?.knownCards?.get(0)?.id,
            "the peeking player learned nothing",
        )
    }

    @Test
    fun aNineCanBeSwappedIntoHandInsteadOfPlayed() {
        val card = testCard(Rank.NINE, "nine1")
        val state = testState(
            subPhase = GameSubPhase.CHOOSING,
            players = listOf(
                testPlayer(
                    "p1", "Player 1", isHuman = true,
                    cards = listOf(testCard(Rank.KING, "p1c1"), testCard(Rank.QUEEN, "p1c2")),
                ),
            ),
            pendingAction = pending(card, "p1", actionPhase = ActionPhase.CHOOSING_ACTION),
        )

        val next = unsafeReduce(state, swapCard("p1", 0))

        assertEquals(GameSubPhase.TOSS_QUEUE_ACTIVE, next.subPhase)
        assertEquals("nine1", next.players[0].cards[0].id)
    }

    @Test
    fun aMatchingNineCanBeTossedInAndQueuesItsAction() {
        val state = testState(
            subPhase = GameSubPhase.TOSS_QUEUE_ACTIVE,
            turnNumber = 1,
            players = listOf(
                testPlayer("p1", "Player 1", isHuman = true),
                testPlayer(
                    "p2", "Player 2", isHuman = false,
                    cards = listOf(testCard(Rank.NINE, "p2m1"), testCard(Rank.SIX, "p2c2")),
                ),
            ),
            activeTossIn = tossIn(ranks = listOf(Rank.NINE), initiatorId = "p1"),
        )

        val next = unsafeReduce(state, participateInTossIn("p2", listOf(0)))

        assertEquals(1, next.players[1].cards.size)
        assertEquals(1, next.activeTossIn?.queuedActions?.size)
        assertEquals(Rank.NINE, next.activeTossIn?.queuedActions?.first()?.rank)
    }

    @Test
    fun aQueuedNineIsPlayedByWhoeverTossedItIn() {
        val state = testState(
            subPhase = GameSubPhase.TOSS_QUEUE_ACTIVE,
            turnNumber = 1,
            players = listOf(
                testPlayer("p1", "Player 1", isHuman = true, cards = listOf(testCard(Rank.FIVE, "p1c1"))),
                testPlayer(
                    "p2", "Player 2", isHuman = false,
                    cards = listOf(testCard(Rank.ACE, "p2c1"), testCard(Rank.KING, "p2c2")),
                ),
            ),
            activeTossIn = tossIn(
                ranks = listOf(Rank.NINE),
                initiatorId = "p1",
                participants = listOf("p2"),
                queuedActions = listOf(TossInAction(playerId = "p2", rank = Rank.NINE, position = 0)),
            ),
        )

        var next = markPlayersReady(state, listOf("p1", "p2"))
        next = unsafeReduce(next, useCardAction("p2"))

        assertEquals(GameSubPhase.AWAITING_ACTION, next.subPhase)
        assertEquals(Rank.NINE, next.pendingAction?.card?.rank)

        next = unsafeReduce(next, selectTarget("p2", "p1", 0))
        next = unsafeReduce(next, confirmPeek("p2"))

        assertEquals(GameSubPhase.TOSS_QUEUE_ACTIVE, next.subPhase)
        assertTrue(next.activeTossIn?.ranks?.contains(Rank.NINE) == true)
    }

    @Test
    fun anotherPlayerCannotAimSomeoneElsesNine() {
        val state = testState(
            subPhase = GameSubPhase.AWAITING_ACTION,
            currentPlayerIndex = 0,
            pendingAction = pending(testCard(Rank.NINE, "nine1"), "p1"),
        )

        assertTrue(rejects(state, selectTarget("p2", "p3", 0)))
    }

    @Test
    fun theEngineDoesNotStopANineFromPeekingItsOwnersOwnCard() {
        // The written rule says "one card of another player", and neither implementation
        // enforces it — the TypeScript test for this asserts nothing at all and says so in a
        // comment. Pinned here as *current behaviour* rather than as intent, so that changing
        // it is a deliberate act and not a surprise.
        val state = testState(
            subPhase = GameSubPhase.AWAITING_ACTION,
            players = listOf(
                testPlayer("p1", "Player 1", isHuman = true, cards = listOf(testCard(Rank.KING, "p1c1"))),
            ),
            pendingAction = pending(testCard(Rank.NINE, "nine1"), "p1"),
        )

        assertTrue(!rejects(state, selectTarget("p1", "p1", 0)))
    }
}
