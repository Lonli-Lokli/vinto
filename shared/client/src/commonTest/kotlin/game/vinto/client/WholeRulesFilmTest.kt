package game.vinto.client

import game.vinto.engine.CardView
import game.vinto.engine.PlayerSeatView
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
import game.vinto.shapes.PlayerState
import game.vinto.shapes.Rank
import game.vinto.shapes.Step
import game.vinto.shapes.TossIn
import game.vinto.shapes.getCardShortDescription
import game.vinto.shapes.getCardValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The plan says the whole of the rules, and the film, the numbers and the health follow it.
 *
 * *"You play this Queen, then I throw in my Queen and play mine"* is one turn: a member's move
 * and the throw-ins it sets off, in the order people throw, each playing its card's action from
 * the table the throw leaves. A look, a King's pointed-at card and an Ace's forced draw are
 * steps like any other. What is held here is that every one of them is **drawn** by the
 * rehearsal, **priced** by the readout off the table the plan arrives at, and **followed** or
 * **broken** by the reading exactly as a trade is — and that a card the film has moved is found
 * again by what was said about it rather than by where it used to lie.
 */
class WholeRulesFilmTest {

    private val me = "human-1"
    private val caller = "bot-2"
    private val nina = "bot-3"
    private val don = "bot-4"

    /** A turn that takes the pile's card, which is the one card a plan can aim in advance. */
    private val takes = Opening.TAKE_THE_DISCARD

    private fun card(rank: Rank, id: String) = Card(
        id = id,
        rank = rank,
        value = getCardValue(rank),
        played = false,
        actionText = getCardShortDescription(rank).takeIf { it.isNotEmpty() },
    )

