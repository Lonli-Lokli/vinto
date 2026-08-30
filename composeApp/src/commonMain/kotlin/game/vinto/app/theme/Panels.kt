package game.vinto.app.theme

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

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
 */
@Composable
fun VintoSheet(open: Boolean, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Scrim(open = open, onDismiss = onDismiss)

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visibleState = rising(open),
            enter = slideInVertically(tween(RiseMs)) { it },
            exit = slideOutVertically(tween(RiseMs)) { it },
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = SheetCorner, topEnd = SheetCorner),
                color = Rail.fill,
                contentColor = Rail.ink,
                border = BorderStroke(Line, Rail.line),
            ) {
                Column(
                    // Expanded, a sheet can reach the top of the screen, and without this the
                    // first line of it sits under the clock.
                    modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Grip()
                    content()
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
 */
@Composable
fun VintoDialog(
    open: Boolean,
    onDismiss: () -> Unit,
    title: String,
    body: String,
    buttons: @Composable () -> Unit,
) {
    Scrim(open = open, onDismiss = onDismiss)

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AnimatedVisibility(
            visibleState = rising(open),
            enter = fadeIn(tween(RiseMs)),
            exit = fadeOut(tween(RiseMs)),
        ) {
            Surface(
                modifier = Modifier
                    .padding(DialogInset)
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
                .semantics { contentDescription = "Close" },
        )
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
private val DialogInset = 28.dp
private val DialogPad = 20.dp
private val DialogGap = 12.dp
private val DialogLift = 8.dp
private val GripWidth = 36.dp
private val GripHeight = 4.dp
private val GripPad = 10.dp

private const val ScrimAlpha = 0.62f
private const val RiseMs = 220
private const val TitleSize = 18
