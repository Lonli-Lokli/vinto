package game.vinto.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import game.vinto.app.game.Chrome
import game.vinto.app.game.ColumnRun
import game.vinto.app.game.FoldPages
import game.vinto.app.game.Posture
import game.vinto.app.game.Sides
import game.vinto.app.game.TableLayout
import game.vinto.app.game.chromeOf
import game.vinto.app.game.sidesOf
import game.vinto.client.MemoryVault
import game.vinto.client.Settings
import game.vinto.client.ThemeChoice
import game.vinto.client.saveSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What the iPhone Duo keeps of its screen, and what the table is told of it ([chromeOf]).
 *
 * Measured on the Duo simulator (Xcode 27.1, 2026-10-12): closed, 466x678 points with a status column
 * 84 wide down the right, its clock and camera in the column's top 150; open on its side, 951x669 with
 * the same column and only a clock, in its top 100; open upright, an 80-point strip across the top with
 * the clock in its corner; half open, a fold down the middle. The table stands its own controls where
 * the system leaves it room, and keeps clear of everything else.
 */
class TheDuosEdgesTest {

    @Test
    fun theClosedDuosColumnIsLeftToTheTableAndPaddedForEveryOtherScreen() {
        val table = chromeOf(CLOSED_INSETS, CLOSED_RESERVED, table = true)
        assertEquals(84.dp, table.chrome.column)
        assertTrue(table.chrome.columnOnRight, "the column is on the right of the glass")
        assertEquals(Sides(bottom = 20.dp), table.padding, "a table keeps clear of the home indicator alone")
        // The box starts at the window's corner, so the clock and camera are where iOS said.
        assertEquals(CLOSED_RESERVED.occlusions, table.chrome.occlusions)

        val menu = chromeOf(CLOSED_INSETS, CLOSED_RESERVED, table = false)
        assertEquals(CLOSED_INSETS, menu.padding, "every other screen keeps clear of the column")
        assertEquals(Chrome.NONE, menu.chrome)
    }

    @Test
    fun theColumnsControlsStartUnderTheCameraAndStopAboveTheFoot() {
        val chrome = chromeOf(CLOSED_INSETS, CLOSED_RESERVED, table = true).chrome
        assertEquals(ColumnRun(84.dp, onRight = true, top = 158.dp, bottom = 650.dp), chrome.run(466.dp, 658.dp))

        // Open on its side there is only the clock, and the run starts under it.
        assertEquals(108.dp, OPEN_CHROME.run(951.dp, 649.dp)?.top)

        // Reported nothing at all, the controls start where a clock and a camera would have ended.
        assertEquals(160.dp, Chrome(column = 84.dp).run(466.dp, 658.dp)?.top)
        assertNull(Chrome.NONE.run(466.dp, 658.dp), "no column, no run")
    }

    @Test
    fun aRightToLeftScreenStillFindsTheColumnOnTheRight() {
        val insets = WindowInsets(right = 84.dp, bottom = 20.dp).asPaddingValues(Density(1f))
        // What the column used to be told from: on a right-to-left screen the end is the left.
        assertEquals(0.dp, insets.calculateEndPadding(LayoutDirection.Rtl))

        val sides = sidesOf(insets, LayoutDirection.Rtl)
        assertEquals(CLOSED_INSETS, sides)
        val framed = chromeOf(sides, CLOSED_RESERVED, table = true)
        assertEquals(84.dp, framed.chrome.column)
        assertTrue(framed.chrome.columnOnRight)
    }

    @Test
    fun aWideLeftInsetIsAColumnOnlyWithSomethingOfTheSystemsInIt() {
        val left = Sides(left = 84.dp, bottom = 20.dp)
        val cutOut = chromeOf(left, Reserved.NONE, table = true)
        assertEquals(0.dp, cutOut.chrome.column, "nothing reported in it: a cut-out, kept clear of")
        assertEquals(left, cutOut.padding)

        val clock = Reserved(occlusions = listOf(DpRect(10.dp, 10.dp, 74.dp, 50.dp)))
        val column = chromeOf(left, clock, table = true)
        assertEquals(84.dp, column.chrome.column)
        assertTrue(!column.chrome.columnOnRight, "the column is on the left")
        assertEquals(Sides(bottom = 20.dp), column.padding)
    }

