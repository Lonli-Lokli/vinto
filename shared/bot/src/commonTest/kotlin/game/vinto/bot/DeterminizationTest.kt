package game.vinto.bot

import game.vinto.shapes.Difficulty
import game.vinto.shapes.Pile
import game.vinto.shapes.Rank
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Ported from `legacy-web/packages/bot/src/lib/__tests__/mcts-determinization.test.ts`.
 *
 * Determinization is where an imperfect-information game becomes searchable, so the property
 * that matters is that each sampled world is *possible* — not that any particular card comes
 * out of it.
 */
class DeterminizationTest {

    private fun state(
        knownCards: Map<Int, CardMemory> = emptyMap(),
        discard: Pile = Pile(),
        modeler: OpponentModeler? = null,
    ): MctsGameState {
        val memory = BotMemory("bot-1", Difficulty.HARD, Random(1))
        return MctsGameState(
            players = listOf(
                MctsPlayerState("bot-1", cardCount = 4, knownCards = knownCards),
                MctsPlayerState("p2", cardCount = 4),
            ),
            currentPlayerIndex = 0,
            botPlayerId = "bot-1",
            discardPile = discard,
            botMemory = memory,
            opponentModeler = modeler,
        )
    }

    // --- the weighting itself ---------------------------------------------------------------

    @Test
    fun everyRankCarriesTheWeightTheTableSays() {
        // A table, so every entry is checked. The exact numbers are judgement and may be
        // re-tuned; what may not change silently is one entry drifting out of order.
        val expected = listOf(
            Rank.JOKER to 2.0,
            Rank.QUEEN to 1.8,
            Rank.JACK to 1.7,
            Rank.KING to 1.6,
            Rank.SEVEN to 1.4,
            Rank.EIGHT to 1.4,
            Rank.ACE to 1.3,
            Rank.NINE to 1.1,
            Rank.TEN to 1.1,
            Rank.SIX to 0.7,
            Rank.FIVE to 0.6,
            Rank.TWO to 0.5,
            Rank.THREE to 0.5,
            Rank.FOUR to 0.5,
        )

        for ((rank, weight) in expected) {
            assertEquals(weight, getStrategicProbabilityWeight(rank), "${rank.serialName}")
        }
    }

    @Test
    fun everyActionCardOutweighsEveryPlainLowCard() {
        val actions = listOf(Rank.QUEEN, Rank.JACK, Rank.KING, Rank.SEVEN, Rank.EIGHT, Rank.ACE)
        val low = listOf(Rank.TWO, Rank.THREE, Rank.FOUR, Rank.FIVE, Rank.SIX)

        for (action in actions) {
            for (plain in low) {
                assertTrue(
                    getStrategicProbabilityWeight(action) > getStrategicProbabilityWeight(plain),
                    "${action.serialName} was not weighted above ${plain.serialName}",
                )
            }
        }
    }

    @Test
    fun theJokerOutweighsEverything() {
        val joker = getStrategicProbabilityWeight(Rank.JOKER)
        for (rank in Rank.entries.filter { it != Rank.JOKER }) {
            assertTrue(joker > getStrategicProbabilityWeight(rank), "${rank.serialName}")
        }
    }

    @Test
    fun aPlainLowCardIsAlwaysBelowEven() {
        // Below 1.0 means "less likely than chance": opponents keep good cards and shed bad
        // ones, so an unseen card is likelier to be good than the deck alone would suggest.
        for (rank in listOf(Rank.TWO, Rank.THREE, Rank.FOUR, Rank.FIVE, Rank.SIX)) {
            assertTrue(getStrategicProbabilityWeight(rank) < 1.0, "${rank.serialName}")
        }
    }

    // --- sampling ----------------------------------------------------------------------------

    @Test
    fun samplingActuallyFollowsTheWeights() {
        // A statistical check rather than a spot one: the weights only mean anything if they
        // show up in the draws. Seeded, so it cannot fail intermittently.
        val random = Random(2026)
        val counts = mutableMapOf(Rank.JOKER to 0, Rank.QUEEN to 0, Rank.TWO to 0)

        repeat(10_000) { index ->
            val pool = mutableListOf(Rank.JOKER, Rank.QUEEN, Rank.TWO)
            val card = sampleCardFromPool(pool, "test", index, random)
            counts[card.rank] = (counts[card.rank] ?: 0) + 1
        }

        val joker = counts.getValue(Rank.JOKER)
        val queen = counts.getValue(Rank.QUEEN)
        val two = counts.getValue(Rank.TWO)

        assertTrue(joker > queen, "Joker (2.0) did not beat Queen (1.8): $joker vs $queen")
        assertTrue(queen > two, "Queen (1.8) did not beat 2 (0.5): $queen vs $two")
        assertTrue(joker > two * 2, "Joker should be well over twice a 2: $joker vs $two")
    }

    @Test
    fun twoDrawsOfTheSameRankAreStillTwoDifferentCards() {
        val random = Random(9)
        val first = sampleCardFromPool(mutableListOf(Rank.ACE, Rank.ACE), "player1", 0, random)
        val second = sampleCardFromPool(mutableListOf(Rank.ACE, Rank.ACE), "player1", 1, random)

        assertEquals(Rank.ACE, first.rank)
        assertEquals(Rank.ACE, second.rank)
        // Ids encode the seat and position, so two cards in one hand cannot collide.
        assertTrue(first.id != second.id, "two sampled cards shared an id: ${first.id}")
    }

