package game.vinto.engine

import game.vinto.shapes.ActionPhase
import game.vinto.shapes.GameSubPhase
import game.vinto.shapes.PendingCardOrigin
import game.vinto.shapes.Rank
import game.vinto.shapes.TossInAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Ace, ported from `packages/engine/src/lib/__tests__/card-actions/ace.test.ts`.
 *
 * Value 1; the action makes a chosen player draw a card face-down. It is the only action that
 * names a *player* rather than a card, which is why its payload has no position.
 */
class AceActionTest {

    @Test
    fun playingAnAceMakesTheChosenPlayerDraw() {
        val state = testState(
            subPhase = GameSubPhase.CHOOSING,
            drawPile = pileOf(testCard(Rank.KING, "penalty1"), testCard(Rank.QUEEN, "card2")),
            players = listOf(
                testPlayer(
                    "p1", "Player 1", isHuman = true,
                    cards = listOf(testCard(Rank.KING, "p1c1"), testCard(Rank.QUEEN, "p1c2")),
                ),
                testPlayer(
                    "p2", "Player 2", isHuman = false,
                    cards = listOf(testCard(Rank.SEVEN, "p2c1"), testCard(Rank.EIGHT, "p2c2")),
                ),
            ),
            pendingAction = pending(
                testCard(Rank.ACE, "ace1"), "p1",
                actionPhase = ActionPhase.CHOOSING_ACTION,
            ),
        )

        var next = unsafeReduce(state, useCardAction("p1"))
        assertEquals(GameSubPhase.AWAITING_ACTION, next.subPhase)

        next = unsafeReduce(next, selectPlayerTarget("p1", "p2"))

        assertEquals(3, next.players[1].cards.size)
        assertEquals(1, next.drawPile.size)
        assertEquals(true, next.discardPile.peekTop()?.played)

        assertEquals(GameSubPhase.TOSS_QUEUE_ACTIVE, next.subPhase)
        assertNotNull(next.activeTossIn)
        assertTrue(next.activeTossIn?.ranks?.contains(Rank.ACE) == true)
        assertEquals("p1", next.activeTossIn?.initiatorId)
    }

    @Test
    fun anAceCanBeSwappedIntoHandInsteadOfPlayed() {
        val state = testState(
            subPhase = GameSubPhase.CHOOSING,
            players = listOf(
                testPlayer(
                    "p1", "Player 1", isHuman = true,
                    cards = listOf(testCard(Rank.KING, "p1c1"), testCard(Rank.QUEEN, "p1c2")),
                ),
            ),
            pendingAction = pending(
                testCard(Rank.ACE, "ace1"), "p1",
                actionPhase = ActionPhase.CHOOSING_ACTION,
                from = PendingCardOrigin.HAND,
            ),
        )

        val next = unsafeReduce(state, swapCard("p1", 0))

        assertEquals(GameSubPhase.TOSS_QUEUE_ACTIVE, next.subPhase)
        assertEquals("ace1", next.players[0].cards[0].id)
        assertEquals("p1c1", next.discardPile.peekTop()?.id)
    }

    @Test
    fun takingAnAceOffTheDiscardCommitsYouToPlayingIt() {
        val state = testState(
            subPhase = GameSubPhase.IDLE,
            drawPile = pileOf(testCard(Rank.KING, "penalty1")),
            discardPile = pileOf(testCard(Rank.ACE, "ace1")),
            players = listOf(
                testPlayer("p1", "Player 1", isHuman = true),
                testPlayer("p2", "Player 2", isHuman = false, cards = listOf(testCard(Rank.SEVEN, "p2c1"))),
            ),
        )

        var next = unsafeReduce(state, playDiscard("p1"))

        // No "choosing" step: the rule is that a card taken from the discard must be played.
        assertEquals(GameSubPhase.AWAITING_ACTION, next.subPhase)
        assertEquals("ace1", next.pendingAction?.card?.id)
        assertEquals(ActionPhase.SELECTING_TARGET, next.pendingAction?.actionPhase)

        next = unsafeReduce(next, selectPlayerTarget("p1", "p2"))

        assertEquals(2, next.players[1].cards.size)
        assertEquals(GameSubPhase.TOSS_QUEUE_ACTIVE, next.subPhase)
        assertTrue(next.activeTossIn?.ranks?.contains(Rank.ACE) == true)
    }

    @Test
    fun aMatchingAceCanBeTossedInAndQueuesItsAction() {
        val state = testState(
            subPhase = GameSubPhase.TOSS_QUEUE_ACTIVE,
            turnNumber = 1,
            drawPile = pileOf(testCard(Rank.KING, "penalty1")),
            players = listOf(
                testPlayer(
                    "p1", "Player 1", isHuman = true,
                    cards = listOf(testCard(Rank.KING, "p1c1"), testCard(Rank.QUEEN, "p1c2")),
                ),
                testPlayer(
                    "p2", "Player 2", isHuman = false,
                    cards = listOf(
                        testCard(Rank.ACE, "p2c1"),
                        testCard(Rank.SEVEN, "p2c2"),
                        testCard(Rank.EIGHT, "p2c3"),
                    ),
                ),
            ),
            activeTossIn = tossIn(ranks = listOf(Rank.ACE), initiatorId = "p1"),
        )

        val next = unsafeReduce(state, participateInTossIn("p2", listOf(0)))

        assertEquals(2, next.players[1].cards.size)
        assertEquals(1, next.activeTossIn?.queuedActions?.size)
        assertEquals(Rank.ACE, next.activeTossIn?.queuedActions?.first()?.rank)
        assertTrue(next.activeTossIn?.participants?.contains("p2") == true)
    }

