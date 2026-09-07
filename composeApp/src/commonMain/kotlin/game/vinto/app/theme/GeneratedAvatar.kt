package game.vinto.app.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import game.vinto.protocol.AvatarTraits
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A seat's generated face, drawn from the seed's [AvatarTraits].
 *
 * The counterpart to the SVG masters under `brand/avatars`, and deliberately the same object:
 * a disc in the
 * seat's colour, the deck's ink ring around it, the court cards' gold hairline just inside,
 * and an engraved mark on top. `_shared.md` describes that as what makes a seat plate and a Queen
 * look like they come from one game, and a generated face that ignored it would look like a
 * different app's widget parked on the felt.
 *
 * **Everything is a fraction of the radius**, because the same face is drawn at 44 dp on the felt
 * and at picker size on this screen, and a stroke width in dp that reads at one is a smudge or a
 * hairline at the other. That is the 44 dp legibility test `_shared.md` sets, kept by construction
 * rather than by checking afterwards.
 *
 * The ground is a parameter and never derived from the seed. A hue drawn from a number cannot be
 * known to clear WCAG against the ink laid over it; the palette in [AvatarGrounds] can, and
 * `ContrastTest` measures every swatch of it.
 */
@Composable
fun GeneratedAvatar(
    traits: AvatarTraits,
    ground: Color,
    size: Dp,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    Canvas(
        modifier = modifier
            .size(size)
            .semantics { if (description != null) contentDescription = description },
    ) {
        drawAvatar(traits, ground)
    }
}

/**
 * The drawing itself, split out so a golden can render a sheet of them without composing twenty
 * canvases — and so the geometry is reachable from a plain [DrawScope] test.
 */
fun DrawScope.drawAvatar(traits: AvatarTraits, ground: Color) {
    val r = kotlin.math.min(size.width, size.height) / 2f
    val c = Offset(size.width / 2f, size.height / 2f)

    // The disc, its ink ring and the gold hairline — the three things every master shares.
    drawCircle(color = ground, radius = r * DISC, center = c)
    drawCircle(color = Ink, radius = r * DISC, center = c, style = Stroke(width = r * RING))
    drawCircle(color = Gold, radius = r * HAIRLINE_R, center = c, style = Stroke(width = r * HAIRLINE))

    val pen = Stroke(width = r * PEN, cap = StrokeCap.Round)
    when (traits) {
        is AvatarTraits.Mark -> drawMark(traits, c, r, pen)
        is AvatarTraits.Face -> drawFace(traits, c, r, pen)
        is AvatarTraits.Orbit -> drawOrbit(traits, c, r, pen)
        is AvatarTraits.Herald -> drawHerald(traits, ground, c, r, pen)
        is AvatarTraits.Knot -> drawKnot(traits, ground, c, r)
        is AvatarTraits.Rune -> drawRune(traits, c, r, pen)
        is AvatarTraits.Whorl -> drawWhorl(traits, c, r, pen)
    }
}

/**
 * Bars, in the manner of the four hand-drawn emblems: direction first, then waver, then curl.
 *
 * That order is `_shared.md`'s, and it is an accessibility claim rather than a style note — the
 * app tells Apple its seats are `differentiateWithoutColorAlone`. Fire stands its lines up and
 * wavers them, water lies them down and rolls them, earth lays them dead straight, air curls them
 * away. A generated mark varies the same three things, so it belongs to the same family and is
 * told apart the same way.
 */
