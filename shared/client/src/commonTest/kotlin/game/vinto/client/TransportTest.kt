package game.vinto.client

import game.vinto.engine.PlayerView
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
import game.vinto.shapes.VintoJson
import game.vinto.shapes.coalitionInTurnOrder
import game.vinto.shapes.getCardShortDescription
import game.vinto.shapes.getCardValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The plan read as a film: where it stops, what each page shows, and what may be done there.
 *
 * The transport is a pager (design D14, redrawn): one page per coalition turn, decided or not,
 * and a last page where the plan lands. A page shows the table its turn starts from — the one
 * its cards are touched on — and, once its film has been watched, the table it leaves. Touching
 * a stop is a jump; the two buttons play the film. Editing sleeps while it runs. The one thing
 * it must not do is touch the plan, which is the first test below.
 */
class TransportTest {

    private val me = "human-1"
    private val caller = "bot-2"
    private val nina = "bot-3"
    private val don = "bot-4"

    /** The coalition plays Nina, Don, then me; the fourth page is where the plan lands. */
    private val lands = 4

    // ------------------------------------------------------------------ the plan is untouched

    @Test
    fun readingThePlanFromADifferentTurnAddsNothingToTheWire() {
        // Which turn a member is looking at is the screen's business and must not travel: two
        // members reading different turns of one plan is normal, and a shared cursor would
        // fight (design D3). Compared as bytes rather than as fields, so a new field on
        // `CoalitionPlan` cannot slip in behind this.
        val standing = plan()
        val before = VintoJson.encodeToString(CoalitionPlan.serializer(), standing)

        for (at in 0..5) {
            tableFor(view(), question = Question.ThePlan(at = at), plan = standing)
        }

        assertEquals(
            before,
            VintoJson.encodeToString(CoalitionPlan.serializer(), standing),
            "reading the plan changed the plan",
        )
    }

    // ------------------------------------------------------------------ the positions

    @Test
    fun thereIsOneStopPerTurnBoundaryAndNoneBetweenThem() {
        val here = view()
        val stops = rehearsal(here, plan())

        assertEquals(
            plan().lanes.size + 1,
            stops.tables.size,
            "the film does not stop at the table now and after each turn",
        )
        assertEquals(plan().lanes.size, stops.frames.size, "a turn lost its picture")
        assertEquals(here, stops.tables.first(), "the first stop is not the table as it is")
    }

    @Test
    fun positionNShowsTheTableAfterTurnsOneToNAndNoFurther() {
        val here = view()
        val stops = rehearsal(here, plan())

        // Turn 1 takes the King and names fives, which empties Nina's; turn 3 calls my King
        // and points it at Don's six. So position 1 has done the first and not the third, and
        // position 3 has done both — which is what "and no further" has to mean.
        fun held(at: Int, seat: String) = stops.tables[at].players.first { it.id == seat }.cards.size

        assertEquals(held(0, nina), here.players.first { it.id == nina }.cards.size, "position 0 played a turn")
        assertEquals(held(0, don), here.players.first { it.id == don }.cards.size, "position 0 played a turn")

        assertEquals(held(0, nina) - 1, held(1, nina), "position 1 did not play turn 1")
        assertEquals(held(0, don), held(1, don), "position 1 ran ahead into turn 3")

        assertEquals(held(1, nina), held(2, nina), "an undecided turn moved a card")
        assertEquals(held(1, don), held(2, don), "position 2 ran ahead into turn 3")

        assertEquals(held(2, don) - 1, held(3, don), "position 3 never played turn 3")
    }

    @Test
    fun aTurnWithNothingToDrawStillKeepsItsPosition() {
        // ② has to mean the same turn to every member reading it, so a turn the film cannot
        // draw is a stop the transport passes through rather than one it skips (design D6).
        // A step naming a card that is not there is the case: there is no honest picture of it.
        val gone = CoalitionPlan(
            lanes = listOf(
                Lane(nina, Step.PutDown(CardAt(nina, 0))),
                Lane(don, Step.PutDown(CardAt(don, 4))),
                Lane(me, Step.Bin),
            ),
        )
        val stops = rehearsal(view(), gone)

        assertEquals(4, stops.tables.size, "a turn with nothing to draw was dropped from the film")
        assertNotNull(stops.frames[0], "the decided turn drew nothing")
        assertNull(stops.frames[1], "a step naming a card that has gone invented a picture")
        assertEquals(stops.tables[1], stops.tables[2], "a turn with nothing to draw moved a card")
    }

