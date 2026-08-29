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
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import game.vinto.app.game.TableLayout
import game.vinto.app.game.TableScreen
import game.vinto.app.game.TableState
import game.vinto.app.theme.VintoTheme
import game.vinto.client.Question
import game.vinto.client.tableFor
import game.vinto.client.teachingSession
import game.vinto.engine.PlayerView
import game.vinto.shapes.GameAction
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PositionPayload
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Everything a thumb has to hit, measured.
 *
 * WCAG 2.2 asks for 24x24 at AA (SC 2.5.8) and 44x44 at AAA (SC 2.5.5); Material asks for
 * 48dp and Apple for 44pt. This holds the app to **44dp in both directions**, which clears
 * the enhanced level and both platform guidelines, because a card game is played by tapping
 * small things quickly and the difference between 24 and 44 is the difference between playing
 * and aiming.
 *
 * It reads the sizes out of the composition rather than trusting the modifiers, which is how
 * it caught the fourteen rank chips: a `heightIn` had been set and no `widthIn`, so a chip
 * offering "2" was 44dp tall and 27dp wide — legal at AA, and far too small to hit while
 * three bots wait.
 *
 * What it reads is each node's **own laid-out size**, not its bounds on screen. Those are the
 * same number until something scrolls, and then they stop being: `boundsInRoot` is clipped by
 * every ancestor, so a control half under the edge of the screen reports half its height and
 * one scrolled fully past it reports nothing at all. Measured that way the rail's rank chips
 * came back "51x28dp" at a doubled system font — a size no chip has ever had, and a report
 * that sent a reader looking for a missing `heightIn` that was there and working. Whether a
 * control can be *reached* is a real question and a different one, and it is asked below by
 * scrolling to it.
 */
@OptIn(ExperimentalTestApi::class)
class TouchTargetTest {

    @Test
    fun everythingOnATurnCanBeHit() = eachTapTarget(Question.None) { what, tap ->
        assertTrue(tap.bigEnough, tooSmall(what, tap))
    }

    /** The worst case: fourteen ranks, a confirm and a cancel, all in one rail. */
    @Test
    fun everyRankChipCanBeHit() = eachTapTarget(Question.CallRank(0)) { what, tap ->
        assertTrue(tap.bigEnough, tooSmall(what, tap))
    }

    /**
     * And again with the system font at twice its size, which is a real accessibility
     * setting and the one that finds targets sized by their text: a control that is 44dp
     * because its label happens to be is a control that was never 44dp at all. Nothing here
     * may *shrink* under a large font — growing is fine, and rows are allowed to wrap.
     */
    @Test
    fun everythingSurvivesALargeFont() = eachTapTarget(Question.None, fontScale = 2f) { what, tap ->
        assertTrue(tap.bigEnough, tooSmall(what, tap))
    }

    @Test
    fun everyRankChipSurvivesALargeFont() =
        eachTapTarget(Question.CallRank(0), fontScale = 2f) { what, tap ->
            assertTrue(tap.bigEnough, tooSmall(what, tap))
        }

    /**
     * And they are plaques rather than pills: wider than they are tall, and all one size.
     *
     * Left to size themselves the ranks came out narrow — a "2" is one character wide, and
     * only the height was being held — so a King's fourteen answers read as a row of tally
     * marks. Sharing the row evenly is also what keeps "JOKER" from wrapping and making its
     * row taller than the other.
     */
    @Test
    fun theRankChipsAreAllTheSameSizeAndWiderThanTall() = runComposeUiTest {
        val view = drawn()
        setContent {
            VintoTheme {
                Box(modifier = Modifier.size(PHONE_W, PHONE_H)) {
                    TableScreen(
                        state = TableState(view, tableFor(view, Question.CallRank(0)), null, emptyList(), 1),
                        layout = TableLayout.forScreen(PHONE_H),
                        onMove = {},
                        onHelp = {},
                        onReport = {},
                        onDeck = {},
                    )
                }
            }
        }
        waitForIdle()

        val ranks = tapTargets().filter { (what, _) -> what in RANKS }
        assertTrue(ranks.size == RANKS.size, "all fourteen ranks: ${ranks.map { it.first }}")

        val widths = ranks.map { it.second.size.width }
        val heights = ranks.map { it.second.size.height }.toSet()

        // A point either way, which is a row of seven divided into a width that is not a
        // multiple of seven, and not something a player can see.
        assertTrue(
            widths.max() - widths.min() <= 1f,
            "every rank is the same width: ${widths.toSet()}",
        )
        assertTrue(heights.size == 1, "and the same height, so no label wraps: $heights")
        assertTrue(
            widths.min() > heights.first(),
            "a plaque is wider than it is tall: ${widths.min()} x ${heights.first()}",
        )
    }