private fun DrawScope.drawMark(mark: AvatarTraits.Mark, c: Offset, r: Float, pen: Stroke) {
    val angle = mark.tilt * (2 * PI / TURN_STEPS)
    val span = r * BAR_SPAN
    // Bars are laid across the disc and then the whole set is turned, so tilt reads as one figure
    // rotating rather than as each bar swinging independently.
    val gap = span * 2 / (mark.bars + 1)

    for (i in 0 until mark.bars) {
        val offset = -span + gap * (i + 1)
        val path = Path()
        var first = true
        var t = -span
        while (t <= span) {
            // The waver rides along the bar rather than displacing it, so a wavered bar and a
            // straight one occupy the same ground and differ only in how they travel.
            val wave = sin(t / span * PI * WAVE_TURNS) * (r * WAVE_DEPTH * mark.waver)
            // The curl lifts only the far end, which is what separates air from fire on the
            // hand-drawn pair and is the cheapest strong difference in the whole family.
            val curled = mark.curl * (r * CURL_DEPTH) * ((t / span + 1f) / 2f).let { it * it }
            val x = t
            val y = offset + wave.toFloat() + curled
            val p = c + Offset(
                (x * cos(angle) - y * sin(angle)).toFloat(),
                (x * sin(angle) + y * cos(angle)).toFloat(),
            )
            if (first) {
                path.moveTo(p.x, p.y)
                first = false
            } else {
                path.lineTo(p.x, p.y)
            }
            t += span / STEPS
        }
        drawPath(path, color = Ink, style = pen)
    }
}

/** Eyes, a brow and a mouth, engraved in the same weight of line and nothing else. */
private fun DrawScope.drawFace(face: AvatarTraits.Face, c: Offset, r: Float, pen: Stroke) {
    val apart = r * (EYE_NEAR + EYE_STEP * face.spacing)
    val eyeY = c.y - r * EYE_UP

    for (side in listOf(-1, 1)) {
        val e = Offset(c.x + apart * side, eyeY)
        when (face.eyes) {
            EYE_DOT -> {
                drawCircle(color = Ink, radius = r * EYE_R, center = e)
            }
            EYE_RING -> {
                drawCircle(color = Ink, radius = r * EYE_R, center = e, style = pen)
            }
            EYE_SLIT -> {
                engrave(e + Offset(-r * EYE_R, 0f), e + Offset(r * EYE_R, 0f), pen)
            }
            else -> {
                // A cross-hatch pair, which reads as a closed or squinting eye at 44 dp.
                engrave(e + Offset(-r * EYE_R, -r * EYE_R), e + Offset(r * EYE_R, r * EYE_R), pen)
                engrave(e + Offset(-r * EYE_R, r * EYE_R), e + Offset(r * EYE_R, -r * EYE_R), pen)
            }
        }

        // The brow is most of the expression, so it is drawn whenever it is not level.
        if (face.brow != 0) {
            val inner = Offset(
                c.x + apart * side * BROW_IN,
                eyeY - r * BROW_UP + r * BROW_TILT * face.brow * side * side,
            )
            val outer = Offset(c.x + apart * side * BROW_OUT, eyeY - r * BROW_UP - r * BROW_TILT * face.brow)
            engrave(inner, outer, pen)
        }
    }

    // Down, flat, up, and open — four states, drawn as one arc whose sweep is the whole difference.
    val mouthW = r * MOUTH_W
    val mouthY = c.y + r * MOUTH_DOWN
    val box = Rect(Offset(c.x - mouthW, mouthY - r * MOUTH_H), Size(mouthW * 2, r * MOUTH_H * 2))
    when (face.mouth) {
        0 -> drawArc(Ink, START_UP, SWEEP, false, box.topLeft, box.size, style = pen)
        1 -> engrave(Offset(c.x - mouthW, mouthY), Offset(c.x + mouthW, mouthY), pen)
        2 -> drawArc(Ink, START_DOWN, SWEEP, false, box.topLeft, box.size, style = pen)
        else -> drawOval(Ink, box.topLeft, box.size, style = pen)
    }
}

