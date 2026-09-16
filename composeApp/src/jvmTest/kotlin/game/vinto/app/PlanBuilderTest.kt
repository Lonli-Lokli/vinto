package game.vinto.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import game.vinto.app.game.GameHolder
import game.vinto.app.game.LocalStage
import game.vinto.app.game.Stage
import game.vinto.app.game.TableLayout
import game.vinto.app.game.TableScreen
import game.vinto.app.game.TableState
import game.vinto.app.theme.VintoTheme
import game.vinto.client.CardRef
import game.vinto.client.LocalGameSession
import game.vinto.client.Move
import game.vinto.client.Part
import game.vinto.client.Question
import game.vinto.client.tableFor
import game.vinto.client.teachingSession
import game.vinto.engine.PlayerView
import game.vinto.engine.tossInIsOpen
import game.vinto.shapes.ActiveTossIn
import game.vinto.shapes.Card
import game.vinto.shapes.CardAt
import game.vinto.shapes.Claim
import game.vinto.shapes.CoalitionPlan
import game.vinto.shapes.Difficulty
import game.vinto.shapes.GameAction
import game.vinto.shapes.GamePhase
import game.vinto.shapes.GameState
import game.vinto.shapes.GameSubPhase
import game.vinto.shapes.Lane
import game.vinto.shapes.Opening
import game.vinto.shapes.Pile
import game.vinto.shapes.PlanEdit
import game.vinto.shapes.PlayerState
import game.vinto.shapes.Rank
import game.vinto.shapes.Step
import game.vinto.shapes.TossIn
import game.vinto.shapes.coalitionInTurnOrder
import game.vinto.shapes.getCardShortDescription
import game.vinto.shapes.getCardValue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Building a turn on the screen: the rail asks, the buttons answer, and the plan stays open.
 *
 * Reported from a phone: *"how should I create plan myself … And how to design moves for
 * them?"* The model's half is `TurnBuilderTest`; this is what the screen had to change for a
 * person to get there — and two things it was doing that no test had caught: every edit
 * closed the plan, and the rank rails a throw-in and a King's declare are named on were not
 * drawn at all while the plan was open.
 */
@OptIn(ExperimentalTestApi::class)
class PlanBuilderTest {

    @Test
    fun anEditLeavesThePlanOpenAtTheTurnItWasMadeOn() = runTest {
        // Every answer to the rail is an edit, and every edit sent the screen back to the live
        // table: the player answered one part of a turn and had to find the switch, the stop and
        // the turn again to answer the next. Building a turn is three or four edits in a row.
        val session = LocalGameSession(seed = 5L, difficulty = Difficulty.MODERATE, resuming = justCalled())
        session.dispatch(GameAction.Empty(JsonNull))
        val holder = GameHolder(
            session,
            view = mutableStateOf(session.view.value),
            plan = mutableStateOf(session.plan.value),
        )

        holder.act(Move.Ask(Question.ThePlan(at = 2)))
        holder.act(Move.Plan(PlanEdit.SetLane("bot-4", Step.Bin)))
        assertNull(holder.refusal)
        assertEquals(Question.ThePlan(at = 2), holder.question, "an edit closed the plan")

        // From a chooser, back to the plan at the same stop — not to the chooser, whose question
        // has been answered, and not to the first turn.
        holder.act(Move.Ask(Question.Doing("bot-4", at = 2)))
        holder.act(Move.Plan(PlanEdit.SetLane("bot-4", Step.UseIt)))
        assertEquals(Question.ThePlan(at = 2), holder.question, "a chooser's answer did not return to the plan")

        // A card picked up is put down by the edit it made.
        holder.act(Move.Ask(Question.ThePlan(at = 2, picked = CardRef("bot-4", 0))))
        holder.act(Move.Plan(PlanEdit.SetLane("bot-4", Step.Bin)))
        assertEquals(Question.ThePlan(at = 2), holder.question, "the picked card was still in hand after its edit")

        // Agreeing is the end of talking, not the end of reading: the board stays up.
        holder.act(Move.Agree(true))
        assertNull(holder.refusal)
        assertEquals(Question.ThePlan(at = 2), holder.question, "agreeing closed the plan")
    }

