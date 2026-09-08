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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import game.vinto.app.art.Res
import game.vinto.app.art.avatar_dune
import game.vinto.app.art.avatar_ember
import game.vinto.app.art.avatar_gale
import game.vinto.app.art.avatar_tide
import game.vinto.app.art.seat_badge_away
import game.vinto.app.art.seat_badge_coalition
import game.vinto.app.art.seat_badge_vinto
import game.vinto.app.art.seat_badge_waiting
import game.vinto.app.art.seat_is_a_bot
import game.vinto.app.art.seat_pointed_coalition
import game.vinto.app.art.seat_pointed_penalty
import game.vinto.app.art.seat_pointed_turn
import game.vinto.app.art.seat_pointed_vinto
import game.vinto.app.theme.GeneratedAvatar
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

/**
 * How much width a name gets, which grows with the table it is on.
 *
 * It was a flat 76 points, sized for a phone, and every online player was introduced as
 * "Clever H…" on a desktop with half a metre of empty felt beside them. A minted nickname is two
 * words and the room guarantees they differ — but only if you can read enough of them to tell,
 * and an ellipsis after eight characters is where "Clever Hedgehog" and "Clever Heron" become
 * the same player.
 *
 * Tied to the portrait rather than to the screen, because that is what already steps with the
 * felt, and floored at the old value so a phone's plate is exactly what it was: on a phone the
 * cap is real, and a plate that grows there pushes the player's own hand onto a second row.
 */
private fun nameRoom(portrait: Dp): Dp = (portrait * NAME_SHARE).coerceIn(NameMax, NameWidest)

private const val NAME_SHARE = 2.5f

private val NameMax = 76.dp
private val NameWidest = 180.dp
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

/**
 * The ring's colour, for the two attentions that still have one.
 *
 * Colour was carrying six meanings on this one object — turn, Vinto, penalty, coalition,
 * clickable, resting — over the top of eight avatar grounds that mean *identity*. Six colours is
 * not a vocabulary, it is a legend nobody reads, and status and identity competing in one circle
 * is why none of it registered.
 *
 * Three survive, and each is something you must react to *now*: whose turn it is, who just took
 * a penalty, and which seats you may tap when an Ace or a Jack is asking you to pick one. The
 * durable facts — being the Vinto caller, being in the coalition, being a bot — are marks
 * instead, because they are things to know rather than things to catch.
 */
private fun Attention.colour(): Color? = when (this) {
    Attention.TURN -> Signal.turn
    Attention.PENALTY -> Signal.penalty
    // Both are marks now, and both are durable: the ring is for what has just changed.
    Attention.VINTO, Attention.COALITION -> null
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
private fun Portrait(name: String, size: Dp) {
    val chosen = chosenFace(name)
    Box {
        // The face its owner picked, when there is one. A bot has no profile and keeps its
        // element's emblem, which is the better answer than a mark it never chose.
        //
        // **No ring of our own.** Every face already draws the deck's ink ring at its own rim —
        // the four masters do, and `GeneratedAvatar` does — so a `border` here landed a second
        // ring on exactly the same circle, and in the resting state it was a *translucent* ink
        // over a solid one. Two edges a pixel apart, one of them see-through, is the definition
        // of a soft edge. The seat's state is carried by the plate's own border around the whole
        // capsule, which is bigger, further from the art, and the ring the eye actually reads.
        if (chosen != null) {
            GeneratedAvatar(traits = chosen.traits(), ground = chosen.ground(), size = size)
        } else {
            Image(
                painter = painterResource(portraitFor(name)),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size).clip(CircleShape),
            )
        }
    }
}

/**
 * The breath on the seat whose turn it is.
 *
 * Its own composable so it is *only* composed by the branch that uses it — the same shape
 * `CardFace.ringColour` uses, and for the same reason. It was read unconditionally, so all four
 * plates ran an infinite transition for the life of the table while at most one of them could
 * ever show it. An infinite transition asks for a frame every vsync, and a composition that
 * never stops asking for frames is a table that never goes idle: three permanent animations
 * nobody could see, redrawing the screen forever. On a phone that is battery; on the web it is
 * the whole reason the page felt slow with nothing happening on it.
 */
@Composable
private fun seatGlow(): Float {
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
    return glow
}

