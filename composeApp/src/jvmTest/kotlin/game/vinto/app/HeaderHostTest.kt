package game.vinto.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import game.vinto.app.art.Res
import game.vinto.app.art.header_rules
import game.vinto.app.game.TableHeader
import game.vinto.app.theme.Rail
import game.vinto.app.theme.VintoTheme
import game.vinto.client.teachingSession
import org.jetbrains.compose.resources.stringResource
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A header draws itself for the host it is told it is on, not for the machine it runs on.
 *
 * [HeaderStyleTest] holds the rule — a phone in portrait gets marks, a desktop gets words. This
 * holds the *plumbing*, which is where it went wrong: [TableHeader] read the platform's own
 * `host`, and the one caller that draws a phone's screen without being a phone is the store
 * renderer. `StoreShotsTest` asks Compose for 1080×1920 at a phone's density, which is 411 dp
 * across, and the JVM answers [Host.DESKTOP] — so six store screenshots went out with every
 * control wearing its word and the app's own wordmark pushed off the left edge. Each one a valid
 * PNG of a real screen, which is why nothing downstream said anything.
 *
 * Run on the JVM, where `host` really is [Host.DESKTOP], so an override that did not take would
 * fail here rather than passing by agreeing with the platform.
 */
@OptIn(ExperimentalTestApi::class)
class HeaderHostTest {

    @Test
    fun aHeaderToldItIsOnAPhoneDrawsMarksEvenOnADesktop() {
        assertEquals(Host.DESKTOP, host, "this test is only worth anything on a desktop JVM")
        assertEquals(0, labelsDrawnFor(Host.PHONE), "a phone's controls are marks, not words")
    }

    /** And with nobody overriding it, the desktop still gets the desktop's header. */
    @Test
    fun aHeaderLeftAloneDrawsForTheMachineItIsOn() {
        assertEquals(1, labelsDrawnFor(null), "the desktop's controls say what they do")
    }

    /** How many times the word on the Rules control is actually drawn. */
    private fun labelsDrawnFor(pretending: Host?): Int {
        var drawn = 0
        runComposeUiTest {
            val word = mutableStateOf("")
            setContent {
                word.value = stringResource(Res.string.header_rules)
                VintoTheme(dark = false) {
                    Surface(color = Rail.fill) {
                        Box(Modifier.width(PHONE_W)) {
                            val header = @androidx.compose.runtime.Composable {
                                TableHeader(
                                    view = teachingSession().view.value,
                                    round = 1,
                                    onHelp = {},
                                    onSettings = {},
                                    onLeave = {},
                                    landscape = false,
                                )
                            }
                            if (pretending == null) {
                                header()
                            } else {
                                CompositionLocalProvider(LocalHost provides pretending) { header() }
                            }
                        }
                    }
                }
            }
            waitForIdle()
            drawn = onAllNodesWithText(word.value).fetchSemanticsNodes().size
        }
        return drawn
    }

    private companion object {
        /** A phone's width, which is what the store renderer asks Compose for. */
        val PHONE_W = 411.dp
    }
}