    @Test
    fun aThrowIsPickedOnTheFeltAndARankOffTheWholeSetWhileThePlanIsOpen() = runComposeUiTest {
        // "Throw in" used to open a table whose ranks the plan's rail never drew, so a member
        // naming the rank they would toss in saw a question, a Back button and fourteen missing
        // answers. A throw is a card touched on the felt now — a throw is a card, and the table
        // can only vouch for a card it has been told about — and the King's rank, where nobody
        // has said what the pointed card is, is the whole set drawn inside the page.
        val view = finalRound()
        val mate = coalitionInTurnOrder(view.players.map { it.id }, view.vintoCallerId!!)[0]

        val throwing = tableFor(view, question = Question.Throwing(mate, at = 1, index = 0), plan = CoalitionPlan())
        assertTrue(throwing.taps.isNotEmpty(), "no card on the felt to throw in")
        show(view, plan = CoalitionPlan(), question = Question.Throwing(mate, at = 1, index = 0))
        assertTrue(
            onAllNodesWithContentDescription("which card?").fetchSemanticsNodes().isNotEmpty(),
            "the sentence does not ask which card is thrown",
        )

        show(view, plan = CoalitionPlan(), question = Question.Naming(mate, at = 1, part = Part.Own))
        assertTrue(onAllNodesWithText("K").fetchSemanticsNodes().isNotEmpty(), "no rank to declare")
    }

    @Test
    fun theSentenceOffersTheNextPartOfTheTurnWhereItsAnswerGoes() = runComposeUiTest {
        // The only button under "What should Tide do with their turn?" was AGREE. Every decision
        // is a word of the sentence now: with a Jack on the pile "draws" is a decision, and
        // touching it says "take the Jack" instead; what becomes of the card is on offer at the
        // end of the line, and its answers sit in the row under the sentence.
        val view = finalRound()
        val first = coalitionInTurnOrder(view.players.map { it.id }, view.vintoCallerId!!)[0]
        val jack = card(Rank.JACK, "jack-on-the-pile")
        val moves = mutableListOf<Move>()

        show(
            view.copy(discardTop = jack),
            plan = CoalitionPlan(),
            question = Question.ThePlan(at = 1),
            onMove = { moves += it },
        )
        // The first: the pager composes the neighbouring page too, and its "draws" is that
        // turn's own word.
        onAllNodesWithContentDescription("draws").onFirst().performClick()
        waitForIdle()
        assertTrue(
            Move.Plan(PlanEdit.OpenLane(first, Opening.TAKE_THE_DISCARD)) in moves,
            "touching \"draws\" with a Jack on the pile did not offer to take it: $moves",
        )

        // The rail’s own words, because they are the rail’s own moves: one move, one name,
        // whether it is being planned or played.
        val answers = textsOn(view, plan = CoalitionPlan(), question = Question.Doing(first, at = 1))
        for (answer in listOf("Swap Cards", "Discard")) {
            assertTrue(answers.any { it.equals(answer, ignoreCase = true) }, "no answer $answer: $answers")
        }
        // And nothing plays a card nobody has drawn yet: the sentence no table can say.
        assertTrue(
            answers.none { it.equals("Use Action", ignoreCase = true) },
            "an action on a blind draw was offered",
        )
        // Nor "we'll see": putting a turn back to undecided closes every page after it, so the
        // one answer that could throw the rest of the board away is not offered (`doingTable`).
        assertTrue(answers.none { it.equals("We’ll see", ignoreCase = true) }, "a turn could be undecided again")
    }

    @Test
    fun readyOpensThePlanOnYourOwnTurn() = runTest {
        // "I'm ready" ends the talking, and the plan is what the talking was for: it opens where
        // there is something to do — your own turn, while it can still be built.
        val session = LocalGameSession(seed = 5L, difficulty = Difficulty.MODERATE, resuming = justCalled())
        session.dispatch(GameAction.Empty(JsonNull))
        val holder = GameHolder(
            session,
            view = mutableStateOf(session.view.value),
            plan = mutableStateOf(session.plan.value),
        )

        holder.act(Move.Done)

        assertNull(holder.refusal)
        // The coalition in turn order from the caller is bot-3, bot-4, then this seat: page 3.
        assertEquals(Question.ThePlan(at = 3), holder.question, "being ready did not open the plan on my turn")
    }

    @Test
    fun theTurnComingRoundLeavesThePlanOpen() = runTest {
        // The turn on play is the one turn the plan most needs to be open for: the card just
        // drawn is the news the plan turns on. It used to close the moment the turn came round.
        val session = LocalGameSession(seed = 5L, difficulty = Difficulty.MODERATE, resuming = justCalled())
        session.dispatch(GameAction.Empty(JsonNull))
        val holder = GameHolder(
            session,
            view = mutableStateOf(session.view.value),
            plan = mutableStateOf(session.plan.value),
        )
        holder.act(Move.Ask(Question.ThePlan(at = 3)))

        val myTurn = session.view.value.copy(
            currentPlayerIndex = session.view.value.players.indexOfFirst { it.id == session.playerId },
            activeTossIn = null,
            conferMsRemaining = null,
        )
        holder.noticed(myTurn)

        assertEquals(Question.ThePlan(at = 3), holder.question, "my turn coming round closed the plan")
    }

