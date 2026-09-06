package game.vinto.client

import game.vinto.engine.PlayerView
import game.vinto.engine.PublicReveal
import game.vinto.engine.projectView
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
import game.vinto.shapes.Pile
import game.vinto.shapes.PlanEdit
import game.vinto.shapes.PlayerState
import game.vinto.shapes.Rank
import game.vinto.shapes.Shed
import game.vinto.shapes.Step
import game.vinto.shapes.getCardShortDescription
import game.vinto.shapes.getCardValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The board, the composer and the pre-arm (design D7a), as the table model offers them.
 *
 * What is held: a coalition member sees one lane per turn in turn order and can tap the ones
 * the door would accept; the caller reads and taps nothing; the composer's three kinds of step
 * each come out as exactly one \`PlanEdit\` for one lane, with the caller's cards never on offer;
 * agreeing is offered when there is something to agree to and not once you have; and on the
 * viewer's own turn their lane is written under the prompt and put first when the table's own
 * controls already offer the move.
 */
class PlanBoardTest {

    private val me = "human-1"
    private val caller = "bot-2"
    private val nina = "bot-3"
    private val don = "bot-4"

    private fun card(rank: Rank, id: String) = Card(
        id = id,
        rank = rank,
        value = getCardValue(rank),
        played = false,
        actionText = getCardShortDescription(rank).takeIf { it.isNotEmpty() },
    )

    private fun seat(id: String, ranks: List<Rank>, claims: List<Claim>? = null) = PlayerState(
        id = id,
        name = id,
        nickname = id.substringBefore('-').replaceFirstChar { it.uppercase() } + id.last(),
        isHuman = id == me,
        isBot = id != me,
        cards = ranks.mapIndexed { index, rank -> card(rank, "$id-c$index") },
        knownCardPositions = emptyList(),
        isVintoCaller = id == caller,
        coalitionWith = if (id == caller) emptyList() else listOf(me, nina, don) - id,
        claims = claims,
    )

    /** A final round the bot in seat two called; the caller is still on play unless said. */
    private fun finalRound(
        onPlay: String = caller,
        discardTop: Rank = Rank.THREE,
        pending: game.vinto.shapes.PendingAction? = null,
        subPhase: GameSubPhase = GameSubPhase.IDLE,
    ) = GameState(
        gameId = "board",
        roundNumber = 1,
        turnNumber = 12,
        phase = GamePhase.FINAL,
        subPhase = subPhase,
        finalTurnTriggered = true,
        players = listOf(
            seat(me, listOf(Rank.NINE, Rank.TWO)),
            seat(caller, listOf(Rank.KING, Rank.TWO)),
            seat(nina, listOf(Rank.FIVE, Rank.SEVEN), claims = listOf(Claim(nina, listOf(0), listOf(Rank.FIVE)))),
            seat(don, listOf(Rank.SIX)),
        ),
        currentPlayerIndex = listOf(me, caller, nina, don).indexOf(onPlay),
        vintoCallerId = caller,
        coalitionLeaderId = null,
        drawPile = Pile((0..6).map { card(Rank.FOUR, "draw-$it") }),
        discardPile = Pile(listOf(card(discardTop, "discard-top"))),
        pendingAction = pending,
        activeTossIn = null,
        turnActions = emptyList(),
        roundActions = emptyList(),
        roundFailedAttempts = emptyList(),
        difficulty = Difficulty.MODERATE,
        rngState = 0,
    )

    private fun view(state: GameState = finalRound(), viewer: String = me): PlayerView =
        projectView(state, viewer, conferMsRemaining = 20_000L)

    private fun swap(from: String, fromPos: Int, to: String, toPos: Int) =
        Step.Swap(CardAt(from, fromPos), CardAt(to, toPos))

    // ------------------------------------------------------------------ the board

    @Test
    fun everyFinalRoundTableCarriesTheWayIntoTheBoard() {
        val window = tableFor(view(), plan = null)
        assertNull(window.board, "the board was drawn as a fixture rather than a mode")
        val summary = assertNotNull(window.planSummary, "a member has no way to open the board")
        assertEquals(0, summary.lanesSet)
        assertEquals(3, summary.lanes)
        assertEquals(Move.Ask(Question.ThePlan), summary.open)

        val plan = CoalitionPlan(
            lanes = listOf(Lane(nina, Step.TakeTheDiscard)),
            agreed = listOf(nina),
            editedBy = nina,
        )
        val standing = assertNotNull(tableFor(view(), plan = plan).planSummary)
        assertEquals(1, standing.lanesSet)
        assertEquals(1, standing.agreed)
        assertEquals(false, standing.mine)
    }

    @Test
    fun aMemberSeesOneLanePerTurnInTurnOrderEvenBeforeAnythingIsPlanned() {
        val opened = tableFor(view(), question = Question.ThePlan, plan = null)
        assertEquals(Ask.ThePlan, opened.prompt)
        val board = assertNotNull(opened.board, "a member has no board to plan on")

        assertEquals(listOf(Speaker.Named("Bot3"), Speaker.Named("Bot4"), Speaker.You), board.lanes.map { it.who })
        assertTrue(board.lanes.all { it.step == null }, "an empty board had steps on it")
        assertTrue(board.lanes.all { it.move != null }, "an empty lane cannot be tapped to start planning")
        assertTrue(opened.choices.none { it.label == Label.Agree }, "nothing on the board, nothing to agree to")
        assertTrue(opened.choices.any { it.label == Label.Back }, "no way to close the board")
    }

    @Test
    fun theCallerReadsTheBoardAndTapsNothing() {
        assertNull(tableFor(view(viewer = caller), plan = null).planSummary, "the caller was offered an empty board")

        val plan = CoalitionPlan(lanes = listOf(Lane(nina, Step.TakeTheDiscard)), agreed = listOf(me), editedBy = me)
        val opened = tableFor(view(viewer = caller), question = Question.ThePlan, plan = plan)
        val board = assertNotNull(opened.board, "the caller cannot see the plan")
        assertTrue(board.lanes.all { it.move == null }, "the caller could edit the coalition's plan")
        assertTrue(
            opened.choices.none { it.label == Label.Agree },
            "the caller was asked to agree to the plan against them",
        )
        assertEquals(StepLine.TakeTheDiscard, board.lanes.first { it.who == Speaker.Named("Bot3") }.step)
    }

    @Test
    fun aLockedLaneAndTheTurnInProgressCannotBeTapped() {
        val plan = CoalitionPlan(
            lanes = listOf(Lane(nina, Step.TakeTheDiscard, locked = true), Lane(don, Step.Declare(Rank.KING))),
            agreed = listOf(me),
            editedBy = me,
        )
        val board = assertNotNull(
            tableFor(view(finalRound(onPlay = don)), question = Question.ThePlan, plan = plan).board,
        )

        assertNull(board.lanes.first { it.who == Speaker.Named("Bot3") }.move, "a locked lane was tappable")
        assertTrue(board.lanes.first { it.who == Speaker.Named("Bot3") }.locked)
        assertNull(board.lanes.first { it.who == Speaker.Named("Bot4") }.move, "the turn in progress was tappable")
        assertNotNull(board.lanes.first { it.who == Speaker.You }.move, "a later lane was frozen too")
    }

    @Test
    fun agreeingIsOfferedUntilYouHave() {
        val plan = CoalitionPlan(
            lanes = listOf(Lane(nina, Step.TakeTheDiscard)),
            agreed = listOf(nina),
            editedBy = nina,
        )
        val before = tableFor(view(), question = Question.ThePlan, plan = plan)
        assertEquals(Move.Agree(true), before.choices.first { it.label == Label.Agree }.move)
        assertEquals(listOf(true, false, false), assertNotNull(before.board).nods.map { it.agreed })

        val after = tableFor(view(), question = Question.ThePlan, plan = plan.copy(agreed = listOf(nina, me)))
        assertTrue(after.choices.none { it.label == Label.Agree }, "asked to agree twice")
        assertTrue(assertNotNull(after.board).nods.first { it.who == Speaker.You }.agreed)
    }

    // ------------------------------------------------------------------ the composer

    @Test
    fun tappingALaneOpensTheComposerForThatSeat() {
        val board = assertNotNull(tableFor(view(), question = Question.ThePlan).board)
        val tap = assertIs<Move.Ask>(board.lanes.first { it.who == Speaker.Named("Bot3") }.move)
        assertEquals(Question.Planning(nina), tap.question)

        val composer = tableFor(view(), question = Question.Planning(nina))
        assertEquals(Ask.WhatShouldTheyDo(Speaker.Named("Bot3")), composer.prompt)
        assertTrue(composer.choices.any { it.label == Label.PlanASwap })
        assertTrue(composer.choices.any { it.label == Label.PlanADeclare })
        assertTrue(
            composer.choices.none { it.label == Label.PlanTakeTheDiscard },
            "a three on the pile is nothing to take",
        )
        assertTrue(composer.choices.none { it.label == Label.ClearLane }, "nothing to clear yet")
    }

    @Test
    fun aSwapIsTwoTapsOnTwoHandsAndNeverOnTheCallers() {
        // The person has read their own second card, so it is on the palette beside Nina's
        // claimed first; nothing else about their hand has been said.
        val read = view(
            finalRound().let { s ->
                s.copy(players = s.players.map { p -> if (p.id == me) p.copy(knownCardPositions = listOf(1)) else p })
            },
        )
        val first = tableFor(read, question = Question.Planning(nina, StepKind.SWAP))
        assertEquals(Ask.ChooseTwoFromDifferentPlayers, first.prompt)
        assertTrue(first.taps.keys.none { it.playerId == caller }, "the caller's cards were on offer")
        assertTrue(first.taps.keys.any { it.playerId == nina } && first.taps.keys.any { it.playerId == me })

        val pickNina = assertIs<Move.Ask>(first.taps.getValue(CardRef(nina, 0)))
        val second = tableFor(read, question = pickNina.question)
        assertTrue(second.taps.keys.none { it.playerId == nina }, "the same hand was offered for the second card")
        assertEquals(1, second.aim?.first?.slot, "the first card is not shown in the aim column")

        val done = assertIs<Move.Plan>(second.taps.getValue(CardRef(me, 1)))
        val edit = assertIs<PlanEdit.SetLane>(done.edit)
        assertEquals(nina, edit.seat)
        val step = assertIs<Step.Swap>(edit.step)
        assertEquals(
            CardAt(nina, 0, Claim(nina, listOf(0), listOf(Rank.FIVE))),
            step.from,
            "the step is not anchored to the claim",
        )
        assertEquals(CardAt(me, 1, null), step.to)
    }

    @Test
    fun aDeclareIsOneRankOffTheRail() {
        val table = tableFor(view(), question = Question.Planning(don, StepKind.DECLARE))
        assertEquals(Ask.WhichRankShouldTheyDeclare(Speaker.Named("Bot4")), table.prompt)
        val king = assertIs<Move.Plan>(table.ranks.first { it.rank == Rank.KING }.move)
        assertEquals(PlanEdit.SetLane(don, Step.Declare(Rank.KING)), king.edit)
        // Nina's five is the one rank the table knows a coalition hand to hold; the rest are
        // there, muted, the way a King's own rail draws a rank with nothing to do.
        assertTrue(!table.ranks.first { it.rank == Rank.FIVE }.muted, "a rank the coalition holds is muted")
        assertTrue(table.ranks.first { it.rank == Rank.KING }.muted, "a rank nobody is known to hold is not muted")
    }

    @Test
    fun thePaletteIsWhatHasBeenSaidAndWhatYouHaveRead() {
        // Nina's first card is claimed; her second is not, and nothing has been said about the
        // person's cards or Don's — so the only card a swap may name is Nina's first and, once
        // the person has read one of their own, that one. Saying what a card is puts it on the
        // palette, which is what makes declaring worth doing (design D7).
        val nothingRead = tableFor(view(), question = Question.Planning(nina, StepKind.SWAP))
        assertEquals(setOf(CardRef(nina, 0)), nothingRead.taps.keys, "an unspoken card was on the palette")

        val read = view(
            finalRound().let { s ->
                s.copy(players = s.players.map { p -> if (p.id == me) p.copy(knownCardPositions = listOf(1)) else p })
            },
        )
        val mine = tableFor(read, question = Question.Planning(nina, StepKind.SWAP))
        assertEquals(
            setOf(CardRef(nina, 0), CardRef(me, 1)),
            mine.taps.keys,
            "a card of your own you have read is not on the palette",
        )
    }

    @Test
    fun takingTheDiscardIsOfferedOnlyWhenThereIsAnActionToTakeAndClearOnlyWhenThereIsALane() {
        val plan = CoalitionPlan(
            lanes = listOf(Lane(nina, Step.Declare(Rank.KING))),
            agreed = listOf(me),
            editedBy = me,
        )
        val table = tableFor(view(finalRound(discardTop = Rank.JACK)), question = Question.Planning(nina), plan = plan)

        val take = assertIs<Move.Plan>(table.choices.first { it.label == Label.PlanTakeTheDiscard }.move)
        assertEquals(PlanEdit.SetLane(nina, Step.TakeTheDiscard), take.edit)
        val clear = assertIs<Move.Plan>(table.choices.first { it.label == Label.ClearLane }.move)
        assertEquals(PlanEdit.ClearLane(nina), clear.edit)
    }

    // ------------------------------------------------------------------ the readout and the decay

    @Test
    fun theOpenBoardSaysWhereThePlanLeavesTheRoundAndTheFeltLineSaysTheVerdict() {
        // The caller's two cards are unspoken, so the caller's believed total is nothing and
        // both are unseen. A readout that stated "our best against their 0" as a fact would be
        // lying by omission, which is why the outcome carries how much is unseen.
        val plan = CoalitionPlan(
            lanes = listOf(Lane(nina, Step.TakeTheDiscard)),
            agreed = listOf(nina),
            editedBy = nina,
        )
        val opened = tableFor(view(), question = Question.ThePlan, plan = plan)

        val outcome = assertNotNull(assertNotNull(opened.board).outcome, "the board says nothing about the outcome")
        assertEquals(2, outcome.unseen, "the caller's unspoken cards are not counted as unseen")
        assertEquals(
            outcome,
            assertNotNull(tableFor(view(), plan = plan).planSummary).outcome,
            "the felt line has no verdict",
        )
        assertNull(tableFor(view(), plan = null).planSummary?.outcome, "an empty board was given a verdict")
    }

    @Test
    fun aStepBuiltOnAClaimTheRevealContradictedIsMarkedBrokenAndSaidSo() {
        // Nina said her first card is a five; a King's wrong name turned it over as a nine. The
        // swap on the board was built on the five, so it is built on nothing — and the rail says
        // that is the game working, not a mistake (design D9).
        val nineNotFive = PublicReveal(nina, 0, Card("nina-c0", Rank.NINE, 9, played = false, actionText = null))
        val anchored = CardAt(nina, 0, Claim(nina, listOf(0), listOf(Rank.FIVE)))
        val plan = CoalitionPlan(
            lanes = listOf(Lane(me, Step.Swap(CardAt(me, 1), anchored))),
            agreed = listOf(me),
            editedBy = me,
        )

        val sound = tableFor(view(), question = Question.ThePlan, plan = plan)
        assertEquals(StepHealth.LIVE, assertNotNull(sound.board).lanes.first { it.who == Speaker.You }.health)
        assertEquals(Detail.APlanIsASuggestion, sound.detail)

        val broken = tableFor(view(), question = Question.ThePlan, plan = plan, reveals = listOf(nineNotFive))
        assertEquals(StepHealth.BROKEN, assertNotNull(broken.board).lanes.first { it.who == Speaker.You }.health)
        assertEquals(Detail.AClaimWasWrong, broken.detail, "a broken step was not explained")
    }

    @Test
    fun thePlanCanBeWatchedOnceItHasAStepToShow() {
        // Watching beats reading (design D8), and there is nothing to watch on an empty board.
        assertNull(assertNotNull(tableFor(view(), question = Question.ThePlan).board).rehearse)

        val plan = CoalitionPlan(
            lanes = listOf(Lane(nina, Step.TakeTheDiscard)),
            agreed = listOf(nina),
            editedBy = nina,
        )
        assertEquals(
            Move.Rehearse,
            assertNotNull(tableFor(view(), question = Question.ThePlan, plan = plan).board).rehearse,
        )
    }

    // ------------------------------------------------------------------ sheds

    @Test
    fun aShedIsOneRankOffTheRailInTheViewersOwnNameAndOnlyItsOwnerTakesItBack() {
        val opened = tableFor(view(), question = Question.ThePlan)
        assertTrue(opened.choices.any { it.label == Label.PlanAShed }, "no way to say what you will throw in")

        val shedding = tableFor(view(), question = Question.Shedding)
        assertEquals(Ask.WhichRankWillYouThrowIn, shedding.prompt)
        val seven = assertIs<Move.Plan>(shedding.ranks.first { it.rank == Rank.SEVEN }.move)
        assertEquals(PlanEdit.AddShed(Shed(me, Rank.SEVEN)), seven.edit, "a shed in somebody else's name")

        val plan = CoalitionPlan(
            sheds = listOf(Shed(me, Rank.SEVEN), Shed(nina, Rank.KING)),
            agreed = listOf(me),
            editedBy = me,
        )
        val board = assertNotNull(tableFor(view(), question = Question.ThePlan, plan = plan).board)
        assertNotNull(board.sheds.first { it.who == Speaker.You }.move, "the viewer cannot take back their own shed")
        assertNull(board.sheds.first { it.who == Speaker.Named("Bot3") }.move, "the viewer could take back Nina's shed")
    }

    @Test
    fun theShedsRiskIsSharperForTheHandTheCoalitionIsPushing() {
        // Don holds one unspoken card and everybody else two, so as far as the table has been
        // told his is the lowest hand — the one the coalition is pushing — and mine is not.
        val mine = tableFor(view(), question = Question.Shedding)
        assertEquals(Detail.ShedRisk(pushed = false), mine.detail)

        val dons = tableFor(view(viewer = don), question = Question.Shedding)
        assertEquals(
            Detail.ShedRisk(pushed = true),
            dons.detail,
            "the lowest hand was not warned it is the one being pushed",
        )
    }

    @Test
    fun aLaneCanPutDownACardTheTableKnowsForATeammateToThrowInOn() {
        // 3.14's other half: the proposal that sets a shed up. Nina has said her first card is a
        // five, so that card — and only that card — can be put down from her lane.
        val menu = tableFor(view(), question = Question.Planning(nina))
        assertTrue(menu.choices.any { it.label == Label.PlanAPutDown }, "no way to plan a put-down")

        val composer = tableFor(view(), question = Question.Planning(nina, StepKind.PUT_DOWN))
        assertEquals(setOf(CardRef(nina, 0)), composer.taps.keys, "a card nobody could name was on offer")
        val step = ((composer.taps[CardRef(nina, 0)] as Move.Plan).edit as PlanEdit.SetLane).step
        assertEquals(CardAt(nina, 0), (step as Step.PutDown).card.copy(anchor = null))

        val plan = CoalitionPlan(lanes = listOf(Lane(nina, step)), agreed = listOf(nina), editedBy = nina)
        val board = assertNotNull(tableFor(view(), question = Question.ThePlan, plan = plan).board)
        assertEquals(
            StepLine.PutDown(Speaker.Named("Bot3"), 1, Rank.FIVE),
            board.lanes.first { it.who == Speaker.Named("Bot3") }.step,
        )

        // Don has said nothing about his hand, so there is nothing of his to put down.
        val dons = tableFor(view(), question = Question.Planning(don))
        assertTrue(dons.choices.none { it.label == Label.PlanAPutDown }, "a put-down of a mystery was offered")
    }

    @Test
    fun whatALaneOwnerWouldRatherDoSitsBesideTheStepAndOneTapPutsItOnTheBoard() {
        // 3.13, as a person would: the alternative is read out beside the step, and tapping it
        // is an ordinary edit by whoever taps — not a bot writing over anybody.
        val set = swap(nina, 0, don, 0)
        val rather = swap(nina, 1, don, 0)
        val plan = CoalitionPlan(
            lanes = listOf(Lane(nina, set, suggestion = rather)),
            agreed = listOf(nina),
            editedBy = me,
        )

        val board = assertNotNull(tableFor(view(), question = Question.ThePlan, plan = plan).board)
        val lane = board.lanes.first { it.who == Speaker.Named("Bot3") }
        assertEquals(StepLine.Swap(Speaker.Named("Bot3"), 2, Speaker.Named("Bot4"), 1), lane.suggestion)
        assertEquals(Move.Plan(PlanEdit.SetLane(nina, rather)), lane.useSuggestion)

        // Once that turn has started the lane is closed, suggestion included.
        val locked = plan.copy(lanes = listOf(Lane(nina, set, locked = true, suggestion = rather)))
        val closed = assertNotNull(tableFor(view(), question = Question.ThePlan, plan = locked).board)
        assertNull(closed.lanes.first { it.who == Speaker.Named("Bot3") }.useSuggestion)
    }

    // ------------------------------------------------------------------ the viewer's turn

    @Test
    fun yourOwnLaneIsWrittenUnderThePromptOnYourTurn() {
        val plan = CoalitionPlan(lanes = listOf(Lane(me, swap(me, 1, nina, 0))), agreed = listOf(nina), editedBy = nina)
        val table = tableFor(projectView(finalRound(onPlay = me), me), plan = plan)

        assertEquals(Ask.YourTurn, table.prompt)
        assertEquals(
            Detail.ThePlanAsksYouTo(StepLine.Swap(Speaker.You, 2, Speaker.Named("Bot3"), 1)),
            table.detail,
        )
        assertTrue(table.choices.none { it.label == Label.DoAsPlanned }, "a swap was pre-armed with no Jack in hand")
    }

    @Test
    fun aDrawThatBeatsThePlanOffersKeepingItInstead() {
        // 3.11: the plan is a suggestion, and a Joker in hand does more for the coalition's
        // lowest hand than the swap the lane asks for — so the rail says so and offers the keep
        // first, with the plan's own step still there to do.
        val plan = CoalitionPlan(
            lanes = listOf(Lane(me, swap(nina, 1, don, 0))),
            agreed = listOf(nina),
            editedBy = nina,
        )
        fun drew(rank: Rank) = projectView(
            finalRound(
                onPlay = me,
                pending = game.vinto.shapes.PendingAction(
                    card = card(rank, "drawn"),
                    playerId = me,
                    actionPhase = game.vinto.shapes.ActionPhase.CHOOSING_ACTION,
                    from = game.vinto.shapes.PendingCardOrigin.DRAWING,
                    targets = emptyList(),
                ),
                subPhase = GameSubPhase.CHOOSING,
            ),
            me,
        )

        val joker = tableFor(drew(Rank.JOKER), plan = plan)
        assertEquals(Detail.YourDrawBeatsThePlan(Rank.JOKER, 0), joker.detail)
        assertEquals(Label.KeepItInstead, joker.choices.first().label, "the better draw was not offered first")
        assertEquals(Move.Ask(Question.WhichSlot), joker.choices.first().move)

        val ten = tableFor(drew(Rank.TEN), plan = plan)
        assertEquals(
            Detail.ThePlanAsksYouTo(StepLine.Swap(Speaker.Named("Bot3"), 2, Speaker.Named("Bot4"), 1)),
            ten.detail,
        )
        assertTrue(ten.choices.none { it.label == Label.KeepItInstead }, "a worse draw was offered over the plan")
    }

    @Test
    fun takingTheDiscardIsPreArmedWhenTheActionCardIsThere() {
        val plan = CoalitionPlan(lanes = listOf(Lane(me, Step.TakeTheDiscard)), agreed = listOf(nina), editedBy = nina)
        val table = tableFor(projectView(finalRound(onPlay = me, discardTop = Rank.JACK), me), plan = plan)

        val first = table.choices.first()
        assertEquals(Label.DoAsPlanned, first.label, "the planned move is not first")
        assertEquals(
            GameAction.PlayDiscard(game.vinto.shapes.PlayerIdPayload(me)),
            assertIs<Move.Send>(first.move).action,
        )
        assertTrue(table.choices.any { it.label == Label.DrawCard }, "pre-arming narrowed the turn")
    }
}
