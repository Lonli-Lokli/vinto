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
import kotlin.time.Duration.Companion.minutes

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
    fun swappingLiftsTheCardOutOfTheSeatAndPutsAnotherBack() = runComposeUiTest(testTimeout = BUDGET) {
        mainClock.autoAdvance = false
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

        // Step through the animation looking for the moment the seat is empty.
        var sawTheGap = false
        repeat(STEPS) {
            mainClock.advanceTimeBy(STEP_MS)
            if (onAllNodesWithContentDescription(SLOT).fetchSemanticsNodes().isEmpty()) {
                sawTheGap = true
            }
        }

        assertTrue(sawTheGap, "the card never left the seat: the swap did not animate")
        settle()
        onNodeWithContentDescription(SLOT).assertIsDisplayed()
    }

    private fun androidx.compose.ui.test.ComposeUiTest.settle() {
        mainClock.advanceTimeBy(SETTLE_MS)
        waitForIdle()
    }

    private companion object {
        /**
         * A CI budget, not a claim about the code.
         *
         * `runComposeUiTest` inherits `runTest`'s sixty-second wall clock, and this test spends
         * that on real work: seven `settle()` calls, each rendering four seconds of paused
         * clock frame by frame, around three bots' MCTS. It fit under Compose Multiplatform
         * 1.8 and does not under 1.12, which renders more per frame — measured at **89 s** to
         * reach the assertion, so the deadline fired mid-body and reported an
         * `UncompletedCoroutinesError` rather than anything about swapping cards.
         */
        val BUDGET = 5.minutes

        const val SEED = 20260819L
        const val SLOT = "You, card 3"

        /**
         * How much clock a click needs before the table has finished reacting.
         *
         * It was 4,000 ms, and nothing had measured it. With the clock paused, `settle()`
         * renders every frame in that span — so the number is multiplied by the seven settles
         * this test does, and it dominated the runtime. Measured, at 40 ms a frame:
         *
         *     4,000 ms -> 99 s     2,000 ms -> 55 s     1,000 ms -> 34 s
         *
         * 2,000 passes with twice the margin of the smallest value that works, and halves the
         * test. It is *virtual* time, so this is deterministic rather than a bet on the
         * machine — a slower runner takes longer per frame but renders the same frames.
         */
        const val SETTLE_MS = 2_000L
        const val STEP_MS = 40L
        const val STEPS = 60
    }
}
