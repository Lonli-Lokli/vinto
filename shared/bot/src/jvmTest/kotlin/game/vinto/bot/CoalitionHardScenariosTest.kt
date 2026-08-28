package game.vinto.bot

import game.vinto.engine.ActionValidator
import game.vinto.engine.GameEngine
import game.vinto.engine.ReduceResult
import game.vinto.engine.Validation
import game.vinto.shapes.Card
import game.vinto.shapes.Difficulty
import game.vinto.shapes.GamePhase
import game.vinto.shapes.GameState
import game.vinto.shapes.GameSubPhase
import game.vinto.shapes.Pile
import game.vinto.shapes.PlayerState
import game.vinto.shapes.Rank
import game.vinto.shapes.SerializedOpponentKnowledge
import game.vinto.shapes.getCardShortDescription
import game.vinto.shapes.getCardValue
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Hard coalition puzzles, written from the *rules* and nothing else.
 *
 * Each position was designed on paper — what would four people at a real table have to do to
 * beat this caller? — and the tests assert only the outcome: the round scores, the best
 * coalition hand ends strictly below the caller's (a tie goes to the caller), and the
 * caller's hand is untouched. No move sequence is pinned and no solver internal is named, so
 * these hold whatever the search inside becomes.
 *
 * What makes them hard is that no single member can win alone. Every puzzle needs a shift
 * across hands or across turns: a King's window emptying somebody else's hand, a worthless
 * swap made purely to open a teammate's toss-in, a declared action card gambled for its
 * effect, a swap that *worsens* a hand now so the next turn's window can finish the job, or
 * a discard pile that is the only door in. Each runs under two seeds — a win that depends on
 * the seed was luck, not planning.
 */
class CoalitionHardScenariosTest {

    private val callerId = "caller"
    private val memberIds = listOf("p1", "p2", "p3")

    private fun card(rank: Rank, id: String) = Card(
        id = id,
        rank = rank,
        value = getCardValue(rank),
        played = false,
        actionText = getCardShortDescription(rank).takeIf { it.isNotEmpty() },
    )

    /** A coalition member with a fully read, truthfully declared hand — the honest table. */
    private fun member(id: String, ranks: List<Rank>) = PlayerState(
        id = id,
        name = id,
        nickname = id,
        isHuman = false,
        isBot = true,
        cards = ranks.mapIndexed { index, rank -> card(rank, "$id-c$index") },
        knownCardPositions = ranks.indices.toList(),
        isVintoCaller = false,
        coalitionWith = memberIds,
        declaredCards = ranks.mapIndexed { index, rank -> index to rank }.toMap(),
    )

    /**
     * A final round about to start its coalition turns: the human caller has called, p1 is
     * on play as leader, and p1 has seen the caller's whole hand — so the coalition knows
     * exactly what total it has to beat.
     */
    private fun scenario(
        caller: List<Rank>,
        p1: List<Rank>,
        p2: List<Rank>,
        p3: List<Rank>,
        drawPile: List<Rank>,
        discardPile: List<Rank> = listOf(Rank.THREE),
    ): GameState {
        val callerSeat = PlayerState(
            id = callerId,
            name = callerId,
            nickname = callerId,
            isHuman = true,
            isBot = false,
            cards = caller.mapIndexed { index, rank -> card(rank, "$callerId-c$index") },
            knownCardPositions = emptyList(),
            isVintoCaller = true,
            coalitionWith = emptyList(),
        )
        val leader = member("p1", p1).copy(
            opponentKnowledge = mapOf(
                callerId to SerializedOpponentKnowledge(
                    callerSeat.cards.mapIndexed { index, c -> index to c }.toMap(),
                ),
            ),
        )

        return GameState(
            gameId = "hard-scenario",
            roundNumber = 1,
            turnNumber = 20,
            phase = GamePhase.FINAL,
            subPhase = GameSubPhase.IDLE,
            finalTurnTriggered = true,
            players = listOf(callerSeat, leader, member("p2", p2), member("p3", p3)),
            currentPlayerIndex = 1,
            vintoCallerId = callerId,
            coalitionLeaderId = "p1",
            drawPile = Pile(drawPile.mapIndexed { index, rank -> card(rank, "draw-$index") }),
            discardPile = Pile(discardPile.mapIndexed { index, rank -> card(rank, "seed-$index") }),
            pendingAction = null,
            activeTossIn = null,
            turnActions = emptyList(),
            roundActions = emptyList(),
            roundFailedAttempts = emptyList(),
            difficulty = Difficulty.MODERATE,
            rngState = 0,
        )
    }

