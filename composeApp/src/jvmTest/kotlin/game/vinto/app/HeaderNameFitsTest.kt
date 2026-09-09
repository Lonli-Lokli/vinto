package game.vinto.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import game.vinto.app.art.Res
import game.vinto.app.art.app_name
import game.vinto.app.game.HeaderName
import game.vinto.app.game.TableHeader
import game.vinto.app.theme.Rail
import game.vinto.app.theme.VintoTheme
import game.vinto.app.theme.Wordmark
import game.vinto.client.teachingSession
import org.jetbrains.compose.resources.stringResource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The wordmark is drawn whole or not at all.
 *
 * The header gives its words away before its controls — a missing word is still a header, a
 * shrunken control is a button nobody can hit — and the group holding the name and the round
 * counter carries the row's weight, so when the row runs out that group is *squeezed*. Which
 * is safe only while the decision to draw the name is made from the width it really needs, in
 * the style it is really drawn in, against the room it really has. It was made from an
 * estimate on the other side of the row instead, in a style written out a second time and
 * missing the two points of tracking on every glyph.
 *
 * A phone reported the result: **"VINT"** under a wordmark, with the counter beside it cut to
 * one letter. On the web the same arithmetic has a second way to be wrong that no arithmetic
 * can fix — a font that arrives after the first frames means the header measured one alphabet
 * and drew a wider one.
 *
 * So the property is not "the estimate is right". It is: *whatever the estimate said, nothing
 * is drawn that does not fit*.
 */
@OptIn(ExperimentalTestApi::class)
class HeaderNameFitsTest {

    /**
     * The direct case, and the one that fails on the old shape: told there is room, and given
     * less than the name needs, the name is dropped rather than clipped.
     */
    @Test
    fun aNameWithNoRoomIsDroppedRatherThanClipped() {
        var drawn = -1
        runComposeUiTest {
            val name = mutableStateOf("")
            setContent {
                name.value = stringResource(Res.string.app_name)
                val need = wordmarkWidth(name.value)
                Framed(need - PINCH) {
                    // Given less room than the name needs: the state the report was in, and the
                    // one the header now decides for itself rather than being told about.
                    HeaderName(
                        name = name.value,
                        counter = COUNTER,
                        modifier = Modifier.width(need - PINCH),
                    )
                }
            }
            waitForIdle()
            drawn = onAllNodesWithText(name.value).fetchSemanticsNodes().size
        }

        assertEquals(0, drawn, "the name is drawn in less room than it needs, so it is clipped")
    }

    /** And with the room, it is there — a header that never shows its name is not the fix. */
    @Test
    fun aNameWithRoomIsDrawn() {
        var drawn = -1
        runComposeUiTest {
            val name = mutableStateOf("")
            setContent {
                name.value = stringResource(Res.string.app_name)
                val need = wordmarkWidth(name.value)
                Framed(need + ROOM) {
                    HeaderName(
                        name = name.value,
                        counter = COUNTER,
                        modifier = Modifier.width(need + ROOM),
                    )
                }
            }
            waitForIdle()
            drawn = onAllNodesWithText(name.value).fetchSemanticsNodes().size
        }

        assertTrue(drawn > 0, "a header with room for its name does not show it")
    }

    /** The whole header, swept: wherever the name is shown, it has the width to be read. */
    @Test
    fun theNameIsNeverDrawnClippedAtAnyWidth() {
        val clipped = mutableListOf<String>()

        widths().forEach { width ->
            runComposeUiTest {
                val need = mutableStateOf(0.dp)
                val name = mutableStateOf("")
                setContent {
                    name.value = stringResource(Res.string.app_name)
                    need.value = wordmarkWidth(name.value)
                    Framed(width) {
                        TableHeader(
                            view = teachingSession().view.value,
                            round = 1,
                            onHelp = {},
                            onSettings = {},
                            onLeave = {},
                        )
                    }
                }
                waitForIdle()

                val node = onAllNodesWithText(name.value).fetchSemanticsNodes().firstOrNull()
                    ?: return@runComposeUiTest
                val shown = with(node.layoutInfo.density) { node.boundsInRoot.width.toDp() }
                if (shown + SLACK < need.value) {
                    clipped += "at $width the name is drawn in $shown and needs ${need.value}"
                }
            }
        }

        assertTrue(
            clipped.isEmpty(),
            "the header shows a wordmark it has not left room for:\n" + clipped.joinToString("\n"),
        )
    }

