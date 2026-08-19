package game.vinto.engine

import game.vinto.shapes.GameAction
import game.vinto.shapes.GameState
import game.vinto.shapes.GameSubPhase
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.Rank
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Whose turn it is during a toss-in, ported from
 * `packages/engine/src/lib/__tests__/toss-in-state.test.ts`.
 *
 * A toss-in window interrupts a turn without ending it, and that is the whole subject of this
 * file. `currentPlayerIndex` must keep pointing at whoever was playing until *everyone* has
 * said they are done — and if a queued action moves it to a tosser, it has to come back
 * afterwards. Get this wrong and the turn order quietly rotates, which shows up much later as
 * a player getting two turns in a row.
 */
class TossInStateTest {

    private fun finished(playerId: String) =
        GameAction.PlayerTossInFinished(PlayerIdPayload(playerId))

    private fun threeSeats(
        firstCards: List<game.vinto.shapes.Card>,
        secondCards: List<game.vinto.shapes.Card>,
        thirdCards: List<game.vinto.shapes.Card>,
    ) = listOf(
        testPlayer("human-1", "Human", isHuman = true, cards = firstCards),
        testPlayer("bot-1", "Bot 1", isHuman = false, cards = secondCards),
        testPlayer("bot-2", "Bot 2", isHuman = false, cards = thirdCards),
    )

    @Test
    fun theTurnStaysPutUntilEveryPlayerHasSaidTheyAreDone() {
        val state = testState(
            turnNumber = 1,
            subPhase = GameSubPhase.IDLE,
            players = threeSeats(
                listOf(testCard(Rank.SEVEN, "h1"), testCard(Rank.THREE, "h2")),
                listOf(testCard(Rank.SEVEN, "b1"), testCard(Rank.FOUR, "b2")),
                listOf(testCard(Rank.FIVE, "b3"), testCard(Rank.SIX, "b4")),
            ),
            drawPile = pileOf(
                testCard(Rank.TWO, "d1"), testCard(Rank.EIGHT, "d2"), testCard(Rank.NINE, "d3"),
            ),
        )

        var next = unsafeReduce(state, drawCard("human-1"))
        assertEquals(GameSubPhase.CHOOSING, next.subPhase)
        assertEquals(0, next.currentPlayerIndex)

        next = unsafeReduce(next, discardCard("human-1"))
        assertEquals(GameSubPhase.TOSS_QUEUE_ACTIVE, next.subPhase)
        assertNotNull(next.activeTossIn)
        assertEquals(0, next.currentPlayerIndex)

        next = unsafeReduce(next, participateInTossIn("bot-1", listOf(0)))
        assertEquals(0, next.currentPlayerIndex, "a toss-in must not move the turn")
        assertEquals(GameSubPhase.TOSS_QUEUE_ACTIVE, next.subPhase)

        next = unsafeReduce(next, finished("bot-1"))
        assertEquals(0, next.currentPlayerIndex)

        next = unsafeReduce(next, finished("bot-2"))
        assertEquals(0, next.currentPlayerIndex)

        // The last one to finish is what closes the window and moves the turn on.
        next = unsafeReduce(next, finished("human-1"))
        assertEquals(1, next.currentPlayerIndex)
        assertEquals(GameSubPhase.AI_THINKING, next.subPhase)
    }

    @Test
    fun botsCanTossIntoAWindowAHumanOpened() {
        val state = testState(
            turnNumber = 1,
            subPhase = GameSubPhase.TOSS_QUEUE_ACTIVE,
            players = threeSeats(
                listOf(testCard(Rank.TWO, "h1"), testCard(Rank.THREE, "h2")),
                listOf(testCard(Rank.KING, "b1k"), testCard(Rank.FOUR, "b2")),
                listOf(testCard(Rank.KING, "b2k"), testCard(Rank.SIX, "b4")),
            ),
            activeTossIn = tossIn(
                ranks = listOf(Rank.KING),
                initiatorId = "human-1",
                waitingForInput = true,
            ),
        )

        var next = unsafeReduce(state, participateInTossIn("bot-1", listOf(0)))
        assertEquals(1, next.activeTossIn?.queuedActions?.size)
        assertEquals("bot-1", next.activeTossIn?.queuedActions?.first()?.playerId)
        assertEquals(0, next.currentPlayerIndex)

        next = unsafeReduce(next, participateInTossIn("bot-2", listOf(0)))
        assertEquals(2, next.activeTossIn?.queuedActions?.size)
        assertEquals("bot-2", next.activeTossIn?.queuedActions?.get(1)?.playerId)
        assertEquals(0, next.currentPlayerIndex)

        next = markPlayersReady(next, listOf("bot-1", "bot-2", "human-1"))
        next = unsafeReduce(next, useCardAction("bot-1"))

        assertEquals(GameSubPhase.AWAITING_ACTION, next.subPhase)
        assertNotNull(next.pendingAction)
        assertEquals("bot-1", next.pendingAction?.playerId, "the queue is served in order")
    }

