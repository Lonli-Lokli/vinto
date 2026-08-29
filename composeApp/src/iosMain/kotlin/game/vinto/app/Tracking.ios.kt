package game.vinto.app

/**
 * iOS retired its app-level tracking flag in favour of App Tracking Transparency, which is
 * about tracking a person *across other companies' apps* — something Vinto does not do and
 * has no framework for. Asking for that permission to justify counting rounds would be asking
 * a question whose honest answer is "we are not doing the thing you are being asked about".
 *
 * So: nothing to read, and the in-app opt-out is the control.
 */
actual fun platformObjectsToTracking(): Boolean = false
