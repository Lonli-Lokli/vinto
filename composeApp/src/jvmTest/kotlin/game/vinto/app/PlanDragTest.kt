package game.vinto.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import game.vinto.app.game.LocalStage
import game.vinto.app.game.Stage
import game.vinto.app.game.TableLayout
import game.vinto.app.game.TableScreen
import game.vinto.app.game.TableState
import game.vinto.app.theme.VintoTheme
import game.vinto.client.CardRef
import game.vinto.client.Move
import game.vinto.client.PlanTarget
import game.vinto.client.Question
import game.vinto.client.Speaker
import game.vinto.client.tableFor
import game.vinto.client.teachingSession
import game.vinto.engine.PlayerView
import game.vinto.shapes.Card
import game.vinto.shapes.Claim
import game.vinto.shapes.CoalitionPlan
import game.vinto.shapes.GamePhase
import game.vinto.shapes.Lane
import game.vinto.shapes.Opening
import game.vinto.shapes.PlanEdit
import game.vinto.shapes.Rank
import game.vinto.shapes.Step
import game.vinto.shapes.coalitionInTurnOrder
import game.vinto.shapes.getCardShortDescription
import game.vinto.shapes.getCardValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Changing the plan by carrying a card to where it should go (design D5).
 *
 * The gesture is the argument: the plan is a picture of cards moving, and the gesture that says
 * "this card goes there" is carrying it there. Two conditions make it work on the felt that
 * would not hold on a live table — **nothing in plan mode scrolls**, so a drag has no scrolling
 * parent to fight, and **a drop is validated before release**, so an illegal one is refused by
 * never lighting up rather than by an error afterwards.
 *
 * What a drag must not cost is reachability, and that is `PlanOnTheFeltTest`'s pairing test:
 * every edit here is also reachable by touching one card and then the next, producing the same
 * `PlanEdit`. This file is about the carrying.
 */
@OptIn(ExperimentalTestApi::class)
class PlanDragTest {

    @Test
    fun aDragBeginsOnMovementRatherThanAfterAHold() = runComposeUiTest {
        // A long press is the tax paid for telling a drag apart from a scroll, and in plan mode
        // there is nothing to scroll, nothing to tap-to-play and nothing to swipe (design D5a).
        // So a card is in the air after a touch and a movement, with no wait in between.
        val view = jackOnThePile(finalRound())
        val stage = Stage()
        val from = CardRef(view.viewerId, 0)

        show(view, stage, plan = takingTheJack(view))

        onNodeWithContentDescription(cardLabel(view, from), substring = true)
            .performTouchInput {
                down(center)
                moveBy(Offset(SLOP, SLOP))
                moveBy(Offset(SLOP, SLOP))
            }
        waitForIdle()

        assertEquals(from, stage.carrying, "no hold, and no card in the air either")
    }

    @Test
    fun carryingACardOntoAnotherSeatsCardPlansASwap() = runComposeUiTest {
        // A trade needs a card that trades: the pile's Jack, taken by the turn being built.
        val view = jackOnThePile(finalRound())
        val stage = Stage()
        val moves = mutableListOf<Move>()
        val from = CardRef(view.viewerId, 0)
        val onto = CardRef(mate(view).id, 0)

        show(view, stage, plan = takingTheJack(view), onMove = { moves += it })
        carry(view, from, onto)

        val planned = moves.filterIsInstance<Move.Plan>()
        assertTrue(planned.isNotEmpty(), "carrying a card onto another seat's planned nothing: $moves")

        val edit = assertNotNull(planned.last().edit as? PlanEdit.SetLane)
        val swap = assertNotNull(edit.step as? Step.Swap, "the edit was not a swap: ${edit.step}")
        assertEquals(
            setOf(from.playerId to from.position, onto.playerId to onto.position),
            setOf(swap.from.seat to swap.from.position, swap.to.seat to swap.to.position),
            "the swap does not name the two cards that were carried together",
        )
    }

    @Test
    fun carryingACardOntoNothingLeavesThePlanByteIdentical() = runComposeUiTest {
        val view = jackOnThePile(finalRound())
        val stage = Stage()
        val moves = mutableListOf<Move>()
        val standing = takingTheJack(view)
        val from = CardRef(view.viewerId, 0)

        show(view, stage, plan = standing, onMove = { moves += it })

        // Released over the felt: the card goes back where it came from, and the plan is not
        // asked to change at all — which is what makes a mis-drop cost nothing.
        onNodeWithContentDescription(cardLabel(view, from), substring = true)
            .performTouchInput {
                down(center)
                moveBy(Offset(SLOP, SLOP))
                moveBy(Offset(0f, NOWHERE))
                up()
            }
        waitForIdle()

        assertTrue(moves.none { it is Move.Plan }, "a release over nothing edited the plan: $moves")
        assertEquals(null, stage.carrying, "the card was left in the air")
    }

