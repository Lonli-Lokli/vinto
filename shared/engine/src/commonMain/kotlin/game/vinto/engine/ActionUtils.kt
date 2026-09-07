package game.vinto.engine

import game.vinto.shapes.ActionTarget
import game.vinto.shapes.Card
import game.vinto.shapes.Claim
import game.vinto.shapes.Rank
import game.vinto.shapes.SerializedOpponentKnowledge
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

/**
 * A watched swap: what each seat has *seen* of the two cards follows them too.
 *
 * The pair of positions is public — the table watches them named and watches the cards move —
 * so a seat that knew either card knows exactly where it went. Leaving this out pinned the
 * table's memory to an address rather than to a card, and it cost a reported deal twice from
 * one cause: a Joker swapped in publicly, moved on by somebody's Queen, and still believed to
 * be in the row it had left, by every seat, all the way through the coalition round.
 *
 * Call it **before** the cards move. [lookedAtBoth] names the seat that peeked first — the
 * Queen's player, who ends up knowing both sides whatever it knew before; a Jack is blind and
 * passes null.
 */
fun carrySeenCardsAcrossSwap(
    state: MutableGameState,
    a: ActionTarget,
    b: ActionTarget,
    lookedAtBoth: String? = null,
) {
    val ownerA = state.playerById(a.playerId) ?: return
    val ownerB = state.playerById(b.playerId) ?: return
    val cardA = ownerA.cards.getOrNull(a.position)?.freeze() ?: return
    val cardB = ownerB.cards.getOrNull(b.position)?.freeze() ?: return

    for (observer in state.players) {
        val peeked = observer.id == lookedAtBoth
        val seenA = if (peeked) cardA else observer.seenAt(ownerA, a.position, cardA)
        val seenB = if (peeked) cardB else observer.seenAt(ownerB, b.position, cardB)
        observer.recordSeen(ownerA, a.position, seenB)
        observer.recordSeen(ownerB, b.position, seenA)
    }
}

/**
 * What this observer knows sits at [position] of [owner]'s row, [card] being what is there.
 *
 * A seat's memory of its own row is a set of positions rather than of cards, so the truth has
 * to be handed in: knowing position 2 means knowing the card that is at position 2.
 */
private fun MutablePlayerState.seenAt(
    owner: MutablePlayerState,
    position: Int,
    card: Card,
): Card? = if (id == owner.id) {
    card.takeIf { position in knownCardPositions }
} else {
    opponentKnowledge?.get(owner.id)?.knownCards?.get(position)
}

/**
 * Writes a belief back, null meaning the seat no longer knows what is there.
 *
 * Nothing is materialised for a seat that knew nothing and still knows nothing — an empty
 * record serialises differently from an absent one, and the parity corpus is full of hands
 * nobody has ever seen.
 */
private fun MutablePlayerState.recordSeen(
    owner: MutablePlayerState,
    position: Int,
    card: Card?,
) {
    if (id == owner.id) {
        when {
            card == null -> knownCardPositions.remove(position)
            position !in knownCardPositions -> knownCardPositions.add(position)
        }
        return
    }

    val about = opponentKnowledge?.get(owner.id)
    if (card == null) {
        if (about == null || position !in about.knownCards) return
        opponentKnowledge?.put(owner.id, about.copy(knownCards = about.knownCards - position))
        return
    }

    val knowledge = opponentKnowledge
        ?: mutableMapOf<String, SerializedOpponentKnowledge>().also { opponentKnowledge = it }
    val standing = about ?: SerializedOpponentKnowledge(emptyMap())
    knowledge[owner.id] = standing.copy(knownCards = standing.knownCards + (position to card))
}

/**
 * A removal renumbers the positions above it, and what the *other* seats have seen of that
 * hand is renumbered with it — otherwise their memory silently points one card along.
 *
 * There are three of these, and they belong together: the owner's own `knownCardPositions`,
 * this, and [shiftDeclarationsAfterRemoval]. A removal path that applies two of the three
 * leaves the table believing something false, which is how the King's correct declaration
 * came to — `SelfPlayGateTest` now fails on any seat holding an untrue belief.
 */
fun shiftSeenCardsAfterRemoval(state: MutableGameState, ownerId: String, removed: Int) {
    for (observer in state.players) {
        if (observer.id == ownerId) continue
        val knowledge = observer.opponentKnowledge ?: continue
        val about = knowledge[ownerId] ?: continue

        val updated = about.knownCards
            .filterKeys { it != removed }
            .mapKeys { (position, _) -> if (position > removed) position - 1 else position }

        knowledge[ownerId] = about.copy(knownCards = updated)
    }
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
