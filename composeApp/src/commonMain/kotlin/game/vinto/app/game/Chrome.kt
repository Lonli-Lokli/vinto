package game.vinto.app.game

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import game.vinto.app.Reserved

/**
 * What the system keeps of a table's box, in the box's own coordinates: a status column down one
 * edge, a status strip across the top, the clock and the camera standing in either, and the fold of
 * a half-open phone.
 *
 * **The table is the one screen that stands its own controls in the system's space** rather than
 * keeping clear of it. The closed iPhone Duo stands its clock and camera in a column down the right
 * edge, 84 of 466 points, and the open one does the same with its clock alone; Apple's own toolbars
 * stack their buttons in that column, under the status, and the table's do too ([StatusColumn]). An
 * inset says only how wide the column is; the occlusions ([Reserved]) say where in it the clock and
 * the camera stand, so the controls can start under the one and stop short of the other ([run]).
 *
 * Everything here is in the table box's coordinates — the window's, less the padding `App` gave the
 * box — so a screen can lay the table out from it without knowing what the window was.
 */
data class Chrome(
    /** The status column's width, or zero where the system keeps its status across the top. */
    val column: Dp = 0.dp,
    /** Which edge the column stands at — the physical one, whichever way the language reads. */
    val columnOnRight: Boolean = true,
    /**
     * The strip across the top the system keeps for its status, where a table may stand its header
     * beside the clock ([stripEnd]); zero where there is none to share, which is every phone but the
     * open iPhone Duo held upright.
     */
    val strip: Dp = 0.dp,
    /** Where across the [strip] the system's own things begin: the header ends there. */
    val stripEnd: Dp = 0.dp,
    /** The clock, the connectivity and the camera, where the system says they are. */
    val occlusions: List<DpRect> = emptyList(),
    /** The fold of a half-open phone, or null while it is flat or closed. */
    val division: DpRect? = null,
) {
    /**
     * The padding that keeps clear of the column, on its own edge: for what opens over a table, a
     * sheet, which leaves the column to the clock and the camera ([game.vinto.app.LocalStatusColumn]).
     */
    val clearOfColumn: PaddingValues
        get() = if (columnOnRight) {
            PaddingValues.Absolute(right = column)
        } else {
            PaddingValues.Absolute(left = column)
        }

    /** Which way a half-open phone is being held, from which way its fold runs; null while flat. */
    val folded: Posture?
        get() = division?.let { if (it.standsUp()) Posture.BOOK else Posture.LAPTOP }

    /**
     * The length of the status column a table may use, in a box [width] by [height]: from under
     * what the system stands at its top to above what it stands at its foot. Null without a column.
     *
     * Told from the occlusions inside the column, each by which half of it its middle is in: the
     * closed Duo's clock and camera are its top 150 points, the open one's clock its top 100, and a
     * closed Duo turned on its side would carry its camera at the foot instead. Where the system
     * reports nothing at all, the column's top is left as if a clock and a camera stood there
     * ([UnreportedTop]) — the controls a little lower than they need be is the safe way to be wrong.
     */
    fun run(width: Dp, height: Dp): ColumnRun? {
        if (column <= 0.dp) return null
        val from = if (columnOnRight) width - column else 0.dp
        val inColumn = occlusions.filter { it.right > from && it.left < from + column }
        val middle = height / 2
        val above = inColumn.filter { it.top + it.bottom < middle * 2 }
        val below = inColumn - above.toSet()
        val top = when {
            occlusions.isEmpty() -> UnreportedTop
            above.isEmpty() -> RunGap
            else -> above.maxOf { it.bottom } + RunGap
        }
        val bottom = below.minOfOrNull { it.top }?.let { it - RunGap } ?: height - RunGap
        return ColumnRun(column, columnOnRight, top, bottom)
    }

    /**
     * The two halves a half-open phone's fold divides a box [width] by [height] into, or null while
     * nothing divides it. A fold that stands up the screen is a book's spine, and the pages are left
     * and right of it; one that lies across it is a laptop's hinge, and they are above and below.
     *
     * A fold reported thinner than a [Crease] is widened to one, about its own middle: a card laid
     * right up against the line would still be bent across it.
     */
    fun pages(width: Dp, height: Dp): FoldPages? {
        val fold = division ?: return null
        return if (fold.standsUp()) {
            val (left, right) = widened(fold.left, fold.right)
            FoldPages(DpRect(0.dp, 0.dp, left, height), DpRect(right, 0.dp, width, height))
        } else {
            val (top, bottom) = widened(fold.top, fold.bottom)
            FoldPages(DpRect(0.dp, 0.dp, width, top), DpRect(0.dp, bottom, width, height))
        }
    }

    private fun widened(from: Dp, to: Dp): Pair<Dp, Dp> {
        val short = (Crease - (to - from)).coerceAtLeast(0.dp) / 2
        return Pair(from - short, to + short)
    }

    companion object {
        /** Nothing kept: every screen without a column, a strip or a fold, and every test that says none. */
        val NONE = Chrome()
    }
}

