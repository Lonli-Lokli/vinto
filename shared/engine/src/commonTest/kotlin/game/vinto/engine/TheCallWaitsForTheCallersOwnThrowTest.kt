package game.vinto.engine

import game.vinto.shapes.GamePhase
import game.vinto.shapes.GameState
import game.vinto.shapes.Rank
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Vinto is declared at the **end** of a turn, and a turn is not over while the caller still
 * owes the table an action.
 *
 * Reported from a phone: *"I saw that bot played card, then toss in, then called vinto and then
 * played toss in cards he tossed in before. It must not work like this — before allowing
 * calling vinto engine must verify that all vinto caller cards has been processed including
 * toss in cards."*
 *
 * A thrown action card lives nowhere but `ActiveTossIn.queuedActions` until it is played, so a
 * caller with one still queued has a card in flight and a hand that is not finished moving —
 * and the final round's own rule, that nobody may touch the caller's cards, starts biting
 * halfway through the caller's own action. The window is the end of the turn; the call comes
 * after the throws that turn set off, not between them.
 *
 * **Only the caller's own.** A throw somebody else made is owed its action too, and
 * [FinalRoundRulesTest.aCardThrownInBeforeTheCallIsStillPlayed] holds that it still gets it —
 * the call does not wait on other seats, because their turns are not the one ending.
 */
class TheCallWaitsForTheCallersOwnThrowTest {

    /** p1 puts a seven face up and then throws their own second seven into the window it opened. */
    private fun throwingIntoTheirOwnWindow(): GameState {
        val players = listOf(
            testPlayer(
                "p1",
                "Player 1",
                isHuman = false,
                cards = listOf(testCard(Rank.SEVEN, "p1c1"), testCard(Rank.TWO, "p1c2")),
                knownCardPositions = listOf(0, 1),
            ),
            testPlayer("p2", "Player 2", isHuman = false, cards = listOf(testCard(Rank.FIVE, "p2c1"))),
            testPlayer("p3", "Player 3", isHuman = false, cards = listOf(testCard(Rank.SIX, "p3c1"))),
            testPlayer("p4", "Player 4", isHuman = false, cards = listOf(testCard(Rank.NINE, "p4c1"))),
        )
        var state = testState(
            players = players,
            drawPile = pileOf(testCard(Rank.SEVEN, "d1"), testCard(Rank.FOUR, "d2"), testCard(Rank.FOUR, "d3")),
        )
        state = unsafeReduce(state, drawCard("p1"))
        state = unsafeReduce(state, discardCard("p1"))
        state = unsafeReduce(state, participateInTossIn("p1", listOf(0)))
        return state
    }

    @Test
    fun theCallerCannotCallWhileTheirOwnThrowIsStillOwedItsAction() {
        val state = throwingIntoTheirOwnWindow()
        val queued = state.activeTossIn?.queuedActions?.map { action -> action.playerId }
        assertEquals(listOf("p1"), queued, "the throw was not queued: this test proves nothing")

        val refused = GameEngine.reduce(state, callVinto("p1"))
        assertIs<ReduceResult.Failure>(refused, "the call landed on top of the caller's own throw")
        assertEquals(GamePhase.PLAYING, refused.state.phase)
        assertEquals(null, refused.state.vintoCallerId)
    }

    @Test
    fun onceTheThrowHasPlayedTheCallLands() {
        // The table waves the window through, which starts p1's own seven: it looks at one of
        // p1's cards, and the turn is only over when that is done.
        var state = throwingIntoTheirOwnWindow()
        state = markPlayersReady(state, listOf("p1", "p2", "p3", "p4"))

        val owed = assertNotNull(state.pendingAction, "the queued seven never started")
        assertEquals("p1", owed.playerId)
        assertEquals(Rank.SEVEN, owed.card.rank)

        state = unsafeReduce(state, selectTarget("p1", "p1", 0))
        state = unsafeReduce(state, confirmPeek("p1"))

        // Nothing of p1's is left in flight, so the call is theirs to make.
        assertTrue(state.activeTossIn?.queuedActions.orEmpty().none { it.playerId == "p1" })
        state = unsafeReduce(state, callVinto("p1"))
        assertEquals(GamePhase.FINAL, state.phase)
        assertEquals("p1", state.vintoCallerId)
    }
}
