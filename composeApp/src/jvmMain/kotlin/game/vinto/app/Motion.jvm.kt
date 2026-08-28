package game.vinto.app

/** No desktop-wide preference worth trusting; the in-app setting is the control here. */
actual fun systemPrefersReducedMotion(): Boolean = false
