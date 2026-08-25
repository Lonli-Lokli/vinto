package game.vinto.engine

import game.vinto.shapes.Rank
import game.vinto.shapes.TargetType

/**
 * What kind of target selection a rank's action requires.
 *
 * Ported from `packages/engine/src/lib/utils/action-utils.ts`. Ranks with no action return
 * null, which is how the caller tells "no target needed" from "target still to choose".
 */
fun getTargetTypeFromRank(rank: Rank): TargetType? = when (rank) {
    Rank.SEVEN, Rank.EIGHT -> TargetType.OWN_CARD
    Rank.NINE, Rank.TEN -> TargetType.OPPONENT_CARD
    Rank.JACK -> TargetType.SWAP_CARDS
    Rank.QUEEN -> TargetType.PEEK_THEN_SWAP
    Rank.KING -> TargetType.DECLARE_ACTION
    Rank.ACE -> TargetType.FORCE_DRAW
    Rank.TWO, Rank.THREE, Rank.FOUR, Rank.FIVE, Rank.SIX, Rank.JOKER -> null
}

/**
 * A declared claim describes one physical card at one position. When the card at that
 * position changes identity — a swap put a different card there — the claim is dropped
 * rather than carried onto a card it was never about.
 *
 * Both helpers no-op on a hand that never declared anything (`declaredCards == null`), which
 * is every hand in every parity recording: the field is never materialised there, so the
 * corpus hashes cannot move. An emptied map is normalised back to null for the same reason.
 */
fun MutablePlayerState.clearDeclarationAt(position: Int) {
    val declared = declaredCards ?: return
    declared.remove(position)
    if (declared.isEmpty()) declaredCards = null
}

/** A removal renumbers the positions above it, and the claims move with their cards. */
fun MutablePlayerState.shiftDeclarationsAfterRemoval(position: Int) {
    val declared = declaredCards ?: return
    val shifted = declared
        .filterKeys { it != position }
        .mapKeys { (claimed, _) -> if (claimed > position) claimed - 1 else claimed }
    if (shifted.isEmpty()) {
        declaredCards = null
    } else {
        declared.clear()
        declared.putAll(shifted)
    }
}
