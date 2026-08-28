package game.vinto.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import game.vinto.app.theme.ButtonTone
import game.vinto.app.theme.CardWhite
import game.vinto.app.theme.Rail
import game.vinto.app.theme.Signal
import game.vinto.app.theme.Slate
import game.vinto.app.theme.VintoTheme
import game.vinto.app.theme.feltGold
import game.vinto.app.theme.feltGradient
import game.vinto.app.theme.onFelt
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Every colour this app puts against another one, measured.
 *
 * WCAG 2.1 asks for 4.5:1 between text and its background (1.4.3) and 3:1 for the visual
 * information that identifies a control or carries a state (1.4.11). Those are numbers, so
 * they can be a test rather than an opinion, and a test is the only form of this that
 * survives somebody picking a nicer green in six months.
 *
 * It reads the tokens *through* the theme rather than from the constants behind them, so it
 * also catches the other half of the problem: a token that resolves to the same colour in
 * both schemes, or a light scheme that was never wired up. Everything here is checked twice,
 * once in each.
 *
 * What it found when it was written, all of it real and all of it fixed:
 *
 *  * the active player's own name in gold on a light seat plate, at 1.9:1;
 *  * "this card can be touched" — the game's most important signal — at 1.8:1 in the dark
 *    scheme, because it was `primary`, and a card is white whatever the phone is set to;
 *  * white on the top of the green button's gradient, at 3.1:1;
 *  * a selected chip that differed from an unselected one by 1.5:1.
 */
@OptIn(ExperimentalTestApi::class)
class ContrastTest {

    // ---------------------------------------------------------------- text, 4.5:1

    @Test
    fun everythingWrittenOnTheRailCanBeRead() = bothSchemes { scheme ->
        text(Rail.ink, Rail.fill, "$scheme: the prompt")
        text(Rail.inkDim, Rail.fill, "$scheme: the second line, the log, every footnote")
        text(Rail.gold, Rail.fill, "$scheme: gold, which is a rank or a name and never decoration")
        text(Rail.coach, Rail.fill, "$scheme: the coach's voice")
        text(Rail.note, Rail.fill, "$scheme: what a card that just turned over is")
    }

    /** The chip that is chosen swaps ink and ground, so both directions have to hold. */
    @Test
    fun aChosenChipIsLegibleInverted() = bothSchemes { scheme ->
        text(Rail.fill, Rail.ink, "$scheme: the selected chip's label")
    }

    /** The header: the name of the game, and the two controls beside it. */
    @Test
    fun theWordmarkAndTheHelpButtonHoldUpOnBothRails() = bothSchemes { scheme ->
        // Large and bold, so 3:1 — but it is the name of the app, and 1.9:1 is what it was.
        ui(Rail.brand, Rail.fill, "$scheme: the wordmark")
        text(Rail.inkDim, Rail.fill, "$scheme: the round and turn count")
        ui(Rail.edge, Rail.fill, "$scheme: the ring round the help button")
    }

    /** The furniture on the cloth does not follow the theme, but it is still read. */
    @Test
    fun everyPlateCanBeRead() = bothSchemes { scheme ->
        text(Slate.ink, Slate.fill, "$scheme: a player's name")
        text(Slate.inkDim, Slate.fill, "$scheme: a plate's second line")
        text(Slate.gold, Slate.fill, "$scheme: the Vinto mark, and the active player's name")
    }

    /**
     * The name of the game, in gold, on green cloth.
     *
     * The rail's brass is 2.7:1 here — this is the pair that made a third gold necessary.
     */
    @Test
    fun theWordmarkOnTheFeltIsGoldThatCanBeRead() = bothSchemes { scheme ->
        val gold = MaterialTheme.colorScheme.feltGold()
        MaterialTheme.colorScheme.feltGradient().forEachIndexed { i, felt ->
            text(gold, felt, "$scheme: the wordmark, at stop $i of the felt")
        }
    }

    /** The felt is a gradient, so the label has to survive both ends of it. */
    @Test
    fun theLabelsOnTheFeltSurviveBothEndsOfIt() = bothSchemes { scheme ->
        val ink = MaterialTheme.colorScheme.onFelt()
        MaterialTheme.colorScheme.feltGradient().forEachIndexed { i, felt ->
            text(ink, felt, "$scheme: DRAW and DISCARD, at stop $i of the felt")
        }
    }

    /**
     * A button's label sits over a gradient, and the pass has to be at the *lighter* end
     * rather than at the average — which is how the green one shipped at 3.1:1.
     */
    @Test
    fun everyButtonLabelSurvivesTheWholeOfItsGradient() = bothSchemes { scheme ->
        ButtonTone.entries.forEach { tone ->
            text(tone.ink, tone.high, "$scheme: ${tone.name}, top of the gradient")
            text(tone.ink, tone.low, "$scheme: ${tone.name}, bottom of the gradient")
        }
    }

    // ---------------------------------------------------------------- signals, 3:1

