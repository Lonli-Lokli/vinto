package game.vinto.app.game

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** How large one card is drawn. */
data class CardScale(val width: Dp, val height: Dp)

/** Whether a card can be touched now, and whether this action has already claimed it. */
data class CardState(val tappable: Boolean = false, val chosen: Boolean = false)

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
            mine = CardScale(50.dp, 70.dp),
            theirs = CardScale(44.dp, 62.dp),
            side = CardScale(40.dp, 56.dp),
            avatar = 38.dp,
            avatarMine = 42.dp,
        )

        private val Tight = TableSizes(
            mine = CardScale(40.dp, 56.dp),
            theirs = CardScale(34.dp, 48.dp),
            side = CardScale(31.dp, 44.dp),
            avatar = 30.dp,
            avatarMine = 34.dp,
        )

        /** Below this the roomy table cannot fit four hands and two piles without clipping. */
        private val ROOMY_FLOOR = 560.dp

        fun forHeight(height: Dp): TableSizes = if (height >= ROOMY_FLOOR) Roomy else Tight
    }
}
