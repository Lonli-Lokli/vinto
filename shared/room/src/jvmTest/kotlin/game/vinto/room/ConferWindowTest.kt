package game.vinto.room

import game.vinto.engine.GameEngine
import game.vinto.engine.ReduceResult
import game.vinto.shapes.CoalitionPlan
import game.vinto.shapes.GameAction
import game.vinto.shapes.GamePhase
import game.vinto.shapes.GameState
import game.vinto.shapes.GameSubPhase
import game.vinto.shapes.Lane
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PositionPayload
import game.vinto.shapes.Rank
import game.vinto.shapes.Step
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The confer window: the coalition's moment to organise, before the final round's first turn.
 *
 * It occupies the slot the leader vote used to hold, spent on the conversation rather than on
 * a question that settled nothing. A coalition of three has one turn each and a single hand to
 * organise between them, and organising it *after* the first turn has been played is
 * organising it too late.
 *
 * Bounded for the reason the vote's clock was: the final round is the part of the game the
 * caller is entitled to see played out, so a coalition that will not stop talking cannot hold
 * them there. And skipped entirely where there is nobody to confer — a solo game against three
 * bots must not acquire a pause it never had.
 */
class ConferWindowTest {

    @Test
    fun theRoundHoldsWhileTheCoalitionTalks() {
        val state = finalRoundCalledBy(seat = 1)
        val before = checkNotNull(decodeRoom(state).game)

        // An alarm during the window moves nothing: the bots wait for the conversation.
        val ticked = decodeLifecycle(onAlarm(state, START + 1_000.0))

        assertNotNull(ticked.state.conferUntilEpochMs, "no window opened")
        assertEquals(
            before.currentPlayerIndex,
            ticked.state.game?.currentPlayerIndex,
            "a bot played through the coalition's conversation",
        )
    }

    @Test
    fun everyPresentMemberSayingSoClosesItAtOnce() {
        // Ann is the only person in the coalition — Bob called — so her word ends it. Three
        // people who agree in five seconds should not be held for twenty.
        val state = finalRoundCalledBy(seat = 1)

        val done = doneConferring(decodeRoom(state), TOKEN_A)

        assertNull(done.error)
        assertNull(done.state.conferUntilEpochMs, "the window stayed open after everyone was done")
        assertEquals(
            decodeRoom(state).game?.roundNumber,
            done.state.conferredRound,
            "and it must not reopen on the next action",
        )
    }

    @Test
    fun theWindowIsNeverOpenEnded() {
        val state = finalRoundCalledBy(seat = 1)

        // Nobody says anything; the deadline closes it and the round is played.
        val fired = decodeLifecycle(onAlarm(state, START + CONFER_LIMIT))

        assertNull(fired.state.conferUntilEpochMs)
        assertNotNull(fired.state.conferredRound, "the window did not close on its own deadline")
    }

    @Test
    fun anAllBotCoalitionGetsNoPause() {
        // Ann calls, so the coalition is Bob plus two filler bots — and then Bob's socket goes
        // away, leaving nobody to confer. The round must not wait for a conversation that
        // cannot happen.
        val state = finalRoundCalledBy(seat = 0)
        val alone = decodeLifecycle(updatePresence(state, "0", START))

        val ticked = decodeLifecycle(onAlarm(encode(alone.state), START + 1_000.0))

        assertNull(ticked.state.conferUntilEpochMs, "a window opened for nobody")
    }

    @Test
    fun aSeatTheWindowIsNotWaitingOnCannotCloseIt() {
        // Bob called Vinto, so he is not in the coalition and the window is not his to end.
        val state = finalRoundCalledBy(seat = 1)

        assertNotNull(doneConferring(decodeRoom(state), TOKEN_B).error)
        assertNotNull(doneConferring(decodeRoom(state), STRANGER).error)
    }

    @Test
    fun sayingSoOverTheWireReleasesTheSeatsTheWindowWasHolding() {
        // Closing is not itself a move, so the answer has to carry what the bots then played —
        // otherwise a table that stopped talking sits waiting for a clock it just cancelled.
        val state = finalRoundCalledBy(seat = 1)

        val envelopes = decodeEnvelopes(doneConferringEnvelopes(state, TOKEN_A, START + 2_000.0))

        assertNull(envelopes.error)
        assertNull(envelopes.state.conferUntilEpochMs, "the window stayed open")
        assertNotNull(envelopes.state.conferredRound, "and it must not reopen")
        // Everybody hears it, because the window closing is what starts the round for all of
        // them. That the *bots* then move is `TwoClientGameTest`'s business: it plays a whole
        // round through a real window, where the Vinto call happens inside a toss-in the way
        // the rules describe. This fixture calls it outside one, so the engine leaves the turn
        // on the caller and there is nothing for a bot to do yet.
        assertTrue(envelopes.messages.isNotEmpty(), "nobody was told the round had started")
    }

    @Test
    fun droppingAndReturningCannotMintAFreshWindow() {
        // The window has to be *closed* when its people go, not merely un-clocked: leaving the
        // round un-conferred let a reconnect start a fresh twenty seconds, and again on the
        // next one. That is the coalition holding the caller, reached by dropping rather than
        // by talking.
        val state = finalRoundCalledBy(seat = 1)
        assertNotNull(decodeRoom(state).conferUntilEpochMs, "no window to lose")

        val gone = decodeLifecycle(updatePresence(state, "1", START + 19_000.0)).state
        assertNull(gone.conferUntilEpochMs, "the window outlived the last person in it")

        val back = decodeLifecycle(updatePresence(encode(gone), "0,1", START + 21_000.0)).state

        assertNull(back.conferUntilEpochMs, "coming back bought the table another twenty seconds")
        assertNotNull(back.conferredRound, "and the round was left able to open one again")
    }

