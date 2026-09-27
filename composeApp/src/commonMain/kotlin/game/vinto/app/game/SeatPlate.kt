package game.vinto.app.game

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
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
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import game.vinto.app.LocalReducedMotion
import game.vinto.app.art.Res
import game.vinto.app.art.avatar_dune
import game.vinto.app.art.avatar_ember
import game.vinto.app.art.avatar_gale
import game.vinto.app.art.avatar_tide
import game.vinto.app.art.seat_badge_agreed
import game.vinto.app.art.seat_badge_away
import game.vinto.app.art.seat_badge_barred
import game.vinto.app.art.seat_badge_vinto
import game.vinto.app.art.seat_badge_waiting
import game.vinto.app.art.seat_badge_will_shed
import game.vinto.app.art.seat_is_a_bot
import game.vinto.app.art.seat_pointed_coalition
import game.vinto.app.art.seat_pointed_penalty
import game.vinto.app.art.seat_pointed_turn
import game.vinto.app.art.seat_pointed_vinto
import game.vinto.app.theme.GeneratedAvatar
import game.vinto.app.theme.Signal
import game.vinto.app.theme.Slate
import game.vinto.app.theme.WholeWords
import game.vinto.app.theme.onFelt
import game.vinto.client.Attention
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor

private val PlatePad = 4.dp
private val PlateGap = 2.dp

/**
 * How wide a plate is: fixed by the portrait it carries, and by nothing it says.
 *
 * The plate used to be a pill — portrait, then the name beside it in a slot capped at 76 points
 * on a phone — and the pill was the widest thing in every seat. On a phone about 400 points
 * across, your own five cards and that pill did not fit one row, so the hand wrapped two over
 * three and took a card's height out of the middle of the felt; the side columns, which live in
 * that middle, then laid five cards in four cards' room. And the name was cut anyway: "Dusty
 * Pebb…". Reported together from a phone, and they were one fault.
 *
 * So the face sits above the name, which gets two lines and shrinks before it is cut, and the
 * plate is about two portraits wide. **Fixed, not capped**: a plate as wide as its widest row is
 * a plate that changes width when a name or a mark does, and the hand beside it re-pitches from
 * exactly that number (`SteadyPlateTest`). Tied to the portrait because that is what already
 * steps with the felt, so a desktop's plate grows with its cards.
 */
internal fun plateWidth(portrait: Dp): Dp = portrait * PLATE_SHARE

private const val PLATE_SHARE = 2.2f

/** Rounder than a card's corner, so a plate is never mistaken for one lying beside it. */
private val PlateCorner = 14.dp
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
 * The seat's face: the one its owner chose, or a bot's emblem.
 *
 * Nothing is drawn on it. The thought cloud used to sit on its corner, which kept the cloud from
 * widening the plate but covered part of the face every turn; it has a home of its own beside
 * the face now ([Homes]), where it costs no room either.
 */