/** A polygon inside a ring of pips — the most abstract of the three, and the most various. */
private fun DrawScope.drawOrbit(orbit: AvatarTraits.Orbit, c: Offset, r: Float, pen: Stroke) {
    val spin = orbit.spin * (2 * PI / TURN_STEPS)

    val poly = Path()
    for (i in 0 until orbit.sides) {
        val a = spin + i * 2 * PI / orbit.sides
        val p = c + Offset((cos(a) * r * POLY_R).toFloat(), (sin(a) * r * POLY_R).toFloat())
        if (i == 0) poly.moveTo(p.x, p.y) else poly.lineTo(p.x, p.y)
    }
    poly.close()
    drawPath(poly, color = Ink, style = pen)

    for (ring in 1..orbit.rings) {
        val radius = r * (PIP_NEAR + PIP_STEP * ring)
        val count = orbit.sides * ring
        for (i in 0 until count) {
            // Each ring is offset by half a step so the pips interleave rather than lining up
            // into spokes, which is what makes two and three rings read as different figures.
            val a = spin + (i + ring * HALF_STEP) * 2 * PI / count
            val p = c + Offset((cos(a) * radius).toFloat(), (sin(a) * radius).toFloat())
            drawCircle(color = Ink, radius = r * PIP_R, center = p)
        }
    }

    when (orbit.pip) {
        0 -> {
            Unit
        }
        1 -> {
            drawCircle(color = Ink, radius = r * CENTRE_R, center = c)
        }
        else -> {
            drawCircle(color = Ink, radius = r * CENTRE_R, center = c, style = pen)
        }
    }
}

/**
 * A shield's division, with something optionally laid on it.
 *
 * Heraldry is borrowed rather than invented because it is the system that already solved this:
 * telling people apart at a distance, by shape, under bad conditions. The division is a *filled*
 * figure rather than a stroke, which is what separates this family from the others at a glance —
 * six of the seven are line work, and one being mass is a difference no detail can hide.
 *
 * Everything is clipped to the disc, so a band that runs off the edge is cut by the rim rather
 * than drawn over the ink ring — which is what a real charge on a real shield does.
 */
private fun DrawScope.drawHerald(
    herald: AvatarTraits.Herald,
    ground: Color,
    c: Offset,
    r: Float,
    pen: Stroke,
) {
    val edge = r * DISC
    val disc = Path().apply {
        addOval(Rect(c - Offset(edge, edge), Size(edge * 2, edge * 2)))
    }
    val band = r * (BAND_NEAR + BAND_STEP * herald.weight)
    val reach = r * DISC
    // "Sinister" in the heraldic sense: the same division facing the other way. Applied as a
    // reflection of the whole figure so a mirrored bend is a bend, not a differently-built shape.
    val hand = if (herald.flip == 1) -1f else 1f

    clipPath(disc) {
        when (herald.division) {
            BEND -> {
                bar(c, r, band, DIAGONAL * hand)
            }
            CHEVRON -> {
                chevron(c, r, band, up = true)
            }
            PALE -> {
                bar(c, r, band, QUARTER)
            }
            FESS -> {
                bar(c, r, band, 0f)
            }
            CROSS -> {
                bar(c, r, band, 0f)
                bar(c, r, band, QUARTER)
            }
            SALTIRE -> {
                bar(c, r, band, DIAGONAL)
                bar(c, r, band, -DIAGONAL)
            }
            PILE -> {
                // A pile: a wedge driven in from the top, which is the only division that is not
                // symmetric top to bottom and so the easiest of the eight to name at a glance.
                val pile = Path().apply {
                    moveTo(c.x - reach * PILE_W * hand, c.y - reach)
                    lineTo(c.x + reach * PILE_W * hand, c.y - reach)
                    lineTo(c.x, c.y + reach * PILE_DROP)
                    close()
                }
                drawPath(pile, color = Ink)
            }
            else -> {
                val lozenge = Path().apply {
                    moveTo(c.x, c.y - reach * LOZENGE)
                    lineTo(c.x + reach * LOZENGE * LOZENGE_W, c.y)
                    lineTo(c.x, c.y + reach * LOZENGE)
                    lineTo(c.x - reach * LOZENGE * LOZENGE_W, c.y)
                    close()
                }
                drawPath(lozenge, color = Ink)
            }
        }
    }

    // The charge sits on top in the ground's own colour, so it reads as cut out of the division
    // rather than as a second mark floating over it. That is how a charge behaves on a shield,
    // and it is also what keeps it visible whichever part of the field it lands on.
    when (herald.charge) {
        PLAIN_FIELD -> {
            Unit
        }
        ROUNDEL -> {
            drawCircle(color = ground, radius = r * CHARGE_R, center = c)
        }
        MULLET -> {
            drawPath(star(c, r * CHARGE_R * STAR_OUT, r * CHARGE_R * STAR_IN, MULLET_POINTS), ground)
        }
        else -> {
            val d = r * CHARGE_R
            val diamond = Path().apply {
                moveTo(c.x, c.y - d)
                lineTo(c.x + d * LOZENGE_W, c.y)
                lineTo(c.x, c.y + d)
                lineTo(c.x - d * LOZENGE_W, c.y)
                close()
            }
            drawPath(diamond, color = ground)
        }
    }
    // A hairline around the charge, so it holds an edge where the division is the same value as
    // the ground it is drawn in — the plain field, which is most of the shield.
    if (herald.charge != PLAIN_FIELD) {
        drawCircle(Ink, r * CHARGE_R * CHARGE_RIM, c, style = Stroke(pen.width * CHARGE_PEN))
    }
}