    @Test
    fun aTurnNobodyHasDecidedLeavesEveryHandWhereItWas() {
        // Undecided is not the same as undrawable. The seat draws either way, so the turn has
        // a picture — and since nothing the table can name has moved, the picture is every
        // hand exactly as it was, with a card nobody can name on the pile.
        val undecided = CoalitionPlan(
            lanes = listOf(Lane(nina, Step.PutDown(CardAt(nina, 0))), Lane(don, null), Lane(me, null)),
        )
        val stops = rehearsal(view(), undecided)

        assertNotNull(stops.frames[1], "a turn nobody had decided had nothing to watch")
        assertEquals(
            stops.tables[1].players.map { it.cards },
            stops.tables[2].players.map { it.cards },
            "an undecided turn moved a card",
        )
        assertNull(stops.tables[2].discardTop, "an undecided turn named the card it left on the pile")
        assertEquals(2, stops.pileUnknown[2], "the pile was still takeable after a blind draw")
    }

    /**
     * Position *k* is the coalition's *k*th **turn**, not the plan's *k*th decision.
     *
     * `CoalitionPlan.lanes` holds only the turns somebody has set, so its length counts
     * decisions. Read as positions, a board with one decided turn had one position, and the
     * arrival — the one picture the transport exists for — was the present.
     */
    @Test
    fun everyCoalitionTurnIsAPositionEvenWhenNobodyHasDecidedIt() {
        val film = rehearsal(view(), oneDecidedTurn())
        fun hands(at: Int) = film.tables[at].players

        assertEquals(4, film.tables.size, "three turns did not make four positions")
        assertEquals(3, film.turns, "a turn nobody has decided lost its place")
        assertEquals(listOf(true, true, true), film.frames.map { it != null }, "a turn lost its picture")

        assertEquals(view().players, hands(1), "an undecided turn moved a card")
        assertTrue(hands(2) != hands(1), "the decided turn changed nothing")
        assertEquals(hands(2), hands(3), "an undecided turn moved a card")
        assertTrue(film.arrival != view(), "the plan's arrival is the table as it is now")
    }

    /**
     * Pressing play part-way through a plan plays what is left of it.
     *
     * A position is an index into [Rehearsal.frames], nulls and all. Filtering the nulls out
     * first and then dropping by position mixes two different numbers.
     */
    @Test
    fun theFilmFromAPositionKeepsEveryTurnStillToCome() {
        // The middle turn is the only one with a picture: the other two name a card that has
        // gone, so their places in `frames` are null. That is what the drop has to step over.
        val film = rehearsal(view(), oneDrawableTurn())
        assertEquals(listOf(false, true, false), film.frames.map { it != null }, "the fixture has changed shape")

        assertEquals(1, film.from(0).size, "the whole film is missing its only move")
        assertEquals(1, film.from(1).size, "the step was dropped by a position that is not its own")
        assertEquals(0, film.from(2).size, "a move already played was played again")
        assertEquals(0, film.from(3).size, "the arrival still had something to play")

        assertEquals(0, film.between(0, 1).size, "the first turn has nothing in it to watch")
        assertEquals(1, film.between(0, 2).size, "watching two turns missed the one with a step")
        assertEquals(1, film.between(1, 3).size)
    }

    /** Three turns with only the middle one decided: Don puts his one card down. */
    private fun oneDecidedTurn(): CoalitionPlan {
        val order = coalitionInTurnOrder(view().players.map { it.id }, caller)
        assertEquals(listOf(nina, don, me), order, "the fixture's turn order has moved")
        return CoalitionPlan(lanes = listOf(Lane(order[1], Step.PutDown(CardAt(don, 0)))))
    }

