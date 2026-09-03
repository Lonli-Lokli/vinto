package game.vinto.engine

import game.vinto.shapes.GameRecording
import game.vinto.shapes.VintoJson
import game.vinto.shapes.hashGameState
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The deal itself must be cross-language.
 *
 * Every recording carries the seed it was dealt from, so `initializeGame(seed)` in Kotlin can
 * be compared against the `initialState` TypeScript produced for that same seed — across all
 * 50 of them. That checks the whole chain the replay gate takes for granted: deck construction
 * order, the seeded shuffle over it, how cards are dealt out, what lands in the draw pile,
 * the derived `gameId`, and the generator state carried forward.
 *
 * Without this, a Kotlin server could deal a *different* game from the same seed and every
 * replay would still pass, because replay starts from a recorded `initialState` rather than
 * re-running the deal.
 */
class DealParityTest {

    private val recordings: List<Pair<String, GameRecording>> =
        File(System.getProperty("vinto.fixtures") ?: "../../fixtures", "recordings")
            .listFiles { file -> file.extension == "json" }
            ?.sortedBy { it.name }
            ?.map { it.name to VintoJson.decodeFromString(GameRecording.serializer(), it.readText()) }
            ?: emptyList()

    @Test
    fun theSameSeedDealsTheSameGame() {
        assertTrue(recordings.size >= 50, "expected the corpus, found ${recordings.size}")

        val mismatches = recordings.mapNotNull { (name, recording) ->
            val dealt = initializeGame(recording.settings.seed, recording.settings.difficulty)

            val expected = hashGameState(seatsNormalised(recording.initialState))
            val actual = hashGameState(seatsNormalised(dealt))
            if (expected == actual) null else "$name (seed ${recording.settings.seed})"
        }

        assertEquals(emptyList(), mismatches, "Kotlin dealt a different game from the same seed")
    }

    /**
     * The two fields a seat carries that the DEAL does not decide, flattened on both sides
     * before hashing, so this test compares the algorithm and nothing else.
     *
     * There are two of them and they arrived for different reasons.
     *
     * **The bot flags** were always here. The corpus is bot-vs-bot self-play:
     * `tools/generate-recordings.ts` dealt a normal game and then marked every seat a bot, so
     * comparing raw would trip over a flag the generator flipped after `initializeGame` had
     * finished.
     *
     * **The names** are newer. The corpus is frozen (`fixtures/recordings/README.md`) and every
     * `initialState` in it carries the cast that shipped with the TypeScript engine — four
     * humanoid turtles called Leo, Raph, Mikey and Don, which is the Teenage Mutant Ninja
     * Turtles cast and which both stores refuse (`brand/avatars/_shared.md` has the whole
     * reasoning). Renaming the seats is what unblocked submission; the corpus cannot be
     * regenerated to match, and rewriting it is what `CorpusIsFrozenTest` exists to prevent.
     *
     * Normalising them is honest rather than convenient, because **a name is a constant this
     * file chooses, not a step the deal computes**: `gameId` is the seed and nothing else
     * (`GAME_ID_PREFIX + seeded`), no shuffle reads a name, and no rule dispatches on one. Every
     * property this test was written for — deck construction order, the seeded shuffle over it,
     * how cards are dealt out, what lands in the draw pile, the derived `gameId`, the generator
     * state carried forward — is still compared byte for byte. What is no longer compared is a
     * string literal, and `theSeatsAreNamedForTheirEmblems` below pins that instead.
     */
    private fun seatsNormalised(state: game.vinto.shapes.GameState) = state.copy(
        players = state.players.mapIndexed { seat, player ->
            player.copy(name = "seat$seat", nickname = "seat$seat", isHuman = false, isBot = true)
        },
    )

    /**
     * What the seats are actually called, which [seatsNormalised] deliberately stops checking.
     *
     * Each is the emblem on its own portrait — a leaf, a flame, a crescent, a ridge — and the
     * room's `BOT_NAMES` deals the same four so an online seat and an offline one are the same
     * opponent. If a rename ever lands, this is the test that should fail.
     */
    @Test
    fun theSeatsAreNamedForTheirEmblems() {
        val dealt = initializeGame(seed = 42)
        assertEquals(listOf("You", "Ember", "Sky", "Dune"), dealt.players.map { it.name })
        assertEquals(dealt.players.map { it.name }, dealt.players.map { it.nickname })
    }

    @Test
    fun theDeckIsFiftyFourCardsCoveringEveryRank() {
        val deck = createDeck()
        assertEquals(54, deck.size)
        assertEquals(54, deck.map { it.id }.toSet().size, "card ids are not unique")
        assertTrue(deckCoversEveryRank())

        // Number cards carry no actionText; that absence is what marks them non-actionable
        // when a toss-in decides whether to queue a card for its action.
        assertTrue(deck.filter { it.rank.isActionableRank() }.all { !it.actionText.isNullOrEmpty() })
        assertTrue(deck.filterNot { it.rank.isActionableRank() }.all { it.actionText == null })
    }

    private fun game.vinto.shapes.Rank.isActionableRank() =
        game.vinto.shapes.getCardConfig(this).action != null
}
