package game.vinto.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onAllNodesWithContentDescription
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
import game.vinto.client.Part
import game.vinto.client.Question
import game.vinto.client.tableFor
import game.vinto.client.teachingSession
import game.vinto.engine.PlayerView
import game.vinto.shapes.CardAt
import game.vinto.shapes.Claim
import game.vinto.shapes.CoalitionPlan
import game.vinto.shapes.GamePhase
import game.vinto.shapes.Lane
import game.vinto.shapes.Rank
import game.vinto.shapes.Step
import game.vinto.shapes.coalitionInTurnOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The plan as table talk, on the screen (design D1, redrawn): a sentence a turn, one turn a
 * page, five rows that never move — and every rule the design gave the rail, read off the
 * screen rather than off the model.
 *
 * The model's half is `PlanAsTalkTest`. This is what a person sees: that a boxed word is the
 * one that can be touched and a plain one cannot, that a card the plan has not dealt yet is
 * rose on the felt with the turn it arrives on, that nothing above the felt is spent on a band
 * while the plan is open, and that the switch says who changed your turn while you were away.
 */
@OptIn(ExperimentalTestApi::class)
class PlanAsTalkScreenTest {

    @Test
    fun theRowsUnderThePageNeverMoveBetweenPages() = runComposeUiTest {
        // Five fixed rows: the page, the answers, the stops, the button. Whichever turn is on
        // screen — and whether or not a rank is being asked for — the stops and the button are
        // where they were, so a thumb that has found them once has found them for the round.
        val view = finalRound()
        val first = turnOrder(view)[0]
        val plan = CoalitionPlan(lanes = listOf(Lane(first, Step.PutDown(CardAt(first, 0)))))

        val onTheFirstTurn = rowsOn(view, plan, Question.ThePlan(at = 1))
        val onTheSecond = rowsOn(view, plan, Question.ThePlan(at = 2))
        val whereItLands = rowsOn(view, plan, Question.ThePlan(at = 4))
        val naming = rowsOn(view, plan, Question.Naming(first, at = 1, part = Part.Own))

        for ((name, rows) in listOf(
            "the second turn" to onTheSecond,
            "the last page" to whereItLands,
            "a rank grid" to naming,
        )) {
            assertEquals(onTheFirstTurn.stops.top, rows.stops.top, "the stops moved on $name")
            assertEquals(onTheFirstTurn.pages.top, rows.pages.top, "the page moved on $name")
        }
        assertTrue(onTheFirstTurn.pages.top < onTheFirstTurn.stops.top, "the stops are not under the page")
    }

    @Test
    fun boxedMeansTouchableAndAFactCannotBeTouched() = runComposeUiTest {
        // With one pile to draw from, "draws" is a fact; "and we'll see" is not a decision
        // either; the next decision is on offer at the end of the line. Only the offer can be
        // touched, and a screen reader is told the same thing a finger is.
        val view = finalRound().copy(discardTop = null)

        show(view, CoalitionPlan(), Question.ThePlan(at = 1))

        assertTrue(described("draws").none { it.touchable }, "a fact could be touched")
        assertTrue(described("and we’ll see").none { it.touchable }, "\"and we'll see\" could be touched")
        assertTrue(described("+ and then…").any { it.touchable }, "the next decision could not be touched")
    }

    @Test
    fun aCardThePlanDealsIsRoseOnTheFeltWithTheTurnItArrivesOn() = runComposeUiTest {
        // Turn 1 puts a card down: from the page after it, the drawn card that took its place
        // is rose and tagged ①, wherever it goes — a member reading turn 3 does not have to
        // remember what turn 1 drew (asked for from a phone).
        val view = finalRound()
        val first = turnOrder(view)[0]
        val plan = CoalitionPlan(lanes = listOf(Lane(first, Step.PutDown(CardAt(first, 0)))))

        show(view, plan, Question.ThePlan(at = 1))
        assertTrue(described("arriving on turn 1", substring = true).isEmpty(), "the card arrived before its turn")

        show(view, plan, Question.ThePlan(at = 2))
        assertTrue(described("arriving on turn 1", substring = true).isNotEmpty(), "the dealt card is not marked")

        // A put-down of a card nobody has named leaves a card nobody knows on the pile, and the
        // pile says so the same way rather than reading as empty.
        val unnamed = CoalitionPlan(lanes = listOf(Lane(first, Step.PutDown(CardAt(first, 1)))))
        show(view, unnamed, Question.ThePlan(at = 2))
        assertTrue(described("arriving on turn 1", substring = true).size >= 2, "the unknown pile card is not marked")
    }

    @Test
    fun nothingAboveTheFeltIsSpentOnABandWhileThePlanIsOpen() = runComposeUiTest {
        // The stops live under the felt now, beside the two film buttons; the countdown that
        // shared the band with them is read on the live table, where the round is.
        val view = finalRound().copy(currentPlayerIndex = 0)
        val closed = textsOn(view, plan = null, question = Question.None)
        assertTrue(closed.any { it.contains("reveal") || it == "last turn" }, "the fixture has no countdown: $closed")

        val open = textsOn(view, plan = CoalitionPlan(), question = Question.ThePlan())
        assertTrue(open.none { it.contains("reveal") || it == "last turn" }, "the band is still drawn: $open")
    }