    /** Three turns, and only the middle one can be drawn: the other two name a card that is not there. */
    private fun oneDrawableTurn(): CoalitionPlan {
        val order = coalitionInTurnOrder(view().players.map { it.id }, caller)
        assertEquals(listOf(nina, don, me), order, "the fixture's turn order has moved")
        return CoalitionPlan(
            lanes = listOf(
                Lane(nina, Step.PutDown(CardAt(nina, 9))),
                Lane(don, Step.PutDown(CardAt(don, 0))),
                Lane(me, Step.PutDown(CardAt(me, 9))),
            ),
        )
    }

    /** [plan] with its open turn settled, so the pager reaches every page. See `Transport.reach`. */
    private fun wholePlan() = CoalitionPlan(
        lanes = plan().lanes.map { if (it.step == null) it.copy(step = Step.Bin) else it },
    )

    /**
     * A card the plan has put down is rose from the turn it arrives on, and not before.
     *
     * A put-down does not leave a gap: the seat draws off the deck, face down, and nobody knows
     * what that card is. The page of the turn shows the table it starts from, where the card
     * is still the one this seat remembers; once the turn has been watched, and on every page
     * after it, the card is rose and tagged with the turn.
     */
    @Test
    fun aCardThePlanPutsDownIsRoseFromTheTurnItArrivesOn() {
        val order = coalitionInTurnOrder(view().players.map { it.id }, caller)
        // The turns before mine are settled so the pager reaches mine at all (`Transport.reach`).
        val standing = CoalitionPlan(
            lanes = listOf(
                Lane(nina, Step.Bin),
                Lane(don, Step.Bin),
                Lane(me, Step.PutDown(CardAt(me, 0))),
            ),
        )
        val turn = order.indexOf(me) + 1

        val before = assertNotNull(tableFor(view(), question = Question.ThePlan(at = turn), plan = standing).board)
        assertTrue(CardRef(me, 0) !in before.fresh, "a draw was marked before the turn that draws it")

        val watched = assertNotNull(
            tableFor(view(), question = Question.ThePlan(at = turn, landed = true), plan = standing).board,
        )
        assertEquals(mapOf(CardRef(me, 0) to turn), watched.fresh, "the put-down left no rose card behind it")

        val landed = assertNotNull(tableFor(view(), question = Question.ThePlan(at = lands), plan = standing).board)
        assertEquals(mapOf(CardRef(me, 0) to turn), landed.fresh, "the tag was lost where the plan lands")

        // And a plan that puts nothing down marks nothing, which is the usual case.
        val ninasOnly = CoalitionPlan(lanes = listOf(plan().lanes.first()))
        val quiet = assertNotNull(tableFor(view(), question = Question.ThePlan(at = lands), plan = ninasOnly).board)
        assertTrue(quiet.fresh.isEmpty(), "a plan with no put-down marked a card as rose")
    }

    /** The turn reads as a sentence, in the order it happens: the pile, what the card does, then the throws. */
    @Test
    fun theTurnBeingBuiltReadsAsASentenceInTheOrderItHappens() {
        val order = coalitionInTurnOrder(view().players.map { it.id }, caller)
        val standing = CoalitionPlan(
            lanes = listOf(
                Lane(
                    order[0],
                    Step.Declare(Rank.FIVE),
                    opening = Opening.TAKE_THE_DISCARD,
                    tossIns = listOf(TossIn(don, Rank.SIX, card = CardAt(don, 0))),
                ),
            ),
        )
        val sentence = assertNotNull(
            tableFor(view(), question = Question.ThePlan(at = 1), plan = standing).board?.sentence,
            "the rail has no turn to build",
        )
        val own = sentence.own.slots
        assertEquals(Says.Takes(Rank.KING), own[0].says, "the pile the card comes from is not said")
        assertEquals(Says.Names(Rank.FIVE), own[1].says, "the rank the King names is not in the sentence")
        assertEquals(2, own.size, "taking the pile's card has a word for playing it")
        assertEquals(Part.Throw(0), sentence.clauses[1].part, "who throws in is not a clause of the turn")
        val throwWord = assertIs<Says.Throws>(sentence.clauses[1].slots[0].says)
        assertEquals(Speaker.Named("Bot4"), throwWord.who)
        // A five lands after the King, and a six is no match: the table cannot vouch for it.
        assertTrue(throwWord.blind, "a throw that cannot match was vouched for")

        // Every decision opens the question it is about, and the opening offers the other pile.
        assertEquals(PlanEdit.OpenLane(order[0], Opening.DRAW), assertIs<Move.Plan>(own[0].open).edit)
        assertNotNull(own[1].open, "the rank cannot be changed")
        assertEquals(Says.AddThrow, sentence.clauses.last().slots.last().says, "nobody else can be asked to throw in")
    }

