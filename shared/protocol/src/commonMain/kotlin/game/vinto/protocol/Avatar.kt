package game.vinto.protocol

import game.vinto.shapes.Prng

/**
 * A seat's face, generated from a number rather than drawn by anybody.
 *
 * ### Why a seed and not a picture
 *
 * The four engraved emblems in `brand/avatars` are the bots' own, and there are exactly four of
 * them for exactly four seats — so a player choosing one sits down next to a bot wearing the same
 * face. Growing that set means authoring masters, and every master added is a file in the bundle,
 * a rule in `_shared.md` to satisfy, and a ceiling that arrives again later.
 *
 * A seed has none of those. **The wire carries the number and every client draws the same face**,
 * which is the same arrangement the deck itself uses: `GameState.rngState` is a seed and the deal
 * is a pure function of it. [Prng] is the reason this is safe to promise — mulberry32, pinned by
 * `fixtures/prng/vectors.json` and run on JVM, JS, Wasm and Kotlin/Native, so "the same seed draws
 * the same face on every client" is a property already under test rather than a hope. A `Long` is
 * two `Int`s on Kotlin/JS, and that is precisely the trap those vectors exist to catch.
 *
 * ### Geometry, and not a glyph
 *
 * Emoji were considered and are the one option that fails the requirement they were suggested to
 * meet. The app bundles Cinzel and Fira — text faces, no emoji font — so a codepoint falls through
 * to the platform: Noto Color Emoji on Android, Apple Color Emoji on iOS, whatever a browser has on
 * web. Three different pictures for one identity, on a thing whose whole caption is *what the other
 * seats see*: the player on Android and the player on an iPhone would see different faces for the
 * same person at the same table. `Panels.kt` already settled this for a close button — "drawn as
 * strokes rather than fetched from a glyph font" — and an identity has more riding on it than a ✕.
 *
 * ### Why the meaning of the seed lives in the protocol
 *
 * Nothing here is drawn. What is here is the *interpretation* of the number on the wire, and two
 * clients disagreeing about what seed 42 looks like is a wire disagreement, not a UI difference —
 * the same reasoning that moved `looksLikeRoomCode` into this module so the client and the room
 * could not disagree about it. `composeApp` turns [AvatarTraits] into strokes; this decides what
 * the traits are.
 *
 * ### The seed says nothing about anybody
 *
 * A nickname needs [looksMinted] because free text is the thing that reaches another player's
 * screen. **A seed needs no such door.** Any 32 bits name some face, none of them names a word,
 * and a hostile client that sends a number it made up gets a different geometric mark rather than
 * a message. That is a real simplification and it is deliberate: there is no allow-list here to
 * keep in step, because there is nothing to allow. [AvatarKind] is the one value with a range,
 * and an unknown one falls back rather than failing — see [avatarKindOf].
 *
 * What the caller must NOT do is put a clock on the wire. A seed is minted from the clock once, at
 * the moment a player picks, and the raw timestamp is discarded — a profile that carried one would
 * quietly say when it was made, which is a correlator between two rooms and the sort of thing this
 * project keeps out of its telemetry on principle.
 *
 * ### Colour is not generated, and that is not an oversight
 *
 * `brand/avatars/_shared.md` promises seats are told apart by **how the lines run** — direction,
 * then waver, then curl — and never by hue, because the app claims `differentiateWithoutColorAlone`
 * to Apple. Generated geometry keeps that promise. Generated *colour* would break the other one:
 * a hue drawn from a seed cannot be known to clear WCAG against the ink on top of it, so the
 * ground is chosen from a measured palette instead and `ContrastTest` measures every swatch.
 */

/** How many faces one family offers at a time — a phone fits six across at plate size. */
public const val AVATAR_ROW: Int = 6

/**
 * The families a face can belong to, one per row of the picker.
 *
 * Seven rather than one because a sheet of forty variations on a single idea reads as forty of the
 * same thing however different the numbers are. The families differ in *kind* — bars, a face, a
 * ring of pips, a shield's division, an interlace, a struck stem, a whorl — so two seats are told
 * apart at a glance before any detail resolves, which is the 44 dp test `_shared.md` sets for the
 * emblems these sit beside.
 *
 * The family is carried on the wire beside the seed rather than drawn from it. That is what lets
 * the picker show a row *of* a family: deriving the kind from the seed would mean searching for
 * seeds that happen to land on the row being drawn.
 */
