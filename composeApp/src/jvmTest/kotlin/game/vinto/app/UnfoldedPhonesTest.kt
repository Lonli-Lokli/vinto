package game.vinto.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import game.vinto.app.game.LOG_LINE
import game.vinto.app.game.TableLayout
import game.vinto.app.game.TableScreen
import game.vinto.app.game.TableState
import game.vinto.app.theme.VintoTheme
import game.vinto.client.Say
import game.vinto.client.tableFor
import game.vinto.client.teachingSession
import game.vinto.engine.PlayerView
import game.vinto.shapes.GameAction
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PositionPayload
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A phone that opens into a tablet: the iPhone Duo, the Galaxy Z Fold, the Pixel Fold.
 *
 * Asked for on 2026-10-12, "we must rethink our layout for folding phones including new iPhone
 * duo", and measured before anything was changed — the real app rendered headless at each open
 * screen. Three things were wrong, and none of them needed a hinge to show:
 *
 *  - **The log lay over the buttons.** A side rail between about 340 and 700 points tall counted
 *    as "tall", which centres the controls and pins the log to the foot, and nothing asked whether
 *    the two fit. On an open Duo the log's first line was drawn across SWAP CARDS.
 *  - **A nearly square screen was dealt the landscape table**, which caps the felt by its shape,
 *    and an open Galaxy Z Fold kept a band of empty surround above and below a table two thirds
 *    of the width it had.
 *  - **The side seats' names ran up and down the rim on a screen with room to spare.** The owner's
 *    words: "player name must be horizontal as we have plenty of space" — on unfolded phones,
 *    tablets and desktops alike, and on wide phones. A phone without that room keeps the turned
 *    name, which is what its width needs.
 */
@OptIn(ExperimentalTestApi::class)
class UnfoldedPhonesTest {

    @Test
    fun theLogNeverLiesOverTheChoices() {
        val (view, said) = drawn()
        OPEN.forEach { (screen, size) ->
            runDesktopComposeUiTest(WINDOW_W, WINDOW_H) {
                show(view, said, size.first, size.second)
                val choices = CHOICES.map { button(it) }
                val lines = onAllNodes(hasTestTag(LOG_LINE), useUnmergedTree = true).fetchSemanticsNodes()
                assertTrue(lines.isNotEmpty(), "on an open $screen the log says nothing")
                lines.forEach { line ->
                    choices.forEach { choice ->
                        assertFalse(
                            line.boundsInRoot.overlaps(choice),
                            "on an open $screen the log's line ${line.boundsInRoot} lies over the choice $choice",
                        )
                    }
                }
            }
        }
    }

    /**
     * Upright where that gives the cards the bigger table, on its side where it does not.
     *
     * An open Fold is about as wide as it is tall, and on its side the felt is held to a table's
     * shape and the rest is surround; upright, the rail takes its share under the felt and the
     * felt has the whole width above it. Everything that was already on its side stays there.
     */
    @Test
    fun aNearlySquareFoldIsDealtTheTableUpright() {
        SQUARE.forEach { (screen, size) ->
            assertFalse(
                TableLayout.forScreen(size.first, size.second).landscape,
                "an open $screen is dealt the table on its side",
            )
        }
        WIDE.forEach { (screen, size) ->
            assertTrue(
                TableLayout.forScreen(size.first, size.second).landscape,
                "a $screen is no longer dealt the table on its side",
            )
        }
    }

    /**
     * Across wherever the seat has room for it, along the rim where it has not.
     *
     * The owner, 2026-10-12, twice: "player name must be horizontal as we have plenty of space", of
     * open folds, tablets and desktops — and then "I was hoping that wide phones and landscape tablets
     * will display names of side players horizontally". So the line is room, not the kind of device:
     * a Plus or a Pro Max has it, a phone of 360 to 412 points and the closed Duo (382 of its 466, the
     * rest being iOS 27.1's status column) do not.
     */
    @Test
    fun theSideNamesReadAcrossWhereverTheSeatHasRoom() {
        val view = named(teachingSession().view.value)
        (ROOMY + PHONES).forEach { (screen, size) ->
            runDesktopComposeUiTest(WINDOW_W, WINDOW_H) {
                show(view, emptyList(), size.first, size.second)
                SIDES.forEach { who ->
                    val name = nameOf(who)
                    if (screen in ROOMY) {
                        assertTrue(
                            name.width > name.height,
                            "on a $screen $who's name does not read across: $name",
                        )
                    } else {
                        assertTrue(
                            name.height > name.width,
                            "on a $screen $who's name no longer runs along the rim: $name",
                        )
                    }
                }
            }
        }
    }