    @Test
    fun aWindowAlreadyOpenWhenThePlanOpensLeavesItOpen() = runTest {
        // The call's own card opened a toss-in window, and "I'm ready" opened the plan over it.
        // The window's view, delivered a beat later, must not shut the plan the member asked
        // for — which is what a phone showed: Ready, and the live table (product owner). A
        // card landing *while* the plan is open still steps it aside.
        val window = ActiveTossIn(
            ranks = listOf(Rank.THREE),
            initiatorId = "bot-2",
            originalPlayerIndex = 1,
            participants = emptyList(),
            queuedActions = emptyList(),
            waitingForInput = true,
            playersReadyForNextTurn = emptyList(),
        )
        val session = LocalGameSession(
            seed = 5L,
            difficulty = Difficulty.EASY,
            resuming = justCalled().copy(subPhase = GameSubPhase.TOSS_QUEUE_ACTIVE, activeTossIn = window),
        )
        val holder = GameHolder(
            session,
            view = mutableStateOf(session.view.value),
            plan = mutableStateOf(session.plan.value),
        )

        holder.act(Move.Done)
        assertNull(holder.refusal)
        assertTrue(holder.question is Question.ThePlan, "being ready did not open the plan: ${holder.question}")

        val open = session.view.value
        assertTrue(open.tossInIsOpen, "the fixture's window is not open")
        holder.noticed(open)
        assertTrue(holder.question is Question.ThePlan, "the window that was already open closed the plan")

        holder.noticed(open.copy(discardTop = card(Rank.THREE, "a-card-that-landed-later")))
        assertEquals(Question.None, holder.question, "a card landing while the plan is open did not step it aside")
    }

    @Test
    fun aTeammatesThrowInIsReadOnTheTurnThatLandsItsRank() = runComposeUiTest {
        // I have said I will throw a five in on Tide's turn, which puts her five down: the throw
        // is a clause of *her* turn's sentence, wearing my face — "you play this, then I throw
        // in mine" read where it happens.
        val view = finalRound()
        val first = coalitionInTurnOrder(view.players.map { it.id }, view.vintoCallerId!!)[0]
        val plan = CoalitionPlan(
            lanes = listOf(
                Lane(first, Step.PutDown(CardAt(first, 0)), tossIns = listOf(TossIn(view.viewerId, Rank.FIVE))),
            ),
        )

        show(view, plan, question = Question.ThePlan(at = 1))
        assertTrue(
            onAllNodesWithContentDescription("then you throw in a 5").fetchSemanticsNodes().isNotEmpty(),
            "my throw-in is not read on the turn that lands a five",
        )
        // And the sentence offers the next throw and the way to change this one.
        assertTrue(
            onAllNodesWithContentDescription("+ throw in…").fetchSemanticsNodes().isNotEmpty(),
            "no way to add a throw",
        )
    }

    @Test
    fun theSentenceIsChipsAndTheWordBeingAskedForIsLit() = runComposeUiTest {
        // A Jack on the pile, taken: the turn reads "takes the Jack, which two cards?" — taking
        // the pile's card is playing it, so no word says so — and the last word is the one the
        // felt is waiting on, said as selected so a screen reader hears it and not only the
        // colour.
        val view = finalRound()
        val first = coalitionInTurnOrder(view.players.map { it.id }, view.vintoCallerId!!)[0]
        val jack = card(Rank.JACK, "jack-on-the-pile")
        val plan = CoalitionPlan(lanes = listOf(Lane(first, Step.UseIt, opening = Opening.TAKE_THE_DISCARD)))

        show(view.copy(discardTop = jack), plan, question = Question.ThePlan(at = 1))
        for (word in listOf("takes the Jack", "which two cards?")) {
            assertTrue(onAllNodesWithContentDescription(word).fetchSemanticsNodes().isNotEmpty(), "no word $word")
        }
        val asked = onAllNodesWithContentDescription("which two cards?").fetchSemanticsNodes().first()
        assertEquals(
            "being asked now",
            asked.config.getOrNull(SemanticsProperties.StateDescription),
            "the word being asked for is not said to be",
        )
    }

