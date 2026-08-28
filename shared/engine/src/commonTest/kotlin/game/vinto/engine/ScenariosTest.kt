package game.vinto.engine

import game.vinto.shapes.ActionPhase
import game.vinto.shapes.Card
import game.vinto.shapes.GameAction
import game.vinto.shapes.GameState
import game.vinto.shapes.GameSubPhase
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.Rank
import game.vinto.shapes.TargetType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Positions that once went wrong, ported from
 * `legacy-web/packages/engine/src/lib/__tests__/scenarios.test.ts`.
 *
 * Every case here was a real bug: a turn that never advanced, a ready-list that was not
 * cleared, an action queue that fed itself forever. They read as sequences rather than as
 * rules because that is what they are — the shape of the position is the bug, and only
 * playing it out finds it.
 *
 * Worth keeping in the port even though the corpus covers the same handlers: a corpus
 * divergence tells you two implementations disagree, and one of these tells you what a
 * player would actually have seen.
 */
class ScenariosTest {

    private fun finished(playerId: String) =
        GameAction.PlayerTossInFinished(PlayerIdPayload(playerId))

    private fun seat(id: String, name: String, isHuman: Boolean, vararg cards: Card) =
        testPlayer(id, name, isHuman, cards.toList())

    private fun everyoneReady(state: GameState, ids: List<String>) = markPlayersReady(state, ids)

    @Test
    fun scenario01_aSecondAceTossedInDoesNotBreakTheReadyList() {
        // 1. Human plays an Ace, opening a toss-in on Ace.
        // 2. Human tosses in their second Ace, which queues.
        // 3. Everyone says they are done, so the queued Ace plays.
        // 4. When it finishes, the ready list must be *cleared* — the window is open again,
        //    and a stale list would refuse the human's next "done" as a duplicate.
        val ace1 = testCard(Rank.ACE, "A_1")
        val ace2 = testCard(Rank.ACE, "A_2")

        val state = testState(
            subPhase = GameSubPhase.CHOOSING,
            players = listOf(
                seat(
                    "human-1", "Human", true,
                    ace1, ace2, testCard(Rank.TWO, "2_1"), testCard(Rank.THREE, "3_1"),
                ),
                seat("bot-1", "Bot 1", false, testCard(Rank.TWO, "2_2"), testCard(Rank.THREE, "3_2")),
                seat("bot-2", "Bot 2", false, testCard(Rank.TWO, "2_3"), testCard(Rank.THREE, "3_3")),
                seat("bot-3", "Bot 3", false, testCard(Rank.TWO, "2_4"), testCard(Rank.THREE, "3_4")),
            ),
            drawPile = pileOf(testCard(Rank.TWO, "d1"), testCard(Rank.THREE, "d2")),
            pendingAction = pending(ace1, "human-1", actionPhase = ActionPhase.CHOOSING_ACTION),
        )

        var next = unsafeReduce(state, useCardAction("human-1"))
        assertEquals(GameSubPhase.AWAITING_ACTION, next.subPhase)

        next = unsafeReduce(next, selectPlayerTarget("human-1", "bot-1"))
        assertEquals(GameSubPhase.TOSS_QUEUE_ACTIVE, next.subPhase)
        assertNotNull(next.activeTossIn)
        assertTrue(next.activeTossIn?.ranks?.contains(Rank.ACE) == true)

        next = unsafeReduce(next, participateInTossIn("human-1", listOf(1)))
        assertEquals(1, next.activeTossIn?.queuedActions?.size)

        next = unsafeReduce(next, finished("human-1"))
        assertTrue(next.activeTossIn?.playersReadyForNextTurn?.contains("human-1") == true)

        next = everyoneReady(next, listOf("bot-1", "bot-2", "bot-3"))
        assertEquals(GameSubPhase.AWAITING_ACTION, next.subPhase)
        assertEquals(Rank.ACE, next.pendingAction?.card?.rank)

        next = unsafeReduce(next, selectPlayerTarget("human-1", "bot-2"))

        assertEquals(GameSubPhase.TOSS_QUEUE_ACTIVE, next.subPhase)
        assertNotNull(next.activeTossIn)
        assertTrue(next.activeTossIn?.playersReadyForNextTurn?.contains("human-1") != true)
        assertEquals(0, next.activeTossIn?.playersReadyForNextTurn?.size)

        // The whole point: saying "done" again must be accepted, not refused as a duplicate.
        val finalState = unsafeReduce(next, finished("human-1"))
        assertTrue(finalState.activeTossIn?.playersReadyForNextTurn?.contains("human-1") == true)
    }

