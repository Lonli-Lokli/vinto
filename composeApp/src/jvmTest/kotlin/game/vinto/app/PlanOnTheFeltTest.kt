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
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
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
import game.vinto.client.StepHealth
import game.vinto.client.tableFor
import game.vinto.client.teachingSession
import game.vinto.engine.PlayerView
import game.vinto.engine.PublicReveal
import game.vinto.shapes.Card
import game.vinto.shapes.CardAt
import game.vinto.shapes.Claim
import game.vinto.shapes.CoalitionPlan
import game.vinto.shapes.GamePhase
import game.vinto.shapes.Lane
import game.vinto.shapes.PlanEdit
import game.vinto.shapes.Rank
import game.vinto.shapes.Step
import game.vinto.shapes.coalitionInTurnOrder
import game.vinto.shapes.edited
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The plan as the table draws it: the turns, the cards they move, and what may be done to them.
 *
 * The model beneath this is covered in `shared/client`, and that is exactly why this file
 * exists: a plan the screen does not draw is a library rather than a feature, and every one of
 * these behaviours reaches a player only because something in `composeApp` is wired to it.
 */
@OptIn(ExperimentalTestApi::class)
class PlanOnTheFeltTest {

    // ------------------------------------------------------------------ the turns

    @Test
    fun theCardsThePlannedTurnMovesAreMarkedAtTheSeatsThatHoldThem() = runComposeUiTest {
        // At rest there is no animation saying where a card goes — and at rest is the state a
        // plan is read and edited in. So both ends of a planned swap wear a mark on the felt.
        val view = finalRound()
        val (mine, theirs) = twoCards(view)
        val plan = planFor(view, Step.Swap(mine.toCardAt(), theirs.toCardAt()))

        // A stop names the turn it *ends*, so the turn at lane `n` is read at stop `n + 1`.
        val at = laneOf(view, plan) + 1
        val board = assertNotNull(tableFor(view, question = Question.ThePlan(at = at), plan = plan).board)

        assertEquals(
            setOf(mine, theirs),
            board.marks,
            "the two ends of a planned swap are not marked where they lie",
        )

        // And the screen draws that mark: the cards are still ordinary cards on the felt.
        show(view, plan = plan, question = Question.ThePlan(at = at))
        assertTrue(
            onAllNodesWithContentDescription("plan:turn").fetchSemanticsNodes().isNotEmpty() ||
                onAllNodesWithText(view.players[at].nickname, substring = true)
                    .fetchSemanticsNodes()
                    .isNotEmpty(),
            "the plan drew no turn at all",
        )
    }

    @Test
    fun aTurnWithNoStepKeepsItsNumberAndItsPlace() = runComposeUiTest {
        // ② has to mean the same turn to every member reading it, so an undecided turn is a
        // numbered turn with "your call" in it rather than a gap (design D6). The rail draws one
        // turn at a time now — the one being composed — so the claim is read a stop at a time.
        val view = finalRound()
        val (mine, theirs) = twoCards(view)
        val plan = planFor(view, Step.Swap(mine.toCardAt(), theirs.toCardAt()))

        // The row names the seat whose turn it is rather than counting turns at the player: the
        // header's stops are the count, and saying it twice was the duplication that started
        // all this. What must survive is that an undecided turn is still *there*, as itself.
        val order = view.players.filter { it.id != view.vintoCallerId }
        val first = textsOn(view, plan = plan, question = Question.ThePlan(at = 1))
        assertTrue(first.any { it == order[0].nickname }, "no first turn: $first")

        val second = textsOn(view, plan = plan, question = Question.ThePlan(at = 2))
        assertTrue(second.any { it == order[1].nickname }, "the undecided turn lost its place: $second")
        assertTrue(
            describedOn(view, plan = plan, question = Question.ThePlan(at = 2))
                .any { it.contains("your call", ignoreCase = true) },
            "an undecided turn is not drawn as one: $second",
        )
    }

