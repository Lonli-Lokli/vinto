package game.vinto.shapes

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A lane closes when its seat's turn has been **played**, not when the next seat begins.
 *
 * Reported from a phone, as two symptoms of one gap: *"when ember declared vinto tide started his
 * turn without me"*, and *"on next turn we still see same 9 — a played card on turn 1 cannot be
 * played on turn 2"*. Replaying that deal shows what the reader was looking at: Tide had drawn,
 * swapped, and opened a toss-in window on the nine they put down — and the plan still offered
 * Tide's turn as *"draws · and we'll see"*. An undecided turn is where the plan's reach stops
 * (`Transport.reach`), so every stop past it clamped back to turn 1 and drew the same table, nine
 * and all. The reader could not get to Dune's turn or their own without deciding a turn that had
 * already happened.
 *
 * [Lane.locked]'s own KDoc has always said the rule — "set once this seat's turn has been
 * **played** — it is history, and history is not edited" — and [lockingLaneOf] implemented
 * something weaker: the lanes *before* the seat on play. The seat on play keeps its lane for the
 * whole of its turn, and a turn lasts until the window it opened closes.
 *
 * The boundary is in that same KDoc: "a turn never locks under the hand of the person playing it:
 * the moment their drawn card is face up it is public, and the whole point of the plan is that the
 * coalition can say what to do with it **while the card is still in their hand**." So a card in
 * the hand keeps the lane open, and a card on the pile closes it.
 */
class LaneClosesWhenPlayedTest {

    private val coalition = listOf("tide", "dune", "you")

    private fun plan() = CoalitionPlan(lanes = coalition.map { Lane(it) })

    private fun locked(plan: CoalitionPlan) = plan.lanes.filter { it.locked }.map { it.seat }

    @Test
    fun aTurnStillBeingDecidedKeepsItsLane() {
        // Tide has drawn and is deciding: the card is in their hand, face up for everybody, and
        // saying what to do with it is the whole point of the plan.
        val settled = plan().lockingLaneOf("tide", coalition, spent = false)

        assertEquals(emptyList(), locked(settled), "a turn was closed while its card was in hand")
    }

    @Test
    fun aTurnWhoseCardIsDownIsHistory() {
        val settled = plan().lockingLaneOf("tide", coalition, spent = true)

        assertEquals(listOf("tide"), locked(settled), "a turn that has been played is still editable")
    }

    @Test
    fun theTurnsBeforeTheSeatOnPlayAreHistoryEitherWay() {
        // The rule that was already there, and it does not change: whatever Dune is doing, Tide
        // has been and gone.
        assertEquals(listOf("tide"), locked(plan().lockingLaneOf("dune", coalition, spent = false)))
        assertEquals(listOf("tide", "dune"), locked(plan().lockingLaneOf("dune", coalition, spent = true)))
    }

    @Test
    fun aSeatOutsideTheCoalitionSettlesNothing() {
        // The caller is on play — the round has come back round to them — and their seat is not
        // in the coalition at all, so there is no turn to close from it.
        assertEquals(emptyList(), locked(plan().lockingLaneOf("ember", coalition, spent = true)))
    }
}
