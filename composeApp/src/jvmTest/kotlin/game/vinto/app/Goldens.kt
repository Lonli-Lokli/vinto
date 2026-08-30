package game.vinto.app

import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.test.fail

/**
 * The golden images: what the screens looked like when somebody last said they were right.
 *
 * A golden test is a tripwire, not a judge. It cannot say a screen is *good* — that was
 * decided by the person who accepted the golden — only that it has changed, which is the one
 * thing a refactor promises not to do and the one thing no assertion-based test notices.
 *
 * The protocol is the usual one:
 *
 * - **No golden yet** → the rendering is written as the golden and the test passes with a
 *   warning. Run twice: once to write, once to prove the rendering is stable.
 * - **Mismatch** → the test fails and the rendering is written beside the golden as
 *   `<name>.actual.png`, so the two can be eyeballed. Deleting the golden accepts the change.
 *
 * The comparison carries a small tolerance because text antialiasing is allowed to differ by
 * a hair between JVMs; a real change moves whole regions, not a fringe of glyph edges.
 */
object Goldens {

    /** Renders are compared against, or become, `<dir>/<name>.png`. */
    fun check(name: String, image: Image) {
        val dir = directory()
        val golden = File(dir, "$name.png")
        val bytes = image.encodeToData(EncodedImageFormat.PNG)?.bytes
            ?: fail("$name: the rendering would not encode as PNG")

        if (!golden.exists()) {
            dir.mkdirs()
            golden.writeBytes(bytes)
            println("golden written: ${golden.path} — run again to verify it is stable")
            return
        }

        val quarrel = mismatch(Image.makeFromEncoded(golden.readBytes()), image)
        val beside = File(dir, "$name.actual.png")
        if (quarrel != null) {
            beside.writeBytes(bytes)
            fail(
                "$name: $quarrel. The rendering is beside the golden as ${beside.name}; " +
                    "if the change is intended, delete the golden and run twice.",
            )
        }
        // A stale .actual from a failure since fixed would sit there implying one.
        beside.delete()
    }

    /** What is different, in words, or null for a match within tolerance. */
    private fun mismatch(golden: Image, actual: Image): String? {
        if (golden.width != actual.width || golden.height != actual.height) {
            return "the size changed: ${golden.width}x${golden.height} → " +
                "${actual.width}x${actual.height}"
        }

        val want = golden.toComposeImageBitmap().toPixelMap()
        val got = actual.toComposeImageBitmap().toPixelMap()
        var differing = 0
        for (y in 0 until want.height) {
            for (x in 0 until want.width) {
                val a = want[x, y]
                val b = got[x, y]
                val delta = max(
                    max(abs(a.red - b.red), abs(a.green - b.green)),
                    max(abs(a.blue - b.blue), abs(a.alpha - b.alpha)),
                )
                if (delta * CHANNEL_SCALE > CHANNEL_TOLERANCE) differing++
            }
        }

        val allowed = (want.width * want.height * ALLOWED_FRACTION).toInt()
        if (differing <= allowed) return null
        return "$differing of ${want.width * want.height} pixels moved " +
            "(up to $allowed pass as antialiasing)"
    }

    /**
     * `src/jvmTest/goldens`, found from wherever Gradle put the working directory — the
     * module dir on a plain test run, but nothing here should break if that changes.
     */
    private fun directory(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            val here = File(dir, "src/jvmTest/kotlin/game/vinto/app")
            if (here.isDirectory) return File(dir, "src/jvmTest/goldens")
            val below = File(dir, "composeApp/src/jvmTest/kotlin/game/vinto/app")
            if (below.isDirectory) return File(dir, "composeApp/src/jvmTest/goldens")
            dir = dir.parentFile
        }
        fail("could not find composeApp from ${System.getProperty("user.dir")}")
    }

    /** Channel values are floats; the tolerance is stated in eight-bit steps. */
    private const val CHANNEL_SCALE = 255f

    /** Two eight-bit steps: past any rounding, far under any visible change. */
    private const val CHANNEL_TOLERANCE = 2f

    /** One pixel in a thousand may sit on a glyph edge that antialiases differently. */
    private const val ALLOWED_FRACTION = 0.001
}