    @Test
    fun anAllBotWindowBehavesTheSameWay() {
        val state = testState(
            turnNumber = 1,
            subPhase = GameSubPhase.AI_THINKING,
            players = listOf(
                testPlayer(
                    "bot-1", "Bot 1", isHuman = false,
                    cards = listOf(testCard(Rank.QUEEN, "b1q"), testCard(Rank.THREE, "b1c2")),
                ),
                testPlayer(
                    "bot-2", "Bot 2", isHuman = false,
                    cards = listOf(testCard(Rank.TWO, "b2c1"), testCard(Rank.FOUR, "b2c2")),
                ),
                testPlayer(
                    "bot-3", "Bot 3", isHuman = false,
                    cards = listOf(testCard(Rank.FIVE, "b3c1"), testCard(Rank.SIX, "b3c2")),
                ),
            ),
            drawPile = pileOf(
                testCard(Rank.TWO, "d1"), testCard(Rank.EIGHT, "d2"), testCard(Rank.NINE, "d3"),
            ),
        )

        var next = unsafeReduce(state, drawCard("bot-1"))
        assertEquals(GameSubPhase.CHOOSING, next.subPhase)
        assertEquals(0, next.currentPlayerIndex)

        next = unsafeReduce(next, discardCard("bot-1"))
        assertEquals(GameSubPhase.TOSS_QUEUE_ACTIVE, next.subPhase)
        assertTrue(next.activeTossIn?.ranks?.contains(Rank.TWO) == true)
        assertEquals(0, next.currentPlayerIndex)

        next = unsafeReduce(next, participateInTossIn("bot-2", listOf(0)))
        assertEquals(0, next.activeTossIn?.queuedActions?.size, "a 2 has no action to queue")
        assertEquals(0, next.currentPlayerIndex)

        next = markPlayersReady(next, listOf("bot-1", "bot-2", "bot-3"))

        assertEquals(1, next.currentPlayerIndex)
        assertEquals(GameSubPhase.AI_THINKING, next.subPhase)
    }

    @Test
    fun aPlayerMayTossIntoTheirOwnWindowAndKeepTheirTurn() {
        val state = testState(
            turnNumber = 1,
            subPhase = GameSubPhase.TOSS_QUEUE_ACTIVE,
            players = threeSeats(
                listOf(testCard(Rank.SEVEN, "h1"), testCard(Rank.FOUR, "h2")),
                listOf(testCard(Rank.KING, "b1k"), testCard(Rank.FOUR, "b2")),
                listOf(testCard(Rank.KING, "b2k"), testCard(Rank.SIX, "b4")),
            ),
            activeTossIn = tossIn(
                ranks = listOf(Rank.SEVEN),
                initiatorId = "human-1",
                waitingForInput = true,
            ),
        )

        var next = unsafeReduce(state, participateInTossIn("human-1", listOf(0)))
        next = markPlayersReady(next, listOf("human-1", "bot-1", "bot-2"))

        assertEquals(1, next.activeTossIn?.queuedActions?.size)
        assertEquals("human-1", next.activeTossIn?.queuedActions?.first()?.playerId)
        assertEquals(0, next.currentPlayerIndex)

        next = unsafeReduce(next, selectTarget("human-1", "human-1", 0))
        next = unsafeReduce(next, confirmPeek("human-1"))

        assertEquals(GameSubPhase.TOSS_QUEUE_ACTIVE, next.subPhase)
        assertNull(next.pendingAction)
        assertEquals(0, next.currentPlayerIndex)
    }