    @Test
    fun aStepThatCannotBeDrawnIsSaidToBeUnplayableRatherThanDrawn() = runComposeUiTest {
        // `rehearse()` already returns no frame for a step naming a card that is not there —
        // "a rehearsal of a broken plan would be a picture of something that cannot happen".
        // The felt says so rather than leaving a numbered gap nobody can account for.
        val view = finalRound()
        val mate = view.players.first { it.id != view.viewerId && it.id != view.vintoCallerId }
        val gone = Step.Swap(CardAt(view.viewerId, 0), CardAt(mate.id, MISSING))
        val plan = CoalitionPlan(lanes = listOf(Lane(mate.id, gone)))

        val words = textsOn(view, plan = plan, question = Question.ThePlan())

        assertTrue(
            words.any { it.contains("cannot be played", ignoreCase = true) },
            "a step naming a card that is not there was drawn as if it could happen: $words",
        )
    }

    @Test
    fun aDisprovedClaimIsMarkedOnTheTurnThatRestsOnIt() = runComposeUiTest {
        // Never repaired, never silently substituted: somebody's memory was wrong, which is the
        // game working, and everything built on that claim is now built on nothing (design D13).
        val view = finalRound()
        val mate = view.players.first { it.id != view.viewerId && it.id != view.vintoCallerId }
        val five = Claim(mate.id, listOf(0), listOf(Rank.FIVE))
        val claimed = view.copy(
            players = view.players.map { seat -> if (seat.id == mate.id) seat.copy(claims = listOf(five)) else seat },
        )
        val plan = CoalitionPlan(
            lanes = listOf(Lane(mate.id, Step.Swap(CardAt(view.viewerId, 0), CardAt(mate.id, 0, five)))),
        )
        val nine = Card("turned", Rank.NINE, 9, played = false, actionText = null)

        val told = textsOn(
            claimed,
            plan = plan,
            question = Question.ThePlan(),
            reveals = listOf(PublicReveal(mate.id, 0, nine)),
        )

        assertTrue(
            told.any { it.contains("proved wrong", ignoreCase = true) },
            "the turn resting on a disproved claim carries no mark: $told",
        )
    }

    @Test
    fun aCardThatMerelyMovedAnnouncesNothing() {
        // The table watched the card go — a Jack or a Queen swap is public — so nothing has been
        // learned and nothing needs saying. A plan that announced its own bookkeeping would be
        // noise at exactly the moment a coalition is busy.
        val view = finalRound()
        val mate = view.players.first { it.id != view.viewerId && it.id != view.vintoCallerId }
        val five = Claim(mate.id, listOf(0), listOf(Rank.FIVE))
        val claimed = view.copy(
            players = view.players.map { seat -> if (seat.id == mate.id) seat.copy(claims = listOf(five)) else seat },
        )
        val plan = CoalitionPlan(
            lanes = listOf(Lane(mate.id, Step.Swap(CardAt(view.viewerId, 0), CardAt(mate.id, 0, five)))),
        )

        val board = assertNotNull(tableFor(claimed, question = Question.ThePlan(), plan = plan).board)

        assertTrue(
            board.lanes.none { it.health == StepHealth.BROKEN },
            "a step whose claim still stands was reported as broken",
        )
    }

    // ------------------------------------------------------------------ the timeline

