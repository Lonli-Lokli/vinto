package game.vinto.app

/**
 * The release build's answer: there is no rig.
 *
 * Not a disabled one — an absent one. See the debug twin for what it is and why the pairing is a
 * build-variant gate rather than a runtime check.
 */
@Suppress("FunctionOnlyReturningConstant")
internal fun lastBotCallsVinto(): Boolean = false