@Composable
private fun Portrait(name: String, size: Dp) {
    val chosen = chosenFace(name)
    // Its own size whatever the plate has left, because the face is who is sitting there and is
    // the one part of a plate that never gives way — and because a face squeezed to no height
    // took the whole process down with a native trap (`SidewaysPhoneTest`). Tagged so a test can
    // find it and measure it: a face has no words of its own to be found by.
    Box(modifier = Modifier.requiredSize(size).testTag(faceTag(name))) {
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
 * A seat's face on its own, as the felt draws it: the one its owner chose, or the bot's emblem.
 * For the lobby, which shows who is sitting before there is a table to seat them at.
 */
@Composable
internal fun FaceOf(name: String, size: Dp) = Portrait(name = name, size = size)

/** How a test finds [name]'s face on the felt. */
internal fun faceTag(name: String): String = "face:$name"

/**
 * The breath on the seat whose turn it is.
 *
 * Its own composable so it is *only* composed by the branch that uses it. It was read
 * unconditionally, so all four plates ran an infinite transition for the life of the table
 * while at most one of them could ever show it. An infinite transition asks for a frame every
 * vsync, and a composition that never stops asking for frames is a table that never goes idle:
 * three permanent animations nobody could see, redrawing the screen forever. On a phone that is
 * battery; on the web it is the whole reason the page felt slow with nothing happening on it.
 *
 * **It hands back the `State`, not the number, and that is the second half of the same fix.**
 * A composable that returns a value is not restartable, so a state read inside it lands on the
 * *caller's* scope — this one's caller being [SeatPlate], which therefore recomposed sixty
 * times a second for the whole of a seat's turn, name, portrait, marks and all. Worse, the
 * number was feeding `animateColorAsState`, so a colour animation was chasing a target that
 * moved every frame: twice the work, and it damped the very pulse it was carrying. Returned as
 * a `State` and dereferenced inside a `graphicsLayer` block, the read happens in the draw phase
 * and the breath costs one layer's alpha per frame. Same shape as `InFlight` in `CardStage`.
 */
@Composable
private fun seatGlow(): State<Float> {
    val pulse = rememberInfiniteTransition(label = "seat")
    return pulse.animateFloat(
        initialValue = GLOW_LOW,
        targetValue = GLOW_HIGH,
        animationSpec = infiniteRepeatable(
            animation = tween(GLOW_MS, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow",
    )
}

/**
 * What a plate's four homes are holding: one mark each, or nothing.
 *
 * The marks used to be a list under the name, and a list grows as marks arrive — so the final
 * round, when a seat calls and the rest start agreeing to a plan, made plates taller exactly when
 * the table was fullest, and the side seats' cards paid for it by sliding onto each other. Four
 * homes instead, each in space the plate already leaves empty, each always in the same place:
 * a mark appears and disappears in its home, and nothing else on the plate moves.
 *
 * Seven marks and a score fit four homes because the marks that share one can never be seen
 * together.
 */
internal data class Homes(
    /** Who plays the seat: a bot, or a person away with a bot covering. Away already means a bot. */
    val who: SeatBadge?,
    /** The seat's part in the round: the crown, or a nod to the plan. The caller never nods. */
    val part: SeatBadge?,
    /** The throw-in: planned, or barred — a barred seat cannot throw. Null once a [score] is shown. */
    val toss: SeatBadge?,
    /** The hand's total once the round is scored, in the throw-in's home: nothing is left to throw. */
    val score: String?,
    /** The table is waiting on this seat. */
    val now: Boolean,
)

internal fun homesFor(badges: List<SeatBadge>, score: String?, thinking: Boolean): Homes = Homes(
    who = when {
        SeatBadge.AWAY in badges -> SeatBadge.AWAY
        SeatBadge.BOT in badges -> SeatBadge.BOT
        else -> null
    },
    part = when {
        SeatBadge.VINTO in badges -> SeatBadge.VINTO
        SeatBadge.AGREED in badges -> SeatBadge.AGREED
        else -> null
    },
    toss = when {
        score != null -> null
        SeatBadge.BARRED in badges -> SeatBadge.BARRED
        SeatBadge.WILL_SHED in badges -> SeatBadge.WILL_SHED
        else -> null
    },
    score = score,
    now = thinking,
)

/**
 * How large a home is: half the face less half a gap, so two of them stacked are exactly one
 * face tall and the four homes beside it cost the plate no height at all.
 */
private fun homeSize(portrait: Dp): Dp = (portrait - PlateGap) / 2

/**
 * The face with its four homes, two either side: the plates above and below the felt.
 *
 * The plate is as wide as a two-word name needs, so the face has room either side of it that
 * was only ever empty. Part in the round and who plays on the left, now and the throw-in on the
 * right: the crown high beside the head, the thought cloud where a thought bubble rises from.
 */
@Composable
private fun FaceWithHomes(name: String, portrait: Dp, homes: Homes) {
    val home = homeSize(portrait)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(verticalArrangement = Arrangement.spacedBy(PlateGap)) {
            HomeOf(homes.part, home)
            HomeOf(homes.who, home)
        }
        Portrait(name = name, size = portrait)
        Column(verticalArrangement = Arrangement.spacedBy(PlateGap)) {
            NowHome(homes.now, home)
            TossHome(homes, home)
        }
    }
}

/**
 * The four homes in a line beside a side seat's turned name, on the table side of it, in order
 * from the face: now, part in the round, who plays, the throw-in. [fromTop] is whether the face
 * is above them — the right-hand seat's is, the left-hand seat's is at the foot.
 */
@Composable
private fun EdgeHomes(portrait: Dp, homes: Homes, fromTop: Boolean) {
    val home = homeSize(portrait)
    val inOrder: List<@Composable () -> Unit> = listOf(
        { NowHome(homes.now, home) },
        { HomeOf(homes.part, home) },
        { HomeOf(homes.who, home) },
        { TossHome(homes, home) },
    )
    Column(verticalArrangement = Arrangement.spacedBy(PlateGap)) {
        (if (fromTop) inOrder else inOrder.reversed()).forEach { it() }
    }
}

/** One home: its mark, or the same room left empty so nothing beside it moves. */
@Composable
private fun HomeOf(badge: SeatBadge?, size: Dp) {
    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        badge?.let { SeatMark(it, size = size) }
    }
}

@Composable
private fun NowHome(now: Boolean, size: Dp) {
    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        if (now) Thought(size = size, modifier = Modifier, bare = true)
    }
}

/** The throw-in's home, which holds the hand's total instead once the round is scored. */
@Composable
private fun TossHome(homes: Homes, size: Dp) {
    val score = homes.score ?: return HomeOf(homes.toss, size)
    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        val style = MaterialTheme.typography.labelSmall
        Text(
            text = score,
            style = style,
            fontWeight = FontWeight.Bold,
            color = Slate.gold,
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = ScoreLeast, maxFontSize = style.fontSize),
        )
    }
}

