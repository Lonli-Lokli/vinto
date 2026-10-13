package game.vinto.app.theme

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import game.vinto.app.LocalStatusColumn
import game.vinto.app.SystemBack
import game.vinto.app.art.Res
import game.vinto.app.art.sheet_close
import org.jetbrains.compose.resources.stringResource

/**
 * A hairline between two things.
 *
 * Material's `HorizontalDivider` with a colour passed to it, which is all four call sites ever
 * wanted, minus the import. Kept as a named thing rather than an inline `Box` so that a change
 * of mind about weight or inset happens once.
 */
@Composable
fun Hairline(modifier: Modifier = Modifier, colour: Color = Rail.line) {
    Box(modifier = modifier.height(Line).background(colour))
}

/**
 * A visibility that animates even on the composition that first shows it.
 *
 * `AnimatedVisibility(visible = true)` does *not* animate on the frame it appears — it draws
 * the end state and there is nothing to move from. That is exactly the case here: the score
 * sheet is composed inside `game.result?.let`, so the first time it exists it is already
 * meant to be showing, and a plain flag would make the one sheet that matters most snap into
 * place while the two driven by a boolean glide.
 *
 * Starting the state at `false` and setting the target immediately gives the transition
 * something to run from, so a caller may pass a constant `true` and still get the rise.
 */
@Composable
private fun rising(open: Boolean): MutableTransitionState<Boolean> =
    remember { MutableTransitionState(false) }.apply { targetState = open }

/**
 * A panel that rises from the bottom edge.
 *
 * `ModalBottomSheet` is Material's, and it brings Material's drag handle, Material's scrim,
 * Material's corner radius and Material's spring — four decisions this app has already made
 * differently everywhere else. It is also `ExperimentalMaterial3Api`, so every screen using it
 * carries an opt-in for a control it does not otherwise want.
 *
 * What it does *not* bring, and what is deliberately not rebuilt here: dragging the sheet down
 * to dismiss. That is a real loss and a cheap one — the scrim dismisses on tap, the system back
 * gesture dismisses, and the sheets in this app are read and closed rather than half-opened.
 * Rebuilding a drag-to-dismiss with velocity and settling would be more machinery than the two
 * screens using it justify, and doing it badly is worse than not having it.
 *
 * The back gesture dismisses because of the [SystemBack] below, and nothing else would do it.
 * This comment said so for a long time while no handler existed: back fell through to the
 * app's own, so pressing it over Help or the standings left the table, and in a room it left
 * the room.
 */

/**
 * Where across the window a sheet should rise: over the felt of a table lying on its side, so it
 * covers cloth and cards rather than cutting the rail's words in half, and over a book's felt page,
 * clear of its crease. Null — the default — is the whole window.
 *
 * [left] is from the left of the table's box, by the side of the glass, whichever way the language
 * reads: the felt lies where the table laid it, and a status column on the left is part of that box.
 */
data class SheetSpan(val left: Dp, val width: Dp)

/** See [SheetSpan]. Provided by the screens that hold a table. */
val LocalSheetSpan = staticCompositionLocalOf<SheetSpan?> { null }

/** How a test finds a sheet. */
const val SHEET_TAG = "sheet"

@Composable
fun VintoSheet(open: Boolean, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    // Composed inside a screen, so after the app's handler, and Android asks the most recently
    // added handler first: while the sheet is open, back closes it and goes no further.
    SystemBack(enabled = open, onBack = onDismiss)
    Scrim(open = open, onDismiss = onDismiss)

    // Over the felt, where a table lying on its side says where that is ([LocalSheetSpan]); across
    // the window otherwise — less a status column, which the closed iPhone Duo's table stands its
    // controls in and the sheet leaves to the clock and the camera ([LocalStatusColumn]), on whichever
    // edge of the glass it is. Consumed, so a platform that reports the column as a system bar too
    // does not see it kept clear twice below.
    val span = LocalSheetSpan.current
    val column = LocalStatusColumn.current
    // The span is measured from the table box's left edge, and the column's own padding has already
    // moved this box past a column standing on the left.
    val columnLeft = column.calculateLeftPadding(LocalLayoutDirection.current)
    Box(
        modifier = Modifier.fillMaxSize().padding(column).consumeWindowInsets(column),
        contentAlignment = AbsoluteAlignment.BottomLeft,
    ) {
        Box(
            modifier = if (span == null) {
                Modifier.fillMaxSize()
            } else {
                Modifier
                    .absolutePadding(left = (span.left - columnLeft).coerceAtLeast(0.dp))
                    .width(span.width)
                    .fillMaxHeight()
            },
            contentAlignment = Alignment.BottomCenter,
        ) {
            AnimatedVisibility(
                visibleState = rising(open),
                enter = slideInVertically(tween(RiseMs)) { it },
                exit = slideOutVertically(tween(RiseMs)) { it },
            ) {
                Surface(
                    // Capped for the same reason as [VintoDialog], and a little wider because a
                    // sheet carries a table of ranks rather than a sentence.
                    modifier = Modifier.widthIn(max = SheetWide).fillMaxWidth().testTag(SHEET_TAG),
                    shape = RoundedCornerShape(topStart = SheetCorner, topEnd = SheetCorner),
                    color = Rail.fill,
                    contentColor = Rail.ink,
                    border = BorderStroke(Line, Rail.line),
                ) {
                    Box(modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars)) {
                        Column(
                            // Expanded, a sheet can reach the top of the screen, and without
                            // this the first line of it sits under the clock.
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Grip()
                            content()
                        }

                        // The way out that is always on screen. The scrim dismisses on tap and
                        // Android's back gesture dismisses (`SystemBack`, above) — but a sheet
                        // tall enough to cover the scrim, on a platform with no back gesture (the
                        // browser, iOS), left a phone user with no exit at all. The help sheet is
                        // exactly that sheet.
                        CloseButton(
                            onDismiss = onDismiss,
                            modifier = Modifier.align(Alignment.TopEnd),
                        )
                    }
                }
            }
        }
    }
}