    @Test
    fun theOpenDuoUprightSharesItsStripBesideTheClockAndNoOtherPhoneDoes() {
        val open = chromeOf(Sides(top = 80.dp, bottom = 20.dp), OPEN_UPRIGHT_CLOCK, table = true)
        assertEquals(80.dp, open.chrome.strip)
        assertEquals(560.dp, open.chrome.stripEnd, "the header ends where the clock begins")
        assertEquals(0.dp, open.padding.top, "the table stands its header in the strip")
        assertEquals(80.dp, TableLayout.forScreen(669.dp, 931.dp, open.chrome).header)

        // An iPhone's island stands in the middle of its strip, and leaves no room for a header.
        val island = Reserved(occlusions = listOf(DpRect(138.dp, 11.dp, 264.dp, 48.dp)))
        val iPhone = chromeOf(Sides(top = 59.dp, bottom = 34.dp), island, table = true)
        assertEquals(0.dp, iPhone.chrome.strip)
        assertEquals(59.dp, iPhone.padding.top)

        // And a strip nobody has said anything about is never shared: before iOS 27.1 nothing is.
        val silent = chromeOf(Sides(top = 80.dp, bottom = 20.dp), Reserved.NONE, table = true)
        assertEquals(0.dp, silent.chrome.strip)
        assertEquals(80.dp, silent.padding.top)
    }

    @Test
    fun whatTheSystemKeepsArrivesInTheTablesOwnCoordinates() {
        val reserved = Reserved(occlusions = listOf(DpRect(600.dp, 30.dp, 640.dp, 60.dp)), division = FOLD)
        val framed = chromeOf(Sides(left = 10.dp, top = 24.dp, bottom = 20.dp), reserved, table = true)
        assertEquals(listOf(DpRect(590.dp, 6.dp, 630.dp, 36.dp)), framed.chrome.occlusions)
        assertEquals(DpRect(461.5.dp, -24.dp, 469.5.dp, 645.dp), framed.chrome.division)
    }

    @Test
    fun aFoldDividesTheBoxIntoTwoPages() {
        val book = Chrome(division = FOLD)
        assertEquals(Posture.BOOK, book.folded)
        assertEquals(
            FoldPages(DpRect(0.dp, 0.dp, 471.5.dp, 669.dp), DpRect(479.5.dp, 0.dp, 951.dp, 669.dp)),
            book.pages(951.dp, 669.dp),
        )

        // A hinge across the screen is a laptop's, and a line thinner than a crease is widened to one.
        val laptop = Chrome(division = DpRect(0.dp, 475.dp, 669.dp, 477.dp))
        assertEquals(Posture.LAPTOP, laptop.folded)
        assertEquals(
            FoldPages(DpRect(0.dp, 0.dp, 669.dp, 472.dp), DpRect(0.dp, 480.dp, 669.dp, 951.dp)),
            laptop.pages(669.dp, 951.dp),
        )
        assertNull(Chrome.NONE.pages(951.dp, 669.dp), "flat, nothing divides the screen")
    }