    /**
     * The words a step lands on. "Play it" is using the card's own action, which for a Jack or
     * a Queen *is* a trade — one is what you do with the card, the other is what the card does.
     */
    @Test
    fun eachKindOfStepLandsOnTheRightWordOfTheSentence() {
        val order = coalitionInTurnOrder(view().players.map { it.id }, caller)
        val seat = order[0]
        // A three on the pile: nothing to take, so every opening below is a draw.
        val three = projectView(finalRound(discardTop = Rank.THREE), me, conferMsRemaining = 20_000L)

        fun saysFor(step: Step?, opening: Opening? = null) = assertNotNull(
            tableFor(
                three,
                question = Question.ThePlan(at = 1),
                plan = CoalitionPlan(lanes = listOf(Lane(seat, step, opening = opening))),
            ).board?.sentence,
        ).own.slots.map { it.says }

        assertEquals(listOf(Says.Draws, Says.PlaysIt), saysFor(Step.UseIt))
        assertEquals(listOf(Says.Draws, Says.PlaysIt, Says.Names(Rank.SIX)), saysFor(Step.Declare(Rank.SIX)))
        assertEquals(listOf(Says.Takes(null)), saysFor(Step.TakeTheDiscard))
        assertEquals(
            Says.PutsDown(CardWord(Speaker.Named("Bot3"), "Bot3", 1, Rank.FIVE)),
            saysFor(Step.PutDown(CardAt(seat, 0)))[1],
        )
        assertEquals(listOf(Says.Draws, Says.LetsItGo), saysFor(Step.Bin))

        // Nothing said yet is a sentence with its decision on offer rather than no sentence: the
        // turn exists either way, and it is the empty one a member most needs to fill.
        assertEquals(listOf(Says.Draws, Says.WellSee, Says.AndThen), saysFor(null))
    }

    /** The caller reads the plan and taps none of it; nothing in the sentence is theirs to change. */
    @Test
    fun theCallerIsOfferedNoWordOfTheSentence() {
        val order = coalitionInTurnOrder(view().players.map { it.id }, caller)
        val standing = CoalitionPlan(lanes = listOf(Lane(order[0], Step.UseIt)))
        val sentence = assertNotNull(
            tableFor(view(viewer = caller), question = Question.ThePlan(at = 1), plan = standing).board?.sentence,
        )

        assertTrue(
            sentence.clauses.flatMap { it.slots }.all { it.open == null },
            "the caller was offered a word to change",
        )
        assertTrue(sentence.says.none { it == Says.AddThrow }, "the caller was offered a throw-in")
    }

    /** The lit stop and the page name the same turn, and the last page has no turn to build. */
    @Test
    fun theLitStopAndThePageNameTheSameTurn() {
        val order = coalitionInTurnOrder(view().players.map { it.id }, caller)

        for ((turn, seat) in order.withIndex()) {
            val board = assertNotNull(
                tableFor(view(), question = Question.ThePlan(at = turn + 1), plan = wholePlan()).board,
            )
            val lit = assertNotNull(board.transport.stops.first { it.here }.seat, "the lit stop names nobody")
            assertEquals(speakerFor(view(), seat), lit, "stop ${turn + 1} names the wrong seat")
            assertEquals(lit, board.sentence?.who, "the rail builds a different turn from the one lit")
        }

        val atLands = assertNotNull(tableFor(view(), question = Question.ThePlan(at = lands), plan = wholePlan()).board)
        assertNull(atLands.sentence, "where the plan lands offered a turn to build")
        assertNull(atLands.building, "where the plan lands offered a composer")
        assertTrue(atLands.transport.arrived)
        assertNull(atLands.transport.stops.last().seat, "the last page is somebody's turn")
    }