enum class AvatarKind {
    /**
     * Eyes, a brow and a mouth — and the row the picker opens on.
     *
     * First because it is the one family a player recognises without being taught what they are
     * looking at. The six abstract families are better marks, and every one of them is a shape
     * somebody has to decide they like; a face is a thing you either are or are not, which is a
     * quicker decision to make on the way into a game.
     *
     * **The order is the wire order.** [AvatarKind.ordinal] is what a profile carries, so moving
     * a family after builds are in players' hands would repaint everybody. Reordered here while
     * nothing has shipped, and not again after.
     */
    FACE,

    /** Engraved bars, in the manner of the four hand-drawn emblems. */
    MARK,

    /** A polygon inside rings of pips. */
    ORBIT,

    /** A shield's division and its charge — the system built for telling people apart. */
    HERALD,

    /** Interlaced bands, crossing over and under. */
    KNOT,

    /** A stem with branches struck off it, in the manner of Ogham. */
    RUNE,

    /** Arms winding out from the centre. */
    WHORL,
}

/**
 * The family named by [ordinal], falling back to [AvatarKind.MARK] for anything unknown.
 *
 * The wire carries an integer, and a build one version older has never heard of the family a
 * newer one is using. Falling back draws *a* face rather than none, which is the right failure:
 * the seat still has a mark, it is simply not the one its owner chose. Refusing would leave a
 * hole on the felt, and throwing would take the table down over a picture.
 */
public fun avatarKindOf(ordinal: Int): AvatarKind =
    AvatarKind.entries.getOrElse(ordinal) { AvatarKind.MARK }

/**
 * What to draw, as numbers a renderer interprets — never pixels, colours or paths.
 *
 * A sealed hierarchy rather than one bag of nullable fields, so a renderer's `when` is exhaustive
 * and an eighth family is a compile error at every call site rather than a face that draws nothing.
 * Every value is small and bounded, because the renderer must be total: there is no seed that
 * produces an invalid face, and so no seed that needs refusing.
 */
sealed interface AvatarTraits {
    val kind: AvatarKind

    /**
     * Bars, in the manner of the hand-drawn emblems: direction first, then waver, then curl.
     *
     * [tilt] is quantised to twelve steps rather than free degrees — two marks a few degrees apart
     * are not two identities, they are one identity drawn twice, and a picker full of near-misses
     * is worse than a smaller set of things that differ.
     */
    data class Mark(
        /** 2..4 bars. One is a line, five is a texture. */
        val bars: Int,
        /** 0..11, in thirty-degree steps. */
        val tilt: Int,
        /** 0..3: dead straight, through to fully rolled. */
        val waver: Int,
        /** -1, 0 or 1: curl away at the end, none, or curl toward. */
        val curl: Int,
    ) : AvatarTraits {
        override val kind: AvatarKind get() = AvatarKind.MARK
    }

    /** A face, engraved in the same weight of line — eyes, a brow and a mouth, and nothing else. */
    data class Face(
        /** 0..3: the eye shape. */
        val eyes: Int,
        /** 0..2: how far apart they sit. */
        val spacing: Int,
        /** -1, 0 or 1: the brow's angle, which is most of the expression. */
        val brow: Int,
        /** 0..3: the mouth's curve, from down through flat to up. */
        val mouth: Int,
    ) : AvatarTraits {
        override val kind: AvatarKind get() = AvatarKind.FACE
    }

    /** A ring of pips around a polygon — the most abstract of the seven, and the most various. */
    data class Orbit(
        /** 3..8 sides on the inner figure. */
        val sides: Int,
        /** 1..3 rings of pips around it. */
        val rings: Int,
        /** 0..11, in thirty-degree steps: how far the whole figure is turned. */
        val spin: Int,
        /** 0..2: what sits at the centre — nothing, a dot, a second figure. */
        val pip: Int,
    ) : AvatarTraits {
        override val kind: AvatarKind get() = AvatarKind.ORBIT
    }

