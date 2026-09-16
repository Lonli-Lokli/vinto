package game.vinto.client

import game.vinto.shapes.Card
import game.vinto.shapes.Difficulty
import game.vinto.shapes.GameAction
import game.vinto.shapes.GamePhase
import game.vinto.shapes.GameState
import game.vinto.shapes.GameSubPhase
import game.vinto.shapes.Pile
import game.vinto.shapes.PlanEdit
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PlayerState
import game.vinto.shapes.Rank
import game.vinto.shapes.Shed
import game.vinto.shapes.Step
import game.vinto.shapes.TableTalk
import game.vinto.shapes.getCardShortDescription
import game.vinto.shapes.getCardValue
import game.vinto.shapes.laneOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The shared plan in a solo game (design D7a).
 *
 * A solo game holds a plan under the same rules as a room — `CoalitionPlan.edited` is the one
 * door — so the composer a person learns against three bots is the composer they meet online.
 * The bots answer for their own lanes in-process, agreeing is how the person finishes talking,
 * and the caller is refused here exactly as at the room's door.
 */
class SharedPlanTest {

    private fun card(rank: Rank, id: String) = Card(
        id = id,
        rank = rank,
        value = getCardValue(rank),
        played = false,
        actionText = getCardShortDescription(rank).takeIf { it.isNotEmpty() },
    )

    private fun seat(id: String, isHuman: Boolean, callerId: String, ranks: List<Rank>) = PlayerState(
        id = id,
        name = id,
        nickname = id,
        isHuman = isHuman,
        isBot = !isHuman,
        cards = ranks.mapIndexed { index, rank -> card(rank, "$id-c$index") },
        knownCardPositions = if (isHuman) emptyList() else ranks.indices.toList(),
        isVintoCaller = id == callerId,
        coalitionWith = if (id == callerId) emptyList() else listOf("human-1", "bot-2", "bot-3", "bot-4") - id,
    )

    /** A final round [callerId] has just called: the caller is still on play, the window opens. */
    private fun justCalled(callerId: String) = GameState(
        gameId = "shared-plan",
        roundNumber = 1,
        turnNumber = 12,
        phase = GamePhase.FINAL,
        subPhase = GameSubPhase.IDLE,
        finalTurnTriggered = true,
        players = listOf(
            seat("human-1", isHuman = true, callerId, ranks = listOf(Rank.NINE)),
            seat("bot-2", isHuman = false, callerId, ranks = listOf(Rank.KING, Rank.TWO)),
            seat("bot-3", isHuman = false, callerId, ranks = listOf(Rank.FIVE)),
            // A Jack to put down and call, and a King beside it worth trading into the low hand:
            // the one shape of trade a bot may propose, since nothing is aimed on a blind draw.
            seat("bot-4", isHuman = false, callerId, ranks = listOf(Rank.JACK, Rank.KING, Rank.SIX)),
        ),
        currentPlayerIndex = listOf("human-1", "bot-2", "bot-3", "bot-4").indexOf(callerId),
        vintoCallerId = callerId,
        coalitionLeaderId = null,
        drawPile = Pile((0..6).map { card(Rank.FOUR, "draw-$it") }),
        discardPile = Pile(listOf(card(Rank.THREE, "discard-seed"))),
        pendingAction = null,
        activeTossIn = null,
        turnActions = emptyList(),
        roundActions = emptyList(),
        roundFailedAttempts = emptyList(),
        difficulty = Difficulty.MODERATE,
        rngState = 0,
    )

    private fun inTheWindow(callerId: String = "bot-2") = LocalGameSession(
        seed = 5L,
        difficulty = Difficulty.MODERATE,
        resuming = justCalled(callerId),
    )

    @Test
    fun aPersonSetsABotsLaneAndTheBotAnswersForIt() = runTest {
        val session = inTheWindow()
        session.dispatch(GameAction.Empty(JsonNull))
        assertNotNull(session.view.value.conferMsRemaining, "the fixture's window is not open")
        // The bots have already put their proposals on the board; the person replaces one.
        assertNotNull(session.plan.value, "the bots proposed nothing in the window")

        assertNull(session.editPlan(PlanEdit.SetLane("bot-4", Step.TakeTheDiscard)))

        val plan = assertNotNull(session.plan.value, "the plan did not land")
        assertEquals(Step.TakeTheDiscard, plan.laneOf("bot-4")?.step)
        assertTrue("human-1" in plan.agreed, "making the edit is agreeing to it")
        assertEquals("human-1", plan.editedBy)

        val answer = session.talk.replayCache.filterIsInstance<TableTalk.Answer>().lastOrNull()
        assertNotNull(answer, "the bot said nothing about its own lane")
        assertEquals("bot-4", answer.by)
        assertEquals("human-1", answer.to, "the answer was not addressed to whoever asked")
        assertEquals(answer.says == TableTalk.Answer.Says.YES, "bot-4" in plan.agreed)
    }

