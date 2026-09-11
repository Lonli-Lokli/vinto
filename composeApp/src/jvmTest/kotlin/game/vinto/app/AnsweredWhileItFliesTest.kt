package game.vinto.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import game.vinto.app.game.GameScreen
import game.vinto.app.theme.VintoTheme
import game.vinto.client.LocalGame
import game.vinto.client.MemoryVault
import game.vinto.client.Pace
import game.vinto.shapes.Difficulty
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A card tapped while the last one is still flying is answered while it is still flying.
 *
 * The report, in the player's words: *"when I draw and play a 7, the card animates to the
 * discard; during this animation I click on my card but it's not shown until after the
 * animation finished."*
 *
 * **The tap was never lost**, which is what made it hard to place: the engine took it at once —
 * the peeked position appears in `knownCardPositions` in the same instant — and it was the
 * *picture* that queued. So for the rest of the flight and the read beat after it, well over a
 * second, the table went on asking the question the player had already answered. A player who
 * sees no answer taps again, which is the second half of the complaint.
 *
 * What fixed it is not a shortcut for this case: the stage takes arrivals off the flow as they
 * are made rather than between batches, and the pause after a move ends when somebody acts —
 * because that pause is for reading a *settled* table. `ThrownTogetherTest` holds the other half
 * of the same change, which is that hands coming down together are drawn together.
 *
 * Seed 9 deals this seat a seven as its first draw, which is the reported card and the reason
 * the seed is pinned rather than fresh.
 */
@OptIn(ExperimentalTestApi::class)
class AnsweredWhileItFliesTest {

    @Test
    fun tappingACardWhileTheLastOneFliesShowsItWithoutWaitingForTheFlight() = runComposeUiTest {
        val game = runBlocking { LocalGame.start(MemoryVault(), SEED, Difficulty.EASY) }
        mainClock.autoAdvance = false
        setContent {
            // A player's pacing rather than nobody's: the pauses are the thing being measured.
            CompositionLocalProvider(LocalPacing provides 1f) {
                VintoTheme { GameScreen(game, pace = Pace.BRISK, onSettings = {}, onQuit = {}) }
            }
        }
        settle()

        press("You, card 1")
        press("You, card 2")
        press("Start the round")
        press("Draw Card")
        press("Use Action")

        // Barely into the flight. The prompt is up, because the engine is waiting to be told
        // which card — and this is the moment the player reaches for one.
        mainClock.advanceTimeBy(INTO_THE_FLIGHT)
        waitForIdle()
        assertTrue(ASKING in texts(), "the table is not asking for a card, so this proves nothing")

        onNodeWithContentDescription("You, card 3").performClick()
        waitForIdle()
        mainClock.advanceTimeBy(A_MOMENT)
        waitForIdle()

        assertTrue(
            ASKING !in texts(),
            "the table went on asking for a card the player had already picked: ${texts()}",
        )
    }

    private fun ComposeUiTest.settle() = repeat(SETTLE_STEPS) {
        mainClock.advanceTimeBy(STEP_MS)
        waitForIdle()
    }

    private fun ComposeUiTest.press(label: String) {
        onNodeWithContentDescription(label).performClick()
        settle()
    }

    private fun ComposeUiTest.texts(): List<String> =
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text))
            .fetchSemanticsNodes()
            .mapNotNull { it.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text }

    private companion object {
        /** Deals this seat a seven to draw first — the reported card. */
        const val SEED = 9L

        /** What the table asks once a peek-own card has been played. */
        const val ASKING = "Look at one of your own cards"

        const val STEP_MS = 100L
        const val SETTLE_STEPS = 12

        /** A couple of frames after the press: the card has barely left the hand. */
        const val INTO_THE_FLIGHT = 100L

        /**
         * Long enough for the answer to be drawn, far short of what used to hold it.
         *
         * The old path cost the rest of the flight plus `Pacing.READ_MS` — over a second — so
         * this is a margin rather than a race.
         */
        const val A_MOMENT = 350L
    }
}
