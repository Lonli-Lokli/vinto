package game.vinto.app

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp

/**
 * What the system keeps for itself inside the window, beyond the plain edge insets.
 *
 * iOS 27.1 says so on the iPhone Duo (`reservedRegions`, handed over from `ContentView.swift`):
 * the **occlusions** are the clock, the connectivity and the camera, which stand in a column down
 * the closed phone's edge, in that column on the open one, and in the corner of the open phone held
 * upright; the **division** is the fold, while the phone is half open. An inset says only how wide
 * the column is; these say where in it the system's own things stand, so the table's controls can
 * start under the clock and stop short of the camera, and a half-open table can keep its cards off
 * the crease. Measured on the simulator, 2026-10-12: the clock and camera take the closed column's
 * top 150 points, the open column's top 100, and the corner of the open phone's 80-point top strip.
 *
 * In window coordinates, in dp — points, on iOS. Empty everywhere else, and in tests unless a test
 * provides its own ([LocalReserved]).
 */
data class Reserved(val occlusions: List<DpRect> = emptyList(), val division: DpRect? = null) {
    companion object {
        val NONE = Reserved()

        private const val RECT_PARTS = 4

        /**
         * Read back what the iOS host sends: each region `left,top,width,height` in points, regions
         * separated by `;`, an empty string for none. A string because it crosses into Swift and back
         * as one value, which a list of rectangles does not do cleanly; anything unreadable in it is
         * dropped rather than guessed at.
         */
        fun parse(occlusions: String, division: String): Reserved = Reserved(
            occlusions = rects(occlusions),
            division = rects(division).firstOrNull(),
        )

        private fun rects(encoded: String): List<DpRect> = encoded.split(';').mapNotNull { region ->
            val parts = region.split(',').map { it.trim().toFloatOrNull() }
            val numbers = parts.filterNotNull().takeIf { it.size == parts.size && it.size == RECT_PARTS }
                ?: return@mapNotNull null
            val (left, top) = numbers
            val (width, height) = numbers.drop(2)
            if (width <= 0f || height <= 0f) return@mapNotNull null
            DpRect(left.dp, top.dp, (left + width).dp, (top + height).dp)
        }
    }
}

/** What the platform last reported, [Reserved.NONE] where it reports nothing. Written by the iOS host. */
object ReservedRegions {
    val now: MutableState<Reserved> = mutableStateOf(Reserved.NONE)
}

/** The live reserved regions, for the screens that have to keep clear of them. A test provides its own. */
val LocalReserved = staticCompositionLocalOf<State<Reserved>> { ReservedRegions.now }