/**
 * The stretch of a status column the table's controls may stand in: [top] is under the clock and the
 * camera, [bottom] above whatever stands at the column's foot, both in the table box's coordinates.
 */
data class ColumnRun(val width: Dp, val onRight: Boolean, val top: Dp, val bottom: Dp) {
    /** How much of the column the controls have. */
    val length: Dp get() = (bottom - top).coerceAtLeast(0.dp)
}

/**
 * How the table lies on the screen it was dealt.
 *
 * Upright and on its side are the two every screen has had ([TableLayout]). A half-open phone adds
 * two more, named for how it is being held: a **book**, its fold standing up the middle, the felt on
 * one page and the rail on the other ([Book]); and a **laptop**, its fold lying across it, the other
 * three seats standing up above the fold and your own hand lying flat below ([Laptop]). Each is a
 * shape of its own ([TableShape]), so folding the phone sets the table gliding to it ([Reshape]).
 */
enum class Posture {
    UPRIGHT,
    ON_ITS_SIDE,
    BOOK,
    LAPTOP,
    ;

    /** Half open, split at the fold: a book or a laptop. */
    val folded: Boolean get() = this == BOOK || this == LAPTOP
}

/** The two halves of a half-open screen, in the table box's coordinates: left and right, or top and foot. */
data class FoldPages(val first: DpRect, val second: DpRect)

/**
 * The four edges the system keeps for itself, by the side of the glass they are on — left and right,
 * not start and end. A status column stands at the right edge of the Duo whichever way the language
 * reads, and told from the end inset, an Arabic or a Hebrew table looked for it on the left.
 */
data class Sides(val left: Dp = 0.dp, val top: Dp = 0.dp, val right: Dp = 0.dp, val bottom: Dp = 0.dp)

/**
 * The edges a platform reports, by the side of the glass. The Duo's column was read as the end inset,
 * which on a right-to-left screen is the left one, where there is nothing: an Arabic or a Hebrew
 * table saw no column at all, and drew its header row under the clock.
 */
fun sidesOf(insets: PaddingValues, direction: LayoutDirection): Sides = Sides(
    left = insets.calculateLeftPadding(direction),
    top = insets.calculateTopPadding(),
    right = insets.calculateRightPadding(direction),
    bottom = insets.calculateBottomPadding(),
)

/** What [chromeOf] decides: what a table is told of the system's edges, and the padding around its box. */
data class Framed(val chrome: Chrome, val padding: Sides)

/**
 * What a screen keeps clear of, and what a table is told it may stand in instead.
 *
 * Every screen is padded out of the system's way ([insets]). A table ([table]) is the exception twice
 * over, and only where the system has said where its own things stand:
 *
 *  - **A status column** — a wide edge inset with no bar above it, the closed iPhone Duo's (84 of 466
 *    points) and the open one's on its side. Left unpadded for the table to stand its controls in,
 *    and kept clear of by every other screen. On the right whenever the right edge is that wide; on
 *    the left only with something of the system's inside it, since a wide left inset with nothing in
 *    it is a cut-out or a rounded corner rather than a column.
 *  - **A status strip** — a top inset at least as deep as a header, holding nothing but things at
 *    its far end ([StripRoom] clear of the left edge): the open Duo held upright, its clock in the
 *    corner of an 80-point strip. Left unpadded too, and the table stands its header beside the
 *    clock rather than under it. A phone whose island or clock stands in the middle or at the left of
 *    its strip keeps the strip to itself, and a strip nothing has been reported in is never shared:
 *    on an iPhone before iOS 27.1, which reports nothing, the header would be drawn under the clock.
 *
 * Pure, so the arithmetic is asserted directly (`TheDuosEdgesTest`).
 */
