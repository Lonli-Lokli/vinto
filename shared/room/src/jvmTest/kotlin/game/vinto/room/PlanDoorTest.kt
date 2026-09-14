package game.vinto.room

import game.vinto.protocol.ProtocolJson
import game.vinto.protocol.ServerMessage
import game.vinto.shapes.CardAt
import game.vinto.shapes.GamePhase
import game.vinto.shapes.PlanEdit
import game.vinto.shapes.Rank
import game.vinto.shapes.Step
import game.vinto.shapes.TableTalk
import game.vinto.shapes.coalitionInTurnOrder
import game.vinto.shapes.laneOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The door the shared plan comes through — a **board of parts agreed as a whole** (design D7a).
 *
 * The merge and the refusals are `CoalitionPlan.edited` in `shared/shapes`, tested there; what
 * this holds is what a *room* adds around it: a token resolving to a seat, the caller refused,
 * the budget spent, the bots answering for their own lanes, agreement doubling as done
 * conferring, and the board riding on every message so a reconnect lands on it.
 */
class PlanDoorTest {

    @Test
    fun anybodyInTheCoalitionMayEditOnePart() {
        val state = finalRoundCalledByABot()
        val mine = seatId(state, 0)

        val edited = editPlan(decodeRoom(state), TOKEN_A, PlanEdit.SetLane(mine, take()), START)

        assertNull(edited.error)
        val plan = assertNotNull(edited.state.plan, "the plan did not land")
        assertEquals(take(), plan.laneOf(mine)?.step)
        assertEquals(listOf(mine), plan.agreed.filter { it == mine }, "making the edit is agreeing to it")
        assertEquals(mine, plan.editedBy)
    }

    @Test
    fun twoMembersOnDifferentLanesBothLand() {
        // The whole point of a part rather than a draft: Ann and Bob each send their lane and
        // neither send carries the other's, so nothing can be overwritten by crossing.
        val state = finalRoundCalledByABot()
        val ann = seatId(state, 0)
        val bob = seatId(state, 1)

        val first = editPlan(decodeRoom(state), TOKEN_A, PlanEdit.SetLane(ann, take()), START)
        val second = editPlan(first.state, TOKEN_B, PlanEdit.SetLane(bob, Step.Declare(Rank.KING)), START)

        assertNull(second.error)
        val plan = assertNotNull(second.state.plan)
        assertEquals(take(), plan.laneOf(ann)?.step, "Ann's lane was lost to Bob's edit")
        assertEquals(Step.Declare(Rank.KING), plan.laneOf(bob)?.step)
    }

    @Test
    fun theCallerHasNoLaneInTheCoalitionsPlan() {
        // Not a check at the point of use: the caller's turn is over and their cards are
        // untouchable, so a lane naming them is a plan about the one hand the coalition is
        // playing against.
        val state = finalRoundCalledByABot()
        val caller = checkNotNull(decodeRoom(state).game?.vintoCallerId)

        val edited = editPlan(decodeRoom(state), TOKEN_A, PlanEdit.SetLane(caller, take()), START)

        assertNotNull(edited.error, "the coalition planned the caller's turn")
        assertNull(edited.state.plan, "and nothing was written")
    }

    @Test
    fun aStepMayNotTouchTheCallersCards() {
        val state = finalRoundCalledByABot()
        val room = decodeRoom(state)
        val mine = seatId(state, 0)
        val caller = checkNotNull(room.game?.vintoCallerId)

        val edited = editPlan(
            room,
            TOKEN_A,
            PlanEdit.SetLane(mine, Step.Swap(CardAt(mine, 0), CardAt(caller, 0))),
            START,
        )

        assertNotNull(edited.error, "a step reached for the caller's card")
    }

    @Test
    fun aLaneNamesASeatThatIsActuallyPlayingOne() {
        val state = finalRoundCalledByABot()

        val edited = editPlan(decodeRoom(state), TOKEN_A, PlanEdit.SetLane("nobody-at-this-table", take()), START)

        assertNotNull(edited.error, "a lane was accepted for a seat that does not exist")
    }