    private fun ComposeUiTest.nameOf(who: String): Rect =
        onAllNodes(hasText(who), useUnmergedTree = true).fetchSemanticsNodes().single().boundsInRoot

    private fun ComposeUiTest.button(label: String): Rect =
        onAllNodes(
            SemanticsMatcher("names $label") {
                it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(label) == true
            },
        ).fetchSemanticsNodes().single().boundsInRoot

    private fun ComposeUiTest.show(view: PlayerView, said: List<Say>, wide: Dp, high: Dp) {
        setContent {
            // The words a large screen draws, as `App` scales them.
            val base = LocalDensity.current
            val scaled = Density(base.density, base.fontScale * typeScaleFor(minOf(wide, high)))
            CompositionLocalProvider(LocalDensity provides scaled) {
                VintoTheme {
                    Box(modifier = Modifier.size(wide, high)) {
                        TableScreen(
                            state = TableState(view, tableFor(view), null, said, 1),
                            layout = TableLayout.forScreen(wide, high),
                            onMove = {},
                            onHelp = {},
                            onSettings = {},
                            onLeave = {},
                        )
                    }
                }
            }
        }
        waitForIdle()
    }

    /** A card drawn — swap or discard — with the round's opening in the log under it. */
    private fun drawn(): Pair<PlayerView, List<Say>> {
        lateinit var view: PlayerView
        lateinit var said: List<Say>
        runTest {
            val session = teachingSession()
            val me = session.playerId
            session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 0)))
            session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 1)))
            session.dispatch(GameAction.FinishSetup(PlayerIdPayload(me)))
            session.dispatch(GameAction.DrawCard(PlayerIdPayload(me)))
            view = session.view.value
            said = session.log.value
        }
        return view to said
    }

    private fun named(view: PlayerView): PlayerView = view.copy(
        players = view.players.mapIndexed { i, seat -> seat.copy(name = NAMES[i], nickname = NAMES[i]) },
    )

    private companion object {
        val CHOICES = listOf("Swap Cards", "Discard")
        val NAMES = listOf("Dusty Pebble", "Clever Harbour", "Golden Lantern", "Patient Sparrow")

        /** The next seat round sits on the left, the last on the right. */
        val SIDES = listOf("Clever Harbour", "Patient Sparrow")

        /**
         * Open screens, in points: the Duo's inner display from Apple's own screenshot size
         * (2853x2007 at 3x), the Fold 7's and the Pixel 9 Pro Fold's from their panels, and an
         * iPad mini on its side, which shares the Duo's rail and its fault.
         */
        val OPEN = mapOf(
            "iPhone Duo, on its side" to (951.dp to 669.dp),
            "iPhone Duo, upright" to (669.dp to 951.dp),
            "Galaxy Z Fold 7" to (832.dp to 750.dp),
            "Pixel 9 Pro Fold" to (923.dp to 891.dp),
            "iPad mini, on its side" to (1133.dp to 744.dp),
        )

        val SQUARE = mapOf(
            "Galaxy Z Fold 7" to (832.dp to 750.dp),
            "Pixel 9 Pro Fold" to (923.dp to 891.dp),
        )

        val WIDE = mapOf(
            "iPhone Duo, on its side" to (951.dp to 669.dp),
            "iPad mini, on its side" to (1133.dp to 744.dp),
            "Galaxy Tab S9, on its side" to (1280.dp to 752.dp),
            "iPad Pro 13, on its side" to (1376.dp to 988.dp),
            "desktop window" to (1920.dp to 1040.dp),
        )

        val ROOMY = mapOf(
            // The app's own area on the wide phones: the screen less its status bar and home indicator.
            "iPhone 17 Pro Max" to (440.dp to 860.dp),
            "iPhone 16 Plus" to (430.dp to 839.dp),
            "iPhone Duo, open on its side" to (951.dp to 669.dp),
            "iPhone Duo, open upright" to (669.dp to 951.dp),
            "Galaxy Z Fold 7, open" to (832.dp to 750.dp),
            "iPad Air 11" to (820.dp to 1180.dp),
            "Galaxy Tab S9, on its side" to (1280.dp to 752.dp),
            "desktop window" to (1920.dp to 1040.dp),
        )

        val PHONES = mapOf(
            // Measured on the Duo simulator (Xcode 27.1): the status column takes 84 of the 466 points.
            "iPhone Duo, closed" to (382.dp to 658.dp),
            "Pixel 7" to (412.dp to 805.dp),
            "iPhone 16" to (393.dp to 759.dp),
            "Galaxy S23" to (360.dp to 740.dp),
            "Galaxy Z Fold 7, closed" to (373.dp to 873.dp),
        )

        const val WINDOW_W = 2000
        const val WINDOW_H = 1300
    }
}
