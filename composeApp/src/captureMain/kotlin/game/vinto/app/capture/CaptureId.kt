// Capture code (kupalinka-capture-a47d267ea8c1795fbb7df224): only in the capture build,
// See CLAUDE.md, "The capture build".
package game.vinto.app.capture

/**
 * The handle key zdymak passes (Android: the intent extra `--es <CAPTURE_ID> <scene>`; iOS: the
 * launch argument `-<CAPTURE_ID> <scene>`), and the id Scripts/capture-check.mjs looks for in every
 * packaged build: the same string in every app of the portfolio, held in no store build.
 */
const val CAPTURE_ID = "kupalinka-capture-a47d267ea8c1795fbb7df224"