    @Test
    fun aBotsVintoCallStillOpensTheWindowBeforeAnyCoalitionTurn() {
        // The commonest case there is, and the one a check outside the bot loop missed: a bot
        // calls Vinto from inside that loop, so a check taken before it ran was reading a state
        // where the final round had not started — and the bots then played the whole coalition
        // round in the same request, before anybody was offered a word.
        val dealt = dealtRoom()
        val played = playRoundOut(dealt, seed = 11, from = START)
        val room = decodeRoom(played)

        // `playRoundOut` closes the window as the people would; what matters is that it had to.
        assertNotNull(
            room.conferredRound ?: room.conferUntilEpochMs,
            "the round reached its end without a confer window ever opening",
        )
    }

    // ------------------------------------------------------------------ the shared plan

    @Test
    fun theCoalitionEditsOneDraftAndTheLastEditStands() {
        // Three competing plans is not a coalition deciding together.
        val state = decodeRoom(finalRoundCalledBy(seat = 1))
        val ann = checkNotNull(state.seats[0].playerId)

        val first = editPlan(state, TOKEN_A, CoalitionPlan(lanes = listOf(Lane(ann))))
        assertNull(first.error)

        val second = editPlan(
            first.state,
            TOKEN_A,
            CoalitionPlan(lanes = listOf(Lane(ann, Step.Declare(Rank.KING)))),
        )

        assertNull(second.error)
        assertEquals(Step.Declare(Rank.KING), second.state.plan?.lanes?.single()?.step)
    }

    @Test
    fun theCallerHasNoCoalitionToPlanWith() {
        val state = decodeRoom(finalRoundCalledBy(seat = 1))
        assertNotNull(editPlan(state, TOKEN_B, CoalitionPlan()).error)
    }

    @Test
    fun aLaneLocksWhenItsOwnersTurnBegins() {
        // A plan must not change under the hand of the person executing it. Later lanes stay
        // open, because the round is still going and better information keeps arriving.
        val room = decodeRoom(finalRoundCalledBy(seat = 1))
        val game = checkNotNull(room.game)
        val onPlay = checkNotNull(game.players.getOrNull(game.currentPlayerIndex)).id
        val other = game.players.first { it.id != onPlay && it.id != game.vintoCallerId }.id

        val planned = editPlan(
            room,
            TOKEN_A,
            CoalitionPlan(lanes = listOf(Lane(onPlay), Lane(other))),
        )
        assertNull(planned.error)

        // Pacing is what notices whose turn it is, so an alarm is enough to lock it.
        val after = decodeLifecycle(onAlarm(encode(planned.state), START + 2_000.0)).state
        val lanes = checkNotNull(after.plan).lanes

        assertTrue(lanes.first { it.seat == onPlay }.locked, "the turn in progress was left editable")
        assertFalse(lanes.first { it.seat == other }.locked, "a later turn was frozen too early")
    }

    @Test
    fun thePlanDiesWithTheRoundItWasMadeFor() {
        // Driven through the action path, because that is where a round is scored — an alarm
        // does not settle one. A plan is what a coalition intended for *that* hand; carrying
        // it over would open the next round on somebody's stale agreement.
        val room = decodeRoom(finalRoundCalledBy(seat = 1))
        val ann = checkNotNull(room.seats[0].playerId)
        val planned = editPlan(room, TOKEN_A, CoalitionPlan(lanes = listOf(Lane(ann)))).state
        assertNotNull(planned.plan, "the fixture never got a plan to lose")

        val played = decodeRoom(playRoundOut(encode(planned), seed = 3, from = START + 3_000.0))

        assertEquals(GamePhase.SCORING, played.game?.phase, "the round never finished")
        assertNull(played.plan, "last round's agreement was carried into the next")
    }

    /** The dealt room, moved into a final round called by [seat]. */
    private fun finalRoundCalledBy(seat: Int): String {
        val room = decodeRoom(dealtRoom())
        val dealt = checkNotNull(room.game)
        val caller = checkNotNull(room.seats[seat].playerId)

        var peeked = dealt
        for (player in dealt.players) {
            repeat(SETUP_PEEKS - player.knownCardPositions.size) { seen ->
                peeked = reduce(peeked, GameAction.PeekSetupCard(PositionPayload(player.id, seen)))
            }
        }
        val dealtOut = reduce(peeked, GameAction.FinishSetup(PlayerIdPayload(peeked.players.first().id)))
        val onPlay = dealtOut.copy(
            currentPlayerIndex = dealtOut.players.indexOfFirst { it.id == caller },
            subPhase = GameSubPhase.IDLE,
        )
        // The call itself goes **through the room**, because that is the path that opens the
        // window: `applyAction` recomputes pacing, and a fixture that reduced its way into the
        // final round would be testing a state no real game reaches.
        val beforeTheCall = encode(room.copy(game = onPlay))
        val token = if (seat == 0) TOKEN_A else TOKEN_B
        val called = decodeAction(
            applyAction(beforeTheCall, token, actionJson(GameAction.CallVinto(PlayerIdPayload(caller))), START),
        )
        check(called.error == null) { "the fixture could not call Vinto: ${called.error}" }
        check(called.state.game?.phase == GamePhase.FINAL)
        return encode(called.state)
    }

    private fun reduce(state: GameState, action: GameAction): GameState =
        when (val result = GameEngine.reduce(state, action)) {
            is ReduceResult.Success -> result.state
            is ReduceResult.Failure -> error("fixture move refused: ${result.reason}")
        }

    private companion object {
        const val SETUP_PEEKS = 2

        /** Comfortably past `CONFER_MS`, which is private to `RoomCore`. */
        const val CONFER_LIMIT = 30_000.0
    }
}
