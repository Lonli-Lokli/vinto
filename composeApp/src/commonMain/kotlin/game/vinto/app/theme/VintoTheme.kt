package game.vinto.app.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * The look of the table.
 *
 * Built on Material 3 rather than against it: the components come with touch targets, focus
 * rings and dynamic type already right, and a card game has no reason to re-litigate any of
 * that. What is overridden is the palette, because a felt table and a deck of cards are the
 * one thing Material's defaults cannot know about.
 *
 * Both schemes are defined outright rather than derived from a seed colour. A generated dark
 * scheme puts the cards and the felt within a few steps of each other, and a card you cannot
 * pick out of the background is the only thing on this screen that has to work.
 */
private val Felt = Color(0xFF1B5E43)
private val FeltDark = Color(0xFF0E3428)
private val Gold = Color(0xFFC9A227)
private val Ink = Color(0xFF14181B)
private val Paper = Color(0xFFF7F5EF)

private val LightScheme = lightColorScheme(
    primary = Felt,
    onPrimary = Paper,
    primaryContainer = Color(0xFFB7E4CE),
    onPrimaryContainer = Color(0xFF06281C),
    secondary = Gold,
    onSecondary = Ink,
    background = Color(0xFFEFEDE6),
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Color(0xFFDCDDD6),
    onSurfaceVariant = Color(0xFF44483F),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF6FD3A6),
    onPrimary = Color(0xFF003824),
    primaryContainer = FeltDark,
    onPrimaryContainer = Color(0xFFB7E4CE),
    secondary = Gold,
    onSecondary = Ink,
    background = Color(0xFF10130F),
    onBackground = Color(0xFFE2E3DC),
    surface = Color(0xFF1B1F1B),
    onSurface = Color(0xFFE2E3DC),
    surfaceVariant = Color(0xFF3F473F),
    onSurfaceVariant = Color(0xFFC0C9BE),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
)

/**
 * The felt itself.
 *
 * Not a colour-scheme role, because it is not one: Material's surfaces are for panels a user
 * reads, and this is a playing surface that has to sit *behind* white cards without competing
 * with them. Two greens with a gradient between, as on the web table, so the middle of the
 * table reads as further away than its edges.
 */
fun ColorScheme.feltGradient(): List<Color> =
    if (isDarkFelt()) listOf(FeltDarkTop, FeltDarkBottom) else listOf(FeltLightTop, FeltLightBottom)

fun ColorScheme.feltEdge(): Color = if (isDarkFelt()) FeltRimDark else FeltRimLight

/**
 * The lamp over the table.
 *
 * A vertical gradient says "gradient"; a card table lit from above says *table*. This is the
 * pool of light the cloth is lit by — brightest a third of the way down, where the piles sit,
 * falling off towards the rim — laid over [feltGradient] rather than replacing it, so the
 * cloth keeps its colour and gains a shape.
 */
fun ColorScheme.feltLamp(radius: Float): Brush = Brush.radialGradient(
    colorStops = arrayOf(
        0f to Color.White.copy(alpha = if (isDarkFelt()) LAMP_DARK else LAMP_LIGHT),
        LAMP_MID to Color.White.copy(alpha = LAMP_FADE),
        1f to Color.Transparent,
    ),
    center = Offset(radius * LAMP_X, radius * LAMP_Y),
    radius = radius,
)

/** And the shadow the rim casts back onto the cloth, which is what makes it a rim. */
fun ColorScheme.feltShade(): Brush = Brush.verticalGradient(
    0f to Color.Black.copy(alpha = SHADE),
    SHADE_DEPTH to Color.Transparent,
)

private const val LAMP_DARK = 0.055f
private const val LAMP_LIGHT = 0.085f
private const val LAMP_FADE = 0.02f
private const val LAMP_MID = 0.55f
private const val LAMP_X = 1f
private const val LAMP_Y = 0.85f
private const val SHADE = 0.22f
private const val SHADE_DEPTH = 0.06f

/**
 * Anything written *on* the felt.
 *
 * The same near-white in both schemes, because the felt is dark green in both. Using
 * `onPrimary` for this was wrong in a way that only showed up in dark mode: there it is a
 * dark green meant to sit on a light button, and the pile labels vanished into the table.
 */