    /**
     * A page shows the table its turn starts from — the one its cards are touched on — and,
     * once the turn has been watched, the table it leaves. Any touch brings the start back.
     */
    @Test
    fun thePageShowsTheTurnsStartUntilItHasBeenWatched() {
        val order = coalitionInTurnOrder(view().players.map { it.id }, caller)
        val film = rehearsal(view(), wholePlan())
        assertTrue(film.tables[0] != film.tables[1], "the fixture's first turn changes nothing")
        // The page of a turn also has the card that turn draws in front of its seat: every turn
        // opens with one, and it is what the turn is about to be aimed with.
        val starts = film.tables[0].drawing(order[0])

        val reading = assertNotNull(tableFor(view(), question = Question.ThePlan(at = 1), plan = wholePlan()).board)
        assertEquals(starts, reading.felt, "the page did not show the table its turn starts from")
        assertEquals(1, reading.drawing, "the turn's own draw was not marked as arriving on it")

        val watched = assertNotNull(
            tableFor(view(), question = Question.ThePlan(at = 1, landed = true), plan = wholePlan()).board,
        )
        assertEquals(film.tables[1], watched.felt, "the felt does not show what the turn did once watched")
        assertNull(watched.drawing, "a turn already watched still had a card waiting to be drawn")

        // A card picked up is a builder that is open, exactly as a chooser is: back to the start.
        val held = CardRef(order[0], 0)
        val editing = assertNotNull(
            tableFor(
                view(),
                question = Question.ThePlan(at = 1, picked = held, landed = true),
                plan = wholePlan(),
            ).board,
        )
        assertEquals(starts, editing.felt, "editing a turn was aimed at the table it leaves behind")

        val choosing = assertNotNull(
            tableFor(view(), question = Question.Doing(order[0], at = 1), plan = wholePlan()).board,
        )
        assertEquals(starts, choosing.felt, "a chooser left the felt on the turn's result")
        assertEquals(1, choosing.at, "opening a chooser sent the transport back to the start")

        val landed = assertNotNull(tableFor(view(), question = Question.ThePlan(at = lands), plan = wholePlan()).board)
        assertEquals(film.arrival, landed.felt, "the last page is not where the plan lands")
        assertNull(landed.drawing, "where the plan lands had a turn still to draw")
    }

    /** The opening is a decision only while the pile has something to take; otherwise it is a fact. */
    @Test
    fun theOpeningIsADecisionOnlyWhileThereIsSomethingToTake() {
        val order = coalitionInTurnOrder(view().players.map { it.id }, caller)
        val seat = order[0]

        fun openingOf(plan: CoalitionPlan, pile: Rank) = assertNotNull(
            tableFor(
                projectView(finalRound(discardTop = pile), me, conferMsRemaining = 20_000L),
                question = Question.ThePlan(at = 1),
                plan = plan,
            ).board?.sentence,
        ).own.slots[0]

        // A three on the pile: one pile to draw from, and nothing to touch.
        val fact = openingOf(CoalitionPlan(lanes = listOf(Lane(seat))), Rank.THREE)
        assertEquals(Says.Draws, fact.says)
        assertNull(fact.open, "a question with one answer was asked")

        // A King on the pile: "draws" is a decision, and one touch takes the King instead.
        val decision = openingOf(CoalitionPlan(lanes = listOf(Lane(seat))), Rank.KING)
        assertEquals(PlanEdit.OpenLane(seat, Opening.TAKE_THE_DISCARD), assertIs<Move.Plan>(decision.open).edit)

        // Taken, the way back is one touch too.
        val taken = openingOf(CoalitionPlan(lanes = listOf(Lane(seat, opening = Opening.TAKE_THE_DISCARD))), Rank.KING)
        assertEquals(Says.Takes(Rank.KING), taken.says)
        assertEquals(PlanEdit.OpenLane(seat, Opening.DRAW), assertIs<Move.Plan>(taken.open).edit)
    }

    // ------------------------------------------------------------------ the controls

