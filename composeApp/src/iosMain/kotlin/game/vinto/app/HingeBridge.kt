package game.vinto.app

/**
 * The hinge, as iOS reports it — called from Swift's `onHingeChange` in `ContentView.swift`, on
 * iOS 27.1 and later, as the iPhone Duo opens and closes. What moves with it is in [Hinge].
 *
 * Top-level functions so Swift reaches them as `HingeBridgeKt.hingeMoved(…)`; renaming either side
 * breaks the other, and nothing checks that but a build of the app.
 */
fun hingeMoved(partiallyOpen: Boolean, degrees: Double) {
    Hinges.now.value = Hinge(partiallyOpen = partiallyOpen, degrees = degrees.toFloat())
}

/** The phone has no hinge, or has stopped reporting one. */
fun hingeGone() {
    Hinges.now.value = null
}
