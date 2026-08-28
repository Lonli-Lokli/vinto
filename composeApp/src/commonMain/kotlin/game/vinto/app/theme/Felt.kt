package game.vinto.app.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import kotlin.random.Random

/**
 * The weave of the cloth.
 *
 * A flat green rectangle with a gradient on it is a *colour*; baize is a woven thing, and the
 * difference at arm's length is almost entirely this — a barely-there speckle that breaks the
 * flatness up. At four per cent it is invisible as texture and unmistakable as material: turn
 * it off and the table looks printed.
 *
 * Built once into a 64-pixel tile and repeated by the GPU, so the per-frame cost is one
 * textured rect no matter how large the table is. Seeded, so it is the same weave every run
 * rather than a pattern that crawls between launches.
 */
@Composable
fun rememberFeltWeave(): Brush = remember {
    val tile = ImageBitmap(TILE, TILE)
    val canvas = Canvas(tile)
    val paint = Paint()
    val random = Random(WEAVE_SEED)

    repeat(TILE) { x ->
        repeat(TILE) { y ->
            // Half the pixels lift and half sink, so the weave changes the *texture* of the
            // felt underneath rather than its colour.
            val lift = random.nextFloat()
            paint.color = if (lift > HALF) {
                Color.White.copy(alpha = (lift - HALF) * STRENGTH)
            } else {
                Color.Black.copy(alpha = (HALF - lift) * STRENGTH)
            }
            canvas.drawRect(
                left = x.toFloat(),
                top = y.toFloat(),
                right = x + 1f,
                bottom = y + 1f,
                paint = paint,
            )
        }
    }

    ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated))
}

/**
 * The shadow a thing sitting on the cloth casts onto it.
 *
 * Cards, piles and name plates were drawn *on* the felt in the sense that a sticker is on a
 * wall — nothing underneath them said the table was a horizontal surface with a light above
 * it. A soft ellipse under each does, and costs one gradient.
 */
fun contactShadow(): Brush = Brush.radialGradient(
    colorStops = arrayOf(
        0f to Color.Black.copy(alpha = CONTACT),
        SHADOW_EDGE to Color.Black.copy(alpha = CONTACT * HALF),
        1f to Color.Transparent,
    ),
    center = Offset.Unspecified,
)

private const val TILE = 64
private const val WEAVE_SEED = 20260821
private const val HALF = 0.5f

/** How far the weave lifts and sinks. Four per cent, either way. */
private const val STRENGTH = 0.08f

private const val CONTACT = 0.28f
private const val SHADOW_EDGE = 0.6f
