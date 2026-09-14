package game.vinto.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onAllNodesWithContentDescription
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
import kotlin.time.Duration.Companion.minutes

/**
 * A control the table has offered does not go away again on its own.
 *
 * The screen draws the table each animated move left behind, and the rail is built from that
 * same picture — so while the bots' turns play out, the buttons are the buttons of a position
 * that has already passed. A toss-in window the player has closed replays with its button, so
 * a control the player was finished with comes back for as long as it takes the next card to
 * move, and then goes again. Reported as *"first no controls, then the bot draws and I see
 * controls for a second, then nothing again"*.
 *
 * **Driven through the real screen, and that is not decoration.** The first attempt at this
 * dispatched straight into the session from `runBlocking`, which runs the whole turn — your
 * move, the bots' search, both batches of frames and the final view — before the UI composes
 * once. That collapses the very gap the bug lives in: it reproduced *a* blink, but not this
 * one, and would have been satisfied by a fix that changed nothing a player could see. Here
 * the press goes through the button, the dispatch runs on the composition's own scope, and the
 * search yields the main thread exactly as it does on a phone.
 *
 * What is sampled is the *rail*: how many things on it can be pressed, tick by tick. The
 * invariant is the blink stated without naming a phase — a button that appears and then
 * disappears with the player having touched nothing is a button that should never have
 * appeared. It is the taking away that is asserted, because that is the half a player reads as
 * the app changing its mind.
 */
@OptIn(ExperimentalTestApi::class)
class ControlBlinkTest {

    @Test
    fun aControlIsNeverOfferedAndThenWithdrawnWhileTheBotsPlay() = runComposeUiTest(testTimeout = BUDGET) {
        val game = runBlocking { LocalGame.start(MemoryVault(), SEED, Difficulty.EASY) }
        setContent {
            // The pauses are the point here, so the pacing is a player's rather than nobody's.
            CompositionLocalProvider(LocalPacing provides 1f) {
                VintoTheme { GameScreen(game, pace = Pace.BRISK, onSettings = {}, onQuit = {}) }
            }
        }
        waitForIdle()

        press("You, card 1")
        press("You, card 2")
        press("Start the round")
        press("Draw Card")
        press("Discard")

        // The discard has to *land* before the window it opens is on the rail.
        repeat(STEPS) {
            mainClock.advanceTimeBy(TICK)
            waitForIdle()
        }

        // The toss-in window that follows a discard is the player's to close, and the bots only
        // play once it is. Everything after this press is the table catching up on its own.
        val closing = onAllNodesWithContentDescription("Continue").fetchSemanticsNodes()
        assertTrue(
            closing.isNotEmpty(),
            "the discard opened no window for the player to close: " + texts(),
        )
        press("Continue")

        val trace = mutableListOf<Int>()
        repeat(STEPS) {
            mainClock.advanceTimeBy(TICK)
            waitForIdle()
            trace += onAllNodes(hasClickAction()).fetchSemanticsNodes().size
        }

        val steps = trace.filterIndexed { i, n -> i == 0 || trace[i - 1] != n }
        assertTrue(steps.size > 1, "the bots never played, so nothing was traced: $steps")
        assertTrue(
            steps.zipWithNext().none { (before, after) -> after < before },
            "the rail offered controls and then took them away with nobody touching it: $steps",
        )
    }

    private fun ComposeUiTest.texts(): List<String> =
        onAllNodes(hasClickAction()).fetchSemanticsNodes().mapNotNull { node ->
            node.config
                .getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)
                ?.firstOrNull()
                ?.text
                ?: node.config
                    .getOrNull(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription)
                    ?.firstOrNull()
        }

    /**
     * Presses a control by the words it is announced with.
     *
     * By its **description**, never by its text: "Discard" is a button on the rail and also the
     * label under the pile on the felt, and matching by text found the label first — so the
     * turn was never taken and the case asserted about a table that had not moved.
     */
    private fun ComposeUiTest.press(label: String) {
        onNodeWithContentDescription(label).performClick()
        waitForIdle()
    }

    private companion object {
        const val SEED = 12L
        const val TICK = 100L
        const val STEPS = 60

        /**
         * A CI budget, not a claim about the code — the same one, and for the same reason, as
         * `SwapAnimationTest`'s.
         *
         * `runComposeUiTest` inherits `runTest`'s sixty-second wall clock, and this test spends it
         * on real work by design: 120 paused-clock ticks, each rendered and settled, around three
         * bots' MCTS running on the composition's own scope. Collapsing that is not an option —
         * the note at the top of this file is about an earlier version that drove the session
         * directly, ran the whole turn before the UI composed once, and so could not see the bug.
         *
         * It measures **14.7 s** on an Apple-silicon laptop and fitted inside the minute there. It
         * did not fit on a GitHub runner, which is several times slower at exactly this kind of
         * work: the deadline fired mid-body and reported an `UncompletedCoroutinesError` naming
         * neither the rail nor the blink, in two of six runs — the intermittency being the tell
         * that it was landing near the line rather than hanging.
         *
         * Five minutes is a hang detector, which is what this clock is for. It is not a licence
         * for the test to grow: at anything near it, the thing to fix is the test.
         */
        val BUDGET = 5.minutes
    }
}
