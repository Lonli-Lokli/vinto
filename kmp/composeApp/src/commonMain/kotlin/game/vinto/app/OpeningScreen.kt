package game.vinto.app

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import game.vinto.app.theme.RailInk
import game.vinto.app.theme.RailInkDim
import game.vinto.app.theme.feltGradient

private const val BOB_MS = 900
private const val BOB_PX = -10f

/**
 * The moment before the game.
 *
 * On a cold start there is almost nothing to wait for — reading one saved game is a
 * synchronous read of a few kilobytes — so this is usually a single frame, and that is fine:
 * a screen that appears for one frame is not a cost, and its absence is a white flash. It
 * earns its place properly later, when the same slot has to cover reaching a room over a
 * network.
 *
 * The web app's version is a bouncing controller and a line of text. Same idea, same bounce.
 */
@Composable
fun OpeningScreen() {
    val bob = rememberInfiniteTransition(label = "opening")
    val lift by bob.animateFloat(
        initialValue = 0f,
        targetValue = BOB_PX,
        animationSpec = infiniteRepeatable(tween(BOB_MS), RepeatMode.Reverse),
        label = "bob",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(MaterialTheme.colorScheme.feltGradient())),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Gap),
        ) {
            Text(
                "🂡",
                fontSize = CardSize,
                color = RailInk,
                modifier = Modifier.graphicsLayer { translationY = lift },
            )
            Text("VINTO", fontSize = TitleSize, fontWeight = FontWeight.Black, color = RailInk)
            Text("Shuffling…", fontSize = BodySize, color = RailInkDim)
        }
    }
}

private val Gap = 10.dp
private val CardSize = 72.sp
private val TitleSize = 28.sp
private val BodySize = 15.sp