@Suppress("UnusedReceiverParameter")
fun ColorScheme.onFelt(): Color = FeltInk

/**
 * Gold, as it looks on green baize.
 *
 * The brass that works on the rail is 2.7:1 against the lighter felt — the name of the game,
 * on the first screen of the app, in a colour that cannot be read on it. This is the same
 * metal under the table's own lamp: pale enough to clear 4.9:1 on the light cloth and 8.4:1
 * on the dark, which makes it gold leaf rather than gold paint, and the right of the two for
 * something stamped on cloth anyway.
 */
@Suppress("UnusedReceiverParameter")
fun ColorScheme.feltGold(): Color = LeafGold

private val LeafGold = Color(0xFFF2DFA6)

private val FeltInk = Color(0xFFF2F5F0)

/**
 * Something wrong, said on the felt.
 *
 * The scheme's own `error` is chosen against the scheme's surfaces, and the light scheme's is
 * a deep red for paper — 1.04:1 on green cloth, which `ScreenContrastTest` measured on the
 * public-rooms screen the first time it looked. The felt is dark in both schemes, so like
 * [onFelt] and [feltGold] this is one colour: the pale red the dark schemes of the world use
 * for the same job, lightened until it clears 4.5:1 on the lighter cloth.
 */
@Suppress("UnusedReceiverParameter")
fun ColorScheme.errorOnFelt(): Color = FeltError

private val FeltError = Color(0xFFFFD2CC)

/**
 * The rail, and everything cut from the same material: the band under the felt, the home
 * panel, the settings sections, the score sheet, the help sheet.
 *
 * This is the half of the app that follows the phone. The felt does not — a card table is
 * green cloth at noon and at midnight — and neither does the furniture standing on it
 * ([Slate]). What changes is the material of the *room*: dark slate, or paper.
 *
 * Read as composable properties rather than constants because a colour that can change with
 * the theme is no longer a value; `Rail.fill` costs a `MaterialTheme` lookup and reads at the
 * call site exactly as the constant it replaced did.
 */
object Rail {
    /** The panel itself. */
    val fill: Color
        @Composable @ReadOnlyComposable
        get() = pick(SlateFill, PaperFill)

    /** Anything a player reads. */
    val ink: Color
        @Composable @ReadOnlyComposable
        get() = pick(SlateInk, PaperInk)

    /** The second line: detail, footnotes, the log. Still 4.5:1 on [fill]. */
    val inkDim: Color
        @Composable @ReadOnlyComposable
        get() = pick(SlateInkDim, PaperInkDim)

    /** A hairline between two things. Decorative: nothing is lost if it is not seen. */
    val line: Color
        @Composable @ReadOnlyComposable
        get() = pick(SlateLine, PaperLine)

    /**
     * The outline of a control — a chip, a switch's track. Not decorative: it is what says
     * where the thing you may press begins, so it clears 3:1 against [fill] in both schemes.
     */
    val edge: Color
        @Composable @ReadOnlyComposable
        get() = pick(SlateEdge, PaperEdge)

    /**
     * Gold, dark enough to read as text on whichever [fill] it lands on.
     *
     * The one colour that could not be shared. `secondary` was used for this and is a bright
     * gold: 6.7:1 on slate and 1.9:1 on paper, which is the active player's own name turning
     * invisible on a light phone.
     */
    val gold: Color
        @Composable @ReadOnlyComposable
        get() = pick(Gold, DeepGold)

    /**
     * The name of the game, and the app's one accent.
     *
     * Gold rather than the web app's green, and gold used the way a card room uses metal:
     * thin, and on the few things that are not ordinary — the name, a Vinto call, the
     * hairlines either side of a screen's title. One accent is what stops a palette reading
     * as a set of options; green is left to mean the table, and the word "go".
     *
     * Two values because it is a *material*: the same brass is dark on paper and bright on
     * slate, which is what metal does, and either way it is read.
     */
    val brand: Color
        @Composable @ReadOnlyComposable
        get() = gold

    /** The coach's own voice, and the chapter marks beside it. */
    val coach: Color
        @Composable @ReadOnlyComposable
        get() = pick(Mint, DeepCoach)

    /** What a card just turned over is, and other asides. Gold's quieter cousin. */
    val note: Color
        @Composable @ReadOnlyComposable
        get() = pick(Amber, DeepGold)
}