    @Test
    fun aPlayedTurnCannotBeSetOrCleared() {
        // A turn that has been played is history. An edit *names* its lane, so "not a target"
        // is the whole check — where a whole-draft door had to notice the locked lane being
        // left out.
        val state = finalRoundCalledByABot()
        val room = decodeRoom(state)
        val game = checkNotNull(room.game)
        val coalition = coalitionInTurnOrder(game.players.map { it.id }, checkNotNull(game.vintoCallerId))
        val (first, second, third) = coalition

        // Planned in the window, with the caller still on play; then the first turn is played
        // and the second begins, and pacing — an alarm here — is what notices and locks it.
        val planned = editPlan(room, TOKEN_A, PlanEdit.SetLane(first, take()), START).state
        val onSecond = planned.copy(
            game = game.copy(currentPlayerIndex = game.players.indexOfFirst { it.id == second }),
        )
        val locked = decodeLifecycle(onAlarm(encode(onSecond), START + 2_000.0)).state
        assertTrue(locked.plan?.laneOf(first)?.locked == true, "the fixture never locked the lane")

        assertNotNull(editPlan(locked, TOKEN_A, PlanEdit.SetLane(first, Step.Declare(Rank.KING)), START).error)
        assertNotNull(editPlan(locked, TOKEN_A, PlanEdit.ClearLane(first), START).error)
        assertNull(editPlan(locked, TOKEN_A, PlanEdit.SetLane(second, take()), START).error, "the turn on play froze")
        assertNull(editPlan(locked, TOKEN_A, PlanEdit.SetLane(third, take()), START).error, "a later lane froze too")
    }

    @Test
    fun theTurnInProgressStaysOpenAndThePlayedOneIsRefusedBeforeAnyStamp() {
        // The seat on play may be planned for — its drawn card is the news the plan turns on —
        // and the seat before it in turn order is refused from the order alone, before pacing
        // has stamped anything.
        val state = finalRoundCalledByABot()
        val game = checkNotNull(decodeRoom(state).game)
        val coalition = coalitionInTurnOrder(game.players.map { it.id }, checkNotNull(game.vintoCallerId))
        val onSecond = finalRoundCalledByABot(onPlay = game.players.indexOfFirst { it.id == coalition[1] })

        assertNull(editPlan(decodeRoom(onSecond), TOKEN_A, PlanEdit.SetLane(coalition[1], take()), START).error)
        assertNotNull(editPlan(decodeRoom(onSecond), TOKEN_A, PlanEdit.SetLane(coalition[0], take()), START).error)
    }

    @Test
    fun anEditResetsAgreementToTheEditor() {
        val state = finalRoundCalledByABot()
        val ann = seatId(state, 0)
        val bob = seatId(state, 1)

        val set = editPlan(decodeRoom(state), TOKEN_A, PlanEdit.SetLane(ann, take()), START).state
        val agreed = agreePlan(set, TOKEN_B, agree = true).state
        assertTrue(bob in checkNotNull(agreed.plan).agreed, "Bob's yes was not recorded")

        val changed = editPlan(agreed, TOKEN_A, PlanEdit.SetLane(ann, Step.Declare(Rank.KING)), START).state
        assertFalse(bob in checkNotNull(changed.plan).agreed, "a yes to a plan that no longer exists is not a yes")
        assertTrue(ann in checkNotNull(changed.plan).agreed)
    }

    @Test
    fun agreeingIsHowYouFinishTalking() {
        // Two people in the coalition, the window open. The first yes marks that seat done;
        // the second closes the window, exactly as two `done-conferring`s would.
        val state = finalRoundCalledByABot()
        val room = decodeRoom(state)
        assertTrue(conferring(room), "the fixture's window is not open")
        val ann = seatId(state, 0)

        val set = editPlan(room, TOKEN_A, PlanEdit.SetLane(ann, take()), START).state
        val one = agreePlan(set, TOKEN_A, agree = true)
        assertNull(one.error)
        assertTrue(conferring(one.state), "one yes closed a window two people were in")
        assertEquals(listOf(0), one.state.conferReady)

        val both = agreePlan(one.state, TOKEN_B, agree = true)
        assertNull(both.error)
        assertFalse(conferring(both.state), "the last yes did not close the window")
    }