    /**
     * The turn order has to come back.
     *
     * A queued toss-in action moves `currentPlayerIndex` to whoever tossed the card, so they
     * can aim it. Once the window closes it must return to the seat *after* the one whose
     * turn was interrupted — not to the tosser's neighbour. The three cases below take the
     * three different exits: a plain peek, a King declaring a card with no action, and a King
     * declaring one that has an action.
     */
    private fun humanSwapsIntoATossInThatBotTwoJoins(
        botTwoSecondCard: Rank,
        humanFirstCard: Rank,
        botTwoFirstCard: Rank,
    ): GameState {
        val state = testState(
            turnNumber = 1,
            subPhase = GameSubPhase.AI_THINKING,
            players = threeSeats(
                listOf(testCard(humanFirstCard, "h1"), testCard(Rank.FOUR, "h2")),
                listOf(testCard(Rank.KING, "b1k"), testCard(Rank.FOUR, "b2")),
                listOf(testCard(botTwoFirstCard, "b2k"), testCard(botTwoSecondCard, "b4")),
            ),
            drawPile = pileOf(testCard(Rank.TWO, "draw_2"), testCard(Rank.THREE, "draw_3")),
        )

        var next = unsafeReduce(state, drawCard("human-1"))
        next = unsafeReduce(next, swapCard("human-1", 0))
        next = unsafeReduce(next, participateInTossIn("bot-2", listOf(0)))

        assertEquals(1, next.players.first { it.id == "bot-2" }.cards.size)

        next = markPlayersReady(next, listOf("human-1", "bot-1", "bot-2"))

        assertEquals(1, next.activeTossIn?.queuedActions?.size)
        assertEquals("bot-2", next.activeTossIn?.queuedActions?.first()?.playerId)
        assertEquals(2, next.currentPlayerIndex, "the tosser takes the seat to aim their card")

        return unsafeReduce(next, selectTarget("bot-2", "bot-2", 0))
    }

    private fun assertTurnReturnedToBotOne(state: GameState, expectedTopRank: Rank) {
        assertEquals(1, state.currentPlayerIndex, "the turn did not return to the right seat")
        assertEquals(GameSubPhase.AI_THINKING, state.subPhase)
        assertNull(state.pendingAction)
        assertEquals(expectedTopRank, state.discardPile.peekTop()?.rank)
        assertEquals(false, state.discardPile.peekTop()?.played)
    }

    @Test
    fun aTossedInSevenReturnsTheTurnToTheInterruptedOrder() {
        var next = humanSwapsIntoATossInThatBotTwoJoins(
            botTwoSecondCard = Rank.SIX,
            humanFirstCard = Rank.SEVEN,
            botTwoFirstCard = Rank.SEVEN,
        )
        next = unsafeReduce(next, confirmPeek("bot-2"))
        next = markPlayersReady(next, listOf("human-1", "bot-1", "bot-2"))

        assertTurnReturnedToBotOne(next, Rank.SEVEN)
    }

    @Test
    fun aTossedInKingDeclaringANonActionCardReturnsTheTurnToo() {
        var next = humanSwapsIntoATossInThatBotTwoJoins(
            botTwoSecondCard = Rank.SIX,
            humanFirstCard = Rank.KING,
            botTwoFirstCard = Rank.KING,
        )
        next = unsafeReduce(next, declareKing("bot-2", Rank.SIX))
        next = markPlayersReady(next, listOf("human-1", "bot-1", "bot-2"))

        assertTurnReturnedToBotOne(next, Rank.KING)
    }

    @Test
    fun aTossedInKingDeclaringAnActionCardReturnsTheTurnToo() {
        var next = humanSwapsIntoATossInThatBotTwoJoins(
            botTwoSecondCard = Rank.SEVEN,
            humanFirstCard = Rank.KING,
            botTwoFirstCard = Rank.KING,
        )
        next = unsafeReduce(next, declareKing("bot-2", Rank.SEVEN))
        next = markPlayersReady(next, listOf("human-1", "bot-1", "bot-2"))

        assertTurnReturnedToBotOne(next, Rank.KING)
    }
}