@Composable
@ReadOnlyComposable
private fun pick(dark: Color, light: Color): Color =
    if (MaterialTheme.colorScheme.isDarkFelt()) dark else light

/**
 * The table's own furniture: the seat plates, and anything else standing on the cloth.
 *
 * Fixed in both schemes, like the felt. A near-white pill on green felt is not a light
 * theme, it is a sticker; and keeping the plates dark is also what keeps the four attention
 * rings legible, since every one of them then has a dark neighbour on its inner edge.
 */

/**
 * The colours that mean something rather than decorate something.
 *
 * Gathered here because each one is a claim that can be checked: a ring that says "your turn"
 * or "this card can be touched" is information carried by colour alone, which WCAG 1.4.11 puts
 * at 3:1 against whatever it is drawn against. `ContrastTest` holds every one of them to it,
 * which it can only do if they live somewhere it can see.
 */
object Signal {
    /** Rings round a seat plate. Drawn against [Slate.fill] on the inside, felt on the out. */
    val turn = Color(0xFF6FD3A6)
    val vinto = Color(0xFFE0A32A)
    val penalty = Color(0xFFEF4444)
    val coalition = Color(0xFF5A94F0)

    /**
     * Rings round a card, which is white in both schemes — so these are not scheme colours,
     * and a good deal deeper than the ones above.
     */
    val tappable = Color(0xFF14713F)
    val chosen = Color(0xFFA8801A)
    val rightCall = Color(0xFF15A34A)
    val wrongCall = Color(0xFFEF4444)

    /**
     * A card on the pile whose action nobody has used. Brass, like everything else on this
     * table that is worth something: it is an offer, and it is only there until somebody
     * takes it or covers it.
     */
    val live = Color(0xFFA8801A)
}

/** The white of a card face: what [Signal.tappable] and its neighbours are drawn against. */
val CardWhite = Color(0xFFFFFFFF)

object Slate {
    val fill = SlateFill
    val ink = SlateInk
    val inkDim = SlateInkDim
    val gold = Gold
}

/** The green the web app's wordmark uses. Kept for the deck badge's own dark ground. */
val Brand = Color(0xFF34D07A)

private val Mint = Color(0xFF6FD3A6)
private val Amber = Color(0xFFF2C14E)

// Slate: the dark scheme's rail, and the table's furniture in both.
//
// Warm, not blue. The old values were a blue-grey — #1B2430 and a blue-white ink — which is
// the neutral every dashboard and banking app is built from, and it is most of what made a
// green card table read as a dark-mode form. A card room is lit by a warm lamp over a green
// cloth: the darks go to charcoal with a green cast, and the ink to cream. Nothing else
// about the design changed and the whole thing stopped looking like software.
private val SlateFill = Color(0xFF19211C)
private val SlateInk = Color(0xFFF3EFE6)
private val SlateInkDim = Color(0xFFB3B5A6)
private val SlateLine = Color(0xFF2E3830)
private val SlateEdge = Color(0xFF6F7A6E)

// Paper: the light scheme's rail. Warm rather than white, so the cards stay the brightest
// thing on the screen — they are what a player is trying to read.
private val PaperFill = Color(0xFFF4F1E8)
private val PaperInk = Color(0xFF191A15)
private val PaperInkDim = Color(0xFF54584A)
private val PaperLine = Color(0xFFD8D4C8)
private val PaperEdge = Color(0xFF7A7C6C)
private val DeepGold = Color(0xFF7A5C10)
private val DeepCoach = Color(0xFF10703F)

private val FeltDarkTop = Color(0xFF14442F)
private val FeltDarkBottom = Color(0xFF0A2A1D)
private val FeltLightTop = Color(0xFF1E6B4C)
private val FeltLightBottom = Color(0xFF124A33)
private val FeltRimDark = Color(0xFF2C7A57)
private val FeltRimLight = Color(0xFF0E3A28)

/** The dark scheme is the one whose background is darker than its surface. */
private fun ColorScheme.isDarkFelt(): Boolean = background.luminanceIsLow()

private fun Color.luminanceIsLow(): Boolean = (red + green + blue) < LOW_LUMINANCE

private const val LOW_LUMINANCE = 1.0f

@Composable
fun VintoTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        typography = vintoTypography(),
        content = content,
    )
}
