package game.vinto.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import game.vinto.app.game.SeatBadge
import game.vinto.app.game.SeatPlate
import game.vinto.app.theme.VintoTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A seat plate is the same size whether or not the table is waiting on it.
 *
 * Reported from a phone: the thought cloud arrives and the plate grows, which pushes the hand
 * beside it along. It is the one mark that comes and goes **every turn** — outside the setup
 * peeks and a toss-in window, `badgesFor` gives it to whoever's turn it is and nobody else — so
 * a plate that resizes for it resizes four times a round.
 *
 * The push is not a matter of taste. A plate sits in a `Row` with the hand at
 * `weight(1f, fill = false)`, so the width the plate takes is width the hand does not get, and
 * `HandLine` pitches the cards from exactly that number. Twenty points is enough to re-pitch a
 * hand of five and, near the wrap threshold, to send it onto a second row and back.
 *
 * Measured at every table size, because the mark scales with the portrait and the widths that
 * matter — the name's cap, the badge floor — do not scale with it.
 */
@OptIn(ExperimentalTestApi::class)
class SteadyPlateTest {

    @Test
    fun aPlateIsTheSameSizeWhetherOrNotItIsThinking() {
        // Every table size, since the mark is half the portrait and the floor under it is not.
        for (portrait in listOf(TIGHT, ROOMY, GRAND, VAST)) {
            // A short name and a long one: the plate is as wide as its widest row, so a name
            // that already outruns the marks would hide the growth the report is about.
            for (name in listOf("You", "Ember", "Clever Hedgehog")) {
                for (wearing in DURABLE) {
                    val quiet = plate(wearing, portrait, name, thinking = false)
                    val thinking = plate(wearing, portrait, name, thinking = true)

                    assertEquals(
                        quiet,
                        thinking,
                        "$name's plate at $portrait wearing $wearing changed size when the " +
                            "table started waiting on it: $quiet became $thinking",
                    )
                }
            }
        }
    }

    /** The plate's own laid-out size, which is what the hand beside it has to plan around. */
    private fun plate(
        badges: List<SeatBadge>,
        portrait: Dp,
        name: String,
        thinking: Boolean,
    ): IntSize {
        var size = IntSize.Zero
        runComposeUiTest {
            setContent {
                VintoTheme {
                    Box(modifier = Modifier.size(PHONE_W, PHONE_H)) {
                        SeatPlate(
                            name = name,
                            active = false,
                            modifier = Modifier.onSizeChanged { size = it },
                            marks = "12",
                            badges = badges,
                            thinking = thinking,
                            size = portrait,
                        )
                    }
                }
            }
            waitForIdle()
        }
        return size
    }

    private companion object {
        /** The four `TableSizes` portraits, from a short phone to a desktop window. */
        val TIGHT = 30.dp
        val ROOMY = 38.dp
        val GRAND = 50.dp
        val VAST = 66.dp

        /**
         * What a seat can already be wearing when the cloud arrives.
         *
         * The durable marks — a machine plays this seat, this seat called Vinto — are what the
         * cloud has to make room beside, and they are what turns a nine-point growth into a
         * twenty-eight-point one.
         */
        val DURABLE = listOf(
            emptyList(),
            listOf(SeatBadge.BOT),
            listOf(SeatBadge.BOT, SeatBadge.VINTO),
        )

        val PHONE_W = 411.dp
        val PHONE_H = 740.dp
    }
}
