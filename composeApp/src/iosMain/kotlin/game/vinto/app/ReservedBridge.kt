package game.vinto.app

/**
 * The regions iOS reserves in the window — called from Swift in `ContentView.swift`, on iOS 27.1 and
 * later, whenever they change: on launch, as the phone opens, closes, turns, or folds half way.
 * What they mean is in [Reserved], and the encoding is [Reserved.parse]'s.
 *
 * A top-level function so Swift reaches it as `ReservedBridgeKt.reservedChanged(…)`; renaming either
 * side breaks the other, and nothing checks that but a build of the app.
 */
fun reservedChanged(occlusions: String, division: String) {
    ReservedRegions.now.value = Reserved.parse(occlusions, division)
}