    @Test
    fun everyStopWearsTheSeatWhoseTurnItEnds() = runComposeUiTest {
        // A stop carries **both**: the seat's face, so a member asking "how to design moves for
        // them" can see whose turn each one is, and the turn's numeral — the same mark a rose
        // card wears for the turn it arrives on (`RoseBack`), so the two pair by eye.
        //
        // It used to read "Turn 1", and the reason was that "1" alone on a header row is a mark
        // a player stops to ask the meaning of. It is not alone: it sits in a circle beside a
        // face, in a row of them joined by arrows that say what the word was carrying, and it is
        // the mark the cards already use for the same thing. What the word bought in width was
        // the whole transport row — four stops and two buttons do not fit a phone, and ▶▶ was
        // taking the shortfall out of its own thumb.
        //
        // It is still "Turn 2, Dune" to a screen reader, where there is no width to buy.
        val view = finalRound()
        show(view, plan = CoalitionPlan(), question = Question.ThePlan())

        val order = coalitionInTurnOrder(view.players.map { it.id }, view.vintoCallerId!!)
        order.forEachIndexed { index, id ->
            val who = if (id == view.viewerId) "You" else view.players.first { it.id == id }.nickname
            // The stops, and not the page's own heading, which says the same words: a stop is
            // the one that is a tab.
            val chips = onAllNodesWithContentDescription("Turn ${index + 1}, $who")
                .fetchSemanticsNodes()
                .filter { it.config.getOrNull(SemanticsProperties.Selected) != null }
            assertTrue(chips.isNotEmpty(), "stop ${index + 1} does not say whose turn it ends")
            val words = chips.first().config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
            assertEquals(
                "${index + 1}",
                words.firstOrNull(),
                "the stop spends width on a word the arrows already carry: $words",
            )
        }
    }

    @Test
    fun theLastStopIsAMarkRatherThanAWordNobodyAskedFor() = runComposeUiTest {
        // "Lands" is the codebase's own metaphor and not a word a player reaches for, reported
        // from a phone as unclear. Every other stop on the row is a mark now — a numeral in a
        // circle, a face, an arrow — so a word there was the odd one out as well as the opaque
        // one. It is the finish mark instead, and the whole of what it means goes to the screen
        // reader, where there is no width to buy and no picture to read.
        val view = finalRound()
        show(view, plan = CoalitionPlan(), question = Question.ThePlan())

        val lands = onAllNodesWithContentDescription("After every turn")
            .fetchSemanticsNodes()
            .filter { it.config.getOrNull(SemanticsProperties.Selected) != null }
        assertTrue(lands.isNotEmpty(), "the last stop does not say what it is")
        assertTrue(
            lands.first().config.getOrNull(SemanticsProperties.Text).orEmpty().isEmpty(),
            "the last stop still spells a word: " +
                lands.first().config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text },
        )
    }

    @Test
    fun touchingATeammatesPlateOpensTheirTurn() = runComposeUiTest {
        // "How to design moves for them": their seat is on the felt, so it is the way to their
        // turn — the same stop the header offers, reached from the person it belongs to.
        val view = finalRound()
        val moves = mutableListOf<Move>()
        val order = coalitionInTurnOrder(view.players.map { it.id }, view.vintoCallerId!!)
        val second = view.players.first { it.id == order[1] }

        show(view, plan = CoalitionPlan(), question = Question.ThePlan(at = 1), onMove = { moves += it })
        onNodeWithContentDescription("Plan ${second.nickname}’s turn").performClick()
        waitForIdle()

        assertTrue(Move.Ask(Question.ThePlan(at = 2)) in moves, "the plate did not open the turn: $moves")
        assertTrue(moves.none { it is Move.Send }, "a plate in plan mode dispatched a move")
    }

    // ------------------------------------------------------------------ fixtures

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

    /** A final round the second seat has just called: the caller is still on play, the window opens. */
    private fun justCalled(callerId: String = "bot-2") = GameState(
        gameId = "plan-builder",
        roundNumber = 1,
        turnNumber = 12,
        phase = GamePhase.FINAL,
        subPhase = GameSubPhase.IDLE,
        finalTurnTriggered = true,
        players = listOf(
            seat("human-1", isHuman = true, callerId, ranks = listOf(Rank.NINE)),
            seat("bot-2", isHuman = false, callerId, ranks = listOf(Rank.KING, Rank.TWO)),
            seat("bot-3", isHuman = false, callerId, ranks = listOf(Rank.FIVE)),
            seat("bot-4", isHuman = false, callerId, ranks = listOf(Rank.KING, Rank.SIX)),
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
        onMove: (Move) -> Unit = {},
    ) {
        setContent {
            VintoTheme {
                Box(modifier = Modifier.size(PHONE_W, PHONE_H)) {
                    CompositionLocalProvider(LocalStage provides Stage()) {
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