fun chromeOf(insets: Sides, reserved: Reserved, table: Boolean): Framed {
    if (!table) return Framed(Chrome.NONE, insets)
    val side = columnSide(insets, reserved)
    val stripEnd = if (side == null) sharedStrip(insets, reserved) else null
    val padding = Sides(
        left = if (side == ColumnSide.LEFT) 0.dp else insets.left,
        top = if (stripEnd != null) 0.dp else insets.top,
        right = if (side == ColumnSide.RIGHT) 0.dp else insets.right,
        bottom = insets.bottom,
    )
    val chrome = Chrome(
        column = when (side) {
            ColumnSide.RIGHT -> insets.right
            ColumnSide.LEFT -> insets.left
            null -> 0.dp
        },
        columnOnRight = side != ColumnSide.LEFT,
        strip = if (stripEnd != null) insets.top else 0.dp,
        stripEnd = stripEnd?.let { it - padding.left } ?: 0.dp,
        occlusions = reserved.occlusions.map { it.movedBy(padding) },
        division = reserved.division?.movedBy(padding),
    )
    return Framed(chrome, padding)
}

/** The two edges a status column can stand at. */
private enum class ColumnSide { LEFT, RIGHT }

/** Which edge a status column stands at, or null where the system keeps a bar across the top instead. */
private fun columnSide(insets: Sides, reserved: Reserved): ColumnSide? {
    if (insets.top >= ColumnTopMost) return null
    if (insets.right >= ColumnFrom) return ColumnSide.RIGHT
    val held = insets.left >= ColumnFrom && reserved.occlusions.any { it.left < insets.left }
    return if (held) ColumnSide.LEFT else null
}

/**
 * Where across the window the system's things begin in a status strip a table may share, or null
 * where it may not: a strip too shallow for a header, one with nothing reported in it, one with
 * something reaching below it, or one whose things stand too far to the left.
 */
private fun sharedStrip(insets: Sides, reserved: Reserved): Dp? {
    if (insets.top < StripFrom) return null
    val inStrip = reserved.occlusions.filter { it.top < insets.top }
    if (inStrip.isEmpty() || inStrip.any { it.bottom > insets.top }) return null
    return inStrip.minOf { it.left }.takeIf { it - insets.left >= StripRoom }
}

/** Whether a fold stands up the screen, a book's spine, rather than lying across it. */
private fun DpRect.standsUp(): Boolean = bottom - top > right - left

/** A region of the window, in the coordinates of a box padded by [padding] inside it. */
private fun DpRect.movedBy(padding: Sides): DpRect =
    DpRect(left - padding.left, top - padding.top, right - padding.left, bottom - padding.top)

/** What the system keeps of the table's box. Provided by `App` for the screens that hold a table. */
val LocalChrome = staticCompositionLocalOf { Chrome.NONE }

/** Narrower than this, an edge inset is a rounded corner or a cut-out rather than a column. */
private val ColumnFrom = 56.dp

/** And a screen with a real bar across the top keeps its status there. */
private val ColumnTopMost = 24.dp

/** A top inset shallower than this is a status bar, not a strip with room for a header in it. */
private val StripFrom = 56.dp

/**
 * How much of a strip has to be clear of the system's things, from the left, for the header to share
 * it: room for the wordmark, the counter and three marks. An island in the middle of an ordinary
 * iPhone's strip leaves well under this; the open Duo's clock in its corner leaves over 500.
 */
private val StripRoom = 300.dp

/** Between the system's things and the table's controls, at either end of the column. */
private val RunGap = 8.dp

/**
 * Where the column's controls start when the system has said nothing about what stands in it: under
 * where the closed Duo's clock and camera end (150 points), with room to spare.
 */
private val UnreportedTop = 160.dp

/** The least room a fold is given, so that a card laid right up against it is not bent across it. */
private val Crease = 8.dp
