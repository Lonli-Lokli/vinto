package game.vinto.app

/**
 * Whether the platform is telling us, on the player's behalf, not to count anything.
 *
 * Global Privacy Control and Do-Not-Track are the two signals that exist. Where a platform
 * offers one, it is read *before the first event* rather than used to filter afterwards, and
 * it is not a suggestion: a positive signal means nothing is sent at all, not that less is.
 * There is no reduced mode, because "do not track me" is not an invitation to send less.
 *
 * A platform that exposes neither answers `false`, and the in-app opt-out in Settings is the
 * control that serves those players. That is the same arrangement as
 * [systemPrefersReducedMotion], for the same reason: an unanswerable question should default
 * to the app's own setting rather than to silence nobody chose.
 */
expect fun platformObjectsToTracking(): Boolean
