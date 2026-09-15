package game.vinto.bot

import game.vinto.shapes.Claim
import game.vinto.shapes.CoalitionPlan
import game.vinto.shapes.GamePhase
import game.vinto.shapes.GameState
import game.vinto.shapes.Lane
import game.vinto.shapes.Opening
import game.vinto.shapes.Pile
import game.vinto.shapes.PlanEdit
import game.vinto.shapes.PlayerState
import game.vinto.shapes.Rank
import game.vinto.shapes.Step
import game.vinto.shapes.coalitionInTurnOrder
import game.vinto.shapes.edited
import game.vinto.shapes.getCardValue
import game.vinto.shapes.laneOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What the bots put on the board (task 2.5, as adopted): the concentration play, one lane at a
 * time, computed from the shared picture and proposed rather than played. Bots seed empty lanes,
 * never touch a lane a person has set or cleared, and only bother while a person is there.
 */
class BoardProposalsTest {

    private val caller = "bot-2"
    private val me = "human-1"
    private val nina = "bot-3"
    private val don = "bot-4"

    private var counter = 0
    private fun card(rank: Rank) = testCard(rank, "${rank.serialName}-${counter++}")

    private fun seat(
        id: String,
        ranks: List<Rank>,
        claims: List<Claim>? = null,
        read: List<Int> = if (id == me) emptyList() else ranks.indices.toList(),
    ): PlayerState =
        testPlayer(id, id, isHuman = id == me, cards = ranks.map(::card), knownCardPositions = read)
            .copy(claims = claims, isVintoCaller = id == caller)

    /**
     * The caller is in seat two, so the coalition plays Nina, Don, then the person. Nina holds
     * a Jack to put down and call, a two she could give away and a ten she would like rid of;
     * Don holds a King and a seven; the person has said they hold a Jack and a nine.
     *
     * The Jacks are the point: a plan can only aim a trade with a card the table can see, so
     * a bot proposes a trade by putting a Jack down and calling it, never on a blind draw.
     */
    private fun table(): GameState = testState(
        players = listOf(
            seat(
                me,
                listOf(Rank.JACK, Rank.NINE),
                claims = listOf(Claim(me, listOf(0), listOf(Rank.JACK)), Claim(me, listOf(1), listOf(Rank.NINE))),
            ),
            seat(caller, listOf(Rank.SIX)),
            seat(nina, listOf(Rank.JACK, Rank.TWO, Rank.TEN)),
            seat(don, listOf(Rank.KING, Rank.SEVEN)),
        ),
        phase = GamePhase.FINAL,
        vintoCallerId = caller,
    ).let { it.copy(currentPlayerIndex = it.players.indexOfFirst { p -> p.id == caller }) }

    /**
     * A coalition of nothing but bots still writes its plan down, because the caller reads it.
     *
     * The one round where the person is on the *other* side: they called Vinto, so the three
     * seats against them are all bots. Nothing was seeded at all — the seeding stopped where
     * there was no human *in the coalition* to seed it for — so the caller called, watched three
     * bots take their turns, and never saw a word of what they intended.
     *
     * The plan is public and the caller may read it (design D12): it is built from claims the
     * table has already heard, so there is nothing in it to hide. And it is the whole tension of
     * the round they just started — "will they beat me?" — which is the one question the screen
     * was answering with a blank. Reported from a phone: *"I as vinto do not see bots plan"*.
     */
    @Test
    fun aCoalitionOfNothingButBotsStillWritesItsPlanDownForTheCallerToRead() {
        // The person calls, so the three seats against them are bots.
        val called = table().let { state ->
            state.copy(
                players = state.players.map { it.copy(isVintoCaller = it.id == me) },
                vintoCallerId = me,
                currentPlayerIndex = state.players.indexOfFirst { it.id == me },
            )
        }

        val seeded = seedTheBoard(called, plan = null)
        val coalition = coalitionInTurnOrder(called.players.map { it.id }, me)
        assertEquals(listOf(caller, nina, don), coalition, "the coalition is not the three bots")

        assertTrue(
            coalition.any { seeded.plan.laneOf(it)?.step != null },
            "an all-bot coalition planned nothing, so the caller has nothing to read",
        )
        assertTrue(
            seeded.plan.lanes.all { lane -> lane.step.cardsTouched().none { it == me } },
            "a proposal reached for the caller's cards",
        )
        assertTrue(seeded.said.isNotEmpty(), "the bots agreed a line without saying anything about it")
    }

    /** Whose cards a step names, for the check above. */
    private fun Step?.cardsTouched(): List<String> = when (this) {
        is Step.Swap -> listOf(from.seat, to.seat)
        is Step.PutDown -> listOf(card.seat)
        else -> emptyList()
    }