    private fun seat(id: String, ranks: List<Rank>, said: Map<Int, Rank>) = PlayerState(
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
     * A final round the bot in seat two called; the coalition plays Nina, then Don, then me.
     * I hold a Jack, a five and a King and have said so; Nina a five, a Jack and an unspoken
     * seven; Don a five and a six, both said. The deck's mean prices an unseen card at five.
     */
    private fun table(discardTop: Rank = Rank.THREE): GameState = GameState(
        gameId = "whole-rules",
        roundNumber = 1,
        turnNumber = 12,
        phase = GamePhase.FINAL,
        subPhase = GameSubPhase.IDLE,
        finalTurnTriggered = true,
        players = listOf(
            seat(me, listOf(Rank.JACK, Rank.FIVE, Rank.KING), mapOf(0 to Rank.JACK, 1 to Rank.FIVE, 2 to Rank.KING)),
            seat(caller, listOf(Rank.KING, Rank.TWO), emptyMap()),
            seat(nina, listOf(Rank.FIVE, Rank.JACK, Rank.SEVEN), mapOf(0 to Rank.FIVE, 1 to Rank.JACK)),
            seat(don, listOf(Rank.FIVE, Rank.SIX), mapOf(0 to Rank.FIVE, 1 to Rank.SIX)),
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

    private fun view(state: GameState = table()): PlayerView = projectView(state, me, conferMsRemaining = 20_000L)

    /** The table with [rank] lying unplayed on the pile, for a turn that takes it: the one card a plan can aim in advance. */
    private fun withPile(rank: Rank): PlayerView = view(table(discardTop = rank))

    /** A card named the way the composer names one: by its position and by what was said about it. */
    private fun named(seat: String, position: Int): CardAt {
        val said = table().players.first { it.id == seat }.claims.orEmpty().first { position in it.positions }
        return CardAt(seat, position, said)
    }

    private fun hand(view: PlayerView, seat: String): List<CardView> = seatOf(view, seat).cards
    private fun seatOf(view: PlayerView, seat: String): PlayerSeatView = view.players.first { it.id == seat }
    private fun believed(view: PlayerView, seat: String, position: Int): Set<Rank> =
        believedOnView(seatOf(view, seat), position).candidates

    private fun outcome(plan: CoalitionPlan): PlanOutcome = assertNotNull(planOutcome(view(), plan))

    /** Every card that flies from a hand to the pile in the frame, in the order it flies. */
    private fun thrownFrom(frame: Frame): List<String> = frame.scenes.flatten()
        .filterIsInstance<Beat.Move>()
        .filter { it.to == Anchor.Discard && it.from is Anchor.Seat }
        .map { (it.from as Anchor.Seat).playerId }

    // ------------------------------------------------------------------ the film

    @Test
    fun aThrowInPlaysInsideTheTurnItLandsOnAndInTheOrderPeopleThrow() {
        // Nina puts her five down; I throw my five in, then Don throws his. One turn, one
        // frame: the put-down flies first and the throws after it, in the order the plan says.
        val plan = CoalitionPlan(
            lanes = listOf(
                Lane(
                    nina,
                    Step.PutDown(named(nina, 0)),
                    tossIns = listOf(TossIn(me, Rank.FIVE), TossIn(don, Rank.FIVE)),
                ),
            ),
        )
        val film = rehearsal(view(), plan)

        assertEquals(3, film.frames.size, "a turn per coalition seat")
        val frame = assertNotNull(film.frames[0], "the turn drew nothing")
        assertEquals(listOf(nina, me, don), thrownFrom(frame), "the throws did not follow the put-down in order")

        val after = film.tables[1]
        assertEquals(CardView.Hidden, hand(after, nina)[0], "the put-down card was not replaced by an unseen draw")
        assertEquals(2, hand(after, me).size, "my five did not leave my hand")
        assertEquals(setOf(Rank.JACK), believed(after, me, 0))
        assertEquals(setOf(Rank.KING), believed(after, me, 1), "the claim on my King did not follow it down a place")
        assertEquals(1, hand(after, don).size, "Don's five did not leave his hand")
        assertEquals(setOf(Rank.SIX), believed(after, don, 0))
    }

    @Test
    fun aThrownCardsActionPlaysFromTheTableTheThrowLeaves() {
        // Nina keeps her draw in her Jack's place and calls the Jack; I throw my Jack in on it
        // and mine trades Nina's five for Don's six. The claims travel with the cards, which is
        // what lets the felt keep saying what each card is.
        val plan = CoalitionPlan(
            lanes = listOf(
                Lane(
                    nina,
                    Step.PutDown(named(nina, 1), guess = Rank.JACK),
                    tossIns = listOf(TossIn(me, Rank.JACK, then = Step.Swap(named(nina, 0), named(don, 1)))),
                ),
            ),
        )
        val after = rehearsal(view(), plan).tables[1]

        assertEquals(2, hand(after, me).size, "my Jack did not leave my hand")
        assertEquals(setOf(Rank.SIX), believed(after, nina, 0), "Don's six did not arrive in Nina's hand")
        assertEquals(setOf(Rank.FIVE), believed(after, don, 1), "Nina's five did not arrive in Don's hand")
    }

    @Test
    fun aLookMovesNothingAndIsStillDrawn() {
        // A Queen off the pile, looking at Nina's own seven and my five.
        val plan = CoalitionPlan(lanes = listOf(Lane(nina, Step.Peek(CardAt(nina, 2), named(me, 1)), opening = takes)))
        val film = rehearsal(withPile(Rank.QUEEN), plan)

        val frame = assertNotNull(film.frames[0], "a look drew nothing")
        val looked = frame.scenes.flatten().filterIsInstance<Beat.Peek>().map { it.at }
        assertEquals(listOf(Anchor.Seat(nina, 2), Anchor.Seat(me, 1)), looked, "the look was not drawn at both cards")
        assertEquals(view().players.map { it.cards }, film.tables[1].players.map { it.cards }, "a look moved a card")
        assertTrue(film.tables[1].discardTop?.played == true, "the Queen was not spent")

        // The same look off a blind draw is a sentence no table can say, and draws nothing.
        assertNull(
            rehearsal(
                view(),
                CoalitionPlan(lanes = listOf(Lane(nina, Step.Peek(CardAt(nina, 2), named(me, 1))))),
            ).frames[0],
        )
    }

    @Test
    fun aForcedDrawLengthensTheHandItNamesWithACardNobodyHasSeen() {
        val plan = CoalitionPlan(lanes = listOf(Lane(nina, Step.ForceDraw(don), opening = takes)))
        val after = rehearsal(withPile(Rank.ACE), plan).tables[1]

        assertEquals(3, hand(after, don).size, "Don did not draw")
        assertEquals(CardView.Hidden, hand(after, don)[2])
        assertTrue(believedOnView(seatOf(after, don), 2).sources.isEmpty(), "a forced draw arrived with a claim on it")
    }

    @Test
    fun aKingPointsAtACardWhichLeavesItsHandAndPlaysItsOwnAction() {
        // Nina's King points at my Jack: the Jack leaves my hand and, played by Nina, trades
        // Nina's five for Don's six.
        val plan = CoalitionPlan(
            lanes = listOf(
                Lane(
                    nina,
                    Step.Declare(Rank.JACK, named(me, 0), then = Step.Swap(named(nina, 0), named(don, 1))),
                    opening = takes,
                ),
            ),
        )
        val film = rehearsal(withPile(Rank.KING), plan)
        val frame = assertNotNull(film.frames[0], "a pointed King drew nothing")
        assertEquals(listOf(me), thrownFrom(frame), "the pointed-at card did not leave for the pile")

        val after = film.tables[1]
        assertEquals(2, hand(after, me).size, "the pointed-at Jack did not leave my hand")
        assertEquals(setOf(Rank.FIVE), believed(after, me, 0), "my five did not move down into the Jack's place")
        assertEquals(setOf(Rank.SIX), believed(after, nina, 0))
        assertEquals(setOf(Rank.FIVE), believed(after, don, 1))
        // The loose shape — a rank at whoever holds one — still plays: one card of that rank leaves.
        val loose = rehearsal(
            withPile(Rank.KING),
            CoalitionPlan(lanes = listOf(Lane(nina, Step.Declare(Rank.FIVE), opening = takes))),
        ).tables[1]
        val shorter = listOf(me, nina, don).count { seat -> hand(loose, seat).size < hand(view(), seat).size }
        assertEquals(1, shorter, "a loose King took more or less than one card")
    }

    @Test
    fun aCardNamedByItsClaimIsFoundWhereverTheFilmHasPutIt() {
        // Turn 1: Nina puts her five down and Don throws his in, so Don's six and Jack slide
        // down a place. Turn 2: Don puts his Jack down, calls it, and its trade names the six
        // where it *was*, by what was said about it — and the film finds both where they are now.
        val withJack = table().let { state ->
            state.copy(
                players = state.players.map { seat ->
                    if (seat.id == don) {
                        seat.copy(
                            cards = seat.cards + card(Rank.JACK, "don-c2"),
                            claims = seat.claims.orEmpty() + Claim(don, listOf(2), listOf(Rank.JACK)),
                        )
                    } else {
                        seat
                    }
                },
            )
        }
        val donsJack = CardAt(don, 2, Claim(don, listOf(2), listOf(Rank.JACK)))
        val plan = CoalitionPlan(
            lanes = listOf(
                Lane(nina, Step.PutDown(named(nina, 0)), tossIns = listOf(TossIn(don, Rank.FIVE))),
                Lane(don, Step.PutDown(donsJack, guess = Rank.JACK, then = Step.Swap(named(don, 1), named(me, 1)))),
            ),
        )
        val film = rehearsal(view(withJack), plan)

        assertNotNull(film.frames[1], "a step naming a card the film had moved was not drawn")
        val arrival = film.arrival
        assertEquals(setOf(Rank.FIVE), believed(arrival, don, 0), "my five did not arrive where Don's six had slid to")
        assertEquals(setOf(Rank.SIX), believed(arrival, me, 1), "Don's six did not arrive in my hand")
    }

    // ------------------------------------------------------------------ the numbers

    @Test
    fun theNumbersAreReadOffTheTableThePlanArrivesAt() {
        // As the table stands: mine is 10+5+0 = 15, Nina's 5+10+5 (the seven unspoken) = 20,
        // Don's 5+6 = 11. Putting Don's five down replaces it with an unseen five: still 11,
        // because nobody has said they will throw in — a put-down alone sweeps nothing.
        val putDown = CoalitionPlan(lanes = listOf(Lane(don, Step.PutDown(named(don, 0)))))
        assertEquals(11, outcome(putDown).ourBest, "a put-down swept a match nobody had promised")

        // With my throw-in said, my five leaves: my Jack and King are 10, the best hand.
        val thrown = CoalitionPlan(
            lanes = listOf(Lane(don, Step.PutDown(named(don, 0)), tossIns = listOf(TossIn(me, Rank.FIVE)))),
        )
        assertEquals(10, outcome(thrown).ourBest)
        // And the thrown card's own action counts too: a thrown Jack trading my King for Nina's five.
        val traded = CoalitionPlan(
            lanes = listOf(
                Lane(
                    nina,
                    Step.PutDown(named(nina, 1), guess = Rank.JACK),
                    tossIns = listOf(TossIn(me, Rank.JACK, then = Step.Swap(named(me, 2), named(nina, 0)))),
                ),
            ),
        )
        // Mine after: five and Nina's five = 10; Nina's: my King, an unseen draw and the seven = 0+5+5.
        assertEquals(10, outcome(traded).ourBest)
    }

    @Test
    fun aForcedDrawAndAKingsPointedCardArePricedAsTheRulesPlayThem() {
        // Don is made to draw by the Ace off the pile: an unseen five on top of 11, and the
        // best hand is mine at 15.
        val forced = CoalitionPlan(lanes = listOf(Lane(nina, Step.ForceDraw(don), opening = takes)))
        assertEquals(15, assertNotNull(planOutcome(withPile(Rank.ACE), forced)).ourBest)

        // Nina's King off the pile points at Don's five: it leaves, his six alone is 6 — and
        // nothing else moves, since nobody has said they will throw a five in on it.
        val pointed = CoalitionPlan(lanes = listOf(Lane(nina, Step.Declare(Rank.FIVE, named(don, 0)), opening = takes)))
        assertEquals(6, assertNotNull(planOutcome(withPile(Rank.KING), pointed)).ourBest)
        assertEquals(
            3,
            hand(rehearsal(withPile(Rank.KING), pointed).arrival, nina).size,
            "a pointed King swept Nina's five too",
        )
    }

    // ------------------------------------------------------------------ the health

    @Test
    fun aThrowInByASeatNoLongerKnownToHoldTheRankIsBroken() {
        // Don said his first card is a five; a reveal shows a nine, so his promised five stands
        // on nothing — and the turn says so, without repairing it.
        val plan = CoalitionPlan(
            lanes = listOf(Lane(nina, Step.PutDown(named(nina, 0)), tossIns = listOf(TossIn(don, Rank.FIVE)))),
        )
        val reveals = listOf(PublicReveal(don, 0, card(Rank.NINE, "really-a-nine")))

        assertEquals(StepHealth.BROKEN, readPlan(view(), plan, reveals).health[0])
        assertEquals(StepHealth.LIVE, readPlan(view(), plan, emptyList()).health[0])
    }

    @Test
    fun aThrownCardsTradeFollowsItsCardsAndBreaksWithThem() {
        // The trade my thrown Jack makes names Don's six. A watched Jack has since moved the
        // six into Nina's hand — the claim went with it — so the trade follows, in silence.
        val plan = CoalitionPlan(
            lanes = listOf(
                Lane(
                    nina,
                    Step.PutDown(named(nina, 1), guess = Rank.JACK),
                    tossIns = listOf(TossIn(me, Rank.JACK, then = Step.Swap(named(don, 1), named(me, 1)))),
                ),
            ),
        )
        val moved = table().let { state ->
            state.copy(
                players = state.players.map { seat ->
                    when (seat.id) {
                        don -> seat.copy(claims = listOf(Claim(don, listOf(0), listOf(Rank.FIVE))))
                        nina -> seat.copy(
                            claims = listOf(
                                Claim(nina, listOf(0), listOf(Rank.FIVE)),
                                Claim(nina, listOf(1), listOf(Rank.JACK)),
                                Claim(don, listOf(2), listOf(Rank.SIX)),
                            ),
                        )
                        else -> seat
                    }
                },
            )
        }
        val reading = readPlan(view(moved), plan, emptyList())

        assertEquals(StepHealth.REANCHORED, reading.health[0])
        val followed = assertNotNull(reading.plan.lanes[0].tossIns[0].then as? Step.Swap)
        assertEquals(CardAt(nina, 2), followed.from.copy(anchor = null), "the trade did not follow the six")

        // A reveal that contradicts the claim under it breaks the trade and the turn with it.
        val reveals = listOf(PublicReveal(don, 1, card(Rank.NINE, "really-a-nine")))
        assertEquals(StepHealth.BROKEN, readPlan(view(), plan, reveals).health[0])
    }

    @Test
    fun aLookAtADisprovedCardAndAKingPointedAtOneAreBroken() {
        val reveals = listOf(PublicReveal(don, 0, card(Rank.NINE, "really-a-nine")))
        val look = CoalitionPlan(lanes = listOf(Lane(nina, Step.Peek(named(don, 0)))))
        assertEquals(StepHealth.BROKEN, readPlan(view(), look, reveals).health[0])
        assertEquals(StepHealth.LIVE, readPlan(view(), look, emptyList()).health[0])

        val pointed = CoalitionPlan(lanes = listOf(Lane(nina, Step.Declare(Rank.FIVE, named(don, 0)))))
        assertEquals(StepHealth.BROKEN, readPlan(view(), pointed, reveals).health[0])
        // A forced draw names a seat, not a card: nothing to follow, nothing to lose.
        val forced = CoalitionPlan(lanes = listOf(Lane(nina, Step.ForceDraw(don))))
        assertEquals(StepHealth.LIVE, readPlan(view(), forced, reveals).health[0])
    }
}