/** One engraved stroke: the ink, the pen's weight and its round cap, which never vary. */
private fun DrawScope.engrave(from: Offset, to: Offset, pen: Stroke) {
    drawLine(Ink, from, to, pen.width, StrokeCap.Round)
}

/** A filled band across the disc at [angle], long enough that the clip does the trimming. */
private fun DrawScope.bar(c: Offset, r: Float, band: Float, angle: Float) {
    val long = r * 2f
    val path = Path()
    val dx = cos(angle.toDouble()).toFloat()
    val dy = sin(angle.toDouble()).toFloat()
    // Perpendicular, to give the band its width.
    val px = -dy * band
    val py = dx * band
    path.moveTo(c.x - dx * long + px, c.y - dy * long + py)
    path.lineTo(c.x + dx * long + px, c.y + dy * long + py)
    path.lineTo(c.x + dx * long - px, c.y + dy * long - py)
    path.lineTo(c.x - dx * long - px, c.y - dy * long - py)
    path.close()
    drawPath(path, color = Ink)
}

/** A chevron, drawn as two bands meeting at a point rather than as one bent path. */
private fun DrawScope.chevron(c: Offset, r: Float, band: Float, up: Boolean) {
    val reach = r * DISC
    val rise = if (up) -1f else 1f
    val path = Path().apply {
        moveTo(c.x - reach, c.y + reach * CHEVRON_FOOT * -rise)
        lineTo(c.x, c.y + reach * CHEVRON_PEAK * rise)
        lineTo(c.x + reach, c.y + reach * CHEVRON_FOOT * -rise)
        lineTo(c.x + reach, c.y + reach * CHEVRON_FOOT * -rise + band * 2)
        lineTo(c.x, c.y + reach * CHEVRON_PEAK * rise + band * 2)
        lineTo(c.x - reach, c.y + reach * CHEVRON_FOOT * -rise + band * 2)
        close()
    }
    drawPath(path, color = Ink)
}

/** An [points]-pointed star between two radii — heraldry's mullet. */
private fun star(c: Offset, outer: Float, inner: Float, points: Int): Path = Path().apply {
    for (i in 0 until points * 2) {
        val radius = if (i % 2 == 0) outer else inner
        val a = -PI / 2 + i * PI / points
        val p = c + Offset((cos(a) * radius).toFloat(), (sin(a) * radius).toFloat())
        if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
    }
    close()
}

