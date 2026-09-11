package game.vinto.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import game.vinto.app.game.LocalStage
import game.vinto.app.game.Stage
import game.vinto.app.game.TableLayout
import game.vinto.app.game.TableScreen
import game.vinto.app.game.TableState
import game.vinto.app.theme.VintoTheme
import game.vinto.client.Move
import game.vinto.client.Question
import game.vinto.client.tableFor
import game.vinto.client.teachingSession
import game.vinto.engine.PlayerView
import game.vinto.shapes.CardAt
import game.vinto.shapes.CoalitionPlan
import game.vinto.shapes.GamePhase
import game.vinto.shapes.Lane
import game.vinto.shapes.Step
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The final round's header: what changes, and the way into the plan.
 *
 * It used to carry four rows and two of them were decoration — a "Together … vs … Called it"
 * roster repeating what the seat plates already show, and a one-time sentence explaining the
 * round. Both said the same thing on the fortieth second as on the first, above a felt with
 * four hands to fit on a phone. What is left is the countdown, which moves, and the plan, which
 * is the only thing anybody does in this round.
 *
 * The plan is a **control** and not a status line that happens to be tappable. That distinction
 * is the whole of the report this change answers: *"it's not clear what I should do to start
 * planning"*.
 */
@OptIn(ExperimentalTestApi::class)
class CoalitionLineTest {

    @Test
    fun theHeaderCarriesTheCountdownAndTheWayIntoThePlan() = runComposeUiTest {
        val words = textsOn(finalRound())

        assertTrue(words.any { it == "FINAL ROUND" }, "the round is not named: $words")
        assertTrue(
            words.any { it.contains("turn", ignoreCase = true) },
            "the countdown is gone, and it is the one thing up here that moves: $words",
        )
        assertTrue(words.any { it.equals("Plan", ignoreCase = true) }, "no way into the plan: $words")
    }

    @Test
    fun theRosterAndTheExplanationAreGone() = runComposeUiTest {
        // Both repeated what the table already says — the caller wears a crown on their plate —
        // and both cost a line above a felt that has none to spare.
        val words = textsOn(finalRound())

        assertFalse(words.any { it.equals("Together", ignoreCase = true) }, "the roster is back: $words")
        assertFalse(words.any { it.equals("vs", ignoreCase = true) }, "the roster is back: $words")
        assertFalse(words.any { it.equals("Called it", ignoreCase = true) }, "the roster is back: $words")
        assertFalse(
            words.any { it.contains("One hand between the three of you") },
            "the one-time explanation is back: $words",
        )
    }

    @Test
    fun thePlanControlIsThereWithNothingPlannedAtAll() = runComposeUiTest {
        // The empty plan is the one a member most needs to open, and "No plan yet" as a
        // sentence reads as a fact about the game rather than as a door.
        val empty = textsOn(finalRound(), plan = null)
        val full = textsOn(finalRound(), plan = wholePlan())

        assertTrue(empty.any { it.equals("Plan", ignoreCase = true) }, "no control with an empty plan: $empty")
        assertTrue(full.any { it.equals("Plan", ignoreCase = true) }, "no control with a full plan: $full")
    }

    @Test
    fun thePlanControlIsAsBigAsAFingerNeeds() = runComposeUiTest {
        show(finalRound())
        val node = onAllNodesWithText("Plan", ignoreCase = true).onFirst().fetchSemanticsNode()
        val height = node.size.height / node.layoutInfo.density.density
        assertTrue(height >= MIN_TARGET, "the way into the plan is ${height}dp tall")
    }

    @Test
    fun theCallerGetsTheCountdownAndNoPlanToOpenUntilThereIsOne() = runComposeUiTest {
        // The caller may *read* a standing plan (design D11) and there is nothing to read until
        // the coalition has put something on it. What they always get is the countdown.
        val whole = teachingSession().view.value
        val theirs = finalRound().copy(viewerId = whole.viewerId).let { view ->
            view.copy(vintoCallerId = view.viewerId)
        }

        val words = textsOn(theirs, plan = null)

        assertTrue(words.any { it == "FINAL ROUND" }, "the caller is not told the round: $words")
        assertFalse(
            words.any { it.equals("Plan", ignoreCase = true) },
            "the caller was offered a plan that does not exist: $words",
        )
    }

    @Test
    fun theSwitchIsTheWayInAndTheWayOut() = runComposeUiTest {
        // One control, both directions. There is deliberately no "Back" among the plan's
        // buttons: a second way to close it is a second thing to learn, and it was the fifth
        // button on a phone's rail — which is how "DECLARE A RANK" came out as "DECL / ARE A".
        val view = finalRound()
        val plan = wholePlan()
        val sent = mutableListOf<Move>()

        val open = tableFor(view, question = Question.ThePlan(), plan = plan)
        assertTrue(open.board != null, "opening the plan did not open it")
        assertTrue(
            open.choices.none { (it.move as? Move.Ask)?.question == Question.None },
            "the plan grew a second way out beside the switch: ${open.choices.map { it.label }}",
        )

        // The switch itself, pressed while the plan is open, asks for the live table back.
        show(view, plan = plan, question = Question.ThePlan(), onMove = { sent += it })
        onAllNodesWithText("Plan", ignoreCase = true).onFirst().performClick()
        waitForIdle()
        assertTrue(
            sent.any { (it as? Move.Ask)?.question == Question.None },
            "the switch did not close the plan: $sent",
        )

        val closed = tableFor(view, question = Question.None, plan = plan)
        assertEquals(null, closed.board, "closing the plan left the felt showing ghosts")
    }

    /** A final round somebody else called, on this seat's own turn. */
    private fun finalRound(): PlayerView {
        val whole = teachingSession().view.value
        val caller = whole.players.first { it.id != whole.viewerId }
        return whole.copy(phase = GamePhase.FINAL, finalTurnTriggered = true, vintoCallerId = caller.id)
    }

    private fun wholePlan(): CoalitionPlan {
        val view = finalRound()
        val mate = view.players.first { it.id != view.viewerId && it.id != view.vintoCallerId }
        return CoalitionPlan(
            lanes = listOf(Lane(mate.id, Step.Swap(CardAt(mate.id, 0), CardAt(view.viewerId, 0)))),
        )
    }

    private fun ComposeUiTest.textsOn(view: PlayerView, plan: CoalitionPlan? = null): List<String> {
        show(view, plan)
        return onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text))
            .fetchSemanticsNodes()
            .mapNotNull { it.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text }
    }

    private fun ComposeUiTest.show(
        view: PlayerView,
        plan: CoalitionPlan? = null,
        question: Question = Question.None,
        onMove: (Move) -> Unit = {},
    ) {
        setContent {
            VintoTheme {
                Box(modifier = Modifier.size(PHONE_W, PHONE_H)) {
                    CompositionLocalProvider(LocalStage provides Stage()) {
                        TableScreen(
                            state = TableState(
                                view,
                                tableFor(view, question = question, plan = plan),
                                null,
                                emptyList(),
                                1,
                            ),
                            layout = TableLayout.forScreen(PHONE_H),
                            onMove = onMove,
                            onHelp = {},
                            onSettings = {},
                        )
                    }
                }
            }
        }
        waitForIdle()
    }

    private companion object {
        val PHONE_W = 411.dp
        val PHONE_H = 740.dp

        /** The target every control in this app offers a finger. */
        const val MIN_TARGET = 44f
    }
}