    /**
     * And on a PHONE it is actually there — which is the half the sweep above cannot ask.
     *
     * That sweep skips a width where the name is absent (`?: return@runComposeUiTest`), because
     * its question is "is what is drawn legible". So a header that draws its name nowhere passes
     * it perfectly, and that is exactly what shipped: reported from a phone, a header with a blank
     * left half and three controls floating on the right.
     *
     * The cause was one condition written twice. `headerRoom` decided the words would fit and
     * subtracted the WIDE header's cost from the room left over — while `TableHeader` drew the
     * narrow header, because that also needs `maxWidth >= WideHeader`. So the estimate charged a
     * phone for labels it was never going to draw and reported no room for the wordmark. Two
     * copies of a rule, disagreeing exactly where nobody was looking.
     */
    @Test
    fun theNameIsDrawnOnAPhone() {
        val missing = mutableListOf<Dp>()

        PHONES.forEach { width ->
            runComposeUiTest {
                val name = mutableStateOf("")
                setContent {
                    name.value = stringResource(Res.string.app_name)
                    Framed(width) {
                        // `onLeave = null` on purpose: it makes this header THREE controls, which
                        // is what a phone draws. The JVM this runs on offers the support cup and a
                        // phone does not (`supportOffer`), so a four-control header here is a
                        // desktop's geometry — and at 320 points four controls genuinely leave no
                        // room for a wordmark, which is the header working rather than failing.
                        TableHeader(
                            view = teachingSession().view.value,
                            round = 1,
                            onHelp = {},
                            onSettings = {},
                            onLeave = null,
                        )
                    }
                }
                waitForIdle()
                if (onAllNodesWithText(name.value).fetchSemanticsNodes().isEmpty()) missing += width
            }
        }

        assertTrue(
            missing.isEmpty(),
            "the header shows no wordmark at ordinary phone widths: $missing",
        )
    }

    // ------------------------------------------------------------------ measuring

    /**
     * What the name needs, in the style it is actually drawn in — tracking included.
     *
     * Written out here rather than read from the app on purpose: measured the app's way this
     * would agree with the app, including where the app is wrong.
     */
    @Composable
    private fun wordmarkWidth(name: String): Dp {
        val measurer = rememberTextMeasurer()
        val style = TextStyle(
            fontFamily = Wordmark,
            fontSize = WORDMARK_SP.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = TRACKING_SP.sp,
        )
        return with(LocalDensity.current) {
            measurer.measure(AnnotatedString(name), style, maxLines = 1).size.width.toDp()
        }
    }

    @Composable
    private fun Framed(width: Dp, content: @Composable () -> Unit) {
        VintoTheme(dark = false) {
            Surface(color = Rail.fill) {
                Box(modifier = Modifier.width(width)) { content() }
            }
        }
    }

    /** Phone to desktop, in steps small enough to land either side of any threshold. */
    private fun widths(): List<Dp> = generateSequence(FROM) { it + STEP }
        .takeWhile { it <= TO }
        .toList()

    private companion object {
        /** Every phone this app is drawn on, narrowest to widest, in points. */
        val PHONES = listOf(320.dp, 360.dp, 384.dp, 411.dp, 430.dp)

        val FROM = 280.dp
        val TO = 760.dp
        val STEP = 8.dp

        /** Just inside the name's own width: enough to clip a glyph, not enough to be a layout. */
        val PINCH = 6.dp
        val ROOM = 24.dp

        const val COUNTER = "R1 / T1"

        /** `TableScreen`'s own wordmark, pinned: a drift there fails this rather than a phone. */
        const val WORDMARK_SP = 19
        const val TRACKING_SP = 2

        /** A point of rounding either way, so this measures clipping and not arithmetic. */
        val SLACK = 1.dp
    }
}
