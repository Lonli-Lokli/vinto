package game.vinto.app.share

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The picture that travels with the message: the code, and one line saying what it opens.
 *
 * A share is two halves and they arrive in different places. The *text* is what a person reads in
 * their chat app and what carries the link they can tap; the *picture* is what somebody across a
 * room can point a camera at, and it is the half that survives being screenshotted, printed, or
 * put on a slide. Sending only the text loses that; sending only the picture loses the tap.
 *
 * [caption] is not decoration. A bare QR arriving in a chat is a grey square that could be a
 * wifi password, and the one thing that makes somebody raise a camera to it is knowing where it
 * goes before they scan. It is the game's own words, so it translates with everything else.
 *
 * The card is drawn on the chip's own cream in both palettes, for the reason `QrChip` is: this
 * image ends up on somebody else's screen, under somebody else's theme, and a code inverted onto a
 * dark ground is a coin flip across scanner apps.
 */
@Composable
fun ShareQrCard(
    url: String,
    caption: String,
    modifier: Modifier = Modifier,
    logo: Painter? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().background(VintoQr.GROUND).padding(CardPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CardGap),
    ) {
        // Unbordered: the card around it is already the quiet zone and the light tile, so the
        // chip's own hairline would draw a second edge a few pixels inside the first.
        QrChip(url = url, logo = logo, size = CodeSize, label = caption, bordered = false)
        Text(
            text = caption,
            style = TextStyle(
                color = VintoQr.MODULE,
                fontSize = CaptionSize,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

/** How wide to lay the card out off-screen. The pixels come out at the device's own density. */
val ShareCardWidth = 300.dp

private val CodeSize = 232.dp
private val CardPadding = 20.dp
private val CardGap = 12.dp
private val CaptionSize = 15.sp
