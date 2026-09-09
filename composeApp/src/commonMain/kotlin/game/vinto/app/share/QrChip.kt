package game.vinto.app.share

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import game.vinto.app.art.Res
import game.vinto.app.art.vinto_mark
import game.vinto.app.theme.Felt
import io.github.alexzhirkevich.qrose.options.QrBallShape
import io.github.alexzhirkevich.qrose.options.QrBrush
import io.github.alexzhirkevich.qrose.options.QrColors
import io.github.alexzhirkevich.qrose.options.QrErrorCorrectionLevel
import io.github.alexzhirkevich.qrose.options.QrFrameShape
import io.github.alexzhirkevich.qrose.options.QrLogo
import io.github.alexzhirkevich.qrose.options.QrLogoPadding
import io.github.alexzhirkevich.qrose.options.QrLogoShape
import io.github.alexzhirkevich.qrose.options.QrPixelShape
import io.github.alexzhirkevich.qrose.options.QrShapes
import io.github.alexzhirkevich.qrose.options.roundCorners
import io.github.alexzhirkevich.qrose.options.solid
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import org.jetbrains.compose.resources.painterResource

/**
 * The portfolio's scan-to-play chip: a branded QR on a cream tile, with Vinto's mark inside it.
 *
 * **Ported, not invented.** Every other game draws this from `games.core.share.QrChip` in gulnya,
 * and this repository has no games-core dependency — so the code is here and the RULES are the
 * ones that library already paid for. Each of them is a way to ship a pretty code nobody can scan:
 *
 * - **A logo needs the modules behind it cleared AND High error correction**, or the code stops
 *   reading on a phone held at arm's length in bad light. Natural padding plus High is the pair
 *   that survives; one without the other is a code that looks right and fails in a lobby.
 * - **The module colour is brand, but it is also contrast.** Scanners threshold luminance and do
 *   not care about hue, so this takes the DEEP tone and never the bright one: Vinto's [Felt] is
 *   6.6:1 on the chip's ground, while the `Brand` green it uses on a dark board is **1.7:1** and
 *   would not scan at all. `QrContrastTest` holds that rather than leaving it to the eye.
 * - **The ground is a fixed cream, not the theme surface.** A QR inverted onto a dark card is a
 *   coin flip across scanner apps, so the chip carries its own light tile in both palettes.
 *
 * What varies per use: the url, the label, and the size. Everything else is the same object
 * everywhere, which is the point of it being one.
 */
@Composable
fun QrChip(
    url: String,
    modifier: Modifier = Modifier,
    logo: Painter? = null,
    module: Color = VintoQr.MODULE,
    ground: Color = VintoQr.GROUND,
    size: Dp = 112.dp,
    label: String? = null,
    /**
     * Draw a hairline of [module] around the tile.
     *
     * The chip's cream sits on a cream rail in the light palette, where without an edge the code
     * bleeds into the card behind it. The rule is also the quiet-zone boundary, so it tells a
     * camera where the code stops.
     */
    bordered: Boolean = true,
) {
    val mark = logo?.let {
        QrLogo(
            painter = it,
            size = LOGO_FRACTION,
            padding = QrLogoPadding.Natural(LOGO_PADDING),
            shape = QrLogoShape.roundCorners(LOGO_CORNER),
        )
    } ?: QrLogo()
    val qr = rememberQrCodePainter(
        data = url,
        shapes = QrShapes(
            darkPixel = QrPixelShape.roundCorners(radius = 0.5f),
            ball = QrBallShape.roundCorners(radius = 0.25f),
            frame = QrFrameShape.roundCorners(corner = 0.25f),
        ),
        colors = QrColors(dark = QrBrush.solid(module), frame = QrBrush.solid(module)),
        logo = mark,
        // A cleared centre costs redundancy; High buys it back. Without a logo, Auto is plenty.
        errorCorrectionLevel = if (logo != null) QrErrorCorrectionLevel.High else QrErrorCorrectionLevel.Auto,
    )
    val shape = RoundedCornerShape(TILE_CORNER)
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(ground)
            .then(if (bordered) Modifier.border(BORDER, module, shape) else Modifier)
            .padding(QUIET_ZONE)
            .semantics { label?.let { contentDescription = it } },
    ) {
        Image(qr, contentDescription = null, modifier = Modifier.fillMaxSize())
    }
}

/** Vinto's half of the chip: which colours it is drawn in, and what sits in the middle. */
object VintoQr {

    /**
     * The felt, which is the brand's deep tone and the only Vinto colour that scans on cream.
     *
     * Read off the theme rather than copied, so a repaint of the felt cannot leave a code behind
     * in last year's green.
     */
    val MODULE: Color get() = Felt

    /**
     * The chip's own tile — light in BOTH palettes.
     *
     * The same cream as every other game's chip (`games.core.share.DEFAULT_GROUND`), because a
     * scannable code is the same rectangle everywhere and a player who has scanned one of these
     * has scanned all of them.
     */
    val GROUND = Color(0xFFF3EDE1)

    /**
     * The app's mark, ringed, ready to sit in the middle of a code.
     *
     * A logo dropped into a QR reads as damage: qrose clears the modules behind it, so the mark
     * ends up floating in a ragged hole with the code's own pixels crowding its corners. A ring in
     * the module colour closes that hole deliberately — the mark becomes a framed token and the
     * hole becomes its mount. Drawn INSIDE the logo painter rather than overlaid on the chip, so
     * qrose places the ring exactly where it places the mark; an overlay would have to re-derive
     * the library's geometry and would drift the moment [LOGO_FRACTION] changed.
     */
    @Composable
    fun ringedMark(): Painter {
        val mark = painterResource(Res.drawable.vinto_mark)
        val ring = MODULE
        val fill = GROUND
        return remember(mark, ring, fill) { RingedMark(mark, ring, fill) }
    }
}

private class RingedMark(
    private val mark: Painter,
    private val ring: Color,
    private val fill: Color,
) : Painter() {

    override val intrinsicSize: Size get() = Size.Unspecified

    override fun DrawScope.onDraw() {
        val side = size.minDimension
        val corner = CornerRadius(side * MARK_CORNER, side * MARK_CORNER)
        val stroke = side * RING_WIDTH
        // The tile first: the cleared area is the code's ground showing through, and painting it
        // explicitly means the ring always closes onto a surface rather than onto pixels.
        drawRoundRect(fill, size = Size(side, side), cornerRadius = corner)
        drawRoundRect(
            ring,
            topLeft = Offset(stroke / 2f, stroke / 2f),
            size = Size(side - stroke, side - stroke),
            cornerRadius = corner,
            style = Stroke(stroke),
        )
        inset(stroke * MARK_INSET) {
            with(mark) { draw(Size(size.minDimension, size.minDimension)) }
        }
    }
}

/**
 * The floor a module colour must clear against its ground.
 *
 * Not a WCAG number — nobody reads a QR — but scanners binarize the image, and below roughly this
 * the two states stop separating under a phone camera's auto-exposure.
 */
const val MIN_SCAN_CONTRAST = 4.5

private val BORDER = 1.dp
private val TILE_CORNER = 12.dp

/** The white margin a code needs around it; without one, scanners hunt for the finder patterns. */
private val QUIET_ZONE = 8.dp

private const val LOGO_FRACTION = 0.30f
private const val LOGO_PADDING = 0.16f
private const val LOGO_CORNER = 0.28f
private const val MARK_CORNER = 0.26f
private const val RING_WIDTH = 0.085f
private const val MARK_INSET = 1.6f
