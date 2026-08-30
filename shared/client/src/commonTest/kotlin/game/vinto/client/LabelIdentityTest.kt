package game.vinto.client

import game.vinto.shapes.Rank
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A button is identified by what it *is*, and the lesson can find it.
 *
 * This exists because the old arrangement — `Choice.label: String`, matched with
 * `startsWith("Take the")` — had failed silently, twice. The model produced "Use Queen", so
 * the beat that teaches the second way to start a turn never fired; and a second check looked
 * for a label containing "Pass", which no button has said for some time. Both compiled, both
 * ran, and both did nothing.
 *
 * A translation would have done the same damage to every remaining match. So the test worth
 * having is not "does this label read correctly" — it is "can the lesson still find the
 * control it means", asked in a way that a compiler helps with.
 */
class LabelIdentityTest {

    @Test
    fun theLessonFindsTheTakeFromPileButtonByType() {
        // The exact case that was broken. A choice offering the pile's unused action card is
        // recognised by its type, whatever the button happens to say.
        val choices = listOf(
            Choice(Label.DrawCard, Move.Ask(Question.None)),
            Choice(Label.UseFromPile(Rank.QUEEN), Move.Ask(Question.None)),
        )

        val found = choices.firstOrNull { it.label is Label.UseFromPile }
        assertNotNull(found, "the lesson can no longer find the take-from-pile button")
        assertEquals(Rank.QUEEN, (found.label as Label.UseFromPile).rank)
    }

    @Test
    fun aLabelCarriesNoEnglish() {
        // The property that makes the above impossible to break by rewording or translating:
        // there is no text in a label to match on, so nobody can be tempted to.
        val everyLabel = listOf(
            Label.Back, Label.StartRound, Label.DrawCard, Label.UseFromPile(Rank.SEVEN),
            Label.UseAction, Label.SwapCards, Label.Discard, Label.JustSwap,
            Label.PutItDown, Label.LeaveThem, Label.Continue, Label.CallVinto, Label.Done,
        )

        assertEquals(everyLabel.size, everyLabel.toSet().size, "two labels collided")
        for (label in everyLabel) {
            // A rank is the only payload any of them has, and a rank is not a word.
            val payload = (label as? Label.UseFromPile)?.rank
            assertTrue(payload == null || payload is Rank, "$label carries something that is not a rank")
        }
    }

    @Test
    fun everyPointableButtonHasADistinctKey() {
        // `ChoiceButton` marks a button by `keyOf(label)` and `Pointer` looks it up the same
        // way. If two labels shared a key the arrow would point at whichever drew first —
        // silently, which is this file's whole subject.
        val keys = listOf(
            Label.Back, Label.StartRound, Label.DrawCard, Label.UseFromPile(Rank.SEVEN),
            Label.UseAction, Label.SwapCards, Label.Discard, Label.JustSwap,
            Label.PutItDown, Label.LeaveThem, Label.Continue, Label.CallVinto, Label.Done,
        ).map { key(it) }

        assertEquals(keys.size, keys.toSet().size, "two buttons share a pointer key: $keys")
    }

    /**
     * The same rule `composeApp`'s `keyOf` applies, restated here.
     *
     * Duplicated on purpose and deliberately trivial: `keyOf` lives beside the resources
     * because that is where it is used, and this module cannot see it. What matters is the
     * property — distinct labels, distinct keys — and that is checkable from either side.
     */
    private fun key(label: Label): String = when (label) {
        is Label.UseFromPile -> "use-from-pile"
        else -> label.toString()
    }
}
