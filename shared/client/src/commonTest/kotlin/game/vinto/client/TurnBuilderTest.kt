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
import game.vinto.shapes.PendingAction
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
 * Building a turn by answering the next question about it — and building anybody's.
 *
 * Reported from a phone: *"how should I create plan myself eg to show that I want to toss now,
 * then swap with my jack and move some cards? And how to design moves for them?"* What is held
 * here: the rail asks the next open question of the turn being built, and it is asked only of a
 * card face up to play with — the pile's, or the one drawn; a card the turn names is touched on
 * the felt, and carrying it is the same edit; the called card's action rides on the put-down step
 * and is read, played, priced and followed like any other; and every coalition seat is a way to
 * its own turn. The grammar itself is `PlanAsTalkTest`.
 */
class TurnBuilderTest {

    private val me = "human-1"
    private val caller = "bot-2"
    private val nina = "bot-3"
    private val don = "bot-4"

    /** The coalition plays Nina, then Don, then me; a page is a turn. */
    private val ninasPage = 1
    private val myPage = 3

    private val bot3 = Speaker.Named("Bot3")
    private val bot4 = Speaker.Named("Bot4")

    /**
     * The two turns before mine, settled with the emptiest decision there is.
     *
     * The plan is read front to back and the page after a turn nobody has decided is closed
     * (`Transport.reach`), so a fixture that sets only my turn would be clamped back to Nina's
     * and every test here would be about a turn it never named.
     */
    private fun before() = listOf(Lane(nina, Step.Bin), Lane(don, Step.Bin))

    private fun card(rank: Rank, id: String) = Card(
        id = id,
        rank = rank,
        value = getCardValue(rank),
        played = false,
        actionText = getCardShortDescription(rank).takeIf { it.isNotEmpty() },
    )

    private fun seat(id: String, ranks: List<Rank>, said: Map<Int, Rank> = emptyMap()) = PlayerState(
        id = id,
        name = id,
        nickname = id.substringBefore('-').replaceFirstChar { it.uppercase() } + id.last(),
        isHuman = id == me,
        isBot = id != me,
        cards = ranks.mapIndexed { index, rank -> card(rank, "$id-c$index") },
        knownCardPositions = emptyList(),
        isVintoCaller = id == caller,
        coalitionWith = if (id == caller) emptyList() else listOf(me, nina, don) - id,
        claims = said.map { (position, rank) -> Claim(id, listOf(position), listOf(rank)) }.takeIf { it.isNotEmpty() },
    )