/** The smallest a score is drawn in its home, for the rare hand that totals three digits' width. */
private val ScoreLeast = 7.sp

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
 *
 * **Every one of them is durable.** Whether the table is *waiting* on a seat is the one thing
 * about a seat that changes every turn, so it is not one of these: it is the thought cloud
 * ([Thought]), in a home of its own ([Homes]).
 *
 * **There is no coalition mark.** There was a link, and three seats wore it for the whole final
 * round: in a four-seat game every seat but the caller's is in the coalition, and the caller
 * already wears the crown and the gold edge. It said nothing the crown had not, and it was half of
 * what made the final round's plates taller.
 */
enum class SeatBadge {
    /** A machine plays this seat. */
    BOT,

    /** This seat called Vinto. */
    VINTO,

    /** Nobody is behind this seat at the moment. */
    AWAY,

    /**
     * This seat has said yes to the coalition's plan as it stands (design D7).
     *
     * On the plate rather than on the plan, because a nod is about a *member* and not about a
     * step: reading the plan's turns, "who is happy with this" is a question about the people
     * round the table, and the plates are where the table already says what each seat is.
     */
    AGREED,

    /**
     * This seat has said it will throw a rank in if one lands (design D7).
     *
     * Not a turn, so it has no place in the sequence of turns — but it belongs to a seat, and
     * the rank itself is beside it in the plate's marks.
     */
    WILL_SHED,

    /**
     * This seat guessed wrong on a toss-in and may not try again.
     *
     * The engine has always known it for *every* seat — `PlayerView.barredFromTossIn` is a list
     * — and the table only ever read it for the viewer, to word their own prompt. So whether an
     * opponent could still throw in was a fact the client held and never drew. It matters most
     * in the final round, where the bar lasts the whole round rather than the one window.
     */
    BARRED,
}

/**
 * One mark, drawn at [size] — its home's, on a plate.
 *
 * Internal because the help sheet's legend draws the **same** composable rather than a picture
 * of it: a legend that redraws a mark is a legend that can come to disagree with the table.
 */
@Composable
internal fun SeatMark(badge: SeatBadge, size: Dp) {
    // The away mark is the one that stands for two facts — nobody is there, and a bot is playing
    // for them — so it says both, where the table used to draw two marks to say them.
    val said = if (badge == SeatBadge.AWAY) {
        listOf(stringResource(Res.string.seat_is_a_bot), stringResource(badge.spoken()))
    } else {
        listOf(stringResource(badge.spoken()))
    }
    val ink = when (badge) {
        SeatBadge.VINTO -> Slate.gold
        SeatBadge.AWAY -> Slate.ink.copy(alpha = QUIET)
        SeatBadge.BOT -> Slate.ink.copy(alpha = QUIET)
        SeatBadge.BARRED -> Signal.penalty
        SeatBadge.AGREED -> Signal.pick
        SeatBadge.WILL_SHED -> Signal.coalition
    }
    val marked = Modifier
        .size(size)
        .semantics { this[SemanticsProperties.ContentDescription] = said }

    // One branch per mark rather than one Canvas over a `when`, so a mark that ever needs to
    // move can be a composable rather than a drawing — which is what [Thought] became.
    when (badge) {
        SeatBadge.BOT -> Canvas(marked) { drawRobot(ink) }
        SeatBadge.VINTO -> Canvas(marked) { drawCrown(ink) }
        SeatBadge.AWAY -> Canvas(marked) { drawAway(ink) }
        SeatBadge.BARRED -> Canvas(marked) { drawBarred(ink) }
        SeatBadge.AGREED -> Canvas(marked) { drawNod(ink) }
        SeatBadge.WILL_SHED -> Canvas(marked) { drawShed(ink) }
    }
}

