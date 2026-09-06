package game.vinto.engine

import game.vinto.shapes.Claim
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
 * A claim describes one physical card. Where the card goes, what was said about it goes.
 *
 * The three rules, and each is a rule about *watching*:
 *
 *  - when the card leaves the table face up — a swap-in discards it, publicly — the claim is
 *    dropped, because it now points at nothing;
 *  - when the whole table watches it move to another position (a Jack or a Queen swap), the
 *    claim travels with it, **keeping its speaker**: table talk about a card everybody tracked
 *    moving is still that person's statement about that card;
 *  - when a removal renumbers the positions above it, claims are renumbered with their cards.
 *
 * All of them no-op on a hand nobody has spoken about (`claims == null`), which is every hand
 * in every parity recording: the field is never materialised there, so the corpus hashes
 * cannot move. An emptied list is normalised back to null for the same reason.
 *
 * A claim naming **two** positions — a pair whose order its speaker has lost — is dropped
 * when either of its cards moves. Half of "these two are a King and an Ace" is not a
 * statement anybody made.
 */
fun MutablePlayerState.clearDeclarationAt(position: Int) {
    val standing = claims ?: return
    standing.removeAll { position in it.positions }
    if (standing.isEmpty()) claims = null
}

/** A watched swap: whatever was claimed about each card follows it to its new hand. */
fun swapDeclarationsBetween(
    playerA: MutablePlayerState,
    positionA: Int,
    playerB: MutablePlayerState,
    positionB: Int,
) {
    val movingA = playerA.claims.orEmpty().filter { it.positions == listOf(positionA) }
    val movingB = playerB.claims.orEmpty().filter { it.positions == listOf(positionB) }
    if (movingA.isEmpty() && movingB.isEmpty()) {
        // Still clear any *pair* claim either card was half of: the pair is broken either way.
        playerA.dropPairsAt(positionA)
        playerB.dropPairsAt(positionB)
        return
    }

    playerA.clearDeclarationAt(positionA)
    playerB.clearDeclarationAt(positionB)
    movingB.forEach { playerA.addClaim(it.copy(positions = listOf(positionA))) }
    movingA.forEach { playerB.addClaim(it.copy(positions = listOf(positionB))) }
}

/** A removal renumbers the positions above it, and the claims move with their cards. */
fun MutablePlayerState.shiftDeclarationsAfterRemoval(position: Int) {
    val standing = claims ?: return
    val shifted = standing
        .filterNot { position in it.positions }
        .map { claim ->
            claim.copy(positions = claim.positions.map { if (it > position) it - 1 else it })
        }
    if (shifted.isEmpty()) {
        claims = null
    } else {
        standing.clear()
        standing.addAll(shifted)
    }
}

/** A pair claim is about two cards together; moving either one ends it. */
private fun MutablePlayerState.dropPairsAt(position: Int) {
    val standing = claims ?: return
    standing.removeAll { it.positions.size > 1 && position in it.positions }
    if (standing.isEmpty()) claims = null
}

private fun MutablePlayerState.addClaim(claim: Claim) {
    val standing = claims ?: mutableListOf<Claim>().also { claims = it }
    standing += claim
}
