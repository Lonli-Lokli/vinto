package game.vinto.app.share

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import java.io.ByteArrayOutputStream

/**
 * Android's own encoder.
 *
 * `compress` rather than reading the pixels out by hand, because a layer captured on API 26 and
 * above comes back as a hardware bitmap — `getPixels` throws on one of those and `compress` does
 * not.
 */
actual fun ImageBitmap.encodeToPngBytes(): ByteArray {
    val out = ByteArrayOutputStream()
    asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out)
    return out.toByteArray()
}

/** PNG is lossless, so this is ignored — the platform still insists on a number. */
private const val PNG_QUALITY = 100
