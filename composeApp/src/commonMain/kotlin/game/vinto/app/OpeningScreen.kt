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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import game.vinto.app.art.Res
import game.vinto.app.art.app_name
import game.vinto.app.art.opening_shuffling
import game.vinto.app.game.CardFace
import game.vinto.app.game.CardScale
import game.vinto.app.theme.Wordmark
import game.vinto.app.theme.feltGold
import game.vinto.app.theme.feltGradient
import game.vinto.app.theme.onFelt
import game.vinto.engine.CardView
import org.jetbrains.compose.resources.stringResource

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
    val still = LocalReducedMotion.current
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
            // The deck's own back, from the artwork every card on the table is drawn with. It was
            // the playing-card character, which each phone drew from its own fonts: a picture on
            // one, an outline on another, a box on an old Android (owner, 2026-10-11).
            Box(
                // Still for a reader who asked for less motion, and silent: a picture of a card,
                // which says nothing the wordmark under it does not.
                modifier = Modifier
                    .graphicsLayer { translationY = if (still) 0f else lift }
                    .clearAndSetSemantics { },
            ) {
                CardFace(card = CardView.Hidden, scale = SplashCard)
            }
            Text(
                text = stringResource(Res.string.app_name),
                fontSize = TitleSize,
                fontFamily = Wordmark,
                fontWeight = FontWeight.Bold,
                // The same gold leaf the home screen sets it in.
                color = MaterialTheme.colorScheme.feltGold(),
            )
            Text(
                text = stringResource(Res.string.opening_shuffling),
                fontSize = BodySize,
                color = MaterialTheme.colorScheme.onFelt().copy(alpha = Quiet),
            )
        }
    }
}

private val Gap = 10.dp

/** The card above the wordmark, at the size a phone draws the player's own hand. */
private val SplashCard = CardScale(width = 56.dp, height = 78.dp)

private val TitleSize = 28.sp
private val BodySize = 15.sp

/** The line under the wordmark: present, and not competing with it. */
private const val Quiet = 0.75f
