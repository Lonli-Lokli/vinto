package game.vinto.shapes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The one door every plan edit comes through, in both lives of `GameSession`.
 *
 * The plan is a board of parts agreed as a whole (design D7a). What this holds is the merge —
 * two people on different lanes do not overwrite each other, the same lane is last edit
 * stands — and the refusals: the caller, a stranger, a locked lane, a step that names the
 * caller's cards. And the reset: every edit unsettles the agreement, because a yes to a plan
 * that no longer exists is not a yes.
 */
class PlanEditTest {

    private val caller = "p1"
    private val ann = "p2"
    private val bob = "p3"
    private val cid = "p4"
    private val table = listOf(caller, ann, bob, cid)
    private val coalition = coalitionInTurnOrder(table, caller)

    private fun swap(a: String, b: String) = Step.Swap(CardAt(a, 0), CardAt(b, 1))

    /** Straight out of the call the caller is still on play, which is the window's own state. */
    private fun edited(plan: CoalitionPlan?, edit: PlanEdit, by: String, onPlay: String = caller): CoalitionPlan =
        assertIs<PlanEditOutcome.Edited>(
            plan.edited(edit, by, coalition, onPlay),
            "refused: ${plan.edited(edit, by, coalition, onPlay)}",
        ).plan

    private fun refused(plan: CoalitionPlan?, edit: PlanEdit, by: String, onPlay: String = caller): String =
        assertIs<PlanEditOutcome.Refused>(
            plan.edited(edit, by, coalition, onPlay),
            "an edit was accepted that should not be",
        ).reason

    @Test
    fun theCoalitionPlaysInTableOrderAfterTheCaller() {
        assertEquals(listOf(ann, bob, cid), coalitionInTurnOrder(table, caller))
        assertEquals(listOf(cid, caller, ann), coalitionInTurnOrder(table, bob))
    }

    @Test
    fun twoMembersOnDifferentLanesDoNotOverwriteEachOther() {
        // The defect a whole-draft door has: two messages cross and one lane is lost.
        val first = edited(null, PlanEdit.SetLane(ann, swap(ann, bob)), by = ann)
        val second = edited(first, PlanEdit.SetLane(bob, Step.Declare(Rank.KING)), by = bob)

        assertEquals(listOf(ann, bob), second.lanes.map { it.seat })
        assertEquals(swap(ann, bob), second.laneOf(ann)?.step, "Ann's lane was lost to Bob's edit")
        assertEquals(Step.Declare(Rank.KING), second.laneOf(bob)?.step)
    }

    @Test
    fun theSameLaneEditedTwiceIsLastEditStands() {
        val first = edited(null, PlanEdit.SetLane(ann, swap(ann, bob)), by = ann)
        val second = edited(first, PlanEdit.SetLane(ann, Step.TakeTheDiscard), by = bob)

        assertEquals(1, second.lanes.size)
        assertEquals(Step.TakeTheDiscard, second.laneOf(ann)?.step)
        assertEquals(bob, second.editedBy)
    }

    @Test
    fun lanesKeepTurnOrderWhateverOrderTheyWereSetIn() {
        val plan = edited(null, PlanEdit.SetLane(cid, Step.TakeTheDiscard), by = ann)
            .let { edited(it, PlanEdit.SetLane(ann, Step.TakeTheDiscard), by = ann) }

        assertEquals(listOf(ann, cid), plan.lanes.map { it.seat }, "a rehearsal would play them out of order")
    }

    @Test
    fun clearingALaneTakesItOffTheBoard() {
        val set = edited(null, PlanEdit.SetLane(ann, swap(ann, bob)), by = ann)
        val cleared = edited(set, PlanEdit.ClearLane(ann), by = bob)

        assertNull(cleared.laneOf(ann))
        assertTrue(cleared.isEmpty)
    }

    @Test
    fun everyEditResetsAgreementToTheEditorAlone() {
        val set = edited(null, PlanEdit.SetLane(ann, swap(ann, bob)), by = ann)
        assertEquals(listOf(ann), set.agreed, "making the edit is agreeing to it")

        val all = set.agreeing(bob, agree = true).agreeing(cid, agree = true)
        assertTrue(all.agreedBy(coalition), "three yeses is an agreed plan")

        val changed = edited(all, PlanEdit.SetLane(bob, Step.Declare(Rank.KING)), by = bob)
        assertEquals(listOf(bob), changed.agreed, "a yes to a plan that no longer exists is not a yes")
        assertFalse(changed.agreedBy(coalition))
    }

