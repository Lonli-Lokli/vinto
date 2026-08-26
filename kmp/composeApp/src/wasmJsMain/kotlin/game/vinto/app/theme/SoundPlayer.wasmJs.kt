// Detekt strips the .jvm/.android/.ios platform suffixes before matching the file name to
// its declaration, but does not know .wasmJs; the name is right, the rule's list is short.
@file:Suppress("MatchingDeclarationName")

package game.vinto.app.theme

import org.w3c.dom.Audio

/**
 * Browser audio: an `Audio` element per sound, created from the resource's own URL — the
 * bytes are already being served, so streaming them again through a decoder would be work
 * for no gain. `play()` returns a promise the browser may reject before the first user
 * gesture; that refusal is ignored on purpose, because the first tap unlocks audio and a
 * game's first sound never precedes its first tap.
 */
actual class SoundPlayer actual constructor() {
    private val players = mutableMapOf<Sfx, Audio>()

    actual fun load(sfx: Sfx, bytes: ByteArray, uri: String) {
        runCatching { players[sfx] = Audio(uri) }
    }

    actual fun play(sfx: Sfx) {
        players[sfx]?.let {
            it.currentTime = 0.0
            it.play()
        }
    }

    actual fun dispose() {
        players.clear()
    }
}
