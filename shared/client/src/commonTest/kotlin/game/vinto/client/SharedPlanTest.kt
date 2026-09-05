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
            seat("bot-4", isHuman = false, callerId, ranks = listOf(Rank.SIX)),
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
        assertNull(session.plan.value, "nothing has been planned yet")

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

    @Test
    fun agreeingIsHowThePersonFinishesTalking() = runTest {
        val session = inTheWindow()
        session.dispatch(GameAction.Empty(JsonNull))
        // The caller cannot be planned for, sheds included; and an empty board is nothing to agree to.
        assertNotNull(session.editPlan(PlanEdit.AddShed(Shed("bot-2", Rank.SEVEN))), "the caller got a shed")
        assertNotNull(session.agreePlan(agree = true), "an empty board was agreed to")

        assertNull(session.editPlan(PlanEdit.SetLane("bot-4", Step.TakeTheDiscard)))
        assertNotNull(session.view.value.conferMsRemaining, "the window closed on an edit")

        assertNull(session.agreePlan(agree = true))
        assertNull(session.view.value.conferMsRemaining, "agreeing did not end the window")
        assertTrue(session.plan.value?.agreed.orEmpty().contains("human-1"))
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
        assertNull(session.plan.value, "a scored round still had a plan")
    }
}
