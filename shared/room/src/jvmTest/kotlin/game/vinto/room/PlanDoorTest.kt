package game.vinto.room

import game.vinto.shapes.CardAt
import game.vinto.shapes.CoalitionPlan
import game.vinto.shapes.GamePhase
import game.vinto.shapes.Lane
import game.vinto.shapes.Step
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The door the shared plan comes through.
 *
 * One draft per final round, edited by anybody in the coalition, last edit standing — so this
 * door is the only thing between "we decide together" and "anybody can write anything". The
 * rules it has to hold are all structural, because the *content* of a plan is a matter of
 * opinion and none of the room's business:
 *
 *  - **the spine is fixed** (design D7): the final round is one turn per coalition member, in
 *    table order after the caller, so a plan has at most that many lanes and each names a seat
 *    that is actually playing one
 *  - **the caller has no lane**, structurally rather than by a check at the point of use — the
 *    coalition may not touch the caller's cards, and a plan that could name their turn is a
 *    plan that could name their cards
 *  - **a locked lane cannot be edited *or dropped*.** Dropping was the hole: the refusal only
 *    looked at lanes that were present, so omitting the locked one deleted the step out from
 *    under the person already playing it
 */
class PlanDoorTest {

    @Test
    fun anybodyInTheCoalitionMayWriteTheOnePlan() {
        val state = finalRoundCalledByABot()
        val room = decodeRoom(state)
        val mine = checkNotNull(room.seats[0].playerId)

        val edited = editPlan(decodeRoom(state), TOKEN_A, oneLane(mine))

        assertNull(edited.error)
        assertEquals(1, edited.state.plan?.lanes?.size, "the plan did not land")
    }

    @Test
    fun theCallerHasNoLaneInTheCoalitionsPlan() {
        // Not a check at the point of use: the caller's turn is over and their cards are
        // untouchable, so a lane naming them is a plan about the one hand the coalition is
        // playing against.
        val state = finalRoundCalledByABot()
        val caller = checkNotNull(decodeRoom(state).game?.vintoCallerId)

        val edited = editPlan(decodeRoom(state), TOKEN_A, oneLane(caller))

        assertNotNull(edited.error, "the coalition planned the caller's turn")
        assertNull(edited.state.plan, "and nothing was written")
    }

    @Test
    fun aLaneNamesASeatThatIsActuallyPlayingOne() {
        val state = finalRoundCalledByABot()

        val edited = editPlan(decodeRoom(state), TOKEN_A, oneLane("nobody-at-this-table"))

        assertNotNull(edited.error, "a lane was accepted for a seat that does not exist")
    }

    @Test
    fun aPlanCannotHaveMoreLanesThanTheRoundHasTurns() {
        // The spine is what makes a plan buildable at all — three lanes, order fixed. A plan
        // with a lane per *card* would be a different feature wearing this one's shape.
        val state = finalRoundCalledByABot()
        val room = decodeRoom(state)
        val mine = checkNotNull(room.seats[0].playerId)

        val edited = editPlan(
            decodeRoom(state),
            TOKEN_A,
            CoalitionPlan(lanes = List(9) { oneLane(mine).lanes.single() }),
        )

        assertNotNull(edited.error, "a plan longer than the round was accepted")
    }

    @Test
    fun aLockedLaneCannotBeDroppedByLeavingItOut() {
        // The hole this closes: the refusal only ever looked at the lanes that were *present*,
        // so omitting the locked one deleted the step out from under the seat already playing
        // it — the exact thing locking exists to prevent, reachable by sending less.
        val state = finalRoundCalledByABot(onPlay = 1)
        val room = decodeRoom(state)
        val mine = checkNotNull(room.seats[0].playerId)
        val onPlay = checkNotNull(room.game?.let { it.players[it.currentPlayerIndex].id })
        check(mine != onPlay) { "the point of this test is that the locked lane is somebody else's" }

        val withLock = encode(
            room.copy(
                plan = CoalitionPlan(
                    lanes = listOf(
                        Lane(seat = onPlay, step = null, locked = true),
                        Lane(seat = mine, step = null, locked = false),
                    ),
                ),
            ),
        )

        val edited = editPlan(decodeRoom(withLock), TOKEN_A, oneLane(mine))

        assertNotNull(edited.error, "a locked lane was dropped by omitting it")
        assertTrue(
            edited.state.plan?.lanes.orEmpty().any { it.seat == onPlay && it.locked },
            "and the lock survived the attempt",
        )
    }

    @Test
    fun anUnchangedLockedLaneMayBeResentBesideAnEdit() {
        // The other half, and the commoner one: a client editing a later lane sends the whole
        // plan back, locked lanes and all. Refusing that would make the plan uneditable the
        // moment the first turn began.
        val state = finalRoundCalledByABot(onPlay = 1)
        val room = decodeRoom(state)
        val mine = checkNotNull(room.seats[0].playerId)
        val onPlay = checkNotNull(room.game?.let { it.players[it.currentPlayerIndex].id })
        check(mine != onPlay)
        val locked = Lane(seat = onPlay, step = null, locked = true)

        val withLock = encode(room.copy(plan = CoalitionPlan(lanes = listOf(locked))))

        val edited = editPlan(
            decodeRoom(withLock),
            TOKEN_A,
            CoalitionPlan(lanes = listOf(locked, Lane(seat = mine, step = null, locked = false))),
        )

        assertNull(edited.error, "resending an unchanged locked lane was refused: ${edited.error}")
        assertEquals(2, edited.state.plan?.lanes?.size)
    }

    /** A plan with one lane for [seat], carrying a step so it is not vacuously empty. */
    private fun oneLane(seat: String) = CoalitionPlan(
        lanes = listOf(
            Lane(
                seat = seat,
                step = Step.Swap(
                    from = CardAt(seat = seat, position = 0, anchor = null),
                    to = CardAt(seat = seat, position = 1, anchor = null),
                ),
                locked = false,
            ),
        ),
    )

    /**
     * A dealt room in a final round a *bot* called, so every human seat is in the coalition.
     *
     * [onPlay] is named rather than inherited from the deal, because the two lock tests turn on
     * whether the locked lane belongs to the editor or to somebody else — and a fixture that
     * left that to the shuffle would pass or fail for reasons the test never states.
     */
    private fun finalRoundCalledByABot(onPlay: Int = 0): String {
        val room = decodeRoom(dealtRoom())
        val game = checkNotNull(room.game)
        val caller = checkNotNull(room.seats.last { it.tokenHash == null }.playerId)
        return encode(
            room.copy(
                game = game.copy(
                    phase = GamePhase.FINAL,
                    finalTurnTriggered = true,
                    vintoCallerId = caller,
                    currentPlayerIndex = onPlay,
                    players = game.players.map { it.copy(isVintoCaller = it.id == caller) },
                ),
            ),
        )
    }
}
