package game.vinto.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import game.vinto.app.theme.VintoTheme
import game.vinto.client.MemoryVault
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Every control on the way into a room can be hit, including at twice the font size.
 *
 * `TouchTargetTest` sweeps the *table* and always has. Nothing swept the lobby, so the three
 * screens between the menu and a seat — the ones every online player passes through before
 * they have seen a card — had never been measured at all. That gap is not hypothetical: the
 * same sweep over the table found rank chips growing sideways and not down at `fontScale = 2`,
 * which is a real control a real person could not press.
 *
 * The new tiles and fields are the ones at risk here. A tile carries a title *and* a sentence,
 * so it grows in the direction that has least room; a code cell is one of six across a phone's
 * width, so it is the narrowest thing in the app by construction.
 *
 * Measured from `node.size`, which is the unclipped semantics size. `boundsInRoot` is clipped
 * to what is on screen, so a control scrolled below the fold measures as nothing and passes —
 * a trap this repository has recorded hitting twice.
 */
@OptIn(ExperimentalTestApi::class)
class LobbyReachTest {

    @Test
    fun everyControlOnTheWayInCanBeHit() =
        eachLobbyControl(fontScale = 1f) { where, what, tap ->
            assertTrue(tap.bigEnough, "$where: ${tooSmall(what, tap)}")
        }

    /**
     * And still can when the phone's font is doubled.
     *
     * The setting that turns a considered layout into an unusable one, and the one nobody
     * developing has switched on. A tile's sentence wraps to three lines at this size, which is
     * the growth that pushes its neighbours off a screen.
     */
    @Test
    fun everyControlSurvivesALargeFont() =
        eachLobbyControl(fontScale = 2f) { where, what, tap ->
            assertTrue(tap.bigEnough, "$where at twice the font: ${tooSmall(what, tap)}")
        }

    // ------------------------------------------------------------------ the sweep

    /**
     * Walks the three screens and hands every tappable thing on each to [check].
     *
     * Through the real `App` rather than by composing the screens directly, because the thing
     * being measured is what a player's thumb meets — and that includes the scaffold's padding,
     * the column's width cap and the order things end up in.
     */
    private fun eachLobbyControl(fontScale: Float, check: (String, String, Tap) -> Unit) =
        runComposeUiTest {
            setContent {
                val density = LocalDensity.current.density
                CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                    VintoTheme {
                        Box(modifier = Modifier.size(PhoneW, PhoneH)) {
                            App(seeds = { SEED }, vault = MemoryVault())
                        }
                    }
                }
            }
            waitForIdle()
            press("Play online")

            sweep("the front door", check)

            listOf("Open a room", "Join with a code").forEach { tile ->
                press(tile)
                sweep(tile, check)
                press("Back")
            }
        }

    private fun ComposeUiTest.sweep(where: String, check: (String, String, Tap) -> Unit) {
        val targets = tapTargets()
        assertTrue(targets.isNotEmpty(), "$where had nothing to tap at all")
        targets.forEach { (what, tap) -> check(where, what, tap) }
    }

    private fun ComposeUiTest.press(label: String) {
        val node = onNodeWithContentDescription(label)
        if (!node.isDisplayed()) node.performScrollTo()
        node.performClick()
        waitForIdle()
    }

    private fun tooSmall(what: String, tap: Tap) =
        "$what is ${tap.size.width}x${tap.size.height}dp, under a ${Tap44.toInt()}dp thumb"

    private data class Tap(val size: IntSize, val at: Offset) {
        val bigEnough: Boolean get() = size.width >= Tap44 && size.height >= Tap44
    }

    private fun ComposeUiTest.tapTargets(): List<Pair<String, Tap>> =
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))
            .fetchSemanticsNodes()
            .map { node ->
                val name = node.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()
                    ?: node.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text
                    ?: "an unnamed control"
                name to Tap(node.size, node.positionInRoot)
            }

    private companion object {
        const val Tap44 = 44f
        const val SEED = 20_260_819L
        val PhoneW = 411.dp
        val PhoneH = 740.dp
    }
}
