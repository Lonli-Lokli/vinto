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
         * So the decision is made once, from a quantity that does not move: the screen, minus
         * the header, minus the rail — and the rail is a fixed share of the screen rather than
         * whatever its contents ask for. The felt is the remainder, and the remainder is the
         * same in every phase of every turn.
         */
        fun forScreen(screen: Dp): TableSizes = forHeight(screen - HeaderHeight - railHeight(screen))
    }
}

/**
 * How much room each part of the screen gets.
 *
 * Both numbers are decided together, from the screen, once: how large a card is drawn and how
 * tall the rail is. Passing them separately is passing two halves of one decision, and the
 * halves can disagree.
 */
data class TableLayout(val sizes: TableSizes, val railHeight: Dp) {
    companion object {
        fun forScreen(screen: Dp): TableLayout =
            TableLayout(TableSizes.forScreen(screen), railHeight(screen))
    }
}

/** The strip above the felt: the wordmark, the round, the help, the bug, the deck count. */
val HeaderHeight = 44.dp

/**
 * How tall the control rail is: **a fixed share of the screen**, and never anything else.
 *
 * It used to be a floor that content could push past, which meant the felt was whatever was
 * left over — and the felt changed size whenever the panel did. A King's fourteen rank chips
 * arrive, the table shrinks; the chips go, it grows back. Animating the change made it a slide
 * rather than a jump, which is a nicer way to move something that should not be moving at all:
 * every card, every seat and every pile shifts under a thumb already on its way to one of them.
 *
 * So the rail is a proportion, clamped at both ends — a third of a phone, but never so short
 * that the controls crowd nor so tall that the table is a strip. Everything else follows from
 * it: the felt is exactly what remains, on every screen and in every phase, and the panel's
 * contents adapt to the rail rather than the other way round.
 */
fun railHeight(screen: Dp): Dp = (screen * RAIL_FRACTION).coerceIn(RAIL_MIN, RAIL_MAX)

private const val RAIL_FRACTION = 0.31f
private val RAIL_MIN = 240.dp
private val RAIL_MAX = 300.dp
