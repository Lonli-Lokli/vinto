package game.vinto.app.theme

import java.io.ByteArrayInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip

/**
 * Desktop audio: one [Clip] per sound, decoded once from the WAV bytes. `javax.sound` has
 * been in the JDK since before this game's rules were written; the only care it needs is
 * rewinding a clip before replaying it.
 */
actual class SoundPlayer actual constructor() {
    private val clips = mutableMapOf<Sfx, Clip>()

    actual fun load(sfx: Sfx, bytes: ByteArray, uri: String) {
        runCatching {
            val stream = AudioSystem.getAudioInputStream(ByteArrayInputStream(bytes))
            val clip = AudioSystem.getClip()
            clip.open(stream)
            clips[sfx] = clip
        }
    }

    actual fun play(sfx: Sfx) {
        val clip = clips[sfx] ?: return
        runCatching {
            if (clip.isRunning) clip.stop()
            clip.framePosition = 0
            clip.start()
        }
    }

    actual fun dispose() {
        clips.values.forEach { runCatching { it.close() } }
        clips.clear()
    }
}
