package game.vinto.app.game

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import game.vinto.app.art.Res
import game.vinto.app.art.avatar_donatello
import game.vinto.app.art.avatar_michelangelo
import game.vinto.app.art.avatar_raphael
import game.vinto.app.art.avatar_you
import game.vinto.app.theme.Signal
import game.vinto.app.theme.Slate
import game.vinto.app.theme.onFelt
import game.vinto.client.Attention
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

private val PlatePad = 4.dp
private val PlateGap = 8.dp
private val NamePad = 10.dp
private val NameMax = 76.dp
private val Hairline = 1.dp
private val Ring = 2.dp

private const val GLOW_LOW = 0.35f
private const val GLOW_HIGH = 1f
private const val GLOW_MS = 1200
private const val QUIET = 0.45f

/** What the table is saying about a seat, in one colour. */
private fun Attention.colour(): Color = when (this) {
    Attention.TURN -> Signal.turn
    Attention.VINTO -> Signal.vinto
    Attention.PENALTY -> Signal.penalty
    Attention.COALITION -> Signal.coalition
}

/**
 * A player: portrait and name in one pill, as on the web table.
 *
 * The two together rather than a portrait with a caption under it — it is a name plate, it
 * reads as one object, and it takes half the vertical room, which on a phone with four hands
 * to fit is the difference between a table and a list.
 *
 * The seat whose turn it is glows. On a table where three of the four players are bots taking
 * their turns in under a second, a static highlight is easy to miss, and the player loses
 * track of whether the game is waiting on them.
 */
@Composable
fun SeatPlate(
    name: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    marks: String? = null,
    pointed: Attention? = null,
    size: Dp = 40.dp,
    onClick: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme

    val pulse = rememberInfiniteTransition(label = "seat")
    val glow by pulse.animateFloat(
        initialValue = GLOW_LOW,
        targetValue = GLOW_HIGH,
        animationSpec = infiniteRepeatable(
            animation = tween(GLOW_MS, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow",
    )

    val edge by animateColorAsState(
        when {
            // Being pointed at wins over everything: it is the table saying *this* seat, now
            // — the one drawing a penalty, the one who called Vinto, the one leading.
            pointed != null -> pointed.colour()
            onClick != null -> Slate.gold
            active -> Slate.gold.copy(alpha = glow)
            else -> scheme.onFelt().copy(alpha = QUIET)
        },
        label = "edge",
    )

    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = Slate.fill.copy(alpha = PLATE_ALPHA),
        border = BorderStroke(if (active || onClick != null) Ring else Hairline, edge),
        onClick = onClick ?: {},
        enabled = onClick != null,
    ) {
        Row(
            modifier = Modifier.padding(PlatePad),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PlateGap),
        ) {
            Image(
                painter = painterResource(portraitFor(name)),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .border(Hairline, edge, CircleShape),
            )
            // Capped, and the name gives way before the marks do. A plate that grows with
            // "Vinto · 12" is a plate that pushes the player's own hand onto a second row,
            // which is the one hand that has to stay in one piece.
            Column(modifier = Modifier.padding(end = NamePad).widthIn(max = NameMax)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    color = if (active) Slate.gold else Slate.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                marks?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = Slate.gold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * The portrait for a seat.
 *
 * Keyed on the name the engine deals, which is fixed: `initializeGame` always seats You,
 * Raphael, Michelangelo and Donatello in that order. Online the seats are people with their
 * own nicknames, and this will need a real mapping — one more reason it is a single function.
 */
private fun portraitFor(name: String): DrawableResource = when {
    name.startsWith("Raph") -> Res.drawable.avatar_raphael
    name.startsWith("Mikey") || name.startsWith("Michel") -> Res.drawable.avatar_michelangelo
    name.startsWith("Don") -> Res.drawable.avatar_donatello
    else -> Res.drawable.avatar_you
}

private const val PLATE_ALPHA = 0.9f

/** Kept for the one place a bare portrait is still wanted: choosing a player for an Ace. */
@Composable
fun Avatar(name: String, size: Dp, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(portraitFor(name)),
        contentDescription = name,
        contentScale = ContentScale.Crop,
        modifier = modifier.size(size).clip(CircleShape),
    )
}