    @Test
    fun everyTurnHasAStopThatSaysWhoseItIsAndTheLastIsWhereThePlanLands() {
        val transport = transportFor(Question.ThePlan(at = 2), seats())

        assertEquals(4, transport.stops.size, "three turns did not make four pages")
        assertEquals(seats()[0], transport.stops[0].seat, "a stop does not name its turn")
        assertEquals(seats()[2], transport.stops[2].seat)
        assertNull(transport.stops[3].seat, "where the plan lands was labelled as somebody's turn")
        assertEquals(listOf(2), transport.stops.filter { it.here }.map { it.at }, "not exactly one is lit")
        assertNull(transport.stops[1].go, "the page on screen was still offered as somewhere to go")
    }

    @Test
    fun touchingAStopIsAJumpInEitherDirection() {
        // A stop is the pager: no card flies for a swipe. The film is the two buttons' business.
        val transport = transportFor(Question.ThePlan(at = 1), seats())
        val forward = focusOf(assertNotNull(transport.stops[2].go, "there was no way to reach turn 3"))
        assertEquals(3, forward.at)
        assertNull(forward.runningTo, "touching a stop played a film")
        assertFalse(forward.landed, "a jump showed the turn's result rather than its start")

        val back = focusOf(assertNotNull(transportFor(Question.ThePlan(at = 3, landed = true), seats()).stops[0].go))
        assertEquals(1, back.at)
        assertNull(back.runningTo, "going back played a film backwards")
        assertFalse(back.landed)
    }

    @Test
    fun watchingATurnRunsItFromItsStartAndTheWholePlanRunsToWhereItLands() {
        val transport = transportFor(Question.ThePlan(at = 2), seats(), drawable = listOf(true, false, true))

        // This turn again: from the table it starts on, to its end.
        assertEquals(Question.ThePlan(at = 1, runningTo = 1), focusOf(assertNotNull(transport.stops[0].replay)))
        assertNull(transport.stops[1].replay, "a turn with nothing to watch offered a replay")
        assertNull(transport.stops[3].replay, "where the plan lands is not a turn")

        // Every turn from here: to where the plan lands, and the head parks there.
        assertEquals(Question.ThePlan(at = 2, runningTo = 4), focusOf(assertNotNull(transport.playAll)))
        // From the last page, the whole plan again from the first turn.
        assertEquals(
            Question.ThePlan(at = 1, runningTo = 4),
            focusOf(
                assertNotNull(
                    transportFor(Question.ThePlan(at = 4), seats(), drawable = listOf(true, false, true)).playAll,
                ),
            ),
        )
        // And nothing to watch past the head is nothing to play.
        assertNull(transportFor(Question.ThePlan(at = 3), seats(), drawable = listOf(true, true, false)).playAll)
    }

    @Test
    fun haltingComesToRestOnThePageItIsOn() {
        val running = transportFor(Question.ThePlan(at = 2, runningTo = 4), seats())

        assertTrue(running.stops.all { it.go == null }, "the film could be redirected mid-flight")
        assertTrue(running.stops.all { it.replay == null }, "a turn could be replayed mid-flight")
        assertNull(running.playAll, "the whole plan was offered while the film was running")
        val halted = focusOf(assertNotNull(running.halt, "a running film could not be stopped"))
        assertFalse(halted.running, "halting did not stop it")
        assertEquals(2, halted.at, "halting moved the head off the page it was on")
        assertTrue(halted.landed, "halting did not leave the result on the felt")
    }

    @Test
    fun nothingMayBeEditedWhileTheFilmRuns() {
        val standing = CoalitionPlan(lanes = listOf(Lane(nina, Step.PutDown(CardAt(nina, 0)))))

        val running = tableFor(view(), question = Question.ThePlan(at = 1, runningTo = 4), plan = standing)
        val board = assertNotNull(running.board)
        assertTrue(board.lanes.all { it.composer == null }, "a card was draggable mid-run")
        assertTrue(running.taps.isEmpty(), "a card was selectable mid-run")
        assertTrue(
            board.sentence?.clauses.orEmpty().flatMap { it.slots }.all { it.open == null },
            "a word was touchable mid-run",
        )

        val rested = tableFor(view(), question = Question.ThePlan(at = 1), plan = standing)
        assertTrue(
            assertNotNull(rested.board).lanes.any { it.composer != null },
            "coming to rest left nothing editable",
        )
        assertTrue(rested.taps.isNotEmpty(), "coming to rest left no card selectable")
    }