    @Test
    fun scenario02_aQueuedJackAimsWithoutDisturbingAFullReadyList() {
        val jack = testCard(Rank.JACK, "J_1")
        val state = testState(
            subPhase = GameSubPhase.AWAITING_ACTION,
            players = listOf(
                seat("human-1", "Human", true, jack, testCard(Rank.TWO, "2_1"), testCard(Rank.THREE, "3_1")),
                seat("bot-1", "Bot 1", false, testCard(Rank.TWO, "2_2"), testCard(Rank.THREE, "3_2")),
                seat("bot-2", "Bot 2", false, testCard(Rank.TWO, "2_3"), testCard(Rank.THREE, "3_3")),
                seat("bot-3", "Bot 3", false, testCard(Rank.TWO, "2_4"), testCard(Rank.THREE, "3_4")),
            ),
            pendingAction = pending(jack, "human-1").copy(targetType = TargetType.SWAP_CARDS),
            activeTossIn = tossIn(
                ranks = listOf(Rank.JACK),
                initiatorId = "human-1",
                playersReadyForNextTurn = listOf("human-1", "bot-1", "bot-2", "bot-3"),
            ),
        )

        var next = unsafeReduce(state, selectTarget("human-1", "human-1", 0))
        next = unsafeReduce(next, selectTarget("human-1", "bot-1", 0))

        assertEquals(GameSubPhase.AWAITING_ACTION, next.subPhase)
        assertEquals(2, next.pendingAction?.targets?.size)
    }

    @Test
    fun scenario03_sayingDoneTwiceIsRefused() {
        val state = testState(
            currentPlayerIndex = 1,
            subPhase = GameSubPhase.CHOOSING,
            players = listOf(
                seat(
                    "bot-1", "Bot 1", false,
                    testCard(Rank.KING, "bot1_K"), testCard(Rank.SEVEN, "bot1_7"),
                    testCard(Rank.EIGHT, "bot1_8"), testCard(Rank.NINE, "bot1_9"),
                ),
                seat(
                    "bot-2", "Bot 2", false,
                    testCard(Rank.KING, "bot2_K"), testCard(Rank.SIX, "bot2_6"),
                    testCard(Rank.SEVEN, "bot2_7"), testCard(Rank.EIGHT, "bot2_8"),
                ),
                seat("bot-3", "Bot 3", false, testCard(Rank.TWO, "bot3_2"), testCard(Rank.THREE, "bot3_3")),
                seat("bot-4", "Bot 4", false, testCard(Rank.TWO, "bot4_2"), testCard(Rank.THREE, "bot4_3")),
            ),
            drawPile = pileOf(testCard(Rank.JACK, "draw_J"), testCard(Rank.QUEEN, "draw_Q")),
            pendingAction = pending(
                testCard(Rank.NINE, "bot2_draw"), "bot-2",
                actionPhase = ActionPhase.CHOOSING_ACTION,
            ),
        )

        // Bot 2 swaps its King out without declaring, opening a King window.
        var next = unsafeReduce(state, swapCard("bot-2", 0))
        assertEquals(listOf(Rank.KING), next.activeTossIn?.ranks)

        next = unsafeReduce(next, finished("bot-2"))
        assertTrue(next.activeTossIn?.playersReadyForNextTurn?.contains("bot-2") == true)

        next = unsafeReduce(next, participateInTossIn("bot-1", listOf(0)))
        assertEquals(1, next.activeTossIn?.queuedActions?.size)

        next = unsafeReduce(next, finished("bot-3"))
        assertTrue(next.activeTossIn?.playersReadyForNextTurn?.contains("bot-3") == true)

        assertTrue(rejects(next, finished("bot-2")), "a duplicate ready was accepted")
    }