    @Test
    fun aNoIsOnlyANo() {
        val state = finalRoundCalledByABot()
        val ann = seatId(state, 0)

        val set = editPlan(decodeRoom(state), TOKEN_A, PlanEdit.SetLane(ann, take()), START).state
        val no = agreePlan(set, TOKEN_B, agree = false)

        assertNull(no.error)
        assertTrue(no.state.conferReady.isEmpty(), "a no was taken as done talking")
        assertTrue(conferring(no.state))
    }

    @Test
    fun anEmptyBoardIsNothingToAgreeTo() {
        val state = finalRoundCalledByABot()
        assertNotNull(agreePlan(decodeRoom(state), TOKEN_A, agree = true).error)
    }

    @Test
    fun anUnagreedPlanStandsAsASuggestionWhenTheWindowCloses() {
        // Propose, never command, applies to a plan too: the deadline ends the talking, not
        // the board. The person on play sees what was suggested and who agreed, and decides.
        val state = finalRoundCalledByABot()
        val ann = seatId(state, 0)

        val set = editPlan(decodeRoom(state), TOKEN_A, PlanEdit.SetLane(ann, take()), START).state
        // The first alarm arms the window's deadline; the second is past it.
        val armed = decodeLifecycle(onAlarm(encode(set), START)).state
        assertTrue(conferring(armed), "the fixture's window closed on being armed")
        val expired = decodeLifecycle(onAlarm(encode(armed), START + 60_000.0)).state

        assertFalse(conferring(expired), "the deadline did not close the window")
        val plan = assertNotNull(expired.plan, "the deadline voided the plan")
        assertEquals(take(), plan.laneOf(ann)?.step)
        assertEquals(listOf(ann), plan.agreed.filter { it == ann })
    }

    @Test
    fun aBotAnswersForItsOwnLaneAndForNobodyElses() {
        val state = finalRoundCalledByABot()
        val room = decodeRoom(state)
        val ann = seatId(state, 0)
        val caller = checkNotNull(room.game?.vintoCallerId)
        val bot = checkNotNull(room.seats.first { it.isBot && it.playerId != caller }.playerId)

        // Setting the bot's lane gets an answer, addressed to whoever set it, and its yes or no
        // is what its agreement follows.
        val asked = editPlan(room, TOKEN_A, PlanEdit.SetLane(bot, take()), START)
        val answer = assertIs<TableTalk.Answer>(asked.talk, "the bot said nothing about its own lane")
        assertEquals(bot, answer.by)
        assertEquals(ann, answer.to)
        assertEquals(
            answer.says == TableTalk.Answer.Says.YES,
            bot in checkNotNull(asked.state.plan).agreed,
            "the bot's agreement does not match what it said",
        )

        // Setting somebody else's lane: the bot re-answers in silence.
        val elsewhere = editPlan(asked.state, TOKEN_A, PlanEdit.SetLane(ann, take()), START)
        assertNull(elsewhere.talk, "a bot spoke about a lane that is not its own")
    }

    @Test
    fun thePlanRidesOnEveryEventsAndSync() {
        // A lane locks on an ordinary action and a reconnect lands on the present, so the board
        // goes wherever the view goes rather than in a message of its own.
        val state = finalRoundCalledByABot()
        val ann = seatId(state, 0)
        val edit = ProtocolJson.encodeToString(PlanEdit.serializer(), PlanEdit.SetLane(ann, take()))

        val envelopes = decodeEnvelopes(editPlanEnvelopes(state, TOKEN_A, edit, START))
        assertNull(envelopes.error)
        assertEquals(
            decodeRoom(state).seats.count { it.playerId != null },
            envelopes.messages.size,
            "not every seat was told",
        )
        envelopes.messages.values.forEach { text ->
            val message = assertIs<ServerMessage.Events>(
                ProtocolJson.decodeFromString(ServerMessage.serializer(), text),
            )
            assertTrue(message.events.isEmpty(), "an edit moved a card")
            assertEquals(take(), message.plan?.laneOf(ann)?.step, "the board did not ride on the events")
        }

        val sync = ProtocolJson.decodeFromString(
            ServerMessage.serializer(),
            syncEnvelope(encode(envelopes.state), seat = 1, sinceIndex = 0, nowMs = START),
        )
        assertEquals(
            take(),
            assertIs<ServerMessage.Sync>(sync).plan?.laneOf(ann)?.step,
            "a reconnect would lose the plan",
        )
    }

