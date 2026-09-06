package game.vinto.client

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
import game.vinto.shapes.Pile
import game.vinto.shapes.PlayerState
import game.vinto.shapes.Rank
import game.vinto.shapes.Shed
import game.vinto.shapes.Step
import game.vinto.shapes.getCardShortDescription
import game.vinto.shapes.getCardValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * What a plan is worth, said as an outcome.
 *
 * The round is decided by the caller's total against the **lowest** coalition hand, and a tie
 * pays the caller — so a strip reading "our best: 4" beside a caller on 4 looks level and is a
 * loss. Every number here exists to stop a player having to make that comparison themselves at
 * the one moment it matters.
 *
 * All of it is computed from **standing claims**: the same arithmetic the coalition could do
 * out loud. A plan built on a wrong claim therefore reads as good right up until the reveal
 * says otherwise, which is the model rather than a defect.
 */
class PlanOutcomeTest {

    private val me = "human-1"
    private val caller = "bot-2"
    private val mate = "bot-3"

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
        nickname = id,
        isHuman = id == me,
        isBot = id != me,
        cards = ranks.mapIndexed { index, rank -> card(rank, "$id-c$index") },
        knownCardPositions = emptyList(),
        isVintoCaller = id == caller,
        coalitionWith = if (id == caller) emptyList() else listOf(me, mate),
        claims = claims,
    )

    /** Everything anybody holds has been claimed, so the arithmetic is fully determined. */
    private fun table(
        mine: List<Rank> = listOf(Rank.TWO, Rank.THREE),
        theirs: List<Rank> = listOf(Rank.NINE, Rank.KING),
        callers: List<Rank> = listOf(Rank.FOUR, Rank.FOUR),
        callerSpoken: Int = 2,
    ): GameState {
        fun spoken(id: String, ranks: List<Rank>, upTo: Int = ranks.size) =
            ranks.indices.take(upTo).map { Claim(id, listOf(it), listOf(ranks[it])) }

        return GameState(
            gameId = "plan",
            roundNumber = 1,
            turnNumber = 9,
            phase = GamePhase.FINAL,
            subPhase = GameSubPhase.IDLE,
            finalTurnTriggered = true,
            players = listOf(
                seat(me, mine, spoken(me, mine)),
                seat(caller, callers, spoken(me, callers, callerSpoken)),
                seat(mate, theirs, spoken(mate, theirs)),
            ),
            currentPlayerIndex = 0,
            vintoCallerId = caller,
            coalitionLeaderId = null,
            drawPile = Pile((0..4).map { card(Rank.THREE, "draw-$it") }),
            discardPile = Pile(listOf(card(Rank.EIGHT, "seed"))),
            pendingAction = null,
            activeTossIn = null,
            turnActions = emptyList(),
            roundActions = emptyList(),
            roundFailedAttempts = emptyList(),
            difficulty = Difficulty.MODERATE,
            rngState = 0,
        )
    }

    private fun read(state: GameState, plan: CoalitionPlan = CoalitionPlan()) =
        assertNotNull(planOutcome(projectView(state, me), plan))

    @Test
    fun theBestHandIsTheLowestOneNotTheAverage() {
        // Mine is 2+3 = 5, theirs 9+0 = 9. Only the lowest is compared, so 5 is the number.
        assertEquals(5, read(table()).ourBest)
    }

    @Test
    fun puttingACardDownTakesEveryKnownMatchWithItAndPricesTheDraw() {
        // 3.14: my nine goes to the pile and the mate's claimed nine follows it in the toss-in,
        // leaving them a King alone — 0. The draw that takes my nine's place is unseen and
        // priced as one, so my hand is 2 plus the deck's mean rather than 2.
        val nines = table(mine = listOf(Rank.TWO, Rank.NINE), theirs = listOf(Rank.NINE, Rank.KING))
        val plan = CoalitionPlan(
            lanes = listOf(Lane(me, Step.PutDown(CardAt(me, 1)))),
            agreed = listOf(me),
            editedBy = me,
        )
        assertEquals(0, read(nines, plan).ourBest)

        // No match anywhere: the card leaves, the draw arrives, and nothing else moves.
        val alone = read(table(mine = listOf(Rank.TWO, Rank.THREE)), plan)
        assertEquals(7, alone.ourBest, "the draw that replaces a put-down card was priced as free")
    }

    @Test
    fun levelIsALossBecauseATiePaysTheCaller() {
        // Mine is 5; the caller's claimed hand is 4+1 = 5. Exactly level — which pays them.
        val outcome = read(table(callers = listOf(Rank.FOUR, Rank.ACE)))

        assertTrue(outcome.level)
        assertFalse(outcome.wins, "level was read as good enough; a tie goes to the caller")
    }

    @Test
    fun beatingThemByOneIsAWin() {
        val outcome = read(table(callers = listOf(Rank.FOUR, Rank.TWO)))

        assertEquals(6, outcome.theirBelieved)
        assertTrue(outcome.wins)
    }

    @Test
    fun aHandNobodyHasSeenIsSaidToBeUnseenRatherThanGuessedAt() {
        // A believed total stated without saying how much of it is a guess is a number
        // pretending to be information.
        val outcome = read(table(callerSpoken = 1))

        assertEquals(1, outcome.unseen)
        assertFalse(outcome.confident)
    }

    // ------------------------------------------------------------------ what a plan does

    @Test
    fun aSwapMovesTheValueAndTheBestHandFollows() {
        // The concentration play: my 3 for their King. Mine becomes 2+0 = 2.
        val plan = CoalitionPlan(
            lanes = listOf(
                Lane(mate, Step.Swap(from = CardAt(me, 1), to = CardAt(mate, 1))),
            ),
        )

        assertEquals(2, read(table(), plan).ourBest)
    }

    @Test
    fun aKingEmptiesTheRankOutOfEveryCoalitionHand() {
        // And only theirs — the caller may not toss in once they have called, so a King can
        // never help them shed.
        val plan = CoalitionPlan(lanes = listOf(Lane(mate, Step.Declare(Rank.NINE))))
        val before = read(table())
        val outcome = read(table(), plan)

        // The teammate's 9 goes and their King is worth nothing, so *their* hand becomes the
        // best one — which is the concentration play working, not a bug.
        assertEquals(0, outcome.ourBest)
        assertTrue(outcome.ourBest < before.ourBest, "a King that emptied a hand did not help")
        assertEquals(
            before.theirBelieved,
            outcome.theirBelieved,
            "the caller shed something they may not shed — they cannot toss in once they call",
        )
    }

    @Test
    fun aShedLowersAHandWithoutSpendingATurn() {
        // The cheapest tool the coalition has, and the one a lanes-only plan would miss: the
        // round is three turns *plus every window they open*.
        val plan = CoalitionPlan(sheds = listOf(Shed(me, Rank.THREE)))

        assertEquals(2, read(table(), plan).ourBest)
    }

    @Test
    fun anEmptyPlanIsEmptyAndChangesNothing() {
        val plan = CoalitionPlan(lanes = listOf(Lane(me), Lane(mate)))

        assertTrue(plan.isEmpty, "lanes nobody has filled are not a plan")
        assertEquals(read(table()).ourBest, read(table(), plan).ourBest)
    }
}
