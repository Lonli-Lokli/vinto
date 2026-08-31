package game.vinto.engine

import game.vinto.shapes.Rank
import game.vinto.shapes.TargetType

/**
 * What kind of target selection a rank's action requires.
 *
 * Ported from `legacy-web/packages/engine/src/lib/utils/action-utils.ts`. Ranks with no action return
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
 * A declared claim describes one physical card. When the card it was about leaves the table
 * face up — a swap-in discards it, publicly — the claim is dropped; when the whole table
 * *watches* it move to another position (a Jack or Queen swap), the claim travels with it,
 * because table talk about a card everyone tracked moving is still about that card.
 *
 * All helpers no-op on hands that never declared anything (`declaredCards == null`), which
 * is every hand in every parity recording: the field is never materialised there, so the
 * corpus hashes cannot move. An emptied map is normalised back to null for the same reason.
 */
fun MutablePlayerState.clearDeclarationAt(position: Int) {
    val declared = declaredCards ?: return
    declared.remove(position)
    if (declared.isEmpty()) declaredCards = null
}

/** A watched swap: whatever was claimed about each card follows it to its new hand. */
fun swapDeclarationsBetween(
    playerA: MutablePlayerState,
    positionA: Int,
    playerB: MutablePlayerState,
    positionB: Int,
) {
    val claimA = playerA.declaredCards?.get(positionA)
    val claimB = playerB.declaredCards?.get(positionB)
    if (claimA == null && claimB == null) return

    playerA.setDeclarationAt(positionA, claimB)
    playerB.setDeclarationAt(positionB, claimA)
}

private fun MutablePlayerState.setDeclarationAt(position: Int, claim: game.vinto.shapes.Rank?) {
    if (claim == null) {
        clearDeclarationAt(position)
        return
    }
    val declared = declaredCards ?: mutableMapOf<Int, game.vinto.shapes.Rank>()
        .also { declaredCards = it }
    declared[position] = claim
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