    /**
     * A final round the bot in seat two called. I have told the coalition my first card is a Jack
     * and my third a King; Nina has said her first is a five, Don that his only card is a six.
     */
    private fun finalRound(
        discardTop: Rank = Rank.THREE,
        onPlay: String = caller,
        pending: PendingAction? = null,
        subPhase: GameSubPhase = GameSubPhase.IDLE,
    ) = GameState(
        gameId = "builder",
        roundNumber = 1,
        turnNumber = 12,
        phase = GamePhase.FINAL,
        subPhase = subPhase,
        finalTurnTriggered = true,
        players = listOf(
            seat(me, listOf(Rank.JACK, Rank.TWO, Rank.KING), said = mapOf(0 to Rank.JACK, 2 to Rank.KING)),
            seat(caller, listOf(Rank.KING, Rank.TWO)),
            seat(nina, listOf(Rank.FIVE, Rank.SEVEN), said = mapOf(0 to Rank.FIVE)),
            seat(don, listOf(Rank.SIX), said = mapOf(0 to Rank.SIX)),
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

    /** The step a `Move.Plan` carries, with the anchors the composer adds stripped for comparing. */
    private fun Move?.step(): Step = assertIs<PlanEdit.SetLane>(assertIs<Move.Plan>(this).edit).step.bare()

    private fun Step.bare(): Step = when (this) {
        is Step.Swap -> Step.Swap(from.copy(anchor = null), to.copy(anchor = null))
        is Step.PutDown -> Step.PutDown(card.copy(anchor = null), guess, then?.bare())
        is Step.Declare -> Step.Declare(rank, card?.copy(anchor = null), then?.bare())
        is Step.Peek -> Step.Peek(card.copy(anchor = null), also?.copy(anchor = null))
        is Step.ForceDraw, Step.TakeTheDiscard, Step.Bin, Step.UseIt -> this
    }

    private fun Table.sentence(): TurnSentence = assertNotNull(board?.sentence, "the rail has no turn to build")

    /** The words of the sentence, clause by clause. */
    private fun Table.words(): List<List<Says>> = sentence().clauses.map { clause -> clause.slots.map { it.says } }

    private fun Table.slot(says: Says): Slot =
        assertNotNull(
            sentence().clauses.flatMap { it.slots }.firstOrNull { it.says == says },
            "no word $says in ${words()}",
        )

    /** The throw-ins a `Move.Plan` carries. */
    private fun Move?.throws(): List<TossIn> =
        assertIs<PlanEdit.SetTossIns>(assertIs<Move.Plan>(this).edit).tossIns
            .map { it.copy(then = it.then?.bare(), card = it.card?.copy(anchor = null)) }

    private fun ninas(slot: Int, rank: Rank?) = CardWord(bot3, "Bot3", slot, rank)
    private fun dons(slot: Int, rank: Rank?) = CardWord(bot4, "Bot4", slot, rank)
    private fun mine(slot: Int, rank: Rank?) = CardWord(Speaker.You, "Human1", slot, rank)

    // ------------------------------------------------------------------ the pile's card

    @Test
    fun theCardTakenOffThePileAsksWhatItsActionNeeds() {
        val taking = CoalitionPlan(lanes = listOf(Lane(nina, opening = Opening.TAKE_THE_DISCARD)))
        fun withPile(rank: Rank) = tableFor(
            view(finalRound(discardTop = rank)),
            Question.ThePlan(at = ninasPage),
            plan = taking,
        )

        // A Jack: which two, and the answer is carried on the felt.
        val jack = withPile(Rank.JACK)
        assertEquals(Ask.WhichTwoWillItSwap(Rank.JACK), jack.prompt)
        assertEquals(listOf(Says.Takes(Rank.JACK), Says.WhichTwo), jack.words().first())
        val composer = assertNotNull(jack.board?.building?.composer)
        val carried = composer.drops[CardRef(nina, 0)]?.get(PlanTarget.Card(CardRef(don, 0)))
        assertEquals(Step.Swap(CardAt(nina, 0), CardAt(don, 0)), carried.step())

        // A King: which card it points at — any coalition card, the rank comes after.
        val king = withPile(Rank.KING)
        assertEquals(Ask.WhichCardDoesTheKingPointAt, king.prompt)
        assertEquals(listOf(Says.Takes(Rank.KING), Says.WhichToPointAt), king.words().first())
        assertEquals(Step.Declare(card = CardAt(nina, 1)), king.taps.getValue(CardRef(nina, 1)).step())
        assertNull(king.taps[CardRef(caller, 0)], "the caller's card was offered to a King")

        // An Ace: who draws, and the answer is a seat.
        val ace = withPile(Rank.ACE)
        assertEquals(Ask.WhoShouldDraw, ace.prompt)
        assertEquals(listOf(nina, don, me), ace.seats.map { it.id }, "the coalition is not on offer to draw")
        assertEquals(Step.ForceDraw(don), ace.seats.first { it.id == don }.move.step())

        // A nine: which card it looks at, with one touch on somebody else's.
        val nine = withPile(Rank.NINE)
        assertEquals(Ask.WhichCardWillItLookAt(Rank.NINE), nine.prompt)
        assertEquals(Step.Peek(CardAt(don, 0)), nine.taps.getValue(CardRef(don, 0)).step())
        assertNull(nine.taps[CardRef(nina, 0)], "a nine was offered one of its own player's cards")
        assertNull(nine.taps[CardRef(caller, 0)], "the caller's card was offered to a look")
    }

    @Test
    fun aCardNobodyHasSeenHasNothingToAimAndIsNotAsked() {
        // "Play whatever it is" off the deck is a sentence with nothing to aim: no question, the
        // word plain, and the film with nothing to draw.
        val drawing = CoalitionPlan(lanes = listOf(Lane(nina, Step.UseIt, opening = Opening.DRAW)))
        val drawn = tableFor(view(finalRound(discardTop = Rank.JACK)), Question.ThePlan(at = ninasPage), plan = drawing)
        assertEquals(Ask.WhatShouldTheyDo(bot3), drawn.prompt)
        assertEquals(listOf(Says.Draws, Says.PlaysIt), drawn.words().first())
        assertNull(drawn.slot(Says.PlaysIt).open, "playing a card nobody has seen was offered as a decision")
        assertTrue(drawn.board?.answers.orEmpty().isEmpty())
    }

    // ------------------------------------------------------------------ the called card

    @Test
    fun aCalledJackAsksWhichTwoCardsItSwapsAndTheAnswerRidesOnTheSameStep() {
        val called = Step.PutDown(CardAt(me, 0), guess = Rank.JACK)
        val plan = CoalitionPlan(lanes = before() + Lane(me, called))
        val table = tableFor(view(), question = Question.ThePlan(at = myPage), plan = plan)
        assertEquals(Ask.WhichTwoWillItSwap(Rank.JACK), table.prompt)
        assertEquals(listOf(Says.Draws, Says.PutsDown(mine(1, Rank.JACK)), Says.WhichTwo), table.words().first())

        // Carrying Nina's five onto Don's six: the trade is what the call *does*, so it lands on
        // the put-down rather than replacing it.
        val composer = assertNotNull(table.board?.building?.composer)
        val carried = assertNotNull(composer.drops[CardRef(nina, 0)]?.get(PlanTarget.Card(CardRef(don, 0))))
        val whole = called.copy(then = Step.Swap(CardAt(nina, 0), CardAt(don, 0)))
        assertEquals(whole, carried.step())

        // Touching one and then the other is the identical edit (design D5).
        val picked = tableFor(view(), question = Question.ThePlan(at = myPage, picked = CardRef(nina, 0)), plan = plan)
        assertEquals<Move?>(carried, picked.taps[CardRef(don, 0)])

        // The card being put down is on the pile by the time the Jack acts: not there to move.
        assertNull(composer.drops[CardRef(me, 0)], "the card put down was offered to its own Jack")
        assertNull(composer.drops[CardRef(nina, 0)]?.get(PlanTarget.Card(CardRef(me, 0))))

        // Answered, the question goes and the turn reads whole, every card it moves marked.
        val answered = tableFor(
            view(),
            question = Question.ThePlan(at = myPage),
            plan = CoalitionPlan(lanes = before() + Lane(me, whole)),
        )
        assertEquals(Ask.WhatShouldTheyDo(Speaker.You), answered.prompt)
        assertEquals(setOf(CardRef(me, 0), CardRef(nina, 0), CardRef(don, 0)), assertNotNull(answered.board).marks)
        assertEquals(Says.Trade(ninas(1, Rank.FIVE), dons(1, Rank.SIX), swap = true), answered.words().first().last())
    }

    @Test
    fun touchingAWordOfTheSentenceReopensItsOwnQuestion() {
        val whole = Step.PutDown(CardAt(me, 0), guess = Rank.JACK, then = Step.Swap(CardAt(nina, 0), CardAt(don, 0)))
        val table = tableFor(
            view(),
            question = Question.ThePlan(at = myPage),
            plan = CoalitionPlan(lanes = before() + Lane(me, whole)),
        )

        // Which card goes out, and the trade reopens the felt aimed at what the call does.
        assertEquals(Move.Ask(Question.PuttingDown(me, myPage)), table.slot(Says.PutsDown(mine(1, Rank.JACK))).open)
        assertEquals(
            Move.Ask(Question.Aiming(me, myPage, Part.Called)),
            table.slot(Says.Trade(ninas(1, Rank.FIVE), dons(1, Rank.SIX), swap = true)).open,
        )
        // What becomes of the card is asked of its own word, and the answers are the two. Never
        // "we'll see": undeciding a turn closes every page after it, so it is not on offer —
        // a decision is changed by answering it again, not by taking it back (`doingTable`).
        val doing = tableFor(
            view(),
            question = Question.Doing(me, myPage),
            plan = CoalitionPlan(lanes = before() + Lane(me, whole)),
        )
        assertEquals(
            listOf(Label.PutACardDown, Label.LetTheCardGo),
            assertNotNull(doing.board).answers.map { it.label },
        )
        assertTrue(
            doing.board?.answers.orEmpty().none { it.move == Move.Plan(PlanEdit.ClearLane(me)) },
            "a turn could be put back to undecided, which closes the pages after it",
        )
    }

    @Test
    fun theWholeTurnIsReadOutAsOneSentence() {
        val whole = Step.PutDown(CardAt(me, 0), guess = Rank.JACK, then = Step.Swap(CardAt(nina, 0), CardAt(don, 0)))
        assertEquals(
            StepLine.PutDown(
                who = Speaker.You,
                slot = 1,
                rank = Rank.JACK,
                call = Rank.JACK,
                then = StepLine.Swap(bot3, 1, bot4, 1),
            ),
            stepLine(view(), whole),
        )
    }

    // ------------------------------------------------------------------ throw-ins

    @Test
    fun aThrowInIsAClauseOfTheTurnAndWhatItsCardDoesFollowsIt() {
        // *"You play this Jack, then I throw in my Jack and play mine"*: Nina puts down her card
        // and calls the Jack; I throw my Jack in on it and mine trades Nina's five for Don's six.
        // One turn, two clauses, every word of it touchable.
        val plan = CoalitionPlan(
            lanes = listOf(
                Lane(
                    nina,
                    Step.PutDown(CardAt(nina, 1), guess = Rank.JACK),
                    tossIns = listOf(TossIn(me, Rank.JACK, then = Step.Swap(CardAt(nina, 0), CardAt(don, 0)))),
                ),
            ),
        )
        val table = tableFor(view(), question = Question.ThePlan(at = ninasPage), plan = plan)
        val sentence = table.sentence()

        assertEquals(listOf(Part.Own, Part.Throw(0), null), sentence.clauses.map { it.part })
        val trade = Says.Trade(ninas(1, Rank.FIVE), dons(1, Rank.SIX), swap = true)
        // Nina's second card is nobody's Jack as far as the table knows, so the landing is a
        // guess and my throw cannot be vouched for — but my Jack is known, so what it does is said.
        val throwWord = assertIs<Says.Throws>(table.words()[1].first())
        assertEquals(Speaker.You, throwWord.who)
        assertEquals(Rank.JACK, throwWord.rank)
        assertEquals(mine(1, Rank.JACK), throwWord.card, "the throw does not name the card known to be the Jack")
        assertEquals(trade, table.words()[1][1])
        assertEquals(Move.Ask(Question.Throwing(nina, ninasPage, index = 0)), table.slot(throwWord).open)
        assertEquals(Move.Ask(Question.Aiming(nina, ninasPage, Part.Throw(0))), table.slot(trade).open)
        assertEquals(Says.AddThrow, sentence.says.last())
    }

    @Test
    fun aThrownJackAsksWhichTwoCardsItSwapsAndTheAnswerRidesOnTheThrow() {
        // Nina lets her draw go; I have said I will throw my Jack in on whatever lands. The next
        // open part of the turn is what my Jack trades, and the felt answers it.
        val plan = CoalitionPlan(lanes = listOf(Lane(nina, Step.Bin, tossIns = listOf(TossIn(me, Rank.JACK)))))
        val table = tableFor(view(), question = Question.ThePlan(at = ninasPage), plan = plan)

        assertEquals(Ask.WhichTwoWillItSwap(Rank.JACK), table.prompt)
        assertTrue(table.slot(Says.WhichTwo).asked)
        val composer = assertNotNull(table.board?.building?.composer)
        val carried = composer.drops[CardRef(nina, 0)]?.get(PlanTarget.Card(CardRef(don, 0)))
        assertEquals(listOf(TossIn(me, Rank.JACK, then = Step.Swap(CardAt(nina, 0), CardAt(don, 0)))), carried.throws())
        // Touching one and then the other is the identical edit (design D5).
        val picked = tableFor(
            view(),
            question = Question.ThePlan(at = ninasPage, picked = CardRef(nina, 0)),
            plan = plan,
        )
        assertEquals<Move?>(carried, picked.taps[CardRef(don, 0)])
    }

    @Test
    fun aThrownNineLooksAtOneCardWithOneTouchAndAThrownAceNamesWhoDraws() {
        val nine = CoalitionPlan(lanes = listOf(Lane(nina, Step.Bin, tossIns = listOf(TossIn(me, Rank.NINE)))))
        val looking = tableFor(view(), question = Question.ThePlan(at = ninasPage), plan = nine)
        assertEquals(Ask.WhichCardWillItLookAt(Rank.NINE), looking.prompt)
        assertEquals(
            listOf(TossIn(me, Rank.NINE, then = Step.Peek(CardAt(don, 0)))),
            looking.taps.getValue(CardRef(don, 0)).throws(),
        )
        assertNull(looking.taps[CardRef(me, 0)], "a nine was offered one of my own cards to look at")

        // A seven looks at one of the thrower's own.
        val seven = CoalitionPlan(lanes = listOf(Lane(nina, Step.Bin, tossIns = listOf(TossIn(me, Rank.SEVEN)))))
        val own = tableFor(view(), question = Question.ThePlan(at = ninasPage), plan = seven)
        assertEquals(setOf(CardRef(me, 0), CardRef(me, 1), CardRef(me, 2)), own.taps.keys)

        val ace = CoalitionPlan(lanes = listOf(Lane(nina, Step.Bin, tossIns = listOf(TossIn(me, Rank.ACE)))))
        val forcing = tableFor(view(), question = Question.ThePlan(at = ninasPage), plan = ace)
        assertEquals(Ask.WhoShouldDraw, forcing.prompt)
        assertEquals(
            listOf(TossIn(me, Rank.ACE, then = Step.ForceDraw(don))),
            forcing.seats.first { it.id == don }.move.throws(),
        )
        assertTrue(forcing.seats.none { it.id == caller }, "the caller was offered as somebody to make draw")
    }

    @Test
    fun aKingPointedAtAJackHasTheJacksOwnTradeAsItsNextQuestion() {
        // The King off the pile, pointed at my Jack: the Jack's own trade is the next open part,
        // one deeper, and the answer rides on the King.
        val jack = CoalitionPlan(
            lanes = listOf(Lane(nina, Step.Declare(Rank.JACK, CardAt(me, 0)), opening = Opening.TAKE_THE_DISCARD)),
        )
        val deeper = tableFor(view(finalRound(discardTop = Rank.KING)), Question.ThePlan(at = ninasPage), plan = jack)
        assertEquals(Ask.WhichTwoWillItSwap(Rank.JACK), deeper.prompt)
        assertEquals(
            listOf(Says.Takes(Rank.KING), Says.Points(mine(1, Rank.JACK), Rank.JACK), Says.WhichTwo),
            deeper.words().first(),
        )
        val composer = assertNotNull(deeper.board?.building?.composer)
        val carried = composer.drops[CardRef(nina, 0)]?.get(PlanTarget.Card(CardRef(don, 0)))
        assertEquals(
            Step.Declare(Rank.JACK, CardAt(me, 0), then = Step.Swap(CardAt(nina, 0), CardAt(don, 0))),
            carried.step(),
        )
    }

    @Test
    fun aThrowAlreadySaidCanBeChangedOrTakenBackFromItsOwnWord() {
        val standing = CoalitionPlan(
            lanes = listOf(
                Lane(
                    nina,
                    Step.PutDown(CardAt(nina, 0)),
                    tossIns = listOf(
                        TossIn(me, null, card = CardAt(me, 1)),
                        TossIn(don, Rank.SIX, card = CardAt(don, 0)),
                    ),
                ),
            ),
        )
        val changing = tableFor(view(), question = Question.Throwing(nina, ninasPage, index = 0), plan = standing)
        assertEquals(TableMode.PLAN, changing.mode, "changing a throw left the plan")
        // A different card in the same place keeps the order.
        assertEquals(
            listOf(TossIn(nina, null, card = CardAt(nina, 1)), TossIn(don, Rank.SIX, card = CardAt(don, 0))),
            changing.taps.getValue(CardRef(nina, 1)).throws(),
        )
        // And it can be taken back, by anybody.
        val dons = tableFor(
            view(viewer = don),
            question = Question.Throwing(nina, ninasPage, index = 0),
            plan = standing,
        )
        assertEquals(
            listOf(TossIn(don, Rank.SIX, card = CardAt(don, 0))),
            assertNotNull(dons.board).answers.first { it.label == Label.RemoveThrow }.move.throws(),
        )
        // A throw not yet said has nothing to take back.
        val adding = tableFor(view(), question = Question.Throwing(nina, ninasPage, index = 2), plan = standing)
        assertTrue(adding.board?.answers.orEmpty().none { it.label == Label.RemoveThrow })
    }

    @Test
    fun everyWordOfTheSentenceIsQuietAndTheCallersAreOnlyToBeRead() {
        val plan = CoalitionPlan(
            lanes = listOf(
                Lane(
                    nina,
                    Step.PutDown(CardAt(nina, 1), guess = Rank.JACK, then = Step.Swap(CardAt(nina, 0), CardAt(don, 0))),
                    tossIns = listOf(TossIn(me, Rank.JACK, then = Step.Swap(CardAt(me, 1), CardAt(don, 0)))),
                ),
            ),
        )
        val mine = tableFor(view(), question = Question.ThePlan(at = ninasPage), plan = plan).sentence()
        assertTrue(mine.clauses.flatMap { it.slots }.count { it.open != null } >= 5, "the sentence cannot be changed")

        val theirs = tableFor(
            view(viewer = caller),
            question = Question.ThePlan(at = ninasPage),
            plan = plan,
        ).sentence()
        // The same words, by kind — the caller addresses me by name where I am "You".
        assertEquals(
            mine.says.filterNot { it == Says.AddThrow }.map { it::class },
            theirs.says.map { it::class },
            "the caller reads a different turn",
        )
        assertTrue(theirs.clauses.flatMap { it.slots }.all { it.open == null }, "the caller was offered an edit")
    }

    // ------------------------------------------------------------------ whose turn

    @Test
    fun everyCoalitionSeatOnTheFeltIsAWayToItsOwnTurn() {
        val table = tableFor(view(), question = Question.ThePlan(at = ninasPage))
        assertEquals(listOf(nina, don, me), table.seats.map { it.id }, "the seats are not the coalition in turn order")
        assertEquals(Move.Ask(Question.ThePlan(at = 2)), table.seats[1].move)
        assertEquals(Speaker.You, table.seats[2].who)

        val plan = CoalitionPlan(lanes = listOf(Lane(nina, Step.Bin)), agreed = listOf(nina), editedBy = nina)
        // The caller has no turn to go to, and reads the plan with nothing to touch.
        assertTrue(tableFor(view(viewer = caller), question = Question.ThePlan(), plan = plan).seats.isEmpty())
        // And while the film runs nothing is a control (design D14).
        assertTrue(tableFor(view(), question = Question.ThePlan(at = 1, runningTo = 4), plan = plan).seats.isEmpty())
    }

    @Test
    fun eachStopCarriesTheSeatWhoseTurnItIsAndTheLastIsWhereThePlanLands() {
        val stops = assertNotNull(tableFor(view(), question = Question.ThePlan()).board).transport.stops
        assertEquals(listOf("Bot3", "Bot4", "Human1", null), stops.map { it.nickname })
    }

    // ------------------------------------------------------------------ the call, everywhere else

    @Test
    fun aCalledJacksSwapPlaysInTheSameTurnOfTheFilm() {
        val whole = Step.PutDown(CardAt(me, 0), guess = Rank.JACK, then = Step.Swap(CardAt(nina, 0), CardAt(don, 0)))
        val film = rehearsal(view(), CoalitionPlan(lanes = listOf(Lane(me, whole))))

        assertEquals(3, film.turns)
        val frame = assertNotNull(film.frames[2], "my turn had nothing to draw")
        assertEquals(3, frame.turn, "the frame does not know which turn it plays")
        val moves = frame.scenes.flatten().filterIsInstance<Beat.Move>()
        assertTrue(moves.any { it.to == Anchor.Discard }, "the put-down did not reach the pile: $moves")
        assertTrue(
            moves.any { it.from == Anchor.Seat(nina, 0) && it.to == Anchor.Seat(don, 0) } &&
                moves.any { it.from == Anchor.Seat(don, 0) && it.to == Anchor.Seat(nina, 0) },
            "the Jack's swap was not played after the call: $moves",
        )
    }

    @Test
    fun aCalledJacksSwapFollowsItsCardsAndBreaksWithThem() {
        val ninasFive = Claim(nina, listOf(0), listOf(Rank.FIVE))
        val whole = Step.PutDown(
            CardAt(me, 0, Claim(me, listOf(0), listOf(Rank.JACK))),
            guess = Rank.JACK,
            then = Step.Swap(CardAt(nina, 0, ninasFive), CardAt(don, 0, Claim(don, listOf(0), listOf(Rank.SIX)))),
        )
        val plan = CoalitionPlan(lanes = listOf(Lane(me, whole)))

        assertEquals(StepHealth.LIVE, readPlan(view(), plan, emptyList()).health.single())
        // Nina's first card turns out to be a ten: the swap the call was for stands on nothing.
        val proved = PublicReveal(nina, 0, card(Rank.TEN, "revealed"))
        assertEquals(StepHealth.BROKEN, readPlan(view(), plan, listOf(proved)).health.single())
    }

    @Test
    fun aCalledJacksSwapCountsWhereThePlanLands() {
        // Don's six for Nina's five leaves Don on five, which is the coalition's best hand once
        // the Jack has done its work — and six without it.
        val called = Step.PutDown(CardAt(me, 0), guess = Rank.JACK)
        val silent = assertNotNull(planOutcome(view(), CoalitionPlan(lanes = listOf(Lane(me, called)))))
        val swapped = assertNotNull(
            planOutcome(
                view(),
                CoalitionPlan(lanes = listOf(Lane(me, called.copy(then = Step.Swap(CardAt(nina, 0), CardAt(don, 0)))))),
            ),
        )
        assertEquals(6, silent.ourBest)
        assertEquals(5, swapped.ourBest)
        assertEquals(
            listOf(nina, don, me),
            swapped.hands.map { it.seat },
            "the hands are not the coalition in turn order",
        )
    }

    // ------------------------------------------------------------------ the live rail

    @Test
    fun theLiveRailKeepsItsOwnButtonsAndCarriesThePlanAsOneRow() {
        // The plan is talk made visible, not a control: on my turn the rail is the ordinary
        // turn's — draw, or take the pile — and the plan is one row under the prompt saying
        // what the coalition agreed, throw-ins included. Nothing is armed and nothing insists.
        val whole = Step.PutDown(CardAt(me, 0), guess = Rank.JACK, then = Step.Swap(CardAt(nina, 0), CardAt(don, 0)))
        val plan = CoalitionPlan(
            lanes = listOf(Lane(me, whole, tossIns = listOf(TossIn(nina, Rank.JACK)))),
            agreed = listOf(nina),
            editedBy = nina,
        )
        val table = tableFor(
            projectView(finalRound(onPlay = me, discardTop = Rank.JACK), me, conferMsRemaining = null),
            plan = plan,
        )

        assertEquals(Ask.YourTurn, table.prompt)
        val row = assertNotNull(table.planLine)
        assertEquals(
            listOf(
                Says.Draws,
                Says.PutsDown(mine(1, Rank.JACK)),
                Says.Trade(ninas(1, Rank.FIVE), dons(1, Rank.SIX), swap = true),
                Says.Throws(bot3, null, Rank.JACK, blind = false),
            ),
            row.says,
        )
        assertEquals(
            listOf(Label.DrawCard, Label.UseFromPile(Rank.JACK)).toSet(),
            table.choices.map { it.label }.toSet(),
        )
        assertTrue(table.choices.all { it.move is Move.Send }, "a plan control was on the live rail")
    }

    // ------------------------------------------------------------------ where the plan opens

    @Test
    fun thePlanOpensOnYourOwnTurnWhileItCanStillBeBuilt() {
        // "How should I create plan myself": the switch lands on the viewer's own turn rather
        // than on the first turn of the round — which online is somebody else's two times out
        // of three.
        val summary = assertNotNull(tableFor(view()).planSummary)
        assertEquals(Question.ThePlan(at = myPage), summary.opens)
        assertEquals(Move.Ask(summary.opens), summary.open)

        // Nina is on play: her turn is still open, and so is mine, which is where I land.
        assertEquals(myPage, assertNotNull(tableFor(view(finalRound(onPlay = nina))).planSummary).opens.at)

        // My turn has been played and stamped: the plan opens on the first turn still open.
        val minePlayed = CoalitionPlan(lanes = listOf(Lane(me, Step.Bin, locked = true)))
        assertEquals(ninasPage, assertNotNull(tableFor(view(), plan = minePlayed).planSummary).opens.at)

        // My turn is under way: it is the one turn most worth rewriting, and the plan opens on it.
        assertEquals(myPage, assertNotNull(tableFor(view(finalRound(onPlay = me))).planSummary).opens.at)

        // The caller has no turn to build and came to watch: the plan opens on the first turn.
        val theirs = CoalitionPlan(lanes = listOf(Lane(nina, Step.Bin)))
        assertEquals(1, assertNotNull(tableFor(view(viewer = caller), plan = theirs).planSummary).opens.at)
    }
}
