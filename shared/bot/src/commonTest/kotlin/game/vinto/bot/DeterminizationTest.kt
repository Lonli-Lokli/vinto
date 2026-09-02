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
 * Determinization is where an imperfect-information game becomes searchable, so the property
 * that matters is that each sampled world is *possible* — not that any particular card comes
 * out of it. A world is possible when every card in it is one the bot has not accounted for,
 * no card is in two places, and the deck it draws from is the rest of the same pool.
 */
class DeterminizationTest {

    private fun state(
        knownCards: Map<Int, CardMemory> = emptyMap(),
        discard: Pile = Pile(),
        modeler: OpponentModeler? = null,
        deckSize: Int = 20,
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
            deckSize = deckSize,
            botMemory = memory,
            opponentModeler = modeler,
        )
    }

    // --- sampling ----------------------------------------------------------------------------

    @Test
    fun samplingIsUniformOverWhatIsLeft() {
        // No prior says an unseen card is likelier to be a Queen than a 2: with one of each
        // in the pool the two come out about as often. Seeded, so it cannot fail intermittently.
        val random = Random(2026)
        val counts = mutableMapOf(Rank.QUEEN to 0, Rank.TWO to 0)

        repeat(10_000) { index ->
            val pool = mutableListOf(Rank.QUEEN, Rank.TWO)
            val card = sampleCardFromPool(pool, "test", index, random)
            counts[card.rank] = (counts[card.rank] ?: 0) + 1
        }

        val queen = counts.getValue(Rank.QUEEN)
        val two = counts.getValue(Rank.TWO)
        assertTrue(queen in 4_700..5_300 && two in 4_700..5_300, "not uniform: Queen $queen, 2 $two")
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
        // A silent fallback would deal a card the deck cannot contain, so this fails instead —
        // the caller has a bug.
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

    // --- a whole world -----------------------------------------------------------------------

    @Test
    fun everyHiddenCardIsFilledIn() {
        val world = determinize(state(), Random(4))

        // Two players of four cards each; the search cannot run on a partial world.
        assertEquals(8, world.hiddenCards.size)
    }

    @Test
    fun theDeckIsDealtTooAndIsTheRestOfTheSamePool() {
        val world = determinize(state(deckSize = 20), Random(4))

        assertEquals(20, world.deckOrder.size, "the sampled deck is the real deck's size")

        // Hands plus deck together hold each rank at most as often as the deck has copies.
        val dealt = world.hiddenCards.values.map { it.rank } + world.deckOrder.map { it.rank }
        for (rank in Rank.entries) {
            val copies = STANDARD_DECK_RANKS.count { it == rank }
            assertTrue(dealt.count { it == rank } <= copies, "${rank.serialName} was over-dealt")
        }
    }

    @Test
    fun aSampledWorldNeverDealsTheSameCardTwice() {
        val world = determinize(state(), Random(11))
        val ids = world.hiddenCards.values.map { it.id } + world.deckOrder.map { it.id }

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
    fun theDiscardPileIsCarriedIntoTheWorldForTheReshuffle() {
        val discard = Pile(listOf(testCard(Rank.KING, "K_0"), testCard(Rank.TWO, "2_0")))
        val world = determinize(state(discard = discard), Random(5))

        assertEquals(listOf("K_0", "2_0"), world.discarded.map { it.id })
    }

    @Test
    fun samplingIsReproducibleFromASeed() {
        // Sorted by key rather than `toSortedMap`, which the stdlib only offers on the JVM.
        fun ranks() = determinize(state(), Random(77)).hiddenCards.entries
            .sortedBy { it.key }
            .map { it.value.rank } + determinize(state(), Random(77)).deckOrder.map { it.rank }
        assertEquals(ranks(), ranks())
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