    /**
     * A shield's division and what is laid on it.
     *
     * Heraldry is the system that exists for this exact problem — telling people apart at a
     * distance, by shape, under bad conditions — so its vocabulary is the one to borrow rather
     * than invent. It also sits beside the court cards without explanation.
     */
    data class Herald(
        /** 0..7: bend, chevron, pale, fess, cross, saltire, pile, lozenge. */
        val division: Int,
        /** 0..3: nothing laid on it, a roundel, a mullet, a lozenge. */
        val charge: Int,
        /** 0..1: the division mirrored, which heraldry calls "sinister". */
        val flip: Int,
        /** 0..2: how heavy the division is drawn. */
        val weight: Int,
    ) : AvatarTraits {
        override val kind: AvatarKind get() = AvatarKind.HERALD
    }

    /** Interlaced bands. [strands] is capped low: a busy knot is texture, not a shape, at 44 dp. */
    data class Knot(
        /** 2..4 bands. */
        val strands: Int,
        /** 3..6 lobes around the ring. */
        val lobes: Int,
        /** 0..1: which band passes over at the first crossing, which flips the whole weave. */
        val over: Int,
        /** 0..11, in thirty-degree steps. */
        val spin: Int,
    ) : AvatarTraits {
        override val kind: AvatarKind get() = AvatarKind.KNOT
    }

    /** A stem with branches struck off it — mostly whitespace, so legible at any size. */
    data class Rune(
        /** 1..4 branches. */
        val branches: Int,
        /** 0..2: struck left, right, or both. */
        val side: Int,
        /** 0..2: how steeply they leave the stem. */
        val angle: Int,
        /** 0..2: the stem's own head — plain, forked, crossed. */
        val head: Int,
    ) : AvatarTraits {
        override val kind: AvatarKind get() = AvatarKind.RUNE
    }

    /** Arms winding out from the centre. The calmest family, and the quickest to read. */
    data class Whorl(
        /** 1..4 arms. */
        val arms: Int,
        /** 1..3 turns before they run out. */
        val turns: Int,
        /** -1 or 1: which way they wind. */
        val hand: Int,
        /** 0..2: what sits at the centre. */
        val pip: Int,
    ) : AvatarTraits {
        override val kind: AvatarKind get() = AvatarKind.WHORL
    }
}

/**
 * The face for [kind] and [seed], which is a pure function of both.
 *
 * Each trait is a fresh draw from [Prng] rather than a slice of the seed's own bits. That is the
 * same lesson `mintNickname` records in its own comment: reading traits straight out of the number
 * makes stepping the seed by one change exactly one trait, so a row of consecutive seeds walks a
 * single axis and looks broken rather than various. Advancing the generator between draws
 * decorrelates them for free.
 *
 * The family is mixed into the state before the first draw, so the same seed under two families
 * does not produce the same run of numbers — otherwise every row of the picker would be the same
 * shape in seven costumes.
 */
public fun mintAvatar(kind: AvatarKind, seed: Long): AvatarTraits {
    var state = Prng.seed(seed + kind.ordinal * FAMILY_STRIDE)
    fun pick(range: IntRange): Int {
        val drawn = Prng.nextInt(state, range.last - range.first + 1)
        state = drawn.state
        return range.first + drawn.value.toInt()
    }

    return when (kind) {
        AvatarKind.MARK -> AvatarTraits.Mark(
            bars = pick(AvatarRanges.BARS),
            tilt = pick(AvatarRanges.TURN),
            waver = pick(AvatarRanges.WAVER),
            curl = pick(AvatarRanges.SWING),
        )

        AvatarKind.FACE -> AvatarTraits.Face(
            eyes = pick(AvatarRanges.EYES),
            spacing = pick(AvatarRanges.SPACING),
            brow = pick(AvatarRanges.SWING),
            mouth = pick(AvatarRanges.MOUTH),
        )

        AvatarKind.ORBIT -> AvatarTraits.Orbit(
            sides = pick(AvatarRanges.SIDES),
            rings = pick(AvatarRanges.RINGS),
            spin = pick(AvatarRanges.TURN),
            pip = pick(AvatarRanges.PIP),
        )

        AvatarKind.HERALD -> AvatarTraits.Herald(
            division = pick(AvatarRanges.DIVISION),
            charge = pick(AvatarRanges.CHARGE),
            flip = pick(AvatarRanges.FLIP),
            weight = pick(AvatarRanges.WEIGHT),
        )

        AvatarKind.KNOT -> AvatarTraits.Knot(
            strands = pick(AvatarRanges.STRANDS),
            lobes = pick(AvatarRanges.LOBES),
            over = pick(AvatarRanges.FLIP),
            spin = pick(AvatarRanges.TURN),
        )

        AvatarKind.RUNE -> AvatarTraits.Rune(
            branches = pick(AvatarRanges.BRANCHES),
            side = pick(AvatarRanges.SIDE),
            angle = pick(AvatarRanges.ANGLE),
            head = pick(AvatarRanges.HEAD),
        )

        AvatarKind.WHORL -> AvatarTraits.Whorl(
            arms = pick(AvatarRanges.ARMS),
            turns = pick(AvatarRanges.TWISTS),
            // -1 or 1, never 0: a whorl that winds neither way is not a whorl.
            hand = if (pick(AvatarRanges.FLIP) == 0) -1 else 1,
            pip = pick(AvatarRanges.PIP),
        )
    }
}