/**
 * The thought cloud, thinking, on the corner of the portrait it belongs to.
 *
 * Every mark under the name is a *fact* — a machine plays this seat, this seat called Vinto —
 * and a fact is a still drawing. This one is the only thing on the felt that says something is
 * happening **now**: a bot deciding, or a player who has not peeked yet. Drawn still, it said
 * that just as well when nothing was happening at all, so a table that had hung and a table
 * that was thinking looked exactly alike, and the only way to tell was to wait and see.
 *
 * So the puffs brighten in turn, bottom to top, the way a thought rises in a comic. It is the
 * progress this moment gets: there is no percentage to show — the search does not know how far
 * through it is — and what a player needs is not a number but the knowledge that the seat is
 * still working.
 *
 * Its own composable so the frame clock is started only by the seats actually being waited on,
 * which is the same reason [seatGlow] is one: an infinite transition asks for a frame every
 * vsync, and plates that never stop asking are a table that never goes idle.
 *
 * **[phase] is dereferenced inside the draw lambda, not beside it.** `by` reads the state
 * wherever the name is mentioned, so mentioning it in `Canvas { }` puts the read in the draw
 * phase and the wave costs one re-record of this canvas per frame. Read a line higher, into a
 * local, it would be a *composition* read, and every frame of the wave would recompose the
 * plate around it — which is the whole of what [seatGlow] used to do.
 */
@Composable
private fun Thought(size: Dp, modifier: Modifier, bare: Boolean = false) {
    val said = stringResource(Res.string.seat_badge_waiting)
    val marked = modifier.size(size).semantics { contentDescription = said }

    // No movement, same information — and the still cloud is the *whole* cloud at full
    // strength rather than one frame of the wave, exactly as `VintoSpinner` stands still.
    if (LocalReducedMotion.current) {
        Canvas(marked) { if (bare) drawThought(Slate.ink, phase = null) else drawThoughtBadge(phase = null) }
        return
    }

    val thought = rememberInfiniteTransition(label = "thinking")
    val phase by thought.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            // Linear and restarting: the wave below is continuous across the seam, so a turn
            // that eased would read as the cloud hesitating rather than as it thinking.
            animation = tween(ThinkMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "puffs",
    )
    Canvas(marked) { if (bare) drawThought(Slate.ink, phase) else drawThoughtBadge(phase) }
}

/**
 * The cloud on its own disc, so it reads over any of the eight grounds a face can wear.
 *
 * The puffs are drawn at [CLOUD_INSET] of the disc and centred on it: [drawThought] lays them
 * out across its whole box, which is right for a mark standing alone on the plate's fill and
 * would run them under the ring here.
 */
private fun DrawScope.drawThoughtBadge(phase: Float?) {
    val w = size.minDimension
    drawCircle(color = Slate.fill, radius = w / 2)
    drawCircle(color = Slate.ink, radius = w / 2 - w * MARK_EDGE / 2, style = Stroke(w * MARK_EDGE))
    scale(CLOUD_INSET, pivot = center) { drawThought(Slate.ink, phase) }
}

/**
 * How much of its disc the cloud fills, leaving the ring a clear rim to be read against.
 *
 * Judged on a phone rather than on a golden: the screenshots render at one device pixel per
 * point, where a mark this size is four pixels of cloud and looks like a smudge, and no real
 * screen this ships to is below two. What the number has to buy is a margin the ink ring is
 * legible in, which is a fraction of the disc and not a count of pixels.
 */
private const val CLOUD_INSET = 0.8f

internal fun SeatBadge.spoken(): StringResource = when (this) {
    SeatBadge.BOT -> Res.string.seat_is_a_bot
    SeatBadge.VINTO -> Res.string.seat_badge_vinto
    SeatBadge.AWAY -> Res.string.seat_badge_away
    SeatBadge.BARRED -> Res.string.seat_badge_barred
    SeatBadge.AGREED -> Res.string.seat_badge_agreed
    SeatBadge.WILL_SHED -> Res.string.seat_badge_will_shed
}