/**
 * Interlace, drawn as a rosette of overlapping ribbons.
 *
 * The first attempt was a self-crossing star polygon banded like a ribbon, and it did not read as
 * a knot at all — it read as a spiky star, which is Orbit's territory and made two of seven rows
 * the same idea. Overlapping circles are what actually looks woven: each ring passes through its
 * neighbours, and a heavy ink stroke with a lighter one laid inside gives every band two edges,
 * so the crossings read as over-and-under without anybody ordering them.
 *
 * True over-under would mean sorting every crossing, and at 44 dp nobody can see which strand
 * passes over. [over] instead offsets the whole rosette by half a lobe, which changes where the
 * crossings fall — a different knot, which is what the trait is for.
 */
private fun DrawScope.drawKnot(knot: AvatarTraits.Knot, ground: Color, c: Offset, r: Float) {
    val spin = knot.spin * (2 * PI / TURN_STEPS) + if (knot.over == 1) PI / knot.lobes else 0.0
    val ring = r * KNOT_RING
    val lobe = r * KNOT_LOBE
    val band = r * KNOT_BAND

    // Every ring is stroked in ink first and then cored out, in two passes rather than one ring at
    // a time: coring each ring as it is drawn would erase the ink of the ring before it, and the
    // rosette would unravel into a row of separate circles.
    val centres = List(knot.lobes) { i ->
        val a = spin + i * 2 * PI / knot.lobes
        c + Offset((cos(a) * ring).toFloat(), (sin(a) * ring).toFloat())
    }
    centres.forEach { drawCircle(Ink, lobe, it, style = Stroke(width = band, cap = StrokeCap.Round)) }
    val core = Stroke(width = band * KNOT_CORE, cap = StrokeCap.Round)
    centres.forEach { drawCircle(ground, lobe, it, style = core) }

    // A third and fourth strand bind the rosette with a ring around or through it, which is what
    // separates two strands from four at a glance rather than by counting.
    if (knot.strands >= BINDING_STRAND) {
        drawCircle(Ink, r * KNOT_BIND, c, style = Stroke(width = band, cap = StrokeCap.Round))
        drawCircle(ground, r * KNOT_BIND, c, style = core)
    }
    if (knot.strands >= EYE_STRAND) {
        drawCircle(Ink, r * KNOT_EYE, c, style = Stroke(width = band, cap = StrokeCap.Round))
        drawCircle(ground, r * KNOT_EYE, c, style = core)
    }
}

/**
 * A stem with branches struck off it, in the manner of Ogham.
 *
 * The cheapest family to make genuinely distinct, because it is mostly whitespace: branch count,
 * which side they leave on and how steeply are all readable at plate size, where a difference of
 * shading or weight would not be.
 */
private fun DrawScope.drawRune(rune: AvatarTraits.Rune, c: Offset, r: Float, pen: Stroke) {
    val top = c.y - r * STEM
    val foot = c.y + r * STEM
    engrave(Offset(c.x, top), Offset(c.x, foot), pen)

    val lean = BRANCH_FLAT + BRANCH_STEP * rune.angle
    val reach = r * BRANCH_LEN
    val gap = (foot - top) / (rune.branches + 1)
    for (i in 1..rune.branches) {
        val y = top + gap * i
        val sides = when (rune.side) {
            0 -> listOf(-1f)
            1 -> listOf(1f)
            else -> listOf(-1f, 1f)
        }
        for (side in sides) {
            val end = Offset(c.x + reach * side, y - reach * lean)
            engrave(Offset(c.x, y), end, pen)
        }
    }

    when (rune.head) {
        HEAD_PLAIN -> {
            Unit
        }
        HEAD_FORKED -> {
            // Forked, which is the strongest of the three at small size.
            engrave(Offset(c.x, top), Offset(c.x - r * HEAD_REACH, top - r * HEAD_RISE), pen)
            engrave(Offset(c.x, top), Offset(c.x + r * HEAD_REACH, top - r * HEAD_RISE), pen)
        }
        else -> {
            // Crossed: a bar struck through the stem's head.
            engrave(Offset(c.x - r * HEAD_REACH, top), Offset(c.x + r * HEAD_REACH, top), pen)
        }
    }
}