    @Test
    fun everyTurnIsReachableWithoutScrollingOnAPhone() = runComposeUiTest {
        // 411×740 dp is the phone the report came from. Every turn of a final round has to be
        // reachable at once — which used to mean all three written out in the rail, and means
        // all three named in the header now. One list, not two: the rail listed them while the
        // header listed them again as stops, and the two were a position out of step with each
        // other. Reported as *"why do we have three steps in the bottom and in the header?"*.
        val view = finalRound()
        val (mine, theirs) = twoCards(view)
        val plan = planFor(view, Step.Swap(mine.toCardAt(), theirs.toCardAt()))

        show(view, plan = plan, question = Question.ThePlan())

        // **In turn order**, which is not player order: the transport's positions are the
        // coalition's turns, and the belt composes the one at the position the head is on.
        val seats = coalitionInTurnOrder(view.players.map { it.id }, view.vintoCallerId.orEmpty())
            .map { id -> view.players.first { it.id == id }.nickname }
        assertTrue(seats.size == TURNS, "the fixture is not a three-seat coalition")
        for (seat in seats) {
            assertTrue(
                onAllNodesWithContentDescription(seat).fetchSemanticsNodes().isNotEmpty(),
                "$seat has no stop on the transport",
            )
        }

        // And the belt below names exactly *one* of them — the turn being composed from where
        // the head is parked. Counted by moving the head rather than by looking for a row:
        // every seat is already named twice on the felt, by its plate and by its stop, so the
        // belt's mention is the one that moves when the transport does.
        // Stop `n + 1` is where turn `n` is read, because a stop names the turn it ends.
        fun mentions(turn: Int, seat: String) =
            textsOn(view, plan = plan, question = Question.ThePlan(at = turn + 1)).count { it == seat }

        for ((i, seat) in seats.withIndex()) {
            val here = mentions(i, seat)
            val elsewhere = mentions((i + 1) % seats.size, seat)
            assertTrue(
                here == elsewhere + 1,
                "$seat is named $here times on its own turn and $elsewhere on another; " +
                    "the belt should name exactly the turn being built",
            )
        }
    }

