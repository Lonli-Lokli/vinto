package game.vinto.engine

import game.vinto.shapes.Difficulty
import game.vinto.shapes.GameState
import game.vinto.shapes.VintoJson
import game.vinto.shapes.canonicalizeGameState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The deal, ported from `legacy-web/packages/local-client/src/lib/__tests__/initialize-game.test.ts`.
 *
 * [DealParityTest] is the stronger check — it compares Kotlin's deal against the one
 * TypeScript recorded for the same seed, across all 50 recordings — but it only runs where
 * the corpus is on disk, and it cannot say anything about a seed the corpus does not contain.
 * These state the structural properties directly, on every target.
 *
 * Two of the TypeScript cases do not port. `initializeGame` here takes a **required** seed,
 * because picking one is ambient randomness and belongs outside a pure engine, so "generates
 * a seed when none is supplied" describes behaviour Kotlin deliberately does not have. And
 * there is no `fourPlayerGame` wrapper to forward a seed through.
 */
class InitializeGameTest {

    private fun deal(seed: Long) = initializeGame(seed, Difficulty.MODERATE)

    private fun canonical(state: GameState) = canonicalizeGameState(state)

    @Test
    fun oneSeedAlwaysDealsTheSameGame() {
        val first = deal(42)
        val second = deal(42)

        assertEquals(canonical(first), canonical(second))
        assertEquals(first.gameId, second.gameId)
        assertEquals(first.rngState, second.rngState)
        assertEquals(first.drawPile.cards.map { it.id }, second.drawPile.cards.map { it.id })
        assertEquals(
            first.players.map { player -> player.cards.map { it.id } },
            second.players.map { player -> player.cards.map { it.id } },
        )
    }

    @Test
    fun differentSeedsDealDifferentGames() {
        val first = deal(1)
        val second = deal(2)

        assertNotEquals(
            first.drawPile.cards.map { it.id },
            second.drawPile.cards.map { it.id },
        )
        assertNotEquals(first.gameId, second.gameId)
    }

    @Test
    fun theGameIdIsDerivedFromTheSeed() {
        // Derived rather than generated, so a game can be identified by the thing that
        // reproduces it.
        assertEquals("vinto-7", deal(7).gameId)
    }

    @Test
    fun everyGameIsFourPlayersOfFiveCards() {
        val state = deal(99)

        assertEquals(4, state.players.size)
        state.players.forEach { assertEquals(5, it.cards.size, "${it.id} was dealt ${it.cards.size}") }
        // 54 in the deck, 20 dealt out.
        assertEquals(34, state.drawPile.size)
    }

    @Test
    fun theBotsKnowTwoCardsAndTheHumanKnowsNone() {
        val state = deal(5)

        val human = state.players.first { it.isHuman }
        assertTrue(human.knownCardPositions.isEmpty(), "the human was dealt knowledge to peek for")
        state.players.filter { it.isBot }.forEach {
            assertEquals(2, it.knownCardPositions.size, "${it.id} knows ${it.knownCardPositions.size}")
        }
    }

    @Test
    fun theDealtStateCarriesNoClockAndNoGeneratedIds() {
        // The two things that would make a recording unreplayable, checked the same way the
        // TypeScript checks them: by looking at the serialised state for their shapes.
        val serialised = VintoJson.encodeToString(GameState.serializer(), deal(3))

        assertTrue(
            !Regex("""\d{13}""").containsMatchIn(serialised),
            "something looking like epoch millis is in the initial state",
        )
        assertTrue(
            !Regex("""[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}""")
                .containsMatchIn(serialised),
            "something looking like a uuid is in the initial state",
        )
    }

    @Test
    fun theWholeDeckIsDealtWithNothingLostOrDuplicated() {
        // Not in the TypeScript. A shuffle that drops or repeats a card would still satisfy
        // every count above, and would go unnoticed until a game behaved impossibly.
        val state = deal(2026)
        val everyCard = state.players.flatMap { it.cards } + state.drawPile.cards + state.discardPile.cards

        assertEquals(54, everyCard.size, "the deck is not 54 cards after dealing")
        assertEquals(54, everyCard.map { it.id }.toSet().size, "a card id appears twice")
    }
}
