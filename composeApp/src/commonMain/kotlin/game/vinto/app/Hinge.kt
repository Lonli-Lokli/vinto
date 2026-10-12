package game.vinto.app

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * A folding phone's hinge, as the platform reports it while it moves.
 *
 * iOS 27.1 reports it live on the iPhone Duo (`onHingeChange`): whether the phone is part way
 * open, and the angle between its halves, 180 degrees when it lies flat. Apple's own use of it is
 * the wallpaper zooming as the phone opens; the table's is the same idea ([game.vinto.app.game.Reshape]):
 * the table spreads across the opening phone with the hand that opens it, rather than on a timer.
 * Elsewhere — Android, which reports a half-open fold but not its angle on most phones, the web,
 * the desktop — there is no hinge, and the table glides on its own clock.
 */
data class Hinge(val partiallyOpen: Boolean, val degrees: Float)

/** The hinge as the platform last reported it, or null where there is none. Written by the iOS host. */
object Hinges {
    val now: MutableState<Hinge?> = mutableStateOf(null)
}

/** The live hinge, for whatever on screen moves with it. A test provides its own. */
val LocalHinge = staticCompositionLocalOf<State<Hinge?>> { Hinges.now }