    @Test
    fun scenario04_backToBackQueuedAcesDoNotLoopForever() {
        // The bug: each queued Ace opened a *new* Ace toss-in as it finished, so the queue
        // refilled itself and the turn never advanced. Every step below is checked, but the
        // one that matters is that the window continues rather than restarting.
        val king = testCard(Rank.KING, "K_1")
        val state = testState(
            subPhase = GameSubPhase.CHOOSING,
            players = listOf(
                seat("human-1", "Human", true, king, testCard(Rank.ACE, "A_1"), testCard(Rank.ACE, "A_2")),
                seat("bot-1", "Bot 1", false, testCard(Rank.ACE, "A_target")),
                seat("bot-2", "Bot 2", false),
                seat("bot-3", "Bot 3", false),
            ),
            pendingAction = pending(king, "human-1", actionPhase = ActionPhase.CHOOSING_ACTION),
        )

        var next = unsafeReduce(state, useCardAction("human-1"))
        assertEquals(GameSubPhase.AWAITING_ACTION, next.subPhase)
        assertEquals(Rank.KING, next.pendingAction?.card?.rank)

        next = unsafeReduce(next, selectTarget("human-1", "bot-1", 0))
        assertNotNull(next.pendingAction?.targets?.firstOrNull()?.card)

        next = unsafeReduce(next, declareKing("human-1", Rank.ACE))
        assertEquals(GameSubPhase.AWAITING_ACTION, next.subPhase)

        next = unsafeReduce(next, selectPlayerTarget("human-1", "bot-2"))
        assertEquals(GameSubPhase.TOSS_QUEUE_ACTIVE, next.subPhase)
        assertTrue(next.activeTossIn?.ranks?.contains(Rank.ACE) == true)
        assertNull(next.pendingAction)

        next = unsafeReduce(next, participateInTossIn("human-1", listOf(1)))
        assertEquals(1, next.activeTossIn?.queuedActions?.size)

        next = everyoneReady(next, listOf("human-1", "bot-1", "bot-2", "bot-3"))
        assertEquals(GameSubPhase.AWAITING_ACTION, next.subPhase)
        assertEquals(Rank.ACE, next.pendingAction?.card?.rank)
        assertEquals("human-1", next.pendingAction?.playerId)

        next = unsafeReduce(next, selectPlayerTarget("human-1", "bot-3"))

        assertEquals(GameSubPhase.TOSS_QUEUE_ACTIVE, next.subPhase)
        assertTrue(next.activeTossIn?.ranks?.contains(Rank.ACE) == true)
        assertNull(next.pendingAction)
        assertEquals(0, next.activeTossIn?.queuedActions?.size, "the queue refilled itself")

        // The second Ace has slid down to position 1 now the first one is gone.
        next = unsafeReduce(next, participateInTossIn("human-1", listOf(1)))
        assertEquals(1, next.activeTossIn?.queuedActions?.size)

        next = everyoneReady(next, listOf("human-1", "bot-1", "bot-2", "bot-3"))
        assertEquals(GameSubPhase.AWAITING_ACTION, next.subPhase)
        assertEquals(Rank.ACE, next.pendingAction?.card?.rank)

        next = unsafeReduce(next, selectPlayerTarget("human-1", "bot-1"))
        assertEquals(GameSubPhase.TOSS_QUEUE_ACTIVE, next.subPhase)
        assertNull(next.pendingAction)
        assertEquals(0, next.activeTossIn?.queuedActions?.size)

        next = everyoneReady(next, listOf("human-1", "bot-1", "bot-2", "bot-3"))
        assertEquals(1, next.currentPlayerIndex, "the turn never advanced")
    }

    private fun fourSeatsWithActionCards() = listOf(
        seat("p1", "Player 1", true, testCard(Rank.ACE, "p1c1"), testCard(Rank.KING, "p1c2")),
        seat("p2", "Player 2", false, testCard(Rank.ACE, "p2c1"), testCard(Rank.KING, "p2c2")),
        seat("p3", "Player 3", false, testCard(Rank.JACK, "p3c1"), testCard(Rank.JACK, "p3c2")),
        seat("p4", "Player 4", false, testCard(Rank.QUEEN, "p4c1"), testCard(Rank.QUEEN, "p4c2")),
    )

    @Test
    fun scenario05_aFullRoundOfSwapsAdvancesTurnAndRoundCounters() {
        var state = testState(
            subPhase = GameSubPhase.IDLE,
            turnNumber = 1,
            players = fourSeatsWithActionCards(),
            drawPile = pileOf(
                testCard(Rank.TWO, "2"), testCard(Rank.THREE, "3"),
                testCard(Rank.FOUR, "4"), testCard(Rank.FIVE, "5"),
            ),
        )

        fun takeTurn(playerId: String, position: Int, from: GameState): GameState {
            var updated = unsafeReduce(from, drawCard(playerId))
            updated = unsafeReduce(updated, swapCard(playerId, position))
            return everyoneReady(updated, listOf("p1", "p2", "p3", "p4"))
        }

        state = takeTurn("p1", 0, state)
        assertEquals(1, state.currentPlayerIndex)
        assertEquals(2, state.turnNumber)

        state = takeTurn("p2", 0, state)
        assertEquals(2, state.currentPlayerIndex)
        assertEquals(3, state.turnNumber)

        state = takeTurn("p3", 0, state)
        assertEquals(3, state.currentPlayerIndex)
        assertEquals(4, state.turnNumber)

        state = takeTurn("p4", 0, state)
        assertEquals(0, state.currentPlayerIndex, "the table did not wrap round")
        assertEquals(5, state.turnNumber)
        assertEquals(2, state.roundNumber, "the round counter did not tick on the wrap")
    }

