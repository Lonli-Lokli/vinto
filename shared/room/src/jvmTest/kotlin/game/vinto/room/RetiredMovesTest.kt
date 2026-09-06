package game.vinto.room

import game.vinto.shapes.GameAction
import game.vinto.shapes.LeaderIdPayload
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The coalition is not asked to nominate anybody, and the asking is refused at the door.
 *
 * The nomination decided nothing. A round is scored against the **lowest** coalition hand
 * whoever holds it, `CoalitionSearch` scores the same, and every bot declares before any
 * coalition turn is played — so the three planners already reach one target from the same
 * public claims. What the vote cost was a twenty-second stall at the top of the final round
 * and, worse, the seat boundary: naming nobody, `SET_COALITION_LEADER` slipped the door's
 * `actorId` check entirely, so the **Vinto caller** could nominate the coalition's leader.
 *
 * Refusing it in `ActionValidator` was tried first and is wrong: `GameEngine.reduce` validates
 * before it dispatches, so that rule refuses the replay path too — and 42 of the 50 frozen
 * recordings carry one. `CorpusReplayTest` said so immediately. The rule belongs at the two
 * live doors, which read the same `GameAction.retired` because a room and a solo game that
 * disagreed about which moves exist would be two games.
 */
class RetiredMovesTest {

    @Test
    fun theRoomRefusesToNominateACoalitionLeader() {
        val state = dealtRoom()
        val room = decodeRoom(state)
        val nominee = checkNotNull(room.seats[1].playerId)

        val result = decodeAction(
            applyAction(state, TOKEN_A, actionJson(nominate(nominee)), START + 1_000.0),
        )

        assertNotNull(result.error, "the room accepted a retired move")
        assertNull(result.state.game?.coalitionLeaderId, "and nothing was set")
    }

    /**
     * The hole this closes, stated as a test: the action names nobody, so the door's seat
     * check never applied to it and the one player forbidden to be in the coalition could
     * choose who led it.
     */
    @Test
    fun theVintoCallerCannotNominateTheCoalitionsLeaderEither() {
        val state = dealtRoom()
        val room = decodeRoom(state)
        val nominee = checkNotNull(room.seats[0].playerId)

        // TOKEN_B is a different seat from the nominee's, which is exactly the case the
        // `actorId` check cannot see: there is no actor to compare.
        val result = decodeAction(
            applyAction(state, TOKEN_B, actionJson(nominate(nominee)), START + 1_000.0),
        )

        assertNotNull(result.error, "a seat nominated another seat's leader")
    }

    @Test
    fun theRefusalCostsTheRoomNothingElse() {
        val state = dealtRoom()
        val before = decodeRoom(state)
        val nominee = checkNotNull(before.seats[1].playerId)

        val result = decodeAction(
            applyAction(state, TOKEN_A, actionJson(nominate(nominee)), START + 1_000.0),
        )

        assertEquals(before.log.size, result.state.log.size, "a refused move is not recorded")
        assertEquals(before.nextIndex, result.state.nextIndex)
    }

    private fun nominate(playerId: String) = GameAction.SetCoalitionLeader(LeaderIdPayload(playerId))
}
