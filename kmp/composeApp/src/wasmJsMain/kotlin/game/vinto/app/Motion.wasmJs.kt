package game.vinto.app

/**
 * The browser exposes `prefers-reduced-motion` via a `matchMedia` interop this port has not
 * grown yet; until it does, the in-app setting is the control. One function to fill in.
 */
actual fun systemPrefersReducedMotion(): Boolean = false
