package game.vinto.client

import game.vinto.engine.PlayerView
import game.vinto.engine.projectView
import game.vinto.shapes.ALL_RANKS
import game.vinto.shapes.ActionPhase
import game.vinto.shapes.ActiveTossIn
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
import game.vinto.shapes.PendingCardOrigin
import game.vinto.shapes.Pile
import game.vinto.shapes.PlanEdit
import game.vinto.shapes.PlayerState
import game.vinto.shapes.Rank
import game.vinto.shapes.Step
import game.vinto.shapes.TossIn
import game.vinto.shapes.TossInAction
import game.vinto.shapes.getCardShortDescription
import game.vinto.shapes.getCardValue
import game.vinto.shapes.hasAction
import game.vinto.shapes.laneOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The plan as table talk: what the design page "Plan as Table Talk" says each moment reads as.
 *
 * The rules the last build broke, held one by one: a plan can only speak about cards the table
 * can see or has been told about, so nothing is aimed on a blind draw; a question with one
 * answer is not asked, so "draws" is a fact when the pile cannot be taken; boxed means
 * touchable, so a fact and "and we'll see" have nothing to touch and the next decision is on
 * offer; a card that is not on the table yet is rose and tagged with the turn it arrives on; a
 * throw the table cannot vouch for says "blind" and rehearses as coming back with a penalty
 * card; a King points before it names, and the rank is what the table said; the turn on play
 * stays open and its drawn card enters the sentence as a fact; the transport is one page per
 * turn and a last page where the plan lands.
 */
class PlanAsTalkTest {

    private val me = "human-1"
    private val caller = "bot-2"
    private val nina = "bot-3"
    private val don = "bot-4"

