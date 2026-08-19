package game.vinto.engine.cases

import game.vinto.engine.MutableCard
import game.vinto.engine.MutableGameState
import game.vinto.engine.MutablePlayerState
import game.vinto.shapes.FailedTossInAttempt
import game.vinto.shapes.GameAction
import game.vinto.shapes.SerializedOpponentKnowledge
import game.vinto.shapes.TossInAction

/**
 * PARTICIPATE_IN_TOSS_IN — a player throws in one or more cards matching the current ranks.
 *
 * The rule is all-or-nothing: if any named card fails to match, none of them leave the hand,
 * the player draws one penalty card per mismatch, and every attempted card is revealed to
 * the table. That is why validation happens over the whole set before anything is removed.
 *
 * Ported from `packages/engine/src/lib/cases/participate-in-toss.ts`.
 */
fun handleParticipateInTossIn(
    state: MutableGameState,
    action: GameAction.ParticipateInTossIn,
): Boolean {
    val playerId = action.payload.playerId
    val positions = action.payload.positions

    val player = state.playerById(playerId) ?: return false
    val tossIn = state.activeTossIn ?: return false
    if (positions.isEmpty()) return false

    // A single unknown position invalidates the whole attempt rather than part of it.
    val attempted = positions.map { position ->
        val card = player.cards.getOrNull(position) ?: return false
        card to position
    }

    val invalid = attempted.filterNot { (card, _) -> tossIn.ranks.contains(card.rank) }
    if (invalid.isNotEmpty()) {
        applyFailedTossIn(state, player, attempted, invalid)
        return true
    }

    if (!tossIn.participants.contains(playerId)) {
        tossIn.participants.add(playerId)
    }

    // Descending, so removing a card never shifts a position still to be removed.
    for (position in positions.sortedDescending()) {
        val card = player.cards.getOrNull(position) ?: continue
        player.cards.removeAt(position)

        player.knownCardPositions.apply {
            val shifted = filter { it != position }.map { if (it > position) it - 1 else it }
            clear()
            addAll(shifted)
        }
        shiftOpponentKnowledge(state, playerId, position)

        // An action card waits its turn in the queue; anything else goes straight to the
        // discard pile, beneath the top card so an unplayed action stays reachable.
        if ((card.actionText?.length ?: 0) > 0) {
            tossIn.queuedActions.add(
                TossInAction(playerId = playerId, rank = card.rank, position = position),
            )
        } else {
            state.discardPile.addBeforeTop(card)
        }
    }

    return true
}

/**
 * The penalty path: nothing leaves the hand, one drawn card per mismatch, and every card the
 * player tried to toss becomes public knowledge.
 */
private fun applyFailedTossIn(
    state: MutableGameState,
    player: MutablePlayerState,
    attempted: List<Pair<MutableCard, Int>>,
    invalid: List<Pair<MutableCard, Int>>,
) {
    val tossIn = state.activeTossIn ?: return

    repeat(invalid.size) {
        if (state.drawPile.length > 0) {
            state.drawPile.drawTop()?.let { player.cards.add(it) }
        }
    }

    val failures = tossIn.failedAttempts ?: mutableListOf<FailedTossInAttempt>().also {
        tossIn.failedAttempts = it
    }
    for ((card, position) in invalid) {
        val attempt = FailedTossInAttempt(
            playerId = player.id,
            cardRank = card.rank,
            position = position,
            expectedRanks = tossIn.ranks.toList(),
        )
        failures.add(attempt)
        state.roundFailedAttempts.add(attempt)
    }

    for ((card, position) in attempted) {
        for (observer in state.players) {
            if (observer.id == player.id) {
                if (!observer.knownCardPositions.contains(position)) {
                    observer.knownCardPositions.add(position)
                }
                continue
            }
            val knowledge = observer.opponentKnowledge ?: mutableMapOf()
            val about = knowledge[player.id] ?: SerializedOpponentKnowledge(emptyMap())
            knowledge[player.id] =
                about.copy(knownCards = about.knownCards + (position to card.freeze()))
            observer.opponentKnowledge = knowledge
        }
    }
}

/**
 * A card leaving a hand renumbers everything after it, so what other players believe about
 * that hand has to be renumbered too — otherwise their knowledge silently points at the
 * wrong card.
 */
private fun shiftOpponentKnowledge(state: MutableGameState, ownerId: String, removed: Int) {
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
