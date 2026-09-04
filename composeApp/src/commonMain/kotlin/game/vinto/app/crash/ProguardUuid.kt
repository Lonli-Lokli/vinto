package game.vinto.app.crash

/**
 * The R8 mapping id, which only Android has.
 *
 * R8 is an Android tool: iOS symbolicates from a dSYM keyed by instruction address, the desktop
 * run is not minified, and the web build has its own story. Only Android needs a mapping named in
 * `debug_meta`, so everywhere else this is honestly null rather than absent.
 */
internal expect fun proguardUuid(): String?
