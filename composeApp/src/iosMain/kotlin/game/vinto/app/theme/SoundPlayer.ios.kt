package game.vinto.app.theme

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryAmbient
import platform.Foundation.NSData
import platform.Foundation.dataWithBytes

/**
 * Lets the game's sounds play over the player's music rather than stopping it.
 *
 * An app that never names an audio category gets iOS's default, solo ambient, which silences
 * every other app's audio the moment it plays anything: the first card sound stopped whatever
 * the player was listening to, and it stayed stopped. Ambient mixes with what is already playing
 * and still obeys the silent switch and the screen lock, which is what a card game's clicks
 * should do. Android needs nothing equivalent: it interrupts other audio only for an app that
 * asks for audio focus, and [SoundPlayer] there never does.
 *
 * The category and nothing more. `setActive` is the call that can block, for long enough to
 * show on a launch, and it is not needed: the first `play()` activates the session itself, under
 * the category named here. Called from `MainViewController`, before anything can make a sound;
 * a device that refuses keeps the default and loses only the mixing, so the result is not read.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun mixWithOtherAudio() {
    AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryAmbient, null)
}

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