    @Test
    fun anEmptyPlanReachesItsFirstTurnAndNoFurther() {
        val bare = tableFor(view(), question = Question.ThePlan(), plan = CoalitionPlan())
        val empty = assertNotNull(bare.board, "a member has no plan to open")

        assertTrue(empty.lanes.all { it.step == null }, "the fixture already has a step in it")
        assertEquals(1, empty.transport.reach, "an empty plan reached past its first turn")
        // Its one open page still has something to watch — the seat draws whatever is decided —
        // and there is nothing beyond it for the whole-plan button to run to.
        assertNotNull(empty.transport.stops[0].replay, "the first turn of an empty plan could not be watched")
        assertTrue(empty.transport.stops.drop(1).all { it.replay == null }, "a closed page could be watched")
        assertNull(empty.transport.playAll, "an empty plan offered a film past the page it reaches")

        // And with every turn decided, the whole thing plays.
        val standing = assertNotNull(tableFor(view(), question = Question.ThePlan(), plan = wholePlan()).board)
        assertNotNull(standing.transport.playAll, "a whole plan cannot be watched")
    }

    @Test
    fun theHeadIsClampedToPagesThatExist() {
        // It arrives from the screen and a plan can shrink under it.
        assertEquals(lands, transportFor(Question.ThePlan(at = 99), seats()).at)
        assertEquals(1, transportFor(Question.ThePlan(at = -4), seats()).at)
        assertEquals(1, transportFor(Question.ThePlan(at = 2), seats = emptyList()).at)
    }

    // ------------------------------------------------------------------ the plan is read in order

    @Test
    fun aTurnIsClosedUntilTheOneBeforeItHasBeenDecided() {
        // A page shows the table its turn starts from, and that is the table the turn before it
        // leaves — so a turn nobody has decided is the end of the plan as far as the pager is
        // concerned. The fixture decides ① and ③ and leaves ② open, so ② is as far as it goes.
        val board = assertNotNull(tableFor(view(), question = Question.ThePlan(at = 1), plan = plan()).board)
        val stops = board.transport.stops

        assertEquals(listOf(false, false, true, true), stops.map { it.locked }, "the wrong pages are closed")
        assertNotNull(stops[1].go, "the turn after a decided one was closed")
        assertNull(stops[2].go, "a turn after an undecided one could be reached")
        assertNull(stops[3].go, "where the plan lands was reachable with a turn still open")
        assertNull(stops[2].replay, "a turn after an undecided one could be watched")
    }

    @Test
    fun theWholePlanIsReachableOnceEveryTurnIsDecided() {
        val whole = CoalitionPlan(
            lanes = plan().lanes.map { if (it.step == null) it.copy(step = Step.Bin) else it },
        )
        val stops = assertNotNull(
            tableFor(view(), question = Question.ThePlan(at = 1), plan = whole).board,
        ).transport.stops

        assertTrue(stops.none { it.locked }, "a whole plan still had a page closed")
        assertNotNull(stops[3].go, "where a whole plan lands could not be reached")
    }

    @Test
    fun theHeadComesBackWhenTheTurnBeforeItIsOpened() {
        // A teammate can open a lane while you are reading a page beyond it (design D3). The
        // page stops existing, so the head comes back to the last one that does rather than
        // drawing a table nobody can account for.
        val open = CoalitionPlan(lanes = listOf(Lane(nina, null), Lane(don, Step.Bin)))
        val board = assertNotNull(tableFor(view(), question = Question.ThePlan(at = 3), plan = open).board)

        assertEquals(1, board.at, "the head stayed on a page the plan no longer reaches")
        assertEquals(1, board.transport.at, "the transport and the page disagree about where the head is")
    }

    // ------------------------------------------------------------------ a turn nobody has decided