    @Test
    fun aPlanEditSpendsTheTalkBudget() {
        // Broadcast to every socket, so charged like a sentence rather than free.
        val state = finalRoundCalledByABot()
        val ann = seatId(state, 0)

        val edited = editPlan(decodeRoom(state), TOKEN_A, PlanEdit.SetLane(ann, take()), START)

        assertNotNull(edited.state.buckets[0], "the edit cost nothing")
    }

    @Test
    fun theBotsSeedTheBoardForThePeopleAndStopOnceAPersonHasEdited() {
        // Two people in the coalition and two bots: the bots' proposals are on the board the
        // first time the room drives them, and a lane a person clears stays clear.
        // The one coalition bot is given a Jack and a King it knows about, so there is a trade
        // the rules let it propose whatever the deal dealt: the Jack put down and called, and
        // the King — worth nothing — traded into a hand of unread cards, which the shared picture
        // prices at the deck's mean each.
        val room = decodeRoom(finalRoundCalledByABot())
        val game = checkNotNull(room.game)
        val bot = game.players.first { it.isBot && it.id != game.vintoCallerId }
        val loaded = room.copy(
            game = game.copy(
                players = game.players.map { player ->
                    if (player.id != bot.id) {
                        player
                    } else {
                        player.copy(
                            cards = player.cards.mapIndexed { i, card ->
                                when (i) {
                                    0 -> card.copy(rank = Rank.JACK, value = 10, actionText = "swap")
                                    1 -> card.copy(rank = Rank.KING, value = 0, actionText = "declare")
                                    // And a ten it would like rid of, so a trade helps whichever hand is lowest.
                                    2 -> card.copy(rank = Rank.TEN, value = 10, actionText = "peek")
                                    else -> card
                                }
                            },
                            knownCardPositions = (player.knownCardPositions + 0 + 1 + 2).distinct(),
                        )
                    }
                },
            ),
        )
        val driven = playBotsTracked(loaded).state
        val plan = assertNotNull(driven.plan, "the bots proposed nothing for a coalition with people in it")
        assertTrue(plan.lanes.any { it.step != null }, "an empty board")

        // Ann clears the proposal — whoever's lane it landed on — and it stays clear.
        val lane = plan.lanes.first { it.step != null }.seat
        val cleared = editPlan(driven, TOKEN_A, PlanEdit.ClearLane(lane), START).state
        val again = playBotsTracked(cleared).state
        assertNull(again.plan?.laneOf(lane), "the bots refilled a lane a person had cleared")
    }

    private fun take(): Step = Step.TakeTheDiscard

    private fun seatId(state: String, seat: Int): String = checkNotNull(decodeRoom(state).seats[seat].playerId)

    /**
     * A dealt room in a final round a *bot* called, so every human seat is in the coalition.
     *
     * Straight out of a call the caller is still on play — the window opens before the turn
     * moves — so that is the default. [onPlay] names a coalition seat for the tests that are
     * about a turn in progress, rather than leaving it to the shuffle.
     */
    private fun finalRoundCalledByABot(onPlay: Int? = null): String {
        val room = decodeRoom(dealtRoom())
        val game = checkNotNull(room.game)
        val caller = checkNotNull(room.seats.last { it.tokenHash == null }.playerId)
        return encode(
            room.copy(
                game = game.copy(
                    phase = GamePhase.FINAL,
                    finalTurnTriggered = true,
                    vintoCallerId = caller,
                    currentPlayerIndex = onPlay ?: game.players.indexOfFirst { it.id == caller },
                    players = game.players.map { it.copy(isVintoCaller = it.id == caller) },
                ),
            ),
        )
    }
}