    /**
     * The rings round a seat. Each is drawn on the boundary between the plate and the felt,
     * so it needs one neighbour it clears — and the plate is the one that is dark in both
     * schemes, which is the reason the plates do not follow the theme.
     */
    @Test
    fun everySeatRingHasANeighbourItStandsOutAgainst() = bothSchemes { scheme ->
        val felt = MaterialTheme.colorScheme.feltGradient()
        listOf(
            "your turn" to Signal.turn,
            "Vinto called" to Signal.vinto,
            "a penalty landed" to Signal.penalty,
            "the coalition" to Signal.coalition,
        ).forEach { (meaning, ring) ->
            val best = (felt + Slate.fill).maxOf { contrast(ring, it) }
            assertTrue(
                best >= UI,
                "$scheme: the ring for \"$meaning\" is %.2f:1 against everything it touches"
                    .format(best),
            )
        }
    }

    /** The rings round a card, which is white in both schemes. */
    @Test
    fun everyCardRingStandsOutAgainstTheCard() = bothSchemes { scheme ->
        ui(Signal.tappable.copy(alpha = DIMMEST), CardWhite, "$scheme: tappable, at its dimmest")
        ui(Signal.tappable, CardWhite, "$scheme: tappable, at its brightest")
        ui(Signal.chosen, CardWhite, "$scheme: chosen")
        ui(Signal.rightCall, CardWhite, "$scheme: a right call")
        ui(Signal.wrongCall, CardWhite, "$scheme: a wrong call")
    }

    /** What says where a control begins: a chip's outline, a switch's track. */
    @Test
    fun theOutlineOfAControlCanBeSeen() = bothSchemes { scheme ->
        ui(Rail.edge, Rail.fill, "$scheme: the outline of an unselected chip")
        ui(Rail.ink, Rail.fill, "$scheme: the fill of a selected one")
    }

    // ---------------------------------------------------------------- the schemes differ

    /**
     * And the point of all of it: that there are two schemes. A theme setting whose light
     * half resolves to the same colours as its dark half is a switch that does nothing, and
     * every case above would still pass.
     */
    @Test
    fun theLightSchemeIsNotTheDarkSchemeAgain() {
        val dark = capture(dark = true) { Rail.fill to Rail.ink }
        val light = capture(dark = false) { Rail.fill to Rail.ink }

        assertTrue(dark.first != light.first, "the rail is a different material in each scheme")
        assertTrue(luminance(light.first) > luminance(dark.first), "and the light one is lighter")
        assertTrue(luminance(light.second) < luminance(dark.second), "with its ink the other way")
    }

    // ---------------------------------------------------------------- the measuring

    private fun text(fg: Color, bg: Color, what: String) = held(fg, bg, TEXT, what)

    private fun ui(fg: Color, bg: Color, what: String) = held(fg, bg, UI, what)

    private fun held(fg: Color, bg: Color, need: Double, what: String) {
        // An indicator that fades is only as good as its faintest frame, and a colour drawn
        // at less than full alpha is really the colour it is drawn *over*.
        val got = contrast(fg.compositeOver(bg), bg)
        assertTrue(got >= need, "%s is %.2f:1, and has to be %.1f:1".format(what, got, need))
    }

    private fun Color.compositeOver(bg: Color): Color =
        if (alpha == 1f) this
        else Color(
            red = red * alpha + bg.red * (1 - alpha),
            green = green * alpha + bg.green * (1 - alpha),
            blue = blue * alpha + bg.blue * (1 - alpha),
        )

    private fun contrast(a: Color, b: Color): Double {
        val (hi, lo) = listOf(luminance(a), luminance(b)).sorted().reversed()
        return (hi + OFFSET) / (lo + OFFSET)
    }

    /** Relative luminance, as WCAG 2.1 defines it. */
    private fun luminance(c: Color): Double {
        fun channel(v: Float): Double {
            val d = v.toDouble()
            return if (d <= LINEAR_MAX) d / LINEAR_DIV else ((d + A) / (1 + A)).pow(GAMMA)
        }
        return R * channel(c.red) + G * channel(c.green) + B * channel(c.blue)
    }

    /** Runs the block inside each scheme in turn, so every case is really two. */
    private fun bothSchemes(check: @Composable (String) -> Unit) = runComposeUiTest {
        setContent {
            VintoTheme(dark = true) { check("dark") }
            VintoTheme(dark = false) { check("light") }
        }
    }

    private fun capture(dark: Boolean, of: @Composable () -> Pair<Color, Color>): Pair<Color, Color> {
        var held: Pair<Color, Color>? = null
        runComposeUiTest { setContent { VintoTheme(dark = dark) { held = of() } } }
        return checkNotNull(held) { "the theme never composed" }
    }

    private companion object {
        const val TEXT = 4.5
        const val UI = 3.0

        /** The trough of the tappable card's breath — see `CardFace`. */
        const val DIMMEST = 0.7f

        const val OFFSET = 0.05
        const val LINEAR_MAX = 0.03928
        const val LINEAR_DIV = 12.92
        const val GAMMA = 2.4
        const val A = 0.055
        const val R = 0.2126
        const val G = 0.7152
        const val B = 0.0722
    }
}