/** The marks and the score, under the name, in the order they are worth reading. */
@Composable
private fun BadgeRow(badges: List<SeatBadge>, marks: String?, portrait: Dp) {
    if (badges.isEmpty() && marks == null) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BadgeGap),
    ) {
        badges.forEach { Badge(it, portrait) }
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

/**
 * What a seat is, said in marks rather than in colour.
 *
 * Colour is down to the three things you must react to *now* — whose turn it is, who just took a
 * penalty, and which seats an Ace is asking you to pick between. Everything else about a seat is
 * a fact rather than an alarm, and a fact is better read than remembered: six ring colours over
 * eight identity grounds was a legend, and nobody reads a legend mid-round.
 *
 * Each of these carries its own words for a screen reader, because a mark nobody can see is
 * exactly the failure the ring already had.
 */
enum class SeatBadge {
    /** The table is waiting on this seat — a bot thinking, or somebody yet to peek or answer. */
    WAITING,

    /** A machine plays this seat. */
    BOT,

    /** This seat called Vinto. */
    VINTO,

    /** This seat is in the coalition playing against the caller. */
    COALITION,

    /** Nobody is behind this seat at the moment. */
    AWAY,
}

/** One mark, drawn at a size that follows the portrait beside it. */
@Composable
private fun Badge(badge: SeatBadge, portrait: Dp) {
    val said = stringResource(badge.spoken())
    val ink = when (badge) {
        SeatBadge.WAITING -> Slate.ink
        SeatBadge.VINTO -> Slate.gold
        SeatBadge.COALITION -> Signal.coalition
        SeatBadge.AWAY -> Slate.ink.copy(alpha = QUIET)
        SeatBadge.BOT -> Slate.ink.copy(alpha = QUIET)
    }
    Canvas(
        modifier = Modifier
            .size(portrait * BadgeShare)
            .semantics { contentDescription = said },
    ) {
        when (badge) {
            SeatBadge.WAITING -> drawThought(ink)
            SeatBadge.BOT -> drawRobot(ink)
            SeatBadge.VINTO -> drawCrown(ink)
            SeatBadge.COALITION -> drawLink(ink)
            SeatBadge.AWAY -> drawAway(ink)
        }
    }
}

private fun SeatBadge.spoken(): StringResource = when (this) {
    SeatBadge.WAITING -> Res.string.seat_badge_waiting
    SeatBadge.BOT -> Res.string.seat_is_a_bot
    SeatBadge.VINTO -> Res.string.seat_badge_vinto
    SeatBadge.COALITION -> Res.string.seat_badge_coalition
    SeatBadge.AWAY -> Res.string.seat_badge_away
}

/** A thought cloud: three bumps over two trailing dots. The table is waiting on this seat. */
private fun DrawScope.drawThought(ink: Color) {
    val w = size.minDimension
    val pen = Stroke(width = w * BADGE_PEN, cap = StrokeCap.Round)
    drawCircle(ink, radius = w * PUFF_BIG, center = Offset(w * PUFF_BIG_X, w * PUFF_BIG_Y), style = pen)
    drawCircle(ink, radius = w * PUFF_MID, center = Offset(w * PUFF_MID_X, w * PUFF_MID_Y), style = pen)
    drawCircle(ink, radius = w * PUFF_LOW, center = Offset(w * PUFF_LOW_X, w * PUFF_LOW_Y), style = pen)
    drawCircle(ink, radius = w * TRAIL_NEAR, center = Offset(w * TRAIL_NEAR_X, w * TRAIL_NEAR_Y))
    drawCircle(ink, radius = w * TRAIL_FAR, center = Offset(w * TRAIL_FAR_X, w * TRAIL_FAR_Y))
}

/** A square head with two eyes and a stub either side — the shape people draw for a robot. */
private fun DrawScope.drawRobot(ink: Color) {
    val w = size.minDimension
    val pen = Stroke(width = w * BADGE_PEN)
    drawRoundRect(
        color = ink,
        topLeft = Offset(w * HEAD_LEFT, w * HEAD_TOP_),
        size = Size(w * HEAD_WIDE, w * HEAD_DEEP),
        cornerRadius = CornerRadius(w * HEAD_ROUND),
        style = pen,
    )
    listOf(EYE_LEFT_X, EYE_RIGHT_X).forEach {
        drawCircle(ink, radius = w * EYE_SIZE, center = Offset(w * it, w * EYE_Y_))
    }
    listOf(STUB_LEFT, STUB_RIGHT).forEach {
        drawLine(ink, Offset(w * it, w * STUB_TOP), Offset(w * it, w * STUB_FOOT), pen.width)
    }
    drawLine(ink, Offset(w * MIDDLE, w * AERIAL_TOP), Offset(w * MIDDLE, w * HEAD_TOP_), pen.width)
}

/** Three points and a band: the caller wears it for the rest of the round. */
private fun DrawScope.drawCrown(ink: Color) {
    val w = size.minDimension
    val pen = Stroke(width = w * BADGE_PEN, cap = StrokeCap.Round)
    val crown = Path().apply {
        moveTo(w * CROWN_LEFT, w * CROWN_FOOT)
        lineTo(w * CROWN_LEFT_TIP, w * CROWN_SHOULDER)
        lineTo(w * CROWN_DIP_LEFT, w * CROWN_DIP)
        lineTo(w * MIDDLE, w * CROWN_PEAK)
        lineTo(w * CROWN_DIP_RIGHT, w * CROWN_DIP)
        lineTo(w * CROWN_RIGHT_TIP, w * CROWN_SHOULDER)
        lineTo(w * CROWN_RIGHT, w * CROWN_FOOT)
        close()
    }
    drawPath(crown, color = ink, style = pen)
}

/** Two links: this seat and the others are one hand. */
private fun DrawScope.drawLink(ink: Color) {
    val w = size.minDimension
    val pen = Stroke(width = w * BADGE_PEN)
    listOf(LINK_LEFT_X, LINK_RIGHT_X).forEach {
        drawCircle(ink, radius = w * LINK_R, center = Offset(w * it, w * MIDDLE), style = pen)
    }
}

/** An open circle with a gap where somebody should be. */
private fun DrawScope.drawAway(ink: Color) {
    val w = size.minDimension
    drawArc(
        color = ink,
        startAngle = AWAY_FROM,
        sweepAngle = AWAY_SWEEP,
        useCenter = false,
        topLeft = Offset(w * AWAY_INSET, w * AWAY_INSET),
        size = Size(w * AWAY_SIZE, w * AWAY_SIZE),
        style = Stroke(width = w * BADGE_PEN, cap = StrokeCap.Round),
    )
}

private const val BADGE_PEN = 0.09f
private const val MIDDLE = 0.50f

private const val PUFF_BIG = 0.20f
private const val PUFF_BIG_X = 0.34f
private const val PUFF_BIG_Y = 0.36f
private const val PUFF_MID = 0.17f
private const val PUFF_MID_X = 0.64f
private const val PUFF_MID_Y = 0.30f
private const val PUFF_LOW = 0.14f
private const val PUFF_LOW_X = 0.72f
private const val PUFF_LOW_Y = 0.52f
private const val TRAIL_NEAR = 0.07f
private const val TRAIL_NEAR_X = 0.26f
private const val TRAIL_NEAR_Y = 0.74f
private const val TRAIL_FAR = 0.05f
private const val TRAIL_FAR_X = 0.13f
private const val TRAIL_FAR_Y = 0.90f

private const val HEAD_LEFT = 0.22f
private const val HEAD_TOP_ = 0.26f
private const val HEAD_WIDE = 0.56f
private const val HEAD_DEEP = 0.50f
private const val HEAD_ROUND = 0.14f
private const val EYE_LEFT_X = 0.38f
private const val EYE_RIGHT_X = 0.62f
private const val EYE_SIZE = 0.06f
private const val EYE_Y_ = 0.50f
private const val STUB_LEFT = 0.14f
private const val STUB_RIGHT = 0.86f
private const val STUB_TOP = 0.44f
private const val STUB_FOOT = 0.58f
private const val AERIAL_TOP = 0.12f

private const val CROWN_LEFT = 0.16f
private const val CROWN_RIGHT = 0.84f
private const val CROWN_FOOT = 0.72f
private const val CROWN_LEFT_TIP = 0.22f
private const val CROWN_RIGHT_TIP = 0.78f
private const val CROWN_SHOULDER = 0.30f
private const val CROWN_DIP_LEFT = 0.40f
private const val CROWN_DIP_RIGHT = 0.60f
private const val CROWN_DIP = 0.54f
private const val CROWN_PEAK = 0.24f

private const val LINK_LEFT_X = 0.36f
private const val LINK_RIGHT_X = 0.64f
private const val LINK_R = 0.20f

private const val AWAY_FROM = 40f
private const val AWAY_SWEEP = 280f
private const val AWAY_INSET = 0.22f
private const val AWAY_SIZE = 0.56f

private val BadgeGap = 3.dp

/** A mark is a little over a third of the portrait beside it, so the two step together. */
private const val BadgeShare = 0.38f

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
    badges: List<SeatBadge> = emptyList(),
    pointed: Attention? = null,
    size: Dp = 40.dp,
    onClick: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme

    val edge by animateColorAsState(
        when {
            // Being pointed at wins over everything: it is the table saying *this* seat, now.
            pointed?.colour() != null -> pointed.colour()!!

            // Gold means "you may pick this one", and it outranks whose turn it is because it
            // is the only one of the three that is a *question being asked of you*.
            //
            // This was gold too when it was merely somebody's turn, so an Ace asking you to
            // choose a player lit the same colour on the seat you had to pick and on the seat
            // whose turn it happened to be. Two meanings, one colour, in the one moment the
            // colour is load-bearing.
            onClick != null -> Slate.gold
            active -> Signal.turn.copy(alpha = seatGlow())
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
            Portrait(name = name, size = size)
            // Capped, and the name gives way before the marks do. A plate that grows with
            // "Vinto · 12" is a plate that pushes the player's own hand onto a second row,
            // which is the one hand that has to stay in one piece.
            Column(modifier = Modifier.padding(end = NamePad).widthIn(max = nameRoom(size))) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    color = if (active) Slate.gold else Slate.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                BadgeRow(badges, marks, size)
            }
        }
    }
}

