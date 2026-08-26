package game.vinto.app.theme

import android.media.AudioAttributes
import android.media.SoundPool
import game.vinto.app.AndroidStorage
import java.io.File

/**
 * Android audio: a [SoundPool], which is the platform's short-latency answer for exactly
 * this — small clips fired during interaction. It has no byte-array loader, so each WAV is
 * written once to the app's cache directory and loaded from there; the cache is the right
 * home because losing these files costs nothing but a rewrite.
 */
actual class SoundPlayer actual constructor() {
    private val pool = SoundPool.Builder()
        .setMaxStreams(MAX_STREAMS)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val ids = mutableMapOf<Sfx, Int>()

    actual fun load(sfx: Sfx, bytes: ByteArray, uri: String) {
        val dir = AndroidStorage.context?.cacheDir ?: return
        runCatching {
            val file = File(dir, "sfx-${sfx.file}")
            if (!file.exists() || file.length() != bytes.size.toLong()) file.writeBytes(bytes)
            ids[sfx] = pool.load(file.path, 1)
        }
    }

    actual fun play(sfx: Sfx) {
        ids[sfx]?.let { pool.play(it, VOLUME, VOLUME, 1, 0, 1f) }
    }

    actual fun dispose() {
        pool.release()
        ids.clear()
    }

    private companion object {
        /** A deal wave can overlap a landing; three is plenty for four sounds this short. */
        const val MAX_STREAMS = 3
        const val VOLUME = 1f
    }
}