    @Test
    fun samplingFromNothingFailsLoudlyRatherThanQuietly() {
        // The TypeScript checks it "handles it gracefully". A silent fallback would deal a
        // card the deck cannot contain, so this fails instead — the caller has a bug.
        assertFailsWith<IllegalArgumentException> {
            sampleCardFromPool(mutableListOf(), "p", 0, Random(1))
        }
    }

    @Test
    fun theCardInPlayIsNotAlsoInTheDeck() {
        val queen = testCard(Rank.QUEEN, "Q_pending")
        val withoutPending = buildAvailableRanksPool(state()).count { it == Rank.QUEEN }
        val withPending = buildAvailableRanksPool(state().copy(pendingCard = queen))
            .count { it == Rank.QUEEN }

        assertEquals(withoutPending - 1, withPending, "the pending card was still in the pool")
    }

    @Test
    fun theDeckIsFiftyFourCards() {
        assertEquals(54, STANDARD_DECK_RANKS.size)
        assertEquals(2, STANDARD_DECK_RANKS.count { it == Rank.JOKER })
        assertEquals(4, STANDARD_DECK_RANKS.count { it == Rank.KING })
    }

    @Test
    fun thePoolExcludesWhatIsAlreadyVisible() {
        val discard = Pile(listOf(testCard(Rank.KING, "K_0"), testCard(Rank.KING, "K_1")))
        val pool = buildAvailableRanksPool(state(discard = discard))

        // Two Kings are face up on the discard pile, so only two can still be hidden.
        assertEquals(2, pool.count { it == Rank.KING })
    }

    @Test
    fun thePoolExcludesWhatTheBotRemembers() {
        val remembered = mapOf(
            0 to CardMemory(testCard(Rank.JOKER, "Joker1"), confidence = 1.0, lastSeen = 0, observations = 1),
        )
        val pool = buildAvailableRanksPool(state(knownCards = remembered))

        assertEquals(1, pool.count { it == Rank.JOKER })
    }

    @Test
    fun everyHiddenCardIsFilledIn() {
        val world = determinize(state(), Random(4))

        // Two players of four cards each; the search cannot run on a partial world.
        assertEquals(8, world.hiddenCards.size)
    }

    @Test
    fun aSampledWorldNeverDealsTheSameCardTwice() {
        val world = determinize(state(), Random(11))
        val ids = world.hiddenCards.values.map { it.id }

        assertEquals(ids.size, ids.toSet().size, "a card was dealt into two places at once")
    }

    @Test
    fun knownCardsSurviveIntoTheSampledWorld() {
        val joker = testCard(Rank.JOKER, "Joker1")
        val remembered = mapOf(
            0 to CardMemory(joker, confidence = 1.0, lastSeen = 0, observations = 1),
        )
        val world = determinize(state(knownCards = remembered), Random(2))

        assertEquals(joker, world.hiddenCards["bot-1-0"], "a remembered card was resampled")
    }

    @Test
    fun samplingIsReproducibleFromASeed() {
        // Sorted by key rather than `toSortedMap`, which the stdlib only offers on the JVM.
        fun ranks() = determinize(state(), Random(77)).hiddenCards.entries
            .sortedBy { it.key }
            .map { it.value.rank }
        assertEquals(ranks(), ranks())
    }

    @Test
    fun goodCardsAreAssumedMoreLikelyThanBadOnes() {
        // Opponents keep what helps them, so an unseen card is likelier to be a Queen than a 3.
        assertTrue(getStrategicProbabilityWeight(Rank.JOKER) > getStrategicProbabilityWeight(Rank.SIX))
        assertTrue(getStrategicProbabilityWeight(Rank.QUEEN) > getStrategicProbabilityWeight(Rank.TEN))
        assertTrue(getStrategicProbabilityWeight(Rank.KING) > getStrategicProbabilityWeight(Rank.TWO))
    }

    @Test
    fun beliefsNarrowTheDraw() {
        // "That card is worth more than 7" should stop 2s and 3s coming out of the pool.
        val modeler = OpponentModeler()
        modeler.handleObservedAction(
            ObservedAction.SwapFromDiscard("p2", testCard(Rank.SEVEN, "7_0"), position = 0),
        )

        val values = (1..30).map { seed ->
            val world = determinize(state(modeler = modeler), Random(seed))
            world.hiddenCards.getValue("p2-0").value
        }

        assertTrue(values.all { it >= 8 }, "a belief-constrained draw produced $values")
    }

    @Test
    fun anImpossibleBeliefFallsBackRatherThanFailing() {
        // No card is worth 99. A belief is evidence, not a guarantee — refusing to sample
        // would abandon the simulation entirely.
        val pool = STANDARD_DECK_RANKS.toMutableList()
        val card = sampleCardFromPool(pool, "p2", 0, Random(1), minValue = 99)
        assertTrue(card.value < 99)
    }
}
