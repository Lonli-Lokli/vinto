package game.vinto.app.crash

import game.vinto.engine.PlayerView

/**
 * The last table the app drew, kept for a crash report and for nothing else.
 *
 * A plain holder rather than a `CompositionLocal`, deliberately. A composition local carrying
 * a value that changes every turn would recompose whatever reads it, and the only reader is
 * the crash handler at the root of the app — so the cost would be a whole-tree recomposition
 * per turn to serve a side channel that fires at most once per process. This is written from
 * the one place both a local game and an online one pass through, and read from a `catch`.
 *
 * It holds nothing identifying (see [CrashPlace]) and it is cleared when a table is left, so
 * a crash in the menu is not filed against the game before it.
 */
object Where {
    private var place = CrashPlace()

    /** Called as the table draws. Null clears it — leaving a game is leaving its address. */
    fun atTable(view: PlayerView?) {
        place = if (view == null) {
            CrashPlace()
        } else {
            CrashPlace(gameId = view.gameId, round = view.roundNumber, turn = view.turnNumber)
        }
    }

    fun now(): CrashPlace = place
}
