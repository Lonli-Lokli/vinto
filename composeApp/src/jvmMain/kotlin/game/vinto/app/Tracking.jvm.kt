package game.vinto.app

/** No desktop-wide signal worth trusting; the in-app setting is the control here. */
actual fun platformObjectsToTracking(): Boolean = false