    @Test
    fun theDiscardIsAPlaceToPutACardDownAndOnlyForACardThatCouldBe() {
        // A put-down is offered from the turn's own seat and only for a card the table knows the
        // rank of: a put-down of a mystery sets nothing up for a teammate to throw in on.
        val view = finalRound()
        val mine = view.players.indexOfFirst { it.id == view.viewerId }
        val turns = assertNotNull(tableFor(view, question = Question.ThePlan(), plan = CoalitionPlan()).board)
        // A stop names the turn it *ends*, so the turn at lane `n` is read at stop `n + 1`.
        val at = turns.lanes.indexOfFirst { it.who == Speaker.You } + 1
        assertTrue(at >= 0 && mine >= 0, "the viewer has no turn in this final round")

        // The turns before mine settled, or the pager never reaches mine: a page after a turn
        // nobody has decided is closed (`Transport.reach`).
        val order = coalitionInTurnOrder(view.players.map { it.id }, view.vintoCallerId.orEmpty())
        val before = CoalitionPlan(lanes = order.take(at - 1).map { Lane(it, Step.Bin) })
        val parked = assertNotNull(tableFor(view, question = Question.ThePlan(at = at), plan = before).board)
        // `building` rather than an index: which lane that is has moved once already, and the
        // board is the one place that knows.
        val composer = assertNotNull(parked.building?.composer, "the viewer's own turn offers no composer")

        val own = composer.drops.filterKeys { it.playerId == view.viewerId }
        assertTrue(
            own.values.any { it.containsKey(PlanTarget.Discard) },
            "none of the turn's own cards could be put down",
        )
        val others = composer.drops.filterKeys { it.playerId != view.viewerId }
        assertTrue(
            others.values.none { it.containsKey(PlanTarget.Discard) },
            "a card belonging to another seat was offered to this seat's put-down",
        )
    }

    @Test
    fun noPlanAffordanceNeedsAPointerToFind() = runComposeUiTest {
        // Nothing here is hover-only, on any platform: the two edits are a carry and a pair of
        // touches, and every control is a button with a label. Read by *doing* it with touch
        // alone — a pointer never enters this test.
        val view = jackOnThePile(finalRound())
        val stage = Stage()
        val moves = mutableListOf<Move>()

        show(view, stage, plan = takingTheJack(view), onMove = { moves += it })
        carry(view, CardRef(view.viewerId, 0), CardRef(mate(view).id, 0))

        assertTrue(
            moves.any { it is Move.Plan },
            "the plan could not be changed by touch alone: $moves",
        )
    }

    // ------------------------------------------------------------------ fixtures

    /** Carries the card at [from] onto the one at [onto], the way a finger does. */
    private fun ComposeUiTest.carry(view: PlayerView, from: CardRef, onto: CardRef) {
        val target = onNodeWithContentDescription(cardLabel(view, onto), substring = true)
        val source = onNodeWithContentDescription(cardLabel(view, from), substring = true)
        val middle = target.fetchSemanticsNode().boundsInRoot.center
        val start = source.fetchSemanticsNode().boundsInRoot.center

        onNodeWithContentDescription(cardLabel(view, from), substring = true)
            .performTouchInput {
                down(center)
                moveBy(Offset(SLOP, SLOP))
                moveBy(middle - start - Offset(SLOP, SLOP))
                up()
            }
        waitForIdle()
    }

    /** How a card names itself to a screen reader, which is also how a test finds it. */
    private fun cardLabel(view: PlayerView, ref: CardRef): String {
        val seat = view.players.first { it.id == ref.playerId }
        return "${seat.nickname}, card ${ref.position + 1}"
    }

    private fun mate(view: PlayerView) =
        view.players.first { it.id != view.viewerId && it.id != view.vintoCallerId }

    /** The same table with an unplayed Jack on the pile: the one card a plan can trade with in advance. */
    private fun jackOnThePile(view: PlayerView): PlayerView = view.copy(
        discardTop = Card(
            id = "jack-on-the-pile",
            rank = Rank.JACK,
            value = getCardValue(Rank.JACK),
            played = false,
            actionText = getCardShortDescription(Rank.JACK),
        ),
    )

    /** The first coalition turn takes the pile's Jack, which is what makes its two cards a question. */
    private fun takingTheJack(view: PlayerView): CoalitionPlan {
        val first = coalitionInTurnOrder(view.players.map { it.id }, view.vintoCallerId.orEmpty())[0]
        return CoalitionPlan(lanes = listOf(Lane(first, Step.UseIt, opening = Opening.TAKE_THE_DISCARD)))
    }

    /** A final round somebody else called, with every coalition seat having claimed a card. */
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

    private fun ComposeUiTest.show(
        view: PlayerView,
        stage: Stage,
        plan: CoalitionPlan?,
        onMove: (Move) -> Unit = {},
    ) {
        setContent {
            VintoTheme {
                Box(modifier = Modifier.size(PHONE_W, PHONE_H)) {
                    CompositionLocalProvider(LocalStage provides stage) {
                        TableScreen(
                            state = TableState(
                                view = view,
                                table = tableFor(view, question = Question.ThePlan(), plan = plan),
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

        /** Enough movement to be a drag rather than a tap, and not enough to be a journey. */
        const val SLOP = 20f

        /** Far enough to be over felt rather than over anything. */
        const val NOWHERE = 60f
    }
}
