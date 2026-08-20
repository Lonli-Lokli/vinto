package game.vinto.app.game

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** How large one card is drawn. */
data class CardScale(val width: Dp, val height: Dp)

/**
 * How one card is being drawn: what can be done to it, and what is happening to it.
 *
 * One object rather than five parameters, because they travel together — every caller that
 * knows one knows all of them, and a card's *appearance* is a single idea.
 */
data class CardState(
    val tappable: Boolean = false,
    /** This action has already been aimed at it. */
    val chosen: Boolean = false,
    /** Lying sideways, as a card does in front of somebody at the side of the table. */
    val turned: Boolean = false,
    /** Flinching, because a penalty card just landed in this hand. */
    val flinching: Boolean = false,
    /** A declaration was answered on this card: true for a right call, false for a wrong one. */
    val verdict: Boolean? = null,
)

/**
 * The table's proportions, chosen from the height it actually has.
 *
 * Four hands, two piles and four portraits have to fit whatever is left once the control
 * panel has taken what it needs — and what it needs varies a lot, from one button to fourteen
 * rank chips. Rather than let the bottom of the table slide off the screen, the whole table
 * steps down a size. Two steps, not a continuum: a smoothly scaling card table ends up with
 * cards that are a different size every turn, which is worse to read than a small one.
 *
 * The tap target stays 44dp regardless (see [CardFace]); what shrinks is the picture, not the
 * area a thumb has to find.
 */
data class TableSizes(
    val mine: CardScale,
    val theirs: CardScale,
    val side: CardScale,
    val avatar: Dp,
    val avatarMine: Dp,
) {
    companion object {
        val Corner = 8.dp

        private val Roomy = TableSizes(
            mine = CardScale(44.dp, 62.dp),
            theirs = CardScale(44.dp, 62.dp),
            side = CardScale(40.dp, 56.dp),
            avatar = 38.dp,
            avatarMine = 42.dp,
        )

        private val Tight = TableSizes(
            mine = CardScale(38.dp, 53.dp),
            theirs = CardScale(34.dp, 48.dp),
            side = CardScale(31.dp, 44.dp),
            avatar = 30.dp,
            avatarMine = 34.dp,
        )

        /** Below this the roomy table cannot fit four hands and two piles without clipping. */
        private val ROOMY_FLOOR = 560.dp

        fun forHeight(height: Dp): TableSizes = if (height >= ROOMY_FLOOR) Roomy else Tight

        /**
         * The size to draw at, decided from the **screen** rather than from the felt.
         *
         * The felt is what is left once the control panel has taken what it needs, and what
         * it needs changes with the phase — one button for a turn, fourteen rank chips for a
         * King. Sizing the cards from that means every phase change is a chance for the whole
         * table to change size, which on a phone reads as the game jumping under your thumb.
         *
         * So the decision is made once, from a quantity that does not move, using the panel's
         * floor rather than its actual height. A panel that overflows its floor still pushes
         * the felt up — there is nowhere else for it to go — but the cards keep their size and
         * their positions relative to each other, and the felt's middle row absorbs it.
         */
        fun forScreen(screen: Dp): TableSizes = forHeight(screen - HeaderHeight - panelFloor(screen))
    }
}

/** The strip above the felt: the wordmark, the round, the bug button, the deck count. */
val HeaderHeight = 44.dp

/**
 * The height the control panel holds on to whether or not it needs it.
 *
 * A panel that is exactly as tall as its contents is a panel that changes height on every
 * phase of every turn, and since the felt takes what is left, the entire table shifts each
 * time. Reserving the height of a *typical* panel — a prompt, a rule, and two buttons — means
 * the common cases cost nothing at all: draw, swap, discard and toss-in all fit inside it and
 * move nothing.
 *
 * Capped as a fraction of the screen so a small phone does not end up with a rail and no felt.
 */
fun panelFloor(screen: Dp): Dp = minOf(PANEL_FLOOR_MAX, screen * PANEL_FLOOR_FRACTION)

private val PANEL_FLOOR_MAX = 268.dp
private const val PANEL_FLOOR_FRACTION = 0.32f
