package game.vinto.app.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * What the phone does that the screen cannot.
 *
 * A card table's other half is physical — a card has a weight, a chip has a click — and a game
 * played through glass has none of it. Three kicks, and no more than three: touching something,
 * committing to something, and being told no. A game that buzzes at every event teaches the
 * player to turn it off, and then the one buzz that mattered is gone with the rest.
 *
 * Off is a real answer, and it is one setting away. Everything here is silent when it is.
 */
class Feedback(private val haptics: HapticFeedback?, private val enabled: Boolean) {

    /** A card, a chip, a choice: something under the thumb has responded. */
    fun touch() = fire(HapticFeedbackType.SegmentTick)

    /** The move is gone to the engine. */
    fun commit() = fire(HapticFeedbackType.Confirm)

    /** A rule bit: a penalty card landing in your own hand, or a refusal. */
    fun refuse() = fire(HapticFeedbackType.Reject)

    private fun fire(type: HapticFeedbackType) {
        if (enabled) haptics?.performHapticFeedback(type)
    }
}

/**
 * The feedback in force.
 *
 * Silent by default so that anything drawn outside the app — a test, a preview, a screenshot
 * harness — is silent without having to say so.
 */
val LocalFeedback = staticCompositionLocalOf { Feedback(haptics = null, enabled = false) }

/** The platform's haptics, obeying the player's setting. */
@Composable
fun rememberFeedback(enabled: Boolean): Feedback {
    val haptics = LocalHapticFeedback.current
    return remember(haptics, enabled) { Feedback(haptics, enabled) }
}