    /**
     * Which position is being read is *said*, not only drawn in gold.
     *
     * This used to be about the past: one line under the turns saying what the last seat did,
     * marked as past in words rather than by colour alone. That line was the one thing in the
     * rail a finger could do nothing with, and the transport's own "Now" is the anchor it stood
     * in for — so the accessibility claim moves with it. A row of positions with one of them
     * current is a row of tabs, and a tab that is current says so.
     */
    @Test
    fun theStopBeingReadIsAnnouncedAndNotOnlyLit() = runComposeUiTest {
        val view = finalRound()
        show(view, plan = null, question = Question.ThePlan(at = 1))

        val stops = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected))
            .fetchSemanticsNodes()
        assertTrue(stops.size > 1, "the transport drew no positions to choose between")
        assertTrue(
            stops.count { it.config.getOrNull(SemanticsProperties.Selected) == true } == 1,
            "the position being read is not announced as the one being read",
        )
    }

    // ------------------------------------------------------------------ changing it

    @Test
    fun theSameSwapIsMadeByCarryingACardOrByTouchingTwo() {
        // The drag and the non-dragging path are the same edit reached another way, not a
        // reduced one (design D5) — so both are built here and the resulting plans compared.
        val view = finalRound()
        val plan = CoalitionPlan()
        // Stop 1: the first coalition turn, which is the one this composes.
        val at = 1
        val board = assertNotNull(tableFor(view, question = Question.ThePlan(at = at), plan = plan).board)
        val composer = assertNotNull(board.building?.composer, "the first turn offered no composer")

        val from = composer.sources.first()
        val onto = assertNotNull(
            composer.drops[from]?.keys?.filterIsInstance<PlanTarget.Card>()?.firstOrNull(),
            "a source with nowhere to go was offered",
        )

        // Carried: one value straight out of the drop map.
        val carried = assertNotNull(composer.drops[from]?.get(onto))

        // Touched: the first touch picks the card up, the second puts it down.
        val picked = tableFor(view, question = Question.ThePlan(at = at), plan = plan).taps[from]
        val holding = (picked as Move.Ask).question as Question.ThePlan
        val touched = tableFor(view, question = holding, plan = plan).taps[onto.ref]

        assertEquals(carried, touched, "carrying a card and touching two made different edits")

        // And the same plan comes out of the door for either.
        val coalition = view.players.map { it.id }.filter { it != view.vintoCallerId }
        val onPlay = view.players.getOrNull(view.currentPlayerIndex)?.id
        assertEquals(
            plan.edited(carried.edit, by = view.viewerId, coalition = coalition, onPlay = onPlay),
            plan.edited((touched as Move.Plan).edit, by = view.viewerId, coalition = coalition, onPlay = onPlay),
            "the two paths did not produce the same plan",
        )
    }

    @Test
    fun onlyTheDestinationsTheComposerAllowsAreOffered() {
        // A drop is refused by never lighting up, not by an error afterwards (design D5). The
        // caller's cards are the case that matters: the coalition may not touch them.
        val view = finalRound()
        val board = assertNotNull(tableFor(view, question = Question.ThePlan(), plan = CoalitionPlan()).board)
        val composer = assertNotNull(board.lanes[0].composer)

        val callers = view.players.first { it.id == view.vintoCallerId }.cards.indices
            .map { CardRef(view.vintoCallerId!!, it) }

        assertTrue(
            composer.sources.none { it in callers },
            "one of the caller's cards was offered as something to carry",
        )
        assertTrue(
            composer.drops.values.none { drops ->
                drops.keys.filterIsInstance<PlanTarget.Card>().any { it.ref in callers }
            },
            "one of the caller's cards was offered as somewhere to put one",
        )
        assertTrue(
            composer.drops.all { (from, drops) ->
                drops.keys.filterIsInstance<PlanTarget.Card>().none { it.ref.playerId == from.playerId }
            },
            "a hand was offered a swap with itself",
        )
    }

    @Test
    fun aLockedTurnOffersNoDestinationAtAll() {
        val view = finalRound()
        val mate = view.players.first { it.id != view.viewerId && it.id != view.vintoCallerId }
        val locked = CoalitionPlan(
            lanes = listOf(Lane(mate.id, Step.Swap(CardAt(view.viewerId, 0), CardAt(mate.id, 0)), locked = true)),
        )
        val at = laneOf(view, locked) + 1

        val board = assertNotNull(tableFor(view, question = Question.ThePlan(at = at), plan = locked).board)

        assertNull(board.building?.composer, "a locked turn offered somewhere to put a card")
        assertTrue(
            tableFor(view, question = Question.ThePlan(at = at), plan = locked).taps.isEmpty(),
            "a locked turn offered a card to select",
        )
    }

    @Test
    fun aCardCarriedNowhereLeavesThePlanExactlyAsItWas() {
        // A release over the felt, over the caller's hand, or over the card's own slot is a
        // release over nothing: the plan is unchanged and the card goes back.
        val view = finalRound()
        val plan = CoalitionPlan(lanes = listOf(Lane(view.players[1].id, Step.TakeTheDiscard)))
        val board = assertNotNull(tableFor(view, question = Question.ThePlan(), plan = plan).board)
        val composer = assertNotNull(board.lanes[0].composer)

        val from = composer.sources.first()
        val felt = PlanTarget.Card(CardRef("nobody-at-this-table", 0))

        assertNull(
            composer.drops[from]?.get(felt),
            "somewhere no step could be made was still a place to drop a card",
        )
    }

    // ------------------------------------------------------------------ the caller

    @Test
    fun theCallerReadsTheSameTurnsInTheSameOrderAndChangesNone() {
        // At a real table the coalition confers within earshot of the player it is planning
        // against, and the plan is already sent to every seat (design D11). What the caller may
        // not do is touch it — and that is the door's answer, not the screen's.
        val view = finalRound()
        val mate = view.players.first { it.id != view.viewerId && it.id != view.vintoCallerId }
        val plan = CoalitionPlan(lanes = listOf(Lane(mate.id, Step.Declare(Rank.KING))))
        val callerSees = view.copy(viewerId = view.vintoCallerId!!)

        val theirs = assertNotNull(
            tableFor(callerSees, question = Question.ThePlan(), plan = plan).board,
            "the caller cannot open a standing plan",
        )
        val ours = assertNotNull(tableFor(view, question = Question.ThePlan(), plan = plan).board)

        assertEquals(ours.lanes.map { it.step }, theirs.lanes.map { it.step }, "different turns")
        assertEquals(ours.outcome, theirs.outcome, "different arrival")

        assertTrue(theirs.lanes.all { it.composer == null }, "the caller was offered an edit")
        assertTrue(
            tableFor(callerSees, question = Question.ThePlan(), plan = plan).taps.isEmpty(),
            "a card responded to the caller's touch",
        )
        val theirTable = tableFor(callerSees, question = Question.ThePlan(), plan = plan)
        assertTrue(
            theirTable.choices.none { it.move is Move.Agree },
            "the caller was offered a way to agree to the plan",
        )
    }

    @Test
    fun thePlanDoorStillRefusesTheCaller() {
        // Seeing it opens neither the door nor the window: `CoalitionPlan.edited` refuses an
        // editor outside the coalition, and the caller is outside it by definition.
        val view = finalRound()
        val caller = view.vintoCallerId!!
        val coalition = view.players.map { it.id }.filter { it != caller }
        val mate = coalition.first()

        val refused = CoalitionPlan().edited(
            PlanEdit.SetLane(mate, Step.Declare(Rank.KING)),
            by = caller,
            coalition = coalition,
            onPlay = view.players.getOrNull(view.currentPlayerIndex)?.id,
        )

        assertTrue(
            refused is game.vinto.shapes.PlanEditOutcome.Refused &&
                refused.reason.contains("coalition", ignoreCase = true),
            "the caller was allowed to plan: $refused",
        )
    }

    @Test
    fun noControlThatActsOnTheRoundSurvivesThePlanBeingOpen() {
        // A control that would act on the real round has no meaning while a hypothetical table
        // is on screen, and leaving one there is how a player draws a card they meant to plan.
        // Absent, not disabled: a disabled control still says "this is where you would do that".
        val onMyTurn = finalRound().let { view ->
            view.copy(currentPlayerIndex = view.players.indexOfFirst { it.id == view.viewerId })
        }
        val live = tableFor(onMyTurn)
        val open = tableFor(onMyTurn, question = Question.ThePlan(), plan = CoalitionPlan())

        assertTrue(live.choices.any { it.move is Move.Send }, "the fixture is not a turn anybody can act on")
        assertTrue(
            open.choices.none { it.move is Move.Send },
            "the open plan still offers a move on the round: ${open.choices.map { it.label }}",
        )
        assertTrue(open.taps.values.none { it is Move.Send }, "a card on the felt still sends a move")
        assertTrue(open.ranks.none { it.move is Move.Send }, "a rank still sends a move")
        assertTrue(open.seats.none { it.move is Move.Send }, "a seat still sends a move")
    }

    @Test
    fun everyNodeOnScreenCanBeActivatedAndNothingIsDispatched() = runComposeUiTest {
        // The whole-screen version of the invariant: not "the model offers no move" but "there
        // is nothing on this screen a finger can reach that becomes one".
        val onMyTurn = finalRound().let { view ->
            view.copy(currentPlayerIndex = view.players.indexOfFirst { it.id == view.viewerId })
        }
        val sent = mutableListOf<Move>()

        setContent {
            VintoTheme {
                Box(modifier = Modifier.size(PHONE_W, PHONE_H)) {
                    CompositionLocalProvider(LocalStage provides Stage()) {
                        TableScreen(
                            state = TableState(
                                onMyTurn,
                                tableFor(onMyTurn, question = Question.ThePlan(), plan = CoalitionPlan()),
                                null,
                                emptyList(),
                                1,
                            ),
                            layout = TableLayout.forScreen(PHONE_H),
                            onMove = { sent += it },
                            onHelp = {},
                            onSettings = {},
                        )
                    }
                }
            }
        }
        waitForIdle()

        val clickable = onAllNodes(hasClickAction()).fetchSemanticsNodes().size
        assertTrue(clickable > 0, "the plan drew a screen with nothing on it")

        repeat(clickable) { index ->
            runCatching {
                onAllNodes(hasClickAction())[index].performClick()
                waitForIdle()
            }
        }

        assertTrue(
            sent.none { it is Move.Send },
            "touching the open plan dispatched a move on the round: ${sent.filterIsInstance<Move.Send>()}",
        )
    }

    // ------------------------------------------------------------------ fixtures

    private fun CardRef.toCardAt() = CardAt(playerId, position)

    /**
     * A final round somebody else called, with the caller still on play.
     *
     * Every coalition seat has said what one of its cards is, because **the palette is what has
     * been said**: a card nobody knows anything about is not on offer to the plan, since a plan
     * that moved it would be moving a guess. A fixture with no claims is a fixture where the
     * composer correctly offers nothing.
     */
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

    /** One of the viewer's cards and one of a teammate's — a swap the door would accept. */
    private fun twoCards(view: PlayerView): Pair<CardRef, CardRef> {
        val mate = view.players.first { it.id != view.viewerId && it.id != view.vintoCallerId }
        return CardRef(view.viewerId, 0) to CardRef(mate.id, 0)
    }

    /** A plan whose only lane belongs to a teammate. */
    private fun planFor(view: PlayerView, step: Step): CoalitionPlan {
        val mate = view.players.first { it.id != view.viewerId && it.id != view.vintoCallerId }
        return CoalitionPlan(lanes = listOf(Lane(mate.id, step)))
    }

    /** Which position of the transport composes the plan's only lane. */
    private fun laneOf(view: PlayerView, plan: CoalitionPlan): Int {
        val board = assertNotNull(tableFor(view, question = Question.ThePlan(), plan = plan).board)
        return board.lanes.indexOfFirst { it.step != null }.coerceAtLeast(0)
    }

    private fun ComposeUiTest.textsOn(
        view: PlayerView,
        plan: CoalitionPlan?,
        question: Question,
        reveals: List<PublicReveal> = emptyList(),
    ): List<String> {
        show(view, plan, question, reveals)
        // Every text of every node, not the first of each: a row that can be tapped *merges*
        // its children's semantics and reports them as a list, so taking only the first hides
        // every line after the one the row happens to draw first.
        return onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text))
            .fetchSemanticsNodes()
            .flatMap { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }
    }

    /** What a screen reader is given: the parts of the row are marks, and the marks carry words. */
    private fun ComposeUiTest.describedOn(
        view: PlayerView,
        plan: CoalitionPlan?,
        question: Question,
        reveals: List<PublicReveal> = emptyList(),
    ): List<String> {
        show(view, plan, question, reveals)
        return onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription))
            .fetchSemanticsNodes()
            .flatMap { it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() }
    }

    private fun ComposeUiTest.show(
        view: PlayerView,
        plan: CoalitionPlan?,
        question: Question,
        reveals: List<PublicReveal> = emptyList(),
        recent: List<game.vinto.client.Say> = emptyList(),
    ) {
        setContent {
            VintoTheme {
                Box(modifier = Modifier.size(PHONE_W, PHONE_H)) {
                    CompositionLocalProvider(LocalStage provides Stage()) {
                        TableScreen(
                            state = TableState(
                                view = view,
                                table = tableFor(view, question = question, plan = plan, reveals = reveals),
                                refusal = null,
                                recent = recent,
                                round = 1,
                            ),
                            layout = TableLayout.forScreen(PHONE_H),
                            onMove = {},
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

        /** A position no hand has, so a step naming it can never be drawn. */
        const val MISSING = 9

        /** How many turns a final round's coalition plays: every game is exactly four seats. */
        const val TURNS = 3
    }
}