/**
 * Every trait's range, named once.
 *
 * Here rather than inline because these bounds are read twice — the generator draws inside them
 * and `AvatarTest` asserts a renderer can rely on them — and two copies of a bound is how a
 * widened trait ships with a test that still passes on the old range. A renderer may treat every
 * value it is handed as within these, which is what lets it be total and so lets a seed never
 * need refusing.
 */
object AvatarRanges {
    /** Thirty-degree steps, shared by everything that rotates. */
    val TURN: IntRange = 0..11

    /** -1, 0 or 1 — a curl, a brow, anything that leans one way or the other. */
    val SWING: IntRange = -1..1

    /** 0 or 1, for a trait that is simply on or off. */
    val FLIP: IntRange = 0..1

    /** Nothing, a dot, or a figure — what sits at a centre. */
    val PIP: IntRange = 0..2

    val BARS: IntRange = 2..4
    val WAVER: IntRange = 0..3

    val EYES: IntRange = 0..3
    val SPACING: IntRange = 0..2
    val MOUTH: IntRange = 0..3

    val SIDES: IntRange = 3..8
    val RINGS: IntRange = 1..3

    val DIVISION: IntRange = 0..7
    val CHARGE: IntRange = 0..3
    val WEIGHT: IntRange = 0..2

    val STRANDS: IntRange = 2..4
    val LOBES: IntRange = 3..6

    val BRANCHES: IntRange = 1..4
    val SIDE: IntRange = 0..2
    val ANGLE: IntRange = 0..2
    val HEAD: IntRange = 0..2

    val ARMS: IntRange = 1..4
    val TWISTS: IntRange = 1..3
}

/**
 * Keeps one seed from drawing the same run of numbers under two families.
 *
 * A large odd stride rather than the ordinal itself: adding 0..6 to a seed would make family N's
 * row overlap family N+1's by all but one entry, so the picker's seven rows would be seven views
 * of nearly the same set.
 */
private const val FAMILY_STRIDE = 0x9E3779B9L

/**
 * A row of [count] seeds for one family, walked from [from].
 *
 * Not `from`, `from + 1`, `from + 2`: [Prng] decorrelates consecutive states well, but a row is
 * the one place six faces are seen side by side, and side by side is where any residual structure
 * shows. Each seed is a full draw, so a row is six independent faces of one family.
 *
 * [from] is the caller's business. The picker mints it from the clock so that opening the sheet
 * twice offers different faces and the regenerate button has something to change; a test passes a
 * constant and gets the same row every run.
 */
public fun avatarRow(kind: AvatarKind, from: Long, count: Int = AVATAR_ROW): List<Long> {
    require(count > 0) { "avatarRow requires a positive count, got $count" }
    // The family is mixed in here as well, so one `from` gives seven different rows rather than
    // seven rows of the same seeds wearing different geometry.
    var state = Prng.seed(from + kind.ordinal * FAMILY_STRIDE)
    return List(count) {
        val drawn = Prng.next(state)
        state = drawn.state
        drawn.value
    }
}

/** Every family's row for one sheet — what the picker draws, in family order. */
public fun avatarSheet(from: Long, count: Int = AVATAR_ROW): Map<AvatarKind, List<Long>> =
    AvatarKind.entries.associateWith { avatarRow(it, from, count) }