    @Test
    fun aNoTakesAYesBack() {
        val plan = edited(null, PlanEdit.SetLane(ann, swap(ann, bob)), by = ann)
            .agreeing(bob, agree = true)
            .agreeing(bob, agree = false)

        assertEquals(listOf(ann), plan.agreed)
    }

    @Test
    fun theCallerMayNeitherEditNorBePlannedFor() {
        assertEquals("only the coalition may plan", refused(null, PlanEdit.SetLane(ann, swap(ann, bob)), by = caller))
        refused(null, PlanEdit.SetLane(caller, swap(ann, bob)), by = ann)
        refused(null, PlanEdit.AddShed(Shed(caller, Rank.SEVEN)), by = ann)
    }

    @Test
    fun aStepMayNotNameTheCallersCards() {
        // The caller has no lane by construction; this is the other way their hand could get
        // into a plan, and it is refused at the same door.
        assertEquals(
            "a step may not touch the caller's cards",
            refused(null, PlanEdit.SetLane(ann, Step.Swap(CardAt(ann, 0), CardAt(caller, 2))), by = ann),
        )
    }

    @Test
    fun aStrangerHasNoSeatToPlanFrom() {
        refused(null, PlanEdit.SetLane(ann, swap(ann, bob)), by = "nobody-at-this-table")
        refused(null, PlanEdit.SetLane("nobody-at-this-table", swap(ann, bob)), by = ann)
    }

    @Test
    fun aLockedLaneCannotBeSetOrCleared() {
        val locked = edited(null, PlanEdit.SetLane(ann, swap(ann, bob)), by = ann).lockingLaneOf(ann)
        assertTrue(locked.laneOf(ann)?.locked == true)

        assertEquals(
            "that turn has already started",
            refused(locked, PlanEdit.SetLane(ann, Step.TakeTheDiscard), by = bob),
        )
        assertEquals("that turn has already started", refused(locked, PlanEdit.ClearLane(ann), by = bob))

        // Later lanes stay open: the round is still going and better information keeps arriving.
        edited(locked, PlanEdit.SetLane(bob, Step.TakeTheDiscard), by = bob)
    }

    @Test
    fun aTurnInProgressCannotBeGivenAFreshLane() {
        // Locking is pacing's doing and pacing runs after the fact; a lane set for the seat on
        // play *now* is the same turn a moment earlier, and the door refuses it directly.
        assertEquals(
            "that turn has already started",
            refused(null, PlanEdit.SetLane(ann, swap(ann, bob)), by = bob, onPlay = ann),
        )
        edited(null, PlanEdit.SetLane(bob, Step.TakeTheDiscard), by = ann, onPlay = ann)
    }

    @Test
    fun lockingIsIdempotentAndOnlyEverForwards() {
        val plan = edited(null, PlanEdit.SetLane(ann, swap(ann, bob)), by = ann)
        assertEquals(plan, plan.lockingLaneOf(bob), "a seat with no lane locks nothing")
        assertEquals(plan, plan.lockingLaneOf(null))

        val once = plan.lockingLaneOf(ann)
        assertEquals(once, once.lockingLaneOf(ann), "a turn does not begin twice")
    }

    @Test
    fun shedsAreAddedOnceAndTakenBack() {
        val shed = Shed(bob, Rank.SEVEN)
        val added = edited(null, PlanEdit.AddShed(shed), by = ann)
        val again = edited(added, PlanEdit.AddShed(shed), by = bob)
        assertEquals(listOf(shed), again.sheds, "the same shed was added twice")

        val removed = edited(again, PlanEdit.RemoveShed(shed), by = cid)
        assertTrue(removed.sheds.isEmpty())
        assertEquals(listOf(cid), removed.agreed)
    }

    @Test
    fun anEditRoundTripsOnTheWire() {
        val edit: PlanEdit = PlanEdit.SetLane(ann, Step.Swap(CardAt(ann, 0), CardAt(bob, 1)))
        val text = VintoJson.encodeToString(PlanEdit.serializer(), edit)
        assertEquals(edit, VintoJson.decodeFromString(PlanEdit.serializer(), text))
    }
}