/**
 * A thought cloud: three puffs and one trailing bubble, filled rather than outlined.
 *
 * Outlined it was five thin rings, which at a phone's mark size is grey fuzz — a stroke of a
 * tenth of seventeen points is under two pixels, and five of them touching is one blob.
 * Filled shapes keep their silhouette at any size, which is the only thing a mark this small
 * has. The trailing bubbles went from two to one and moved up: two of them hung below the
 * cloud's mass and dragged its optical centre off the line the other marks sit on.
 *
 * [phase] is how far through the wave the cloud is, or null for the still one. Each puff
 * brightens and swells as its own turn comes round, and never goes out: at a mark this size a
 * puff that vanished would be a cloud changing shape rather than a cloud thinking.
 */
private fun DrawScope.drawThought(ink: Color, phase: Float?) {
    val w = size.minDimension
    Puffs.forEachIndexed { i, puff ->
        val lit = phase?.let { rising(it - i * PUFF_STEP) } ?: 1f
        drawCircle(
            color = ink.copy(alpha = ink.alpha * (PUFF_REST + (1f - PUFF_REST) * lit)),
            radius = w * puff.size * (PUFF_SMALL + (1f - PUFF_SMALL) * lit),
            center = Offset(w * puff.x, w * puff.y),
        )
    }
}

/**
 * One turn of the wave: dark, up to full, and back down again.
 *
 * A raised cosine rather than a ramp, because it has to be continuous *at both ends* — the
 * transition driving it restarts from zero, and a wave that did not meet itself there would
 * put a visible tick in the cloud once a second.
 */
private fun rising(at: Float): Float = (1f - cos(TWO_PI * (at - floor(at)))) / 2f

/** One circle of the cloud: where it sits, and how big it is at full brightness. */
private data class Puff(val x: Float, val y: Float, val size: Float)

/** Bottom to top, which is the order the eye reads a thought rising in. */
private val Puffs = listOf(
    Puff(TRAIL_NEAR_X, TRAIL_NEAR_Y, TRAIL_NEAR),
    Puff(PUFF_LOW_X, PUFF_LOW_Y, PUFF_LOW),
    Puff(PUFF_BIG_X, PUFF_BIG_Y, PUFF_BIG),
    Puff(PUFF_MID_X, PUFF_MID_Y, PUFF_MID),
)

/** How far apart the puffs' turns are: one whole wave, shared out between them. */
private val PUFF_STEP = 1f / Puffs.size

/**
 * How dim a puff goes between its turns, and how small.
 *
 * Dim rather than out, and 0.45 is [QUIET] — the same weight this file already draws a durable
 * fact at. The size barely moves: the silhouette is the whole of what a 17-point mark has, and
 * a cloud whose puffs deflate is one that looks like it is losing its shape.
 */
private const val PUFF_REST = QUIET
private const val PUFF_SMALL = 0.88f

/**
 * One turn of the whole cloud.
 *
 * Four puffs over a second and a half is about a third of a second each, which is the rate a
 * person reads as *working* — the same reasoning as the spinner's turn, which is a little over
 * a second and reads as an error when it is faster.
 */
private const val ThinkMs = 1500

private val TWO_PI = (2 * PI).toFloat()

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

/** A tick: this seat has said yes to the plan as it stands. */
private fun DrawScope.drawNod(ink: Color) {
    val w = size.minDimension
    val pen = w * BADGE_PEN
    stroke(ink, pen, Offset(w * NOD_LEFT, w * MIDDLE), Offset(w * NOD_TURN, w * NOD_LOW))
    stroke(ink, pen, Offset(w * NOD_TURN, w * NOD_LOW), Offset(w * NOD_RIGHT, w * NOD_HIGH))
}

/** A card leaving downwards: this seat will throw a rank in if one lands. */
private fun DrawScope.drawShed(ink: Color) {
    val w = size.minDimension
    val pen = w * BADGE_PEN
    stroke(ink, pen, Offset(w * MIDDLE, w * SHED_TOP), Offset(w * MIDDLE, w * SHED_LOW))
    stroke(ink, pen, Offset(w * SHED_LEFT, w * SHED_MID), Offset(w * MIDDLE, w * SHED_LOW))
    stroke(ink, pen, Offset(w * SHED_RIGHT, w * SHED_MID), Offset(w * MIDDLE, w * SHED_LOW))
}

/** One round-capped stroke of a badge's glyph, so the two above read as drawings. */
private fun DrawScope.stroke(ink: Color, pen: Float, from: Offset, to: Offset) =
    drawLine(ink, from, to, pen, StrokeCap.Round)