    @Test
    fun theBotsFillLanesWhileATradeStillLowersTheLowestHandAndThenStop() {
        // The first lane gets the trade that leaves one hand on two. After that no trade can
        // lower the lowest hand further, so the later lanes are left as "your call" — a plan
        // says what helps, not something for every turn.
        val seeded = seedTheBoard(table(), plan = null)

        val order = coalitionInTurnOrder(table().players.map { it.id }, caller)
        assertEquals(listOf(nina, don, me), order)
        val first = assertIs<Step.PutDown>(
            assertNotNull(seeded.plan.laneOf(nina), "no proposal for the first lane").step,
        )
        assertEquals(Rank.JACK, first.guess, "the Jack put down was not called")
        val trade = assertIs<Step.Swap>(first.then, "the called Jack trades nothing")
        assertTrue(trade.from.seat != caller && trade.to.seat != caller, "a proposal reached for the caller's cards")
        assertTrue(trade.from.seat != trade.to.seat, "a swap within one hand")
        assertNull(seeded.plan.laneOf(don)?.step, "a trade was proposed that could not lower the lowest hand")
        assertNull(seeded.plan.laneOf(me)?.step)
        assertEquals(nina, seeded.plan.editedBy, "a bot's proposal was signed by somebody else")
        assertTrue(nina in seeded.plan.agreed && don in seeded.plan.agreed, "the bots did not nod to their own line")
    }

    @Test
    fun aProposalIsAnchoredToWhatTheTableSaid() {
        // The person has said their one card is a two; the only trade that lowers the lowest
        // hand below it is Don's King for it. The step names the person's card by the claim,
        // so it can follow the card if a Jack moves it before the turn comes.
        val lowPerson = testState(
            players = listOf(
                seat(me, listOf(Rank.TWO), claims = listOf(Claim(me, listOf(0), listOf(Rank.TWO)))),
                seat(caller, listOf(Rank.SIX)),
                seat(nina, listOf(Rank.JACK, Rank.NINE)),
                seat(don, listOf(Rank.SEVEN, Rank.KING)),
            ),
            phase = GamePhase.FINAL,
            vintoCallerId = caller,
        ).let { it.copy(currentPlayerIndex = it.players.indexOfFirst { p -> p.id == caller }) }

        val seeded = seedTheBoard(lowPerson, plan = null)
        val called = assertIs<Step.PutDown>(assertNotNull(seeded.plan.laneOf(nina)).step)
        val step = assertIs<Step.Swap>(called.then)
        val persons = listOf(step.from, step.to).single { it.seat == me }
        assertEquals(Claim(me, listOf(0), listOf(Rank.TWO)), persons.anchor, "the step cannot follow its card")
        val dons = listOf(step.from, step.to).single { it.seat == don }
        assertEquals(1, dons.position, "the King is not the card that moves")
        assertNull(dons.anchor, "a card nobody has spoken about got an anchor")
    }

    @Test
    fun theBotsStopTheMomentAPersonHasEditedTheBoard() {
        val state = table()
        val coalition = coalitionInTurnOrder(state.players.map { it.id }, caller)
        val seeded = seedTheBoard(state, plan = null).plan

        // The person clears Don's lane. It stays clear: the board is theirs now.
        val cleared = (
            seeded.edited(
                PlanEdit.ClearLane(don),
                me,
                coalition,
                caller,
            ) as game.vinto.shapes.PlanEditOutcome.Edited
            ).plan
        val again = seedTheBoard(state, cleared)
        assertNull(again.plan.laneOf(don), "the bots refilled a lane the person cleared")
        assertEquals(cleared, again.plan, "the bots edited a board a person had touched")
    }

    @Test
    fun aLockedLaneAndTheTurnInProgressAreLeftAlone() {
        val state = table().let { it.copy(currentPlayerIndex = it.players.indexOfFirst { p -> p.id == nina }) }
        val standing = CoalitionPlan(
            lanes = listOf(Lane(don, Step.TakeTheDiscard, locked = true)),
            agreed = listOf(don),
            editedBy = don,
        )

        val seeded = seedTheBoard(state, standing)

        assertNull(seeded.plan.laneOf(nina), "the turn in progress was given a lane")
        assertEquals(Step.TakeTheDiscard, seeded.plan.laneOf(don)?.step, "a locked lane was replaced")
        assertNotNull(seeded.plan.laneOf(me)?.step, "the one open lane was left empty")
    }

    @Test
    fun nothingIsProposedWithoutAPersonToReadIt() {
        val allBots = table().let { s -> s.copy(players = s.players.map { it.copy(isHuman = false, isBot = true) }) }
        assertTrue(seedTheBoard(allBots, plan = null).plan.isEmpty, "three bots planned on a board nobody reads")
    }

