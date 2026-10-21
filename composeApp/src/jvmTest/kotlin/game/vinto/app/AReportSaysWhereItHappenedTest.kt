package game.vinto.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import game.vinto.app.capture.MarketingScene
import game.vinto.app.capture.Staged
import game.vinto.app.crash.CrashSurface
import game.vinto.app.crash.Crashes
import game.vinto.app.theme.VintoTheme
import game.vinto.client.MemoryVault
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A report says the screen it happened on.
 *
 * `MOVE_REFUSED in SOLO` arrived from the web on 2026-09-28 tagged `surface: MENU` — its own
 * message named the solo table, and its tag named the menu. The tag was always going to: the
 * reporter read the surface from a composition local one level above the screen that provides
 * it, so it read the default, and every report the app has ever sent said MENU wherever it
 * came from.
 *
 * Still the one question on every client. On Android the SDK asks it too: `Crashes.install` hands
 * the same lambda to the SDK's event processor, which reads it when an event is made
 * (`PhoneReportsTest.theAddressIsTheOneShowingWhenTheReportIsMade`).
 */
@OptIn(ExperimentalTestApi::class)
class AReportSaysWhereItHappenedTest {

    @AfterTest
    fun forget() = Crashes.forget()

    @Test
    fun aReportFromTheSoloTableSaysTheSoloTable() = runComposeUiTest {
        setContent {
            VintoTheme {
                Staged(MarketingScene.TABLE.id) { App(seeds = { SEED }, vault = MemoryVault(), firstScreen = it) }
            }
        }
        waitForIdle()

        assertEquals(CrashSurface.SOLO, Crashes.where(), "a report made at the solo table is filed as somewhere else")
    }

    private companion object {
        const val SEED = 42L
    }
}