private const val NOD_LEFT = 0.28f
private const val NOD_TURN = 0.44f
private const val NOD_RIGHT = 0.74f
private const val NOD_LOW = 0.66f
private const val NOD_HIGH = 0.32f
private const val SHED_TOP = 0.26f
private const val SHED_LOW = 0.72f
private const val SHED_MID = 0.52f
private const val SHED_LEFT = 0.30f
private const val SHED_RIGHT = 0.70f

/** A circle with a bar through it: this seat may not throw in again. */
private fun DrawScope.drawBarred(ink: Color) {
    val w = size.minDimension
    val pen = Stroke(width = w * BADGE_PEN, cap = StrokeCap.Round)
    drawCircle(ink, radius = w * BARRED_R, center = Offset(w * MIDDLE, w * MIDDLE), style = pen)
    drawLine(
        ink,
        Offset(w * BARRED_FROM, w * BARRED_TO),
        Offset(w * BARRED_TO, w * BARRED_FROM),
        strokeWidth = w * BADGE_PEN,
        cap = StrokeCap.Round,
    )
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

private const val PUFF_BIG = 0.24f
private const val PUFF_BIG_X = 0.40f
private const val PUFF_BIG_Y = 0.42f
private const val PUFF_MID = 0.19f
private const val PUFF_MID_X = 0.70f
private const val PUFF_MID_Y = 0.35f
private const val PUFF_LOW = 0.15f
private const val PUFF_LOW_X = 0.20f
private const val PUFF_LOW_Y = 0.62f
private const val TRAIL_NEAR = 0.08f
private const val TRAIL_NEAR_X = 0.80f
private const val TRAIL_NEAR_Y = 0.72f

private const val HEAD_LEFT = 0.22f
private const val HEAD_TOP_ = 0.22f
private const val HEAD_WIDE = 0.56f
private const val HEAD_DEEP = 0.56f
private const val HEAD_ROUND = 0.14f
private const val EYE_LEFT_X = 0.38f
private const val EYE_RIGHT_X = 0.62f
private const val EYE_SIZE = 0.06f
private const val EYE_Y_ = 0.50f
private const val STUB_LEFT = 0.14f
private const val STUB_RIGHT = 0.86f
private const val STUB_TOP = 0.44f
private const val STUB_FOOT = 0.58f

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

private const val BARRED_R = 0.30f
private const val BARRED_FROM = 0.28f
private const val BARRED_TO = 0.72f

private const val AWAY_FROM = 40f
private const val AWAY_SWEEP = 280f
private const val AWAY_INSET = 0.22f
private const val AWAY_SIZE = 0.56f

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
 * The ring's colour, for everything about a seat except the breath.
 *
 * A plain function: the four answers depend on nothing but their arguments, and keeping them
 * out of [SeatPlate] keeps the one composable here readable — the plate is a pill with a
 * portrait, a name and a ring, and three of those should not be a `when`.
 */
private fun edgeFor(
    pointed: Attention?,
    breathing: Boolean,
    clickable: Boolean,
    lit: Boolean,
    resting: Color,
): Color = when {
    // Being pointed at wins over everything: it is the table saying *this* seat, now.
    pointed?.colour() != null -> pointed.colour()!!

    // The turn the plan is building, in the plan's own colour. Above "you may touch this",
    // because with the plan open every coalition plate may be touched and only one is the
    // turn on the rail — that one has to read as different from the other two.
    lit -> Signal.coalition

    // Green means "you may touch this", here and on a card, and it outranks whose turn it is
    // because it is the only one of the three that is a *question being asked of you*. A card
    // you can play breathes green; a seat you may choose does the same, because it is the same
    // question — and the answer should not depend on whether the thing being asked about is a
    // card or a person.
    clickable -> Signal.pick

    // Nothing, because the breathing ring is drawing it. The colour cannot carry the breath:
    // `animateColorAsState` would be animating towards a target that moves every frame, which
    // both doubles the work and damps the very pulse it is carrying — see [seatGlow].
    breathing -> Color.Transparent
    else -> resting
}

/** How heavy that ring is drawn: loudest for a seat the table is pointing at. */
private fun ringFor(pointed: Attention?, active: Boolean, clickable: Boolean): Dp = when {
    pointed != null -> PointedRing
    active || clickable -> Ring
    else -> Hairline
}

/**
 * A player: the face, and the name under it, on one plate.
 *
 * It was a pill with the name beside the face, which is the widest shape a plate can take — and
 * width is what a phone's felt has least of. See [plateWidth] for what that cost, and [NameRun]
 * for the two seats whose name runs along the edge instead.
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
    /** The table is waiting on this seat — a bot thinking, or somebody yet to peek or answer. */
    thinking: Boolean = false,
    pointed: Attention? = null,
    size: Dp = 40.dp,
    onClick: (() -> Unit)? = null,
    /**
     * The seat whose turn the plan is building: ringed in the coalition's colour, so that the
     * turn being composed is visibly *somebody's* on the felt and not only named in the rail.
     */
    lit: Boolean = false,
    /**
     * What the plate says when it is a control rather than a pointer — "Plan Tide's turn". A
     * plate that can be pressed and says only a name is a button a screen reader cannot explain.
     */
    described: String? = null,
    /** Which way the name runs: across for the seats above and below, along the rim at the sides. */
    run: NameRun = NameRun.ACROSS,
) {
    val scheme = MaterialTheme.colorScheme

    // Whether the ring is the breathing one, decided once and read twice — the `when` below
    // orders the same three questions, and the two answers must not be able to disagree.
    val breathing = active && pointed?.colour() == null && onClick == null && !lit

    val edge by animateColorAsState(
        edgeFor(pointed, breathing, onClick != null, lit, scheme.onFelt().copy(alpha = QUIET)),
        label = "edge",
    )

    val said = pointed?.let { stringResource(it.spoken(), name) } ?: described

    val shape = RoundedCornerShape(PlateCorner)
    Box(modifier = modifier) {
        Surface(
            // A plate is a target — a Nine looks at one of these, a Jack swaps into one — so it
            // is at least a thumb tall even when the portrait inside it is not.
            modifier = Modifier
                .width(if (run == NameRun.ACROSS) plateWidth(size) else edgeWidth(size, nameLine()))
                .heightIn(min = PlateTap)
                .semantics { said?.let { contentDescription = it } },
            shape = shape,
            color = Slate.fill.copy(alpha = PLATE_ALPHA),
            border = BorderStroke(ringFor(pointed, active, onClick != null || lit), edge),
            onClick = onClick ?: {},
            enabled = onClick != null,
        ) {
            // The face, then who it is, then what they are: read top to bottom, the way a
            // place card at a table is.
            Column(
                modifier = Modifier.padding(PlatePad),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(PlateGap),
            ) {
                // A turned name starts at the face: the right seat's reads down from it, and the
                // left seat's reads up from it — so there the face is at the foot and the marks,
                // which come after the name, at the head.
                val homes = homesFor(badges, marks, thinking)
                when (run) {
                    NameRun.ACROSS -> {
                        FaceWithHomes(name, size, homes)
                        PlateName(name, active)
                    }

                    // The line of name and homes is weighted, so it is measured after the face
                    // and takes what is left; the name hugs the rim and the homes face the table.
                    NameRun.DOWN -> {
                        Portrait(name = name, size = size)
                        Row(
                            modifier = Modifier.weight(1f, fill = false),
                            horizontalArrangement = Arrangement.spacedBy(PlateGap),
                        ) {
                            EdgeHomes(size, homes, fromTop = true)
                            EdgeName(name, active, clockwise = true)
                        }
                    }

                    NameRun.UP -> {
                        Row(
                            modifier = Modifier.weight(1f, fill = false),
                            horizontalArrangement = Arrangement.spacedBy(PlateGap),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            EdgeName(name, active, clockwise = false)
                            EdgeHomes(size, homes, fromTop = false)
                        }
                        Portrait(name = name, size = size)
                    }
                }
            }
        }

        // The breath, as a ring of its own laid over the plate's own edge.
        //
        // It is a separate node so that the only thing a frame of the pulse costs is this
        // layer's alpha: the plate keeps its composition, and the portrait, the name and the
        // marks under it are not touched. Composed only while it is wanted, so no seat holds a
        // frame clock open for a ring nobody is looking at.
        //
        // Still under reduced motion: the ring at full strength says whose turn it is as clearly
        // as the breath does, the way the thought cloud and the spinner stand still. It used to
        // breathe regardless, which nobody saw from a test until the cloud's home came to sit
        // beside the plate's rounded corner.
        if (breathing && LocalReducedMotion.current) {
            Box(modifier = Modifier.matchParentSize().border(Ring, Signal.turn, shape))
        } else if (breathing) {
            val glow = seatGlow()
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = glow.value }
                    .border(Ring, Signal.turn, shape),
            )
        }
    }
}

