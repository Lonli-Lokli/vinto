package game.vinto.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.Role
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
import game.vinto.shapes.PlanEdit
import game.vinto.shapes.Rank
import game.vinto.shapes.Step
import game.vinto.shapes.coalitionInTurnOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
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

        // On turn 1's own page the only card arriving on turn 1 is the one the turn is about to
        // draw, in the slot under the deck — the card it will *put in the hand* has not been put
        // there yet. One node, not none: every turn opens with a card nobody has seen.
        show(view, plan, Question.ThePlan(at = 1))
        assertEquals(1, described("arriving on turn 1", substring = true).size, "the card arrived before its turn")

        // From the page after it, the drawn card has taken its place in the hand and is marked
        // there — and the slot under the deck now holds turn 2's own draw instead.
        show(view, plan, Question.ThePlan(at = 2))
        assertTrue(described("arriving on turn 1", substring = true).isNotEmpty(), "the dealt card is not marked")
        assertEquals(1, described("arriving on turn 2", substring = true).size, "turn 2 has no card to draw")

        // A put-down of a card nobody has named leaves a card nobody knows on the pile, and the
        // pile says so the same way rather than reading as empty.
        val unnamed = CoalitionPlan(lanes = listOf(Lane(first, Step.PutDown(CardAt(first, 1)))))
        show(view, unnamed, Question.ThePlan(at = 2))
        assertTrue(described("arriving on turn 1", substring = true).size >= 2, "the unknown pile card is not marked")
    }

    @Test
    fun nothingAboveTheFeltIsSpentOnABandAtAll() = runComposeUiTest {
        // The stops live under the felt, beside the two film buttons. The countdown that used to
        // share the band with them was the last row in it and is gone as well: it was drawn only
        // while the round was final, so it arrived when somebody called and left when the hands
        // went over, moving the whole felt under the player's thumb both times.
        val view = finalRound().copy(currentPlayerIndex = 0)

        for (open in listOf(false, true)) {
            val words = textsOn(
                view,
                plan = if (open) CoalitionPlan() else null,
                question = if (open) Question.ThePlan() else Question.None,
            )
            assertTrue(
                words.none { it.contains("reveal") || it == "last turn" },
                "a band above the felt with the plan ${if (open) "open" else "closed"}: $words",
            )
        }
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
        // Every turn settled, so all four pages are open and the jump under test can happen at
        // all — a page after an undecided turn is closed and has nothing to touch.
        val whole = reaching(view, turnOrder(view).size + 1)

        setContent {
            VintoTheme {
                Box(modifier = Modifier.size(PHONE_W, PHONE_H)) {
                    CompositionLocalProvider(LocalStage provides Stage()) {
                        TableScreen(
                            state = TableState(
                                view,
                                tableFor(view, question = question, plan = whole),
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

    /**
     * The Queen's arrow, on the felt: lit she trades, dim she only looks, and a touch flips the
     * two. It is the smallest control the rail draws and the only one whose whole meaning is its
     * state, so what a finger does with it is worth holding — the model's half is
     * `PlanAsTalkTest.aQueensArrowFlipsBetweenTradingAndOnlyLookingAndAJackHasNone`, and until
     * now neither half had a test.
     */
    @Test
    fun touchingAQueensArrowTurnsHerTradeIntoALook() = runComposeUiTest {
        val view = finalRound()
        val me = view.viewerId
        val others = turnOrder(view).filter { it != me }
        val trading = Step.Swap(CardAt(others[0], 0), CardAt(others[1], 0))
        val plan = CoalitionPlan(
            lanes = listOf(Lane(me, Step.PutDown(CardAt(me, 0), guess = Rank.QUEEN, then = trading))),
        )
        val moves = mutableListOf<Move>()

        val stage = Stage()
        val page = turnOrder(view).indexOf(me) + 1
        show(view, reaching(view, page, plan), Question.ThePlan(at = page), stage, onMove = { moves += it })
        // Found by where it is rather than by what it is: the header wears a switch too, and
        // the one being tested is the mark between the two cards on the felt.
        val where = assertNotNull(stage.boundsOf("plan:arrow"), "the Queen's arrow is not on the felt")
        val switches = onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
        val arrow = switches.fetchSemanticsNodes().indexOfFirst { it.boundsInRoot == where }
        assertTrue(arrow >= 0, "the arrow on the felt answers no touch")
        // Named as well as stated: a switch announced as "on" and nothing else is a control a
        // screen reader cannot use, and this one is the whole difference between a look and a
        // trade. It says the clause it controls, which is what the eye reads off the same row.
        val named = switches.fetchSemanticsNodes()[arrow].config
        val spoken = named.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
        assertTrue(spoken.isNotEmpty(), "the arrow is a switch a screen reader cannot name")
        switches[arrow].performClick()
        waitForIdle()

        val edit = assertIs<PlanEdit.SetLane>(moves.filterIsInstance<Move.Plan>().single().edit)
        val put = assertIs<Step.PutDown>(edit.step)
        val look = assertIs<Step.Peek>(put.then, "the arrow did not turn the trade into a look")
        assertEquals(others[0], look.card.seat)
        assertEquals(others[1], assertNotNull(look.also, "a Queen looks at two cards").seat)
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

    /**
     * [plan] with every turn before [page] settled, so the pager reaches [page] at all.
     *
     * The plan is read front to back and the page after a turn nobody has decided is closed
     * (`Transport.reach`), so a fixture that sets one seat's turn and opens it would be clamped
     * back to the first page. Let-go is the emptiest decision there is.
     */
    private fun reaching(view: PlayerView, page: Int, plan: CoalitionPlan = CoalitionPlan()): CoalitionPlan =
        turnOrder(view).take(page - 1).fold(plan) { standing, seat ->
            if (standing.lanes.any { it.seat == seat && it.step != null }) {
                standing
            } else {
                standing.copy(lanes = standing.lanes.filterNot { it.seat == seat } + Lane(seat, Step.Bin))
            }
        }

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
