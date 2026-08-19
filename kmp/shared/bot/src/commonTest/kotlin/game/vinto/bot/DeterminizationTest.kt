package game.vinto.bot

import game.vinto.shapes.Difficulty
import game.vinto.shapes.Pile
import game.vinto.shapes.Rank
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Ported from `packages/bot/src/lib/__tests__/mcts-determinization.test.ts`.
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
