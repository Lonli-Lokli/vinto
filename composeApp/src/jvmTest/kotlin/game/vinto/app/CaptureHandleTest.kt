package game.vinto.app

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import game.vinto.app.theme.VintoTheme
import game.vinto.client.MemoryVault
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The handle a store capture drives, and the states it reaches.
 *
 * `zdymak` photographs five scenes and three of them are not screens anybody can navigate to — a
 * table mid-round, a finished round, a lobby. Its config left them as names for shots taken by
 * hand precisely because there was no way to ask the app for them. This is that way, and these
 * are the tests that make it a feature rather than a flag nobody runs.
 *
 * **The failure this guards against is silence.** A handle that stops working does not crash: the
 * app opens on its home screen, the capture run photographs five home screens, and the listing
 * gets a set of identical pictures that somebody notices in review. So each case asserts
 * something only that state shows.
 */
@OptIn(ExperimentalTestApi::class)
class CaptureHandleTest {

    @Test
    fun everySceneIdIsRecognisedAndAnythingElseIsNot() {
        MarketingScene.entries.forEach {
            assertEquals(it, MarketingScene.named(it.id), "${it.id} is not its own id")
        }

        // Whitespace and case forgiven, because these are typed into an `adb` line by hand.
        assertEquals(MarketingScene.TABLE, MarketingScene.named("  TABLE "))

        // Everything else is null rather than a default. A typo that silently opened the home
        // screen is the exact failure this handle exists to make impossible.
        listOf(null, "", " ", "tabel", "home2", "Screen.Home").forEach {
            assertNull(MarketingScene.named(it), "'$it' was accepted as a scene")
        }
    }