    @Test
    fun theCallerIsRefusedAtTheSameDoorAsOnline() = runTest {
        val member = inTheWindow()
        member.dispatch(GameAction.Empty(JsonNull))
        assertNotNull(member.editPlan(PlanEdit.SetLane("bot-2", Step.TakeTheDiscard)), "the caller got a lane")

        val caller = inTheWindow(callerId = "human-1")
        assertNotNull(caller.editPlan(PlanEdit.SetLane("bot-3", Step.TakeTheDiscard)), "the caller was let plan")
        assertNotNull(caller.agreePlan(agree = true))
    }

    /**
     * Agreeing is an opinion about the plan, and nothing else.
     *
     * It used to finish the talking too, so the last member to agree started the round. That put
     * the press meaning "I like this plan" in charge of three final turns — and a person who
     * pressed it to say so watched the round go. The window is ended by the button that says it
     * ends it now (`Label.StartTheTurns`), and this one only ever records a yes.
     */
    @Test
    fun agreeingSaysYesAndStartsNothing() = runTest {
        val session = inTheWindow()
        // Before the window opens nothing is on the board, and nothing is nothing to agree to.
        assertNotNull(session.agreePlan(agree = true), "an empty board was agreed to")
        session.dispatch(GameAction.Empty(JsonNull))
        // The caller cannot be planned for, sheds included.
        assertNotNull(session.editPlan(PlanEdit.AddShed(Shed("bot-2", Rank.SEVEN))), "the caller got a shed")

        assertNull(session.editPlan(PlanEdit.SetLane("bot-4", Step.TakeTheDiscard)))
        assertNotNull(session.view.value.conferMsRemaining, "the window closed on an edit")

        assertNull(session.agreePlan(agree = true))
        assertTrue(session.plan.value?.agreed.orEmpty().contains("human-1"), "the yes was not recorded")
        assertNotNull(
            session.view.value.conferMsRemaining,
            "agreeing started the round, which is the press it was split away from",
        )

        // And the button that does end it still does.
        assertNull(session.doneConferring())
        assertNull(session.view.value.conferMsRemaining, "the window would not close")
    }

    @Test
    fun theBotsProposeOnTheBoardAndLeaveThePersonsEditsAlone() = runTest {
        val session = inTheWindow()
        session.dispatch(GameAction.Empty(JsonNull))

        val seeded = assertNotNull(session.plan.value, "the bots proposed nothing in the window")
        assertTrue(seeded.lanes.any { it.step != null }, "an empty board")
        val botLane = seeded.lanes.first { it.step != null && it.seat != session.playerId }.seat

        assertNull(session.editPlan(PlanEdit.ClearLane(botLane)))
        session.dispatch(GameAction.Empty(JsonNull))
        assertNull(session.plan.value?.laneOf(botLane), "the bots refilled a lane the person cleared")
    }

    @Test
    fun thePlanDiesWithTheRound() = runTest {
        val session = inTheWindow()
        session.dispatch(GameAction.Empty(JsonNull))
        assertNull(session.editPlan(PlanEdit.SetLane("bot-4", Step.TakeTheDiscard)))
        session.doneConferring()

        // The human's one turn, then the bots finish the round.
        var guard = 0
        while (!session.isOver && guard++ < 60) {
            val v = session.view.value
            val action = when {
                v.activeTossIn != null && session.playerId !in v.activeTossIn!!.playersReadyForNextTurn ->
                    GameAction.PlayerTossInFinished(PlayerIdPayload(session.playerId))

                v.players.getOrNull(v.currentPlayerIndex)?.id == session.playerId &&
                    v.pendingAction == null && v.subPhase == GameSubPhase.IDLE ->
                    GameAction.DrawCard(PlayerIdPayload(session.playerId))

                v.pendingAction?.playerId == session.playerId && v.subPhase == GameSubPhase.CHOOSING ->
                    GameAction.DiscardCard(PlayerIdPayload(session.playerId))

                else -> break
            }
            session.dispatch(action)
        }

        assertTrue(session.isOver, "the round never scored")

        // **Asserted on the screen rather than on the field**, because the field deliberately
        // outlives the round now. `settlePlan` used to null it here, and that threw the Vinto
        // caller's board away between two statements of the dispatch that played their whole
        // final round — before a frame of it had been drawn, and `plan` is a conflating
        // `StateFlow`, so the screen never saw it at all. What must not outlive the round is
        // anything a player can be shown, and a later round cannot inherit a stale agreement
        // either: it is a new session with a plan of its own (`LocalGame.deal`).
        val scored = tableFor(session.view.value, plan = session.plan.value)
        assertNull(scored.planSummary, "a scored round still offered a plan to open")
        assertNull(scored.board, "a scored round still drew a board")
        assertNull(scored.planLine, "a scored round still drew the plan's row")
    }
}