    @Test
    fun scenario06_aFullRoundOfPlayedActionsAdvancesTheSameWay() {
        var state = testState(
            subPhase = GameSubPhase.IDLE,
            turnNumber = 1,
            players = fourSeatsWithActionCards(),
            drawPile = pileOf(
                testCard(Rank.QUEEN, "draw1"), testCard(Rank.QUEEN, "draw2"),
                testCard(Rank.QUEEN, "draw3"), testCard(Rank.QUEEN, "draw4"),
            ),
        )

        fun takeTurn(playerId: String, from: GameState): GameState {
            var updated = unsafeReduce(from, drawCard(playerId))
            updated = unsafeReduce(updated, useCardAction(playerId))
            return everyoneReady(updated, listOf("p1", "p2", "p3", "p4"))
        }

        state = takeTurn("p1", state)
        assertEquals(1, state.currentPlayerIndex)
        assertEquals(2, state.turnNumber)

        state = takeTurn("p2", state)
        assertEquals(2, state.currentPlayerIndex)

        state = takeTurn("p3", state)
        assertEquals(3, state.currentPlayerIndex)

        state = takeTurn("p4", state)
        assertEquals(0, state.currentPlayerIndex)
        assertEquals(5, state.turnNumber)
        assertEquals(2, state.roundNumber)
    }

    @Test
    fun scenario07_theDiscardPileIsReshuffledBackWhenTheDrawPileRunsDown() {
        var state = testState(
            subPhase = GameSubPhase.IDLE,
            turnNumber = 1,
            players = fourSeatsWithActionCards(),
            drawPile = pileOf(
                testCard(Rank.TWO, "draw1"), testCard(Rank.THREE, "draw2"),
                testCard(Rank.FOUR, "draw3"), testCard(Rank.FIVE, "draw4"),
            ),
            discardPile = pileOf(
                testCard(Rank.SIX, "discard1"),
                testCard(Rank.SIX, "discard2"),
                testCard(Rank.SIX, "discard3"),
            ),
        )

        fun takeTurn(playerId: String, from: GameState): GameState {
            var updated = unsafeReduce(from, drawCard(playerId))
            updated = unsafeReduce(updated, discardCard(playerId))
            return everyoneReady(updated, listOf("p1", "p2", "p3", "p4"))
        }

        assertEquals(4, state.drawPile.size)
        assertEquals(3, state.discardPile.size)

        state = takeTurn("p1", state)
        assertEquals(3, state.drawPile.size)
        assertEquals(4, state.discardPile.size)

        state = takeTurn("p2", state)
        assertEquals(2, state.drawPile.size)
        assertEquals(5, state.discardPile.size)

        // Down to one card at the turn boundary, so the discard pile comes back — all of it
        // except the top card, which stays where players can see and take it.
        state = takeTurn("p3", state)
        assertEquals(6, state.drawPile.size)
        assertEquals(1, state.discardPile.size)
    }

    @Test
    fun scenario08_aBotTossesInAKingDuringTheWindowAndPlaysIt() {
        // The TypeScript's scenario 08 is a verbatim copy of 07 — same setup, same
        // assertions — despite its title. Written here as what the title describes, since
        // that sequence is worth covering and nothing else covered it.
        val state = testState(
            subPhase = GameSubPhase.TOSS_QUEUE_ACTIVE,
            turnNumber = 1,
            players = listOf(
                seat("p1", "Player 1", true, testCard(Rank.ACE, "p1c1"), testCard(Rank.TWO, "p1c2")),
                seat("p2", "Player 2", false, testCard(Rank.KING, "p2c1"), testCard(Rank.SIX, "p2c2")),
                seat("p3", "Player 3", false, testCard(Rank.JACK, "p3c1"), testCard(Rank.JACK, "p3c2")),
                seat("p4", "Player 4", false, testCard(Rank.QUEEN, "p4c1"), testCard(Rank.QUEEN, "p4c2")),
            ),
            drawPile = pileOf(testCard(Rank.TWO, "draw1"), testCard(Rank.THREE, "draw2")),
            activeTossIn = tossIn(ranks = listOf(Rank.KING), initiatorId = "p1"),
        )

        var next = unsafeReduce(state, participateInTossIn("p2", listOf(0)))
        assertEquals(1, next.players[1].cards.size)
        assertEquals(1, next.activeTossIn?.queuedActions?.size)

        next = everyoneReady(next, listOf("p1", "p2", "p3", "p4"))
        assertEquals(1, next.currentPlayerIndex, "the tosser takes the seat to aim their King")

        next = unsafeReduce(next, useCardAction("p2"))
        next = unsafeReduce(next, selectTarget("p2", "p2", 0))
        next = unsafeReduce(next, declareKing("p2", Rank.SIX))

        assertEquals(0, next.players[1].cards.size, "the declared card did not leave the hand")
        assertTrue(next.activeTossIn?.ranks?.contains(Rank.SIX) == true)
        assertEquals(GameSubPhase.TOSS_QUEUE_ACTIVE, next.subPhase)
        assertEquals(0, next.activeTossIn?.queuedActions?.size)
    }
}