    @Test
    fun aTurnNobodyHasDecidedStillDrawsACardNobodyKnows() {
        // A turn happens whether or not it has been planned: the seat draws, and something
        // lands on the pile. What the plan cannot say is what either card is — so every hand
        // is left exactly as it was, and the pile's top becomes a card nobody can name, which
        // is what stops a later turn planning to take it.
        val here = view()
        val film = rehearsal(here, CoalitionPlan())

        assertNotNull(film.frames.first(), "a turn nobody had decided had nothing to watch")
        assertEquals(
            here.players.map { it.cards.size },
            film.tables[1].players.map { it.cards.size },
            "an undecided turn moved a card",
        )
        assertNull(film.tables[1].discardTop, "an undecided turn named the card it put on the pile")
        assertEquals(1, film.pileUnknown[1], "the pile was still takeable after a blind draw")
    }

    @Test
    fun theCardAnUndecidedTurnDrawsIsOnTheFeltAndIsNobodys() {
        // "Draws, and we'll see" is a card on the table, not an absence: the seat has something
        // in front of them and nobody knows what it is. Drawn rose, like every other card the
        // plan has dealt rather than found (design D6).
        val board = assertNotNull(
            tableFor(view(), question = Question.ThePlan(at = 1), plan = CoalitionPlan()).board,
        )
        val order = coalitionInTurnOrder(view().players.map { it.id }, caller)

        assertEquals(1, board.drawing, "the turn's own draw was not on the felt")
        val pending = assertNotNull(board.felt?.pendingAction, "nothing was in front of the seat on the page")
        assertEquals(order[0], pending.playerId, "the draw was put in front of the wrong seat")
    }

    // ------------------------------------------------------------------ fixtures

    /** The three coalition seats the fixture's plan runs over, in turn order. */
    private fun seats(): List<Speaker?> = listOf(Speaker.Named(nina), Speaker.Named(don), Speaker.You)

    private fun focusOf(move: Move.Quiet): Question.ThePlan {
        val ask = move as Move.Ask
        return ask.question as Question.ThePlan
    }

    /**
     * Three turns: the King off the pile named at fives, one still undecided, then my own King
     * put down, called and pointed at Don's six. Every action has a card face up to play it
     * with, which is the rule the film holds — a bare trade on a blind draw draws nothing.
     */
    private fun plan() = CoalitionPlan(
        lanes = listOf(
            Lane(nina, Step.Declare(Rank.FIVE), opening = Opening.TAKE_THE_DISCARD),
            Lane(don, null),
            Lane(me, Step.PutDown(CardAt(me, 0), guess = Rank.KING, then = Step.Declare(Rank.SIX, CardAt(don, 0)))),
        ),
    )

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
        knownCardPositions = if (id == me) listOf(0) else emptyList(),
        isVintoCaller = id == caller,
        coalitionWith = if (id == caller) emptyList() else listOf(me, nina, don) - id,
        claims = claims,
    )

    /** A final round the bot in seat two called, with an unused King lying on the pile. */
    private fun finalRound(discardTop: Rank = Rank.KING) = GameState(
        gameId = "transport",
        roundNumber = 1,
        turnNumber = 12,
        phase = GamePhase.FINAL,
        subPhase = GameSubPhase.IDLE,
        finalTurnTriggered = true,
        players = listOf(
            seat(me, listOf(Rank.KING, Rank.TWO), claims = listOf(Claim(me, listOf(0), listOf(Rank.KING)))),
            seat(caller, listOf(Rank.KING, Rank.TWO)),
            seat(nina, listOf(Rank.FIVE, Rank.SEVEN), claims = listOf(Claim(nina, listOf(0), listOf(Rank.FIVE)))),
            seat(don, listOf(Rank.SIX), claims = listOf(Claim(don, listOf(0), listOf(Rank.SIX)))),
        ),
        currentPlayerIndex = 1,
        vintoCallerId = caller,
        coalitionLeaderId = null,
        drawPile = Pile((0..6).map { card(Rank.FOUR, "draw-$it") }),
        discardPile = Pile(listOf(card(discardTop, "discard-top"))),
        pendingAction = null,
        activeTossIn = null,
        turnActions = emptyList(),
        roundActions = emptyList(),
        roundFailedAttempts = emptyList(),
        difficulty = Difficulty.MODERATE,
        rngState = 0,
    )

    private fun view(viewer: String = me): PlayerView =
        projectView(finalRound(), viewer, conferMsRemaining = 20_000L)
}