    /** The coalition plays Nina, then Don, then me; a page is a turn, and the fourth is where it lands. */
    private val ninasPage = 1
    private val donsPage = 2
    private val myPage = 3
    private val lands = 4

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
        queued: List<TossInAction> = emptyList(),
    ) = GameState(
        gameId = "talk",
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
        activeTossIn = if (queued.isEmpty()) {
            null
        } else {
            ActiveTossIn(
                ranks = listOf(discardTop),
                initiatorId = caller,
                originalPlayerIndex = listOf(me, caller, nina, don).indexOf(caller),
                participants = queued.map { it.playerId },
                queuedActions = queued,
                waitingForInput = true,
                playersReadyForNextTurn = emptyList(),
            )
        },
        turnActions = emptyList(),
        roundActions = emptyList(),
        roundFailedAttempts = emptyList(),
        difficulty = Difficulty.MODERATE,
        rngState = 0,
    )

    private fun view(state: GameState = finalRound(), viewer: String = me): PlayerView =
        projectView(state, viewer, conferMsRemaining = 20_000L)

    /**
     * [plan] with every turn before [page] settled, so the pager reaches [page] at all.
     *
     * The plan is read front to back: the page after a turn nobody has decided is closed,
     * because the table it would start from is the one that turn leaves (`Transport.reach`).
     * A fixture that sets one seat's turn and opens it would otherwise be clamped back to the
     * first page and the test would be about a turn it never named. Let-go is the emptiest
     * decision there is, which keeps the padding out of whatever the test is actually asking.
     */
    private fun reaching(page: Int, plan: CoalitionPlan = CoalitionPlan()): CoalitionPlan =
        listOf(nina, don, me).take(page - 1).fold(plan) { standing, seat ->
            if (standing.laneOf(seat)?.step != null) {
                standing
            } else {
                standing.copy(lanes = standing.lanes.filterNot { it.seat == seat } + Lane(seat, Step.Bin))
            }
        }

    private fun Table.sentence(): TurnSentence = assertNotNull(board?.sentence, "the rail has no turn to build")

    /** The words of the turn's own row. */
    private fun Table.own(): List<Says> = sentence().own.slots.map { it.says }

    /** The words of the throw-in row, every clause flattened. */
    private fun Table.throws(): List<Says> = sentence().throws.flatMap { clause -> clause.slots.map { it.says } }

    private fun Table.slot(says: Says): Slot =
        assertNotNull(
            sentence().clauses.flatMap { it.slots }.firstOrNull { it.says == says },
            "no word $says in ${own() + throws()}",
        )

    /** The two cards and the arrow between them, wherever in the sentence they are. */
    private fun Table.arrow(): Slot =
        assertNotNull(
            sentence().clauses.flatMap { it.slots }.firstOrNull { it.says is Says.Trade },
            "no two cards in " + own(),
        )

    private fun Table.answers(): List<Label> = assertNotNull(board).answers.map { it.label }

    private fun Move?.edit(): PlanEdit = assertIs<Move.Plan>(this).edit

    private fun Step.bare(): Step = when (this) {
        is Step.Swap -> Step.Swap(from.copy(anchor = null), to.copy(anchor = null))
        is Step.PutDown -> Step.PutDown(card.copy(anchor = null), guess, then?.bare())
        is Step.Declare -> Step.Declare(rank, card?.copy(anchor = null), then?.bare())
        is Step.Peek -> Step.Peek(card.copy(anchor = null), also?.copy(anchor = null))
        is Step.ForceDraw, Step.TakeTheDiscard, Step.Bin, Step.UseIt -> this
    }

    private fun Slot.plain(): Boolean = open == null && !asked && !offer

    // ------------------------------------------------------------------ the empty turn

    @Test
    fun anUntouchedTurnIsAWholeSentenceAndOnlyItsDecisionsAreBoxed() {
        // "Draws · and we'll see · + then…": what happens if nobody says anything. With a three
        // on the pile there is one pile to take from, so "draws" is a fact with nothing to touch.
        val table = tableFor(view(), question = Question.ThePlan(at = ninasPage))
        assertEquals(listOf(Says.Draws, Says.WellSee, Says.AndThen), table.own())
        assertTrue(table.slot(Says.Draws).plain(), "a draw with no alternative was boxed")
        assertTrue(table.slot(Says.WellSee).plain(), "nothing decided was drawn as a decision")
        val offer = table.slot(Says.AndThen)
        assertTrue(offer.offer, "the next decision was not on offer")
        assertEquals(Move.Ask(Question.Doing(nina, ninasPage)), offer.open)

        // A nine on the pile can be taken, so "draws" is a decision and one touch flips it.
        val takeable = tableFor(view(finalRound(discardTop = Rank.NINE)), question = Question.ThePlan(at = ninasPage))
        assertEquals(Move.Plan(PlanEdit.OpenLane(nina, Opening.TAKE_THE_DISCARD)), takeable.slot(Says.Draws).open)
    }

    @Test
    fun takingThePileCardAsksWhatItIsAimedAtAndNothingAsksWhichPile() {
        val plan = CoalitionPlan(lanes = listOf(Lane(nina, opening = Opening.TAKE_THE_DISCARD)))
        val table = tableFor(view(finalRound(discardTop = Rank.NINE)), Question.ThePlan(at = ninasPage), plan = plan)

        assertEquals(listOf(Says.Takes(Rank.NINE), Says.WhichToLookAt), table.own())
        assertTrue(table.slot(Says.WhichToLookAt).asked)
        // Flipping back is one touch on the word.
        assertEquals(Move.Plan(PlanEdit.OpenLane(nina, Opening.DRAW)), table.slot(Says.Takes(Rank.NINE)).open)
        // A nine looks at one card of another hand: the felt offers those and nothing else.
        val touches = assertNotNull(table.board?.building?.composer).touches.keys
        assertTrue(touches.isNotEmpty())
        assertTrue(touches.none { it.playerId == nina || it.playerId == caller }, "$touches")
    }

    @Test
    fun theNextDecisionIsAskedOnlyWhileItsQuestionIsOpenAndNothingIsPlayedBeforeADraw() {
        val table = tableFor(view(), Question.Doing(nina, ninasPage))
        assertEquals(listOf(Says.Draws, Says.WhatWith), table.own())
        assertTrue(table.slot(Says.WhatWith).asked)
        // Puts down or lets it go. Never "plays it": nobody knows what will be drawn.
        assertEquals(listOf(Label.SwapCards, Label.Discard), table.answers())
        assertEquals(Move.Plan(PlanEdit.SetLane(nina, Step.Bin)), table.board?.answers?.get(1)?.move)
    }

    @Test
    fun puttingDownOffersEveryOwnCardAndNamesTheCallForAKnownActionCard() {
        // Any of my cards may go out, known or not; a card the table knows to be an action card
        // is called in one word, and there is no separate word for that call once made. A card
        // nobody has read is called off the rail instead — see the test below.
        val asking = tableFor(view(), Question.PuttingDown(me, myPage), plan = reaching(myPage))
        assertEquals(listOf(Says.Draws, Says.WhichCard), asking.own())
        assertEquals(setOf(CardRef(me, 0), CardRef(me, 1), CardRef(me, 2)), asking.taps.keys)

        val jack = CoalitionPlan(lanes = listOf(Lane(me, Step.PutDown(CardAt(me, 0)))))
        val putDown = tableFor(view(), Question.ThePlan(at = myPage), plan = reaching(myPage, jack))
        assertEquals(
            listOf(Says.Draws, Says.PutsDown(CardWord(Speaker.You, "Human1", 1, Rank.JACK)), Says.CallIt(Rank.JACK)),
            putDown.own(),
        )
        assertTrue(putDown.slot(Says.CallIt(Rank.JACK)).offer)
        assertEquals(
            Step.PutDown(CardAt(me, 0), guess = Rank.JACK),
            assertIs<PlanEdit.SetLane>(putDown.slot(Says.CallIt(Rank.JACK)).open.edit()).step.bare(),
        )

        // A card the table knows is *not* an action card is offered no call at all: calling it
        // right plays an action that does not exist, and calling it anything else costs a
        // penalty card. Nina has told us her first card is a five.
        val five = CoalitionPlan(lanes = listOf(Lane(nina, Step.PutDown(CardAt(nina, 0)))))
        val silent = tableFor(view(), Question.ThePlan(at = ninasPage), plan = five)
        assertEquals(
            listOf(Says.Draws, Says.PutsDown(CardWord(Speaker.Named("Bot3"), "Bot3", 1, Rank.FIVE))),
            silent.own(),
        )

        // And a call made on a card the table can name gets no word of its own: "puts down your
        // King, calls it a King" is the same word twice.
        val called = CoalitionPlan(lanes = listOf(Lane(me, Step.PutDown(CardAt(me, 2), guess = Rank.KING))))
        val shown = tableFor(view(), Question.ThePlan(at = myPage), plan = reaching(myPage, called))
        assertNull(shown.own().firstOrNull { it is Says.Called }, "the call was said twice over")
    }

    @Test
    fun aCardNobodyHasReadIsCalledOffTheRankRailWithoutTheRanksThatDoNothing() {
        // Reported from a phone: "when we swap unknown card plan does not allow us specify rank
        // for unknown card, we should allow it (skipping 2-6)". My second card is a card the
        // table has never been told about and I have never read.
        val blind = CoalitionPlan(lanes = listOf(Lane(me, Step.PutDown(CardAt(me, 1)))))
        val putDown = tableFor(view(), Question.ThePlan(at = myPage), plan = reaching(myPage, blind))
        assertEquals(
            listOf(Says.Draws, Says.PutsDown(CardWord(Speaker.You, "Human1", 2, null)), Says.CallIt(null)),
            putDown.own(),
        )
        val offer = putDown.slot(Says.CallIt(null))
        assertTrue(offer.offer)
        assertEquals(Move.Ask(Question.Calling(me, myPage)), offer.open)

        // The rail is every action card and only those: a call buys the card's action, and a
        // two through a six has none, so naming one correctly does nothing and naming one wrong
        // costs a penalty card.
        val rail = tableFor(view(), Question.Calling(me, myPage), plan = reaching(myPage, blind))
        assertEquals(ALL_RANKS.filter { hasAction(it) }, rail.ranks.map { it.rank })
        assertTrue(rail.ranks.none { it.rank == Rank.THREE || it.rank == Rank.JOKER }, "${rail.ranks}")
        assertEquals(
            Step.PutDown(CardAt(me, 1), guess = Rank.QUEEN),
            assertIs<PlanEdit.SetLane>(rail.ranks.first { it.rank == Rank.QUEEN }.move.edit()).step.bare(),
        )

        // And the call made is a word of its own here. For a card the table can name it would be
        // the same word twice — the put-down already said what it is — but this put-down named
        // no rank, so without it the Queen's look below belongs to nothing anybody can see.
        val queen = CoalitionPlan(lanes = listOf(Lane(me, Step.PutDown(CardAt(me, 1), guess = Rank.QUEEN))))
        val made = tableFor(view(), Question.ThePlan(at = myPage), plan = reaching(myPage, queen))
        assertEquals(Says.Called(Rank.QUEEN), made.own()[2])
        assertEquals(Move.Ask(Question.Calling(me, myPage)), made.slot(Says.Called(Rank.QUEEN)).open)
    }

    @Test
    fun callingTheJackAsksWhichTwoAndTheTradeTakesTheOffersPlace() {
        val called = CoalitionPlan(lanes = listOf(Lane(me, Step.PutDown(CardAt(me, 0), guess = Rank.JACK))))
        val asking = tableFor(view(), Question.ThePlan(at = myPage), plan = reaching(myPage, called))
        assertEquals(Says.WhichTwo, asking.own().last())
        assertTrue(asking.slot(Says.WhichTwo).asked)
        // The Jack may trade any coalition card — never the caller's, and never the card that
        // is on the pile by then.
        val composer = assertNotNull(asking.board?.building?.composer)
        assertTrue(composer.sources.isNotEmpty())
        assertTrue(composer.sources.none { it.playerId == caller })
        assertFalse(CardRef(me, 0) in composer.sources, "the card put down was offered for trading")

        // **Including cards nobody has named.** A Jack is blind: what it moves is decided by
        // where the cards are, not by what has been said about them, and a coalition that may
        // only trade what it has already described cannot plan the commonest Jack there is —
        // reported from a phone as not being able to pick two cards at all. `nina` position 1
        // and `me` position 1 are unspoken for in this fixture.
        assertTrue(
            CardRef(nina, 1) in composer.sources,
            "a teammate's unnamed card could not be traded: ${composer.sources}",
        )
        assertNotNull(
            composer.drops[CardRef(nina, 1)]?.get(PlanTarget.Card(CardRef(me, 1))),
            "two unnamed cards could not be traded with each other",
        )

        val traded = called.copy(
            lanes = listOf(
                Lane(
                    me,
                    Step.PutDown(CardAt(me, 0), guess = Rank.JACK, then = Step.Swap(CardAt(nina, 0), CardAt(don, 0))),
                ),
            ),
        )
        val said = tableFor(view(), Question.ThePlan(at = myPage), plan = reaching(myPage, traded))
        val trade = Says.Trade(
            CardWord(Speaker.Named("Bot3"), "Bot3", 1, Rank.FIVE),
            CardWord(Speaker.Named("Bot4"), "Bot4", 1, Rank.SIX),
            swap = true,
        )
        assertEquals(
            listOf(Says.Draws, Says.PutsDown(CardWord(Speaker.You, "Human1", 1, Rank.JACK)), trade),
            said.own(),
        )
        assertEquals(Move.Ask(Question.Aiming(me, myPage, Part.Called)), said.slot(trade).open)
    }

    /**
     * A Queen looks at two cards and then *may* trade them, and the arrow between the two cards
     * is where the plan says which. Lit, she trades; dim, she only looks — and touching it flips
     * the two, so a look-only costs a touch rather than a question of its own. The builder asks
     * "which two cards?" once, for both readings, because a coalition naming two cards under a
     * Queen means the trade far more often than not (design open question, settled by the rail).
     *
     * A Jack has no arrow to touch. Its action *is* the trade — the plan's door will not let a
     * Jack say anything else — so a control that could only be refused is not offered.
     */
    @Test
    fun aQueensArrowFlipsBetweenTradingAndOnlyLookingAndAJackHasNone() {
        val holding = finalRound().let { round ->
            round.copy(
                players = round.players.map { seat ->
                    if (seat.id != me) {
                        seat
                    } else {
                        seat(me, listOf(Rank.QUEEN, Rank.TWO, Rank.KING), said = mapOf(0 to Rank.QUEEN, 2 to Rank.KING))
                    }
                },
            )
        }
        val looking = Step.Peek(CardAt(nina, 0), CardAt(don, 0))
        val trading = Step.Swap(CardAt(nina, 0), CardAt(don, 0))
        fun queenSaying(then: Step) =
            CoalitionPlan(lanes = listOf(Lane(me, Step.PutDown(CardAt(me, 0), guess = Rank.QUEEN, then = then))))

        // Said as a trade: the arrow is lit, and touching it leaves the two cards where they are.
        val trades = tableFor(
            view(holding),
            Question.ThePlan(at = myPage),
            plan = reaching(myPage, queenSaying(trading)),
        ).arrow()
        assertTrue(assertIs<Says.Trade>(trades.says).swap, "a Queen said to trade read as only looking")
        assertEquals(
            Step.PutDown(CardAt(me, 0), guess = Rank.QUEEN, then = looking),
            assertIs<PlanEdit.SetLane>(trades.toggle.edit()).step.bare(),
            "the lit arrow does not turn the trade into a look",
        )

        // Said as a look: the arrow is dim, and touching it trades the two cards it looked at.
        val looks = tableFor(
            view(holding),
            Question.ThePlan(at = myPage),
            plan = reaching(myPage, queenSaying(looking)),
        ).arrow()
        assertFalse(assertIs<Says.Trade>(looks.says).swap, "a Queen said to look read as trading")
        assertEquals(
            Step.PutDown(CardAt(me, 0), guess = Rank.QUEEN, then = trading),
            assertIs<PlanEdit.SetLane>(looks.toggle.edit()).step.bare(),
            "the dim arrow does not turn the look into a trade",
        )

        // A Jack trades or says nothing; there is no second reading to offer.
        val jack = CoalitionPlan(
            lanes = listOf(Lane(me, Step.PutDown(CardAt(me, 0), guess = Rank.JACK, then = trading))),
        )
        assertNull(
            tableFor(view(), Question.ThePlan(at = myPage), plan = reaching(myPage, jack)).arrow().toggle,
            "a Jack was offered a look it may not take",
        )
    }

    @Test
    fun aKingPointsAtACardAndThenTheTableSaysWhatItIs() {
        val king = CoalitionPlan(lanes = listOf(Lane(me, Step.PutDown(CardAt(me, 2), guess = Rank.KING))))
        val pointing = tableFor(view(), Question.ThePlan(at = myPage), plan = reaching(myPage, king))
        assertEquals(Says.WhichToPointAt, pointing.own().last())
        // Every coalition card can be pointed at, named or not; the caller's never.
        val touches = assertNotNull(pointing.board?.building?.composer).touches
        assertTrue(CardRef(nina, 1) in touches, "an unnamed card could not be pointed at")
        assertTrue(touches.keys.none { it.playerId == caller })
        assertEquals(
            Step.PutDown(CardAt(me, 2), guess = Rank.KING, then = Step.Declare(card = CardAt(nina, 0))),
            assertIs<PlanEdit.SetLane>(touches.getValue(CardRef(nina, 0)).edit()).step.bare(),
        )

        val pointed = king.copy(
            lanes = listOf(
                Lane(me, Step.PutDown(CardAt(me, 2), guess = Rank.KING, then = Step.Declare(card = CardAt(nina, 0)))),
            ),
        )
        val naming = tableFor(view(), Question.ThePlan(at = myPage), plan = reaching(myPage, pointed))
        val ninasFive = CardWord(Speaker.Named("Bot3"), "Bot3", 1, Rank.FIVE)
        assertEquals(Says.Points(ninasFive, rank = null), naming.own().last())
        assertTrue(naming.slot(Says.Points(ninasFive, null)).asked)
        // The rank is what Nina said the card is, and the whole set is one touch further.
        assertEquals(listOf(Label.RankSaidBy(Rank.FIVE, Speaker.Named("Bot3")), Label.AnotherRank), naming.answers())
        assertEquals(Move.Ask(Question.Naming(me, myPage, Part.Called)), naming.board?.answers?.last()?.move)

        val named = pointed.copy(
            lanes = listOf(
                Lane(
                    me,
                    Step.PutDown(CardAt(me, 2), guess = Rank.KING, then = Step.Declare(Rank.FIVE, CardAt(nina, 0))),
                ),
            ),
        )
        val said = tableFor(view(), Question.ThePlan(at = myPage), plan = reaching(myPage, named))
        assertEquals(Says.Points(ninasFive, Rank.FIVE), said.own().last())
        assertTrue(said.board?.answers.orEmpty().isEmpty(), "answers were offered with nothing asked")
    }

    @Test
    fun theWholeSetOfRanksIsOnlyForACardNobodyHasNamed() {
        val pointed = CoalitionPlan(
            lanes = listOf(
                Lane(me, Step.PutDown(CardAt(me, 2), guess = Rank.KING, then = Step.Declare(card = CardAt(nina, 1)))),
            ),
        )
        val naming = tableFor(view(), Question.ThePlan(at = myPage), plan = reaching(myPage, pointed))
        assertEquals(listOf(Label.AnotherRank), naming.answers())

        val grid = tableFor(view(), Question.Naming(me, myPage, Part.Called), plan = reaching(myPage, pointed))
        assertEquals(game.vinto.shapes.ALL_RANKS.size, grid.ranks.size)
        assertEquals(
            Step.PutDown(CardAt(me, 2), guess = Rank.KING, then = Step.Declare(Rank.SEVEN, CardAt(nina, 1))),
            assertIs<PlanEdit.SetLane>(grid.ranks.first { it.rank == Rank.SEVEN }.move.edit()).step.bare(),
        )
    }

    // ------------------------------------------------------------------ throws

    @Test
    fun aThrowMadeBeforeTheCallIsSaidOnTheFirstTurnAndNowhereElse() {
        // Reported from a phone twice over: the first turn missing the toss-in the others have,
        // and "if bots or somebody tossin before vinto called vinto their toss in must be part
        // of plan". Both are the same window — the one already open when the plan is composed,
        // answering the caller's own last card. It belongs to nobody's turn, so the maintainer
        // put it on the first player's, visible only where such throws have been made.
        val thrown = listOf(TossInAction(nina, Rank.THREE, position = 1), TossInAction(caller, Rank.THREE, 0))
        val table = tableFor(view(finalRound(queued = thrown)), Question.ThePlan(at = ninasPage))

        val said = table.sentence().throws.flatMap { it.slots }.map { it.says }
        assertEquals(Says.Throws(Speaker.Named("Bot3"), card = null, rank = Rank.THREE, blind = false), said.first())
        // The caller's own throw is not the coalition's business and is never drawn here.
        assertTrue(said.none { it is Says.Throws && it.who == Speaker.Named("Bot2") }, "$said")
        // A fact, not a step: there is nothing to touch and nothing the plan could edit away.
        assertNull(table.sentence().throws.first().slots.first().open)

        // The second turn says nothing of it — the window it answered closed before that turn.
        val later = tableFor(
            view(finalRound(queued = thrown)),
            Question.ThePlan(at = donsPage),
            plan = reaching(donsPage),
        )
        assertTrue(
            later.sentence().throws.flatMap { it.slots }.none { it.says is Says.Throws },
            "the throw was repeated on a turn it does not belong to",
        )

        // And with no such throws the row is simply absent, not an empty one.
        val quiet = tableFor(view(), Question.ThePlan(at = ninasPage))
        assertTrue(quiet.sentence().throws.flatMap { it.slots }.none { it.says is Says.Throws })
    }

    @Test
    fun aThrowIsPickedOnTheFeltAndOneTheTableCannotVouchForIsBlind() {
        // Nina puts her five down, so a five lands. Touching "+ throw in…" lights the coalition's
        // cards: Don's known six can never match and is not offered; my unnamed cards are, blind.
        val plan = CoalitionPlan(lanes = listOf(Lane(nina, Step.PutDown(CardAt(nina, 0)))))
        val table = tableFor(view(), Question.ThePlan(at = ninasPage), plan = plan)
        val offer = table.slot(Says.AddThrow)
        assertTrue(offer.offer)
        assertEquals(Move.Ask(Question.Throwing(nina, ninasPage, index = 0)), offer.open)

        val throwing = tableFor(view(), Question.Throwing(nina, ninasPage, index = 0), plan = plan)
        assertEquals(setOf(CardRef(me, 1), CardRef(nina, 1)), throwing.taps.keys, "the wrong cards were on offer")
        assertEquals(
            PlanEdit.SetTossIns(nina, listOf(TossIn(me, rank = null, card = CardAt(me, 1)))),
            throwing.taps.getValue(CardRef(me, 1)).edit(),
        )

        val blind = plan.copy(
            lanes = listOf(plan.lanes.single().copy(tossIns = listOf(TossIn(me, null, card = CardAt(me, 1))))),
        )
        val said = tableFor(view(), Question.ThePlan(at = ninasPage), plan = blind)
        assertEquals(
            listOf(
                Says.Throws(Speaker.You, CardWord(Speaker.You, "Human1", 2, null), rank = null, blind = true),
                Says.AddThrow,
            ),
            said.throws(),
        )
    }

    @Test
    fun aVouchedThrowLightsItsCardInGoldAndReadsAsItsRank() {
        // Don's turn lands a six if he puts his down; Nina's five is no match, and my Jack is
        // not either — only a card nobody has named, or one known to be a six, may be thrown.
        val plan = CoalitionPlan(lanes = listOf(Lane(don, Step.PutDown(CardAt(don, 0)))))
        val throwing = tableFor(
            view(finalRound()),
            Question.Throwing(don, donsPage, index = 0),
            plan = reaching(donsPage, plan),
        )
        assertEquals(setOf(CardRef(me, 1), CardRef(nina, 1)), throwing.taps.keys)

        val withSix = finalRound().let { state ->
            state.copy(
                players = state.players.map {
                    if (it.id == nina) {
                        seat(
                            nina,
                            listOf(Rank.FIVE, Rank.SIX),
                            said = mapOf(0 to Rank.FIVE, 1 to Rank.SIX),
                        )
                    } else {
                        it
                    }
                },
            )
        }
        val vouched = tableFor(
            view(withSix),
            Question.Throwing(don, donsPage, index = 0),
            plan = reaching(donsPage, plan),
        )
        assertEquals(
            PlanEdit.SetTossIns(don, listOf(TossIn(nina, Rank.SIX, card = CardAt(nina, 1)))),
            vouched.taps.getValue(CardRef(nina, 1)).edit().let { edit ->
                assertIs<PlanEdit.SetTossIns>(
                    edit,
                ).copy(tossIns = edit.tossIns.map { it.copy(card = it.card?.copy(anchor = null)) })
            },
        )
        assertEquals(setOf(CardRef(nina, 1)), assertNotNull(vouched.board).wanted, "the match was not marked")
    }

    @Test
    fun aBlindThrowRehearsesAsTheCardComingBackWithAPenaltyCard() {
        val plan = CoalitionPlan(
            lanes = listOf(
                Lane(nina, Step.PutDown(CardAt(nina, 0)), tossIns = listOf(TossIn(me, null, card = CardAt(me, 1)))),
            ),
        )
        val film = rehearsal(view(), plan)
        val after = film.tables[1].players.first { it.id == me }
        assertEquals(4, after.cards.size, "the blind throw did not come back with a penalty card")
        assertEquals(1, film.fresh[1][CardRef(me, 3)], "the penalty card is not rose and tagged with the turn")
        assertEquals(2, film.tables[1].players.first { it.id == nina }.cards.size)
    }

    // ------------------------------------------------------------------ the drawn card

    @Test
    fun theDrawnCardIsAFactAndTheTurnStaysOpenWhileItIsPlayed() {
        val queen = card(Rank.QUEEN, "drawn-q")
        val drawn = finalRound(
            onPlay = me,
            subPhase = GameSubPhase.CHOOSING,
            pending = PendingAction(
                playerId = me,
                card = queen,
                actionPhase = ActionPhase.CHOOSING_ACTION,
                from = PendingCardOrigin.DRAWING,
                targets = emptyList(),
            ),
        )
        val open = tableFor(view(drawn), Question.ThePlan(at = myPage))
        assertEquals(listOf(Says.Drew(Rank.QUEEN), Says.AndThen), open.own())
        assertTrue(open.slot(Says.Drew(Rank.QUEEN)).plain(), "a fact was boxed")
        assertNotNull(open.board?.building?.composer, "the turn on play was not open for changing")

        val doing = tableFor(view(drawn), Question.Doing(me, myPage))
        assertEquals(listOf(Label.UseAction, Label.SwapCards, Label.Discard), doing.answers())

        val plays = CoalitionPlan(lanes = listOf(Lane(me, Step.UseIt)))
        val aiming = tableFor(view(drawn), Question.ThePlan(at = myPage), plan = reaching(myPage, plays))
        assertEquals(listOf(Says.Drew(Rank.QUEEN), Says.PlaysIt, Says.WhichTwo), aiming.own())

        val trade = Step.Swap(CardAt(nina, 0), CardAt(don, 0))
        val traded = tableFor(
            view(drawn),
            Question.ThePlan(at = myPage),
            plan = CoalitionPlan(lanes = listOf(Lane(me, trade))),
        )
        assertEquals(Says.PlaysIt, traded.own()[1])
        assertIs<Says.Trade>(traded.own()[2])
        assertTrue(assertNotNull(traded.board?.sentence).playable, "a Queen face up in the hand could not be played")
    }

    @Test
    fun anActionOnABlindDrawIsNeverPlayedAndTheSentenceSaysSo() {
        // "Draws, plays it, trades …" is the sentence the last build allowed and no table can
        // say: nobody knows the drawn card. The film refuses to draw it and the turn says why.
        val plan = CoalitionPlan(lanes = listOf(Lane(nina, Step.Swap(CardAt(nina, 0), CardAt(don, 0)))))
        val film = rehearsal(view(), plan)
        assertNull(film.frames[0], "a trade on a blind draw was drawn")
        val table = tableFor(view(), Question.ThePlan(at = ninasPage), plan = plan)
        assertFalse(assertNotNull(table.board?.sentence).playable)
    }

    // ------------------------------------------------------------------ rose cards and the pile

    @Test
    fun aCardThePlanDrawsIsRoseAndTaggedWithTheTurnItArrivesOn() {
        val plan = CoalitionPlan(
            lanes = listOf(Lane(nina, Step.PutDown(CardAt(nina, 0))), Lane(don, Step.Bin)),
        )
        val film = rehearsal(view(), plan)
        assertEquals(mapOf(CardRef(nina, 0) to 1), film.fresh[1])
        assertEquals(mapOf(CardRef(nina, 0) to 1), film.fresh[2], "the tag was lost a turn later")
        // Don lets a card nobody knows go: the pile's top is unknown from then on, and nothing
        // unknown can be taken.
        assertNull(film.pileUnknown[1])
        assertEquals(2, film.pileUnknown[2])
        val mine = tableFor(view(), Question.ThePlan(at = myPage), plan = reaching(myPage, plan))
        assertEquals(2, mine.board?.pileUnknown)
        assertTrue(mine.slot(Says.Draws).plain(), "an unknown pile card was offered for taking")
        assertEquals(mapOf(CardRef(nina, 0) to 1), mine.board?.fresh)
    }

    // ------------------------------------------------------------------ pages

    @Test
    fun theTransportIsOnePagePerTurnAndTheLastPageIsWhereThePlanLands() {
        val plan = CoalitionPlan(lanes = listOf(Lane(nina, Step.PutDown(CardAt(nina, 0)))))
        val focus = Question.ThePlan(at = donsPage)
        val transport = assertNotNull(tableFor(view(), focus, plan = plan).board).transport

        assertEquals(lands, transport.stops.size)
        assertEquals(
            listOf(Speaker.Named("Bot3"), Speaker.Named("Bot4"), Speaker.You, null),
            transport.stops.map { it.seat },
        )
        assertEquals(donsPage, transport.at)
        assertFalse(transport.arrived)
        // Don's turn is the first nobody has decided, so it is as far as the plan reads: the
        // pages past it are drawn, and closed (`Transport.reach`).
        assertEquals(donsPage, transport.reach)
        assertNull(transport.stops.last().go, "where the plan lands was reachable with a turn still open")
        // A stop is a jump, in either direction: the pager, not a film.
        assertEquals(Move.Ask(focus.copy(at = ninasPage, landed = false)), transport.stops.first().go)
        // Watching one turn runs it from the table it starts on and parks on it.
        assertEquals(Move.Ask(focus.copy(at = ninasPage, runningTo = ninasPage)), transport.stops.first().replay)
        assertNull(transport.stops[2].replay, "a page the plan does not reach offered a replay")

        // With every turn decided the whole thing is reachable, and the jump forward is back.
        val whole = assertNotNull(tableFor(view(), focus, plan = reaching(lands, plan)).board).transport
        assertEquals(lands, whole.reach)
        assertEquals(Move.Ask(focus.copy(at = lands, landed = false)), whole.stops.last().go)

        // Playing every turn runs from here to as far as the plan reads. From Don's page that
        // is Don's page, so there is nothing to run; from Nina's, the whole plan.
        assertNull(transport.playAll, "a film of nothing was offered")
        val fromTheStart = assertNotNull(
            tableFor(view(), Question.ThePlan(at = ninasPage), plan = plan).board,
        ).transport
        assertEquals(Move.Ask(Question.ThePlan(at = ninasPage, runningTo = donsPage)), fromTheStart.playAll)

        val landed = assertNotNull(tableFor(view(), Question.ThePlan(at = lands), plan = reaching(lands, plan)).board)
        assertTrue(landed.transport.arrived)
        assertNull(landed.sentence, "the last page has no turn to build")
        assertNotNull(landed.outcome)
    }

    @Test
    fun everyPageHasItsSentenceAndTheRunnerKnowsWhichTurnsHaveAFilm() {
        // The pager shows the neighbours of the page on screen mid-swipe, so every turn is a
        // sentence read against its own start; and the runner that parks the head after a film
        // has to know which turn's cards are the last to land.
        val plan = CoalitionPlan(lanes = listOf(Lane(nina, Step.PutDown(CardAt(nina, 0)))))
        val board = assertNotNull(
            tableFor(view(), Question.ThePlan(at = donsPage), plan = reaching(donsPage, plan)).board,
        )

        assertEquals(3, board.pages.size, "one sentence per turn")
        assertEquals(board.sentence, board.pages[donsPage - 1], "the page on screen is not among the pages")
        assertEquals(listOf(Speaker.Named("Bot3"), Speaker.Named("Bot4"), Speaker.You), board.pages.map { it.who })
        assertTrue(board.pages[0].says.any { it is Says.PutsDown }, "a neighbouring page is not read whole")
        // Every turn has a film: a turn nobody has decided still draws a card nobody knows.
        assertEquals(listOf(true, true, true), board.watchable, "a turn lost its film")

        // What has no film is a turn the table cannot draw — one naming a card that has gone.
        val broken = plan.copy(lanes = plan.lanes + Lane(don, Step.PutDown(CardAt(don, 4))))
        val cannot = assertNotNull(
            tableFor(view(), Question.ThePlan(at = donsPage), plan = reaching(donsPage, broken)).board,
        )
        assertEquals(listOf(true, false, true), cannot.watchable, "a turn naming a card that has gone drew one")

        val landed = assertNotNull(tableFor(view(), Question.ThePlan(at = lands), plan = reaching(lands, plan)).board)
        assertNull(landed.sentence, "the last page has a turn to build")
        assertEquals(3, landed.pages.size, "the last page lost the turns before it")
    }

    @Test
    fun thePageShowsTheTableItsTurnStartsFromAndTheResultOnceWatched() {
        val plan = CoalitionPlan(lanes = listOf(Lane(nina, Step.PutDown(CardAt(nina, 0)))))
        val start = assertNotNull(tableFor(view(), Question.ThePlan(at = ninasPage), plan = plan).board)
        assertEquals(CardRef(nina, 0) in start.marks, true)
        assertTrue(start.fresh.isEmpty(), "the page showed the turn's result before it was watched")

        val watched = assertNotNull(
            tableFor(view(), Question.ThePlan(at = ninasPage, landed = true), plan = plan).board,
        )
        assertEquals(mapOf(CardRef(nina, 0) to 1), watched.fresh)
    }

    // ------------------------------------------------------------------ the live rail and the switch

    @Test
    fun theLiveRailCarriesThePlansRowForTheTurnOnPlay() {
        val plan = CoalitionPlan(
            lanes = listOf(
                Lane(me, Step.PutDown(CardAt(me, 0), Rank.JACK, Step.Swap(CardAt(nina, 0), CardAt(don, 0)))),
            ),
        )
        val live = tableFor(view(finalRound(onPlay = me)), plan = plan)
        val row = assertNotNull(live.planLine, "the live rail has no row for the plan")
        assertEquals(Speaker.You, row.who)
        assertEquals(listOf(Says.Draws, Says.PutsDown(CardWord(Speaker.You, "Human1", 1, Rank.JACK))), row.says.take(2))
        assertIs<Says.Trade>(row.says[2])
        assertNull(live.board, "the plan opened on the live rail")

        // Nothing planned: the row is there and empty, so nothing under it moves.
        val empty = assertNotNull(tableFor(view(finalRound(onPlay = me))).planLine)
        assertTrue(empty.says.isEmpty())
        // Somebody else's turn: their row.
        assertEquals(Speaker.Named("Bot3"), tableFor(view(finalRound(onPlay = nina))).planLine?.who)
    }

    @Test
    fun theSwitchWearsWhoeverChangedYourTurnUntilYouHaveLookedAtIt() {
        val plan = CoalitionPlan(lanes = listOf(Lane(me, Step.Bin)), editedBy = nina, agreed = listOf(nina))
        val unread = assertNotNull(summaryFor(view(), plan, seen = null))
        assertEquals(Speaker.Named("Bot3"), unread.changedBy)
        val read = assertNotNull(summaryFor(view(), plan, seen = plan.laneOf(me)))
        assertNull(read.changedBy)
        val mine = assertNotNull(summaryFor(view(), plan.copy(editedBy = me), seen = null))
        assertNull(mine.changedBy, "my own edit was news to me")
    }

    @Test
    fun theCallerReadsEveryWordPlainAndTheBotsNeverProposeABlindThrow() {
        val plan = CoalitionPlan(
            lanes = listOf(
                Lane(nina, Step.PutDown(CardAt(nina, 0)), tossIns = listOf(TossIn(me, null, card = CardAt(me, 1)))),
            ),
        )
        val table = tableFor(view(viewer = caller), Question.ThePlan(at = ninasPage), plan = plan)
        val words = table.sentence().clauses.flatMap { it.slots }
        assertTrue(words.all { it.plain() }, "the caller was offered a word: ${words.filterNot { it.plain() }}")
        assertTrue(table.board?.answers.orEmpty().isEmpty())
        assertTrue(table.choices.isEmpty())
    }
}