/**
 * Which way a plate's name runs.
 *
 * The two side seats' cards lie a quarter turned, the way cards lie in front of somebody sitting
 * at the side of a table — and their names do too, which was the player's own suggestion. Turned,
 * a name costs the plate one line of width however long it is, and a side seat is short of width
 * and long in height: the plate stands beside the column of cards, as tall as it needs to be and
 * a thumb wide. Both read from the middle of the table, looking out at the seat — the left one
 * from the bottom up, the right one from the top down — which is how the player asked for them.
 */
enum class NameRun {
    /** Across the plate, under the face: the seats above and below the felt. */
    ACROSS,

    /** Down the plate, a quarter turn clockwise: the seat on the right. */
    DOWN,

    /** Up the plate, a quarter turn the other way, from a face at its foot: the seat on the left. */
    UP,
}

/**
 * How wide a side seat's plate is: the face, or the line of homes and the turned name side by
 * side, whichever is wider — and never under a thumb. [line] is the name's thickness, one line of
 * its type at its largest. Fixed for the same reason [plateWidth] is: the cards beside it are laid
 * in what it leaves, and a name that shrinks to fit must not take the plate in with it.
 */
internal fun edgeWidth(portrait: Dp, line: Dp): Dp =
    maxOf(maxOf(portrait, homeSize(portrait) + PlateGap + line) + PlatePad * 2, PlateTap)