/**
 * The portrait for a seat, where the seat has one.
 *
 * Keyed on the name the engine deals, which is fixed: `initializeGame` always seats You, Ember,
 * Tide and Dune in that order, and a room fills its seats from the same list starting at Gale
 * (`RoomCore.botName`). Online the seats are people who typed their own nicknames and **have no
 * portrait at all** — not yet; nothing carries one over the wire — which is what this returns
 * null for.
 *
 * That null is the whole reason this exists beside [portraitFor]. Somewhere that draws a
 * portrait *beside a name already on the screen* — the rail's seat buttons — can simply leave
 * it out, and a stranger with no face is honest. Somewhere that has a round hole to fill
 * whatever happens, like the felt's plate, needs a fallback, and that is [portraitFor]'s job.
 *
 * Matched on the whole name rather than a prefix. The old cast was matched with `startsWith`
 * so that "Raph" and "Raphael" landed on one portrait, and the cost was that any stranger
 * whose nickname happened to begin with those letters was handed a bot's face. These four
 * names are the only forms there are, so the looser match buys nothing.
 */
internal fun portraitOrNull(name: String): DrawableResource? = when (name) {
    "Gale" -> Res.drawable.avatar_gale
    "Ember" -> Res.drawable.avatar_ember
    "Tide" -> Res.drawable.avatar_tide
    "Dune" -> Res.drawable.avatar_dune
    // The offline game's human seat is dealt as "You" and the felt draws Gale's leaf on it, so
    // this says the same rather than leaving the one seat in a solo game faceless.
    "You" -> Res.drawable.avatar_gale
    else -> null
}

/**
 * The portrait for a seat, whoever they are.
 *
 * Gale is the fallback, because the seat it marks is the one the offline game gives the human:
 * the emblem was filed as `avatar_you` in an earlier life and read as "a picture of the
 * viewer", which is true of exactly one game mode. Online the viewer is whoever typed their
 * name in, and seat zero can be Gale itself when the room fills it with a bot. The file is
 * named for the seat now; the fallback is a separate decision that happens to land on it.
 */
internal fun portraitFor(name: String): DrawableResource =
    portraitOrNull(name) ?: Res.drawable.avatar_gale

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
