package game.vinto.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import game.vinto.app.crash.Where
import game.vinto.app.game.GameScreen
import game.vinto.app.theme.VintoTheme
import game.vinto.client.LocalGame
import game.vinto.client.MemoryVault
import game.vinto.client.Pace
import game.vinto.client.playItselfOut
import game.vinto.shapes.Difficulty
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * One whole round, on the real screen, start to standings.
 *
 * Every other UI test drives the table into one situation and reads it; this one plays a
 * round *out* — the deal, the setup peeks, a turn taken by tapping the actual buttons, and
 * then the rest of the game at speed — and holds the screen to what a player was promised at
 * the end of it: the strip saying the round is over, the sheet with all four hands, and the
 * line explaining why the hands went face-up.
 *
 * The failure it exists to catch is the one no situational test can: a round that plays
 * correctly in the model and never *ends* on the screen — a frame queue that wedges, a
 * scoring transition the stage drops, a sheet that opens on a state it cannot render.
 *
 * The opening moves go through the same semantics a finger uses; the tail goes through
 * [playItselfOut], which dispatches into the same session the screen is watching — so the
 * whole game flows through the frames the stage is animating, at a pace no player would.
 */
@OptIn(ExperimentalTestApi::class)
class FullGameUiTest {

    @Test
    fun aWholeRoundIsPlayedOutOnTheScreen() = runComposeUiTest {
        val game = LocalGame.start(MemoryVault(), FIXED_SEED, Difficulty.EASY)
        setContent {
            // Nobody is watching this one, so the table does not wait for anybody — see
            // `LocalPacing`. Without it this case spends its whole life rendering pauses.
            CompositionLocalProvider(LocalPacing provides 0f) {
                VintoTheme { GameScreen(game, pace = Pace.BRISK, onSettings = {}, onQuit = {}) }
            }
        }
        waitForIdle()

        // The opening, by hand: the two setup peeks and the first draw, tapped rather than
        // dispatched, because this half of the test is about the buttons being where the
        // round needs them.
        onNodeWithContentDescription("You, card 1").performClick()
        waitForIdle()
        onNodeWithContentDescription("You, card 2").performClick()
        waitForIdle()
        button("Start the round").performClick()
        waitForIdle()

        // The crash reporter's address is written from `rememberHolder`, which is the one
        // point a local game and an online one both pass through — so a table on screen is
        // exactly the condition under which it has to be filled. Asserted here rather than in
        // a test of its own because the thing that can regress is the *wiring*, and this is
        // the only suite with a real table in it.
        assertEquals(
            game.session.view.value.gameId,
            Where.now().gameId,
            "the crash reporter does not know which game is on screen",
        )

        button("Draw Card").performClick()
        waitForIdle()
        button("Discard").assertIsDisplayed()
        button("Discard").performClick()
        waitForIdle()

        // The rest of the round at machine speed, through the same dispatch the buttons use.
        // The screen keeps watching the session's frames while this runs.
        assertTrue(
            runBlocking { game.session.playItselfOut(seed = FIXED_SEED) },
            "the round never reached its scoring",
        )

        // The stage still has the whole game queued, and it is queued as *time* — a beat
        // before each move, a dwell after a drawn card, a gap between the scenes of one move.
        // Watching that out is the one thing this test is not here to do: at `Pace.BRISK` a
        // forty-turn round is minutes of real clock, and every one of those milliseconds is a
        // frame this machine has to render. The original two-minute wait was not a budget, it
        // was a bet on the runner — it got as far as turn 44 in 380 seconds and lost.
        //
        // `LocalPacing` at zero is the seam: the same frames, the same queue, the same scenes,
        // with the waiting taken out. What is being tested is that the round *reaches* its
        // ending on screen, and nothing about that is carried by the pauses.
        waitUntil(timeoutMillis = END_TIMEOUT) {
            onAllNodesWithContentDescription("See the score").fetchSemanticsNodes().isNotEmpty()
        }
        button("See the score").performClick()
        waitForIdle()

        // The sheet: the round named, the score offered, and the reason the hands went
        // face-up. All three of A1's endings contain the same two words — "You called
        // Vinto…", "%s called Vinto…", "Nobody called Vinto — the deck ran out…" — so the
        // assertion is on the promise (a reason is given) rather than on this seed's ending.
        onNodeWithText("Round 1", substring = true).assertIsDisplayed()
        button("Deal the next round").assertIsDisplayed()
        assertTrue(
            texts().any { it.contains("called Vinto") },
            "the sheet does not say why the round ended: ${texts()}",
        )
    }

    private fun ComposeUiTest.button(label: String) = onNodeWithContentDescription(label)

    private fun ComposeUiTest.texts() =
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text))
            .fetchSemanticsNodes()
            .mapNotNull { it.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text }

    private companion object {
        /** The seed `TableUiTest` already walks to its first draw-and-discard. */
        const val FIXED_SEED = 20260819L

        /**
         * Long enough for a whole round with the pauses taken out, and no longer.
         *
         * With `LocalPacing` at zero what is left is one frame per scene, so this is a wedge
         * detector rather than a pace: a queue that stops draining never reaches the score,
         * and says so in a minute instead of in whatever the runner felt like.
         */
        const val END_TIMEOUT = 60_000L
    }
}