/** Arms winding out from the centre — the calmest family, and the quickest to read. */
private fun DrawScope.drawWhorl(whorl: AvatarTraits.Whorl, c: Offset, r: Float, pen: Stroke) {
    // Turns are spent per arm, not per figure. Four arms each winding three times at plate size
    // is not a whorl, it is a filled disc — which is exactly what the first draw produced, three
    // of six tiles solid black. The budget keeps the ink in a spiral whatever the two numbers are.
    val turns = (WHORL_BUDGET / whorl.arms).coerceIn(1, whorl.turns)
    // Thinner than the other families, and only here. A whorl is the one figure whose own arms
    // run alongside each other for their whole length, so the pen that reads as engraved on a
    // rune reads as a smear on a four-armed spiral.
    val sweep = turns * 2 * PI
    for (arm in 0 until whorl.arms) {
        val start = arm * 2 * PI / whorl.arms
        val path = Path()
        var t = 0f
        while (t <= 1f) {
            val a = start + sweep * t * whorl.hand
            // The square root is what makes this a whorl rather than a blot: a linear radius
            // spends most of its length near the centre, where consecutive turns are a stroke
            // width apart and merge. This leaves the eye quickly and opens out.
            val radius = r * (WHORL_EYE + (WHORL_R - WHORL_EYE) * sqrt(t))
            val p = c + Offset((cos(a) * radius).toFloat(), (sin(a) * radius).toFloat())
            if (t == 0f) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            t += 1f / WHORL_STEPS
        }
        drawPath(path, color = Ink, style = Stroke(width = pen.width * WHORL_PEN, cap = StrokeCap.Round))
    }

    when (whorl.pip) {
        0 -> {
            Unit
        }
        1 -> {
            drawCircle(color = Ink, radius = r * CENTRE_R, center = c)
        }
        else -> {
            drawCircle(color = Ink, radius = r * CENTRE_R, center = c, style = pen)
        }
    }
}

// The deck's own ink and the court cards' gold, so a generated face and a hand-drawn one sit
// together. Taken as literals rather than through the scheme because the emblems are fixed in
// both themes exactly as `avatar_*.xml` are — a face is not repainted by a phone's night setting.
private val Ink = Color(0xFF14181B)
private val Gold = Color(0xFFC9A227)

private const val TURN_STEPS = 12.0

private const val DISC = 0.98f
private const val RING = 0.04f
private const val HAIRLINE_R = 0.89f
private const val HAIRLINE = 0.016f
private const val PEN = 0.11f

private const val BAR_SPAN = 0.46f
private const val WAVE_TURNS = 2.0
private const val WAVE_DEPTH = 0.045f
private const val CURL_DEPTH = 0.16f
private const val STEPS = 24f

private const val EYE_NEAR = 0.20f
private const val EYE_STEP = 0.08f
private const val EYE_UP = 0.18f
private const val EYE_R = 0.10f
private const val BROW_UP = 0.24f
private const val BROW_TILT = 0.07f
private const val BROW_IN = 0.7f
private const val BROW_OUT = 1.35f
private const val MOUTH_W = 0.26f
private const val MOUTH_H = 0.16f
private const val MOUTH_DOWN = 0.26f
private const val START_UP = 200f
private const val START_DOWN = 20f
private const val SWEEP = 140f

private const val POLY_R = 0.34f
private const val PIP_NEAR = 0.46f
private const val PIP_STEP = 0.11f
private const val PIP_R = 0.045f
private const val CENTRE_R = 0.09f
private const val HALF_STEP = 0.5