    /** No argument is the ordinary launch, and it must be untouched by any of this. */
    @Test
    fun withoutAHandleTheAppOpensWhereItAlwaysDid() = runComposeUiTest {
        open(marketing = null)
        onNodeWithContentDescription("Play online").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theTableSceneOpensOnARoundInProgress() = runComposeUiTest {
        open(marketing = "table")

        // The rail's two piles: only a dealt table has them, and a home screen has neither.
        // `onAllNodes` because each pile is labelled twice — once on the pile and once on the
        // rail's own caption — and a strict single-node matcher fails on the second one.
        assertTrue(onAllNodesWithText("DRAW", substring = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue(onAllNodesWithText("DISCARD", substring = true).fetchSemanticsNodes().isNotEmpty())
    }

    /**
     * And it is the SAME table every time.
     *
     * A screenshot set is regenerated on every release. If the deal moved between runs, every
     * listing would need re-reviewing for a change nobody made — so the seed is pinned, and this
     * is what says so out loud.
     */
    @Test
    fun theTableSceneIsTheSameTableTwice() = runComposeUiTest {
        val first = tableSignature()
        val second = tableSignature()
        assertEquals(first, second, "the staged deal moved between two runs")
        assertTrue(first.isNotEmpty(), "no cards were read off the staged table")
    }

    @Test
    fun theScoreSceneOpensOnAFinishedRound() = runComposeUiTest {
        open(marketing = "score")
        // The score sheet's three column headings, together.
        //
        // This started as a check for "Vinto" and then for "called Vinto", and BOTH passed on the
        // wrong screen: the first matches the home wordmark, the second matches the move log on a
        // table where Vinto had just been called but the round had not finished. The scene was
        // genuinely wrong and the test said it was fine — which is the exact failure this class
        // was written to catch, arriving in the test rather than in the app.
        //
        // Three headings that only appear side by side on the finished sheet is the version that
        // cannot be satisfied by a table or a menu.
        // Waited for rather than asserted straight away: the table plays its opening deal before
        // it shows anything else, so the sheet arrives a few frames after the screen does.
        waitUntil(timeoutMillis = SHEET_TIMEOUT_MS) {
            onAllNodesWithText("game", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        listOf("hand", "round", "game").forEach { column ->
            assertTrue(
                onAllNodesWithText(column, substring = true).fetchSemanticsNodes().isNotEmpty(),
                "the score sheet's '$column' column is missing, so the round did not finish",
            )
        }
    }

    @Test
    fun theTeachSceneOpensTheLesson() = runComposeUiTest {
        open(marketing = "teach")
        // The menu's own control, which the lesson does not carry. `assertDoesNotExist` rather
        // than a displayed check: an absent node makes `isDisplayed` throw rather than answer.
        onNodeWithContentDescription("Play online").assertDoesNotExist()
    }

    /**
     * The lobby is the online MENU, and that is the honest answer rather than a shortfall.
     *
     * A real lobby is a room, and a room is the network — so staging one means either opening a
     * socket during a screenshot run or writing a second implementation of the room's state
     * machine for one picture. `MarketingScene.LOBBY` says as much.
     */
    @Test
    fun theLobbySceneOpensTheOnlineMenu() = runComposeUiTest {
        open(marketing = "lobby")
        onNodeWithContentDescription("Open a room").performScrollTo().assertIsDisplayed()
    }

    // ------------------------------------------------------------------ the reading

    /** What is on the felt, as text — enough to tell one deal from another. */
    private fun tableSignature(): String {
        var seen = ""
        runComposeUiTest {
            open(marketing = "table")
            seen = onAllNodesWithText("DRAW", substring = true)
                .fetchSemanticsNodes()
                .joinToString { node -> node.positionInRoot.toString() + node.size.toString() }
        }
        return seen
    }

    /**
     * [settle] is false for a scene that never goes idle, which is the demo: `waitForIdle` waits
     * for a composition with no work left, and one that is playing a round always has some.
     */
    private fun ComposeUiTest.open(marketing: String?, settle: Boolean = true) {
        setContent { VintoTheme { App(seeds = { SEED }, vault = MemoryVault(), marketing = marketing) } }
        if (settle) waitForIdle()
    }

    /**
     * The demo scene opens on a table, which is the half of the claim a screen can answer.
     *
     * This is the one scene that is not a photograph. The App Store preview needs *footage of the
     * game being played*, and every other staged scene is deliberately a frozen moment — `TABLE`
     * deals a position and then waits for a human who, on a capture machine, is never coming. A
     * clip of it is a still with a soundtrack.
     *
     * **The moving half is asserted one layer down**, in [theDemoRoundPlaysItselfWithNobodyTouchingIt].
     * This test used to wait here for the header's turn counter to reach a second lap, and that is
     * where it went wrong: a lap is four seats' worth of choreography, and rendering it headless
     * cost ten seconds on a developer's machine and over a minute on a CI runner — where it failed,
     * and nowhere else. It failed as `UncompletedCoroutinesError` too, saying only that the body
     * had not finished, because its own wait was as long as the whole budget `runComposeUiTest`
     * allows. `FullGameUiTest` recorded the same lesson: a long wait is a bet on the runner.
     *
     * Nothing about "the round moves" is carried by the pixels, so it is claimed where it is cheap
     * and total — and a round that reaches its ending *on screen* is already `FullGameUiTest`'s.
     *
     * `settle = false` because this screen never goes idle by design: it is playing. The beat is
     * left at the one the app ships, which is what makes this cheap: the loop sleeps between moves,
     * so the dealt table is on screen and quiet long before the second one lands.
     */
    @Test
    fun theDemoSceneOpensOnATable() = runComposeUiTest {
        open(marketing = "demo", settle = false)

        // The rail's two piles, as `theTableSceneOpensOnARoundInProgress` reads them. Waited for
        // rather than asserted flat: nothing has settled, because nothing here ever will.
        waitUntil(timeoutMillis = DEMO_TIMEOUT_MS) {
            onAllNodesWithText("DRAW", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(onAllNodesWithText("DISCARD", substring = true).fetchSemanticsNodes().isNotEmpty())
    }

    /**
     * The demo round plays itself, with nobody touching it.
     *
     * The property worth asserting is the opposite of [theTableSceneIsTheSameTableTwice]: not that
     * the deal is fixed, but that the round MOVES. The turn counter is what says so — it can only
     * advance if seats are taking turns — and it is read off the session rather than off the header
     * because a header is a rendering of it, and rendering is the part that cost a minute.
     *
     * At the shipping beat, deliberately: [playOn]'s pauses are what a capture machine films, and a
     * loop that only worked with them taken out would be a loop the reel never sees. They are real
     * delays rather than work, so they cost the same on any machine — which is the whole reason
     * this one can carry a budget honestly.
     */
    @Test
    fun theDemoRoundPlaysItselfWithNobodyTouchingIt() = runBlocking {
        val game = demoGame(MemoryVault())
        val played = withTimeoutOrNull(LAP_TIMEOUT_MS) {
            val loop = launch { playOn(game) }
            while (game.session.view.value.turnNumber < DEMO_LAP) delay(POLL_MS)
            loop.cancel()
            true
        }
        assertTrue(
            played == true,
            "the demo table never reached lap $DEMO_LAP, so nothing played itself",
        )
    }

    private companion object {
        const val SEED = 20_260_903L

        /** Long enough for the opening deal to play out; short enough to fail rather than hang. */
        const val SHEET_TIMEOUT_MS = 10_000L

        /**
         * Two laps of the table, as the session counts them.
         *
         * It is a LAP counter, not a move counter — `CallVinto.kt` advances it only when play
         * comes back round to the first seat — so two means the table went all the way round with
         * nobody touching it, which is exactly the claim. Four would mean four laps, which is more
         * than a test wants to sit through.
         */
        const val DEMO_LAP = 2

        /** How often the lap above is looked for. Short enough not to pad the measurement. */
        const val POLL_MS = 5L

        /**
         * A lap at the demo's own beat, with room for a slow runner's thinking on top.
         *
         * The beats inside it are delays rather than work, so they take the same wall clock
         * anywhere; what a slower machine adds is the bots' search between them. Generous against
         * that, and still a failure rather than a hang if the loop stops.
         */
        const val LAP_TIMEOUT_MS = 45_000L

        /**
         * A wedge detector for the screen, not a pace — and deliberately under the budget around it.
         *
         * With the beats taken out the table is dealt in a moment, so anything approaching this
         * means the screen never arrived. It must stay well under the minute `runComposeUiTest`
         * allows the whole test body: at sixty it could never be the thing that failed, and the
         * report would say only that the body had not finished.
         */
        const val DEMO_TIMEOUT_MS = 20_000L
    }
}