/** How wide a side seat's plate is drawn at this table's portrait size — see [edgeWidth]. */
@Composable
internal fun sidePlateWidth(portrait: Dp): Dp = edgeWidth(portrait, nameLine())

/**
 * A side seat's name, turned to run along the rim. One line — however long, it costs the plate
 * only the height it has plenty of — and it shrinks before it is cut, for a name longer than the
 * column it stands beside.
 */
@Composable
private fun EdgeName(name: String, active: Boolean, clockwise: Boolean, modifier: Modifier = Modifier) {
    val style = MaterialTheme.typography.titleSmall
    Text(
        text = name,
        style = style.copy(lineHeight = NameLeading),
        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
        color = if (active) Slate.gold else Slate.ink,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        autoSize = WholeWords(least = NameLeast, most = style.fontSize),
        modifier = modifier.quarterTurn(clockwise),
    )
}

/**
 * Lays a line out along the other axis: measured as a line of the height it is given, and placed
 * a quarter turn round about its own middle. A rotation alone is drawn rather than laid out, so a
 * plain `rotate` would leave the name's unrotated width in the plate — the very width this saves.
 */
private fun Modifier.quarterTurn(clockwise: Boolean): Modifier = layout { measurable, constraints ->
    val line = measurable.measure(
        Constraints(maxWidth = constraints.maxHeight, maxHeight = constraints.maxWidth),
    )
    layout(line.height, line.width) {
        line.placeWithLayer(x = (line.height - line.width) / 2, y = (line.width - line.height) / 2) {
            rotationZ = if (clockwise) QUARTER else -QUARTER
        }
    }
}

private const val QUARTER = 90f

/**
 * The name on the plate: gold and bold for the seat whose turn it is, so the felt says so on its
 * own.
 *
 * Two lines, because a minted name is two words and the plate is narrow; and it gets smaller
 * before it is cut ([WholeWords]), because a name is how two players are told apart. Tight
 * leading, since the two lines are one name rather than a paragraph.
 */
@Composable
private fun PlateName(name: String, active: Boolean) {
    val style = MaterialTheme.typography.titleSmall
    Text(
        text = name,
        style = style.copy(lineHeight = NameLeading, textAlign = TextAlign.Center),
        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
        color = if (active) Slate.gold else Slate.ink,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        autoSize = WholeWords(least = NameLeast, most = style.fontSize),
    )
}

/** One line of a plate's name at its largest, as thick as it is drawn: what a turned name costs a plate. */
@Composable
private fun nameLine(): Dp = with(LocalDensity.current) {
    (MaterialTheme.typography.titleSmall.fontSize * NameLeading.value).toDp()
}

/** Leading barely past the letters: two lines of a name are one name. */
private val NameLeading = 1.1.em

/**
 * The smallest a name is drawn, which is the size of the marks' own captions. The longest minted
 * words are seven letters, and a phone's plate fits them well above it.
 */
private val NameLeast = 10.sp

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
