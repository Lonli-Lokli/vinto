package game.vinto.app.theme

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.AVFAudio.AVAudioPlayer
import platform.Foundation.NSData
import platform.Foundation.dataWithBytes

/**
 * iOS audio: one [AVAudioPlayer] per sound, fed the WAV bytes as `NSData` and prepared once
 * so the first play is not also the first decode.
 */
@OptIn(ExperimentalForeignApi::class)
actual class SoundPlayer actual constructor() {
    private val players = mutableMapOf<Sfx, AVAudioPlayer>()

    actual fun load(sfx: Sfx, bytes: ByteArray, uri: String) {
        if (bytes.isEmpty()) return
        runCatching {
            val data = bytes.usePinned { pinned ->
                NSData.dataWithBytes(pinned.addressOf(0), bytes.size.toULong())
            }
            val player = AVAudioPlayer(data = data, error = null)
            player.prepareToPlay()
            players[sfx] = player
        }
    }

    actual fun play(sfx: Sfx) {
        players[sfx]?.let {
            it.currentTime = 0.0
            it.play()
        }
    }

    actual fun dispose() {
        players.values.forEach { it.stop() }
        players.clear()
    }
}