/**
 * A question that has to be answered before anything else happens.
 *
 * `AlertDialog` is Material's, and on a table it reads as a permission prompt — which is the
 * wrong register for the two things this app asks: whether to abandon a round, and whether to
 * send a bug report. Both are the app's own moments and should look like the app.
 *
 * The buttons are the caller's, and are [GameButton]s, so the answer to a question looks like
 * every other move a player makes.
 *
 * Back on Android answers it the way a tap on the scrim does, through [SystemBack]. Without
 * that it went past the dialog to the app's own handler and left the screen behind it, which
 * is not an answer to either question.
 */
@Composable
fun VintoDialog(
    open: Boolean,
    onDismiss: () -> Unit,
    title: String,
    body: String,
    buttons: @Composable () -> Unit,
) {
    // Asked before the app's handler while open, for the reason [VintoSheet] gives.
    SystemBack(enabled = open, onBack = onDismiss)
    Scrim(open = open, onDismiss = onDismiss)

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AnimatedVisibility(
            visibleState = rising(open),
            enter = fadeIn(tween(RiseMs)),
            exit = fadeOut(tween(RiseMs)),
        ) {
            Surface(
                // Capped, because `fillMaxWidth` on a phone is a dialog and on a desktop is a
                // banner: two metres of panel holding one sentence and a button stretched across
                // all of it. A dialog is read, and a line of text past about sixty characters
                // stops being read and starts being scanned.
                modifier = Modifier
                    .padding(DialogInset)
                    .widthIn(max = DialogWide)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(DialogCorner),
                color = Rail.fill,
                contentColor = Rail.ink,
                border = BorderStroke(Line, Rail.line),
                shadowElevation = DialogLift,
            ) {
                Column(
                    modifier = Modifier.padding(DialogPad),
                    verticalArrangement = Arrangement.spacedBy(DialogGap),
                ) {
                    Text(text = title, style = stamped(size = TitleSize))
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Rail.inkDim,
                    )
                    buttons()
                }
            }
        }
    }
}

/**
 * The dark behind a panel, and the tap that dismisses it.
 *
 * Its own composable because both panels need exactly this and nothing else: a fade, a click
 * target with no ripple — a ripple on a full-screen scrim is a splash across the whole display
 * — and a spoken name, so a screen reader user has a way out that is not the back gesture.
 */
@Composable
private fun Scrim(open: Boolean, onDismiss: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val close = stringResource(Res.string.sheet_close)
    AnimatedVisibility(
        visibleState = rising(open),
        enter = fadeIn(tween(RiseMs)),
        exit = fadeOut(tween(RiseMs)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = ScrimAlpha))
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onDismiss,
                )
                .semantics { contentDescription = close },
        )
    }
}

/**
 * A ✕ in the sheet's own chrome, drawn as strokes rather than fetched from a glyph font —
 * the same decision the header's gear made. Spoken as "Close", matching the scrim, so the two
 * exits announce themselves alike. In the reader's language: this said "Close" in English in
 * every locale, the one spoken word the theme layer kept out of `strings.xml`.
 */
@Composable
private fun CloseButton(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val close = stringResource(Res.string.sheet_close)
    Box(
        modifier = modifier
            .padding(GripPad)
            .size(CloseSize)
            .clip(RoundedCornerShape(CloseSize))
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onDismiss,
            )
            .semantics { contentDescription = close },
        contentAlignment = Alignment.Center,
    ) {
        val ink = Rail.inkDim
        Canvas(modifier = Modifier.size(CloseGlyph)) {
            val stroke = CloseStroke.toPx()
            drawLine(ink, Offset(0f, 0f), Offset(size.width, size.height), stroke, StrokeCap.Round)
            drawLine(ink, Offset(size.width, 0f), Offset(0f, size.height), stroke, StrokeCap.Round)
        }
    }
}

/** The bar that says a panel is a panel. Decorative: it is not draggable, and claims nothing. */
@Composable
private fun Grip() {
    Box(
        modifier = Modifier
            .padding(vertical = GripPad)
            .width(GripWidth)
            .height(GripHeight)
            .clip(RoundedCornerShape(GripHeight))
            .background(Rail.edge),
    )
}

private val Line = 1.dp
private val SheetCorner = 18.dp
private val DialogCorner = 14.dp

/**
 * The widest a dialog and a sheet are ever drawn.
 *
 * Both filled the window, which is right on the phone they were drawn for and wrong on
 * everything else: on a desktop the deck explainer came out two metres wide, one sentence
 * across all of it and a "GOT IT" the width of the screen. Reading has a comfortable measure
 * and it is nowhere near a monitor's width.
 */
private val DialogWide = 460.dp

private val SheetWide = 620.dp

private val DialogInset = 28.dp
private val DialogPad = 20.dp
internal val DialogGap = 12.dp
private val DialogLift = 8.dp
private val GripWidth = 36.dp
private val GripHeight = 4.dp
private val GripPad = 10.dp
private val CloseSize = 44.dp
private val CloseGlyph = 12.dp
private val CloseStroke = 2.dp

private const val ScrimAlpha = 0.62f
private const val RiseMs = 220
private const val TitleSize = 18
