package game.vinto.engine

import game.vinto.shapes.GamePhase
import game.vinto.shapes.GameState
import game.vinto.shapes.GameSubPhase
import game.vinto.shapes.Rank
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A wrong toss-in bars you from **the window you got it wrong in** — and, in the final round,
 * from the rest of the round.
 *
 * The bar used to last the whole deal in every phase, which meant one wrong read on the second
 * player's discard cost a player every window until the round ended, including windows opened
 * by cards they could not have known about when they guessed. The toss-in is the one moment
 * that belongs to the whole table at once, and a punishment that long makes most players stop
 * touching it. The final round keeps the long version, because there the coalition plays one
 * hand against the caller and a second guess is a second go at a shared prize.
 *
 * These cases hold both halves still. Neither is the sort of thing anybody notices going wrong:
 * a bar that is too long looks like a UI that has stopped offering something.
 */
class TossInBarTest {

    /**
     * Three seats, mid-deal, with bot-1 about to take a turn.
     *
     * The human holds a Seven and nothing on the table will be one, so throwing it in is always
     * a wrong guess — which is the only way to get barred in the first place. The draw pile is
     * stocked deep enough for two turns plus the penalty card a wrong throw costs.
     */
    private fun tableOnBotOnesTurn() = testState(
        players = listOf(
            testPlayer("human-1", "Human", isHuman = true, cards = listOf(testCard(Rank.SEVEN, "h1"))),
            testPlayer("bot-1", "Bot 1", isHuman = false, cards = listOf(testCard(Rank.TWO, "b1"))),
            testPlayer("bot-2", "Bot 2", isHuman = false, cards = listOf(testCard(Rank.THREE, "c1"))),
        ),
        currentPlayerIndex = 1,
        drawPile = pileOf(
            testCard(Rank.FOUR, "d1"),
            testCard(Rank.FIVE, "d2"),
            testCard(Rank.SIX, "d3"),
            testCard(Rank.TWO, "d4"),
            testCard(Rank.THREE, "d5"),
        ),
    )

    /** Draws and throws the card away, which is what opens a toss-in window on its rank. */
    private fun takeATurn(state: GameState, playerId: String): GameState =
        unsafeReduce(unsafeReduce(state, drawCard(playerId)), discardCard(playerId))

    private fun throwTheSevenIn(playerId: String = "human-1") =
        participateInTossIn(playerId, listOf(0))

    private fun barred(state: GameState) =
        ActionValidator.validate(state, throwTheSevenIn()) is Validation.Invalid

    @Test
    fun aWrongThrowBarsThatPlayerForTheRestOfTheWindow() {
        val open = takeATurn(tableOnBotOnesTurn(), "bot-1")
        assertTrue(
            ActionValidator.validate(open, throwTheSevenIn()) is Validation.Valid,
            "the window is open and the human has a card to try: this test proves nothing otherwise",
        )

        val after = unsafeReduce(open, throwTheSevenIn())

        assertEquals(
            listOf("human-1"),
            after.activeTossIn?.failedAttempts?.map { it.playerId },
            "the wrong throw is remembered against them in this window",
        )
        assertTrue(barred(after), "and they do not get a second guess at the same card")
    }

    /**
     * The user's own case, and the reason the rule changed: getting it wrong on one bot's
     * discard must not sit you out of the next bot's.
     */
    @Test
    fun theBarLiftsWhenTheNextWindowOpens() {
        var state = unsafeReduce(takeATurn(tableOnBotOnesTurn(), "bot-1"), throwTheSevenIn())
        assertTrue(barred(state), "barred to begin with, or the lift below means nothing")

        state = markPlayersReady(state, state.players.map { it.id })
        assertEquals("bot-2", state.players[state.currentPlayerIndex].id, "the turn moved on")

        state = takeATurn(state, "bot-2")

        assertEquals(
            emptyList(),
            state.activeTossIn?.failedAttempts?.map { it.playerId },
            "a new window starts everybody level",
        )
        assertTrue(
            ActionValidator.validate(state, throwTheSevenIn()) is Validation.Valid,
            "a wrong guess against bot-1's card does not bar the human from bot-2's",
        )
    }

    /**
     * The final round is the exception, and it is the half that has to keep working: there the
     * failure is remembered for the whole round rather than for the window.
     */
    @Test
    fun theBarHoldsAcrossWindowsInTheFinalRound() {
        var state = unsafeReduce(takeATurn(tableOnBotOnesTurn(), "bot-1"), throwTheSevenIn())

        assertEquals(
            listOf("human-1"),
            state.roundFailedAttempts.map { it.playerId },
            "the round remembers it either way — what changes is who consults the list",
        )

        // Somebody calls Vinto, and the window the human failed in gives way to a fresh one.
        state = state.copy(
            phase = GamePhase.FINAL,
            finalTurnTriggered = true,
            vintoCallerId = "bot-2",
            subPhase = GameSubPhase.TOSS_QUEUE_ACTIVE,
            activeTossIn = state.activeTossIn?.copy(failedAttempts = emptyList()),
        )

        assertTrue(
            barred(state),
            "in the final round a wrong throw costs the coalition member the rest of the round",
        )
    }
}
