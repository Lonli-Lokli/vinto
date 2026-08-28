package game.vinto.engine

import game.vinto.shapes.ActiveTossIn
import game.vinto.shapes.GameAction
import game.vinto.shapes.GamePhase
import game.vinto.shapes.GameSubPhase
import game.vinto.shapes.ParticipateInTossInPayload
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.Rank
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A wrong toss-in bars you for the rest of the **round**, and the round is the deal.
 *
 * The engines used to clear the bar every time the turn came back to the first seat, which is
 * one lap of the table rather than a round — so a player who threw a wrong card in was free
 * again three turns later, and free for the whole of the final round, which is precisely when
 * a barred player would most like to be rid of a card. These cases hold the corrected rule
 * still: it is not the sort of thing anybody notices going wrong.
 */
class TossInBarTest {

    private val window = ActiveTossIn(
        ranks = listOf(Rank.SEVEN),
        initiatorId = "human-1",
        originalPlayerIndex = 0,
        participants = emptyList(),
        queuedActions = emptyList(),
        waitingForInput = true,
        playersReadyForNextTurn = emptyList(),
    )

    /** A hand with no Seven in it, so throwing one in is a wrong guess. */
    private fun tableWithAWrongThrow() = testState(
        players = listOf(
            testPlayer("human-1", "Human", isHuman = true, cards = listOf(testCard(Rank.SEVEN, "h1"))),
            testPlayer("bot-1", "Bot 1", isHuman = false, cards = listOf(testCard(Rank.TWO, "b1"))),
            testPlayer("bot-2", "Bot 2", isHuman = false, cards = listOf(testCard(Rank.THREE, "c1"))),
        ),
        subPhase = GameSubPhase.TOSS_QUEUE_ACTIVE,
        discardPile = pileOf(testCard(Rank.SEVEN, "top")),
        drawPile = pileOf(testCard(Rank.FOUR, "d1"), testCard(Rank.FIVE, "d2")),
        activeTossIn = window,
    )

    private fun throwWrongly(playerId: String) =
        GameAction.ParticipateInTossIn(ParticipateInTossInPayload(playerId, listOf(0)))

    @Test
    fun aWrongThrowBarsThatPlayer() {
        val after = unsafeReduce(tableWithAWrongThrow(), throwWrongly("bot-1"))

        assertEquals(
            listOf("bot-1"),
            after.roundFailedAttempts.map { it.playerId },
            "the wrong throw is remembered against them",
        )
        assertTrue(
            ActionValidator.validate(after, throwWrongly("bot-1")) is Validation.Invalid,
            "and they may not throw again",
        )
    }

    /**
     * The case the old code got wrong: the turn comes back round to the first seat, which the
     * engine counts as a new `roundNumber`, and the bar has to survive it.
     */
    @Test
    fun theBarSurvivesTheTurnComingBackRoundToTheFirstSeat() {
        var state = unsafeReduce(tableWithAWrongThrow(), throwWrongly("bot-1"))
        val laps = state.roundNumber

        // Everybody agrees the window is over, three times over, which walks the turn all the
        // way round the table and past seat zero.
        repeat(state.players.size * 2) {
            state.players.forEach { player ->
                val done = GameAction.PlayerTossInFinished(PlayerIdPayload(player.id))
                if (ActionValidator.validate(state, done) is Validation.Valid) {
                    state = unsafeReduce(state, done)
                }
            }
        }

        assertTrue(state.roundNumber > laps, "the table came round at least once: ${state.roundNumber}")
        assertEquals(
            listOf("bot-1"),
            state.roundFailedAttempts.map { it.playerId },
            "and the bar is still on, which is what a round-long bar means",
        )
    }

    /** And it is still on when the round is ending, which is when it matters most. */
    @Test
    fun theBarHoldsThroughTheFinalRound() {
        var state = unsafeReduce(tableWithAWrongThrow(), throwWrongly("bot-1"))
        state = state.copy(
            phase = GamePhase.FINAL,
            finalTurnTriggered = true,
            vintoCallerId = "human-1",
        )

        assertTrue(
            ActionValidator.validate(state, throwWrongly("bot-1")) is Validation.Invalid,
            "a barred player does not get a fresh start because somebody called Vinto",
        )
    }
}
