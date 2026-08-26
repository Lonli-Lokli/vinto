package game.vinto.app

import platform.UIKit.UIAccessibilityIsReduceMotionEnabled

/** iOS says it outright: Settings → Accessibility → Motion → Reduce Motion. */
actual fun systemPrefersReducedMotion(): Boolean = UIAccessibilityIsReduceMotionEnabled()
