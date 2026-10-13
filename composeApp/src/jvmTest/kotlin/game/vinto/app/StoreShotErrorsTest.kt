package game.vinto.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import game.vinto.app.game.COACH_TAG
import game.vinto.app.game.Chrome
import game.vinto.app.game.LocalChrome
import game.vinto.app.game.PLATE_TAG
import game.vinto.app.game.StandingsSheet
import game.vinto.app.game.TableLayout
import game.vinto.app.game.TableScreen
import game.vinto.app.game.TableState
import game.vinto.app.game.TeachScreen
import game.vinto.app.theme.LocalSheetSpan
import game.vinto.app.theme.SHEET_TAG
import game.vinto.app.theme.VintoTheme
import game.vinto.client.MemoryVault
import game.vinto.client.Pace
import game.vinto.client.RoundResult
import game.vinto.client.Settings
import game.vinto.client.ThemeChoice
import game.vinto.client.roundPoints
import game.vinto.client.saveSettings
import game.vinto.client.tableFor
import game.vinto.client.teachingSession
import kotlinx.coroutines.Dispatchers
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * What a review of the store screenshots found, 2026-10-12, each held here so it stays found.
 *
 * Every one of them is the app, not the pipeline — the shots are the app drawing itself — so a
 * phone showed the same thing:
 *
 *  1. **The closed iPhone Duo left a column empty.** iOS 27.1 stands its status bar and camera in
 *     a column down the right edge (84 of 466 points, measured on the Duo simulator) and the app
 *     padded the whole column away. Apple's own toolbars stack their buttons in it, under the
 *     status; the table's controls now stand there — the way out first, under the camera, then the
 *     rules and the settings — and the felt gets the header's row.
 *  2. **The coach cut a plate in two.** Talking, it lies at an end of the felt at a height of its
 *     own, and its edge ran through whatever was there: a sliver of a plate under "Go on" on a
 *     phone, half of Ember's plate and of the draw pile on a tablet.
 *  3. **A green screen sat between cream bars.** The app painted the rail behind the system bars
 *     on every screen, so the menu's felt stopped short of the clock and of the home indicator.
 *  4. **The score sheet cut the rail's words in half.** Centred on the window, on a table lying on
 *     its side it straddled the felt and the rail, and "others" and "ou throw away the 2" stood at
 *     its edge.
 *
 * And what the shots showed once those were fixed:
 *
 *  5. **The coach cut the side seats' cards.** It cleared plates and piles, and a sliver of each
 *     side seat's card still stood under its edge on a phone.
 *  6. **The closed Duo's column was two flat greens**, a step between them halfway down, beside a
 *     menu whose felt is a gradient.
 *  7. **The score sheet reached into the closed Duo's column**, its ✕ where iOS draws the clock.
 */
@OptIn(ExperimentalTestApi::class)
class StoreShotErrorsTest {

    // 1. The closed Duo's status column.