    /**
     * Being big enough is not the same as being on screen, so this asks the other half.
     *
     * At a doubled system font a King's fourteen chips and the two buttons under them come to
     * some five hundred points of rail, and the rail is a fixed share of the screen — about
     * two hundred and forty. Everything below the first row of chips is therefore *below the
     * screen*, and the only reason that is not a control nobody can press is that the rail
     * scrolls. `ControlPanel` says so in as many words — "so that a large system font cannot
     * push a button off the bottom of the rail" — and until this case nothing checked it.
     *
     * Truncating the prompt or shrinking the chips to make it all fit would trade this for a
     * worse failure: text that cannot be read at the size the player asked for, or a target
     * back under a thumb. Reflowing into a scroll is the right answer; reaching every control
     * is what makes it the right answer, so that is what is measured.
     */
    @Test
    fun everyRankChipCanBeReachedAtALargeFont() = runComposeUiTest {
        val view = drawn()
        setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 2f)) {
                VintoTheme {
                    Box(modifier = Modifier.size(PHONE_W, PHONE_H)) {
                        TableScreen(
                            state = TableState(
                                view,
                                tableFor(view, Question.CallRank(0)),
                                null,
                                emptyList(),
                                1,
                            ),
                            layout = TableLayout.forScreen(PHONE_H),
                            onMove = {},
                            onHelp = {},
                            onReport = {},
                            onDeck = {},
                        )
                    }
                }
            }
        }
        waitForIdle()

        // The chips, and then the two buttons that answer with them — those sit lowest, so a
        // rail that scrolls far enough for "Joker" has not necessarily scrolled far enough to
        // confirm.
        (RANKS + ANSWERS).forEach { label ->
            onNode(hasClickAction() and hasContentDescription(label))
                .performScrollTo()
                .assertIsDisplayed()
        }
    }

    /** And the header, whose controls are the smallest things drawn. */
    @Test
    fun theHeaderControlsCanBeHit() = eachTapTarget(Question.None, header = true) { what, tap ->
        assertTrue(tap.bigEnough, tooSmall(what, tap))
    }

    private fun tooSmall(what: String, tap: Tap) =
        "$what is ${tap.size.width}x${tap.size.height}dp, under a ${TAP.toInt()}dp thumb"

    /**
     * Renders the table and hands every tappable thing on it to [check].
     *
     * @param header when true, looks only at the strip above the felt, whose controls are
     *   otherwise lost among forty cards.
     */
    private fun eachTapTarget(
        question: Question,
        header: Boolean = false,
        fontScale: Float = 1f,
        check: (String, Tap) -> Unit,
    ) = runComposeUiTest {
        val view = drawn()
        setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                VintoTheme {
                    Box(modifier = Modifier.size(PHONE_W, PHONE_H)) {
                        TableScreen(
                            state = TableState(view, tableFor(view, question), null, emptyList(), 1),
                            layout = TableLayout.forScreen(PHONE_H),
                            onMove = {},
                            onHelp = {},
                            onReport = {},
                            onDeck = {},
                        )
                    }
                }
            }
        }
        waitForIdle()

        val targets = tapTargets().filter { (_, tap) ->
            if (header) tap.at.y < HEADER else tap.at.y >= HEADER
        }
        assertTrue(targets.isNotEmpty(), "nothing to tap at all")
        targets.forEach { (what, tap) -> check(what, tap) }
    }

    /**
     * A table with a card drawn and waiting to be placed.
     *
     * Not the dealt table: a question like [Question.CallRank] is answered against a *pending*
     * card, and asking it of a table that has none quietly produces the ordinary turn instead.
     * This case measured that turn for a while and reported that all fourteen rank chips were
     * the right size, there being none.
     */
    private fun drawn(): PlayerView {
        lateinit var view: PlayerView
        runTest {
            val session = teachingSession()
            val me = session.playerId
            session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 0)))
            session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 1)))
            session.dispatch(GameAction.FinishSetup(PlayerIdPayload(me)))
            session.dispatch(GameAction.DrawCard(PlayerIdPayload(me)))
            view = session.view.value
        }
        return view
    }

    /**
     * A tappable thing: how big it is, and where the layout put it.
     *
     * [size] is the node's own size and is what "big enough for a thumb" is asked of. [at] is
     * its unclipped position, used only to tell the header strip from the felt — unclipped
     * because a scrolled control's clipped bounds collapse to the origin, which would file it
     * under the header.
     */
    private data class Tap(val size: IntSize, val at: Offset) {
        val bigEnough: Boolean get() = size.width >= TAP && size.height >= TAP
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
        const val TAP = 44f
        val RANKS = listOf(
            "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K", "A", "Joker",
        )

        /** The two buttons under a rank grid: the last things the rail has room for. */
        val ANSWERS = listOf("Just Swap", "Back")
        const val HEADER = 44f
        val PHONE_W = 411.dp
        val PHONE_H = 740.dp
    }
}
