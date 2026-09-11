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
import game.vinto.shapes.Shed
import game.vinto.shapes.Step
import game.vinto.shapes.VintoJson
import game.vinto.shapes.coalitionInTurnOrder
import game.vinto.shapes.getCardShortDescription
import game.vinto.shapes.getCardValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The plan read as a film: where it stops, what each stop shows, and what may be done there.
 *
 * The transport is the part of this change with no counterpart in the old rail, so everything
 * about it is pinned here — that its positions *are* the turn boundaries and there is nothing
 * between two of them (design D14), that position *n* shows the table after turns 1..*n* and no
 * further, that a run ends on the state the plan arrives at, and that editing sleeps while it
 * runs. The one thing it must not do is touch the plan, which is the first test below.
 */
class TransportTest {

    private val me = "human-1"
    private val caller = "bot-2"
    private val nina = "bot-3"
    private val don = "bot-4"

    // ------------------------------------------------------------------ the plan is untouched

    @Test
    fun readingThePlanFromADifferentTurnAddsNothingToTheWire() {
        // Which turn a member is looking at is the screen's business and must not travel: two
        // members reading different turns of one plan is normal, and a shared cursor would
        // fight (design D3). Compared as bytes rather than as fields, so a new field on
        // `CoalitionPlan` cannot slip in behind this.
        val standing = plan()
        val before = VintoJson.encodeToString(CoalitionPlan.serializer(), standing)

        for (at in 0..4) {
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

        // Turn 1 names fives and empties Nina's; turn 3 names sixes and empties Don's. So
        // position 1 has done the first and not the third, and position 3 has done both —
        // which is what "and no further" has to mean to be worth asserting.
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
        // ② has to mean the same turn to every member reading it, so a lane with no step is a
        // stop the transport passes through rather than one it skips (design D6).
        val undecided = CoalitionPlan(
            lanes = listOf(
                Lane(nina, swap(nina, 0, don, 0)),
                Lane(don, null),
                Lane(me, null),
            ),
        )
        val stops = rehearsal(view(), undecided)

        assertEquals(4, stops.tables.size, "an undecided turn was dropped from the film")
        assertNotNull(stops.frames[0], "the decided turn drew nothing")
        assertNull(stops.frames[1], "an undecided turn invented a picture")
        assertEquals(stops.tables[1], stops.tables[2], "an undecided turn moved a card")
    }

    /**
     * Position *k* is the coalition's *k*th **turn**, not the plan's *k*th decision.
     *
     * `CoalitionPlan.lanes` holds only the turns somebody has set, so its length counts
     * decisions. Read as positions, a board with one decided turn had one position: ① showed
     * that turn's step already taken and attributed to whoever sits before it, and ② and ③ fell
     * off the end and showed the table **as it is now** — so the arrival, the one picture the
     * transport exists for, was the present. Reported from a phone as not being able to see how
     * the cards would look at the end, on a board where two of three turns said "your call".
     */
    @Test
    fun everyCoalitionTurnIsAPositionEvenWhenNobodyHasDecidedIt() {
        val film = rehearsal(view(), oneDecidedTurn())

        assertEquals(4, film.tables.size, "three turns did not make four positions")
        assertEquals(3, film.turns, "a turn nobody has decided lost its place")
        assertEquals(listOf(false, true, false), film.frames.map { it != null })

        assertEquals(view(), film.tables[1], "an undecided turn moved a card")
        assertTrue(film.tables[2] != film.tables[1], "the decided turn changed nothing")
        assertEquals(film.tables[2], film.tables[3], "an undecided turn moved a card")
        assertTrue(film.arrival != view(), "the plan's arrival is the table as it is now")
    }

    /**
     * Pressing play part-way through a plan plays what is left of it.
     *
     * A position is an index into [Rehearsal.frames], nulls and all. Filtering the nulls out
     * first and then dropping by position mixes two different numbers: on this plan the filtered
     * film is one frame long, so a head parked on turn 1 dropped the only move there was and
     * play ran an empty film — the transport travelled to the end and not one card moved.
     * Reported from a phone as *"why does play not play all the cards?"*.
     */
    @Test
    fun theFilmFromAPositionKeepsEveryTurnStillToCome() {
        val film = rehearsal(view(), oneDecidedTurn())

        assertEquals(1, film.from(0).size, "the whole film is missing its only move")
        assertEquals(1, film.from(1).size, "the step was dropped by a position that is not its own")
        assertEquals(0, film.from(2).size, "a move already played was played again")
        assertEquals(0, film.from(3).size, "the arrival still had something to play")

        // And between two stops, which is what a named stop asks for: the turns in between and
        // no more, so pressing ② watches the first two turns and stops there.
        assertEquals(0, film.between(0, 1).size, "the first turn has nothing in it to watch")
        assertEquals(1, film.between(0, 2).size, "watching two turns missed the one with a step")
        assertEquals(1, film.between(1, 3).size)
    }

    /**
     * Three coalition turns in turn order, with only the middle one decided.
     *
     * The shape a phone reported: the bots propose one thing and have nothing to say about the
     * other two turns, so two of the three lanes read "your call" and only one is stored.
     */
    private fun oneDecidedTurn(): CoalitionPlan {
        val order = coalitionInTurnOrder(view().players.map { it.id }, caller)
        assertEquals(listOf(nina, don, me), order, "the fixture's turn order has moved")
        // A declaration rather than a swap: two *hidden* cards changing places leaves the view
        // identical, so there would be nothing to assert about the picture.
        return CoalitionPlan(lanes = listOf(Lane(order[1], Step.Declare(Rank.SIX))))
    }

    /**
     * A card the plan has put down is an unseen draw from the stop after it, and not before.
     *
     * A put-down does not leave a gap: the seat draws off the deck, face down, and nobody knows
     * what that card is — not even the seat holding it. On the felt it wears the same back as
     * every other card, so a hand read at a later stop mixed a known quantity and a lottery
     * ticket with nothing to tell them apart. Asked for from a phone: *"drawn cards during the
     * plan must be a different colour"*.
     */
    @Test
    fun aCardThePlanPutsDownReadsAsAnUnseenDrawFromTheStopAfterIt() {
        val order = coalitionInTurnOrder(view().players.map { it.id }, caller)
        val mine = CardAt(me, 0)
        val standing = CoalitionPlan(lanes = listOf(Lane(me, Step.PutDown(mine))))
        val turn = order.indexOf(me) + 1

        // Before that turn has been played, the card is still the one this seat remembers.
        val before = assertNotNull(tableFor(view(), question = Question.ThePlan(at = turn - 1), plan = standing).board)
        assertTrue(CardRef(me, 0) !in before.fresh, "a draw was marked before the turn that draws it")

        // From the stop after it, it is a card nobody has seen.
        val after = assertNotNull(tableFor(view(), question = Question.ThePlan(at = turn), plan = standing).board)
        assertEquals(setOf(CardRef(me, 0)), after.fresh, "the put-down left no unseen draw behind it")

        // And a plan that puts nothing down marks nothing, which is the usual case.
        val quiet = assertNotNull(tableFor(view(), question = Question.ThePlan(at = 3), plan = plan()).board)
        assertTrue(quiet.fresh.isEmpty(), "a plan with no put-down marked a card as unseen")
    }

    /**
     * The turn reads as a row of parts, in the order they happen.
     *
     * It was a sentence under a row of verbs — "Turn 1, Tide: swap Tide's card 1 with your card
     * 1", then DECLARE A RANK and CLEAR — and neither said which of the two piles the card came
     * from, because three of the four steps never recorded it. Reported from a phone twice, the
     * second time as *"think not as actions but as a plan builder"*.
     */
    @Test
    fun theTurnBeingBuiltReadsAsItsPartsInTheOrderTheyHappen() {
        val order = coalitionInTurnOrder(view().players.map { it.id }, caller)
        val standing = CoalitionPlan(
            lanes = listOf(Lane(order[0], Step.Declare(Rank.SIX), opening = Opening.TAKE_THE_DISCARD)),
            sheds = listOf(Shed(order[0], Rank.SIX)),
        )
        val parts = assertNotNull(
            tableFor(view(), question = Question.ThePlan(at = 1), plan = standing).board?.turn,
            "the belt has no turn to build",
        )

        assertEquals(Opening.TAKE_THE_DISCARD, parts.opening, "the pile the card comes from is not recorded")
        assertEquals(PlayKind.PLAY_IT, parts.play, "using a card's action is not reading as playing it")
        assertEquals(StepLine.Declare(Rank.SIX), parts.detail, "the rank the King names is not on the row")
        assertEquals(listOf(Rank.SIX), parts.tossers.map { it.rank }, "who throws in is not on the turn")

        // Every part opens the question it is about, and the opening offers the other pile.
        assertNotNull(parts.changePlay, "what to do with the card cannot be changed")
        assertNotNull(parts.changeDetail, "the rank cannot be changed")
        assertNotNull(parts.addToss, "nobody else can be asked to throw in")
    }

    /**
     * The three things a turn can do with its card, and the two piles it can take one from.
     *
     * "Play it" is using the card's own action — which for a Jack or a Queen *is* a swap, and is
     * why the middle part is not called "swap": one is what you do with the card, the other is
     * what the card does. Getting those two confused is what made the old row unreadable.
     */
    @Test
    fun eachKindOfStepLandsOnTheRightPartOfTheRow() {
        val order = coalitionInTurnOrder(view().players.map { it.id }, caller)
        val seat = order[0]

        fun partsFor(step: Step?, opening: Opening? = null) = assertNotNull(
            tableFor(
                view(),
                // Stop 1 is the first coalition turn, which is `seat`'s.
                question = Question.ThePlan(at = 1),
                plan = CoalitionPlan(lanes = listOf(Lane(seat, step, opening = opening))),
            ).board?.turn,
        )

        assertEquals(PlayKind.PLAY_IT, partsFor(Step.UseIt).play)
        assertEquals(PlayKind.PLAY_IT, partsFor(Step.Declare(Rank.SIX)).play)
        assertEquals(PlayKind.PLAY_IT, partsFor(Step.TakeTheDiscard).play)
        assertEquals(PlayKind.KEEP_IT, partsFor(Step.PutDown(CardAt(seat, 0))).play)
        assertEquals(PlayKind.BIN_IT, partsFor(Step.Bin).play)

        // Nothing said yet is a row with its parts empty rather than a row that is not there:
        // the turn exists either way, and it is the empty one a member most needs to fill.
        val blank = partsFor(null)
        assertNull(blank.opening, "an untouched turn arrived with a pile already chosen")
        assertNull(blank.play, "an untouched turn arrived with something already decided")
        assertNull(blank.detail, "an untouched turn names something")

        // And letting the card go names nothing beyond itself, so it has no third part.
        assertNull(partsFor(Step.Bin).detail, "letting a card go named something")
    }

    /** The caller reads the plan and taps none of it; nothing on the row is theirs to change. */
    @Test
    fun theCallerIsOfferedNoPartOfTheRow() {
        val order = coalitionInTurnOrder(view().players.map { it.id }, caller)
        val standing = CoalitionPlan(lanes = listOf(Lane(order[0], Step.UseIt)))
        val parts = assertNotNull(
            tableFor(view(viewer = caller), question = Question.ThePlan(at = 1), plan = standing).board?.turn,
        )

        assertNull(parts.changeOpening, "the caller was offered the pile")
        assertNull(parts.changePlay, "the caller was offered the turn")
        assertNull(parts.addToss, "the caller was offered a throw-in")
    }

    /**
     * The lit stop and the belt name the same turn.
     *
     * A stop named "Tide" is where Tide's turn has just happened, so Tide's turn is the one to
     * build there. It used to build the turn that *starts* at the stop rather than the one that
     * ends there, so the header said "Tide" while the belt below built Dune's — and tapping the
     * rail's third row lit the header's second. Reported from a phone as the two being a
     * position out of step.
     */
    @Test
    fun theLitStopAndTheBeltNameTheSameTurn() {
        val order = coalitionInTurnOrder(view().players.map { it.id }, caller)

        for ((turn, seat) in order.withIndex()) {
            val board = assertNotNull(
                tableFor(view(), question = Question.ThePlan(at = turn + 1), plan = plan()).board,
            )
            val lit = assertNotNull(board.transport.stops.first { it.here }.seat, "the lit stop names nobody")
            assertEquals(speakerFor(view(), seat), lit, "stop ${turn + 1} names the wrong seat")
            assertEquals(lit, board.turn?.who, "the belt builds a different turn from the one lit")
        }

        // And at "now" there is no turn to build: it is the table before the plan begins.
        val atNow = assertNotNull(tableFor(view(), question = Question.ThePlan(at = 0), plan = plan()).board)
        assertNull(atNow.turn, "the table as it is offered a turn to build")
        assertNull(atNow.building, "the table as it is offered a composer")
    }

    /**
     * Watching and editing want different tables, and the felt gives each of them its own.
     *
     * Parked on Tide's stop you have just watched Tide's turn, so the felt shows its **result** —
     * that is the whole reason for going there. But to *change* Tide's turn you need the table
     * Tide starts from. So the felt steps back the moment a part of the turn is open, and
     * returns to the result when it is not.
     */
    @Test
    fun theFeltShowsTheResultUntilAPartOfTheTurnIsOpened() {
        val order = coalitionInTurnOrder(view().players.map { it.id }, caller)
        // Turn 1 is a King's declare, which empties a claimed rank out of the coalition's hands
        // — a difference the felt can actually show.
        val film = rehearsal(view(), plan())
        assertTrue(film.tables[0] != film.tables[1], "the fixture's first turn changes nothing")

        val watching = assertNotNull(tableFor(view(), question = Question.ThePlan(at = 1), plan = plan()).board)
        assertEquals(film.tables[1], watching.felt, "the felt does not show what the turn did")

        // A card picked up is a builder that is open, exactly as a chooser is.
        val held = CardRef(order[0], 0)
        val editing = assertNotNull(
            tableFor(view(), question = Question.ThePlan(at = 1, picked = held), plan = plan()).board,
        )
        assertEquals(film.tables[0], editing.felt, "editing a turn was aimed at the table it leaves behind")

        // And so is a chooser: the same table, reached the other way.
        val choosing = assertNotNull(
            tableFor(view(), question = Question.Doing(order[0], at = 1), plan = plan()).board,
        )
        assertEquals(film.tables[0], choosing.felt, "a chooser left the felt on the turn's result")
        assertEquals(1, choosing.at, "opening a chooser sent the transport back to the start")
    }

    /**
     * An undecided opening can always be answered, because every turn can draw.
     *
     * The tap offered "whichever of the two piles is not already chosen", and with nothing chosen
     * that is *take the discard* — legal only while the pile has an unused action card on it.
     * Over a played card the part was therefore dead, and drawing, the one opening that is always
     * legal, could not be chosen at all. Reported from a phone as nothing happening when the belt
     * was tapped.
     */
    @Test
    fun theOpeningCanAlwaysBeAnsweredEvenWithNothingWorthTakingOffThePile() {
        val order = coalitionInTurnOrder(view().players.map { it.id }, caller)
        val seat = order[0]

        fun openingOf(plan: CoalitionPlan) = assertNotNull(
            tableFor(view(), question = Question.ThePlan(at = 1), plan = plan).board?.turn,
        )

        // The fixture's discard is a three — no action to take — so taking is not on offer at
        // all, and an undecided opening must still answer something.
        val blank = openingOf(CoalitionPlan(lanes = listOf(Lane(seat))))
        val first = assertNotNull(blank.changeOpening, "an undecided opening could not be answered")
        assertEquals(
            PlanEdit.OpenLane(seat, Opening.DRAW),
            (first as Move.Plan).edit,
            "the first answer to an opening is not the one that is always legal",
        )

        // And once it says draw, there is nothing else it could say here: the pile holds nothing
        // worth taking, so the part is answered rather than dead.
        val drawn = openingOf(CoalitionPlan(lanes = listOf(Lane(seat, opening = Opening.DRAW))))
        assertNull(drawn.changeOpening, "a pile with nothing on it was offered as an alternative")

        // Taking, where it is legal, is the other answer — and from there the way back is draw.
        val taken = openingOf(CoalitionPlan(lanes = listOf(Lane(seat, opening = Opening.TAKE_THE_DISCARD))))
        assertEquals(
            PlanEdit.OpenLane(seat, Opening.DRAW),
            (assertNotNull(taken.changeOpening) as Move.Plan).edit,
            "there is no way back from taking the discard",
        )
    }

    // ------------------------------------------------------------------ the controls

    @Test
    fun everyTurnHasAStopThatSaysWhoseItIsAndOneOfThemIsLit() {
        // The whole of what the shuttle could not do. Back / Play / Next says how to travel and
        // never where you are: the same felt meant "now" and "after the plan", the difference
        // was how many times you had pressed, and the position everybody wants — how the hands
        // end up — announced itself nowhere. A stop per turn, named, with the one being read lit.
        val transport = transportFor(Question.ThePlan(at = 2), seats())

        assertEquals(4, transport.stops.size, "three turns did not make four positions")
        assertNull(transport.stops[0].seat, "the table as it is was labelled as somebody's turn")
        assertEquals(seats()[0], transport.stops[1].seat, "a stop does not name the turn it ends")
        assertEquals(seats()[2], transport.stops[3].seat)

        assertEquals(listOf(2), transport.stops.filter { it.here }.map { it.at }, "not exactly one is lit")
        assertNull(transport.stops[2].go, "the stop the head is on was still offered as somewhere to go")
    }

    @Test
    fun goingForwardOverSomethingToWatchPlaysTheFilmToThatStop() {
        // The point of the plan is that a coalition *watches* it. So a stop ahead of the head is
        // a film: the head stays where it is and travels, and the screen parks it on arrival.
        val transport = transportFor(Question.ThePlan(at = 0), seats())
        val watching = focusOf(assertNotNull(transport.stops[2].go, "there was no way to reach turn 2"))

        assertEquals(2, watching.runningTo, "pressing a stop did not send the film to it")
        assertEquals(0, watching.at, "the head jumped instead of travelling")
        assertTrue(watching.running, "the transport did not start")

        // And the last stop is the plan's arrival, which is the one this was reported for.
        assertEquals(3, focusOf(assertNotNull(transport.stops[3].go)).runningTo)
    }

    @Test
    fun goingBackIsAJumpBecauseCardsDoNotFlyInReverse() {
        val transport = transportFor(Question.ThePlan(at = 3), seats())
        val back = focusOf(assertNotNull(transport.stops[1].go, "there was no way back to turn 1"))

        assertEquals(1, back.at, "going back did not move the head")
        assertNull(back.runningTo, "going back played a film backwards")
    }

    @Test
    fun aStopWithNothingToWatchOnTheWayIsAJumpRatherThanAFilmOfNothing() {
        // Offered as a film anyway, "play" ran no frames at all: the head travelled to the end,
        // not one card moved, and the member was left looking at a transport parked past three
        // turns that all still said "your call". Reported from a phone twice.
        val nothing = transportFor(Question.ThePlan(at = 0), seats(), drawable = listOf(false, false, false))
        assertEquals(0, focusOf(assertNotNull(nothing.stops[3].go)).runningTo?.let { 1 } ?: 0)
        assertEquals(3, focusOf(assertNotNull(nothing.stops[3].go)).at, "an empty film was played")

        // One turn worth watching between here and there is enough to make it a film.
        val some = transportFor(Question.ThePlan(at = 0), seats(), drawable = listOf(false, true, false))
        assertEquals(3, focusOf(assertNotNull(some.stops[3].go)).runningTo)

        // But not when it is behind the head: from turn 2 the only turn with anything in it has
        // already been played, so reaching the end is a jump.
        val past = transportFor(Question.ThePlan(at = 2), seats(), drawable = listOf(false, true, false))
        assertEquals(3, focusOf(assertNotNull(past.stops[3].go)).at)
        assertNull(focusOf(assertNotNull(past.stops[3].go)).runningTo)
    }

    @Test
    fun haltingComesToRestOnABoundaryAndNeverBetweenTwo() {
        // The detent is structural: a position is a *count of turns*, so there is no value the
        // head can take that shows cards in flight. Halting parks it where it already is.
        val running = transportFor(Question.ThePlan(at = 2, runningTo = 3), seats())

        assertTrue(running.stops.all { it.go == null }, "the film could be redirected mid-flight")
        val halted = focusOf(assertNotNull(running.halt, "a running film could not be stopped"))
        assertTrue(!halted.running, "halting did not stop it")
        assertEquals(2, halted.at, "halting moved the head off the boundary it was on")
        assertTrue(halted.at in 0..seats().size, "halting left the head outside the film")
    }

    @Test
    fun nothingMayBeEditedWhileTheFilmRuns() {
        // A card halfway between two seats is at no position, so there is nothing to drop onto
        // and nothing to drag. The affordances sleep rather than misfire (design D14).
        val standing = plan()
        val running = tableFor(view(), question = Question.ThePlan(at = 1, runningTo = 3), plan = standing)
        val board = assertNotNull(running.board)

        assertTrue(board.lanes.all { it.composer == null }, "a card was draggable mid-run")
        assertTrue(running.taps.isEmpty(), "a card was selectable mid-run")

        val rested = tableFor(view(), question = Question.ThePlan(at = 1), plan = standing)
        assertTrue(
            assertNotNull(rested.board).lanes.any { it.composer != null },
            "coming to rest did not wake the composer again",
        )
        assertTrue(rested.taps.isNotEmpty(), "coming to rest left no card selectable")
    }

    @Test
    fun anEmptyPlanIsSteppedThroughRatherThanWatched() {
        val bare = tableFor(view(), question = Question.ThePlan(), plan = CoalitionPlan())
        val empty = assertNotNull(bare.board, "a member has no plan to open")

        assertTrue(empty.lanes.all { it.step == null }, "the fixture already has a step in it")
        assertTrue(
            empty.transport.stops.filterNot { it.here }.all { focusOf(assertNotNull(it.go)).runningTo == null },
            "an empty plan offered a film of nothing to watch",
        )

        // And with a step in it, the film is back.
        val standing = tableFor(view(), question = Question.ThePlan(), plan = plan())
        // From the first turn, the last stop is two turns ahead and one of them has a step in
        // it, so getting there is a film rather than a jump.
        val stops = assertNotNull(standing.board).transport.stops
        assertNotNull(
            focusOf(assertNotNull(stops.last().go)).runningTo,
            "a plan with steps in it cannot be watched",
        )
    }

    @Test
    fun theHeadIsClampedToPositionsThatExist() {
        // It arrives from the screen and a plan can shrink under it — another member clears the
        // last lane while this one is parked past it.
        assertEquals(seats().size, transportFor(Question.ThePlan(at = 99), seats()).at)
        assertEquals(0, transportFor(Question.ThePlan(at = -4), seats()).at)
        assertEquals(0, transportFor(Question.ThePlan(at = 2), seats = emptyList()).at)
    }

    // ------------------------------------------------------------------ fixtures

    /** The three coalition seats the fixture's plan runs over, in turn order. */
    private fun seats(): List<Speaker?> = listOf(Speaker.Named(nina), Speaker.Named(don), Speaker.You)

    private fun focusOf(move: Move.Quiet): Question.ThePlan {
        val ask = move as Move.Ask
        return ask.question as Question.ThePlan
    }

    /**
     * Three turns: a King, one still undecided, another King.
     *
     * Two declarations rather than two swaps, because a swap of two *hidden* cards leaves the
     * view identical — the rehearsal transforms the view and cannot invent information, so two
     * face-down cards changing places is a picture with no readable difference behind it. A
     * King empties a claimed rank out of the coalition's hands, which is a difference this can
     * point at. The swap has its own test below, where what is asserted is the picture.
     */
    private fun plan() = CoalitionPlan(
        lanes = listOf(
            Lane(nina, Step.Declare(Rank.FIVE)),
            Lane(don, null),
            Lane(me, Step.Declare(Rank.SIX)),
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

    private fun finalRound() = GameState(
        gameId = "transport",
        roundNumber = 1,
        turnNumber = 12,
        phase = GamePhase.FINAL,
        subPhase = GameSubPhase.IDLE,
        finalTurnTriggered = true,
        players = listOf(
            seat(me, listOf(Rank.NINE, Rank.TWO), claims = listOf(Claim(me, listOf(0), listOf(Rank.NINE)))),
            seat(caller, listOf(Rank.KING, Rank.TWO)),
            seat(nina, listOf(Rank.FIVE, Rank.SEVEN), claims = listOf(Claim(nina, listOf(0), listOf(Rank.FIVE)))),
            seat(don, listOf(Rank.SIX), claims = listOf(Claim(don, listOf(0), listOf(Rank.SIX)))),
        ),
        currentPlayerIndex = 1,
        vintoCallerId = caller,
        coalitionLeaderId = null,
        drawPile = Pile((0..6).map { card(Rank.FOUR, "draw-$it") }),
        discardPile = Pile(listOf(card(Rank.THREE, "discard-top"))),
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

    private fun swap(from: String, fromPos: Int, to: String, toPos: Int) =
        Step.Swap(CardAt(from, fromPos), CardAt(to, toPos))
}