    @Test
    fun nothingIsProposedWhenNoTradeHelps() {
        // One card each, every one a two and every one spoken for: no trade changes the lowest
        // hand, so nothing is worth saying. Spoken for, because an unspoken card is priced at
        // the deck's average in the shared picture, and trading a known two for it *is* a gain.
        val flat = testState(
            players = listOf(
                seat(me, listOf(Rank.TWO), claims = listOf(Claim(me, listOf(0), listOf(Rank.TWO)))),
                seat(caller, listOf(Rank.SIX)),
                seat(nina, listOf(Rank.TWO), claims = listOf(Claim(nina, listOf(0), listOf(Rank.TWO)))),
                seat(don, listOf(Rank.TWO), claims = listOf(Claim(don, listOf(0), listOf(Rank.TWO)))),
            ),
            phase = GamePhase.FINAL,
            vintoCallerId = caller,
        )
        assertTrue(seedTheBoard(flat, plan = null).plan.isEmpty, "a pointless trade was proposed")
    }

    /**
     * A King is the coalition's demolition charge: point at the highest card the table can name
     * and it leaves the game outright, where a trade only moves points between two hands.
     *
     * Nina holds a King and a ten, so she has no trade to offer at all; Don has said he holds a
     * ten and a two. Putting the King down costs Nina her own King slot — she draws into it —
     * and takes Don's ten off the table, which is worth more than either card she could move.
     */
    @Test
    fun aKingIsProposedAsAPutDownThatPointsAtTheHighestCardTheTableCanName() {
        val seeded = seedTheBoard(kingTable(don = listOf(Rank.TEN, Rank.TWO)), plan = null)

        val step = assertIs<Step.PutDown>(assertNotNull(seeded.plan.laneOf(nina), "no proposal for the King").step)
        assertEquals(Rank.KING, step.guess, "the King put down was not called")
        val declare = assertIs<Step.Declare>(step.then, "the called King declared nothing")
        assertEquals(Rank.TEN, declare.rank, "the King named the wrong rank")
        assertEquals(don, assertNotNull(declare.card, "the King pointed at nothing").seat)
        assertEquals(0, declare.card?.position, "the King pointed at the two rather than the ten")
    }

    /**
     * An ace makes somebody draw, and in a final round every seat it can reach is the coalition's
     * own — the caller's hand is frozen from the call. So a bot never names one, even where naming
     * it is the best arithmetic on the board: a correct declaration hands the named card's action
     * to whoever played the King, and there is no good victim for that one.
     *
     * Don holds a single card. Empty it and the coalition has a hand of nothing, which is the
     * lowest hand there is — and no other card on this table lowers anything. A nine there and
     * the King says so; an ace and the bots would rather leave the turn at "your call".
     */
    @Test
    fun noProposalEverAimsAnAceAtATeammate() {
        val tempting = seedTheBoard(kingTable(don = listOf(Rank.ACE)), plan = null)

        assertTrue(
            tempting.plan.lanes.none { lane -> lane.step.forcesADraw() },
            "a proposal made a teammate draw: " + tempting.plan.lanes,
        )
        assertNull(
            tempting.plan.laneOf(nina)?.step,
            "a King was pointed at an ace, which hands its forced draw to the coalition",
        )

        // The same table with a nine in the ace's place, so the silence above is about the ace
        // and not about the shape of the board.
        val named = seedTheBoard(kingTable(don = listOf(Rank.NINE)), plan = null)
        val step = assertIs<Step.PutDown>(assertNotNull(named.plan.laneOf(nina), "no proposal for the King").step)
        assertEquals(Rank.NINE, assertIs<Step.Declare>(step.then).rank, "the King named the wrong rank")
    }

    /**
     * Nina holds the King, and a ten she cannot trade with; the person has said they hold a two
     * and a three. What Don holds is the question each King test asks.
     */
    private fun kingTable(don: List<Rank>): GameState = testState(
        players = listOf(
            seat(
                me,
                listOf(Rank.TWO, Rank.THREE),
                claims = listOf(Claim(me, listOf(0), listOf(Rank.TWO)), Claim(me, listOf(1), listOf(Rank.THREE))),
            ),
            seat(caller, listOf(Rank.SIX)),
            seat(nina, listOf(Rank.KING, Rank.TEN)),
            seat(
                this.don,
                don,
                claims = don.indices.map { Claim(this.don, listOf(it), listOf(don[it])) },
            ),
        ),
        phase = GamePhase.FINAL,
        vintoCallerId = caller,
    ).let { it.copy(currentPlayerIndex = it.players.indexOfFirst { p -> p.id == caller }) }

