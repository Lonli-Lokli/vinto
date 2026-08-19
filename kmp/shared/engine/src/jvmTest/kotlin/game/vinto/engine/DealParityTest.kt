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
        File("../../../fixtures/recordings")
            .listFiles { file -> file.extension == "json" }
            ?.sortedBy { it.name }
            ?.map { it.name to VintoJson.decodeFromString(GameRecording.serializer(), it.readText()) }
            ?: emptyList()

    @Test
    fun theSameSeedDealsTheSameGame() {
        assertTrue(recordings.size >= 50, "expected the corpus, found ${recordings.size}")

        val mismatches = recordings.mapNotNull { (name, recording) ->
            val dealt = initializeGame(recording.settings.seed, recording.settings.difficulty)

            // The corpus is bot-vs-bot self-play: `tools/generate-recordings.ts` deals a normal
            // game and then marks every seat a bot. Reproduced here so the comparison is
            // like-for-like rather than tripping over a flag the generator changed.
            val allBots = dealt.copy(
                players = dealt.players.map { it.copy(isHuman = false, isBot = true) },
            )

            val expected = hashGameState(recording.initialState)
            val actual = hashGameState(allBots)
            if (expected == actual) null else "$name (seed ${recording.settings.seed})"
        }

        assertEquals(emptyList(), mismatches, "Kotlin dealt a different game from the same seed")
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
