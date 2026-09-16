package game.vinto.client

import game.vinto.shapes.ActiveTossIn
import game.vinto.shapes.CoalitionPlan
import game.vinto.shapes.GamePhase
import game.vinto.shapes.GameSubPhase
import game.vinto.shapes.Lane
import game.vinto.shapes.Rank
import game.vinto.shapes.coalitionInTurnOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * The plan reads past a turn that has already been played.
 *
 * Reported from a phone as two symptoms of one gap: *"when ember declared vinto tide started his
 * turn without me"*, and *"on next turn we still see same 9 — a played card on turn 1 cannot be
 * played on turn 2"*. Replaying that deal shows what the reader had in front of them: Tide had
 * drawn, swapped and opened a toss-in window on the nine they put down, and the plan still offered
 * Tide's turn as *"draws · and we'll see"*.
 *
 * An undecided turn is where the plan's reading stops (`Transport.reach`, and deliberately — a
 * turn's table depends on the one before it). So **every** stop clamped back to turn one and drew
 * the same table, nine and all, and the reader could get to neither Dune's turn nor their own
 * without first deciding a turn that had already happened.
 *
 * Two places said "already played" and both meant "before the seat on play", which a seat keeps
 * for the whole of its turn — `CoalitionPlan.lockingLaneOf` and [Composing.decided]. What settles
 * a turn is its card being down, which is the engine's `turnIsSpent`, and `Lane.locked` is where
 * that answer is written.
 */
class PlanReadsPastAPlayedTurnTest {

    private val whole = teachingSession().view.value
    private val caller = whole.players.first { it.id != whole.viewerId }.id
    private val coalition = coalitionInTurnOrder(whole.players.map { it.id }, caller)

    /** The reported position: the first coalition seat has put its card down, and the table is throwing at it. */
    private fun theirCardIsDown() = whole.copy(
        phase = GamePhase.FINAL,
        finalTurnTriggered = true,
        vintoCallerId = caller,
        subPhase = GameSubPhase.TOSS_QUEUE_ACTIVE,
        currentPlayerIndex = whole.players.indexOfFirst { it.id == coalition.first() },
        activeTossIn = ActiveTossIn(
            ranks = listOf(Rank.NINE),
            initiatorId = coalition.first(),
            originalPlayerIndex = whole.players.indexOfFirst { it.id == coalition.first() },
            participants = emptyList(),
            queuedActions = emptyList(),
            waitingForInput = true,
            playersReadyForNextTurn = emptyList(),
        ),
    )

    private fun plan(playedIsLocked: Boolean) = CoalitionPlan(
        lanes = coalition.mapIndexed { index, seat -> Lane(seat, locked = playedIsLocked && index == 0) },
    )

    @Test
    fun aTurnWhoseCardIsDownDoesNotCloseThePagesAfterIt() {
        val board = assertNotNull(
            boardFor(theirCardIsDown(), plan(playedIsLocked = true), emptySet(), focus = Question.ThePlan(at = 2)),
            "the coalition member was given no board",
        )

        assertEquals(2, board.at, "the plan would not read past a turn that has already been played")
        assertEquals(2, board.lanes[1].number, "the second page is not the second turn")
    }

    /**
     * And it still stops at one that has *not* been played, which is the rule this must not undo:
     * a turn's table is the one the turn before it leaves, so reading past an undecided turn is
     * reading a table nobody can predict.
     */
    @Test
    fun anUndecidedTurnStillClosesThePagesAfterIt() {
        val board = assertNotNull(
            boardFor(theirCardIsDown(), plan(playedIsLocked = false), emptySet(), focus = Question.ThePlan(at = 2)),
        )

        assertEquals(1, board.at, "the plan read past a turn nobody has decided or played")
    }
}
