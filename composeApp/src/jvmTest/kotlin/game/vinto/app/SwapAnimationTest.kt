package game.vinto.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import game.vinto.app.theme.VintoTheme
import game.vinto.client.MemoryVault
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A swap is two cards moving, and both of them have to be seen to move.
 *
 * The drawn card goes into the hand and the card it replaces comes out face-up onto the
 * discard — that exchange *is* the move, and a table that performs it instantly has told the
 * player only that something changed. The choreography has emitted both beats since it was
 * written; what this checks is that they reach the screen.
 *
 * It watches the seat rather than the flight: while a card is in the air the seat draws an
 * empty gap in its place, so the card's own node disappears for the length of the animation
 * and comes back. No gap, no animation.
 */
@OptIn(ExperimentalTestApi::class)
class SwapAnimationTest {

    @Test
    fun swappingLiftsTheCardOutOfTheSeatAndPutsAnotherBack() = runComposeUiTest {
        // The clock runs normally while the round is being set up, and is taken over only for
        // the animation this test is about.
        //
        // It used to be paused for the whole test. From Compose Multiplatform 1.12 that
        // deadlocks: `waitForIdle()` under a paused clock waits for a frame that will not
        // come until something advances the clock, and `settle()` is the thing that would
        // have. The test hung for its full minute and reported an `UncompletedCoroutinesError`
        // rather than an assertion, which is what a deadlock looks like from outside.
        setContent { VintoTheme { App(seeds = { SEED }, vault = MemoryVault()) } }
        settle()

        onNodeWithContentDescription("Play").performClick()
        settle()
        onNodeWithContentDescription("You, card 1").performClick()
        settle()
        onNodeWithContentDescription("You, card 2").performClick()
        settle()
        onNodeWithContentDescription("Start the round").performClick()
        settle()

        onNodeWithContentDescription("Draw Card").performClick()
        settle()
        onNodeWithContentDescription("Swap Cards").performClick()
        settle()
        onNodeWithContentDescription(SLOT).performClick()
        settle()
        onNodeWithContentDescription("Just Swap").performClick()

        // From here the clock is this test's: the point is to catch a single frame in which
        // the seat is empty, and an auto-advancing clock would step past it.
        mainClock.autoAdvance = false

        // Step through the animation looking for the moment the seat is empty.
        var sawTheGap = false
        repeat(STEPS) {
            mainClock.advanceTimeBy(STEP_MS)
            if (onAllNodesWithContentDescription(SLOT).fetchSemanticsNodes().isEmpty()) {
                sawTheGap = true
            }
        }

        assertTrue(sawTheGap, "the card never left the seat: the swap did not animate")

        // Hand the clock back before waiting on it again. `settle()` ends in `waitForIdle()`,
        // and from Compose Multiplatform 1.12 that waits for a frame which a paused clock will
        // never produce — the test then hangs for its full minute and reports an
        // `UncompletedCoroutinesError` instead of an assertion.
        mainClock.autoAdvance = true
        settle()
        onNodeWithContentDescription(SLOT).assertIsDisplayed()
    }

    private fun androidx.compose.ui.test.ComposeUiTest.settle() {
        mainClock.advanceTimeBy(SETTLE_MS)
        waitForIdle()
    }

    private companion object {
        const val SEED = 20260819L
        const val SLOT = "You, card 3"
        const val SETTLE_MS = 4_000L
        const val STEP_MS = 40L
        const val STEPS = 60
    }
}