// Heraldry's own names for the eight divisions, so a reader does not count branches to find one.
private const val BEND = 0
private const val CHEVRON = 1
private const val PALE = 2
private const val FESS = 3
private const val CROSS = 4
private const val SALTIRE = 5
private const val PILE = 6

/** No charge laid on the field — the shield as divided and nothing more. */
private const val PLAIN_FIELD = 0
private const val ROUNDEL = 1
private const val MULLET = 2

private const val EYE_DOT = 0
private const val EYE_RING = 1
private const val EYE_SLIT = 2

private const val HEAD_PLAIN = 0
private const val HEAD_FORKED = 1

/** Where a third strand starts binding the rosette, and where a fourth adds its eye. */
private const val BINDING_STRAND = 3
private const val EYE_STRAND = 4

private const val QUARTER = 1.5707964f
private const val DIAGONAL = 0.7853982f
private const val BAND_NEAR = 0.13f
private const val BAND_STEP = 0.07f
private const val PILE_W = 0.42f
private const val PILE_DROP = 0.72f
private const val LOZENGE = 0.72f
private const val LOZENGE_W = 0.72f
private const val CHEVRON_FOOT = 0.34f
private const val CHEVRON_PEAK = 0.30f
private const val CHARGE_R = 0.20f
private const val CHARGE_RIM = 1.08f
private const val CHARGE_PEN = 0.42f
private const val STAR_OUT = 1.25f
private const val STAR_IN = 0.5f
private const val MULLET_POINTS = 5

private const val KNOT_RING = 0.30f
private const val KNOT_LOBE = 0.36f
private const val KNOT_BAND = 0.13f
private const val KNOT_CORE = 0.45f
private const val KNOT_BIND = 0.66f
private const val KNOT_EYE = 0.16f

private const val STEM = 0.60f
private const val BRANCH_LEN = 0.30f
private const val BRANCH_FLAT = -0.15f
private const val BRANCH_STEP = 0.55f
private const val HEAD_REACH = 0.20f
private const val HEAD_RISE = 0.16f

private const val WHORL_R = 0.70f

/** Where an arm starts. Wide, because the centre is where consecutive turns merge. */
private const val WHORL_EYE = 0.26f
private const val WHORL_STEPS = 96f
private const val WHORL_PEN = 0.68f

/** Total turns a figure may spend, shared out between its arms. */
private const val WHORL_BUDGET = 2

/** The picker's tile, and the felt's plate. */
val AvatarSheetSize: Dp = 64.dp

/**
 * The grounds a player may choose between — and the reason the choice is a list rather than a
 * colour wheel.
 *
 * Two claims constrain every one of these, and neither survives a freely picked hue:
 *
 *  * **The ink has to be readable on it.** `Ink` is laid over the disc at [PEN] weight and the
 *    gold hairline sits just inside the rim. WCAG 1.4.11 asks 3:1 for graphics that carry
 *    meaning, and a mark that identifies a seat is exactly that. `ContrastTest` measures each
 *    swatch here against both, so "allowed" is a number rather than a judgement.
 *  * **It has to hold an edge against the felt.** `_shared.md` says the green seat is teal
 *    rather than `FELT`'s own #1B5E43, because a disc in the table's colour has no edge on the
 *    table. Every ground here is chosen to be near enough to the deck's family to belong and far
 *    enough from the cloth to be seen on it.
 *
 * The four hand-drawn emblems' own disc colours lead the list, so a generated face and a bot's
 * sit in one palette rather than two.
 */
val AvatarGrounds: List<Color> = listOf(
    Color(0xFFB9C6CC), // Gale's pewter
    Color(0xFFD98C5F), // Ember's clay
    Color(0xFF7FB2B8), // Tide's teal
    Color(0xFFC9B072), // Dune's sand
    Color(0xFFC5A3B5), // heather
    Color(0xFF9FB98A), // sage
    Color(0xFFD5C7A9), // bone
    Color(0xFFA9B4CE), // slate blue
)