    private fun playOut(start: GameState, seed: Long): GameState {
        val runner = BotRunner(Difficulty.MODERATE, Random(seed))
        var state = start
        var actions = 0

        while (actions < 300 && state.phase != GamePhase.SCORING) {
            val action = runner.nextAction(state)
                ?: fail("stalled after $actions actions: subPhase=${state.subPhase.serialName}")

            when (val validation = ActionValidator.validate(state, action)) {
                is Validation.Invalid ->
                    fail("action #$actions: illegal ${action.type} — ${validation.reason}")

                Validation.Valid -> Unit
            }
            state = when (val result = GameEngine.reduce(state, action)) {
                is ReduceResult.Success -> result.state
                is ReduceResult.Failure -> fail("engine rejected ${action.type}: ${result.reason}")
            }
            actions++
        }
        return state
    }

    /** The outcome, and only the outcome: scored, strictly beaten, caller untouched. */
    private fun assertBeaten(start: GameState, seed: Long, bestAtMost: Int) {
        val end = playOut(start, seed)
        assertEquals(GamePhase.SCORING, end.phase, "seed $seed: the round never scored")

        val callerTotal = end.players.first { it.id == callerId }.cards.sumOf { it.value }
        val best = end.players.filter { it.id != callerId }.minOf { p -> p.cards.sumOf { it.value } }

        assertEquals(
            start.players.first { it.id == callerId }.cards.map { it.rank },
            end.players.first { it.id == callerId }.cards.map { it.rank },
            "seed $seed: the caller's hand was touched",
        )
        assertTrue(
            best < callerTotal,
            "seed $seed: best coalition hand $best did not beat the caller's $callerTotal",
        )
        assertTrue(
            best <= bestAtMost,
            "seed $seed: the winning line was left on the table — best $best, expected ≤ $bestAtMost",
        )
    }

    private fun assertBeatenUnderTwoSeeds(start: GameState, bestAtMost: Int) {
        assertBeaten(start, seed = 4, bestAtMost = bestAtMost)
        assertBeaten(start, seed = 99, bestAtMost = bestAtMost)
    }

    @Test
    fun aKingsWindowIsTheOnlyWayToEmptyTheChampionsHand() {
        // The caller sits on a lone Ace: total 1, so the coalition needs a hand at 0 — an
        // *empty* one, since no negative card is on the table. p2's pair of fives can only
        // both leave through a toss-in window someone else opens on fives: any swap gains a
        // card back, and no member can discard a five on their own turn except by breaking
        // up the very pair that must go. The drawn King is the key — declaring a five opens
        // a [K,5] window and p2 sheds both — though a five discarded from p3's hand later
        // does it too. Either way the win is a cross-member cascade, never a solo line.
        val state = scenario(
            caller = listOf(Rank.ACE),
            p1 = listOf(Rank.NINE, Rank.EIGHT),
            p2 = listOf(Rank.FIVE, Rank.FIVE),
            p3 = listOf(Rank.FIVE, Rank.TWO),
            drawPile = listOf(Rank.KING, Rank.EIGHT, Rank.EIGHT, Rank.EIGHT, Rank.EIGHT),
        )
        assertBeatenUnderTwoSeeds(state, bestAtMost = 0)
    }

