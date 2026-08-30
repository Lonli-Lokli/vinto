package game.vinto.shapes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `shortDescription` is **data**, not copy, and must not be translated.
 *
 * This test exists to fail loudly and explain itself, because the alternative is fifty failing
 * recordings and a bad afternoon.
 *
 * The chain is not obvious and nothing else states it. `CardConfig.shortDescription` is copied
 * into `Card.actionText` when a toss-in resolves (`TossInHandlers`). `Card`s live in
 * `GameState`. The canonical hash excludes exactly three things — `turnActions`,
 * `roundActions` and `botMemory` — so `actionText` is **inside the hash** that every one of the
 * 50 fixtures pins against the value TypeScript computed. Change the words and every recording
 * diverges.
 *
 * So the localization work in §6h stops at this boundary. `name`, `longDescription` and
 * `helpText` are presentation and may be translated; `shortDescription` may not, until
 * `actionText` stops being a string in the state at all — which is the right fix and needs the
 * corpus regenerated, something §1d says is on its way to being impossible.
 */
class CardCopyIsDataTest {

    /**
     * The exact strings TypeScript wrote, for the ranks whose action text reaches the state.
     *
     * Written out rather than derived: a test that compares `CARD_CONFIGS` to itself would
     * pass after somebody translated it, which is the one thing this is here to prevent.
     */
    private val pinned = mapOf(
        Rank.SEVEN to "Peek 1 of your cards",
        Rank.EIGHT to "Peek 1 of your cards",
        Rank.NINE to "Peek 1 opponent card",
        Rank.TEN to "Peek 1 opponent card",
    )

    @Test
    fun theActionTextInTheStateIsExactlyWhatTypeScriptWrote() {
        for ((rank, expected) in pinned) {
            assertEquals(
                expected,
                getCardShortDescription(rank),
                "$rank's shortDescription reaches Card.actionText, which is inside the canonical " +
                    "hash. Changing it diverges all 50 fixtures from TypeScript. If this is a " +
                    "translation, see docs/kotlin/README.md §6h — this field is data, not copy.",
            )
        }
    }

    @Test
    fun everyActionCardHasOneAndNoPlainCardDoes() {
        // The shape of the rule, so a *new* rank cannot quietly acquire hashed prose either.
        for ((rank, config) in CARD_CONFIGS) {
            if (config.action == null) {
                assertTrue(
                    config.shortDescription.isEmpty(),
                    "$rank has no action but carries action text, which would enter the hash",
                )
            } else {
                assertTrue(
                    config.shortDescription.isNotEmpty(),
                    "$rank has an action and no text for it",
                )
            }
        }
    }

    @Test
    fun theHashExcludesOnlyWhatItSaysItExcludes() {
        // If this list ever grows to include `actionText`, the constraint above is lifted and
        // this file can go. If it shrinks, something else has just entered the hash.
        assertEquals(setOf("turnActions", "roundActions"), CanonicalJson.EXCLUDED_STATE_FIELDS)
        assertEquals(setOf("botMemory"), CanonicalJson.EXCLUDED_PLAYER_FIELDS)
    }
}
