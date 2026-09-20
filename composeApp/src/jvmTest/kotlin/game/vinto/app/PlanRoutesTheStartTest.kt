package game.vinto.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import game.vinto.app.game.routed
import game.vinto.client.Move
import game.vinto.client.Question
import game.vinto.client.TableMode
import game.vinto.client.mode
import game.vinto.client.tableFor
import game.vinto.client.teachingSession
import game.vinto.engine.PlayerView
import game.vinto.shapes.Claim
import game.vinto.shapes.CoalitionPlan
import game.vinto.shapes.GameAction
import game.vinto.shapes.GamePhase
import game.vinto.shapes.Lane
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.Rank
import game.vinto.shapes.Step
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The press the round is waiting on reaches the session from the plan.
 *
 * Reported 2026-09-20 as *"start round button wasn't working"*, and it was not: the button was
 * built, drawn, and measured for fit, and then `routed` dropped it on the floor. Plan mode
 * passes a [Move.Quiet] and nothing else — a tap on a ghost card must never become a move on
 * the real round (design D2) — and `Move.Done` is deliberately not quiet, because it sets three
 * turns going.
 *
 * The lesson is in where this test sits. `PlanModeTest` holds exactly this invariant and went
 * red when the button was added; it was *relaxed* to allow `Move.Done` among the plan's choices,
 * which made the model right and left the router refusing it. Offering a move and routing one
 * are two different claims, and only the first was ever checked.
 */
@OptIn(ExperimentalTestApi::class)
class PlanRoutesTheStartTest {

    @Test
    fun theStartOfTheTurnsSurvivesPlanModesRouter() = runComposeUiTest {
        val reached = mutableListOf<Move>()
        val onThePlan = tableFor(
            finalRound(),
            question = Question.ThePlan(),
            plan = CoalitionPlan(
                lanes = listOf(Lane(seat = "bot-2", step = Step.TakeTheDiscard)),
            ),
        )
        assertEquals(TableMode.PLAN, onThePlan.mode, "the fixture is not the plan")

        setContent {
            val route = { move: Move -> reached += move }.routed(onThePlan)

            // One of each kind the rail and the felt can produce.
            route(Move.Done)
            route(Move.Ask(Question.ThePlan()))
            route(Move.Send(GameAction.DrawCard(PlayerIdPayload("human-1"))))
        }

        assertTrue(
            reached.any { it is Move.Done },
            "the plan dropped the press its own button sends, so the button does nothing",
        )
        assertTrue(reached.any { it is Move.Ask }, "a quiet move stopped passing")
        assertTrue(
            reached.none { it is Move.Send },
            "plan mode let a move on the real round through: $reached",
        )
    }

    private fun finalRound(): PlayerView {
        val whole = teachingSession().view.value
        val caller = whole.players.first { it.id != whole.viewerId }
        return whole.copy(
            phase = GamePhase.FINAL,
            finalTurnTriggered = true,
            vintoCallerId = caller.id,
            conferMsRemaining = 20_000L,
            players = whole.players.map { seat ->
                if (seat.id == caller.id) {
                    seat
                } else {
                    seat.copy(claims = listOf(Claim(seat.id, listOf(0), listOf(Rank.FIVE))))
                }
            },
        )
    }
}
