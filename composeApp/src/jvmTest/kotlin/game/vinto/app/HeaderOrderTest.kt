package game.vinto.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import game.vinto.app.art.Res
import game.vinto.app.art.header_leave
import game.vinto.app.art.header_rules
import game.vinto.app.art.header_settings
import game.vinto.app.art.header_support
import game.vinto.app.game.HeaderStyle
import game.vinto.app.game.TableHeader
import game.vinto.app.theme.Rail
import game.vinto.app.theme.VintoTheme
import game.vinto.client.teachingSession
import org.jetbrains.compose.resources.stringResource
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The way out of a room is the rightmost control, in every shape the header has.
 *
 * Leaving is where a row of controls ends — and it is the one control a misplaced thumb should be
 * least likely to find, which is an argument for the far end and against anywhere in the middle.
 * The rule is easy to keep by accident and easy to break by accident: the cup arrives between the
 * settings and the exit on the web only, so somebody adding the next control has a one-in-two
 * chance of appending it after the exit and never seeing the difference on their own machine.
 *
 * Asserted in POINTS rather than by reading the source, because "last in the Row" and "rightmost
 * on the screen" are the same thing only while nothing reorders or right-aligns.
 */
@OptIn(ExperimentalTestApi::class)
class HeaderOrderTest {

    @Test
    fun leavingIsTheRightmostControlInEveryHeader() {
        val wrong = mutableListOf<String>()

        SHAPES.forEach { (what, style) ->
            runComposeUiTest {
                val words = mutableStateOf(emptyList<String>())
                setContent {
                    words.value = listOfNotNull(
                        stringResource(Res.string.header_rules),
                        stringResource(Res.string.header_settings),
                        stringResource(Res.string.header_support).takeIf { style.cup },
                        stringResource(Res.string.header_leave),
                    )
                    VintoTheme(dark = false) {
                        Surface(color = Rail.fill) {
                            Box(Modifier.width(WIDE)) {
                                TableHeader(
                                    view = teachingSession().view.value,
                                    round = 1,
                                    onHelp = {},
                                    onSettings = {},
                                    onLeave = {},
                                    landscape = style.labelled,
                                    style = style,
                                )
                            }
                        }
                    }
                }
                waitForIdle()

                val edges = words.value.associateWith { word ->
                    val found = onAllNodesWithContentDescription(word).fetchSemanticsNodes()
                    found.firstOrNull()?.boundsInRoot?.left
                }
                val leaveAt = edges[words.value.last()]
                if (leaveAt == null) {
                    wrong += "$what: there is no way out in the header at all"
                    return@runComposeUiTest
                }
                val further = edges.filterValues { it != null && it > leaveAt }.keys
                if (further.isNotEmpty()) wrong += "$what: $further sit to the right of the way out"
            }
        }

        assertTrue(wrong.isEmpty(), "the way out is not where a row of controls ends:\n" + wrong.joinToString("\n"))
    }

    private companion object {
        /** Wide enough that every shape draws every control it has, labels included. */
        val WIDE = 900.dp

        /** Every header this app draws, named as the thing that produces it. */
        val SHAPES = listOf(
            "a phone in portrait" to HeaderStyle(labelled = false, cup = false),
            "a phone in landscape" to HeaderStyle(labelled = true, cup = false),
            "the desktop" to HeaderStyle(labelled = true, cup = false),
            "the web, which is the only header with a cup" to HeaderStyle(labelled = true, cup = true),
            "the web on a phone, marks and a cup" to HeaderStyle(labelled = false, cup = true),
        )
    }
}