    @Test
    fun aQueuedAceIsPlayedAndThenTheWindowCloses() {
        val state = testState(
            subPhase = GameSubPhase.TOSS_QUEUE_ACTIVE,
            turnNumber = 1,
            drawPile = pileOf(testCard(Rank.KING, "penalty1")),
            players = listOf(
                testPlayer("p1", "Player 1", isHuman = true),
                testPlayer("p2", "Player 2", isHuman = false, cards = listOf(testCard(Rank.SEVEN, "p2c1"))),
            ),
            activeTossIn = tossIn(
                ranks = listOf(Rank.ACE),
                initiatorId = "p2",
                originalPlayerIndex = 1,
                participants = listOf("p2"),
                queuedActions = listOf(TossInAction(playerId = "p2", rank = Rank.ACE, position = 0)),
            ),
        )

        var next = markPlayersReady(state, listOf("p1", "p2"))
        next = unsafeReduce(next, useCardAction("p2"))

        assertEquals(GameSubPhase.AWAITING_ACTION, next.subPhase)
        assertEquals(Rank.ACE, next.pendingAction?.card?.rank)
        assertEquals("p2", next.pendingAction?.playerId)

        next = unsafeReduce(next, selectPlayerTarget("p2", "p1"))
        // The window stays open for further toss-ins, so everyone says they are done again.
        next = markPlayersReady(next, listOf("p1", "p2"))

        assertEquals(GameSubPhase.IDLE, next.subPhase)
        assertTrue(next.activeTossIn?.ranks?.contains(Rank.ACE) == true)
        assertEquals(0, next.activeTossIn?.queuedActions?.size)
        assertNull(next.pendingAction)
        assertEquals(Rank.ACE, next.discardPile.peekTop()?.rank)
        assertEquals(true, next.discardPile.peekTop()?.played)
    }

    @Test
    fun tossingInTheWrongRankDoesNotEndTheWindow() {
        val state = testState(
            subPhase = GameSubPhase.TOSS_QUEUE_ACTIVE,
            turnNumber = 1,
            drawPile = pileOf(testCard(Rank.KING, "penalty1")),
            players = listOf(
                testPlayer("p1", "Player 1", isHuman = true),
                testPlayer(
                    "p2", "Player 2", isHuman = false,
                    cards = listOf(testCard(Rank.SEVEN, "p2c1"), testCard(Rank.EIGHT, "p2c2")),
                ),
            ),
            activeTossIn = tossIn(ranks = listOf(Rank.ACE), initiatorId = "p1"),
        )

        val next = unsafeReduce(state, participateInTossIn("p2", listOf(0)))

        assertNotNull(next.activeTossIn)
        // The rule: a wrong toss-in costs a penalty card and bars the player from the window.
        assertTrue(
            next.players[1].cards.size > 2 ||
                next.activeTossIn?.failedAttempts?.any { it.playerId == "p2" } == true,
            "a wrong toss-in went unpunished",
        )
    }

    @Test
    fun anAceWithNoDeckLeftDrawsNothing() {
        val state = testState(
            subPhase = GameSubPhase.AWAITING_ACTION,
            drawPile = pileOf(),
            players = listOf(
                testPlayer("p1", "Player 1", isHuman = true),
                testPlayer("p2", "Player 2", isHuman = false),
            ),
            pendingAction = pending(testCard(Rank.ACE, "ace1"), "p1"),
        )

        val next = unsafeReduce(state, selectPlayerTarget("p1", "p2"))

        assertEquals(0, next.players[1].cards.size)
    }

    @Test
    fun anotherPlayerCannotAimSomeoneElsesAce() {
        val state = testState(
            subPhase = GameSubPhase.AWAITING_ACTION,
            currentPlayerIndex = 0,
            drawPile = pileOf(testCard(Rank.KING, "penalty1")),
            pendingAction = pending(testCard(Rank.ACE, "ace1"), "p1"),
        )

        assertTrue(rejects(state, selectPlayerTarget("p2", "p3")))
    }

    @Test
    fun anAceMayBeAimedAtItsOwnPlayer() {
        // Pointless, and legal — the rule says "choose a player", not "choose an opponent".
        val state = testState(
            subPhase = GameSubPhase.AWAITING_ACTION,
            drawPile = pileOf(testCard(Rank.KING, "penalty1")),
            players = listOf(
                testPlayer("p1", "Player 1", isHuman = true, cards = listOf(testCard(Rank.SEVEN, "p1c1"))),
            ),
            pendingAction = pending(testCard(Rank.ACE, "ace1"), "p1"),
        )

        val next = unsafeReduce(state, selectPlayerTarget("p1", "p1"))

        assertEquals(2, next.players[0].cards.size)
    }
}