    @Test
    fun theClosedDuosHeaderStandsInTheStatusColumn() = runDesktopComposeUiTest(DUO_W, DUO_H) {
        val view = teachingSession().view.value
        // The column as iOS reports it, its clock and camera in its top 150 points.
        val layout = TableLayout.forScreen(DUO_W.dp, DUO_APP_H.dp, Duos.CLOSED)
        setContent {
            VintoTheme {
                Box(modifier = Modifier.size(DUO_W.dp, DUO_APP_H.dp)) {
                    TableScreen(
                        state = TableState(view, tableFor(view), null, emptyList(), 1),
                        layout = layout,
                        onMove = {},
                        onHelp = {},
                        onSettings = {},
                        onLeave = {},
                    )
                }
            }
        }
        waitForIdle()

        val app = (DUO_W - COLUMN).toFloat()
        // In the owner's order, top down: the way out, the rules, the settings.
        val controls = listOf("Leave the table", "Rules", "Settings").map { name ->
            val control = described(name)
            assertTrue(control.left >= app - EDGE, "\"$name\" is not in the status column: $control")
            assertTrue(control.top >= STATUS_RESERVE, "\"$name\" is up among the status and the camera: $control")
            control
        }
        controls.zipWithNext().forEach { (above, below) ->
            assertTrue(above.bottom <= below.top, "the column's controls are out of order: $controls")
        }
        // No header row: the seat opposite starts at the top of the felt.
        assertTrue(described("Tide, card 1").top < NO_HEADER, "the felt still sits under a header row")
        // And nothing of the table stands in the column.
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription))
            .fetchSemanticsNodes()
            .filter { it.said().contains(", card ") }
            .forEach { assertTrue(it.boundsInRoot.right <= app + EDGE, "${it.said()} lies in the status column") }
    }

    // A felt screen's column is the felt, continuing the screen beside it rather than two flat halves.
    @Test
    fun theClosedDuosStatusColumnContinuesTheFeltBesideIt() = runDesktopComposeUiTest(DUO_W, DUO_H) {
        setContent {
            App(
                seeds = { SEED },
                vault = MemoryVault().apply { saveSettings(Settings(theme = ThemeChoice.LIGHT)) },
                safeArea = PaddingValues(bottom = DUO_HOME.dp, end = COLUMN.dp),
            )
        }
        waitForIdle()
        val pixels = onRoot().captureToImage().toPixelMap()
        val column = DUO_W - COLUMN / 2
        val beside = DUO_W - COLUMN - BESIDE
        listOf(BESIDE * 10, DUO_APP_H / 2 - BESIDE, DUO_APP_H / 2 + BESIDE, DUO_APP_H - BESIDE * 10).forEach { y ->
            assertNear(
                pixels[column, y],
                pixels[beside, y],
                "on home the status column at $y is not the felt beside it",
            )
        }
    }

    // And a sheet over the table keeps out of it: its top is the clock's and the camera's.
    @Test
    fun onAClosedDuoASheetKeepsClearOfTheStatusColumn() = runDesktopComposeUiTest(DUO_W, DUO_APP_H) {
        setContent {
            VintoTheme {
                CompositionLocalProvider(LocalStatusColumn provides PaddingValues.Absolute(right = COLUMN.dp)) {
                    Box(modifier = Modifier.size(DUO_W.dp, DUO_APP_H.dp)) { Standings() }
                }
            }
        }
        mainClock.advanceTimeBy(SHEET_MS)
        waitForIdle()
        val sheet = onAllNodes(hasTestTag(SHEET_TAG), useUnmergedTree = true).fetchSemanticsNodes().single()
        val app = (DUO_W - COLUMN).toFloat()
        assertTrue(sheet.boundsInRoot.right <= app + EDGE, "the sheet ${sheet.boundsInRoot} reaches into the column")
    }

    // 2. The coach never cuts a plate, a card or a pile in two.

    @Test
    fun theCoachCoversWhollyOrNotAtAll() {
        SCREENS.forEach { (screen, size) -> coachOn(screen, size.first, size.second, Chrome.NONE) }
    }

    /**
     * And on every shape the iPhone Duo takes, where the felt is not where it is on any other phone: a
     * status column down one edge, closed, open and turned on either side; a strip across the top; a
     * fold down the middle or across it, which the coach may not lie on either.
     */
    @Test
    fun theCoachCoversWhollyOrNotAtAllOnTheDuo() {
        DUO_SCREENS.forEach { (screen, shape) -> coachOn(screen, shape.wide, shape.high, shape.chrome) }
    }

    private fun coachOn(screen: String, wide: Dp, high: Dp, chrome: Chrome) =
        runDesktopComposeUiTest(WINDOW_W, WINDOW_H) {
            setContent {
                VintoTheme {
                    CompositionLocalProvider(LocalChrome provides chrome) {
                        Box(modifier = Modifier.size(wide, high)) {
                            TeachScreen(
                                botDispatcher = Dispatchers.Default,
                                pace = Pace.STEADY,
                                onSettings = {},
                                onDone = {},
                            )
                        }
                    }
                }
            }
            waitForIdle()
            val coach = onAllNodes(hasTestTag(COACH_TAG), useUnmergedTree = true).fetchSemanticsNodes()
            assertTrue(coach.isNotEmpty(), "on a $screen the coach is not talking")
            val panel = coach.first().boundsInRoot
            val things = onAllNodes(
                SemanticsMatcher("a plate") { it.tag().startsWith(PLATE_TAG) },
                useUnmergedTree = true,
            )
                .fetchSemanticsNodes()
                .map { it.tag() to it.boundsInRoot } +
                // The seats' cards too: on a phone it was a sliver of each side seat's card
                // that stood under the coach's edge.
                onAllNodes(SemanticsMatcher("a card") { it.said().contains(", card ") })
                    .fetchSemanticsNodes()
                    .map { it.said() to it.boundsInRoot } +
                listOfNotNull(deck()?.let { "the deck" to it })
            things.forEach { (what, r) ->
                val cut = r.overlaps(panel) && !panel.holds(r)
                // Everything, so a failure says what the coach had to find its way between.
                assertTrue(!cut, "on a $screen the coach $panel cuts $what $r in two, among $things")
            }
            chrome.division?.let { fold ->
                val crease = Rect(fold.left.value, fold.top.value, fold.right.value, fold.bottom.value)
                assertTrue(!panel.overlaps(crease), "on a $screen the coach $panel lies on the crease $crease")
            }
        }

    // 3. A felt screen is felt behind the bars too.

    @Test
    fun whatIsBehindTheBarsContinuesTheScreenBesideIt() {
        listOf<String?>(null, "lobby", "table").forEach { scene ->
            runDesktopComposeUiTest(PHONE_W, PHONE_H) {
                setContent {
                    App(
                        seeds = { SEED },
                        vault = MemoryVault().apply { saveSettings(Settings(theme = ThemeChoice.LIGHT)) },
                        marketing = scene,
                        safeArea = PaddingValues(top = BAR_TOP.dp, bottom = BAR_BOTTOM.dp),
                    )
                }
                waitForIdle()
                val pixels = onRoot().captureToImage().toPixelMap()
                val x = PHONE_W / 2
                val what = scene ?: "home"
                assertNear(
                    pixels[x, 2],
                    pixels[x, BAR_TOP + 2],
                    "on $what the status bar's strip is not the screen below it",
                )
                assertNear(
                    pixels[x, PHONE_H - 2],
                    pixels[x, PHONE_H - BAR_BOTTOM - 2],
                    "on $what the home indicator's strip is not the screen above it",
                )
            }
        }
    }

    // 4. The score sheet rises over the felt, not across the rail.

    @Test
    fun onATableOnItsSideTheScoreSheetRisesOverTheFelt() = runDesktopComposeUiTest(OPEN_W, OPEN_H) {
        val layout = TableLayout.forScreen(OPEN_W.dp, OPEN_H.dp)
        val span = assertNotNull(layout.feltSpan(), "a table on its side has a felt to centre on")
        setContent {
            VintoTheme {
                CompositionLocalProvider(LocalSheetSpan provides span) {
                    Box(modifier = Modifier.size(OPEN_W.dp, OPEN_H.dp)) { Standings() }
                }
            }
        }
        mainClock.advanceTimeBy(SHEET_MS)
        waitForIdle()
        val sheet = onAllNodes(
            hasTestTag(SHEET_TAG),
            useUnmergedTree = true,
        ).fetchSemanticsNodes().single().boundsInRoot
        val left = span.left.value
        val right = (span.left + span.width).value
        assertTrue(
            sheet.left >= left - EDGE && sheet.right <= right + EDGE,
            "the score sheet $sheet is not over the felt ($left..$right): it reaches across the rail",
        )
    }

    @Test
    fun anUprightTableHasNoFeltToCentreOn() {
        assertTrue(TableLayout.forScreen(PHONE_W.dp, PHONE_H.dp).feltSpan() == null)
    }

    /** A round's score sheet, open: the Vinto call held, 5 against 16. */
    @Composable
    private fun Standings() {
        val hands = mapOf("p1" to 5, "p2" to 34, "p3" to 16, "p4" to 23)
        val seats = listOf("p1" to "You", "p2" to "Ember", "p3" to "Tide", "p4" to "Dune")
        StandingsSheet(
            open = true,
            round = 1,
            you = "p1",
            result = RoundResult(callerId = "p1", hands = hands, points = roundPoints(hands, "p1"), seats = seats),
            standings = emptyMap(),
            onNextRound = {},
            onQuit = {},
        )
    }

    private fun ComposeUiTest.described(label: String): Rect =
        onAllNodes(SemanticsMatcher("names $label") { node -> node.said().startsWith(label) })
            .fetchSemanticsNodes()
            .first()
            .boundsInRoot

    private fun ComposeUiTest.deck(): Rect? =
        onAllNodes(SemanticsMatcher("the deck") { node -> node.said().startsWith("Cards left in the deck") })
            .fetchSemanticsNodes()
            .firstOrNull()
            ?.boundsInRoot

    private fun Rect.holds(other: Rect): Boolean =
        left <= other.left && top <= other.top && right >= other.right && bottom >= other.bottom

    private fun SemanticsNode.said(): String =
        config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull().orEmpty()

    private fun SemanticsNode.tag(): String = config.getOrNull(SemanticsProperties.TestTag).orEmpty()

    private fun assertNear(a: Color, b: Color, message: String) {
        val far = abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue)
        assertTrue(far < NEAR, "$message: $a against $b")
    }

    private companion object {
        /** The closed iPhone Duo, measured: 466 across, its status column 84, its home indicator 20. */
        const val DUO_W = 466
        const val DUO_H = 678
        const val DUO_APP_H = 658
        const val COLUMN = 84
        const val DUO_HOME = 20

        /** How far beside the column to read the screen, and the step its heights are read in. */
        const val BESIDE = 4

        /** Where iOS's own clock, connectivity and camera end in the column, and the app's part begins. */
        const val STATUS_RESERVE = Duos.CAMERA_FOOT
        const val NO_HEADER = 30f

        const val PHONE_W = 430
        const val PHONE_H = 932
        const val BAR_TOP = 59
        const val BAR_BOTTOM = 34

        const val OPEN_W = 951
        const val OPEN_H = 669

        const val SEED = 20_260_903L
        const val EDGE = 1f
        const val NEAR = 0.03f
        const val SHEET_MS = 2_000L

        val SCREENS: Map<String, Pair<Dp, Dp>> = mapOf(
            "iPhone 6.9\"" to (430.dp to 839.dp),
            "closed iPhone Duo" to (382.dp to 658.dp),
            "Pixel" to (411.dp to 731.dp),
            "iPad 13\"" to (1032.dp to 1376.dp),
            "Play tablet" to (1280.dp to 720.dp),
        )
        const val WINDOW_W = 1400
        const val WINDOW_H = 1400

        val DUO_SCREENS: Map<String, DuoShape> = mapOf(
            "closed iPhone Duo, its column" to DuoShape(Duos.CLOSED_W.dp, Duos.CLOSED_H.dp, Duos.CLOSED),
            "open iPhone Duo on its side" to DuoShape(Duos.OPEN_W.dp, Duos.OPEN_H.dp, Duos.OPEN),
            "open iPhone Duo upright" to DuoShape(Duos.UPRIGHT_W.dp, Duos.UPRIGHT_H.dp, Duos.UPRIGHT),
            "iPhone Duo half open like a book" to DuoShape(Duos.OPEN_W.dp, Duos.OPEN_H.dp, Duos.BOOK),
            "iPhone Duo half open like a laptop" to DuoShape(Duos.UPRIGHT_W.dp, Duos.UPRIGHT_H.dp, Duos.LAPTOP),
            "closed iPhone Duo turned, its column on the right" to
                DuoShape(Duos.TURNED_W.dp, Duos.TURNED_H.dp, Duos.turnedColumn(onRight = true)),
            "closed iPhone Duo turned, its column on the left" to
                DuoShape(Duos.TURNED_W.dp, Duos.TURNED_H.dp, Duos.turnedColumn(onRight = false)),
        )
    }

    /** A screen the table is laid out on, and what the system keeps of it. */
    private class DuoShape(val wide: Dp, val high: Dp, val chrome: Chrome)
}
