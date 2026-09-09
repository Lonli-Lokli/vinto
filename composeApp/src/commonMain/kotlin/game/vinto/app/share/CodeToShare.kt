package game.vinto.app.share

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import game.vinto.app.sharePicture
import game.vinto.app.shareText
import kotlinx.coroutines.launch

/**
 * A button that sends a link and the code for it in one message.
 *
 * Both invitations in this app are the same errand — the room code on the lobby's invite sheet,
 * and the game itself under About — so they share the machinery and keep their own button: the
 * lobby's sits in a row of two and is compact, the settings one is full width. What is common is
 * the part that is easy to get subtly wrong, which is the order things are tried in.
 *
 * **The card is drawn before it is needed.** A `GraphicsLayer` records what it draws, so there has
 * to be something drawing: the card is laid out off-screen at real pixel size, at zero alpha, in a
 * host that reports no size to its parent. Rendering it on the tap instead would mean a frame
 * between the tap and the sheet, in which nothing has happened and the button looks broken.
 *
 * **Three steps down, and never off the end.** The picture is the better share; the text is the one
 * that has to happen; [onNoSheet] — the clipboard — is what a desktop gets, where there is no sheet
 * at all. A failure to encode drops to the text rather than to nothing, because a share button that
 * silently does nothing is the one outcome none of the three may produce.
 *
 * [modifier] applies to the pair, not to the button: the off-screen card measures 0 x 0, so the
 * box around them is exactly the button's size and a `Modifier.weight` from a caller's row still
 * means what it says.
 */
@Composable
fun CodeToShare(
    url: String,
    caption: String,
    subject: String,
    body: String,
    onNoSheet: () -> Unit,
    modifier: Modifier = Modifier,
    button: @Composable (send: () -> Unit) -> Unit,
) {
    val layer = rememberShareLayer()
    val logo = VintoQr.ringedMark()
    val scope = rememberCoroutineScope()

    Box(modifier) {
        button {
            scope.launch {
                val picture = runCatching { layer.toPngBytes() }.getOrNull()
                val sent = picture != null && sharePicture(subject, body, picture)
                if (!sent && !shareText(subject, body)) onNoSheet()
            }
        }

        OffscreenLayer(width = ShareCardWidth) {
            ShareQrCard(
                url = url,
                caption = caption,
                logo = logo,
                // Invisible to a camera AND to a screen reader. `alpha(0f)` only stops it being
                // seen: without clearing the semantics the card's own label and caption are two
                // more things read aloud on a settings screen, describing a picture nobody on
                // this device can look at.
                modifier = Modifier.alpha(0f).clearAndSetSemantics { }.captureInto(layer),
            )
        }
    }
}