    @Test
    fun theSwitchWearsWhoeverChangedYourTurnWhileThePlanWasClosed() = runComposeUiTest {
        val view = finalRound()
        val mate = view.players.first { it.id != view.viewerId && it.id != view.vintoCallerId }
        val plan = CoalitionPlan(lanes = listOf(Lane(view.viewerId, Step.Bin)), editedBy = mate.id)

        show(view, plan, Question.None)

        assertTrue(
            described("${mate.nickname} changed your turn", substring = true).isNotEmpty(),
            "the switch does not say who changed my turn",
        )
    }

    @Test
    fun theLiveRailReadsThePlansRowForTheTurnOnPlay() = runComposeUiTest {
        // Information only: the plan's words for the turn on play, under the prompt, with the
        // ordinary buttons beneath them and nothing the plan armed.
        val view = finalRound().copy(currentPlayerIndex = 0)
        val plan = CoalitionPlan(lanes = listOf(Lane(view.viewerId, Step.PutDown(CardAt(view.viewerId, 0)))))

        val words = textsOn(view, plan, Question.None)

        assertTrue(words.any { it.contains("puts down your card 1") }, "the plan's line is not read: $words")
        assertTrue(
            onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text))
                .fetchSemanticsNodes()
                .filter { node ->
                    node.config.getOrNull(
                        SemanticsProperties.Text,
                    )?.any { it.text.contains("puts down") } == true
                }
                .none { it.config.contains(SemanticsActions.OnClick) },
            "the line can be touched",
        )
    }

    @Test
    fun touchingAStopTurnsThePageAndNothingTurnsItBack() = runComposeUiTest {
        // The pager's own collector used to fire on every new board, read the page still on
        // screen against the page the board had just moved to, and send the head straight back
        // — so no turn but the one on screen could be reached (product owner). The screen here
        // answers its own moves, the way the holder does, so the round trip is real.
        val view = finalRound()
        val second = turnOrder(view)[1]
        val who = if (second == view.viewerId) "You" else view.players.first { it.id == second }.nickname
        val asked = mutableListOf<Question>()
        var question by mutableStateOf<Question>(Question.ThePlan(at = 3))

        setContent {
            VintoTheme {
                Box(modifier = Modifier.size(PHONE_W, PHONE_H)) {
                    CompositionLocalProvider(LocalStage provides Stage()) {
                        TableScreen(
                            state = TableState(
                                view,
                                tableFor(view, question = question, plan = CoalitionPlan()),
                                null,
                                emptyList(),
                                1,
                            ),
                            layout = TableLayout.forScreen(PHONE_H),
                            onMove = { move ->
                                (move as? Move.Ask)?.question?.let {
                                    asked += it
                                    question = it
                                }
                            },
                            onHelp = {},
                            onSettings = {},
                        )
                    }
                }
            }
        }
        waitForIdle()

        onAllNodesWithContentDescription("Turn 2, $who").filterToOne(hasClickAction()).performClick()
        waitForIdle()

        assertEquals(Question.ThePlan(at = 2), question, "the page did not stay where it was sent: $asked")
        assertEquals<List<Question>>(
            listOf(Question.ThePlan(at = 2)),
            asked,
            "something asked for a page nobody touched",
        )
    }

    // ------------------------------------------------------------------ fixtures

    /** A final round somebody else called, the caller still on play, every coalition seat having claimed a card. */
    private fun finalRound(): PlayerView {
        val whole = teachingSession().view.value
        val caller = whole.players.first { it.id != whole.viewerId }
        return whole.copy(
            phase = GamePhase.FINAL,
            finalTurnTriggered = true,
            vintoCallerId = caller.id,
            currentPlayerIndex = whole.players.indexOfFirst { it.id == caller.id },
            players = whole.players.map { seat ->
                if (seat.id == caller.id) {
                    seat
                } else {
                    seat.copy(claims = listOf(Claim(seat.id, listOf(0), listOf(Rank.FIVE))))
                }
            },
        )
    }

    private fun turnOrder(view: PlayerView): List<String> =
        coalitionInTurnOrder(view.players.map { it.id }, view.vintoCallerId.orEmpty())

    /** A node's whole description, and whether a finger can do anything with it. */
    private data class Described(val words: String, val touchable: Boolean)

    private fun ComposeUiTest.described(words: String, substring: Boolean = false): List<Described> =
        onAllNodesWithContentDescription(words, substring = substring)
            .fetchSemanticsNodes()
            .map { node -> Described(words, node.config.contains(SemanticsActions.OnClick)) }

    private class Rows(val pages: Rect, val stops: Rect)

    private fun ComposeUiTest.rowsOn(view: PlayerView, plan: CoalitionPlan, question: Question): Rows {
        val stage = Stage()
        show(view, plan, question, stage)
        return Rows(
            pages = assertNotNull(stage.boundsOf("plan:pages"), "no page"),
            stops = assertNotNull(stage.boundsOf("plan:transport"), "no stops"),
        )
    }

    private fun ComposeUiTest.textsOn(view: PlayerView, plan: CoalitionPlan?, question: Question): List<String> {
        show(view, plan, question)
        return onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text))
            .fetchSemanticsNodes()
            .flatMap { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }
    }

    private fun ComposeUiTest.show(
        view: PlayerView,
        plan: CoalitionPlan?,
        question: Question,
        stage: Stage = Stage(),
        onMove: (Move) -> Unit = {},
    ) {
        setContent {
            VintoTheme {
                Box(modifier = Modifier.size(PHONE_W, PHONE_H)) {
                    CompositionLocalProvider(LocalStage provides stage) {
                        TableScreen(
                            state = TableState(
                                view = view,
                                table = tableFor(view, question = question, plan = plan),
                                refusal = null,
                                recent = emptyList(),
                                round = 1,
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
    }
}
