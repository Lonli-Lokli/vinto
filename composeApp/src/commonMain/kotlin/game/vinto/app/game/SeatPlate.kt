package game.vinto.app.game

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import game.vinto.app.art.Res
import game.vinto.app.art.avatar_donatello
import game.vinto.app.art.avatar_michelangelo
import game.vinto.app.art.avatar_raphael
import game.vinto.app.art.avatar_you
import game.vinto.app.art.seat_is_a_bot
import game.vinto.app.art.seat_pointed_coalition
import game.vinto.app.art.seat_pointed_penalty
import game.vinto.app.art.seat_pointed_turn
import game.vinto.app.art.seat_pointed_vinto
import game.vinto.app.theme.Signal
import game.vinto.app.theme.Slate
import game.vinto.app.theme.onFelt
import game.vinto.client.Attention
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private val PlatePad = 4.dp
private val PlateGap = 8.dp
private val NamePad = 10.dp
private val NameMax = 76.dp
private val Hairline = 1.dp
private val Ring = 2.dp

/**
 * The ring on a seat the table is pointing at, which is thicker than any other.
 *
 * Being pointed at is the loudest thing a plate can say and it lasts a second or two — an Ace
 * has just named this player, or a penalty has landed on them — while the ring saying it was
 * the same two points as the one that means "it is your turn", in a colour the player has to
 * remember the meaning of.
 */
private val PointedRing = 4.dp

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
 * And in words, for a player who cannot see the colour.
 *
 * A ring is the whole of what the table says about a seat at these moments, so without this
 * an Ace aimed at a screen-reader user is a card that appears in their hand for no stated
 * reason — the one thing this game must never do, since the hand is what they are holding in
 * their head.
 */
private fun Attention.spoken(): StringResource = when (this) {
    Attention.TURN -> Res.string.seat_pointed_turn
    Attention.VINTO -> Res.string.seat_pointed_vinto
    Attention.PENALTY -> Res.string.seat_pointed_penalty
    Attention.COALITION -> Res.string.seat_pointed_coalition
}

/**
 * The seat's face, and the badge if a machine is behind it.
 *
 * The badge is on the corner of the portrait rather than beside the name: a plate is capped
 * in width and the name gives way first, so a mark that costs width is a mark that pushes
 * somebody's name to an ellipsis. Three of the four seats are machines and nothing said so —
 * which matters most to the player who has just been beaten by one and wants to know by what.
 */
@Composable
private fun Portrait(name: String, bot: Boolean, edge: Color, size: Dp) {
    Box(contentAlignment = Alignment.BottomEnd) {
        Image(
            painter = painterResource(portraitFor(name)),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .border(Hairline, edge, CircleShape),
        )
        if (bot) BotMark(diameter = size * BotShare)
    }
}

/**
 * The badge that says a seat is played by the machine.
 *
 * Drawn rather than lettered, because it sits at 14 dp on a 40 dp portrait and a word at
 * that size is a smudge in every language. It carries its own description, so a screen
 * reader announces the seat and then that it is a bot, instead of the badge being silent
 * or the name being replaced by it.
 */
@Composable
private fun BotMark(diameter: Dp) {
    val spoken = stringResource(Res.string.seat_is_a_bot)
    Canvas(
        modifier = Modifier
            .size(diameter)
            .semantics { contentDescription = spoken },
    ) {
        val d = size.minDimension
        // Gold ground with a dark face on it, rather than the other way round: at 14 px the
        // silhouette is all there is, and a filled disc has one.
        drawCircle(color = Slate.gold, radius = d / 2)
        drawCircle(color = Slate.fill, radius = d / 2 - d * MARK_EDGE / 2, style = Stroke(d * MARK_EDGE))
        // Two stubs at the temples, and no aerial. The first version had a mast standing out
        // of a wide head and read as a crown — or worse, as reported.
        listOf(-1, 1).forEach { side ->
            drawRoundRect(
                color = Slate.fill,
                topLeft = Offset(d / 2 + side * EAR_X * d - d * EAR_W / 2, d * EAR_TOP),
                size = Size(d * EAR_W, d * EAR_H),
                cornerRadius = CornerRadius(d * EAR_W / 2),
            )
        }
        // A square head with two eyes and a mouth: the shape a person draws when asked for a
        // robot, which is the only test a 14 px glyph can pass.
        drawRoundRect(
            color = Slate.fill,
            topLeft = Offset(d * HEAD_X, d * HEAD_TOP),
            size = Size(d * HEAD_W, d * HEAD_H),
            cornerRadius = CornerRadius(d * HEAD_R),
        )
        listOf(-1, 1).forEach { side ->
            drawCircle(
                color = Slate.gold,
                radius = d * EYE_R,
                center = Offset(d / 2 + side * d * EYE_X, d * EYE_Y),
            )
        }
        drawLine(
            color = Slate.gold,
            start = Offset(d / 2 - d * MOUTH_W / 2, d * MOUTH_Y),
            end = Offset(d / 2 + d * MOUTH_W / 2, d * MOUTH_Y),
            strokeWidth = d * MOUTH_H,
            cap = StrokeCap.Round,
        )
    }
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
    /** Whether this seat is played by the machine, which is worth saying out loud. */
    bot: Boolean = false,
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

    val said = pointed?.let { stringResource(it.spoken(), name) }

    Surface(
        // A plate is a target — a Nine looks at one of these, a Jack swaps into one — so it
        // is at least a thumb tall even when the portrait inside it is not.
        modifier = modifier
            .heightIn(min = PlateTap)
            .semantics { said?.let { contentDescription = it } },
        shape = CircleShape,
        color = Slate.fill.copy(alpha = PLATE_ALPHA),
        border = BorderStroke(
            when {
                pointed != null -> PointedRing
                active || onClick != null -> Ring
                else -> Hairline
            },
            edge,
        ),
        onClick = onClick ?: {},
        enabled = onClick != null,
    ) {
        Row(
            modifier = Modifier.padding(PlatePad),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PlateGap),
        ) {
            Portrait(name = name, bot = bot, edge = edge, size = size)
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
internal fun portraitFor(name: String): DrawableResource = when {
    name.startsWith("Raph") -> Res.drawable.avatar_raphael
    name.startsWith("Mikey") || name.startsWith("Michel") -> Res.drawable.avatar_michelangelo
    name.startsWith("Don") -> Res.drawable.avatar_donatello
    // Leonardo is named by `RoomCore.botName` for seat zero, which is a bot only in a
    // networked room whose host has gone. There is no `avatar_leonardo` yet, so he borrows
    // the fallback like any unrecognised name — the bot badge is what tells him from a
    // person, and it is on the portrait rather than in it for that reason.
    else -> Res.drawable.avatar_you
}

private val PlateTap = 44.dp

private const val PLATE_ALPHA = 0.9f

/**
 * The bot badge, as fractions of the portrait it sits on, so it scales with the three table
 * sizes rather than being drawn for one of them.
 */
private const val BotShare = 0.46f
private const val MARK_EDGE = 0.08f
private const val HEAD_X = 0.24f
private const val HEAD_W = 0.52f
private const val HEAD_TOP = 0.24f
private const val HEAD_H = 0.52f
private const val HEAD_R = 0.14f
private const val EAR_X = 0.33f
private const val EAR_W = 0.12f
private const val EAR_TOP = 0.38f
private const val EAR_H = 0.24f
private const val EYE_R = 0.075f
private const val EYE_X = 0.13f
private const val EYE_Y = 0.42f
private const val MOUTH_W = 0.24f
private const val MOUTH_H = 0.07f
private const val MOUTH_Y = 0.62f

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
