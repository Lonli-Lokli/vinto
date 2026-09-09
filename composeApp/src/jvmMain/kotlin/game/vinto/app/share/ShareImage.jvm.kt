package game.vinto.app.share

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image

/** Skia's encoder, which Compose already carries on every target that is not Android. */
actual fun ImageBitmap.encodeToPngBytes(): ByteArray {
    val image = Image.makeFromBitmap(asSkiaBitmap())
    val data = image.encodeToData(EncodedImageFormat.PNG) ?: error("PNG encoding failed")
    return data.bytes
}