    @Test
    fun aWorthlessSwapIsMadePurelyToOpenTheChampionsWindow() {
        // The caller holds a two. p2's Ace-plus-six is one shed away from winning at 1, but
        // p2 cannot open a six-window alone: swapping its own six out means taking the drawn
        // card in, and the hand stays above 1. Only p1 can help — by swapping a worthless
        // drawn five *into its own hand* just to put its six on the pile, a move that does
        // nothing for p1 and everything for p2.
        val state = scenario(
            caller = listOf(Rank.TWO),
            p1 = listOf(Rank.SIX, Rank.NINE),
            p2 = listOf(Rank.ACE, Rank.SIX),
            p3 = listOf(Rank.EIGHT, Rank.NINE),
            drawPile = listOf(Rank.FIVE, Rank.EIGHT, Rank.EIGHT, Rank.EIGHT, Rank.EIGHT),
        )
        assertBeatenUnderTwoSeeds(state, bestAtMost = 1)
    }

    @Test
    fun aDeclaredJackInTheHandIsWorthMoreThanTheCardItself() {
        // Two Aces give the caller 2, and the only way under it is p3's Joker landing next
        // to p2's Ace. Nobody draws a Jack or Queen — but p1 is *holding* a Jack it has read
        // and declared. The win is the swap-declare gamble: put the drawn four in, name the
        // Jack on its way out, and spend its swap moving the Joker onto p2's ten.
        val state = scenario(
            caller = listOf(Rank.ACE, Rank.ACE),
            p1 = listOf(Rank.JACK, Rank.NINE),
            p2 = listOf(Rank.TEN, Rank.ACE),
            p3 = listOf(Rank.JOKER, Rank.SEVEN),
            drawPile = listOf(Rank.FOUR, Rank.EIGHT, Rank.EIGHT, Rank.EIGHT, Rank.EIGHT),
        )
        assertBeatenUnderTwoSeeds(state, bestAtMost = 0)
    }

    @Test
    fun theJackPaysOffOnlyOnTheNextTurnsWindow() {
        // A King-holding caller at zero again: only a freed Joker wins, and p3's Joker is
        // chained to a ten no window will ever match. The declared Jack must reposition
        // cards *for the future* — pairing the ten away against a seven, or walking the
        // Joker over to the sevens — knowing the swap wins nothing on the spot and pays off
        // only when a seven hits the pile on a later turn and the toss-in finishes the job.
        val state = scenario(
            caller = listOf(Rank.KING),
            p1 = listOf(Rank.JACK, Rank.TWO),
            p2 = listOf(Rank.SEVEN, Rank.SEVEN),
            p3 = listOf(Rank.JOKER, Rank.TEN),
            drawPile = listOf(Rank.FOUR, Rank.SEVEN, Rank.EIGHT, Rank.EIGHT, Rank.EIGHT),
        )
        assertBeatenUnderTwoSeeds(state, bestAtMost = -1)
    }

    @Test
    fun theDiscardPileIsTheOnlyDoorIn() {
        // The deck holds nothing but junk, so drawing can never beat the caller's two. The
        // unplayed Queen on the discard pile is the one action on the table — and taking it
        // commits to playing it at once. The win runs through Option B: lift the Queen,
        // peek, and swap p3's Joker onto p2's ten for a hand at 0.
        val state = scenario(
            caller = listOf(Rank.TWO),
            p1 = listOf(Rank.EIGHT, Rank.NINE),
            p2 = listOf(Rank.TEN, Rank.ACE),
            p3 = listOf(Rank.JOKER, Rank.SEVEN),
            drawPile = listOf(Rank.NINE, Rank.EIGHT, Rank.EIGHT, Rank.EIGHT, Rank.EIGHT),
            discardPile = listOf(Rank.QUEEN, Rank.THREE),
        )
        assertBeatenUnderTwoSeeds(state, bestAtMost = 0)
    }
}