    /**
     * A King that names a Jack or a Queen says the trade it makes on the way out.
     *
     * The card leaving the game is only half of what a correct declaration is worth: its action
     * belongs to whoever played the King, and for those two ranks that action is a trade — the
     * strongest single turn of the round. The addresses are the ones on the felt now, before the
     * named card is taken off it, which is the order a lane is read in.
     *
     * Everything but Don's hand is unspoken here, so the Jack is the only card worth naming and
     * the trade is what settles where the points end up.
     */
    @Test
    fun aKingThatNamesAJackSaysTheTradeThatJackThenMakes() {
        val hands = mapOf(
            nina to listOf(unspoken(nina, 0), unspoken(nina, 1)),
            don to listOf(spoken(Rank.JACK, 0), spoken(Rank.TWO, 1)),
            me to listOf(unspoken(me, 0), unspoken(me, 1)),
        )

        val named = assertNotNull(bestDeclare(hands, listOf(nina, don, me)), "the King named nothing")

        assertEquals(Rank.JACK, named.rank, "the King named the two rather than the Jack")
        assertEquals(Slot(don, 0), named.at)
        val trade = assertNotNull(named.then, "the named Jack's own trade went unsaid")
        assertTrue(
            Slot(don, 0) != trade.from && Slot(don, 0) != trade.to,
            "the trade moved the card the King had just taken off the table: " + trade,
        )
        assertTrue(named.minAfter < 10, "the trade bought nothing: " + named)
    }

    /**
     * The King off the pile, which is the cheapest declaration there is — nothing has to leave
     * the taker's hand to buy it — and the one that shows a King doing the whole of its job.
     *
     * Nina's Jack is the only card at this table anybody can name, so it is what the King points
     * at; and a named Jack does not merely leave the game, it trades on the way out. The door
     * takes the whole sentence, which is the half a test can get wrong by believing: a call may
     * not name the card it put down, and this one names two cards under a card it removed.
     */
    @Test
    fun aKingTakenOffThePileNamesAJackAndTheTradeGoesThroughTheDoor() {
        val state = testState(
            players = listOf(
                seat(me, listOf(Rank.SEVEN, Rank.EIGHT)),
                seat(caller, listOf(Rank.SIX)),
                seat(nina, listOf(Rank.TWO, Rank.JACK)),
                // Read nothing, and say nothing: Don's hand is a pair of cards the table can
                // price and nobody can name, which is what most of a hand is most of the time.
                seat(don, listOf(Rank.FOUR, Rank.FIVE), read = emptyList()),
            ),
            phase = GamePhase.FINAL,
            vintoCallerId = caller,
            discardPile = Pile(listOf(testCard(Rank.KING, "pile-king"))),
        ).let { it.copy(currentPlayerIndex = it.players.indexOfFirst { p -> p.id == caller }) }

        val seeded = seedTheBoard(state, plan = null)

        val lane = assertNotNull(seeded.plan.laneOf(nina), "no proposal for the King on the pile")
        assertEquals(Opening.TAKE_THE_DISCARD, lane.opening, "the turn does not open on the pile")
        val declare = assertIs<Step.Declare>(lane.step, "the taken King declared nothing")
        assertEquals(Rank.JACK, declare.rank)
        assertEquals(Slot(nina, 1), assertNotNull(declare.card).let { Slot(it.seat, it.position) })
        val trade = assertIs<Step.Swap>(declare.then, "the named Jack's trade went unsaid")
        assertTrue(trade.from.seat != trade.to.seat, "a swap within one hand")
        assertTrue(nina in seeded.plan.agreed, "the bot did not nod to its own line")
    }

    /** A card the table has been told about, so a King may name it. */
    private fun spoken(rank: Rank, at: Int) =
        PlanCard("${rank.serialName}-$at", rank, getCardValue(rank), played = false)

    /** A card nobody has spoken about: priced at the deck's mean, and nameable by nobody. */
    private fun unspoken(seat: String, at: Int) =
        PlanCard("unspoken-$seat-$at", Rank.SIX, UNSEEN_CARD_VALUE, played = false, rankKnown = false)

    /** Whether a step, or anything it sets off, makes a seat draw. */
    private fun Step?.forcesADraw(): Boolean = when (this) {
        is Step.ForceDraw -> true
        is Step.PutDown -> then.forcesADraw()
        is Step.Declare -> then.forcesADraw()
        else -> false
    }

    @Test
    fun theBestTradeConcentratesTheLowCards() {
        // Nina's picture of the table above: Don has said nothing, so his two cards are priced at
        // the deck's mean each and his is the lowest hand at ten. The trade that lowers it most is
        // Nina's two into it for one of those — the two is the card that moves.
        val input = assertNotNull(buildCoalitionPlanInput(table(), nina))
        val hands = input.members.associate { it.id to it.cards }
        val best = assertNotNull(bestSwap(hands, input.members.map { it.id }))
        assertEquals(7, best.minAfter)
        val moved = listOf(best.from, best.to).map { hands.getValue(it.seat)[it.position].value }
        assertTrue(0 in moved || 2 in moved, "the trade moved neither the two nor the King: $best")
    }
}
