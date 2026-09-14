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
    fun aCardIsPutDownOnlyByTheHandThatHoldsIt() {
        // 3.14: putting a card down means drawing into its place, and only the lane's own seat
        // draws on that turn — a plan that had Ann put Bob's card down would be a plan nobody
        // could carry out.
        assertEquals(
            "you can only put down your own card",
            refused(null, PlanEdit.SetLane(ann, Step.PutDown(CardAt(bob, 0))), by = ann),
        )
        val own = edited(null, PlanEdit.SetLane(ann, Step.PutDown(CardAt(ann, 0))), by = ann)
        assertEquals(Step.PutDown(CardAt(ann, 0)), own.lanes.single().step)
        assertEquals(
            "a step may not touch the caller's cards",
            refused(null, PlanEdit.SetLane(ann, Step.PutDown(CardAt(caller, 0))), by = ann),
        )
    }

    @Test
    fun aCalledCardSaysWhatItsActionDoesAndOnlyWhatItCan() {
        // The reporter's own example: keep the draw in place of my Jack, call it, and the Jack
        // then moves two cards. One step, because it is one turn — the swap is what the call
        // *does*, not a second thing the turn does — and it is only legal for a card that does it.
        val jack = Step.PutDown(CardAt(ann, 0), guess = Rank.JACK, then = swap(bob, cid))
        assertEquals(jack, edited(null, PlanEdit.SetLane(ann, jack), by = ann).laneOf(ann)?.step)

        val king = Step.PutDown(CardAt(ann, 0), guess = Rank.KING, then = Step.Declare(Rank.SIX))
        assertEquals(king, edited(null, PlanEdit.SetLane(ann, king), by = ann).laneOf(ann)?.step)

        assertEquals(
            "only a Jack or a Queen swaps",
            refused(null, PlanEdit.SetLane(ann, jack.copy(guess = Rank.KING)), by = ann),
        )
        assertEquals(
            "only a King declares",
            refused(null, PlanEdit.SetLane(ann, king.copy(guess = Rank.JACK)), by = ann),
        )
        assertEquals(
            "call the card before saying what it does",
            refused(null, PlanEdit.SetLane(ann, jack.copy(guess = null)), by = ann),
        )
        assertEquals(
            "a played card can only look, swap, declare or make somebody draw",
            refused(null, PlanEdit.SetLane(ann, jack.copy(then = Step.Bin)), by = ann),
        )
    }

    @Test
    fun theCalledCardsSwapIsHeldToTheSameRulesAsAnyOther() {
        // The caller's cards are untouchable however the Jack reached the table, a swap is still
        // between two hands, and the card that was put down is on the pile — not there to move.
        fun called(then: Step) = PlanEdit.SetLane(ann, Step.PutDown(CardAt(ann, 0), guess = Rank.JACK, then = then))

        assertEquals("a step may not touch the caller's cards", refused(null, called(swap(bob, caller)), by = ann))
        assertEquals(
            "a swap is between two hands",
            refused(null, called(Step.Swap(CardAt(bob, 0), CardAt(bob, 1))), by = ann),
        )
        assertEquals(
            "the card put down is not there to swap",
            refused(null, called(Step.Swap(CardAt(ann, 0), CardAt(bob, 1))), by = ann),
        )
    }

    @Test
    fun editingALaneClearsWhatItsOwnerWouldRatherHaveDone() {
        // 3.13: a suggestion is about the step it sat beside. Once that step changes — to the
        // suggestion itself, or to anything else — it has been answered.
        val standing = CoalitionPlan(
            lanes = listOf(Lane(ann, swap(ann, bob), suggestion = Step.TakeTheDiscard)),
            agreed = listOf(bob),
            editedBy = bob,
        )
        val applied = edited(standing, PlanEdit.SetLane(ann, Step.TakeTheDiscard), by = ann)
        assertEquals(Step.TakeTheDiscard, applied.lanes.single().step)
        assertNull(applied.lanes.single().suggestion)
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
    fun aSwapIsBetweenTwoHands() {
        // A Jack and a Queen both swap across two players; a step the engine could never play
        // is not a plan, and the bots would be asked to agree to nothing.
        assertEquals(
            "a swap is between two hands",
            refused(null, PlanEdit.SetLane(ann, Step.Swap(CardAt(bob, 0), CardAt(bob, 1))), by = ann),
        )
    }

    @Test
    fun aStrangerHasNoSeatToPlanFrom() {
        refused(null, PlanEdit.SetLane(ann, swap(ann, bob)), by = "nobody-at-this-table")
        refused(null, PlanEdit.SetLane("nobody-at-this-table", swap(ann, bob)), by = ann)
    }

    @Test
    fun aTurnThatHasBeenPlayedCannotBeSetOrCleared() {
        // Ann has played: once Bob is on play, Ann's turn is history and the door refuses to
        // rewrite it. Bob's own turn and Cid's stay open.
        val played = edited(null, PlanEdit.SetLane(ann, swap(ann, bob)), by = ann).lockingLaneOf(bob, coalition)
        assertTrue(played.laneOf(ann)?.locked == true)

        assertEquals(
            "that turn has been played",
            refused(played, PlanEdit.SetLane(ann, Step.TakeTheDiscard), by = bob, onPlay = bob),
        )
        assertEquals("that turn has been played", refused(played, PlanEdit.ClearLane(ann), by = bob, onPlay = bob))
        edited(played, PlanEdit.SetLane(bob, Step.TakeTheDiscard), by = bob, onPlay = bob)
        edited(played, PlanEdit.SetLane(cid, Step.Bin), by = bob, onPlay = bob)
    }

    @Test
    fun theTurnInProgressStaysEditableUntilItHasBeenPlayed() {
        // **A turn never locks under the hand of the person playing it.** The moment the drawn
        // card is face up it is public, and the whole point of the plan is that the coalition
        // can say what to do with it — *"a Queen! forget the Jack, look at your fourth and my
        // second"* — while the card is still in the player's hand. So the seat on play is the
        // one lane that must stay open; the ones before it are the ones that close.
        val mine = edited(null, PlanEdit.SetLane(ann, swap(ann, bob)), by = bob, onPlay = ann)
        assertEquals(swap(ann, bob), mine.laneOf(ann)?.step)
        edited(mine, PlanEdit.SetTossIns(ann, listOf(TossIn(bob, Rank.FIVE))), by = cid, onPlay = ann)

        // Pacing has not stamped anything yet, and the door still knows Ann is history once Bob
        // is on play: the order of the coalition says so.
        assertEquals(
            "that turn has been played",
            refused(mine, PlanEdit.SetLane(ann, Step.Bin), by = cid, onPlay = bob),
        )
    }

    @Test
    fun lockingClosesThePlayedTurnsAndIsIdempotent() {
        val plan = edited(null, PlanEdit.SetLane(ann, swap(ann, bob)), by = ann)
        assertEquals(plan, plan.lockingLaneOf(ann, coalition), "the turn in progress was locked")
        assertEquals(plan, plan.lockingLaneOf(null, coalition))
        assertEquals(plan, plan.lockingLaneOf(caller, coalition), "nothing has been played while the caller is on play")

        val once = plan.lockingLaneOf(bob, coalition)
        assertTrue(once.laneOf(ann)?.locked == true)
        assertEquals(once, once.lockingLaneOf(bob, coalition), "a turn does not end twice")
        assertEquals(once, once.lockingLaneOf(cid, coalition), "a seat with no lane locks nothing")
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

    /**
     * A turn is built a part at a time, and neither part wipes the other.
     *
     * A turn is a *sequence*: take a card from one of the two piles, then do something with it.
     * Three of the four steps never recorded which pile, so "take the King off the pile **and**
     * declare fives" could not be said at all — a lane held one step and that was the whole turn.
     * The opening is its own edit because it is its own decision, and the first one; setting it
     * must leave an agreed step standing, and choosing a step must not silently pick a pile.
     */
    @Test
    fun theOpeningAndTheStepAreTwoPartsOfOneTurnAndNeitherWipesTheOther() {
        val opened = edited(null, PlanEdit.OpenLane(ann, Opening.TAKE_THE_DISCARD), by = ann)
        assertEquals(Opening.TAKE_THE_DISCARD, opened.laneOf(ann)?.opening)
        assertNull(opened.laneOf(ann)?.step, "opening a turn invented something to do with it")
        assertFalse(opened.isEmpty, "a turn with a pile chosen reads as nothing planned")

        val both = edited(opened, PlanEdit.SetLane(ann, Step.Declare(Rank.FIVE)), by = ann)
        assertEquals(Opening.TAKE_THE_DISCARD, both.laneOf(ann)?.opening, "choosing a step lost the pile")
        assertEquals(Step.Declare(Rank.FIVE), both.laneOf(ann)?.step)

        val reopened = edited(both, PlanEdit.OpenLane(ann, Opening.DRAW), by = ann)
        assertEquals(Opening.DRAW, reopened.laneOf(ann)?.opening)
        assertEquals(Step.Declare(Rank.FIVE), reopened.laneOf(ann)?.step, "changing the pile wiped the step")

        // And clearing still takes the whole turn back to "your call", both parts of it.
        val cleared = edited(reopened, PlanEdit.ClearLane(ann), by = ann)
        assertNull(cleared.laneOf(ann), "clearing left half a turn behind")
    }

    /**
     * The opening is refused exactly where a step is: it is the same turn.
     *
     * A turn that has been played is history, and the caller plans nothing. Both rules live in
     * one place, so a new part of a turn cannot quietly arrive without them.
     */
    @Test
    fun theOpeningAnswersToTheSameDoorTheStepDoes() {
        assertEquals(
            "only the coalition may plan",
            refused(null, PlanEdit.OpenLane(ann, Opening.DRAW), by = caller),
        )
        assertEquals(
            "that turn has been played",
            refused(null, PlanEdit.OpenLane(ann, Opening.DRAW), by = bob, onPlay = bob),
        )
    }

    /**
     * A put-down can carry the guess the rules let a player make.
     *
     * Swapping a card out lets its owner name its rank: right, they play that card's action for
     * free; wrong, they take a penalty card. A real decision with a real price, so it belongs on
     * the board rather than being sprung by one member — and it is **not** the King's declare,
     * which names a rank for everybody to throw in.
     */
    @Test
    fun aPutDownCarriesItsGuessAndIsStillOnlyEverYourOwnCard() {
        val guessed = Step.PutDown(CardAt(ann, 2), guess = Rank.SEVEN)
        val plan = edited(null, PlanEdit.SetLane(ann, guessed), by = ann)
        assertEquals(guessed, plan.laneOf(ann)?.step)
        assertEquals(Rank.SEVEN, (plan.laneOf(ann)?.step as? Step.PutDown)?.guess)

        assertEquals(
            "you can only put down your own card",
            refused(null, PlanEdit.SetLane(ann, Step.PutDown(CardAt(bob, 0), guess = Rank.SEVEN)), by = ann),
        )
    }

    // ------------------------------------------------------------------ the whole of the rules

    /**
     * The plan can say every coalition action the rules allow — *"you play this Queen, then I
     * throw in my Queen and play mine"* is a turn, and a turn is a sequence of everybody's
     * actions in the order they happen, not one member's move plus a list of promises beside it.
     * So a lane carries its throw-ins, in the order people throw, each saying what its card then
     * does; a look is a step; a King points at a card and says what that card does; an Ace names
     * who draws.
     */
    @Test
    fun aTurnCarriesItsThrowInsInTheOrderTheyAreThrown() {
        val throws = listOf(
            TossIn(bob, Rank.QUEEN, then = Step.Swap(CardAt(bob, 0), CardAt(cid, 0))),
            TossIn(cid, Rank.QUEEN),
        )
        val plan = edited(null, PlanEdit.SetTossIns(ann, throws), by = bob)
        assertEquals(throws, plan.laneOf(ann)?.tossIns, "the throw-ins did not keep their order")
        assertFalse(plan.isEmpty, "a turn with a throw-in planned reads as nothing planned")

        // The rest of the turn is built around them and neither part wipes the other.
        val opened = edited(plan, PlanEdit.OpenLane(ann, Opening.TAKE_THE_DISCARD), by = ann)
        assertEquals(throws, opened.laneOf(ann)?.tossIns, "choosing a pile lost the throw-ins")
        val stepped = edited(opened, PlanEdit.SetLane(ann, Step.UseIt), by = ann)
        assertEquals(throws, stepped.laneOf(ann)?.tossIns, "choosing a step lost the throw-ins")
        assertEquals(Opening.TAKE_THE_DISCARD, stepped.laneOf(ann)?.opening)

        // Reordering is the same edit with the list the other way round: last edit stands.
        val reversed = edited(stepped, PlanEdit.SetTossIns(ann, throws.reversed()), by = cid)
        assertEquals(throws.reversed(), reversed.laneOf(ann)?.tossIns)
        assertEquals(listOf(cid), reversed.agreed, "an edit to the throw-ins did not reset agreement")

        // Clearing the lane takes the whole turn off the board, throw-ins included.
        assertNull(edited(reversed, PlanEdit.ClearLane(ann), by = ann).laneOf(ann))
        // And nobody plans a throw for a turn that has been played.
        assertEquals(
            "that turn has been played",
            refused(stepped, PlanEdit.SetTossIns(ann, emptyList()), by = bob, onPlay = cid),
        )
    }

    @Test
    fun aThrowInSaysWhatItsCardDoesAndOnlyWhatItCan() {
        fun throwing(rank: Rank, then: Step?) = PlanEdit.SetTossIns(ann, listOf(TossIn(bob, rank, then)))
        fun ok(rank: Rank, then: Step?) = assertEquals(
            listOf(TossIn(bob, rank, then)),
            edited(null, throwing(rank, then), by = ann).laneOf(ann)?.tossIns,
        )

        // What each rank's action is, by the rules: a 7 or an 8 looks at one of your own, a 9 or
        // a 10 at one of somebody else's, a Jack swaps, a Queen looks at two and may swap them,
        // a King declares, an Ace makes somebody draw — and a plain card does nothing at all.
        ok(Rank.SEVEN, Step.Peek(CardAt(bob, 1)))
        ok(Rank.NINE, Step.Peek(CardAt(cid, 0)))
        ok(Rank.JACK, Step.Swap(CardAt(bob, 0), CardAt(cid, 0)))
        ok(Rank.QUEEN, Step.Peek(CardAt(bob, 0), CardAt(cid, 0)))
        ok(Rank.QUEEN, Step.Swap(CardAt(bob, 0), CardAt(cid, 0)))
        ok(Rank.KING, Step.Declare(Rank.FIVE, CardAt(cid, 0)))
        ok(Rank.ACE, Step.ForceDraw(cid))
        ok(Rank.FIVE, null)
        ok(Rank.QUEEN, null)

        assertEquals(
            "a 7 or an 8 looks at one of your own cards",
            refused(null, throwing(Rank.SEVEN, Step.Peek(CardAt(cid, 0))), by = ann),
        )
        assertEquals(
            "a 9 or a 10 looks at one card of another hand",
            refused(null, throwing(Rank.NINE, Step.Peek(CardAt(bob, 0))), by = ann),
        )
        assertEquals(
            "a Queen looks at two cards of two hands",
            refused(null, throwing(Rank.QUEEN, Step.Peek(CardAt(bob, 0))), by = ann),
        )
        assertEquals(
            "only a Jack or a Queen swaps",
            refused(null, throwing(Rank.KING, Step.Swap(CardAt(bob, 0), CardAt(cid, 0))), by = ann),
        )
        assertEquals(
            "only a King declares",
            refused(null, throwing(Rank.JACK, Step.Declare(Rank.FIVE)), by = ann),
        )
        assertEquals(
            "only an Ace makes somebody draw",
            refused(null, throwing(Rank.SEVEN, Step.ForceDraw(cid)), by = ann),
        )
        assertEquals(
            "a 5 has no action to plan",
            refused(null, throwing(Rank.FIVE, Step.Peek(CardAt(bob, 0))), by = ann),
        )
        assertEquals(
            "a played card can only look, swap, declare or make somebody draw",
            refused(null, throwing(Rank.JACK, Step.Bin), by = ann),
        )
        // The thrown card's trade is held to the same rules as any other.
        assertEquals(
            "a swap is between two hands",
            refused(null, throwing(Rank.JACK, Step.Swap(CardAt(bob, 0), CardAt(bob, 1))), by = ann),
        )
    }

    @Test
    fun nothingInAPlanTouchesTheCallerAndTheCallerThrowsNothingIn() {
        // The caller's hand is frozen from the moment of the call: the coalition may not look at
        // it, move it, name it or add to it, and the caller may not throw in. The same door for
        // every shape that names a seat.
        assertEquals(
            "the caller may not throw in",
            refused(null, PlanEdit.SetTossIns(ann, listOf(TossIn(caller, Rank.SEVEN))), by = ann),
        )
        assertEquals(
            "a step may not touch the caller's cards",
            refused(null, PlanEdit.SetLane(ann, Step.Peek(CardAt(caller, 0))), by = ann),
        )
        assertEquals(
            "a step may not touch the caller's cards",
            refused(null, PlanEdit.SetLane(ann, Step.Peek(CardAt(ann, 0), CardAt(caller, 0))), by = ann),
        )
        assertEquals(
            "a step may not touch the caller's cards",
            refused(null, PlanEdit.SetLane(ann, Step.Declare(Rank.FIVE, CardAt(caller, 0))), by = ann),
        )
        assertEquals(
            "the caller may not be made to draw",
            refused(null, PlanEdit.SetLane(ann, Step.ForceDraw(caller)), by = ann),
        )
        assertEquals(
            "a step may not touch the caller's cards",
            refused(
                null,
                PlanEdit.SetTossIns(ann, listOf(TossIn(bob, Rank.NINE, Step.Peek(CardAt(caller, 0))))),
                by = ann,
            ),
        )
        // Two looks are at two hands, as a Queen's are.
        assertEquals(
            "a Queen looks at two cards of two hands",
            refused(null, PlanEdit.SetLane(ann, Step.Peek(CardAt(bob, 0), CardAt(bob, 1))), by = ann),
        )
    }

    @Test
    fun aKingPointsAtACardAndSaysWhatThatCardDoes() {
        // The rules' King: name a card in somebody's hand and its rank; right, and that card
        // leaves the hand and its own action is played. So a declare names the card it points
        // at and what the card does — a pointed-at Jack trades two cards, and never the card
        // that has just left the hand.
        val pointed = Step.Declare(Rank.JACK, CardAt(bob, 0), then = Step.Swap(CardAt(bob, 1), CardAt(cid, 0)))
        assertEquals(pointed, edited(null, PlanEdit.SetLane(ann, pointed), by = ann).laneOf(ann)?.step)

        // The old shape — a rank alone — still stands, for a King aimed at whoever holds one.
        assertEquals(
            Step.Declare(Rank.FIVE),
            edited(null, PlanEdit.SetLane(ann, Step.Declare(Rank.FIVE)), by = ann).laneOf(ann)?.step,
        )

        assertEquals(
            "the declared card is not there to move",
            refused(
                null,
                PlanEdit.SetLane(ann, pointed.copy(then = Step.Swap(CardAt(bob, 0), CardAt(cid, 0)))),
                by = ann,
            ),
        )
        assertEquals(
            "only a King declares",
            refused(
                null,
                PlanEdit.SetLane(ann, Step.Declare(Rank.JACK, CardAt(bob, 0), then = Step.Declare(Rank.SIX))),
                by = ann,
            ),
        )
        assertEquals(
            "a 5 has no action to plan",
            refused(
                null,
                PlanEdit.SetLane(ann, Step.Declare(Rank.FIVE, CardAt(bob, 0), then = Step.ForceDraw(cid))),
                by = ann,
            ),
        )
        // The called card's action is held to the same rules: a called 9 looks at somebody else's card.
        assertEquals(
            "a 9 or a 10 looks at one card of another hand",
            refused(
                null,
                PlanEdit.SetLane(ann, Step.PutDown(CardAt(ann, 0), Rank.NINE, Step.Peek(CardAt(ann, 1)))),
                by = ann,
            ),
        )
        val calledNine = Step.PutDown(CardAt(ann, 0), Rank.NINE, Step.Peek(CardAt(bob, 1)))
        assertEquals(calledNine, edited(null, PlanEdit.SetLane(ann, calledNine), by = ann).laneOf(ann)?.step)
        val calledAce = Step.PutDown(CardAt(ann, 0), Rank.ACE, Step.ForceDraw(bob))
        assertEquals(calledAce, edited(null, PlanEdit.SetLane(ann, calledAce), by = ann).laneOf(ann)?.step)
    }

    @Test
    fun aBlindThrowNamesTheCardItThrowsAndSaysNothingOfWhatItDoes() {
        // Real tables do this: a member tosses a card nobody has named, because a wrong throw
        // costs a penalty card but shows the card, and knowing beats not knowing. The plan can
        // say it — *which* card, since no rank can be said — and nothing more: a card nobody has
        // seen has no action anybody can aim.
        val blind = TossIn(bob, rank = null, card = CardAt(bob, 1))
        assertTrue(blind.blind)
        assertFalse(TossIn(bob, Rank.FIVE).blind)
        assertEquals(
            listOf(blind),
            edited(null, PlanEdit.SetTossIns(ann, listOf(blind)), by = bob).laneOf(ann)?.tossIns,
        )
        assertEquals(
            "a blind throw names the card it throws",
            refused(null, PlanEdit.SetTossIns(ann, listOf(TossIn(bob))), by = bob),
        )
        assertEquals(
            "a blind throw cannot say what its card does",
            refused(null, PlanEdit.SetTossIns(ann, listOf(blind.copy(then = Step.Peek(CardAt(cid, 0))))), by = bob),
        )
        assertEquals(
            "you can only throw your own card",
            refused(null, PlanEdit.SetTossIns(ann, listOf(TossIn(bob, card = CardAt(cid, 0)))), by = bob),
        )
        assertEquals(
            "the caller may not throw in",
            refused(null, PlanEdit.SetTossIns(ann, listOf(TossIn(caller, card = CardAt(caller, 0)))), by = bob),
        )
        // A vouched throw may name its card too, so a film need not go looking for it.
        val named = TossIn(bob, Rank.FIVE, card = CardAt(bob, 0))
        assertEquals(
            listOf(named),
            edited(null, PlanEdit.SetTossIns(ann, listOf(named)), by = bob).laneOf(ann)?.tossIns,
        )
    }

    @Test
    fun aKingMayPointAtACardBeforeItNamesARank() {
        // The card comes first at a table — "point at Ember's third" — and the rank is what the
        // table says that card is, offered afterwards. So a King with a card and no rank yet is a
        // step in progress rather than a refusal; only what the pointed-at card *does* has to
        // wait for the rank, since its action is the rank's.
        val pointing = Step.Declare(card = CardAt(bob, 0))
        assertEquals(pointing, edited(null, PlanEdit.SetLane(ann, pointing), by = ann).laneOf(ann)?.step)
        assertEquals(
            "name the rank before saying what the card does",
            refused(
                null,
                PlanEdit.SetLane(ann, pointing.copy(then = Step.Swap(CardAt(bob, 1), CardAt(cid, 0)))),
                by = ann,
            ),
        )
        // And still never the caller's card.
        assertEquals(
            "a step may not touch the caller's cards",
            refused(null, PlanEdit.SetLane(ann, Step.Declare(card = CardAt(caller, 0))), by = ann),
        )
    }

    @Test
    fun everyShapeOfTheWholeRulesRoundTripsOnTheWire() {
        val plan = CoalitionPlan(
            lanes = listOf(
                Lane(
                    seat = ann,
                    step = Step.Declare(Rank.JACK, CardAt(bob, 0), then = Step.Swap(CardAt(bob, 1), CardAt(cid, 0))),
                    opening = Opening.TAKE_THE_DISCARD,
                    tossIns = listOf(
                        TossIn(bob, Rank.QUEEN, then = Step.Peek(CardAt(bob, 0), CardAt(cid, 0))),
                        TossIn(cid, Rank.ACE, then = Step.ForceDraw(bob)),
                        TossIn(ann, Rank.SEVEN, then = Step.Peek(CardAt(ann, 1))),
                        TossIn(cid, rank = null, card = CardAt(cid, 1)),
                    ),
                ),
                Lane(bob, Step.PutDown(CardAt(bob, 0), Rank.NINE, Step.Peek(CardAt(cid, 0)))),
                Lane(cid, Step.Declare(card = CardAt(bob, 1))),
            ),
            agreed = listOf(ann),
            editedBy = ann,
        )
        val text = VintoJson.encodeToString(CoalitionPlan.serializer(), plan)
        assertEquals(plan, VintoJson.decodeFromString(CoalitionPlan.serializer(), text))

        val edit: PlanEdit = PlanEdit.SetTossIns(ann, plan.lanes.first().tossIns)
        assertEquals(
            edit,
            VintoJson.decodeFromString(PlanEdit.serializer(), VintoJson.encodeToString(PlanEdit.serializer(), edit)),
        )

        // A throw written before a card could be named still reads as a vouched throw of its rank.
        val throwOfOld = """{"seat":"$bob","rank":"5"}"""
        assertEquals(TossIn(bob, Rank.FIVE), VintoJson.decodeFromString(TossIn.serializer(), throwOfOld))

        // A plan written before throw-ins existed still reads: the field is optional.
        val old = """{"lanes":[{"seat":"$ann","step":{"type":"swap","from":{"seat":"$ann","position":0},"to":{"seat":"$bob","position":1}}}]}"""
        assertEquals(emptyList(), VintoJson.decodeFromString(CoalitionPlan.serializer(), old).lanes.single().tossIns)
    }
}
