package game.vinto.app

/**
 * The debug build's answer: yes, rig the last bot to call Vinto.
 *
 * **This file has a no-op twin in `src/release`**, and the pairing is the whole of the gate — the
 * same shape `captureScene` uses next door, and for the same reason. A runtime
 * `if (BuildConfig.DEBUG)` would leave the rig in the shipped binary for somebody to find; a
 * build-variant gate means the release build does not contain one at all.
 *
 * What it buys: the coalition's final round is the part of this game hardest to reach on purpose.
 * It needs a bot to *decide* to call, which needs a good hand and a search that agrees, so getting
 * there means playing rounds and hoping — and the round you get by playing is usually the one you
 * called yourself, which is the other side of the table. With this on, the last bot calls the
 * moment its turn comes and the person is first in the coalition.
 *
 * It reaches only the **local** game: `LocalGameSession` takes the flag, and a room deals its own
 * bots. Nothing else about the app changes — it still starts cold on the home screen, and a round
 * is still only picked up where one was left.
 */
@Suppress("FunctionOnlyReturningConstant")
internal fun lastBotCallsVinto(): Boolean = true
