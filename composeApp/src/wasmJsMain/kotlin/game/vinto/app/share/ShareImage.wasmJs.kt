package game.vinto.app.share

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image

/**
 * Skia again, this time the one compiled into `skiko.wasm`.
 *
 * Worth checking rather than assuming, because a browser build is exactly where a codec gets left
 * out to save bytes: libpng's *write* path (`png_write_info`, `png_write_row`) is in the shipped
 * binary, so this is the same encoder the desktop and iOS use rather than a hopeful call.
 */
actual fun ImageBitmap.encodeToPngBytes(): ByteArray {
    val image = Image.makeFromBitmap(asSkiaBitmap())
    val data = image.encodeToData(EncodedImageFormat.PNG) ?: error("PNG encoding failed")
    return data.bytes
}
