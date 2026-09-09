package game.vinto.app.share

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp

/**
 * Turning a composable into PNG bytes, so a picture can leave the device with the words.
 *
 * **Ported, not invented** — the same shape as `games.core.share.ShareImage` in gulnya, which every
 * other game in the portfolio shares its cards with, and which this repository cannot depend on
 * (it has no games-core dependency). What is new here is the wasmJs actual: gulnya has no web
 * target, and this app does.
 *
 * The capture is Compose's own `GraphicsLayer` snapshot rather than anything platform-shaped:
 * record the composable into a layer while it draws, then ask the layer for an `ImageBitmap`. Only
 * the last step needs a platform, and only because PNG encoders are not portable.
 *
 * Usage:
 * ```
 * val layer = rememberShareLayer()
 * OffscreenLayer(width = 320.dp) { ShareQrCard(url, caption, Modifier.alpha(0f).captureInto(layer)) }
 * // then, on a tap, inside a coroutine:
 * sharePicture(subject, body, layer.toPngBytes())
 * ```
 */

/** Encode a captured [ImageBitmap] to PNG bytes. Platform actuals beside this file. */
expect fun ImageBitmap.encodeToPngBytes(): ByteArray

/** A graphics layer to record the share card into. */
@Composable
fun rememberShareLayer(): GraphicsLayer = rememberGraphicsLayer()

/** Record the composable into [layer] while still drawing it normally. */
fun Modifier.captureInto(layer: GraphicsLayer): Modifier = drawWithContent {
    layer.record { this@drawWithContent.drawContent() }
    drawLayer(layer)
}

/** Snapshot the recorded layer to PNG bytes. Call from a coroutine. */
suspend fun GraphicsLayer.toPngBytes(): ByteArray = toImageBitmap().encodeToPngBytes()

/**
 * Hosts an off-screen [captureInto] source: lays [content] out at exactly [width] with its natural
 * height and draws it, but reports ZERO size to its parent — so the card takes no room on screen
 * while the recorded layer still has real pixel dimensions.
 *
 * `Box(Modifier.size(0.dp))` instead gives the layer a 0px height, and `toImageBitmap()` then
 * throws "width & height must be > 0". Keep the card at `Modifier.alpha(0f)`, so the overflowing
 * off-screen draw stays invisible while the recorded layer keeps full opacity.
 */
@Composable
fun OffscreenLayer(width: Dp, content: @Composable () -> Unit) {
    Layout(content = content) { measurables, _ ->
        val w = width.roundToPx()
        val placeables = measurables.map { it.measure(Constraints(minWidth = w, maxWidth = w)) }
        layout(0, 0) { placeables.forEach { it.place(0, 0) } }
    }
}
