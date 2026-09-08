package game.vinto.app.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon

/**
 * The pointer a thing that can be pressed puts under the mouse.
 *
 * On a phone there is no pointer and this is nothing. On the web and the desktop there is, and
 * Compose leaves it as the arrow for *everything* — so a page built out of `Surface(onClick)`
 * and drawn glyphs offers no clue which of its shapes are controls. A browser has taught
 * everybody that the hand is the answer to that question, and a game whose buttons do not
 * answer it reads as a picture of an app rather than an app.
 *
 * Applied at the components rather than at the call sites: every button in the app is
 * [GameButton], and a rule kept in one definition cannot be forgotten by the next screen.
 */
fun Modifier.pressable(): Modifier = pointerHoverIcon(PointerIcon.Hand)
