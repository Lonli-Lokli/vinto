package game.vinto.client

import game.vinto.engine.PlayerView
import game.vinto.engine.PublicReveal
import game.vinto.engine.projectView
import game.vinto.shapes.Card
import game.vinto.shapes.CardAt
import game.vinto.shapes.Claim
import game.vinto.shapes.CoalitionPlan
import game.vinto.shapes.Difficulty
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
        // On the viewer's own turn, which is the third: "how should I create plan myself" is
        // answered by landing on it. The stops and the plates are the way to the other two.
        assertEquals(Move.Ask(Question.ThePlan(at = 3)), summary.open)

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
        val opened = tableFor(view(), question = Question.ThePlan(), plan = null)
        // The prompt names the turn being read rather than the plan as a whole: the rail's job
        // in plan mode is the *selected* turn's words, since the plan itself is on the felt.
        assertIs<Ask.WhatShouldTheyDo>(opened.prompt, "the rail does not say whose turn is being read")
        val board = assertNotNull(opened.board, "a member has no board to plan on")

        assertEquals(listOf(Speaker.Named("Bot3"), Speaker.Named("Bot4"), Speaker.You), board.lanes.map { it.who })
        assertTrue(board.lanes.all { it.step == null }, "an empty board had steps on it")
        assertNotNull(board.building?.composer, "an empty turn cannot be composed at all")
        assertTrue(opened.choices.none { it.label == Label.Agree }, "nothing on the board, nothing to agree to")
        // No "Back" among them: the switch in the header is the plan's one way in and out, and
        // a second control that closes it is a second thing to learn.
        assertTrue(opened.choices.none { it.label == Label.Back }, "the plan grew a second way out")
    }

    @Test
    fun theCallerReadsTheBoardAndTapsNothing() {
        assertNull(tableFor(view(viewer = caller), plan = null).planSummary, "the caller was offered an empty board")

        val plan = CoalitionPlan(lanes = listOf(Lane(nina, Step.TakeTheDiscard)), agreed = listOf(me), editedBy = me)
        val opened = tableFor(view(viewer = caller), question = Question.ThePlan(), plan = plan)
        val board = assertNotNull(opened.board, "the caller cannot see the plan")
        assertTrue(board.lanes.all { it.composer == null }, "the caller could edit the coalition's plan")
        assertTrue(
            opened.choices.none { it.label == Label.Agree },
            "the caller was asked to agree to the plan against them",
        )
        assertEquals(StepLine.TakeTheDiscard, board.lanes.first { it.who == Speaker.Named("Bot3") }.step)
    }

    @Test
    fun aPlayedTurnCannotBeComposedAndTheTurnInProgressCan() {
        val plan = CoalitionPlan(
            lanes = listOf(Lane(nina, Step.TakeTheDiscard, locked = true), Lane(don, Step.Bin)),
            agreed = listOf(me),
            editedBy = me,
        )
        val here = view(finalRound(onPlay = don))

        // Parked on each page in turn, because a composer belongs to the turn being read and to
        // no other — three of them at once would be three ways to edit one plan.
        fun composerAt(turn: Int) =
            assertNotNull(tableFor(here, question = Question.ThePlan(at = turn + 1), plan = plan).board)
                .lanes[turn]
                .composer

        assertNull(composerAt(0), "a played lane could be composed")
        assertNotNull(composerAt(1), "the turn in progress was frozen")
        assertNotNull(composerAt(2), "a later turn was frozen too")
    }

    @Test
    fun agreeingIsOfferedUntilYouHave() {
        val plan = CoalitionPlan(
            lanes = listOf(Lane(nina, Step.TakeTheDiscard)),
            agreed = listOf(nina),
            editedBy = nina,
        )
        val before = tableFor(view(), question = Question.ThePlan(), plan = plan)
        assertEquals(Move.Agree(true), before.choices.first { it.label == Label.Agree }.move)
        assertEquals(listOf(true, false, false), assertNotNull(before.board).nods.map { it.agreed })

        val after = tableFor(view(), question = Question.ThePlan(), plan = plan.copy(agreed = listOf(nina, me)))
        assertTrue(after.choices.none { it.label == Label.Agree }, "asked to agree twice")
        assertTrue(assertNotNull(after.board).nods.first { it.who == Speaker.You }.agreed)
    }

    // ------------------------------------------------------------------ the composer

    /** A Jack lying unplayed on the pile, and the first turn taking it: the one trade a blind draw cannot make. */
    private fun takingTheJack() = CoalitionPlan(lanes = listOf(Lane(nina, opening = Opening.TAKE_THE_DISCARD)))

    @Test
    fun aTurnIsComposedByCarryingACardAndTheCallersAreNeverOnOffer() {
        // The Jack's own gesture, on the felt: this card goes there. The caller's cards are not
        // a source and not a destination — the coalition may not touch them, and the door would
        // refuse the step anyway, so they are simply never lit.
        val read = view(
            finalRound(discardTop = Rank.JACK).let { s ->
                s.copy(players = s.players.map { p -> if (p.id == me) p.copy(knownCardPositions = listOf(1)) else p })
            },
        )
        val composer = assertNotNull(
            assertNotNull(
                tableFor(read, question = Question.ThePlan(), plan = takingTheJack()).board,
            ).lanes[0].composer,
        )

        assertTrue(composer.sources.none { it.playerId == caller }, "the caller's cards were on offer")
        assertTrue(
            composer.drops.values.none { drops ->
                drops.keys.filterIsInstance<PlanTarget.Card>().any { it.ref.playerId == caller }
            },
            "a card could be carried onto the caller's hand",
        )

        val onto = PlanTarget.Card(CardRef(me, 1))
        val edit = assertIs<PlanEdit.SetLane>(assertNotNull(composer.drops[CardRef(nina, 0)]?.get(onto)).edit)
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
    fun theSameSwapComesOutOfTouchingTwoCardsAsOutOfCarryingOne() {
        // The non-dragging path is the same edit reached another way, not a reduced one (design
        // D5): a screen reader, a keyboard and a switch device compose the identical `PlanEdit`.
        val read = view(
            finalRound(discardTop = Rank.JACK).let { s ->
                s.copy(players = s.players.map { p -> if (p.id == me) p.copy(knownCardPositions = listOf(1)) else p })
            },
        )
        val opened = Question.ThePlan()
        val plan = takingTheJack()
        val composer = assertNotNull(
            assertNotNull(tableFor(read, question = opened, plan = plan).board).lanes[0].composer,
        )
        val carried = assertNotNull(composer.drops[CardRef(nina, 0)]?.get(PlanTarget.Card(CardRef(me, 1))))

        val picked = assertIs<Move.Ask>(tableFor(read, question = opened, plan = plan).taps.getValue(CardRef(nina, 0)))
        val holding = assertIs<Question.ThePlan>(picked.question)
        val touched = tableFor(read, question = holding, plan = plan).taps.getValue(CardRef(me, 1))

        assertEquals(carried, touched, "carrying a card and touching two made different edits")
    }

    @Test
    fun aDeclareIsOneRankOffTheRail() {
        // The one step with no destination to carry a card to: a King names a *rank*.
        val table = tableFor(view(), question = Question.Naming(don, at = 2, part = Part.Own))
        assertEquals(Ask.WhichRankShouldTheyDeclare(Speaker.Named("Bot4")), table.prompt)
        val king = assertIs<Move.Plan>(table.ranks.first { it.rank == Rank.KING }.move)
        assertEquals(PlanEdit.SetLane(don, Step.Declare(Rank.KING)), king.edit)
        // Nina's five is the one rank the table knows a coalition hand to hold; the rest are
        // there, muted, the way a King's own rail draws a rank with nothing to do.
        assertTrue(!table.ranks.first { it.rank == Rank.FIVE }.muted, "a rank the coalition holds is muted")
        assertTrue(table.ranks.first { it.rank == Rank.KING }.muted, "a rank nobody is known to hold is not muted")
        assertNotNull(table.board, "naming a rank closed the plan behind it")
    }

    @Test
    fun thePaletteIsWhatHasBeenSaidAndWhatYouHaveRead() {
        // Nina's first card is claimed; her second is not, and nothing has been said about the
        // person's cards or Don's — so the only card a swap may name is Nina's first and, once
        // the person has read one of their own, that one. Saying what a card is puts it on the
        // palette, which is what makes declaring worth doing (design D7).
        val nothingRead = assertNotNull(
            assertNotNull(
                tableFor(view(finalRound(discardTop = Rank.JACK)), Question.ThePlan(), plan = takingTheJack()).board,
            ).lanes[0].composer,
        )
        assertEquals(setOf(CardRef(nina, 0)), nothingRead.drops.keys, "an unspoken card was on the palette")

        val read = view(
            finalRound(discardTop = Rank.JACK).let { s ->
                s.copy(players = s.players.map { p -> if (p.id == me) p.copy(knownCardPositions = listOf(1)) else p })
            },
        )
        val mine = assertNotNull(
            assertNotNull(
                tableFor(read, question = Question.ThePlan(), plan = takingTheJack()).board,
            ).lanes[0].composer,
        )
        assertEquals(
            setOf(CardRef(nina, 0), CardRef(me, 1)),
            mine.drops.keys,
            "a card of your own you have read is not on the palette",
        )
    }

    @Test
    fun takingTheDiscardIsADecisionOnlyWhileThereIsAnActionToTake() {
        val plan = CoalitionPlan(
            lanes = listOf(Lane(nina, Step.PutDown(CardAt(nina, 0)))),
            editedBy = me,
        )
        val table = tableFor(view(finalRound(discardTop = Rank.JACK)), question = Question.ThePlan(), plan = plan)

        // A Jack on the pile makes "draws" a decision: one touch flips it to taking the Jack.
        val draws = assertNotNull(table.board?.sentence).own.slots.first()
        assertEquals(Says.Draws, draws.says)
        assertEquals(PlanEdit.OpenLane(nina, Opening.TAKE_THE_DISCARD), assertIs<Move.Plan>(draws.open).edit)

        // A three on the pile is nothing to take, so "draws" is a fact with nothing to touch —
        // and no button stands on the rail but the way to agree.
        val bare = tableFor(view(), question = Question.ThePlan(), plan = plan)
        assertNull(assertNotNull(bare.board?.sentence).own.slots.first().open, "a question with one answer was asked")
        assertEquals(listOf(Label.Agree), bare.choices.map { it.label })
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
        val opened = tableFor(view(), question = Question.ThePlan(), plan = plan)

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

        val sound = tableFor(view(), question = Question.ThePlan(), plan = plan)
        assertEquals(StepHealth.LIVE, assertNotNull(sound.board).lanes.first { it.who == Speaker.You }.health)
        assertEquals(Detail.TouchAWord, sound.detail)

        val broken = tableFor(view(), question = Question.ThePlan(), plan = plan, reveals = listOf(nineNotFive))
        assertEquals(StepHealth.BROKEN, assertNotNull(broken.board).lanes.first { it.who == Speaker.You }.health)
        assertEquals(Detail.AClaimWasWrong, broken.detail, "a broken step was not explained")
    }

    // ------------------------------------------------------------------ throw-ins

    @Test
    fun aThrowInIsSaidOnTheTurnItLandsOnInAnybodysNameAndAnybodyTakesItBack() {
        // "+ throw in" is a word at the end of the turn's sentence; the question it opens is
        // answered on the felt, and the thrower is whoever's card is touched.
        val opened = tableFor(view(), question = Question.ThePlan())
        val offer = assertNotNull(
            opened.board?.sentence?.clauses?.last()?.slots?.firstOrNull { it.says == Says.AddThrow },
        )
        assertEquals(Move.Ask(Question.Throwing(nina, at = 1, index = 0)), offer.open)

        // Nina's turn lands nothing anybody knows, so every coalition card throws blind.
        val throwing = tableFor(view(), question = Question.Throwing(nina, at = 1, index = 0))
        assertEquals(Ask.WhichCardWillTheyThrowIn(Speaker.Named("Bot3")), throwing.prompt)
        val mine = assertIs<Move.Plan>(throwing.taps.getValue(CardRef(me, 1)))
        assertEquals(
            PlanEdit.SetTossIns(nina, listOf(TossIn(me, rank = null, card = CardAt(me, 1)))),
            mine.edit,
            "a throw in somebody else's name",
        )
        assertTrue(throwing.taps.keys.none { it.playerId == caller }, "the caller's cards were offered to throw")

        // Said, it is a clause of Nina's turn — and a part of the board like any other, which
        // any member may change (design D7a): the plan is one shared thing.
        val plan = CoalitionPlan(
            lanes = listOf(
                Lane(
                    nina,
                    Step.PutDown(CardAt(nina, 0)),
                    tossIns = listOf(
                        TossIn(me, null, card = CardAt(me, 1)),
                        TossIn(nina, null, card = CardAt(nina, 1)),
                    ),
                ),
            ),
            agreed = listOf(me),
            editedBy = me,
        )
        val sentence = assertNotNull(tableFor(view(), question = Question.ThePlan(at = 1), plan = plan).board?.sentence)
        assertEquals(
            listOf(Speaker.You, Speaker.Named("Bot3")),
            sentence.clauses.filter { it.part is Part.Throw }.map { assertIs<Says.Throws>(it.slots[0].says).who },
        )
        val ninas = tableFor(view(viewer = don), question = Question.Throwing(nina, at = 1, index = 1), plan = plan)
        assertEquals(
            PlanEdit.SetTossIns(nina, listOf(TossIn(me, null, card = CardAt(me, 1)))),
            assertIs<Move.Plan>(assertNotNull(ninas.board).answers.first { it.label == Label.RemoveThrow }.move).edit,
            "a teammate could not take a throw off the board",
        )
    }

    @Test
    fun aLaneCanPutDownAnyOfItsOwnCardsForATeammateToThrowInOn() {
        // 3.14's other half: the proposal that sets a shed up. Any of Nina's own cards may be
        // carried onto the pile from her turn — one nobody has named lands a card nobody knows.
        val composer = assertNotNull(
            assertNotNull(tableFor(view(), question = Question.PuttingDown(nina, at = 1)).board).lanes[0].composer,
        )
        assertEquals(setOf(CardRef(nina, 0), CardRef(nina, 1)), composer.drops.keys)
        val step = assertIs<Step.PutDown>(
            assertIs<PlanEdit.SetLane>(
                assertNotNull(composer.drops[CardRef(nina, 0)]?.get(PlanTarget.Discard)).edit,
            ).step,
        )
        assertEquals(CardAt(nina, 0, Claim(nina, listOf(0), listOf(Rank.FIVE))), step.card)

        val plan = CoalitionPlan(lanes = listOf(Lane(nina, step)), agreed = listOf(nina), editedBy = nina)
        val board = assertNotNull(tableFor(view(), question = Question.ThePlan(), plan = plan).board)
        assertEquals(
            StepLine.PutDown(Speaker.Named("Bot3"), 1, Rank.FIVE),
            board.lanes.first { it.who == Speaker.Named("Bot3") }.step,
        )
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

        val board = assertNotNull(tableFor(view(), question = Question.ThePlan(), plan = plan).board)
        val lane = board.lanes.first { it.who == Speaker.Named("Bot3") }
        assertEquals(StepLine.Swap(Speaker.Named("Bot3"), 2, Speaker.Named("Bot4"), 1), lane.suggestion)
        assertEquals(Move.Plan(PlanEdit.SetLane(nina, rather)), lane.useSuggestion)

        // Once that turn has started the lane is closed, suggestion included.
        val locked = plan.copy(lanes = listOf(Lane(nina, set, locked = true, suggestion = rather)))
        val closed = assertNotNull(tableFor(view(), question = Question.ThePlan(), plan = locked).board)
        assertNull(closed.lanes.first { it.who == Speaker.Named("Bot3") }.useSuggestion)
    }

    // ------------------------------------------------------------------ the viewer's turn

    @Test
    fun yourOwnTurnIsWrittenUnderThePromptOnYourTurnAndNothingIsArmed() {
        // The plan is information: one row saying what the coalition agreed, and the ordinary
        // buttons above it. No "do as planned", no re-plan, nothing that insists.
        val plan = CoalitionPlan(
            lanes = listOf(Lane(me, Step.PutDown(CardAt(me, 1)))),
            agreed = listOf(nina),
            editedBy = nina,
        )
        val table = tableFor(projectView(finalRound(onPlay = me), me), plan = plan)

        assertEquals(Ask.YourTurn, table.prompt)
        assertEquals(
            listOf(Says.Draws, Says.PutsDown(CardWord(Speaker.You, "Human1", 2, null))),
            assertNotNull(table.planLine).says,
        )
        assertTrue(table.choices.all { it.move is Move.Send }, "a plan control was on the live rail: ${table.choices}")
        assertEquals(listOf(Label.DrawCard), table.choices.map { it.label }, "the ordinary turn's buttons changed")
    }

    @Test
    fun aGoodDrawIsThePlayersToJudgeAndThePlanSaysNothingAboutIt() {
        // A Joker in hand does more for the coalition than the swap the lane asks for. At a real
        // table the person on play would see that themselves; the app does not grade the draw
        // against the plan, and the rail offers exactly what any turn offers.
        val plan = CoalitionPlan(
            lanes = listOf(Lane(me, swap(nina, 1, don, 0))),
            agreed = listOf(nina),
            editedBy = nina,
        )
        val drew = projectView(
            finalRound(
                onPlay = me,
                pending = game.vinto.shapes.PendingAction(
                    card = card(Rank.JOKER, "drawn"),
                    playerId = me,
                    actionPhase = game.vinto.shapes.ActionPhase.CHOOSING_ACTION,
                    from = game.vinto.shapes.PendingCardOrigin.DRAWING,
                    targets = emptyList(),
                ),
                subPhase = GameSubPhase.CHOOSING,
            ),
            me,
        )
        val joker = tableFor(drew, plan = plan)
        // The Joker is face up: the row says so, and says what the coalition planned after it.
        assertEquals(
            listOf(
                Says.Drew(Rank.JOKER),
                Says.PlaysIt,
                Says.Trade(
                    CardWord(Speaker.Named("Bot3"), "Bot3", 2, null),
                    CardWord(Speaker.Named("Bot4"), "Bot4", 1, null),
                    swap = true,
                ),
            ),
            assertNotNull(joker.planLine).says,
        )
        assertTrue(
            joker.choices.all { it.move is Move.Send || it.move is Move.Ask },
            "a plan control was on the live rail",
        )
        assertTrue(joker.choices.none { it.move is Move.Plan || it.move is Move.Agree })
    }

    @Test
    fun takingTheDiscardIsOfferedAsItAlwaysIsWhetherOrNotThePlanSaysSo() {
        val plan = CoalitionPlan(lanes = listOf(Lane(me, Step.TakeTheDiscard)), agreed = listOf(nina), editedBy = nina)
        val table = tableFor(projectView(finalRound(onPlay = me, discardTop = Rank.JACK), me), plan = plan)

        assertEquals(setOf(Label.DrawCard, Label.UseFromPile(Rank.JACK)), table.choices.map { it.label }.toSet())
        assertEquals(listOf(Says.Takes(Rank.JACK)), assertNotNull(table.planLine).says)
    }
}