    /**
     * Open upright, the whole app: the header stands in the strip iOS keeps across the top, its
     * wordmark and counter at the left and its controls after them, the way out last and short of the
     * clock in the strip's corner — and the felt starts right under the strip rather than under a
     * second row of chrome. What iOS reports is handed in as it is on the phone ([LocalReserved]), with
     * the edges it keeps ([App]'s `safeArea`).
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun openUprightTheHeaderStandsInTheStripShortOfTheClock() =
        runDesktopComposeUiTest(Duos.UPRIGHT_W, Duos.UPRIGHT_H + HOME) {
            setContent {
                CompositionLocalProvider(
                    LocalReserved provides mutableStateOf(OPEN_UPRIGHT_CLOCK),
                    LocalHost provides Host.PHONE,
                ) {
                    App(
                        seeds = { SEED },
                        vault = MemoryVault().apply { saveSettings(Settings(theme = ThemeChoice.LIGHT)) },
                        marketing = "table",
                        safeArea = PaddingValues(top = Duos.STRIP.dp, bottom = HOME.dp),
                    )
                }
            }
            // The staged round deals before it settles, and its cards land a moment after the screen.
            waitUntil(timeoutMillis = TABLE_MS) { nodesNamed(LEAVE).isNotEmpty() }
            waitForIdle()

            val clock = OPEN_UPRIGHT_CLOCK.occlusions.single()
            val header = listOf(RULES, SETTINGS, LEAVE).map { whereIs(it) }
            header.forEach { at ->
                assertTrue(at.top >= 0f && at.bottom <= Duos.STRIP, "the header's $at is not in the strip")
            }
            header.zipWithNext().forEach { (before, after) ->
                assertTrue(before.right <= after.left, "the way out is not the last control: $header")
            }
            assertTrue(header.last().right < clock.left.value, "the way out ${header.last()} runs under the clock")

            val cards = onAllNodes(SemanticsMatcher("a seat's card") { it.calledAloud().contains(", card ") })
                .fetchSemanticsNodes()
                .map { it.boundsInRoot }
            assertTrue(cards.isNotEmpty(), "no table was dealt")
            val top = cards.minOf { it.top }
            assertTrue(
                top >= Duos.STRIP && top <= Duos.STRIP + UNDER_THE_STRIP,
                "the seat opposite starts at $top, not just under the ${Duos.STRIP}-point strip",
            )
        }

    @Test
    fun theClosedDuoIsDealtTheTableUprightWithNoHeaderRow() {
        val layout = TableLayout.forScreen(466.dp, 658.dp, chromeOf(CLOSED_INSETS, CLOSED_RESERVED, true).chrome)
        assertEquals(Posture.UPRIGHT, layout.posture)
        assertEquals(0.dp, layout.header, "the header stands in the column")
        assertTrue(!layout.narrowRail)
        assertNotNull(layout.run)
    }

    @Test
    fun onTheOpenDuoTheFeltTakesTheWidthTheNarrowRailGivesUp() {
        val layout = TableLayout.forScreen(951.dp, 649.dp, OPEN_CHROME)
        assertEquals(Posture.ON_ITS_SIDE, layout.posture)
        assertTrue(layout.narrowRail, "beside a status column the rail is the narrow stack")
        assertTrue(layout.railWidth in 184.dp..232.dp, "the stack is a fifth of the screen: ${layout.railWidth}")
        assertEquals(951.dp - 84.dp - layout.railWidth, layout.feltWidth)

        // Without the column it is the side rail it always was, and wider.
        val wide = TableLayout.forScreen(951.dp, 649.dp)
        assertTrue(!wide.narrowRail)
        assertTrue(wide.railWidth > layout.railWidth)
    }

    private companion object {
        /** The closed Duo: its column on the right, its home indicator at the foot. */
        val CLOSED_INSETS = Sides(right = 84.dp, bottom = 20.dp)

        /** Its clock and connectivity, and under them the camera, in the column's top 150 points. */
        val CLOSED_RESERVED = Reserved(
            occlusions = listOf(DpRect(390.dp, 10.dp, 454.dp, 50.dp), DpRect(386.dp, 60.dp, 458.dp, 150.dp)),
        )

        /** Open on its side: the column again, and only a clock in its top 100. */
        val OPEN_CHROME = Chrome(column = 84.dp, occlusions = listOf(DpRect(880.dp, 20.dp, 940.dp, 100.dp)))

        /** Open upright: the clock in the corner of the 80-point strip. */
        val OPEN_UPRIGHT_CLOCK = Reserved(occlusions = listOf(DpRect(560.dp, 20.dp, 660.dp, 60.dp)))

        /** Half open like a book, the fold down the middle of the inner screen. */
        val FOLD = DpRect(471.5.dp, 0.dp, 479.5.dp, 669.dp)

        /** The home indicator's strip at the foot, which every screen keeps clear of. */
        const val HOME = 20

        /** How far under the strip the seat opposite's cards may start: the felt's own margins. */
        const val UNDER_THE_STRIP = 30f

        const val LEAVE = "Leave the table"
        const val RULES = "Rules"
        const val SETTINGS = "Settings"
        const val SEED = 20_260_903L
        const val TABLE_MS = 10_000L
    }
}
